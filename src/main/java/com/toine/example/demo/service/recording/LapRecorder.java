package com.toine.example.demo.service.recording;

import com.toine.example.demo.models.SessionUids;
import com.toine.example.demo.models.dto.packets.F1LapData;
import com.toine.example.demo.models.dto.packets.F1SessionHistory;
import com.toine.example.demo.models.dto.sessionHistory.LapHistory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Clock;
import java.util.HashMap;
import java.util.Map;

/**
 * Turns the player's stream of frames into completed laps.
 * <ul>
 *   <li>A lap runs from the start/finish line to the start/finish line. Laps that don't (out-laps
 *   from the pit exit, laps interrupted by the garage, a restart or the end of the session) are
 *   discarded, so every stored lap can be compared with every other lap at the same track position.
 *   Garage samples and samples before the line is first crossed never enter a lap.</li>
 *   <li>The frame identifier orders the samples. It goes back after a flashback, so a lower frame
 *   rewinds the recording to that frame - back across the line into the previous lap if needed, which
 *   is then retracted and driven again.</li>
 *   <li>A lap's timing comes from the game's Session History (authoritative, including validity),
 *   with Lap Data as the fallback. Later Session History changes - e.g. a lap invalidated afterwards
 *   by a penalty - are passed on as corrections.</li>
 *   <li>Sessions are keyed by the session UID in every packet header, so recording doesn't depend on
 *   receiving the one "session started" event and works when the app starts mid-session.</li>
 * </ul>
 * Packets are fed in from the single UDP handler thread; the methods are synchronized only so that
 * {@link #status()} can be read from web threads.
 */
public final class LapRecorder {

    private static final Logger log = LoggerFactory.getLogger(LapRecorder.class);

    /** How far from the start/finish line a lap's first and last samples may be. */
    static final float LINE_TOLERANCE_M = 100f;
    /** How far into the next lap Lap Data's last-lap time is trusted if Session History hasn't confirmed the lap. */
    static final int LAP_DATA_TIMING_DELAY_MS = 2_000;

    private final LapSink sink;
    private final Clock clock;

    private RecordedSession session;            // null until the first packet of a session
    private long lastFrame = -1;
    private int reportedLastLapTimeMs;          // Lap Data's m_lastLapTimeInMS on the latest frame
    private LapRecording current;               // the lap being driven
    private LapRecording awaitingTiming;        // crossed the line, official lap time not known yet
    private LapRecording lastCompleted;         // kept so a flashback into it can reopen it
    private final Map<Integer, LapTiming> completedTimings = new HashMap<>();

    public LapRecorder(LapSink sink, Clock clock) {
        this.sink = sink;
        this.clock = clock;
    }

    public synchronized void onSessionInfo(long sessionUid, SessionInfo info) {
        if (!enterSession(sessionUid)) return;
        if (!info.equals(session.info())) {
            session = new RecordedSession(session.sessionUid(), session.firstSeenAt(), info);
        }
    }

    public synchronized void onSample(long sessionUid, FrameSample sample) {
        if (!enterSession(sessionUid) || sample.frame() == lastFrame) return;
        if (sample.frame() < lastFrame) {
            rewindTo(sample.frame());
        }
        lastFrame = sample.frame();
        reportedLastLapTimeMs = sample.lastLapTimeMs();

        if (sample.driverStatus() == F1LapData.DRIVER_STATUS_IN_GARAGE) {
            settleAwaitingLap();
            discardCurrent("the car returned to the garage");
            return;
        }
        // Negative until the car first crosses the start/finish line (grid, pit exit before the line).
        if (sample.lapDistance() < 0 || sample.lapNumber() < 1) return;

        if (current == null || current.isEmpty()) {
            current = new LapRecording(sample.lapNumber());
        } else if (sample.lapNumber() == current.lapNumber() + 1) {
            crossLine(sample.lapNumber());
        } else if (sample.lapNumber() != current.lapNumber()) {
            discardCurrent("the lap number jumped to " + sample.lapNumber());
            current = new LapRecording(sample.lapNumber());
        } else if (sample.lapTimeMs() < current.last().lapTimeMs()) {
            // Same lap, earlier lap time, no flashback (that would have lowered the frame): a restart.
            discardCurrent("the lap was restarted");
            current = new LapRecording(sample.lapNumber());
        }
        current.append(sample);

        if (awaitingTiming != null && sample.lapTimeMs() >= LAP_DATA_TIMING_DELAY_MS) {
            completeAwaitingLap(LapTiming.fromLapData(sample.lastLapTimeMs(), awaitingTiming.last()));
        }
    }

    public synchronized void onSessionHistory(long sessionUid, F1SessionHistory history) {
        if (!enterSession(sessionUid)) return;

        if (awaitingTiming != null) {
            LapHistory lap = history.lap(awaitingTiming.lapNumber());
            if (lap != null && lap.lapTimeInMS() > 0) {
                completeAwaitingLap(LapTiming.fromHistory(lap));
            }
        }

        completedTimings.replaceAll((lapNumber, stored) -> {
            LapHistory lap = history.lap(lapNumber);
            if (lap == null || lap.lapTimeInMS() == 0) return stored;
            LapTiming official = LapTiming.fromHistory(lap);
            if (official.equals(stored)) return stored;
            sink.lapTimingCorrected(session.sessionUid(), lapNumber, official);
            return official;
        });
    }

    public synchronized void onSessionEnded(long sessionUid) {
        if (session != null && session.sessionUid() == sessionUid) {
            settleAwaitingLap();
            discardCurrent("the session ended");
            lastCompleted = null;
        }
    }

    /** Completes a lap that already crossed the line; call before shutting down. */
    public synchronized void close() {
        settleAwaitingLap();
    }

    public synchronized RecorderStatus status() {
        FrameSample latest = current == null || current.isEmpty() ? null : current.last();
        return new RecorderStatus(session,
                latest == null ? null : current.lapNumber(),
                latest == null ? null : latest.lapDistance(),
                completedTimings.size());
    }

    private boolean enterSession(long sessionUid) {
        if (sessionUid == 0) return false;
        if (session == null || session.sessionUid() != sessionUid) {
            if (session != null) {
                settleAwaitingLap();
                discardCurrent("a new session started");
            }
            session = new RecordedSession(sessionUid, clock.instant(), null);
            lastFrame = -1;
            reportedLastLapTimeMs = 0;
            lastCompleted = null;
            completedTimings.clear();
            log.info("Recording session {}", SessionUids.format(sessionUid));
        }
        return true;
    }

    private void crossLine(int nextLapNumber) {
        if (awaitingTiming != null) {
            log.warn("Lap {} never received a lap time - discarding it", awaitingTiming.lapNumber());
            awaitingTiming = null;
        }
        String incomplete = incompleteReason(current);
        if (incomplete == null) {
            awaitingTiming = current;
        } else {
            log.info("Lap {} not recorded: {}", current.lapNumber(), incomplete);
        }
        current = new LapRecording(nextLapNumber);
    }

    private String incompleteReason(LapRecording lap) {
        float start = lap.first().lapDistance();
        if (start > LINE_TOLERANCE_M) {
            return "recording started %.0f m after the start/finish line (out-lap or joined mid-lap)".formatted(start);
        }
        SessionInfo info = session.info();
        float end = lap.last().lapDistance();
        if (info != null && info.trackLengthM() > 0 && end < info.trackLengthM() - LINE_TOLERANCE_M) {
            return "recording ended %.0f m before the start/finish line".formatted(info.trackLengthM() - end);
        }
        return null;
    }

    private void rewindTo(long frame) {
        if (current != null) {
            current.truncateFrom(frame);
            if (!current.isEmpty()) return;
        }
        // The flashback went back across the start/finish line: the previous lap is being driven again.
        LapRecording previous = awaitingTiming != null ? awaitingTiming : lastCompleted;
        if (previous == null || previous.last().frame() < frame) return;

        previous.truncateFrom(frame);
        if (previous == lastCompleted) {
            sink.lapRetracted(session.sessionUid(), previous.lapNumber());
            completedTimings.remove(previous.lapNumber());
            lastCompleted = null;
        }
        awaitingTiming = null;
        current = previous;
        log.info("Flashback into lap {} - recording it again", previous.lapNumber());
    }

    /** Leaving a lap that already crossed the line: complete it if its time is known, else it's lost. */
    private void settleAwaitingLap() {
        if (awaitingTiming == null) return;
        // m_lastLapTimeInMS still shows the lap before until the game updates it for the awaiting lap.
        int previousLapTimeMs = awaitingTiming.last().lastLapTimeMs();
        if (reportedLastLapTimeMs > 0 && reportedLastLapTimeMs != previousLapTimeMs) {
            completeAwaitingLap(LapTiming.fromLapData(reportedLastLapTimeMs, awaitingTiming.last()));
        } else {
            log.warn("Lap {} not recorded: the game never reported its lap time", awaitingTiming.lapNumber());
            awaitingTiming = null;
        }
    }

    private void completeAwaitingLap(LapTiming timing) {
        LapRecording lap = awaitingTiming;
        awaitingTiming = null;
        if (timing.lapTimeMs() <= 0) {
            log.warn("Lap {} not recorded: invalid lap time {} ms", lap.lapNumber(), timing.lapTimeMs());
            return;
        }
        sink.lapCompleted(session, lap.complete(timing));
        completedTimings.put(lap.lapNumber(), timing);
        lastCompleted = lap;
    }

    private void discardCurrent(String reason) {
        if (current != null && !current.isEmpty()) {
            log.info("Lap {} not recorded: {}", current.lapNumber(), reason);
        }
        current = null;
    }
}
