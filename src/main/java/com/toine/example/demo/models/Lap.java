package com.toine.example.demo.models;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;

@Entity
public class Lap {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @JsonIgnore
    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "session_id")
    private Session session;

    private short currentLapNum;

    private Long lastLapTimeMs;
    private Integer sector1TimeMs;
    private Integer sector2TimeMs;
    private Integer sector3TimeMs;
    private Boolean lapValid;

    protected Lap() {}

    public Lap(Session session, short currentLapNum, Long lastLapTimeMs,
               Integer sector1TimeMs, Integer sector2TimeMs, Integer sector3TimeMs, Boolean lapValid) {
        this.session = session;
        this.currentLapNum = currentLapNum;
        this.lastLapTimeMs = lastLapTimeMs;
        this.sector1TimeMs = sector1TimeMs;
        this.sector2TimeMs = sector2TimeMs;
        this.sector3TimeMs = sector3TimeMs;
        this.lapValid = lapValid;
    }

    public Long getId() {
        return id;
    }

    public Session getSession() {
        return session;
    }

    public short getCurrentLapNum() {
        return currentLapNum;
    }

    public Long getLastLapTimeMs() {
        return lastLapTimeMs;
    }

    public void setLastLapTimeMs(Long lastLapTimeMs) {
        this.lastLapTimeMs = lastLapTimeMs;
    }

    public Integer getSector1TimeMs() {
        return sector1TimeMs;
    }

    public void setSector1TimeMs(Integer sector1TimeMs) {
        this.sector1TimeMs = sector1TimeMs;
    }

    public Integer getSector2TimeMs() {
        return sector2TimeMs;
    }

    public void setSector2TimeMs(Integer sector2TimeMs) {
        this.sector2TimeMs = sector2TimeMs;
    }

    public Integer getSector3TimeMs() {
        return sector3TimeMs;
    }

    public void setSector3TimeMs(Integer sector3TimeMs) {
        this.sector3TimeMs = sector3TimeMs;
    }

    public Boolean getLapValid() {
        return lapValid;
    }

    public void setLapValid(Boolean lapValid) {
        this.lapValid = lapValid;
    }
}
