package com.toine.example.demo.models.dto.api;

import com.toine.example.demo.models.Session;
import com.toine.example.demo.models.SessionUids;
import com.toine.example.demo.reference.F1Appendix;

import java.time.Instant;

public record SessionView(
        String sessionUid,          // unsigned decimal - see SessionUids
        int sessionType,
        String sessionTypeName,
        int trackId,
        String trackName,
        Integer trackLengthM,
        Float sector2StartM,
        Float sector3StartM,
        long weekendId,             // 0 = not part of a race weekend (e.g. Time Trial)
        Instant startedAt,
        long lapCount,
        Integer bestLapTimeMs       // fastest valid lap, null if none
) {
    public static SessionView of(Session session, long lapCount, Integer bestLapTimeMs) {
        return new SessionView(SessionUids.format(session.getSessionUid()),
                session.getSessionType(), F1Appendix.sessionType(session.getSessionType()),
                session.getTrackId(), F1Appendix.track(session.getTrackId()),
                session.getTrackLengthM(), session.getSector2StartM(), session.getSector3StartM(),
                session.getWeekendLinkId(), session.getStartedAt(), lapCount, bestLapTimeMs);
    }
}
