package com.toine.example.demo.tools;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;

/**
 * Standalone dev tool (not a Spring bean, not run by the test suite) that sends
 * scripted F1-25-shaped UDP telemetry packets to a locally running backend, so the
 * ingest -> flush -> API pipeline can be exercised without a real PS5/game.
 *
 * Only satisfies this backend's own parser (TelemetryParser: 29-byte header, then
 * fields packed immediately after) - it is not a byte-perfect replica of the full
 * real F1 25 UDP spec.
 *
 * Run with: mvn -o test-compile exec:java -Dexec.mainClass=... (or run main() directly
 * from an IDE) while the Spring Boot app is up and udp.telemetry.bind-address is unset.
 */
public class UdpTelemetrySimulator {

    private static final String HOST = "127.0.0.1";
    private static final int PORT = 20777;
    private static final long SESSION_UID_BASE = 123456789012345L;
    private static final float TRACK_LENGTH_M = 1000f;
    private static final int LAP_DURATION_MS = 60_000;
    private static final int LAPDATA_PACKETS_PER_LAP = 60; // ~1000ms of simulated time per packet
    private static final int TELEMETRY_PACKETS_PER_LAPDATA = 3;
    private static final int LAPS_TO_SIMULATE = 3; // lap 1 & 2 fully complete+flush; lap 3 left in progress

    // Deliberately > 32.767s so it exercises the unsigned-uint16 sector-time fix
    // (m_sector1TimeMsPart is uint16 ms-within-minute; a naive signed short read
    // would turn this into a negative number).
    private static final int LONG_SECTOR1_MS = 35_000;
    private static final int SECTOR2_MS = 20_000;

    // Session type / track codes - see F1Appendix / the F1 25 UDP spec appendix.
    private static final short SESSION_TYPE_PRACTICE_1 = 1;
    private static final short SESSION_TYPE_RACE = 15;
    private static final short SESSION_TYPE_TIME_TRIAL = 18;
    private static final short TRACK_MONACO = 5;
    private static final short TRACK_SILVERSTONE = 7;

    public static void main(String[] args) throws IOException, InterruptedException {
        try (DatagramSocket socket = new DatagramSocket()) {
            InetAddress address = InetAddress.getByName(HOST);

            // Two sessions sharing one weekendId (a "race weekend": Practice 1 -> Race at Monaco),
            // plus one standalone session with its own unique weekendId (a Time Trial at Silverstone) -
            // exercises the frontend's weekend-grouping (shared weekendId groups sessions; a session
            // whose weekendId matches nothing else renders as its own standalone card).
            long weekendId = 42;
            runSession(socket, address, SESSION_UID_BASE, SESSION_TYPE_PRACTICE_1, TRACK_MONACO, weekendId);
            runSession(socket, address, SESSION_UID_BASE + 1, SESSION_TYPE_RACE, TRACK_MONACO, weekendId);
            runSession(socket, address, SESSION_UID_BASE + 2, SESSION_TYPE_TIME_TRIAL, TRACK_SILVERSTONE, 999);

            System.out.println("Done. Lap " + LAPS_TO_SIMULATE + " left in progress in each session (never flushed) - "
                    + "a pre-existing, accepted gap for the last lap of a session.");
        }
    }

    private static void runSession(DatagramSocket socket, InetAddress address, long sessionUid,
                                    short sessionTypeCode, short trackIdCode, long weekendId) throws IOException, InterruptedException {
        System.out.println("Sending SSTA event for session " + sessionUid + " ...");
        send(socket, address, buildEventPacket(sessionUid, "SSTA", 0f));
        Thread.sleep(50);

        send(socket, address, buildSessionPacket(sessionUid, sessionTypeCode, trackIdCode, weekendId));
        Thread.sleep(15);

        int frame = 0;
        int[] lapTimesMs = new int[LAPS_TO_SIMULATE + 1];

        for (byte lapNum = 1; lapNum <= LAPS_TO_SIMULATE; lapNum++) {
            System.out.println("Simulating lap " + lapNum + " ...");

            // Completion data for the PREVIOUS lap becomes known on the packet right after
            // the lap-number transition (not on the transition packet itself) - see the plan's
            // TelemetryService.processLapData confirmation logic.
            boolean justTransitioned = true;
            int previousLapLastLapTimeMs = LAP_DURATION_MS; // scripted: every lap takes 60s
            int sector1Ms = LONG_SECTOR1_MS;
            int sector2Ms = SECTOR2_MS;

            for (int i = 0; i < LAPDATA_PACKETS_PER_LAP; i++) {
                int currentLapTimeMs = (int) ((long) i * LAP_DURATION_MS / LAPDATA_PACKETS_PER_LAP);
                float lapDistance = TRACK_LENGTH_M * i / LAPDATA_PACKETS_PER_LAP;
                float sessionTime = frame * 0.02f;

                int lastLapTimeMs = 0;
                int s1 = 0;
                int s2 = 0;
                if (lapNum > 1 && !justTransitioned) {
                    // Confirming packet (and every one after, until the next transition)
                    lastLapTimeMs = previousLapLastLapTimeMs;
                    s1 = sector1Ms;
                    s2 = sector2Ms;
                }

                // 255 = not set yet (spec sentinel); once lap 1 completes, the game reports
                // which lap the fastest speed trap was recorded on.
                int speedTrapFastestLap = lapNum == 1 ? 255 : (lapNum - 1);

                send(socket, address, buildLapDataPacket(
                        sessionUid, sessionTime, currentLapTimeMs, lapDistance, lapNum,
                        lastLapTimeMs, s1, s2, (byte) 0, speedTrapFastestLap));
                justTransitioned = false;

                for (int t = 0; t < TELEMETRY_PACKETS_PER_LAPDATA; t++) {
                    double lapFraction = lapDistance / TRACK_LENGTH_M;
                    boolean brakingZone = (lapFraction > 0.28 && lapFraction < 0.35)
                            || (lapFraction > 0.68 && lapFraction < 0.75);

                    short speed = brakingZone ? (short) 90 : (short) 280;
                    float throttle = brakingZone ? 0f : 1f;
                    float brake = brakingZone ? 1f : 0f;
                    // Hard braking pushes tyre surface temp past 127 (uint8 sign-flip threshold);
                    // spec allows up to 255 and real tyres do exceed 127C under heavy braking.
                    int tyreSurfaceTemp = brakingZone ? 145 : 90;

                    send(socket, address, buildCarTelemetryPacket(sessionUid, sessionTime, speed, throttle, brake, tyreSurfaceTemp));
                    frame++;
                }

                Thread.sleep(15);
            }

            lapTimesMs[lapNum] = LAP_DURATION_MS;

            // Session History (packet ID 11) is what actually confirms a lap to the backend
            // and drives TelemetryService.processSesionHistory's flush logic.
            send(socket, address, buildSessionHistoryPacket(sessionUid, lapNum, lapTimesMs));
            Thread.sleep(15);
        }
    }

    private static void send(DatagramSocket socket, InetAddress address, byte[] payload) throws IOException {
        socket.send(new DatagramPacket(payload, payload.length, address, PORT));
    }

    private static ByteBuffer newBuffer(int payloadSize) {
        ByteBuffer buffer = ByteBuffer.allocate(29 + payloadSize).order(ByteOrder.LITTLE_ENDIAN);
        return buffer;
    }

    private static void putZeros(ByteBuffer buffer, int count) {
        buffer.put(new byte[count]);
    }

    private static void writeHeader(ByteBuffer buffer, long sessionUid, short packetId, float sessionTime) {
        buffer.putShort((short) 2025);      // m_packetFormat
        buffer.put((byte) 25);              // m_gameYear
        buffer.put((byte) 1);               // m_gameMajorVersion
        buffer.put((byte) 0);               // m_gameMinorVersion
        buffer.put((byte) 1);               // m_packetVersion
        buffer.put((byte) packetId);        // m_packetId
        buffer.putLong(sessionUid);         // m_sessionUID
        buffer.putFloat(sessionTime);       // m_sessionTime
        buffer.putInt(0);                   // m_frameIdentifier
        buffer.putInt(0);                   // m_overallFrameIdentifier
        buffer.put((byte) 0);               // m_playerCarIndex
        buffer.put((byte) 0xFF);            // m_secondaryPlayerCarIndex (255 = no second player, the normal case)
    }

    private static byte[] buildEventPacket(long sessionUid, String eventCode, float sessionTime) {
        byte[] codeBytes = eventCode.getBytes(StandardCharsets.US_ASCII);
        ByteBuffer buffer = newBuffer(codeBytes.length);
        writeHeader(buffer, sessionUid, (short) 3, sessionTime);
        buffer.put(codeBytes);
        return buffer.array();
    }

    /**
     * Packet ID 1 (Session) - only built out through m_weekendLinkIdentifier, since that's
     * all TelemetryParser.parseSessionData reads. Everything in between is zero-filled but
     * still present at the correct byte offsets, so the parser's skip-by-size jumps land
     * exactly where the real fields would be.
     */
    private static byte[] buildSessionPacket(long sessionUid, short sessionTypeCode, short trackIdCode, long weekendId) {
        int bodySize = 8   // weather, trackTemperature, airTemperature, totalLaps, trackLength, sessionType, trackId (up to and incl. trackId = 8 bytes)
                + 11        // formula..numMarshalZones
                + 21 * 5    // m_marshalZones[21]
                + 3         // safetyCarStatus, networkGame, numWeatherForecastSamples
                + 64 * 8    // m_weatherForecastSamples[64]
                + 6         // forecastAccuracy, aiDifficulty, seasonLinkIdentifier
                + 4;        // weekendLinkIdentifier

        ByteBuffer buffer = newBuffer(bodySize);
        writeHeader(buffer, sessionUid, (short) 1, 0f);

        buffer.put((byte) 0);                // m_weather
        buffer.put((byte) 20);               // m_trackTemperature
        buffer.put((byte) 25);               // m_airTemperature
        buffer.put((byte) 0);                // m_totalLaps
        buffer.putShort((short) 0);          // m_trackLength
        buffer.put((byte) sessionTypeCode);  // m_sessionType
        buffer.put((byte) trackIdCode);      // m_trackId

        putZeros(buffer, 11);                // formula..numMarshalZones
        putZeros(buffer, 21 * 5);            // m_marshalZones[21]
        putZeros(buffer, 3);                 // safetyCarStatus, networkGame, numWeatherForecastSamples
        putZeros(buffer, 64 * 8);            // m_weatherForecastSamples[64]
        putZeros(buffer, 2);                 // forecastAccuracy, aiDifficulty
        buffer.putInt(0);                    // m_seasonLinkIdentifier
        buffer.putInt((int) weekendId);      // m_weekendLinkIdentifier

        return buffer.array();
    }

    private static byte[] buildLapDataPacket(long sessionUid, float sessionTime, int currentLapTimeMs, float lapDistance,
                                              byte currentLapNum, int lastLapTimeMs, int sector1Ms, int sector2Ms,
                                              byte currentLapInvalid, int speedTrapFastestLap) {
        ByteBuffer buffer = newBuffer(57);
        writeHeader(buffer, sessionUid, (short) 2, sessionTime);

        buffer.putInt(lastLapTimeMs);                 // lastLapTimeInMS
        buffer.putInt(currentLapTimeMs);               // currentLapTimeInMS
        buffer.putShort((short) (sector1Ms % 60_000)); // sector1TimeMSPart
        buffer.put((byte) (sector1Ms / 60_000));       // sector1TimeMinutesPart
        buffer.putShort((short) (sector2Ms % 60_000)); // sector2TimeMSPart
        buffer.put((byte) (sector2Ms / 60_000));       // sector2TimeMinutesPart
        buffer.putShort((short) 0);                    // deltaToCarInFrontMSPart
        buffer.put((byte) 0);                          // deltaToCarInFrontMinutesPart
        buffer.putShort((short) 0);                    // deltaToRaceLeaderMSPart
        buffer.put((byte) 0);                           // deltaToRaceLeaderMinutesPart
        buffer.putFloat(lapDistance);                  // lapDistance
        buffer.putFloat(lapDistance);                  // totalDistance
        buffer.putFloat(0f);                           // safetyCarDelta
        buffer.put((byte) 1);                           // carPosition
        buffer.put(currentLapNum);                      // currentLapNum
        buffer.put((byte) 0);                           // pitStatus
        buffer.put((byte) 0);                           // numPitStops
        buffer.put((byte) 0);                           // sector
        buffer.put(currentLapInvalid);                  // currentLapInvalid
        buffer.put((byte) 0);                           // penalties
        buffer.put((byte) 0);                           // totalWarnings
        buffer.put((byte) 0);                           // cornerCuttingWarnings
        buffer.put((byte) 0);                           // numUnservedDriveThroughPens
        buffer.put((byte) 0);                           // numUnservedStopGoPens
        buffer.put((byte) 1);                           // gridPosition
        buffer.put((byte) 4);                           // driverStatus (4 = on track; car is driving, not in garage)
        buffer.put((byte) 0);                           // resultStatus
        buffer.put((byte) 0);                           // pitLaneTimerActive
        buffer.putShort((short) 0);                     // pitLaneTimeInLaneInMS
        buffer.putShort((short) 0);                     // pitStopTimerInMS
        buffer.put((byte) 0);                           // pitStopShouldServePen
        buffer.putFloat(0f);                            // speedTrapFastestSpeed
        buffer.put((byte) speedTrapFastestLap);         // speedTrapFastestLap (255 = not set, spec sentinel)

        return buffer.array();
    }

    private static byte[] buildCarTelemetryPacket(long sessionUid, float sessionTime, short speed, float throttle,
                                                   float brake, int tyreSurfaceTemp) {
        ByteBuffer buffer = newBuffer(60);
        writeHeader(buffer, sessionUid, (short) 6, sessionTime);

        buffer.putShort(speed);       // speed
        buffer.putFloat(throttle);    // throttle
        buffer.putFloat(0f);          // steer
        buffer.putFloat(brake);       // brake
        buffer.put((byte) 0);         // clutch
        buffer.put((byte) 4);         // gear
        buffer.putShort((short) 10000); // engineRPM
        buffer.put((byte) 0);         // drs
        buffer.put((byte) 0);         // revLightsPerc
        buffer.putShort((short) 0);   // revLightsBitValue
        buffer.putShort((short) 400); // frontLeftBrakeTemp
        buffer.putShort((short) 400); // frontRightBrakeTemp
        buffer.putShort((short) 400); // rearLeftBrakeTemp
        buffer.putShort((short) 400); // rearRightBrakeTemp
        buffer.put((byte) tyreSurfaceTemp); // frontLeftTireSurfaceTemp
        buffer.put((byte) tyreSurfaceTemp); // frontRightTireSurfaceTemp
        buffer.put((byte) tyreSurfaceTemp); // rearLeftTireSurfaceTemp
        buffer.put((byte) tyreSurfaceTemp); // rearRightTireSurfaceTemp
        buffer.put((byte) tyreSurfaceTemp); // frontLeftInnerTireSurfaceTemp
        buffer.put((byte) tyreSurfaceTemp); // frontRightInnerTireSurfaceTemp
        buffer.put((byte) tyreSurfaceTemp); // rearLeftInnerTireSurfaceTemp
        buffer.put((byte) tyreSurfaceTemp); // rearRightInnerTireSurfaceTemp
        buffer.putShort((short) 100); // engineTemp
        buffer.putFloat(23f);         // frontLeftTirePressure
        buffer.putFloat(23f);         // frontRightTirePressure
        buffer.putFloat(23f);         // rearLeftTirePressure
        buffer.putFloat(23f);         // rearRightTirePressure
        buffer.put((byte) 0);         // frontLeftsurfaceType
        buffer.put((byte) 0);         // frontRightsurfaceType
        buffer.put((byte) 0);         // rearLeftsurfaceType
        buffer.put((byte) 0);         // rearRightsurfaceType

        return buffer.array();
    }

    /**
     * Packet ID 11 (Session History) - what TelemetryService actually listens on to
     * confirm laps and trigger flushes. m_numLaps is the count of laps with data so far
     * (including the current in-progress one), i.e. completedLaps + 1.
     */
    private static byte[] buildSessionHistoryPacket(long sessionUid, int completedLaps, int[] lapTimesMs) {
        int numLaps = completedLaps + 1; // + current in-progress lap
        int lapHistorySize = 14; // 4 + 2 + 1 + 2 + 1 + 2 + 1 + 1
        int tyreStintSize = 3;
        int bodySize = 7 + (numLaps * lapHistorySize) + (8 * tyreStintSize);

        ByteBuffer buffer = newBuffer(bodySize);
        writeHeader(buffer, sessionUid, (short) 11, 0f);

        buffer.put((byte) 0);              // m_carIdx (matches m_playerCarIndex in header)
        buffer.put((byte) numLaps);        // m_numLaps
        buffer.put((byte) 0);              // m_numTyreStints
        buffer.put((byte) 0);              // m_bestLapTimeLapNum
        buffer.put((byte) 0);              // m_bestSector1LapNum
        buffer.put((byte) 0);              // m_bestSector2LapNum
        buffer.put((byte) 0);              // m_bestSector3LapNum

        for (int lap = 1; lap <= numLaps; lap++) {
            boolean completed = lap <= completedLaps;
            int lapTimeMs = completed ? lapTimesMs[lap] : 0;
            int sector1Ms = completed ? LONG_SECTOR1_MS : 0;
            int sector2Ms = completed ? SECTOR2_MS : 0;
            int sector3Ms = completed ? (lapTimeMs - sector1Ms - sector2Ms) : 0;

            buffer.putInt(lapTimeMs);                        // m_lapTimeInMS
            buffer.putShort((short) (sector1Ms % 60_000));   // m_sector1TimeMsPart
            buffer.put((byte) (sector1Ms / 60_000));         // m_sector1TimeMinutesPart
            buffer.putShort((short) (sector2Ms % 60_000));   // m_sector2TimeMsPart
            buffer.put((byte) (sector2Ms / 60_000));         // m_sector2TimeMinutesPart
            buffer.putShort((short) (sector3Ms % 60_000));   // m_sector3TimeMsPart
            buffer.put((byte) (sector3Ms / 60_000));         // m_sector3TimeMinutesPart
            buffer.put((byte) (completed ? 0x01 : 0x00));    // m_lapValidBitFlags (bit0 = valid)
        }

        // Stint 0 is the active tyre stint - m_endLap = 255 is the spec sentinel for "current tyre",
        // true for every session's last stint. Remaining slots are unused (0).
        buffer.put((byte) 0xFF); // m_endLap (255 = current tyre)
        buffer.put((byte) 16);   // m_tyreActualCompound (16 = C5)
        buffer.put((byte) 16);   // m_tyreVisualCompound
        for (int i = 1; i < 8; i++) {
            buffer.put((byte) 0); // m_endLap
            buffer.put((byte) 0); // m_tyreActualCompound
            buffer.put((byte) 0); // m_tyreVisualCompound
        }

        return buffer.array();
    }
}
