package com.toine.example.demo.models;

import com.toine.example.demo.service.recording.LapTiming;
import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "lap")
public class Lap {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "session_uid")
    private Session session;

    private int lapNumber;
    private int lapTimeMs;
    @Column(name = "sector1_ms")
    private Integer sector1Ms;
    @Column(name = "sector2_ms")
    private Integer sector2Ms;
    @Column(name = "sector3_ms")
    private Integer sector3Ms;
    private boolean valid;
    private boolean pitIn;          // lap ended in the pit lane
    private boolean pitOut;         // lap started in the pit lane
    private int sampleCount;
    private Instant recordedAt;

    protected Lap() {}

    public Lap(Session session, int lapNumber, LapTiming timing, boolean pitIn, boolean pitOut,
               int sampleCount, Instant recordedAt) {
        this.session = session;
        this.lapNumber = lapNumber;
        this.pitIn = pitIn;
        this.pitOut = pitOut;
        this.sampleCount = sampleCount;
        this.recordedAt = recordedAt;
        setTiming(timing);
    }

    public void setTiming(LapTiming timing) {
        this.lapTimeMs = timing.lapTimeMs();
        this.sector1Ms = timing.sector1Ms();
        this.sector2Ms = timing.sector2Ms();
        this.sector3Ms = timing.sector3Ms();
        this.valid = timing.valid();
    }

    public Long getId() {
        return id;
    }

    public Session getSession() {
        return session;
    }

    public int getLapNumber() {
        return lapNumber;
    }

    public int getLapTimeMs() {
        return lapTimeMs;
    }

    public Integer getSector1Ms() {
        return sector1Ms;
    }

    public Integer getSector2Ms() {
        return sector2Ms;
    }

    public Integer getSector3Ms() {
        return sector3Ms;
    }

    public boolean isValid() {
        return valid;
    }

    public boolean isPitIn() {
        return pitIn;
    }

    public boolean isPitOut() {
        return pitOut;
    }

    public int getSampleCount() {
        return sampleCount;
    }

    public Instant getRecordedAt() {
        return recordedAt;
    }
}
