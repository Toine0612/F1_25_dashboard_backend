package com.toine.example.demo.repository;

import com.toine.example.demo.models.Lap;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface LapRepository extends JpaRepository<Lap, Long> {

    @Query("""
            select new com.toine.example.demo.repository.SessionLapStats(
                l.session.sessionUid, count(l), min(case when l.valid = true then l.lapTimeMs end))
            from Lap l
            group by l.session.sessionUid""")
    List<SessionLapStats> findSessionLapStats();

    List<Lap> findBySession_SessionUidOrderByLapNumber(long sessionUid);

    Optional<Lap> findBySession_SessionUidAndLapNumber(long sessionUid, int lapNumber);

    @EntityGraph(attributePaths = "session")
    Optional<Lap> findWithSessionById(long id);

    /** Channels go with it (ON DELETE CASCADE). */
    @Modifying
    @Query("delete from Lap l where l.session.sessionUid = :sessionUid and l.lapNumber = :lapNumber")
    int deleteBySessionAndLapNumber(@Param("sessionUid") long sessionUid, @Param("lapNumber") int lapNumber);
}
