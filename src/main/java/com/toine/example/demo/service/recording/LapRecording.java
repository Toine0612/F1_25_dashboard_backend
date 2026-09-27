package com.toine.example.demo.service.recording;

import java.util.ArrayList;
import java.util.List;

/** The samples of one lap, in frame order, while it is being driven. */
final class LapRecording {

    // Furthest a car can plausibly travel per millisecond of lap time (~400 km/h), plus slack for
    // float noise. Anything beyond that between two samples is a glitch, not driving.
    private static final float MAX_METRES_PER_MS = 0.110f;
    private static final float DISTANCE_SLACK_M = 25f;

    private final int lapNumber;
    private final List<FrameSample> samples = new ArrayList<>(4096);

    LapRecording(int lapNumber) {
        this.lapNumber = lapNumber;
    }

    int lapNumber() {
        return lapNumber;
    }

    boolean isEmpty() {
        return samples.isEmpty();
    }

    FrameSample first() {
        return samples.getFirst();
    }

    FrameSample last() {
        return samples.getLast();
    }

    /**
     * Adds a sample if it moves the car forward along the lap. Samples that don't (standing still,
     * spinning, reversing) are skipped so that lap distance strictly increases and can be used as the
     * x-axis to compare laps; the time lost is still visible in the time channel.
     */
    void append(FrameSample sample) {
        if (!samples.isEmpty()) {
            FrameSample last = last();
            float advance = sample.lapDistance() - last.lapDistance();
            float reachable = Math.max(0, sample.lapTimeMs() - last.lapTimeMs()) * MAX_METRES_PER_MS + DISTANCE_SLACK_M;
            if (advance <= 0 || advance > reachable) return;
        }
        samples.add(sample);
    }

    /** Drops every sample from {@code frame} onwards - the game rewound to that frame. */
    void truncateFrom(long frame) {
        while (!samples.isEmpty() && last().frame() >= frame) {
            samples.removeLast();
        }
    }

    CompletedLap complete(LapTiming timing) {
        return new CompletedLap(lapNumber, timing,
                last().pitStatus() != 0,    // crossed the line in the pit lane: an in-lap
                first().pitStatus() != 0,   // started in the pit lane: an out-lap
                samples.size(),
                LapTrace.channels(samples));
    }
}
