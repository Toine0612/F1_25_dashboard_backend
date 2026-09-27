package com.toine.example.demo.service;

import com.toine.example.demo.models.dto.event.EventDetails;
import com.toine.example.demo.models.dto.event.EventFlashback;
import com.toine.example.demo.models.dto.event.EventSessionEnded;
import com.toine.example.demo.models.dto.event.EventSessionStarted;
import com.toine.example.demo.models.dto.packets.*;
import com.toine.example.demo.models.dto.sessionHistory.LapHistory;
import com.toine.example.demo.models.dto.sessionHistory.TyreStintHistory;
import org.springframework.stereotype.Service;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Decodes F1 25 UDP packets ("Data Output from F1 25 v3.pdf"). All values are little-endian and
 * packed. Every struct is located by its absolute offset in the packet and then read field by field
 * in spec order, so each method can be checked line by line against the spec.
 */
@Service
public class TelemetryParser {

    public static final int PACKET_FORMAT = 2025;
    public static final int HEADER_SIZE = 29;
    public static final int MAX_CARS = 22;

    static final int CAR_MOTION_SIZE = 60;
    static final int LAP_DATA_SIZE = 57;
    static final int CAR_TELEMETRY_SIZE = 60;
    static final int LAP_HISTORY_SIZE = 14;
    static final int MAX_LAP_HISTORY = 100;
    static final int MAX_TYRE_STINTS = 8;

    // Absolute offsets inside the Session packet (everything before them is fixed-size).
    private static final int SESSION_WEEKEND_LINK_ID = 674;
    private static final int SESSION_SESSION_LINK_ID = 678;
    private static final int SESSION_GAME_MODE = 694;
    private static final int SESSION_SECTOR2_START = 745;
    private static final int SESSION_SECTOR3_START = 749;

    // Session History: the tyre stints follow ALL 100 lap slots, not just the m_numLaps used ones.
    private static final int SESSION_HISTORY_LAPS = HEADER_SIZE + 7;
    private static final int SESSION_HISTORY_STINTS = SESSION_HISTORY_LAPS + MAX_LAP_HISTORY * LAP_HISTORY_SIZE;

    /**
     * @return why the packet can't be decoded, or {@code null} if it can. Packet types this backend
     * doesn't decode only need a valid header.
     */
    public String validate(byte[] payload) {
        if (payload.length < HEADER_SIZE) {
            return "shorter than the " + HEADER_SIZE + "-byte header";
        }
        int packetFormat = Short.toUnsignedInt(buffer(payload).getShort(0));
        if (packetFormat != PACKET_FORMAT) {
            return "UDP format " + packetFormat + " is not supported - set 'UDP Format' to " + PACKET_FORMAT
                    + " in the game's telemetry settings";
        }
        PacketId packetId = PacketId.of(Byte.toUnsignedInt(payload[6]));
        if (packetId != null && payload.length < packetId.size()) {
            return packetId + " packet is " + payload.length + " bytes, expected " + packetId.size();
        }
        return null;
    }

    public F1Header parseHeader(byte[] payload) {
        ByteBuffer buffer = buffer(payload);

        return new F1Header(
                Short.toUnsignedInt(buffer.getShort()),     // m_packetFormat
                Byte.toUnsignedInt(buffer.get()),           // m_gameYear
                Byte.toUnsignedInt(buffer.get()),           // m_gameMajorVersion
                Byte.toUnsignedInt(buffer.get()),           // m_gameMinorVersion
                Byte.toUnsignedInt(buffer.get()),           // m_packetVersion
                Byte.toUnsignedInt(buffer.get()),           // m_packetId
                buffer.getLong(),                           // m_sessionUID
                buffer.getFloat(),                          // m_sessionTime
                Integer.toUnsignedLong(buffer.getInt()),    // m_frameIdentifier
                Integer.toUnsignedLong(buffer.getInt()),    // m_overallFrameIdentifier
                Byte.toUnsignedInt(buffer.get()),           // m_playerCarIndex
                Byte.toUnsignedInt(buffer.get())            // m_secondaryPlayerCarIndex
        );
    }

    public F1CarMotion parseCarMotion(byte[] payload, int carIndex) {
        ByteBuffer buffer = buffer(payload).position(HEADER_SIZE + CAR_MOTION_SIZE * carIndex);

        return new F1CarMotion(
                buffer.getFloat(),                          // m_worldPositionX
                buffer.getFloat(),                          // m_worldPositionY
                buffer.getFloat(),                          // m_worldPositionZ
                buffer.getFloat(),                          // m_worldVelocityX
                buffer.getFloat(),                          // m_worldVelocityY
                buffer.getFloat(),                          // m_worldVelocityZ
                normalised(buffer.getShort()),              // m_worldForwardDirX
                normalised(buffer.getShort()),              // m_worldForwardDirY
                normalised(buffer.getShort()),              // m_worldForwardDirZ
                normalised(buffer.getShort()),              // m_worldRightDirX
                normalised(buffer.getShort()),              // m_worldRightDirY
                normalised(buffer.getShort()),              // m_worldRightDirZ
                buffer.getFloat(),                          // m_gForceLateral
                buffer.getFloat(),                          // m_gForceLongitudinal
                buffer.getFloat(),                          // m_gForceVertical
                buffer.getFloat(),                          // m_yaw
                buffer.getFloat(),                          // m_pitch
                buffer.getFloat()                           // m_roll
        );
    }

    public F1LapData parseLapData(byte[] payload, int carIndex) {
        ByteBuffer buffer = buffer(payload).position(HEADER_SIZE + LAP_DATA_SIZE * carIndex);

        return new F1LapData(
                Integer.toUnsignedLong(buffer.getInt()),    // m_lastLapTimeInMS
                Integer.toUnsignedLong(buffer.getInt()),    // m_currentLapTimeInMS
                Short.toUnsignedInt(buffer.getShort()),     // m_sector1TimeMSPart
                Byte.toUnsignedInt(buffer.get()),           // m_sector1TimeMinutesPart
                Short.toUnsignedInt(buffer.getShort()),     // m_sector2TimeMSPart
                Byte.toUnsignedInt(buffer.get()),           // m_sector2TimeMinutesPart
                Short.toUnsignedInt(buffer.getShort()),     // m_deltaToCarInFrontMSPart
                Byte.toUnsignedInt(buffer.get()),           // m_deltaToCarInFrontMinutesPart
                Short.toUnsignedInt(buffer.getShort()),     // m_deltaToRaceLeaderMSPart
                Byte.toUnsignedInt(buffer.get()),           // m_deltaToRaceLeaderMinutesPart
                buffer.getFloat(),                          // m_lapDistance
                buffer.getFloat(),                          // m_totalDistance
                buffer.getFloat(),                          // m_safetyCarDelta
                Byte.toUnsignedInt(buffer.get()),           // m_carPosition
                Byte.toUnsignedInt(buffer.get()),           // m_currentLapNum
                Byte.toUnsignedInt(buffer.get()),           // m_pitStatus
                Byte.toUnsignedInt(buffer.get()),           // m_numPitStops
                Byte.toUnsignedInt(buffer.get()),           // m_sector
                Byte.toUnsignedInt(buffer.get()),           // m_currentLapInvalid
                Byte.toUnsignedInt(buffer.get()),           // m_penalties
                Byte.toUnsignedInt(buffer.get()),           // m_totalWarnings
                Byte.toUnsignedInt(buffer.get()),           // m_cornerCuttingWarnings
                Byte.toUnsignedInt(buffer.get()),           // m_numUnservedDriveThroughPens
                Byte.toUnsignedInt(buffer.get()),           // m_numUnservedStopGoPens
                Byte.toUnsignedInt(buffer.get()),           // m_gridPosition
                Byte.toUnsignedInt(buffer.get()),           // m_driverStatus
                Byte.toUnsignedInt(buffer.get()),           // m_resultStatus
                Byte.toUnsignedInt(buffer.get()),           // m_pitLaneTimerActive
                Short.toUnsignedInt(buffer.getShort()),     // m_pitLaneTimeInLaneInMS
                Short.toUnsignedInt(buffer.getShort()),     // m_pitStopTimerInMS
                Byte.toUnsignedInt(buffer.get()),           // m_pitStopShouldServePen
                buffer.getFloat(),                          // m_speedTrapFastestSpeed
                Byte.toUnsignedInt(buffer.get())            // m_speedTrapFastestLap
        );
    }

    public F1CarTelemetry parseCarTelemetry(byte[] payload, int carIndex) {
        ByteBuffer buffer = buffer(payload).position(HEADER_SIZE + CAR_TELEMETRY_SIZE * carIndex);

        return new F1CarTelemetry(
                Short.toUnsignedInt(buffer.getShort()),     // m_speed
                buffer.getFloat(),                          // m_throttle
                buffer.getFloat(),                          // m_steer
                buffer.getFloat(),                          // m_brake
                Byte.toUnsignedInt(buffer.get()),           // m_clutch
                buffer.get(),                               // m_gear (int8: R = -1)
                Short.toUnsignedInt(buffer.getShort()),     // m_engineRPM
                Byte.toUnsignedInt(buffer.get()),           // m_drs
                Byte.toUnsignedInt(buffer.get()),           // m_revLightsPercent
                Short.toUnsignedInt(buffer.getShort()),     // m_revLightsBitValue
                unsignedShorts(buffer, 4),                  // m_brakesTemperature[4]
                unsignedBytes(buffer, 4),                   // m_tyresSurfaceTemperature[4]
                unsignedBytes(buffer, 4),                   // m_tyresInnerTemperature[4]
                Short.toUnsignedInt(buffer.getShort()),     // m_engineTemperature
                floats(buffer, 4),                          // m_tyresPressure[4]
                unsignedBytes(buffer, 4)                    // m_surfaceType[4]
        );
    }

    public F1SessionData parseSessionData(byte[] payload) {
        ByteBuffer buffer = buffer(payload).position(HEADER_SIZE);

        int weather = Byte.toUnsignedInt(buffer.get());     // m_weather
        int trackTemperature = buffer.get();                // m_trackTemperature (int8)
        int airTemperature = buffer.get();                  // m_airTemperature (int8)
        int totalLaps = Byte.toUnsignedInt(buffer.get());   // m_totalLaps
        int trackLength = Short.toUnsignedInt(buffer.getShort()); // m_trackLength
        int sessionType = Byte.toUnsignedInt(buffer.get()); // m_sessionType
        int trackId = buffer.get();                         // m_trackId (int8: -1 = unknown)
        int formula = Byte.toUnsignedInt(buffer.get());     // m_formula

        return new F1SessionData(
                weather, trackTemperature, airTemperature, totalLaps, trackLength, sessionType, trackId, formula,
                Integer.toUnsignedLong(buffer.getInt(SESSION_WEEKEND_LINK_ID)),  // m_weekendLinkIdentifier
                Integer.toUnsignedLong(buffer.getInt(SESSION_SESSION_LINK_ID)),  // m_sessionLinkIdentifier
                Byte.toUnsignedInt(buffer.get(SESSION_GAME_MODE)),               // m_gameMode
                buffer.getFloat(SESSION_SECTOR2_START),                          // m_sector2LapDistanceStart
                buffer.getFloat(SESSION_SECTOR3_START)                           // m_sector3LapDistanceStart
        );
    }

    public F1EventData parseEventData(byte[] payload) {
        ByteBuffer buffer = buffer(payload).position(HEADER_SIZE);

        byte[] eventCodeBytes = new byte[4];
        buffer.get(eventCodeBytes);
        String eventCode = new String(eventCodeBytes, StandardCharsets.US_ASCII);

        EventDetails details = switch (eventCode) {
            case "SSTA" -> new EventSessionStarted();
            case "SEND" -> new EventSessionEnded();
            case "FLBK" -> new EventFlashback(
                    Integer.toUnsignedLong(buffer.getInt()), // flashbackFrameIdentifier
                    buffer.getFloat()                        // flashbackSessionTime
            );
            default -> null;
        };

        return new F1EventData(eventCode, details);
    }

    public F1SessionHistory parseSessionHistory(byte[] payload) {
        ByteBuffer buffer = buffer(payload).position(HEADER_SIZE);

        int carIdx = Byte.toUnsignedInt(buffer.get());
        int numLaps = Math.min(Byte.toUnsignedInt(buffer.get()), MAX_LAP_HISTORY);
        int numTyreStints = Math.min(Byte.toUnsignedInt(buffer.get()), MAX_TYRE_STINTS);
        int bestLapTimeLapNum = Byte.toUnsignedInt(buffer.get());
        int bestSector1LapNum = Byte.toUnsignedInt(buffer.get());
        int bestSector2LapNum = Byte.toUnsignedInt(buffer.get());
        int bestSector3LapNum = Byte.toUnsignedInt(buffer.get());

        List<LapHistory> laps = new ArrayList<>(numLaps);
        buffer.position(SESSION_HISTORY_LAPS);
        for (int i = 0; i < numLaps; i++) {
            long lapTimeInMS = Integer.toUnsignedLong(buffer.getInt());
            int sector1MsPart = Short.toUnsignedInt(buffer.getShort());
            int sector1Minutes = Byte.toUnsignedInt(buffer.get());
            int sector2MsPart = Short.toUnsignedInt(buffer.getShort());
            int sector2Minutes = Byte.toUnsignedInt(buffer.get());
            int sector3MsPart = Short.toUnsignedInt(buffer.getShort());
            int sector3Minutes = Byte.toUnsignedInt(buffer.get());
            int lapValidBitFlags = Byte.toUnsignedInt(buffer.get());
            laps.add(new LapHistory(lapTimeInMS,
                    sector1Minutes * 60_000 + sector1MsPart,
                    sector2Minutes * 60_000 + sector2MsPart,
                    sector3Minutes * 60_000 + sector3MsPart,
                    lapValidBitFlags));
        }

        List<TyreStintHistory> stints = new ArrayList<>(numTyreStints);
        buffer.position(SESSION_HISTORY_STINTS);
        for (int i = 0; i < numTyreStints; i++) {
            stints.add(new TyreStintHistory(
                    Byte.toUnsignedInt(buffer.get()),       // m_endLap
                    Byte.toUnsignedInt(buffer.get()),       // m_tyreActualCompound
                    Byte.toUnsignedInt(buffer.get())        // m_tyreVisualCompound
            ));
        }

        return new F1SessionHistory(carIdx, numLaps, numTyreStints, bestLapTimeLapNum,
                bestSector1LapNum, bestSector2LapNum, bestSector3LapNum, laps, stints);
    }

    private static ByteBuffer buffer(byte[] payload) {
        return ByteBuffer.wrap(payload).order(ByteOrder.LITTLE_ENDIAN);
    }

    private static float normalised(short value) {
        return value / 32767.0f;
    }

    private static int[] unsignedShorts(ByteBuffer buffer, int count) {
        int[] values = new int[count];
        for (int i = 0; i < count; i++) values[i] = Short.toUnsignedInt(buffer.getShort());
        return values;
    }

    private static int[] unsignedBytes(ByteBuffer buffer, int count) {
        int[] values = new int[count];
        for (int i = 0; i < count; i++) values[i] = Byte.toUnsignedInt(buffer.get());
        return values;
    }

    private static float[] floats(ByteBuffer buffer, int count) {
        float[] values = new float[count];
        for (int i = 0; i < count; i++) values[i] = buffer.getFloat();
        return values;
    }
}
