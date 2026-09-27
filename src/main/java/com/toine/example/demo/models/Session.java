package com.toine.example.demo.models;

import com.toine.example.demo.service.recording.SessionInfo;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "session")
public class Session {

    @Id
    @Column(name = "session_uid", nullable = false, updatable = false)
    private long sessionUid;        // see SessionUids

    private int sessionType;        // see F1Appendix.sessionType
    private int trackId;            // see F1Appendix.track
    @Column(name = "track_length_m")
    private Integer trackLengthM;
    @Column(name = "sector2_start_m")
    private Float sector2StartM;
    @Column(name = "sector3_start_m")
    private Float sector3StartM;
    private long weekendLinkId;     // shared by every session of one race weekend, 0 = none
    private Integer gameMode;
    private Integer formula;

    @Column(nullable = false, updatable = false)
    private Instant startedAt;

    protected Session() {}

    public Session(long sessionUid, Instant startedAt) {
        this.sessionUid = sessionUid;
        this.startedAt = startedAt;
        this.sessionType = 0;       // "Unknown" until a Session packet has been received
        this.trackId = -1;
    }

    public void update(SessionInfo info) {
        this.sessionType = info.sessionType();
        this.trackId = info.trackId();
        this.trackLengthM = info.trackLengthM() > 0 ? info.trackLengthM() : null;
        this.sector2StartM = info.sector2StartM() > 0 ? info.sector2StartM() : null;
        this.sector3StartM = info.sector3StartM() > 0 ? info.sector3StartM() : null;
        this.weekendLinkId = info.weekendLinkId();
        this.gameMode = info.gameMode();
        this.formula = info.formula();
    }

    public long getSessionUid() {
        return sessionUid;
    }

    public int getSessionType() {
        return sessionType;
    }

    public int getTrackId() {
        return trackId;
    }

    public Integer getTrackLengthM() {
        return trackLengthM;
    }

    public Float getSector2StartM() {
        return sector2StartM;
    }

    public Float getSector3StartM() {
        return sector3StartM;
    }

    public long getWeekendLinkId() {
        return weekendLinkId;
    }

    public Integer getGameMode() {
        return gameMode;
    }

    public Integer getFormula() {
        return formula;
    }

    public Instant getStartedAt() {
        return startedAt;
    }
}
