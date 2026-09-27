package com.toine.example.demo.repository;

/** Lap count and fastest valid lap time (null if none) of one session. */
public record SessionLapStats(long sessionUid, long lapCount, Integer bestLapTimeMs) {}
