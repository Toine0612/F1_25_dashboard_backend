package com.toine.example.demo.models.dto.api;

import java.time.Instant;

/** Whether telemetry is arriving and what is being recorded - for a "live" indicator in the UI. */
public record LiveStatusView(
        boolean receiving,          // a packet arrived in the last few seconds
        Instant lastPacketAt,
        long packetsReceived,
        long packetsRejected,
        String sessionUid,
        String sessionTypeName,
        String trackName,
        Integer lapNumber,          // lap being recorded, null when not on a timed lap
        Float lapDistance,
        int lapsSaved               // in the current session
) {}
