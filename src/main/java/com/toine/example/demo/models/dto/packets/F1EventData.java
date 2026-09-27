package com.toine.example.demo.models.dto.packets;

import com.toine.example.demo.models.dto.event.EventDetails;

public record F1EventData(
        String eventCode,           // e.g. "SSTA", "SEND", "FLBK"
        EventDetails eventDetails   // null for event codes this backend doesn't act on
) {}
