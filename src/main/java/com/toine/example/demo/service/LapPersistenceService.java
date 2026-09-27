package com.toine.example.demo.service;

import com.toine.example.demo.models.Channel;
import com.toine.example.demo.models.Lap;
import com.toine.example.demo.models.LapChannel;
import com.toine.example.demo.models.Session;
import com.toine.example.demo.models.SessionUids;
import com.toine.example.demo.reference.F1Appendix;
import com.toine.example.demo.repository.LapChannelRepository;
import com.toine.example.demo.repository.LapRepository;
import com.toine.example.demo.repository.SessionRepository;
import com.toine.example.demo.service.recording.CompletedLap;
import com.toine.example.demo.service.recording.LapSink;
import com.toine.example.demo.service.recording.LapTiming;
import com.toine.example.demo.service.recording.RecordedSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.RejectedExecutionException;

/** Stores what the LapRecorder produces. Every write runs in its own transaction on the lap writer thread. */
@Service
public class LapPersistenceService implements LapSink {

    private static final Logger log = LoggerFactory.getLogger(LapPersistenceService.class);

    private final SessionRepository sessionRepository;
    private final LapRepository lapRepository;
    private final LapChannelRepository lapChannelRepository;
    private final TransactionTemplate transaction;
    private final TaskExecutor lapWriterExecutor;

    public LapPersistenceService(SessionRepository sessionRepository,
                                 LapRepository lapRepository,
                                 LapChannelRepository lapChannelRepository,
                                 TransactionTemplate transaction,
                                 @Qualifier("lapWriterExecutor") TaskExecutor lapWriterExecutor) {
        this.sessionRepository = sessionRepository;
        this.lapRepository = lapRepository;
        this.lapChannelRepository = lapChannelRepository;
        this.transaction = transaction;
        this.lapWriterExecutor = lapWriterExecutor;
    }

    @Override
    public void lapCompleted(RecordedSession recordedSession, CompletedLap lap) {
        write("save lap " + lap.lapNumber(), () -> {
            Session session = sessionRepository.findById(recordedSession.sessionUid())
                    .orElseGet(() -> sessionRepository.save(new Session(recordedSession.sessionUid(), recordedSession.firstSeenAt())));
            if (recordedSession.info() != null) {
                session.update(recordedSession.info());
            }

            // A lap number is recorded again after a flashback or restart: the new drive replaces it.
            lapRepository.deleteBySessionAndLapNumber(session.getSessionUid(), lap.lapNumber());
            Lap saved = lapRepository.save(new Lap(session, lap.lapNumber(), lap.timing(), lap.pitIn(), lap.pitOut(),
                    lap.sampleCount(), Instant.now()));

            List<LapChannel> channels = new ArrayList<>(lap.channels().size());
            for (Map.Entry<Channel, float[]> channel : lap.channels().entrySet()) {
                channels.add(new LapChannel(saved.getId(), channel.getKey(), channel.getValue()));
            }
            lapChannelRepository.saveAll(channels);

            log.info("Saved lap {} of {} {} (session {}): {}{}, {} samples",
                    lap.lapNumber(), F1Appendix.track(session.getTrackId()), F1Appendix.sessionType(session.getSessionType()),
                    SessionUids.format(session.getSessionUid()), formatLapTime(lap.timing().lapTimeMs()),
                    lap.timing().valid() ? "" : " (invalid)", lap.sampleCount());
        });
    }

    @Override
    public void lapRetracted(long sessionUid, int lapNumber) {
        write("retract lap " + lapNumber, () -> {
            lapRepository.deleteBySessionAndLapNumber(sessionUid, lapNumber);
            log.info("Removed lap {} of session {}: a flashback rewound into it", lapNumber, SessionUids.format(sessionUid));
        });
    }

    @Override
    public void lapTimingCorrected(long sessionUid, int lapNumber, LapTiming timing) {
        write("correct the timing of lap " + lapNumber, () -> lapRepository.findBySession_SessionUidAndLapNumber(sessionUid, lapNumber)
                .ifPresent(lap -> {
                    lap.setTiming(timing);
                    log.info("Lap {} timing corrected by the game: {}{}", lapNumber,
                            formatLapTime(timing.lapTimeMs()), timing.valid() ? "" : " (invalid)");
                }));
    }

    private void write(String description, Runnable work) {
        try {
            lapWriterExecutor.execute(() -> {
                try {
                    transaction.executeWithoutResult(status -> work.run());
                } catch (RuntimeException e) {
                    log.error("Failed to {}", description, e);
                }
            });
        } catch (RejectedExecutionException e) {
            log.error("Database write queue is full - could not {}", description);
        }
    }

    private static String formatLapTime(int ms) {
        return String.format(Locale.ROOT, "%d:%06.3f", ms / 60_000, (ms % 60_000) / 1000.0);
    }
}
