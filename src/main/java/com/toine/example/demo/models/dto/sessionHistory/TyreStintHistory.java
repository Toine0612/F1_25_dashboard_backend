package com.toine.example.demo.models.dto.sessionHistory;

public record TyreStintHistory(
        int endLap,                 // 255 = current tyre
        int tyreActualCompound,
        int tyreVisualCompound
) {}
