package com.toine.example.demo.service.recording;

import com.toine.example.demo.models.Channel;

import java.util.Map;

/**
 * A lap driven from start/finish line to start/finish line, ready to be stored.
 *
 * @param channels index-aligned channel arrays, all {@code sampleCount} long, ordered by lap distance
 */
public record CompletedLap(
        int lapNumber,
        LapTiming timing,
        boolean pitIn,
        boolean pitOut,
        int sampleCount,
        Map<Channel, float[]> channels
) {}
