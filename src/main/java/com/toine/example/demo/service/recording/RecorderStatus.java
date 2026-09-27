package com.toine.example.demo.service.recording;

/**
 * Snapshot of what the recorder is doing right now.
 *
 * @param session    {@code null} when no session is active
 * @param lapNumber  lap currently being recorded, {@code null} when not on a timed lap
 * @param lapsSaved  laps completed in the active session
 */
public record RecorderStatus(RecordedSession session, Integer lapNumber, Float lapDistance, int lapsSaved) {}
