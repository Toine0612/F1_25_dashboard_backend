package com.toine.example.demo.reference;

import java.util.Map;

/**
 * Lookup tables from the F1 25 UDP spec's "Appendices" section
 * ("Data Output from F1 25 v3.pdf"), used to resolve raw protocol codes to human-readable names.
 */
public final class F1Appendix {

    private static final Map<Integer, String> SESSION_TYPES = Map.ofEntries(
            Map.entry(0, "Unknown"),
            Map.entry(1, "Practice 1"),
            Map.entry(2, "Practice 2"),
            Map.entry(3, "Practice 3"),
            Map.entry(4, "Short Practice"),
            Map.entry(5, "Qualifying 1"),
            Map.entry(6, "Qualifying 2"),
            Map.entry(7, "Qualifying 3"),
            Map.entry(8, "Short Qualifying"),
            Map.entry(9, "One-Shot Qualifying"),
            Map.entry(10, "Sprint Shootout 1"),
            Map.entry(11, "Sprint Shootout 2"),
            Map.entry(12, "Sprint Shootout 3"),
            Map.entry(13, "Short Sprint Shootout"),
            Map.entry(14, "One-Shot Sprint Shootout"),
            Map.entry(15, "Race"),
            Map.entry(16, "Race 2"),
            Map.entry(17, "Race 3"),
            Map.entry(18, "Time Trial")
    );

    private static final Map<Integer, String> TRACKS = Map.ofEntries(
            Map.entry(-1, "Unknown"),
            Map.entry(0, "Melbourne"),
            Map.entry(2, "Shanghai"),
            Map.entry(3, "Sakhir (Bahrain)"),
            Map.entry(4, "Catalunya"),
            Map.entry(5, "Monaco"),
            Map.entry(6, "Montreal"),
            Map.entry(7, "Silverstone"),
            Map.entry(9, "Hungaroring"),
            Map.entry(10, "Spa"),
            Map.entry(11, "Monza"),
            Map.entry(12, "Singapore"),
            Map.entry(13, "Suzuka"),
            Map.entry(14, "Abu Dhabi"),
            Map.entry(15, "Texas"),
            Map.entry(16, "Brazil"),
            Map.entry(17, "Austria"),
            Map.entry(19, "Mexico"),
            Map.entry(20, "Baku (Azerbaijan)"),
            Map.entry(26, "Zandvoort"),
            Map.entry(27, "Imola"),
            Map.entry(29, "Jeddah"),
            Map.entry(30, "Miami"),
            Map.entry(31, "Las Vegas"),
            Map.entry(32, "Losail"),
            Map.entry(39, "Silverstone (Reverse)"),
            Map.entry(40, "Austria (Reverse)"),
            Map.entry(41, "Zandvoort (Reverse)")
    );

    private F1Appendix() {}

    public static String sessionType(int code) {
        return SESSION_TYPES.getOrDefault(code, "Unknown (" + code + ")");
    }

    public static String track(int code) {
        return TRACKS.getOrDefault(code, "Unknown (" + code + ")");
    }
}
