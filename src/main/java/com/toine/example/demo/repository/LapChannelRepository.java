package com.toine.example.demo.repository;

import com.toine.example.demo.models.LapChannel;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
public interface LapChannelRepository extends JpaRepository<LapChannel, LapChannel.Key> {

    List<LapChannel> findByLapId(long lapId);

    List<LapChannel> findByLapIdAndChannelIn(long lapId, Collection<String> channels);
}
