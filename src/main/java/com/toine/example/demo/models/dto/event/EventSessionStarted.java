package com.toine.example.demo.models.dto.event;

public record EventSessionStarted (
        long session_id
) implements EventDetails {}
