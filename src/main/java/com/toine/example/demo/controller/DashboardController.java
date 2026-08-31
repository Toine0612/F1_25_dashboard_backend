package com.toine.example.demo.controller;

import com.toine.example.demo.models.Lap;
import com.toine.example.demo.models.Session;
import com.toine.example.demo.models.Telemetry;
import com.toine.example.demo.repository.LapRepository;
import com.toine.example.demo.repository.SessionRepository;
import com.toine.example.demo.repository.TelemetryRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class DashboardController {

    final SessionRepository sessionRepository;
    final LapRepository lapRepository;
    final TelemetryRepository telemetryRepository;

    public DashboardController(
            SessionRepository sessionRepository,
            LapRepository lapRepository,
            TelemetryRepository telemetryRepository
    ) {
        this.sessionRepository = sessionRepository;
        this.lapRepository = lapRepository;
        this.telemetryRepository = telemetryRepository;
    }

    @GetMapping("/sessions")
    public List<Session> getSessions() {
        return sessionRepository.findAll();
    }

    @GetMapping("/{session_id}/laps")
    public List<Lap> getLaps(@PathVariable Long session_id) {
        return lapRepository.findBySession_SessionId(session_id);
    }

    @GetMapping("/{lap_id}/telemetry")
    public List<Telemetry> getTelemetry(@PathVariable Long lap_id) {
        return telemetryRepository.findByLap_IdOrderByElapsedTimeMsAsc(lap_id);
    }

}