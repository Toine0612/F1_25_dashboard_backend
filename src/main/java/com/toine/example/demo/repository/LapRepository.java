package com.toine.example.demo.repository;

import com.toine.example.demo.models.Lap;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface LapRepository extends JpaRepository<Lap, Long> {

    List<Lap> findBySession_SessionId(Long sessionId);

    // Most recently inserted Lap row for a given lap number in a session, used to attach
    // completion metadata (lap/sector times, validity) once it becomes known.
    Optional<Lap> findFirstBySession_SessionIdAndCurrentLapNumOrderByIdDesc(Long sessionId, short currentLapNum);
}