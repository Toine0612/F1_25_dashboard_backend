package com.toine.example.demo.repository;

import com.toine.example.demo.models.Session;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface SessionRepository extends JpaRepository<Session, Long> {

    // Atomic upsert so concurrent async flushes for the same brand-new session
    // (see DatabaseFlushService) can't race each other on the session_id primary key.
    // DO UPDATE (rather than DO NOTHING) also self-heals session_type/track/weekend_id if an earlier
    // flush ever raced ahead of the first Session packet and inserted "Unknown" placeholders.
    @Modifying
    @Query(value = "INSERT INTO session (session_id, session_type, track, weekend_id) VALUES (:sessionId, :sessionType, :track, :weekendId) " +
            "ON CONFLICT (session_id) DO UPDATE SET session_type = EXCLUDED.session_type, track = EXCLUDED.track, weekend_id = EXCLUDED.weekend_id",
            nativeQuery = true)
    void upsertSession(@Param("sessionId") Long sessionId, @Param("sessionType") String sessionType, @Param("track") String track, @Param("weekendId") Long weekendId);
}