package com.toine.example.demo.models.dto.packets;

import com.toine.example.demo.models.dto.sessionHistory.LapHistory;
import com.toine.example.demo.models.dto.sessionHistory.TyreStintHistory;

import java.util.List;

public record F1SessionHistory(
        short m_carIdx,
        short m_numLaps,
        short m_numTyreStints,
        short m_bestLapTimeLapNum,
        short m_bestSector1LapNum,
        short m_bestSector2LapNum,
        short m_bestSector3LapNum,
        List<LapHistory> lapHistoryData,
        List<TyreStintHistory> tyreStintsHistoryData
) {}