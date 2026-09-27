package com.toine.example.demo.models.dto.packets;

/** The subset of PacketSessionData this backend uses. */
public record F1SessionData(
        int weather,
        int trackTemperature,
        int airTemperature,
        int totalLaps,
        int trackLength,            // metres
        int sessionType,            // see F1Appendix.sessionType
        int trackId,                // see F1Appendix.track (-1 = unknown)
        int formula,
        long weekendLinkIdentifier, // shared by every session of one race weekend, persists across saves
        long sessionLinkIdentifier,
        int gameMode,
        float sector2LapDistanceStart, // metres around the lap where sector 2 starts
        float sector3LapDistanceStart
) {}
