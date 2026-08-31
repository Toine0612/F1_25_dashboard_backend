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
    // DO UPDATE (rather than DO NOTHING) also self-heals session_type/track if an earlier
    // flush ever raced ahead of the first Session packet and inserted "Unknown" placeholders.
    @Modifying
    @Query(value = "INSERT INTO session (session_id, session_type, track) VALUES (:sessionId, :sessionType, :track) " +
            "ON CONFLICT (session_id) DO UPDATE SET session_type = EXCLUDED.session_type, track = EXCLUDED.track",
            nativeQuery = true)
    void upsertSession(@Param("sessionId") Long sessionId, @Param("sessionType") String sessionType, @Param("track") String track);
}