package com.toine.example.demo.service;

import com.toine.example.demo.models.dto.event.EventDetails;
import com.toine.example.demo.models.dto.event.EventRewind;
import com.toine.example.demo.models.dto.event.EventSessionEnded;
import com.toine.example.demo.models.dto.event.EventSessionStarted;
import com.toine.example.demo.models.dto.packets.*;
import com.toine.example.demo.models.dto.sessionHistory.LapHistory;
import com.toine.example.demo.models.dto.sessionHistory.TyreStintHistory;
import com.toine.example.demo.reference.F1Appendix;
import org.springframework.stereotype.Service;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;

@Service
public class TelemetryParser {

    private final int HEADER_SIZE = 29;
    private final int LAP_DATA_SIZE = 57;
    private final int TELEMETRY_DATA_SIZE = 60;

    public F1Header parseHeader(byte[] payload) {

        ByteBuffer buffer = ByteBuffer.wrap(payload).order(ByteOrder.LITTLE_ENDIAN);

        int m_packetFormat = Short.toUnsignedInt(buffer.getShort());
        int m_gameYear = Byte.toUnsignedInt(buffer.get());
        short m_gameMajorVersion = (short) Byte.toUnsignedInt(buffer.get());
        short m_gameMinorVersion = (short) Byte.toUnsignedInt(buffer.get());
        short m_packetVersion = (short) Byte.toUnsignedInt(buffer.get());
        short m_packetId = (short) Byte.toUnsignedInt(buffer.get());
        long m_sessionUID = buffer.getLong();
        float m_sessionTime = buffer.getFloat();
        long m_frameIdentifier = Integer.toUnsignedLong(buffer.getInt());
        long m_overallFrameIdentifier = Integer.toUnsignedLong(buffer.getInt());
        short m_playerCarIndex = (short) Byte.toUnsignedInt(buffer.get());
        short m_secondaryPlayerCarIndex = (short) Byte.toUnsignedInt(buffer.get());

        return new F1Header(
                m_packetFormat, m_gameYear, m_gameMajorVersion, m_gameMinorVersion, m_packetVersion,
                m_packetId, m_sessionUID, m_sessionTime, m_frameIdentifier, m_overallFrameIdentifier,
                m_playerCarIndex, m_secondaryPlayerCarIndex
        );
    }

    public F1LapData parseLapData(byte[] payload, short car_index) {

        ByteBuffer buffer = ByteBuffer.wrap(payload).order(ByteOrder.LITTLE_ENDIAN);
        buffer.position(HEADER_SIZE + (LAP_DATA_SIZE * car_index));

        long lastLapTimeInMS = Integer.toUnsignedLong(buffer.getInt());
        long currentLapTimeInMS = Integer.toUnsignedLong(buffer.getInt());
        int sector1TimeMSPart = Short.toUnsignedInt(buffer.getShort());
        short sector1TimeMinutesPart = (short) Byte.toUnsignedInt(buffer.get());
        int sector2TimeMSPart = Short.toUnsignedInt(buffer.getShort());
        short sector2TimeMinutesPart = (short) Byte.toUnsignedInt(buffer.get());
        int deltaToCarInFrontMSPart = Short.toUnsignedInt(buffer.getShort());
        short deltaToCarInFrontMinutesPart = (short) Byte.toUnsignedInt(buffer.get());
        int deltaToRaceLeaderMSPart = Short.toUnsignedInt(buffer.getShort());
        short deltaToRaceLeaderMinutesPart = (short) Byte.toUnsignedInt(buffer.get());
        float lapDistance = buffer.getFloat();
        float totalDistance = buffer.getFloat();
        float safetyCarDelta = buffer.getFloat();
        short carPosition = (short) Byte.toUnsignedInt(buffer.get());
        short currentLapNum = (short) Byte.toUnsignedInt(buffer.get());
        short pitStatus = (short) Byte.toUnsignedInt(buffer.get());
        short numPitStops = (short) Byte.toUnsignedInt(buffer.get());
        short sector = (short) Byte.toUnsignedInt(buffer.get());
        short currentLapInvalid = (short) Byte.toUnsignedInt(buffer.get());
        short penalties = (short) Byte.toUnsignedInt(buffer.get());
        short totalWarnings = (short) Byte.toUnsignedInt(buffer.get());
        short cornerCuttingWarnings = (short) Byte.toUnsignedInt(buffer.get());
        short numUnservedDriveThroughPens = (short) Byte.toUnsignedInt(buffer.get());
        short numUnservedStopGoPens = (short) Byte.toUnsignedInt(buffer.get());
        short gridPosition = (short) Byte.toUnsignedInt(buffer.get());
        short driverStatus = (short) Byte.toUnsignedInt(buffer.get());
        short resultStatus = (short) Byte.toUnsignedInt(buffer.get());
        short pitLaneTimerActive = (short) Byte.toUnsignedInt(buffer.get());
        int pitLaneTimeInLaneInMS = Short.toUnsignedInt(buffer.getShort());
        int pitStopTimerInMS = Short.toUnsignedInt(buffer.getShort());
        short pitStopShouldServePen = (short) Byte.toUnsignedInt(buffer.get());
        float speedTrapFastestSpeed = buffer.getFloat();
        short speedTrapFastestLap = (short) Byte.toUnsignedInt(buffer.get());

        return new F1LapData(
                lastLapTimeInMS, currentLapTimeInMS, sector1TimeMSPart, sector1TimeMinutesPart,
                sector2TimeMSPart, sector2TimeMinutesPart, deltaToCarInFrontMSPart,
                deltaToCarInFrontMinutesPart, deltaToRaceLeaderMSPart, deltaToRaceLeaderMinutesPart,
                lapDistance, totalDistance, safetyCarDelta, carPosition, currentLapNum,
                pitStatus, numPitStops, sector, currentLapInvalid, penalties, totalWarnings,
                cornerCuttingWarnings, numUnservedDriveThroughPens, numUnservedStopGoPens,
                gridPosition, driverStatus, resultStatus, pitLaneTimerActive,
                pitLaneTimeInLaneInMS, pitStopTimerInMS, pitStopShouldServePen,
                speedTrapFastestSpeed, speedTrapFastestLap
        );
    }

    public F1SessionData parseSessionData(byte[] payload) {

        ByteBuffer buffer = ByteBuffer.wrap(payload).order(ByteOrder.LITTLE_ENDIAN);
        buffer.position(HEADER_SIZE + 6); // skip weather, trackTemperature, airTemperature, totalLaps, trackLength

        short sessionTypeCode = (short) Byte.toUnsignedInt(buffer.get()); // uint8
        short trackIdCode = buffer.get(); // int8 (signed) - do NOT use an unsigned conversion here

        return new F1SessionData(F1Appendix.sessionType(sessionTypeCode), F1Appendix.track(trackIdCode));
    }

    public F1EventData parseEventData(byte[] payload) {

        ByteBuffer buffer = ByteBuffer.wrap(payload).order(ByteOrder.LITTLE_ENDIAN);

        F1Header header = parseHeader(payload);

        byte[] eventCodeBytes = new byte[4];
        buffer.get(eventCodeBytes);
        String eventCode = new String(eventCodeBytes, StandardCharsets.US_ASCII);

        EventDetails details = switch (eventCode) {
            case "FLBK" -> new EventRewind(
                Integer.toUnsignedLong(buffer.getInt()),
                buffer.getFloat()
            );
            case "SSTA" -> new EventSessionStarted(
                header.m_sessionUID()
            );
            case "SEND" -> new EventSessionEnded();
            default -> null;
        };

        return new F1EventData(eventCode, details);
    }

    public F1CarTelemetry parseCarTelemetry(byte[] payload, short car_index) {

        ByteBuffer buffer = ByteBuffer.wrap(payload).order(ByteOrder.LITTLE_ENDIAN);
        buffer.position(HEADER_SIZE + (TELEMETRY_DATA_SIZE * car_index));

        // uint16 fields below (speed, engineRPM, revLightsBitValue, brake temps, engineTemp) are read
        // with a plain getShort(): their real-world ranges (speed/RPM/brake-temp/revLightsBitValue's
        // documented 15-bit max of 0x7FFF) never reach the 32768 sign-flip threshold, and F1CarTelemetry
        // stores them as `short` anyway, so an unsigned round-trip here would be a no-op.
        short speed = buffer.getShort();
        float throttle = buffer.getFloat();
        float steer = buffer.getFloat();
        float brake = buffer.getFloat();
        int clutch = Byte.toUnsignedInt(buffer.get());
        int gear = buffer.get(); // int8 (signed): N=0, R=-1 - must stay a plain signed read
        short engineRPM = buffer.getShort();
        int drs = Byte.toUnsignedInt(buffer.get());
        int revLightsPerc = Byte.toUnsignedInt(buffer.get());
        short revLightsBitValue = buffer.getShort();
        short rearLeftBrakeTemp = buffer.getShort();
        short rearRightBrakeTemp = buffer.getShort();
        short frontLeftBrakeTemp = buffer.getShort();
        short frontRightBrakeTemp = buffer.getShort();
        int rearLeftTireSurfaceTemp = Byte.toUnsignedInt(buffer.get());
        int rearRightTireSurfaceTemp = Byte.toUnsignedInt(buffer.get());
        int frontLeftTireSurfaceTemp = Byte.toUnsignedInt(buffer.get());
        int frontRightTireSurfaceTemp = Byte.toUnsignedInt(buffer.get());
        int rearLeftInnerTireSurfaceTemp = Byte.toUnsignedInt(buffer.get());
        int rearRightInnerTireSurfaceTemp = Byte.toUnsignedInt(buffer.get());
        int frontLeftInnerTireSurfaceTemp = Byte.toUnsignedInt(buffer.get());
        int frontRightInnerTireSurfaceTemp = Byte.toUnsignedInt(buffer.get());
        short engineTemp = buffer.getShort();
        float rearLeftTirePressure = buffer.getFloat();
        float rearRightTirePressure = buffer.getFloat();
        float frontLeftTirePressure = buffer.getFloat();
        float frontRightTirePressure = buffer.getFloat();
        int rearLeftsurfaceType = Byte.toUnsignedInt(buffer.get());
        int rearRightsurfaceType = Byte.toUnsignedInt(buffer.get());
        int frontLeftsurfaceType = Byte.toUnsignedInt(buffer.get());
        int frontRightsurfaceType = Byte.toUnsignedInt(buffer.get());


        return new F1CarTelemetry(
                speed, throttle, steer, brake, clutch, gear, engineRPM, drs, revLightsPerc,
                revLightsBitValue, frontLeftBrakeTemp, frontRightBrakeTemp, rearLeftBrakeTemp, rearRightBrakeTemp,
                frontLeftTireSurfaceTemp, frontRightTireSurfaceTemp, rearLeftTireSurfaceTemp, rearRightTireSurfaceTemp,
                frontLeftInnerTireSurfaceTemp, frontRightInnerTireSurfaceTemp, rearLeftInnerTireSurfaceTemp,
                rearRightInnerTireSurfaceTemp, engineTemp, frontLeftTirePressure, frontRightTirePressure,
                rearLeftTirePressure, rearRightTirePressure, frontLeftsurfaceType, frontRightsurfaceType,
                rearLeftsurfaceType, rearRightsurfaceType
        );
    }

    public F1SessionHistory parseSessionHistory(byte[] payload) {

        ByteBuffer buffer = ByteBuffer.wrap(payload).order(ByteOrder.LITTLE_ENDIAN);
        buffer.position(HEADER_SIZE);

        short m_carIdx = (short) Byte.toUnsignedInt(buffer.get());
        short m_numLaps = (short) Byte.toUnsignedInt(buffer.get());
        short m_numTyreStints = (short) Byte.toUnsignedInt(buffer.get());
        short m_bestLapTimeLapNum = (short) Byte.toUnsignedInt(buffer.get());
        short m_bestSector1LapNum = (short) Byte.toUnsignedInt(buffer.get());
        short m_bestSector2LapNum = (short) Byte.toUnsignedInt(buffer.get());
        short m_bestSector3LapNum = (short) Byte.toUnsignedInt(buffer.get());

        // Parse the 100 LapHistoryData structs
        LapHistory[] lapHistoryArray = new LapHistory[100];
        for (int i = 0; i < m_numLaps; i++) {
            lapHistoryArray[i] = new LapHistory(
                    Integer.toUnsignedLong(buffer.getInt()),
                    Short.toUnsignedInt(buffer.getShort()),
                    (short) Byte.toUnsignedInt(buffer.get()),
                    Short.toUnsignedInt(buffer.getShort()),
                    (short) Byte.toUnsignedInt(buffer.get()),
                    Short.toUnsignedInt(buffer.getShort()),
                    (short) Byte.toUnsignedInt(buffer.get()),
                    (short) Byte.toUnsignedInt(buffer.get())
            );
        }

        // Parse the 8 TyreStintHistoryData structs
        TyreStintHistory[] tyreStintArray = new TyreStintHistory[8];
        for (int i = 0; i < 8; i++) {
            tyreStintArray[i] = new TyreStintHistory(
                    (short) Byte.toUnsignedInt(buffer.get()),
                    (short) Byte.toUnsignedInt(buffer.get()),
                    (short) Byte.toUnsignedInt(buffer.get())
            );
        }

        return new F1SessionHistory(
                m_carIdx, m_numLaps, m_numTyreStints, m_bestLapTimeLapNum,
                m_bestSector1LapNum, m_bestSector2LapNum, m_bestSector3LapNum,
                java.util.Arrays.asList(lapHistoryArray),
                java.util.Arrays.asList(tyreStintArray)
        );
    }
}
