package com.toine.example.demo.models;

import tools.jackson.databind.annotation.JsonSerialize;
import tools.jackson.databind.ser.std.ToStringSerializer;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
public class Session {

    @Id
    @Column(name = "session_id", nullable = false, updatable = false)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long sessionId; // The F1 session UID

    @Column(name = "session_type", nullable = false)
    private String sessionType; // e.g. "Race", "Time Trial" - see F1Appendix

    @Column(name = "track", nullable = false)
    private String track; // e.g. "Monaco" - see F1Appendix

    protected Session() {}

    public Long getSessionId() {
        return sessionId;
    }

    public String getSessionType() {
        return sessionType;
    }

    public String getTrack() {
        return track;
    }
}