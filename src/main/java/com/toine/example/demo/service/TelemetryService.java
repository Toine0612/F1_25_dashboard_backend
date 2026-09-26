package com.toine.example.demo.service;

import com.toine.example.demo.models.dto.*;
import com.toine.example.demo.models.dto.event.EventRewind;
import com.toine.example.demo.models.dto.event.EventSessionEnded;
import com.toine.example.demo.models.dto.event.EventSessionStarted;
import com.toine.example.demo.models.dto.packets.*;
import com.toine.example.demo.models.dto.sessionHistory.LapHistory;
import com.toine.example.demo.repository.LapRepository;
import com.toine.example.demo.repository.SessionRepository;
import com.toine.example.demo.repository.TelemetryRepository;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.integration.annotation.ServiceActivator;
import org.springframework.stereotype.Service;

import java.util.NavigableMap;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.Executor;

/**
 * INFO
 * Only Completed laps and in laps are stored. (in laps give detail in crashes) (out laps have no value)
 */

@Service
public class TelemetryService {

    private final SessionRepository sessionRepository;
    private final LapRepository lapRepository;
    private final TelemetryRepository telemetryRepository;
    private final TelemetryParser telemetryParser;
    private final DatabaseFlushService databaseFlushService;
    private final Executor ioDatabaseExecutor;

    // Use volatile to ensure all threads see the most up-to-date value instantly
    private volatile long session_id = 0;

    // Last lap-data packet seen, used only to detect lap transitions/rewinds
    private volatile long lastReceivedFrame = -1;
    private volatile short next_lap_flush = 0;

    // Previous lap-data packet's driver status, used to detect the *transition* into the garage
    // (a pit stop / end of session) rather than firing on every packet while already parked.
    private volatile short lastDriverStatus = -1; // -1 = not yet observed this session

    private volatile F1SessionHistory lastSessionHistory;
    private volatile F1SessionData lastSessionData;

    // A lock to prevent multiple threads from mutating the lap state at the same time
    private final Object stateLock = new Object();

    private static final class FrameEntry {
        F1LapData lap;
        F1CarTelemetry telemetry;
    }
    private final NavigableMap<Long, FrameEntry> frameBuffer = new ConcurrentSkipListMap<>();

    private final ConcurrentSkipListMap<Short, ConcurrentSkipListMap<Long, TelemetrySample>> telemetryData = new ConcurrentSkipListMap<>();

    public TelemetryService(TelemetryParser parser,
                            SessionRepository sessionRepository,
                            LapRepository lapRepository,
                            TelemetryRepository telemetryRepository,
                            DatabaseFlushService databaseFlushService,
                            @Qualifier("ioDatabaseExecutor") Executor ioDatabaseExecutor) {
        this.telemetryParser = parser;
        this.sessionRepository = sessionRepository;
        this.lapRepository = lapRepository;
        this.telemetryRepository = telemetryRepository;
        this.databaseFlushService = databaseFlushService;
        this.ioDatabaseExecutor = ioDatabaseExecutor;
    }

    @ServiceActivator(inputChannel = "eventChanel")
    public void processEventData(byte[] payload) {
        F1EventData eventData = telemetryParser.parseEventData(payload);
        if (eventData == null || eventData.eventDetails() == null) return;

        switch (eventData.eventDetails()) {
            case EventSessionStarted startedSession -> {
                synchronized (stateLock) {
                    session_id = startedSession.session_id();
                    telemetryData.clear();
                    frameBuffer.clear();
                    lastSessionHistory = null;
                    lastSessionData = null;
                    next_lap_flush = 0;
                    lastDriverStatus = -1;
                    System.out.println("Session started cleanly: " + session_id);
                }
            }

            case EventSessionEnded endedSession -> {
                synchronized (stateLock) {
                    if (lastSessionHistory == null) return;
                    System.out.println("Session ended");
                    short current_lap_number = lastSessionHistory.m_numLaps();

                    // Flush lap N-1 and N (last lap + current (un)finished lap)
                    for (short l = (short) (current_lap_number - 1); l <= current_lap_number; l++) {
                        flushLap(lastSessionHistory, l);
                    }

                    session_id = 0;
                    telemetryData.clear();
                    frameBuffer.clear();
                    next_lap_flush = 0;
                    lastDriverStatus = -1;
                    lastSessionData = null;
                }
            }

            case EventRewind rewind -> {
                synchronized (stateLock) {
                    if (telemetryData.isEmpty()) return;

                    long rewindedFrame = rewind.frameIdentifier();
                    short current_lap = telemetryData.lastKey();


                    ConcurrentSkipListMap<Long, TelemetrySample> currentLapFrames = telemetryData.get(current_lap);
                    if (currentLapFrames != null) {
                        Long lapFirstFrame = currentLapFrames.firstKey();

                        // Check current and previous lap since rewind cannot go further back
                        if (lapFirstFrame > rewindedFrame) {
                            ConcurrentSkipListMap<Long, TelemetrySample> previousLapFrames = telemetryData.get(current_lap - 1);
                            if (previousLapFrames != null) {
                                previousLapFrames.tailMap(rewindedFrame).clear();
                            }
                        }

                        currentLapFrames.tailMap(rewindedFrame).clear();
                    }

                    frameBuffer.tailMap(rewindedFrame).clear();
                }
            }

            default -> {
                return;
            }
        }
    }

    @ServiceActivator(inputChannel = "lapDataChanel")
    public void processLapData(byte[] payload) {
        if (session_id == 0) return;

        F1Header header = telemetryParser.parseHeader(payload);
        F1LapData lapDataPacket = telemetryParser.parseLapData(payload, header.m_playerCarIndex());

        synchronized (stateLock) {
            lastReceivedFrame = header.m_frameIdentifier();
        }

        pairFrame(header.m_frameIdentifier(), lapDataPacket, null);

        short driverStatus = lapDataPacket.driverStatus();

        // Flush laps N-1 and N (last lap + in lap) the instant the car returns to the garage
        // (pit stop or end of session) - only on the transition from on-track (1-4) to garage (0),
        // never while already sitting in the garage (e.g. before ever leaving it at session start).
        if (driverStatus == 0 && lastDriverStatus > 0) {
            if (lastSessionHistory != null) {
                short current_lap_number = lastSessionHistory.m_numLaps();

                // Flush lap N-1 and N
                for (short l = (short) (current_lap_number - 1); l <= current_lap_number; l++) {
                    flushLap(lastSessionHistory, l);
                }
            }
        }

        lastDriverStatus = driverStatus;
    }

    @ServiceActivator(inputChannel = "telemetryChanel")
    public void processTelemetryData(byte[] payload) {
        if (session_id == 0) return;

        F1Header header = telemetryParser.parseHeader(payload);
        F1CarTelemetry carTelemetryPacket = telemetryParser.parseCarTelemetry(payload, header.m_playerCarIndex());

        synchronized (stateLock) {
            lastReceivedFrame = header.m_frameIdentifier();
        }

        pairFrame(header.m_frameIdentifier(), null, carTelemetryPacket);
    }

    @ServiceActivator(inputChannel = "sessionChanel")
    public void processSessionData(byte[] payload) {
        if (session_id == 0) return;
        lastSessionData = telemetryParser.parseSessionData(payload);
    }

    // Used for determining when a lap finishes and flushing the data to the DB
    @ServiceActivator(inputChannel = "sessionHistoryChanel")
    public void processSesionHistory(byte[] payload) {
        if (session_id == 0) return;

        F1Header header = telemetryParser.parseHeader(payload);
        F1SessionHistory sessionHistory = telemetryParser.parseSessionHistory(payload);
        if (header.m_playerCarIndex() != sessionHistory.m_carIdx()) return; // Only get data from current user

        lastSessionHistory = sessionHistory;

        short current_lap_number = sessionHistory.m_numLaps();

        synchronized (stateLock) {
            if (next_lap_flush == 0) {
                next_lap_flush = (short) Math.max(1, current_lap_number);
            }

            if ((current_lap_number - next_lap_flush) >= 2) {
                System.out.println("Flush lap N-1");
                flushLap(sessionHistory, next_lap_flush);
            }
        }
    }

    private void pairFrame(long frame, F1LapData lap, F1CarTelemetry telemetry) {
        FrameEntry entry = frameBuffer.computeIfAbsent(frame, f -> new FrameEntry());

        F1LapData completedLap;
        F1CarTelemetry completedTelemetry;
        synchronized (entry) {
            if (lap != null) entry.lap = lap;
            if (telemetry != null) entry.telemetry = telemetry;
            if (entry.lap == null || entry.telemetry == null) return;
            completedLap = entry.lap;
            completedTelemetry = entry.telemetry;
        }
        frameBuffer.remove(frame);

        if (completedLap.currentLapNum() == 0) return;

        // m_lapDistance is negative until the car has actually crossed the start/finish line
        // (garage idle, out-lap tail) - never attribute that pre-line window to a stored lap.
        if (completedLap.lapDistance() < 0) return;

        TelemetrySample sample = new TelemetrySample(frame, completedTelemetry, completedLap);
        telemetryData.computeIfAbsent(completedLap.currentLapNum(), k -> new ConcurrentSkipListMap<>())
                .computeIfAbsent(frame, f -> sample);
    }

    private void flushLap(F1SessionHistory sessionHistory, short lap_number) {
        if (lap_number <= 0) return;

        synchronized (stateLock) {
            ConcurrentSkipListMap<Long, TelemetrySample> telemetryToFlush = telemetryData.remove(lap_number);
            LapHistory lapHistory = sessionHistory.lapHistoryData().get(lap_number - 1); // -1 for array index
            if (telemetryToFlush == null || telemetryToFlush.isEmpty() || lapHistory == null) return;

            // Stored data must not come from garage, and out laps
            TelemetrySample lastTelemetrySample = telemetryToFlush.lastEntry().getValue();
            short driverStatus = lastTelemetrySample.lapData().driverStatus();
            if (driverStatus != 0 || (lapHistory.m_sector1TimeMSPart() == 0 && lapHistory.m_sector2TimeMSPart() == 0)) {
                F1SessionData sessionData = lastSessionData;
                databaseFlushService.flushTelemetryDataAsync(session_id, sessionData, lap_number, lapHistory, telemetryToFlush);
            } else {
                System.out.println("Discarding incomplete Lap: " + lap_number);
            }

            // Delete all buffers with frames that are lower than or equal to the last frame of the lap
            frameBuffer.headMap(lastTelemetrySample.frame(), true).clear();

            next_lap_flush = (short) (lap_number + 1);
        }
    }
}