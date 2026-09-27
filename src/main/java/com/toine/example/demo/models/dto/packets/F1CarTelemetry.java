package com.toine.example.demo.models.dto.packets;

/**
 * CarTelemetryData (Car Telemetry packet, one entry per car). Every per-wheel array uses the spec's
 * wheel order: 0 = rear left, 1 = rear right, 2 = front left, 3 = front right.
 */
public record F1CarTelemetry(
        int speed,                  // km/h
        float throttle,             // 0.0 - 1.0
        float steer,                // -1.0 (full lock left) - 1.0 (full lock right)
        float brake,                // 0.0 - 1.0
        int clutch,                 // 0 - 100
        int gear,                   // 1-8, N = 0, R = -1
        int engineRPM,
        int drs,                    // 0 = off, 1 = on
        int revLightsPercent,
        int revLightsBitValue,
        int[] brakesTemperature,    // celsius
        int[] tyresSurfaceTemperature,
        int[] tyresInnerTemperature,
        int engineTemperature,
        float[] tyresPressure,      // PSI
        int[] surfaceType           // see spec appendix "Surface types"
) {}
