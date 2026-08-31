package com.toine.example.demo.reference;

import java.util.Map;

/**
 * Lookup tables from the F1 25 UDP spec's "Appendices" section
 * ("Data Output from F1 25 v3.pdf"), used to resolve raw protocol codes to human-readable names.
 */
public final class F1Appendix {

    private static final Map<Short, String> SESSION_TYPES = Map.ofEntries(
            Map.entry((short) 0, "Unknown"),
            Map.entry((short) 1, "Practice 1"),
            Map.entry((short) 2, "Practice 2"),
            Map.entry((short) 3, "Practice 3"),
            Map.entry((short) 4, "Short Practice"),
            Map.entry((short) 5, "Qualifying 1"),
            Map.entry((short) 6, "Qualifying 2"),
            Map.entry((short) 7, "Qualifying 3"),
            Map.entry((short) 8, "Short Qualifying"),
            Map.entry((short) 9, "One-Shot Qualifying"),
            Map.entry((short) 10, "Sprint Shootout 1"),
            Map.entry((short) 11, "Sprint Shootout 2"),
            Map.entry((short) 12, "Sprint Shootout 3"),
            Map.entry((short) 13, "Short Sprint Shootout"),
            Map.entry((short) 14, "One-Shot Sprint Shootout"),
            Map.entry((short) 15, "Race"),
            Map.entry((short) 16, "Race 2"),
            Map.entry((short) 17, "Race 3"),
            Map.entry((short) 18, "Time Trial")
    );

    private static final Map<Short, String> TRACKS = Map.ofEntries(
            Map.entry((short) -1, "Unknown"),
            Map.entry((short) 0, "Melbourne"),
            Map.entry((short) 2, "Shanghai"),
            Map.entry((short) 3, "Sakhir (Bahrain)"),
            Map.entry((short) 4, "Catalunya"),
            Map.entry((short) 5, "Monaco"),
            Map.entry((short) 6, "Montreal"),
            Map.entry((short) 7, "Silverstone"),
            Map.entry((short) 9, "Hungaroring"),
            Map.entry((short) 10, "Spa"),
            Map.entry((short) 11, "Monza"),
            Map.entry((short) 12, "Singapore"),
            Map.entry((short) 13, "Suzuka"),
            Map.entry((short) 14, "Abu Dhabi"),
            Map.entry((short) 15, "Texas"),
            Map.entry((short) 16, "Brazil"),
            Map.entry((short) 17, "Austria"),
            Map.entry((short) 19, "Mexico"),
            Map.entry((short) 20, "Baku (Azerbaijan)"),
            Map.entry((short) 26, "Zandvoort"),
            Map.entry((short) 27, "Imola"),
            Map.entry((short) 29, "Jeddah"),
            Map.entry((short) 30, "Miami"),
            Map.entry((short) 31, "Las Vegas"),
            Map.entry((short) 32, "Losail"),
            Map.entry((short) 39, "Silverstone (Reverse)"),
            Map.entry((short) 40, "Austria (Reverse)"),
            Map.entry((short) 41, "Zandvoort (Reverse)")
    );

    private F1Appendix() {}

    public static String sessionType(short code) {
        return SESSION_TYPES.getOrDefault(code, "Unknown (" + code + ")");
    }

    public static String track(short code) {
        return TRACKS.getOrDefault(code, "Unknown (" + code + ")");
    }
}
