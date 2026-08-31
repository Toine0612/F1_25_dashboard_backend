package com.toine.example.demo.models.dto.event;

public sealed interface EventDetails permits EventRewind, EventSessionStarted, EventSessionEnded {}
