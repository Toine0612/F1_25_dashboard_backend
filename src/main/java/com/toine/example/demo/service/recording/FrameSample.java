package com.toine.example.demo.service.recording;

import com.toine.example.demo.models.dto.packets.F1CarMotion;
import com.toine.example.demo.models.dto.packets.F1CarTelemetry;
import com.toine.example.demo.models.dto.packets.F1LapData;

/** Everything recorded about the player's car on one frame, joined from three packets. */
public record FrameSample(
        long frame,
        // Lap Data
        int lapNumber,
        float lapDistance,
        int lapTimeMs,
        int lastLapTimeMs,
        int sector1Ms,
        int sector2Ms,
        boolean lapInvalid,
        int pitStatus,
        int driverStatus,
        // Car Telemetry
        int speedKph,
        float throttle,
        float brake,
        float steer,
        int gear,
        int engineRpm,
        boolean drs,
        // Motion
        float worldX,
        float worldY,
        float worldZ
) {
    static FrameSample of(long frame, F1LapData lap, F1CarTelemetry telemetry, F1CarMotion motion) {
        return new FrameSample(frame,
                lap.currentLapNum(), lap.lapDistance(), (int) lap.currentLapTimeInMS(), (int) lap.lastLapTimeInMS(),
                lap.sector1TimeMs(), lap.sector2TimeMs(), lap.currentLapInvalid() == 1, lap.pitStatus(), lap.driverStatus(),
                telemetry.speed(), telemetry.throttle(), telemetry.brake(), telemetry.steer(), telemetry.gear(),
                telemetry.engineRPM(), telemetry.drs() == 1,
                motion.worldPositionX(), motion.worldPositionY(), motion.worldPositionZ());
    }
}
