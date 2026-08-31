package com.toine.example.demo.models;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.toine.example.demo.models.dto.packets.F1CarTelemetry;
import jakarta.persistence.*;

@Entity
public class Telemetry {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private long frame;
    private float lapDistance;
    private long elapsedTimeMs;

    @JsonIgnore
    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "lap_id")
    private Lap lap;

    private short speed;
    private float throttle;
    private float steer;
    private float brake;
    private int clutch;
    private int gear;
    private short engineRPM;
    private short engineTemp;

    private short frontLeftBrakeTemp;
    private short frontRightBrakeTemp;
    private short rearLeftBrakeTemp;
    private short rearRightBrakeTemp;

    private int frontLeftTireSurfaceTemp;
    private int frontRightTireSurfaceTemp;
    private int rearLeftTireSurfaceTemp;
    private int rearRightTireSurfaceTemp;

    private int frontLeftInnerTireSurfaceTemp;
    private int frontRightInnerTireSurfaceTemp;
    private int rearLeftInnerTireSurfaceTemp;
    private int rearRightInnerTireSurfaceTemp;

    private float frontLeftTirePressure;
    private float frontRightTirePressure;
    private float rearLeftTirePressure;
    private float rearRightTirePressure;

    private int frontLeftsurfaceType;
    private int frontRightsurfaceType;
    private int rearLeftsurfaceType;
    private int rearRightsurfaceType;


    protected Telemetry() {
    }

    public Telemetry(Long frame, Lap lap, F1CarTelemetry telemetry, float lapDistance, long elapsedTimeMs) {
        this.frame = frame;
        this.lap = lap;
        this.lapDistance = lapDistance;
        this.elapsedTimeMs = elapsedTimeMs;

        this.speed = telemetry.speed();
        this.throttle = telemetry.throttle();
        this.steer = telemetry.steer();
        this.brake = telemetry.brake();
        this.clutch = telemetry.clutch();
        this.gear = telemetry.gear();
        this.engineRPM = telemetry.engineRPM();
        this.engineTemp = telemetry.engineTemp();

        this.frontLeftBrakeTemp = telemetry.frontLeftBrakeTemp();
        this.frontRightBrakeTemp = telemetry.frontRightBrakeTemp();
        this.rearLeftBrakeTemp = telemetry.rearLeftBrakeTemp();
        this.rearRightBrakeTemp = telemetry.rearRightBrakeTemp();

        this.frontLeftTireSurfaceTemp = telemetry.frontLeftTireSurfaceTemp();
        this.frontRightTireSurfaceTemp = telemetry.frontRightTireSurfaceTemp();
        this.rearLeftTireSurfaceTemp = telemetry.rearLeftTireSurfaceTemp();
        this.rearRightTireSurfaceTemp = telemetry.rearRightTireSurfaceTemp();

        this.frontLeftInnerTireSurfaceTemp = telemetry.frontLeftInnerTireSurfaceTemp();
        this.frontRightInnerTireSurfaceTemp = telemetry.frontRightInnerTireSurfaceTemp();
        this.rearLeftInnerTireSurfaceTemp = telemetry.rearLeftInnerTireSurfaceTemp();
        this.rearRightInnerTireSurfaceTemp = telemetry.rearRightInnerTireSurfaceTemp();

        this.frontLeftTirePressure = telemetry.frontLeftTirePressure();
        this.frontRightTirePressure = telemetry.frontRightTirePressure();
        this.rearLeftTirePressure = telemetry.rearLeftTirePressure();
        this.rearRightTirePressure = telemetry.rearRightTirePressure();

        this.frontLeftsurfaceType = telemetry.frontLeftsurfaceType();
        this.frontRightsurfaceType = telemetry.frontRightsurfaceType();
        this.rearLeftsurfaceType = telemetry.rearLeftsurfaceType();
        this.rearRightsurfaceType = telemetry.rearRightsurfaceType();
    }

    public Long getId() {
        return id;
    }

    public long getFrame() {
        return frame;
    }

    public Lap getLap() {
        return lap;
    }

    public short getSpeed() {
        return speed;
    }

    public float getThrottle() {
        return throttle;
    }

    public float getSteer() {
        return steer;
    }

    public float getBrake() {
        return brake;
    }

    public int getClutch() {
        return clutch;
    }

    public int getGear() {
        return gear;
    }

    public short getEngineRPM() {
        return engineRPM;
    }

    public short getEngineTemp() {
        return engineTemp;
    }

    public Float getLapDistance() {
        return lapDistance;
    }

    public Long getElapsedTimeMs() {
        return elapsedTimeMs;
    }

    public short getFrontLeftBrakeTemp() {
        return frontLeftBrakeTemp;
    }

    public short getFrontRightBrakeTemp() {
        return frontRightBrakeTemp;
    }

    public short getRearLeftBrakeTemp() {
        return rearLeftBrakeTemp;
    }

    public short getRearRightBrakeTemp() {
        return rearRightBrakeTemp;
    }

    public int getFrontLeftTireSurfaceTemp() {
        return frontLeftTireSurfaceTemp;
    }

    public int getFrontRightTireSurfaceTemp() {
        return frontRightTireSurfaceTemp;
    }

    public int getRearLeftTireSurfaceTemp() {
        return rearLeftTireSurfaceTemp;
    }

    public int getRearRightTireSurfaceTemp() {
        return rearRightTireSurfaceTemp;
    }

    public int getFrontLeftInnerTireSurfaceTemp() {
        return frontLeftInnerTireSurfaceTemp;
    }

    public int getFrontRightInnerTireSurfaceTemp() {
        return frontRightInnerTireSurfaceTemp;
    }

    public int getRearLeftInnerTireSurfaceTemp() {
        return rearLeftInnerTireSurfaceTemp;
    }

    public int getRearRightInnerTireSurfaceTemp() {
        return rearRightInnerTireSurfaceTemp;
    }

    public float getFrontLeftTirePressure() {
        return frontLeftTirePressure;
    }

    public float getFrontRightTirePressure() {
        return frontRightTirePressure;
    }

    public float getRearLeftTirePressure() {
        return rearLeftTirePressure;
    }

    public float getRearRightTirePressure() {
        return rearRightTirePressure;
    }

    public int getFrontLeftsurfaceType() {
        return frontLeftsurfaceType;
    }

    public int getFrontRightsurfaceType() {
        return frontRightsurfaceType;
    }

    public int getRearLeftsurfaceType() {
        return rearLeftsurfaceType;
    }

    public int getRearRightsurfaceType() {
        return rearRightsurfaceType;
    }
}
