package com.toine.example.demo.support;

import java.util.ArrayList;
import java.util.List;

/**
 * A made-up ~3.3 km clockwise circuit (long main straight, tight T1, esses, hairpin, chicane, fast
 * sweepers), sampled every metre along its centreline. World coordinates follow the game: the
 * track lies in the X/Z plane, Y is up.
 */
public final class SyntheticCircuit {

    private record Segment(double length, double turnDegrees, double radius) {
        static Segment straight(double length) {
            return new Segment(length, 0, 0);
        }

        /** Positive angles turn left, negative right. */
        static Segment corner(double turnDegrees, double radius) {
            return new Segment(Math.toRadians(Math.abs(turnDegrees)) * radius, turnDegrees, radius);
        }
    }

    // The lengths of the two straights after T5 and T9 are solved so that the loop closes exactly.
    private static final List<Segment> LAYOUT = List.of(
            Segment.straight(850),          // main straight, start/finish line at its start
            Segment.corner(-100, 35),       // T1
            Segment.straight(150),
            Segment.corner(70, 90),         // T2 esses
            Segment.corner(-70, 90),        // T3
            Segment.straight(250),
            Segment.corner(-160, 22),       // T4 hairpin
            Segment.straight(350),
            Segment.corner(120, 60),        // T5
            Segment.straight(289.093),
            Segment.corner(-45, 150),       // T6 fast right
            Segment.straight(120),
            Segment.corner(30, 40),         // T7 chicane
            Segment.corner(-30, 40),        // T8
            Segment.straight(150),
            Segment.corner(-75, 70),        // T9
            Segment.straight(322.830),
            Segment.corner(-100, 60)        // T10 onto the main straight
    );

    private final double[] x;
    private final double[] z;
    private final double[] heading;
    private final double[] curvature;  // 1/m, positive = left
    private final double length;

    public SyntheticCircuit() {
        List<double[]> points = new ArrayList<>();
        double px = 0, pz = 0, h = 0;
        points.add(new double[]{px, pz, h, 0});
        for (Segment segment : LAYOUT) {
            int steps = Math.max(1, (int) Math.round(segment.length));
            double step = segment.length / steps;
            double turnPerStep = Math.toRadians(segment.turnDegrees) / steps;
            double k = segment.radius == 0 ? 0 : Math.signum(segment.turnDegrees) / segment.radius;
            for (int i = 0; i < steps; i++) {
                double mid = h + turnPerStep / 2;
                double chord = turnPerStep == 0 ? step : 2 * segment.radius * Math.sin(Math.abs(turnPerStep) / 2);
                px += Math.cos(mid) * chord;
                pz += Math.sin(mid) * chord;
                h += turnPerStep;
                points.add(new double[]{px, pz, h, k});
            }
        }
        points.removeLast(); // the loop closes onto the first point
        int n = points.size();
        x = new double[n];
        z = new double[n];
        heading = new double[n];
        curvature = new double[n];
        for (int i = 0; i < n; i++) {
            x[i] = points.get(i)[0];
            z[i] = points.get(i)[1];
            heading[i] = points.get(i)[2];
            curvature[i] = points.get(i)[3];
        }
        length = LAYOUT.stream().mapToDouble(Segment::length).sum();
    }

    public double length() {
        return length;
    }

    public int trackLengthMetres() {
        return (int) Math.round(length);
    }

    public float sector2Start() {
        return (float) (length * 0.36);
    }

    public float sector3Start() {
        return (float) (length * 0.70);
    }

    /** Samples every metre; index {@code i} is {@code i * length() / samples()} metres from the line. */
    public int samples() {
        return x.length;
    }

    public double metresPerSample() {
        return length / x.length;
    }

    public double curvatureAt(int index) {
        return curvature[Math.floorMod(index, curvature.length)];
    }

    public double headingAt(double distance) {
        return heading[index(distance)];
    }

    /** World position at a lap distance, shifted sideways by {@code offset} metres (positive = left). */
    public double[] position(double distance, double offset) {
        double exact = distance / metresPerSample();
        int i = (int) Math.floor(exact);
        double t = exact - i;
        int a = Math.floorMod(i, x.length);
        int b = Math.floorMod(i + 1, x.length);
        double h = heading[a];
        double px = x[a] + (x[b] - x[a]) * t - Math.sin(h) * offset;
        double pz = z[a] + (z[b] - z[a]) * t + Math.cos(h) * offset;
        return new double[]{px, pz};
    }

    private int index(double distance) {
        return Math.floorMod((int) Math.floor(distance / metresPerSample()), x.length);
    }
}
