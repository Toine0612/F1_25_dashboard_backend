package com.toine.example.demo.models.dto.packets;

import com.toine.example.demo.models.dto.sessionHistory.LapHistory;
import com.toine.example.demo.models.dto.sessionHistory.TyreStintHistory;

import java.util.List;

/** PacketSessionHistoryData - the game's own lap-by-lap timing record for one car. */
public record F1SessionHistory(
        int carIdx,
        int numLaps,                // laps in the data, including the current partial lap
        int numTyreStints,
        int bestLapTimeLapNum,
        int bestSector1LapNum,
        int bestSector2LapNum,
        int bestSector3LapNum,
        List<LapHistory> lapHistoryData,        // numLaps entries, index 0 = lap 1
        List<TyreStintHistory> tyreStintsHistoryData
) {
    /** @return the history entry for a 1-based lap number, or {@code null} if the game hasn't reported it */
    public LapHistory lap(int lapNumber) {
        return lapNumber >= 1 && lapNumber <= lapHistoryData.size() ? lapHistoryData.get(lapNumber - 1) : null;
    }
}
