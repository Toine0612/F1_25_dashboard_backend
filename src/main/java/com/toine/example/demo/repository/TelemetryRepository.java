package com.toine.example.demo.repository;

import com.toine.example.demo.models.Lap;
import com.toine.example.demo.models.Telemetry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TelemetryRepository extends JpaRepository<Telemetry, Long> {
    // Option A: Find by passing the entire Lap entity
    List<Telemetry> findByLap(Lap lap);

    // Option B: Find by passing just the database ID of the Lap
    // Spring translates this to: SELECT * FROM telemetry WHERE lap_id = ?
    List<Telemetry> findByLap_Id(Long lapId);

    // Chronological order within the lap, using the elapsed-time snapshot captured per sample
    List<Telemetry> findByLap_IdOrderByElapsedTimeMsAsc(Long lapId);
}
