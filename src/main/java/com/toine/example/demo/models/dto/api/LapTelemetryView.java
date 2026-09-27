package com.toine.example.demo.models.dto.api;

import java.util.Map;

/**
 * A lap's telemetry in columnar form: {@code channels.speed[i]} and {@code channels.distance[i]}
 * belong to the same frame. Samples are ordered by strictly increasing lap distance. Integral
 * channels are sent as integer arrays, the rest as float arrays.
 */
public record LapTelemetryView(
        long lapId,
        int sampleCount,
        Map<String, Object> channels
) {}
