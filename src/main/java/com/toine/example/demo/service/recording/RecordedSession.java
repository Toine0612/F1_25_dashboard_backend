package com.toine.example.demo.service.recording;

import java.time.Instant;

/**
 * @param info {@code null} if no Session packet has been received for this session yet
 */
public record RecordedSession(long sessionUid, Instant firstSeenAt, SessionInfo info) {}
