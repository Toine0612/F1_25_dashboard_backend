package com.toine.example.demo.models.dto.event;

public record EventRewind (
        long frameIdentifier,
        float sessionTime
) implements EventDetails {}
