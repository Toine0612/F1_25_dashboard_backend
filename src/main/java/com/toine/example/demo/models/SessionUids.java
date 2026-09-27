package com.toine.example.demo.models;

/**
 * The game's session UID is an unsigned 64-bit integer. Java has no unsigned long, so it is kept as
 * its signed bit pattern internally and in the database, and shown in its real (unsigned) decimal
 * form at the API boundary - as a string, because JavaScript numbers can't hold 64-bit integers.
 */
public final class SessionUids {

    private SessionUids() {}

    public static String format(long sessionUid) {
        return Long.toUnsignedString(sessionUid);
    }

    public static long parse(String sessionUid) {
        return Long.parseUnsignedLong(sessionUid);
    }
}
