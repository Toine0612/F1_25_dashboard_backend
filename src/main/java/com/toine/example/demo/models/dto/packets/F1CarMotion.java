package com.toine.example.demo.models.dto.packets;

/**
 * CarMotionData (Motion packet, one entry per car). World space is Y-up: the track surface lies in
 * the X/Z plane, which is what a top-down track map plots.
 */
public record F1CarMotion(
        float worldPositionX,       // metres
        float worldPositionY,
        float worldPositionZ,
        float worldVelocityX,       // metres/s
        float worldVelocityY,
        float worldVelocityZ,
        float worldForwardDirX,     // normalised (already divided by 32767)
        float worldForwardDirY,
        float worldForwardDirZ,
        float worldRightDirX,
        float worldRightDirY,
        float worldRightDirZ,
        float gForceLateral,
        float gForceLongitudinal,
        float gForceVertical,
        float yaw,                  // radians
        float pitch,
        float roll
) {}
