package com.toine.example.demo.models.dto.api;

import com.toine.example.demo.models.Lap;
import com.toine.example.demo.models.SessionUids;

import java.time.Instant;

public record LapView(
        long id,
        String sessionUid,
        int lapNumber,
        int lapTimeMs,
        Integer sector1Ms,
        Integer sector2Ms,
        Integer sector3Ms,
        boolean valid,
        boolean pitIn,
        boolean pitOut,
        int sampleCount,
        Instant recordedAt
) {
    public static LapView of(Lap lap, long sessionUid) {
        return new LapView(lap.getId(), SessionUids.format(sessionUid), lap.getLapNumber(), lap.getLapTimeMs(),
                lap.getSector1Ms(), lap.getSector2Ms(), lap.getSector3Ms(), lap.isValid(), lap.isPitIn(), lap.isPitOut(),
                lap.getSampleCount(), lap.getRecordedAt());
    }
}
