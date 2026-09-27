package com.toine.example.demo.service.recording;

import com.toine.example.demo.models.Channel;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** Converts a lap's samples into index-aligned channel arrays. */
final class LapTrace {

    private LapTrace() {}

    static Map<Channel, float[]> channels(List<FrameSample> samples) {
        Map<Channel, float[]> channels = new EnumMap<>(Channel.class);
        for (Channel channel : Channel.values()) {
            float[] values = new float[samples.size()];
            for (int i = 0; i < values.length; i++) {
                values[i] = value(channel, samples.get(i));
            }
            channels.put(channel, values);
        }
        return channels;
    }

    static float value(Channel channel, FrameSample sample) {
        return switch (channel) {
            case DISTANCE -> sample.lapDistance();
            case TIME -> sample.lapTimeMs();
            case SPEED -> sample.speedKph();
            case THROTTLE -> sample.throttle();
            case BRAKE -> sample.brake();
            case STEER -> sample.steer();
            case GEAR -> sample.gear();
            case RPM -> sample.engineRpm();
            case DRS -> sample.drs() ? 1 : 0;
            case X -> sample.worldX();
            case Y -> sample.worldY();
            case Z -> sample.worldZ();
        };
    }
}
