package com.toine.example.demo.service.recording;

import com.toine.example.demo.models.dto.sessionHistory.LapHistory;

/** Official timing of one lap. Sector times are {@code null} when the game didn't report them. */
public record LapTiming(
        int lapTimeMs,
        Integer sector1Ms,
        Integer sector2Ms,
        Integer sector3Ms,
        boolean valid
) {
    /** From the game's Session History - the authoritative source, including sector validity. */
    static LapTiming fromHistory(LapHistory history) {
        return new LapTiming((int) history.lapTimeInMS(),
                positiveOrNull(history.sector1TimeMs()),
                positiveOrNull(history.sector2TimeMs()),
                positiveOrNull(history.sector3TimeMs()),
                history.lapValid());
    }

    /**
     * Fallback from Lap Data: the lap time as reported on the next lap, the sector 1/2 splits and the
     * invalid flag as they stood on the lap's last recorded frame (the game keeps both until the line).
     */
    static LapTiming fromLapData(int lapTimeMs, FrameSample lastSampleOfLap) {
        Integer sector1 = positiveOrNull(lastSampleOfLap.sector1Ms());
        Integer sector2 = positiveOrNull(lastSampleOfLap.sector2Ms());
        Integer sector3 = sector1 != null && sector2 != null ? positiveOrNull(lapTimeMs - sector1 - sector2) : null;
        return new LapTiming(lapTimeMs, sector1, sector2, sector3, !lastSampleOfLap.lapInvalid());
    }

    private static Integer positiveOrNull(int value) {
        return value > 0 ? value : null;
    }
}
