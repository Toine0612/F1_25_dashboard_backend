package com.toine.example.demo.models;

/**
 * The telemetry channels recorded for every lap. Each is stored as one array per lap (see
 * {@link LapChannel}); all arrays of a lap are index-aligned, one entry per recorded frame.
 * <p>
 * {@link #DISTANCE} is the primary axis: laps are compared at the same point on track, never at the
 * same elapsed time. Adding a channel means adding a constant here and a mapping in
 * {@code LapTrace.value} - no schema change.
 */
public enum Channel {
    DISTANCE("distance", false),    // m, lap distance from the start/finish line
    TIME("time", true),             // ms, current lap time
    SPEED("speed", true),           // km/h
    THROTTLE("throttle", false),    // 0 - 1
    BRAKE("brake", false),          // 0 - 1
    STEER("steer", false),          // -1 (full left) - 1 (full right)
    GEAR("gear", true),             // -1 = R, 0 = N, 1 - 8
    RPM("rpm", true),
    DRS("drs", true),               // 0 / 1
    X("x", false),                  // m, world position; the track surface is the X/Z plane
    Y("y", false),                  // m, elevation
    Z("z", false);                  // m

    private final String key;
    private final boolean integral;

    Channel(String key, boolean integral) {
        this.key = key;
        this.integral = integral;
    }

    /** Name used in the database and the API. */
    public String key() {
        return key;
    }

    /** Whether every value is a whole number (serialised without decimals). */
    public boolean integral() {
        return integral;
    }

    public static Channel fromKey(String key) {
        for (Channel channel : values()) {
            if (channel.key.equals(key)) return channel;
        }
        throw new IllegalArgumentException("Unknown telemetry channel: " + key);
    }
}
