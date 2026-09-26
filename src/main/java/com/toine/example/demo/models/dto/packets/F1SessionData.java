package com.toine.example.demo.models.dto.packets;

public record F1SessionData(
        String sessionType, // resolved via F1Appendix.sessionType(...), e.g. "Race", "Time Trial"
        String track,       // resolved via F1Appendix.track(...), e.g. "Monaco", "Silverstone (Reverse)"
        long weekendId       // m_weekendLinkIdentifier (uint32) - shared by every session in the same race weekend
) {}
