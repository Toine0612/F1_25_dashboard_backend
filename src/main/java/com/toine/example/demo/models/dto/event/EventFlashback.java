package com.toine.example.demo.models.dto.event;

/** "FLBK" - the player used a flashback; the game rewinds to this frame. */
public record EventFlashback(
        long frameIdentifier,
        float sessionTime
) implements EventDetails {}
