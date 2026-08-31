package com.toine.example.demo.models.dto.sessionHistory;

public record LapHistory(
        long m_lapTimeInMS,
        int m_sector1TimeMSPart,
        short m_sector1TimeMinutesPart,
        int m_sector2TimeMSPart,
        short m_sector2TimeMinutesPart,
        int m_sector3TimeMSPart,
        short m_sector3TimeMinutesPart,
        short m_lapValidBitFlags
) {}