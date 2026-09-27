package com.toine.example.demo.models.dto.sessionHistory;

/** LapHistoryData, with each sector's minutes and milliseconds parts already combined. */
public record LapHistory(
        long lapTimeInMS,           // 0 while the lap is still in progress
        int sector1TimeMs,
        int sector2TimeMs,
        int sector3TimeMs,
        int lapValidBitFlags        // 0x01 lap valid, 0x02/0x04/0x08 sector 1/2/3 valid
) {
    public boolean lapValid() {
        return (lapValidBitFlags & 0x01) != 0;
    }
}
