package com.toine.example.demo.models.dto.packets;

public record F1Header(
        int m_packetFormat, // 2025
        int m_gameYear, // Game year - last two digits e.g. 25
        short m_gameMajorVersion, // Game major version - "X.00"
        short m_gameMinorVersion, // Game minor version - "1.XX"
        short m_packetVersion, // Version of this packet type, all start from 1
        short m_packetId, // Identifier for the packet type, see below
        long m_sessionUID, // Unique identifier for the session
        float m_sessionTime, // Session timestamp
        long m_frameIdentifier, // Identifier for the frame the data was retrieved on
        long m_overallFrameIdentifier, // Overall identifier for the frame the data was retrieved on, doesn't go back after flashbacks
        short m_playerCarIndex, // Index of player's car in the array
        short m_secondaryPlayerCarIndex // Index of secondary player's car in the array (splitscreen) 255 if no second player
) {}