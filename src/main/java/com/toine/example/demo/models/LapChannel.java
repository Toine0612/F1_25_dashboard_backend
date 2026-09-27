package com.toine.example.demo.models;

import jakarta.persistence.*;

import java.io.Serializable;
import java.util.Optional;

/** One telemetry channel of one lap, stored as a single array - see the V1 migration. */
@Entity
@Table(name = "lap_channel")
@IdClass(LapChannel.Key.class)
public class LapChannel {

    public record Key(Long lapId, String channel) implements Serializable {}

    @Id
    @Column(name = "lap_id")
    private Long lapId;

    @Id
    @Column(length = 32)
    private String channel;

    @Column(nullable = false)
    private float[] samples;

    protected LapChannel() {}

    public LapChannel(Long lapId, Channel channel, float[] samples) {
        this.lapId = lapId;
        this.channel = channel.key();
        this.samples = samples;
    }

    public Long getLapId() {
        return lapId;
    }

    /** Empty if the stored channel isn't one this version knows - see {@link Channel#find}. */
    public Optional<Channel> getChannel() {
        return Channel.find(channel);
    }

    public float[] getSamples() {
        return samples;
    }
}
