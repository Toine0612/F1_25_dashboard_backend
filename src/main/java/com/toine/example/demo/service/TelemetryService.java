package com.toine.example.demo.service;

import com.toine.example.demo.models.dto.event.EventFlashback;
import com.toine.example.demo.models.dto.event.EventSessionEnded;
import com.toine.example.demo.models.dto.event.EventSessionStarted;
import com.toine.example.demo.models.dto.packets.F1EventData;
import com.toine.example.demo.models.dto.packets.F1Header;
import com.toine.example.demo.models.dto.packets.F1SessionHistory;
import com.toine.example.demo.models.SessionUids;
import com.toine.example.demo.service.recording.FrameAssembler;
import com.toine.example.demo.service.recording.FrameSample;
import com.toine.example.demo.service.recording.LapRecorder;
import com.toine.example.demo.service.recording.LapSink;
import com.toine.example.demo.service.recording.RecorderStatus;
import com.toine.example.demo.service.recording.SessionInfo;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.integration.annotation.ServiceActivator;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Entry point for decoded UDP packets (see UDPConfig): picks out the player's car, joins the per-frame
 * packets and feeds the {@link LapRecorder}. Packets arrive one at a time, in the order received.
 */
@Service
public class TelemetryService {

    /** Message header carrying the already-decoded {@link F1Header}. */
    public static final String F1_HEADER = "f1Header";

    private static final Logger log = LoggerFactory.getLogger(TelemetryService.class);

    private final TelemetryParser parser;
    private final LapRecorder recorder;
    private final FrameAssembler frames = new FrameAssembler();

    private final AtomicLong packetsReceived = new AtomicLong();
    private final AtomicLong packetsRejected = new AtomicLong();
    private final Set<String> reportedRejections = ConcurrentHashMap.newKeySet();
    private volatile Instant lastPacketAt;

    public TelemetryService(TelemetryParser parser, LapSink lapSink) {
        this.parser = parser;
        this.recorder = new LapRecorder(lapSink, Clock.systemUTC());
    }

    /** Filters out packets that can't be decoded; each distinct problem is logged once. */
    public boolean accept(byte[] payload) {
        if (packetsReceived.getAndIncrement() == 0) {
            log.info("UDP telemetry is arriving ({} byte first packet)", payload.length);
        }
        lastPacketAt = Instant.now();

        String problem = parser.validate(payload);
        if (problem == null) return true;

        packetsRejected.incrementAndGet();
        if (reportedRejections.add(problem)) {
            log.warn("Ignoring UDP packets: {}", problem);
        }
        return false;
    }

    @ServiceActivator(inputChannel = "motionChannel")
    public void onMotion(@Header(F1_HEADER) F1Header header, byte[] payload) {
        if (!hasPlayerCar(header)) return;
        record(header, frames.add(header.frameIdentifier(), parser.parseCarMotion(payload, header.playerCarIndex())));
    }

    @ServiceActivator(inputChannel = "lapDataChannel")
    public void onLapData(@Header(F1_HEADER) F1Header header, byte[] payload) {
        if (!hasPlayerCar(header)) return;
        record(header, frames.add(header.frameIdentifier(), parser.parseLapData(payload, header.playerCarIndex())));
    }

    @ServiceActivator(inputChannel = "carTelemetryChannel")
    public void onCarTelemetry(@Header(F1_HEADER) F1Header header, byte[] payload) {
        if (!hasPlayerCar(header)) return;
        record(header, frames.add(header.frameIdentifier(), parser.parseCarTelemetry(payload, header.playerCarIndex())));
    }

    @ServiceActivator(inputChannel = "sessionChannel")
    public void onSession(@Header(F1_HEADER) F1Header header, byte[] payload) {
        recorder.onSessionInfo(header.sessionUid(), SessionInfo.from(parser.parseSessionData(payload)));
    }

    @ServiceActivator(inputChannel = "sessionHistoryChannel")
    public void onSessionHistory(@Header(F1_HEADER) F1Header header, byte[] payload) {
        F1SessionHistory history = parser.parseSessionHistory(payload);
        // The game cycles through every car's history; only the player's matters here.
        if (history.carIdx() == header.playerCarIndex()) {
            recorder.onSessionHistory(header.sessionUid(), history);
        }
    }

    @ServiceActivator(inputChannel = "eventChannel")
    public void onEvent(@Header(F1_HEADER) F1Header header, byte[] payload) {
        F1EventData event = parser.parseEventData(payload);
        switch (event.eventDetails()) {
            case EventSessionStarted started -> log.info("Session {} started", SessionUids.format(header.sessionUid()));
            case EventSessionEnded ended -> {
                log.info("Session {} ended", SessionUids.format(header.sessionUid()));
                recorder.onSessionEnded(header.sessionUid());
            }
            // Nothing to do: the frame identifier goes back after a flashback, which the recorder
            // detects on the next frame - that also works if this event packet gets lost.
            case EventFlashback flashback -> log.debug("Flashback to frame {}", flashback.frameIdentifier());
            case null -> { }
        }
    }

    public RecorderStatus recorderStatus() {
        return recorder.status();
    }

    public Instant lastPacketAt() {
        return lastPacketAt;
    }

    public long packetsReceived() {
        return packetsReceived.get();
    }

    public long packetsRejected() {
        return packetsRejected.get();
    }

    @PreDestroy
    void shutdown() {
        recorder.close();
    }

    private void record(F1Header header, Optional<FrameSample> sample) {
        sample.ifPresent(s -> recorder.onSample(header.sessionUid(), s));
    }

    /** 255 while spectating: there is no player car to record. */
    private static boolean hasPlayerCar(F1Header header) {
        return header.playerCarIndex() < TelemetryParser.MAX_CARS;
    }
}
