package com.toine.example.demo.service.recording;

import com.toine.example.demo.models.dto.packets.F1CarMotion;
import com.toine.example.demo.models.dto.packets.F1CarTelemetry;
import com.toine.example.demo.models.dto.packets.F1LapData;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Joins the Motion, Lap Data and Car Telemetry packets of one frame into a {@link FrameSample}.
 * <p>
 * The spec guarantees that packets sent at the menu rate are all sent together on the same frame and
 * share its frame identifier, so the frame identifier is the join key. A few frames are kept open to
 * tolerate UDP reordering; frames that never complete (a lost packet) simply fall out of the window.
 */
public final class FrameAssembler {

    private static final int OPEN_FRAMES = 8;

    private static final class Parts {
        F1LapData lap;
        F1CarTelemetry telemetry;
        F1CarMotion motion;
    }

    private final Map<Long, Parts> open = new LinkedHashMap<>() {
        @Override
        protected boolean removeEldestEntry(Map.Entry<Long, Parts> eldest) {
            return size() > OPEN_FRAMES;
        }
    };
    private long newestFrame = -1;

    public Optional<FrameSample> add(long frame, F1LapData lap) {
        Parts parts = partsFor(frame);
        parts.lap = lap;
        return completed(frame, parts);
    }

    public Optional<FrameSample> add(long frame, F1CarTelemetry telemetry) {
        Parts parts = partsFor(frame);
        parts.telemetry = telemetry;
        return completed(frame, parts);
    }

    public Optional<FrameSample> add(long frame, F1CarMotion motion) {
        Parts parts = partsFor(frame);
        parts.motion = motion;
        return completed(frame, parts);
    }

    private Parts partsFor(long frame) {
        // Frame identifiers go back after a flashback (and restart with a new session): anything
        // still open is from the abandoned timeline and must not be merged with the new one.
        if (frame < newestFrame - OPEN_FRAMES) {
            open.clear();
            newestFrame = frame;
        }
        newestFrame = Math.max(newestFrame, frame);
        return open.computeIfAbsent(frame, f -> new Parts());
    }

    private Optional<FrameSample> completed(long frame, Parts parts) {
        if (parts.lap == null || parts.telemetry == null || parts.motion == null) return Optional.empty();
        open.remove(frame);
        return Optional.of(FrameSample.of(frame, parts.lap, parts.telemetry, parts.motion));
    }
}
