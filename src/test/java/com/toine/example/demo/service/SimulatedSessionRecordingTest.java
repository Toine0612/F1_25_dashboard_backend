package com.toine.example.demo.service;

import com.toine.example.demo.models.Channel;
import com.toine.example.demo.models.dto.packets.F1Header;
import com.toine.example.demo.models.dto.packets.F1SessionHistory;
import com.toine.example.demo.models.dto.packets.PacketId;
import com.toine.example.demo.service.recording.CompletedLap;
import com.toine.example.demo.service.recording.LapSink;
import com.toine.example.demo.service.recording.LapTiming;
import com.toine.example.demo.service.recording.RecordedSession;
import com.toine.example.demo.support.SimulatedSession;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/** Plays scripted sessions packet by packet through the real parser, routing and recorder. */
class SimulatedSessionRecordingTest {

    private static final int HZ = 20;

    private final TelemetryParser parser = new TelemetryParser();
    private final CapturingSink sink = new CapturingSink();
    private final TelemetryService service = new TelemetryService(parser, sink);
    private F1SessionHistory lastPlayerHistory;

    @Test
    void timeTrialWithFlashbacksRecordsEveryFlyingLapOnce() {
        SimulatedSession session = SimulatedSession.timeTrial(1001, 7, HZ);
        session.play((packet, time) -> deliver(packet));

        assertThat(sink.laps.keySet()).containsExactly(1, 2, 3);
        // The flashback right after the line of lap 3 rewinds into lap 3, which is recorded again
        assertThat(sink.events).containsSubsequence("completed 3", "retracted 3", "completed 3");
        assertLapsMatchTheGame(session);
    }

    @Test
    void practiceSkipsTheOutLapAndKeepsTheInvalidLap() {
        SimulatedSession session = SimulatedSession.practice(1002, 11, 7, HZ);
        session.play((packet, time) -> deliver(packet));

        assertThat(sink.laps.keySet()).containsExactlyElementsOf(session.completeLapNumbers()).containsExactly(2, 3, 4);
        assertThat(sink.laps.get(4).timing().valid()).isFalse();
        assertThat(sink.laps.get(3).timing().valid()).isTrue();
        assertLapsMatchTheGame(session);
    }

    @Test
    void raceRecordsEveryLap() {
        SimulatedSession session = SimulatedSession.race(1003, 11, 7, HZ);
        session.play((packet, time) -> deliver(packet));

        assertThat(sink.laps.keySet()).containsExactly(1, 2, 3, 4);
        assertLapsMatchTheGame(session);
    }

    private void assertLapsMatchTheGame(SimulatedSession session) {
        double trackLength = session.circuit().length();
        for (CompletedLap lap : sink.laps.values()) {
            LapTiming official = LapTiming.class.cast(sink.laps.get(lap.lapNumber()).timing());
            var history = lastPlayerHistory.lap(lap.lapNumber());
            assertThat(official.lapTimeMs()).as("lap %d time", lap.lapNumber()).isEqualTo((int) history.lapTimeInMS());
            assertThat(official.sector1Ms()).isEqualTo(history.sector1TimeMs());

            float[] distance = lap.channels().get(Channel.DISTANCE);
            float[] time = lap.channels().get(Channel.TIME);
            assertThat(distance[0]).as("lap %d starts at the line", lap.lapNumber()).isLessThan(100);
            assertThat(distance[distance.length - 1]).isGreaterThan((float) trackLength - 100);
            for (int i = 1; i < distance.length; i++) {
                assertThat(distance[i]).isGreaterThan(distance[i - 1]);
            }
            // One sample per frame: the lap's duration times the send rate
            assertThat((double) lap.sampleCount()).isCloseTo(official.lapTimeMs() / 1000.0 * HZ, within(3.0));
            assertThat(time[time.length - 1]).isCloseTo(official.lapTimeMs(), within(1000f / HZ + 1));
            for (Channel channel : Channel.values()) {
                assertThat(lap.channels().get(channel)).hasSize(lap.sampleCount());
            }
        }
    }

    private void deliver(byte[] packet) {
        if (!service.accept(packet)) return;
        F1Header header = parser.parseHeader(packet);
        switch (PacketId.of(header.packetId())) {
            case MOTION -> service.onMotion(header, packet);
            case SESSION -> service.onSession(header, packet);
            case LAP_DATA -> service.onLapData(header, packet);
            case EVENT -> service.onEvent(header, packet);
            case CAR_TELEMETRY -> service.onCarTelemetry(header, packet);
            case SESSION_HISTORY -> {
                F1SessionHistory history = parser.parseSessionHistory(packet);
                if (history.carIdx() == header.playerCarIndex()) lastPlayerHistory = history;
                service.onSessionHistory(header, packet);
            }
            case null -> { }
        }
    }

    private static final class CapturingSink implements LapSink {
        final Map<Integer, CompletedLap> laps = new LinkedHashMap<>();
        final List<String> events = new ArrayList<>();

        @Override
        public void lapCompleted(RecordedSession session, CompletedLap lap) {
            laps.put(lap.lapNumber(), lap);
            events.add("completed " + lap.lapNumber());
        }

        @Override
        public void lapRetracted(long sessionUid, int lapNumber) {
            laps.remove(lapNumber);
            events.add("retracted " + lapNumber);
        }

        @Override
        public void lapTimingCorrected(long sessionUid, int lapNumber, LapTiming timing) {
            CompletedLap lap = laps.get(lapNumber);
            laps.put(lapNumber, new CompletedLap(lapNumber, timing, lap.pitIn(), lap.pitOut(), lap.sampleCount(), lap.channels()));
            events.add("corrected " + lapNumber);
        }
    }
}
