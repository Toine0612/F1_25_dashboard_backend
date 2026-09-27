package com.toine.example.demo.models.dto.packets;

/** PacketHeader - the 29 bytes every F1 25 packet starts with. */
public record F1Header(
        int packetFormat,           // 2025
        int gameYear,               // last two digits, e.g. 25
        int gameMajorVersion,
        int gameMinorVersion,
        int packetVersion,
        int packetId,               // see PacketId
        long sessionUid,            // uint64 kept as its signed bit pattern - see SessionUids
        float sessionTime,          // seconds since the session started
        long frameIdentifier,       // goes BACK after a flashback
        long overallFrameIdentifier, // never goes back
        int playerCarIndex,
        int secondaryPlayerCarIndex // 255 if no second player
) {}
