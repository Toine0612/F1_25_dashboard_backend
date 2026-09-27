package com.toine.example.demo.service;

import com.toine.example.demo.models.dto.event.EventFlashback;
import com.toine.example.demo.models.dto.event.EventSessionEnded;
import com.toine.example.demo.models.dto.packets.*;
import com.toine.example.demo.models.dto.sessionHistory.LapHistory;
import com.toine.example.demo.models.dto.sessionHistory.TyreStintHistory;
import com.toine.example.demo.support.F1PacketWriter;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TelemetryParserTest {

    private static final int PLAYER = 3;
    private static final long SESSION_UID = 0xF123_4567_89AB_CDEFL; // above Long.MAX_VALUE as unsigned

    private final TelemetryParser parser = new TelemetryParser();
    private final F1PacketWriter writer = new F1PacketWriter(SESSION_UID, PLAYER);

    @Test
    void headerFieldsAreReadAtSpecOffsets() {
        byte[] packet = writer.lapData(3_000_000_000L, 12.5f, lapData(1500f, 3, 0));

        F1Header header = parser.parseHeader(packet);

        assertThat(header.packetFormat()).isEqualTo(2025);
        assertThat(header.gameYear()).isEqualTo(25);
        assertThat(header.packetId()).isEqualTo(PacketId.LAP_DATA.id());
        assertThat(header.sessionUid()).isEqualTo(SESSION_UID);
        assertThat(header.sessionTime()).isEqualTo(12.5f);
        assertThat(header.frameIdentifier()).isEqualTo(3_000_000_000L); // uint32 above Integer.MAX_VALUE
        assertThat(header.playerCarIndex()).isEqualTo(PLAYER);
        assertThat(header.secondaryPlayerCarIndex()).isEqualTo(255);
    }

    @Test
    void lapDataOfThePlayerCarIsReadFromItsSlot() {
        F1LapData expected = lapData(1234.5f, 7, 4);
        byte[] packet = writer.lapData(1, 0, expected);

        // Spot-check absolute offsets straight from the spec: header 29 + 57 bytes per car
        ByteBuffer raw = ByteBuffer.wrap(packet).order(ByteOrder.LITTLE_ENDIAN);
        int car = 29 + 57 * PLAYER;
        assertThat(packet).hasSize(1285);
        assertThat(raw.getFloat(car + 20)).isEqualTo(1234.5f);   // m_lapDistance
        assertThat(raw.get(car + 33)).isEqualTo((byte) 7);       // m_currentLapNum
        assertThat(raw.get(car + 44)).isEqualTo((byte) 4);       // m_driverStatus

        assertThat(parser.parseLapData(packet, PLAYER)).isEqualTo(expected);
        assertThat(parser.parseLapData(packet, 0).currentLapNum()).isZero();
    }

    @Test
    void sectorTimesIncludeTheirMinutesPart() {
        F1LapData lap = new F1LapData(0, 0, 5_000, 1, 59_999, 0, 0, 0, 0, 0, 0f, 0f, 0f,
                1, 1, 0, 0, 2, 0, 0, 0, 0, 0, 0, 1, 1, 2, 0, 0, 0, 0, 0f, 255);

        F1LapData parsed = parser.parseLapData(writer.lapData(1, 0, lap), PLAYER);

        assertThat(parsed.sector1TimeMs()).isEqualTo(65_000);
        assertThat(parsed.sector2TimeMs()).isEqualTo(59_999);
    }

    @Test
    void carTelemetryKeepsSignedGearAndWheelOrder() {
        F1CarTelemetry expected = new F1CarTelemetry(312, 0.75f, -0.25f, 0.5f, 0, -1, 11_500, 1, 80, 0x7FFF,
                new int[]{700, 710, 900, 910}, new int[]{101, 102, 140, 141}, new int[]{95, 96, 97, 98},
                110, new float[]{22.1f, 22.2f, 23.3f, 23.4f}, new int[]{0, 1, 4, 7});
        byte[] packet = writer.carTelemetry(1, 0, expected);

        ByteBuffer raw = ByteBuffer.wrap(packet).order(ByteOrder.LITTLE_ENDIAN);
        int car = 29 + 60 * PLAYER;
        assertThat(packet).hasSize(1352);
        assertThat(raw.getShort(car)).isEqualTo((short) 312);   // m_speed
        assertThat(raw.getFloat(car + 2)).isEqualTo(0.75f);     // m_throttle
        assertThat(raw.get(car + 15)).isEqualTo((byte) -1);     // m_gear

        F1CarTelemetry parsed = parser.parseCarTelemetry(packet, PLAYER);
        assertThat(parsed).usingRecursiveComparison().isEqualTo(expected);
    }

    @Test
    void motionPositionIsReadFromThePlayerSlot() {
        F1CarMotion expected = new F1CarMotion(-512.25f, 8.5f, 1024.75f, 80f, 0f, -12f,
                1f, 0f, 0f, 0f, 0f, -1f, 1.5f, -4f, 1f, 0.5f, 0.01f, -0.02f);
        byte[] packet = writer.motion(1, 0, expected);

        ByteBuffer raw = ByteBuffer.wrap(packet).order(ByteOrder.LITTLE_ENDIAN);
        assertThat(packet).hasSize(1349);
        assertThat(raw.getFloat(29 + 60 * PLAYER)).isEqualTo(-512.25f);      // m_worldPositionX
        assertThat(raw.getFloat(29 + 60 * PLAYER + 8)).isEqualTo(1024.75f);  // m_worldPositionZ

        assertThat(parser.parseCarMotion(packet, PLAYER)).isEqualTo(expected);
    }

    @Test
    void sessionFieldsAfterTheVariableLookingArraysAreAtTheirAbsoluteOffsets() {
        F1SessionData expected = new F1SessionData(1, -2, 18, 50, 5891, 18, -1, 0,
                4_000_000_000L, 77, 5, 1900.5f, 3800.25f);
        byte[] packet = writer.session(0, expected);

        ByteBuffer raw = ByteBuffer.wrap(packet).order(ByteOrder.LITTLE_ENDIAN);
        assertThat(packet).hasSize(753);
        assertThat(raw.getShort(33)).isEqualTo((short) 5891);   // m_trackLength
        assertThat(raw.get(36)).isEqualTo((byte) -1);           // m_trackId
        assertThat(raw.getFloat(749)).isEqualTo(3800.25f);      // m_sector3LapDistanceStart, the last field

        assertThat(parser.parseSessionData(packet)).isEqualTo(expected);
    }

    @Test
    void sessionHistoryReadsOnlyUsedLapsAndStintsAfterAllLapSlots() {
        List<LapHistory> laps = List.of(
                new LapHistory(95_123, 31_000, 65_500, 0, 0x0F),       // sector 2 over a minute
                new LapHistory(0, 30_500, 0, 0, 0));                   // current, partial lap
        byte[] packet = writer.sessionHistory(0, PLAYER, laps);

        F1SessionHistory parsed = parser.parseSessionHistory(packet);

        assertThat(packet).hasSize(1460);
        assertThat(parsed.carIdx()).isEqualTo(PLAYER);
        assertThat(parsed.numLaps()).isEqualTo(2);
        assertThat(parsed.lapHistoryData()).containsExactlyElementsOf(laps);
        assertThat(parsed.lap(1).lapValid()).isTrue();
        assertThat(parsed.lap(3)).isNull();
        assertThat(parsed.tyreStintsHistoryData()).containsExactly(new TyreStintHistory(255, 18, 17));
    }

    @Test
    void flashbackAndSessionEndEventsAreDecoded() {
        F1EventData flashback = parser.parseEventData(writer.flashbackEvent(900, 30f, 600, 25f));
        F1EventData ended = parser.parseEventData(writer.event(900, 30f, "SEND"));
        F1EventData ignored = parser.parseEventData(writer.event(900, 30f, "SPTP"));

        assertThat(flashback.eventDetails()).isEqualTo(new EventFlashback(600, 25f));
        assertThat(ended.eventDetails()).isInstanceOf(EventSessionEnded.class);
        assertThat(ignored.eventCode()).isEqualTo("SPTP");
        assertThat(ignored.eventDetails()).isNull();
    }

    @Test
    void validateRejectsPacketsThatCannotBeDecoded() {
        byte[] lapData = writer.lapData(1, 0, lapData(10f, 1, 1));
        byte[] olderFormat = lapData.clone();
        ByteBuffer.wrap(olderFormat).order(ByteOrder.LITTLE_ENDIAN).putShort(0, (short) 2024);
        byte[] unknownType = new byte[40];
        ByteBuffer.wrap(unknownType).order(ByteOrder.LITTLE_ENDIAN).putShort(0, (short) 2025).put(6, (byte) 4);

        assertThat(parser.validate(lapData)).isNull();
        assertThat(parser.validate(unknownType)).isNull();
        assertThat(parser.validate(new byte[10])).contains("header");
        assertThat(parser.validate(olderFormat)).contains("UDP format 2024");
        assertThat(parser.validate(java.util.Arrays.copyOf(lapData, 1000))).contains("LAP_DATA packet is 1000 bytes");
    }

    private static F1LapData lapData(float lapDistance, int lapNumber, int driverStatus) {
        return new F1LapData(88_000, 42_123, 30_100, 0, 29_900, 0, 1_200, 0, 5_400, 0,
                lapDistance, lapDistance + 10_000, 0f, 2, lapNumber, 0, 0, 1, 0, 0, 1, 1, 0, 0, 5,
                driverStatus, 2, 0, 0, 0, 0, 301.5f, 2);
    }
}
