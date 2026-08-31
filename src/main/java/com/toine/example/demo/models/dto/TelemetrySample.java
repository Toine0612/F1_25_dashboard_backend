package com.toine.example.demo.models.dto;

import com.toine.example.demo.models.dto.packets.F1CarTelemetry;
import com.toine.example.demo.models.dto.packets.F1LapData;

public record TelemetrySample(
        Long frame,
        F1CarTelemetry telemetry,
        F1LapData lapData
) {}
