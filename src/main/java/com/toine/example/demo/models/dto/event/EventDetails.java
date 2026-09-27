package com.toine.example.demo.models.dto.event;

/** The event types this backend acts on; every other event code decodes to {@code null} details. */
public sealed interface EventDetails permits EventFlashback, EventSessionStarted, EventSessionEnded {}
