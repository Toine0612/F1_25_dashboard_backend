package com.toine.example.demo.support;

import com.toine.example.demo.models.dto.packets.F1CarMotion;
import com.toine.example.demo.models.dto.packets.F1CarTelemetry;
import com.toine.example.demo.models.dto.packets.F1LapData;
import com.toine.example.demo.models.dto.packets.F1SessionData;
import com.toine.example.demo.models.dto.packets.PacketId;
import com.toine.example.demo.models.dto.sessionHistory.LapHistory;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Encodes full-size F1 25 packets exactly as laid out in the UDP spec: every packet has its
 * documented size, every car slot is present (only the player's is filled in) and all values are
 * little-endian. Written independently from TelemetryParser so each can catch the other's mistakes.
 */
public final class F1PacketWriter {

    private static final int HEADER_SIZE = 29;

    private final long sessionUid;
    private final int playerCarIndex;

    public F1PacketWriter(long sessionUid, int playerCarIndex) {
        this.sessionUid = sessionUid;
        this.playerCarIndex = playerCarIndex;
    }

    public byte[] motion(long frame, float sessionTime, F1CarMotion motion) {
        ByteBuffer buffer = packet(PacketId.MOTION, frame, sessionTime);
        buffer.position(HEADER_SIZE + 60 * playerCarIndex);
        buffer.putFloat(motion.worldPositionX());
        buffer.putFloat(motion.worldPositionY());
        buffer.putFloat(motion.worldPositionZ());
        buffer.putFloat(motion.worldVelocityX());
        buffer.putFloat(motion.worldVelocityY());
        buffer.putFloat(motion.worldVelocityZ());
        buffer.putShort(normalised(motion.worldForwardDirX()));
        buffer.putShort(normalised(motion.worldForwardDirY()));
        buffer.putShort(normalised(motion.worldForwardDirZ()));
        buffer.putShort(normalised(motion.worldRightDirX()));
        buffer.putShort(normalised(motion.worldRightDirY()));
        buffer.putShort(normalised(motion.worldRightDirZ()));
        buffer.putFloat(motion.gForceLateral());
        buffer.putFloat(motion.gForceLongitudinal());
        buffer.putFloat(motion.gForceVertical());
        buffer.putFloat(motion.yaw());
        buffer.putFloat(motion.pitch());
        buffer.putFloat(motion.roll());
        return buffer.array();
    }

    public byte[] lapData(long frame, float sessionTime, F1LapData lap) {
        ByteBuffer buffer = packet(PacketId.LAP_DATA, frame, sessionTime);
        buffer.position(HEADER_SIZE + 57 * playerCarIndex);
        buffer.putInt((int) lap.lastLapTimeInMS());
        buffer.putInt((int) lap.currentLapTimeInMS());
        buffer.putShort((short) lap.sector1TimeMSPart());
        buffer.put((byte) lap.sector1TimeMinutesPart());
        buffer.putShort((short) lap.sector2TimeMSPart());
        buffer.put((byte) lap.sector2TimeMinutesPart());
        buffer.putShort((short) lap.deltaToCarInFrontMSPart());
        buffer.put((byte) lap.deltaToCarInFrontMinutesPart());
        buffer.putShort((short) lap.deltaToRaceLeaderMSPart());
        buffer.put((byte) lap.deltaToRaceLeaderMinutesPart());
        buffer.putFloat(lap.lapDistance());
        buffer.putFloat(lap.totalDistance());
        buffer.putFloat(lap.safetyCarDelta());
        buffer.put((byte) lap.carPosition());
        buffer.put((byte) lap.currentLapNum());
        buffer.put((byte) lap.pitStatus());
        buffer.put((byte) lap.numPitStops());
        buffer.put((byte) lap.sector());
        buffer.put((byte) lap.currentLapInvalid());
        buffer.put((byte) lap.penalties());
        buffer.put((byte) lap.totalWarnings());
        buffer.put((byte) lap.cornerCuttingWarnings());
        buffer.put((byte) lap.numUnservedDriveThroughPens());
        buffer.put((byte) lap.numUnservedStopGoPens());
        buffer.put((byte) lap.gridPosition());
        buffer.put((byte) lap.driverStatus());
        buffer.put((byte) lap.resultStatus());
        buffer.put((byte) lap.pitLaneTimerActive());
        buffer.putShort((short) lap.pitLaneTimeInLaneInMS());
        buffer.putShort((short) lap.pitStopTimerInMS());
        buffer.put((byte) lap.pitStopShouldServePen());
        buffer.putFloat(lap.speedTrapFastestSpeed());
        buffer.put((byte) lap.speedTrapFastestLap());
        // After all 22 cars: m_timeTrialPBCarIdx, m_timeTrialRivalCarIdx (255 = invalid)
        buffer.position(HEADER_SIZE + 57 * 22);
        buffer.put((byte) 255);
        buffer.put((byte) 255);
        return buffer.array();
    }

    public byte[] carTelemetry(long frame, float sessionTime, F1CarTelemetry telemetry) {
        ByteBuffer buffer = packet(PacketId.CAR_TELEMETRY, frame, sessionTime);
        buffer.position(HEADER_SIZE + 60 * playerCarIndex);
        buffer.putShort((short) telemetry.speed());
        buffer.putFloat(telemetry.throttle());
        buffer.putFloat(telemetry.steer());
        buffer.putFloat(telemetry.brake());
        buffer.put((byte) telemetry.clutch());
        buffer.put((byte) telemetry.gear());
        buffer.putShort((short) telemetry.engineRPM());
        buffer.put((byte) telemetry.drs());
        buffer.put((byte) telemetry.revLightsPercent());
        buffer.putShort((short) telemetry.revLightsBitValue());
        for (int value : telemetry.brakesTemperature()) buffer.putShort((short) value);
        for (int value : telemetry.tyresSurfaceTemperature()) buffer.put((byte) value);
        for (int value : telemetry.tyresInnerTemperature()) buffer.put((byte) value);
        buffer.putShort((short) telemetry.engineTemperature());
        for (float value : telemetry.tyresPressure()) buffer.putFloat(value);
        for (int value : telemetry.surfaceType()) buffer.put((byte) value);
        // After all 22 cars: m_mfdPanelIndex, m_mfdPanelIndexSecondaryPlayer, m_suggestedGear
        buffer.position(HEADER_SIZE + 60 * 22);
        buffer.put((byte) 255);
        buffer.put((byte) 255);
        buffer.put((byte) 0);
        return buffer.array();
    }

    public byte[] session(float sessionTime, F1SessionData session) {
        ByteBuffer buffer = packet(PacketId.SESSION, 0, sessionTime);
        buffer.position(HEADER_SIZE);
        buffer.put((byte) session.weather());
        buffer.put((byte) session.trackTemperature());
        buffer.put((byte) session.airTemperature());
        buffer.put((byte) session.totalLaps());
        buffer.putShort((short) session.trackLength());
        buffer.put((byte) session.sessionType());
        buffer.put((byte) session.trackId());
        buffer.put((byte) session.formula());
        // m_sessionTimeLeft .. m_aiDifficulty: marshal zones (21 x 5 bytes) and weather forecast
        // samples (64 x 8 bytes) in between; left zeroed
        buffer.putInt(670, 0);                                        // m_seasonLinkIdentifier
        buffer.putInt(674, (int) session.weekendLinkIdentifier());    // m_weekendLinkIdentifier
        buffer.putInt(678, (int) session.sessionLinkIdentifier());    // m_sessionLinkIdentifier
        buffer.put(694, (byte) session.gameMode());                   // m_gameMode
        buffer.putFloat(745, session.sector2LapDistanceStart());      // m_sector2LapDistanceStart
        buffer.putFloat(749, session.sector3LapDistanceStart());      // m_sector3LapDistanceStart
        return buffer.array();
    }

    /** @param laps completed laps followed by the current partial lap (lap time 0) */
    public byte[] sessionHistory(float sessionTime, int carIdx, List<LapHistory> laps) {
        ByteBuffer buffer = packet(PacketId.SESSION_HISTORY, 0, sessionTime);
        buffer.position(HEADER_SIZE);
        buffer.put((byte) carIdx);
        buffer.put((byte) laps.size());     // m_numLaps
        buffer.put((byte) 1);               // m_numTyreStints
        buffer.put((byte) 0);               // m_bestLapTimeLapNum
        buffer.put((byte) 0);               // m_bestSector1LapNum
        buffer.put((byte) 0);               // m_bestSector2LapNum
        buffer.put((byte) 0);               // m_bestSector3LapNum
        for (LapHistory lap : laps) {
            buffer.putInt((int) lap.lapTimeInMS());
            buffer.putShort((short) (lap.sector1TimeMs() % 60_000));
            buffer.put((byte) (lap.sector1TimeMs() / 60_000));
            buffer.putShort((short) (lap.sector2TimeMs() % 60_000));
            buffer.put((byte) (lap.sector2TimeMs() / 60_000));
            buffer.putShort((short) (lap.sector3TimeMs() % 60_000));
            buffer.put((byte) (lap.sector3TimeMs() / 60_000));
            buffer.put((byte) lap.lapValidBitFlags());
        }
        // The tyre stints follow all 100 lap slots
        buffer.position(HEADER_SIZE + 7 + 100 * 14);
        buffer.put((byte) 255);             // m_endLap: 255 = current tyre
        buffer.put((byte) 18);              // m_tyreActualCompound: C3
        buffer.put((byte) 17);              // m_tyreVisualCompound: medium
        return buffer.array();
    }

    public byte[] event(long frame, float sessionTime, String eventCode) {
        ByteBuffer buffer = packet(PacketId.EVENT, frame, sessionTime);
        buffer.position(HEADER_SIZE);
        buffer.put(eventCode.getBytes(StandardCharsets.US_ASCII));
        return buffer.array();
    }

    public byte[] flashbackEvent(long frame, float sessionTime, long flashbackFrame, float flashbackSessionTime) {
        ByteBuffer buffer = ByteBuffer.wrap(event(frame, sessionTime, "FLBK")).order(ByteOrder.LITTLE_ENDIAN);
        buffer.position(HEADER_SIZE + 4);
        buffer.putInt((int) flashbackFrame);
        buffer.putFloat(flashbackSessionTime);
        return buffer.array();
    }

    private ByteBuffer packet(PacketId packetId, long frame, float sessionTime) {
        ByteBuffer buffer = ByteBuffer.allocate(packetId.size()).order(ByteOrder.LITTLE_ENDIAN);
        buffer.putShort((short) 2025);          // m_packetFormat
        buffer.put((byte) 25);                  // m_gameYear
        buffer.put((byte) 1);                   // m_gameMajorVersion
        buffer.put((byte) 0);                   // m_gameMinorVersion
        buffer.put((byte) 1);                   // m_packetVersion
        buffer.put((byte) packetId.id());       // m_packetId
        buffer.putLong(sessionUid);             // m_sessionUID
        buffer.putFloat(sessionTime);           // m_sessionTime
        buffer.putInt((int) frame);             // m_frameIdentifier
        buffer.putInt((int) frame);             // m_overallFrameIdentifier
        buffer.put((byte) playerCarIndex);      // m_playerCarIndex
        buffer.put((byte) 255);                 // m_secondaryPlayerCarIndex
        return buffer;
    }

    private static short normalised(float value) {
        return (short) Math.round(value * 32767.0f);
    }
}
