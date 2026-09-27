package com.toine.example.demo.service.recording;

import com.toine.example.demo.models.dto.packets.F1SessionData;

/** What a session is (track, type, layout) - from the Session packet, which arrives twice a second. */
public record SessionInfo(
        int sessionType,
        int trackId,
        int trackLengthM,
        float sector2StartM,
        float sector3StartM,
        long weekendLinkId,
        int gameMode,
        int formula
) {
    public static SessionInfo from(F1SessionData data) {
        return new SessionInfo(data.sessionType(), data.trackId(), data.trackLength(),
                data.sector2LapDistanceStart(), data.sector3LapDistanceStart(),
                data.weekendLinkIdentifier(), data.gameMode(), data.formula());
    }
}
