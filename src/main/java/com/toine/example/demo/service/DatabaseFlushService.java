package com.toine.example.demo.service;

import com.toine.example.demo.models.Lap;
import com.toine.example.demo.models.Session;
import com.toine.example.demo.models.Telemetry;
import com.toine.example.demo.models.dto.TelemetrySample;
import com.toine.example.demo.models.dto.packets.F1SessionData;
import com.toine.example.demo.models.dto.sessionHistory.LapHistory;
import com.toine.example.demo.repository.LapRepository;
import com.toine.example.demo.repository.SessionRepository;
import com.toine.example.demo.repository.TelemetryRepository;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentSkipListMap;

@Service
public class DatabaseFlushService {

    private final SessionRepository sessionRepository;
    private final LapRepository lapRepository;
    private final TelemetryRepository telemetryRepository;

    public DatabaseFlushService(SessionRepository sessionRepository, LapRepository lapRepository, TelemetryRepository telemetryRepository) {
        this.sessionRepository = sessionRepository;
        this.lapRepository = lapRepository;
        this.telemetryRepository = telemetryRepository;
    }

    @Async("ioDatabaseExecutor")
    @Transactional
    public CompletableFuture<Void> flushTelemetryDataAsync(long sessionId, F1SessionData sessionData, short lap_number, LapHistory lapHistory, ConcurrentSkipListMap<Long, TelemetrySample> flushTelemetry) {
        int sampleCount = flushTelemetry == null ? 0 : flushTelemetry.size();
        System.out.println("Async thread [" + Thread.currentThread().getName() + "] flushing " + sampleCount + " records for lap " + lap_number + ".");

        try {
            sessionRepository.upsertSession(sessionId, sessionData.sessionType(), sessionData.track(), sessionData.weekendId());
            Session session = sessionRepository.getReferenceById(sessionId);

            Boolean isValid = (lapHistory.m_lapValidBitFlags() & 0x01) != 0;
            Lap lap = lapRepository.save(new Lap(session, lap_number, lapHistory.m_lapTimeInMS(), lapHistory.m_sector1TimeMSPart(), lapHistory.m_sector2TimeMSPart(), lapHistory.m_sector3TimeMSPart(), isValid));

            if (flushTelemetry != null && !flushTelemetry.isEmpty()) {
                List<Telemetry> entitiesToSave = new ArrayList<>();
                for (TelemetrySample sample : flushTelemetry.values()) {
                    entitiesToSave.add(new Telemetry(sample.frame(), lap, sample.telemetry(), sample.lapData().lapDistance(), sample.lapData().currentLapTimeInMS()));
                }
                telemetryRepository.saveAll(entitiesToSave);
            }
        } catch (Exception e) {
            System.err.println("Database flush failed for lap " + lap_number + ": " + e.getMessage());
        }

        return CompletableFuture.completedFuture(null);
    }
}