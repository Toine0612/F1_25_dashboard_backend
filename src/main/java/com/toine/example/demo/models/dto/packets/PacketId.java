package com.toine.example.demo.models.dto.packets;

/**
 * Packet type identifiers (header m_packetId) from the F1 25 UDP spec, with the exact packet size the
 * spec documents for each type this backend decodes.
 */
public enum PacketId {
    MOTION(0, 1349),
    SESSION(1, 753),
    LAP_DATA(2, 1285),
    EVENT(3, 45),
    CAR_TELEMETRY(6, 1352),
    SESSION_HISTORY(11, 1460);

    private final int id;
    private final int size;

    PacketId(int id, int size) {
        this.id = id;
        this.size = size;
    }

    public int id() {
        return id;
    }

    public int size() {
        return size;
    }

    /** @return the decoded packet type, or {@code null} for a packet type this backend ignores */
    public static PacketId of(int id) {
        for (PacketId packetId : values()) {
            if (packetId.id == id) return packetId;
        }
        return null;
    }
}
