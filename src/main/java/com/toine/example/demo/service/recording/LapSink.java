package com.toine.example.demo.service.recording;

/** Where the {@link LapRecorder} sends its results. Implementations must not block the caller. */
public interface LapSink {

    /** A lap was completed; replaces any stored lap with the same number in that session. */
    void lapCompleted(RecordedSession session, CompletedLap lap);

    /** A flashback rewound into a lap that was already completed - it is being driven again. */
    void lapRetracted(long sessionUid, int lapNumber);

    /** The game's Session History reports different official timing for an already completed lap. */
    void lapTimingCorrected(long sessionUid, int lapNumber, LapTiming timing);
}
