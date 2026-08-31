package com.toine.example.demo.models.dto.packets;

import com.toine.example.demo.models.dto.event.EventDetails;

public record F1EventData (
        String eventCode,
        EventDetails eventDetails
) {};
