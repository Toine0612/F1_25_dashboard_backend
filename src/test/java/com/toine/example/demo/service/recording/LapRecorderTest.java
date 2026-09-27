package com.toine.example.demo.service.recording;

import com.toine.example.demo.models.Channel;
import com.toine.example.demo.models.dto.packets.F1SessionHistory;
import com.toine.example.demo.models.dto.sessionHistory.LapHistory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class LapRecorderTest {

    private static final long SESSION = 42;
    private static final int TRACK_LENGTH = 1000;

    private final RecordingSink sink = new RecordingSink();
    private final LapRecorder recorder = new LapRecorder(sink, Clock.fixed(Instant.parse("2026-09-26T12:00:00Z"), ZoneOffset.UTC));

    private long frame = 0;

    @BeforeEach
    void knowTheTrack() {
        recorder.onSessionInfo(SESSION, new SessionInfo(18, 7, TRACK_LENGTH, 330f, 660f, 0, 5, 0));
    }

    @Test
    void recordsALapFromLineToLineWithTheOfficialTiming() {
        drive(1, 2, 998, 40, 0);
        drive(2, 3, 50, 60, 20_000);
        recorder.onSessionHistory(SESSION, history(new LapHistory(20_000, 6_600, 6_600, 6_800, 0x0F)));

        assertThat(sink.completed).hasSize(1);
        CompletedLap lap = sink.completed.getFirst();
        assertThat(lap.lapNumber()).isEqualTo(1);
        assertThat(lap.timing()).isEqualTo(new LapTiming(20_000, 6_600, 6_600, 6_800, true));
        assertThat(lap.sampleCount()).isEqualTo(200);
        float[] distance = lap.channels().get(Channel.DISTANCE);
        assertThat(distance[0]).isEqualTo(2f);
        assertThat(distance[distance.length - 1]).isEqualTo(997f);
        assertStrictlyIncreasing(distance);
    }

    @Test
    void fallsBackToLapDataTimingWhenSessionHistoryDoesNotArrive() {
        drive(1, 2, 998, 40, 0);
        FrameSample last = sample(1, 999, 19_990, 0);
        recorder.onSample(SESSION, new FrameSample(last.frame(), 1, 999, 19_990, 0, 6_600, 6_700, true, 0, 1,
                200, 1, 0, 0, 7, 11_000, false, 0, 0, 0));
        drive(2, 3, 150, 60, 20_050);

        assertThat(sink.completed).hasSize(1);
        assertThat(sink.completed.getFirst().timing()).isEqualTo(new LapTiming(20_050, 6_600, 6_700, 6_750, false));
    }

    @Test
    void doesNotStoreAnOutLapThatStartedAtThePitExit() {
        drive(1, 400, 998, 0, 0);
        drive(2, 3, 998, 0, 15_000);
        drive(3, 3, 50, 0, 20_000);
        recorder.onSessionHistory(SESSION, history(new LapHistory(15_000, 0, 0, 0, 0x0F),
                new LapHistory(20_000, 6_000, 7_000, 7_000, 0x0F)));

        assertThat(sink.completed).extracting(CompletedLap::lapNumber).containsExactly(2);
    }

    @Test
    void neverRecordsGarageOrPreLineSamples() {
        for (int i = 0; i < 50; i++) {
            recorder.onSample(SESSION, garageSample(20));       // idle in the garage, lap 1 at 20 m
        }
        drive(1, -100, -5, 0, 0);                               // run-up to the line
        drive(1, 3, 998, 0, 0);
        drive(2, 3, 50, 0, 20_000);
        recorder.onSessionHistory(SESSION, history(new LapHistory(20_000, 6_600, 6_600, 6_800, 0x0F)));

        float[] speed = sink.completed.getFirst().channels().get(Channel.SPEED);
        assertThat(sink.completed.getFirst().channels().get(Channel.DISTANCE)[0]).isEqualTo(3f);
        assertThat(speed).doesNotContain(0f);
    }

    @Test
    void flashbackWithinALapReplacesTheRewoundPart() {
        drive(1, 2, 600, 0, 0);
        frame = 59;                                             // the game rewinds to frame 60 (297 m, 5.9 s)
        driveAt(1, 297, 998, 5_900, 0, 111);                    // ...and the lap is driven again from there
        drive(2, 3, 50, 0, 20_000);
        recorder.onSessionHistory(SESSION, history(new LapHistory(20_000, 6_600, 6_600, 6_800, 0x0F)));

        CompletedLap lap = sink.completed.getFirst();
        float[] distance = lap.channels().get(Channel.DISTANCE);
        float[] speed = lap.channels().get(Channel.SPEED);
        assertStrictlyIncreasing(distance);
        for (int i = 0; i < distance.length; i++) {
            assertThat(speed[i]).isEqualTo(distance[i] >= 297 ? 111f : 200f);
        }
    }

    @Test
    void flashbackAcrossTheLineRetractsTheLapAndRecordsItAgain() {
        drive(1, 2, 998, 0, 0);
        long lastFrameOfLap1 = frame;
        drive(2, 3, 100, 0, 20_000);
        recorder.onSessionHistory(SESSION, history(new LapHistory(20_000, 6_600, 6_600, 6_800, 0x0F)));
        assertThat(sink.completed).hasSize(1);

        frame = lastFrameOfLap1 - 10;                           // flashback to 952 m (19.0 s) into lap 1
        driveAt(1, 952, 998, 19_000, 0, 150);
        drive(2, 3, 50, 0, 20_400);
        recorder.onSessionHistory(SESSION, history(new LapHistory(20_400, 6_600, 6_600, 7_200, 0x0F)));

        assertThat(sink.events).containsExactly("completed 1", "retracted 1", "completed 1");
        CompletedLap redriven = sink.completed.getLast();
        assertThat(redriven.timing().lapTimeMs()).isEqualTo(20_400);
        assertThat(redriven.channels().get(Channel.SPEED)).endsWith(150f);
        assertStrictlyIncreasing(redriven.channels().get(Channel.DISTANCE));
    }

    @Test
    void sessionEndStillStoresALapThatAlreadyCrossedTheLine() {
        drive(1, 2, 998, 0, 0);
        drive(2, 3, 20, 0, 20_000);                             // lap time already updated, no history yet
        recorder.onSessionEnded(SESSION);

        assertThat(sink.completed).extracting(lap -> lap.timing().lapTimeMs()).containsExactly(20_000);
    }

    @Test
    void lapIsLostIfTheGameNeverReportedItsTime() {
        drive(1, 2, 998, 0, 18_000);                            // shows the previous lap's time
        drive(2, 3, 20, 0, 18_000);                             // ...and still does after the line
        recorder.onSessionEnded(SESSION);

        assertThat(sink.completed).isEmpty();
    }

    @Test
    void passesOnTimingTheGameCorrectsAfterwards() {
        drive(1, 2, 998, 0, 0);
        drive(2, 3, 50, 0, 20_000);
        recorder.onSessionHistory(SESSION, history(new LapHistory(20_000, 6_600, 6_600, 6_800, 0x0F)));
        recorder.onSessionHistory(SESSION, history(new LapHistory(20_000, 6_600, 6_600, 6_800, 0x0F)));
        recorder.onSessionHistory(SESSION, history(new LapHistory(20_000, 6_600, 6_600, 6_800, 0x00)));

        assertThat(sink.events).containsExactly("completed 1", "corrected 1");
        assertThat(sink.corrections.getFirst().valid()).isFalse();
    }

    @Test
    void aRestartedLapOnlyKeepsTheNewAttempt() {
        drive(1, 2, 500, 0, 0);
        drive(1, 2, 998, 0, 0);                                 // same lap, lap time back at zero
        drive(2, 3, 50, 0, 20_000);
        recorder.onSessionHistory(SESSION, history(new LapHistory(20_000, 6_600, 6_600, 6_800, 0x0F)));

        assertThat(sink.completed.getFirst().sampleCount()).isEqualTo(200);
    }

    @Test
    void aNewSessionUidDiscardsTheLapInProgress() {
        drive(1, 2, 500, 0, 0);
        frame = 0;                                              // frame identifiers restart as well
        long nextSession = 43;
        for (int i = 0; i < 200; i++) recorder.onSample(nextSession, sample(1, 2 + i * 5, 40 + i * 100, 0));
        for (int i = 0; i < 10; i++) recorder.onSample(nextSession, sample(2, 3 + i * 5, 60 + i * 100, 20_000));
        recorder.onSessionHistory(nextSession, history(new LapHistory(20_000, 6_600, 6_600, 6_800, 0x0F)));

        assertThat(sink.sessions).containsExactly(nextSession);
        assertThat(sink.completed.getFirst().sampleCount()).isEqualTo(200);
    }

    @Test
    void skipsSamplesThatDoNotMoveTheCarForward() {
        drive(1, 2, 400, 0, 0);
        recorder.onSample(SESSION, sample(1, 390, 8_000, 0));   // spun and rolled back
        recorder.onSample(SESSION, sample(1, 390, 8_100, 0));
        recorder.onSample(SESSION, sample(1, 2_000, 8_200, 0)); // impossible jump: a glitch
        drive(1, 402, 998, 8_300, 0);
        drive(2, 3, 50, 0, 20_000);
        recorder.onSessionHistory(SESSION, history(new LapHistory(20_000, 6_600, 6_600, 6_800, 0x0F)));

        float[] distance = sink.completed.getFirst().channels().get(Channel.DISTANCE);
        assertStrictlyIncreasing(distance);
        assertThat(distance).doesNotContain(2_000f);
    }

    /** Drives {@code lap} from {@code from} to {@code to} metres in 5 m steps, 100 ms apart. */
    private void drive(int lap, int from, int to, int startTimeMs, int lastLapTimeMs) {
        driveAt(lap, from, to, startTimeMs, lastLapTimeMs, 200);
    }

    private void driveAt(int lap, int from, int to, int startTimeMs, int lastLapTimeMs, int speed) {
        int time = startTimeMs;
        for (int distance = from; distance < to; distance += 5, time += 100) {
            FrameSample s = sample(lap, distance, time, lastLapTimeMs);
            recorder.onSample(SESSION, new FrameSample(s.frame(), lap, distance, time, lastLapTimeMs, 0, 0, false, 0, 1,
                    speed, 1, 0, 0, 7, 11_000, false, distance, 0, 0));
        }
    }

    private FrameSample sample(int lap, float distance, int lapTimeMs, int lastLapTimeMs) {
        return new FrameSample(++frame, lap, distance, lapTimeMs, lastLapTimeMs, 0, 0, false, 0, 1,
                200, 1, 0, 0, 7, 11_000, false, distance, 0, 0);
    }

    private FrameSample garageSample(float distance) {
        return new FrameSample(++frame, 1, distance, 0, 0, 0, 0, false, 0, 0,
                0, 0, 0, 0, 0, 4_000, false, distance, 0, 0);
    }

    /** The given completed laps plus the current partial lap. */
    private static F1SessionHistory history(LapHistory... completed) {
        List<LapHistory> laps = new ArrayList<>(List.of(completed));
        laps.add(new LapHistory(0, 0, 0, 0, 0));
        return new F1SessionHistory(0, laps.size(), 1, 1, 1, 1, 1, laps, List.of());
    }

    private static void assertStrictlyIncreasing(float[] values) {
        for (int i = 1; i < values.length; i++) {
            assertThat(values[i]).isGreaterThan(values[i - 1]);
        }
    }

    private static final class RecordingSink implements LapSink {
        final List<String> events = new ArrayList<>();
        final List<CompletedLap> completed = new ArrayList<>();
        final List<LapTiming> corrections = new ArrayList<>();
        final List<Long> sessions = new ArrayList<>();

        @Override
        public void lapCompleted(RecordedSession session, CompletedLap lap) {
            events.add("completed " + lap.lapNumber());
            completed.add(lap);
            sessions.add(session.sessionUid());
        }

        @Override
        public void lapRetracted(long sessionUid, int lapNumber) {
            events.add("retracted " + lapNumber);
        }

        @Override
        public void lapTimingCorrected(long sessionUid, int lapNumber, LapTiming timing) {
            events.add("corrected " + lapNumber);
            corrections.add(timing);
        }
    }
}
