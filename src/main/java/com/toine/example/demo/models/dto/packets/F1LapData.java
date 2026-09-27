package com.toine.example.demo.models.dto.packets;

/** LapData (Lap Data packet, one entry per car). */
public record F1LapData(
        long lastLapTimeInMS,
        long currentLapTimeInMS,
        int sector1TimeMSPart,
        int sector1TimeMinutesPart,
        int sector2TimeMSPart,
        int sector2TimeMinutesPart,
        int deltaToCarInFrontMSPart,
        int deltaToCarInFrontMinutesPart,
        int deltaToRaceLeaderMSPart,
        int deltaToRaceLeaderMinutesPart,
        float lapDistance,          // metres around the current lap - negative until the line is crossed
        float totalDistance,
        float safetyCarDelta,
        int carPosition,
        int currentLapNum,
        int pitStatus,              // 0 = none, 1 = pitting, 2 = in pit area
        int numPitStops,
        int sector,                 // 0 = sector 1, 1 = sector 2, 2 = sector 3
        int currentLapInvalid,      // 0 = valid, 1 = invalid
        int penalties,
        int totalWarnings,
        int cornerCuttingWarnings,
        int numUnservedDriveThroughPens,
        int numUnservedStopGoPens,
        int gridPosition,
        int driverStatus,           // 0 = in garage, 1 = flying lap, 2 = in lap, 3 = out lap, 4 = on track
        int resultStatus,
        int pitLaneTimerActive,
        int pitLaneTimeInLaneInMS,
        int pitStopTimerInMS,
        int pitStopShouldServePen,
        float speedTrapFastestSpeed,
        int speedTrapFastestLap
) {
    public static final int DRIVER_STATUS_IN_GARAGE = 0;

    /** Sector 1 time of the current lap in ms (0 until sector 1 is completed). */
    public int sector1TimeMs() {
        return sector1TimeMinutesPart * 60_000 + sector1TimeMSPart;
    }

    /** Sector 2 time of the current lap in ms (0 until sector 2 is completed). */
    public int sector2TimeMs() {
        return sector2TimeMinutesPart * 60_000 + sector2TimeMSPart;
    }
}
