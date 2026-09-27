package com.toine.example.demo.support;

import com.toine.example.demo.models.dto.packets.F1CarMotion;
import com.toine.example.demo.models.dto.packets.F1CarTelemetry;
import com.toine.example.demo.models.dto.packets.F1LapData;
import com.toine.example.demo.models.dto.packets.F1SessionData;
import com.toine.example.demo.models.dto.sessionHistory.LapHistory;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * A scripted F1 25 session: the packet stream the game sends for the player's car while it drives a
 * {@link SyntheticCircuit}. The driving model is simple but physically plausible - corner speeds
 * limited by grip, speed-dependent acceleration and braking, a racing line that cuts to the apex -
 * and every lap can be driven differently, so comparing laps shows real differences.
 * <p>
 * The script: session start, time in the garage, then the car is released either before the line
 * (a flying start) or at the pit exit (an out-lap), drives the given laps (optionally with a
 * flashback), crosses the line once more, returns to the garage and the session ends.
 */
public final class SimulatedSession {

    /** How one lap is driven. */
    public record LapStyle(double grip, double braking, double lineWidth, Double mistakeAtFraction, boolean invalid) {
        public static LapStyle clean(double grip, double braking, double lineWidth) {
            return new LapStyle(grip, braking, lineWidth, null, false);
        }

        public LapStyle withMistakeAt(double fraction) {
            return new LapStyle(grip, braking, lineWidth, fraction, invalid);
        }

        public LapStyle invalidated() {
            return new LapStyle(grip, braking, lineWidth, mistakeAtFraction, true);
        }
    }

    /** Rewind {@code seconds} when lap {@code lapNumber} reaches {@code atFraction} of its distance. */
    public record Flashback(int lapNumber, double atFraction, double seconds) {}

    public interface PacketSink {
        void send(byte[] packet, double sessionTimeSeconds);
    }

    private static final double G = 9.81;
    private static final double TOP_SPEED = 92;            // m/s, ~330 km/h
    private static final int OTHER_CAR_INDEX = 5;

    private final SyntheticCircuit circuit = new SyntheticCircuit();
    private final F1PacketWriter writer;
    private final int sessionType;
    private final int trackId;
    private final long weekendId;
    private final int hz;
    private final double releaseDistance;
    private final List<LapStyle> laps;
    private final List<Flashback> flashbacks;
    private final List<LapModel> models = new ArrayList<>();

    private SimulatedSession(long sessionUid, int sessionType, int trackId, long weekendId, int hz,
                             double releaseDistance, List<LapStyle> laps, List<Flashback> flashbacks) {
        this.writer = new F1PacketWriter(sessionUid, 0);
        this.sessionType = sessionType;
        this.trackId = trackId;
        this.weekendId = weekendId;
        this.hz = hz;
        this.releaseDistance = releaseDistance;
        this.laps = laps;
        this.flashbacks = flashbacks;
        for (LapStyle lap : laps) models.add(new LapModel(circuit, lap));
    }

    /**
     * Time Trial: flying start, three laps (the second runs wide out of T1), a flashback in the middle
     * of lap 2 and one straight after the line of lap 3 that rewinds into lap 3.
     */
    public static SimulatedSession timeTrial(long sessionUid, int trackId, int hz) {
        return new SimulatedSession(sessionUid, 18, trackId, 0, hz, -250,
                List.of(LapStyle.clean(1.00, 1.00, 4.0),
                        LapStyle.clean(1.02, 1.05, 5.0).withMistakeAt(0.285),
                        LapStyle.clean(1.03, 1.08, 5.5)),
                List.of(new Flashback(2, 0.55, 3.0), new Flashback(4, 0.03, 3.0)));
    }

    /** Practice: an out-lap from the pit exit (not a complete lap), three laps, the third invalidated. */
    public static SimulatedSession practice(long sessionUid, int trackId, long weekendId, int hz) {
        return new SimulatedSession(sessionUid, 1, trackId, weekendId, hz, 420,
                List.of(LapStyle.clean(0.85, 0.80, 2.0),
                        LapStyle.clean(0.97, 0.95, 3.5),
                        LapStyle.clean(1.00, 1.00, 4.5),
                        LapStyle.clean(1.01, 1.02, 5.0).invalidated()),
                List.of());
    }

    /** Race: rolling start before the line, four laps getting faster as the fuel burns off. */
    public static SimulatedSession race(long sessionUid, int trackId, long weekendId, int hz) {
        return new SimulatedSession(sessionUid, 15, trackId, weekendId, hz, -250,
                List.of(LapStyle.clean(0.96, 0.95, 4.0),
                        LapStyle.clean(0.98, 0.97, 4.5),
                        LapStyle.clean(0.99, 1.00, 4.5).withMistakeAt(0.62),
                        LapStyle.clean(1.00, 1.01, 5.0)),
                List.of());
    }

    public SyntheticCircuit circuit() {
        return circuit;
    }

    /** Laps the backend should record: every lap that starts at the line. */
    public List<Integer> completeLapNumbers() {
        List<Integer> numbers = new ArrayList<>();
        for (int lap = releaseDistance > 0 ? 2 : 1; lap <= laps.size(); lap++) numbers.add(lap);
        return numbers;
    }

    public void play(PacketSink sink) {
        new Run(sink).play();
    }

    /** Car state on one frame - immutable, so a flashback can restore an earlier one. */
    private record State(long frame, double sessionTime, int lapNumber, double lapDistance, double lapTime,
                         int lastLapTimeMs, int sector1Ms, int sector2Ms, boolean invalid, int driverStatus,
                         List<LapHistory> history) {}

    private final class Run {
        private final PacketSink sink;
        private final Deque<State> recent = new ArrayDeque<>();
        private final List<Flashback> pendingFlashbacks = new ArrayList<>(flashbacks);
        private State state;
        private long nextFrame = 1;
        private double nextSessionPacket = 0;
        private double nextHistoryPacket = 0;

        Run(PacketSink sink) {
            this.sink = sink;
        }

        void play() {
            state = new State(0, 0, 1, 20, 0, 0, 0, 0, false, 0, List.of());
            sink.send(writer.event(0, 0, "SSTA"), 0);

            idleInGarage(2.0);
            state = new State(state.frame(), state.sessionTime(), 1, releaseDistance, 0, 0, 0, 0, false,
                    releaseDistance > 0 ? 3 : 1, state.history());
            while (state.lapNumber() <= laps.size() || state.lapTime() < 3.0) {
                drive();
            }
            idleInGarage(1.0);
            sink.send(writer.event(state.frame(), (float) state.sessionTime(), "SEND"), state.sessionTime());
        }

        private void idleInGarage(double seconds) {
            double until = state.sessionTime() + seconds;
            while (state.sessionTime() < until) {
                state = new State(nextFrame++, state.sessionTime() + 1.0 / hz, state.lapNumber(), state.lapDistance(),
                        state.lapTime(), state.lastLapTimeMs(), state.sector1Ms(), state.sector2Ms(), state.invalid(), 0,
                        state.history());
                emit(0, 0, 0);
            }
        }

        private void drive() {
            double dt = 1.0 / hz;
            LapModel model = models.get(Math.min(state.lapNumber(), laps.size()) - 1);
            double d = state.lapDistance();
            double v = model.speedAt(d);
            double next = d + v * dt;

            int lapNumber = state.lapNumber();
            double lapTime = d < 0 ? 0 : state.lapTime() + dt;
            int lastLapTimeMs = state.lastLapTimeMs();
            int sector1Ms = state.sector1Ms();
            int sector2Ms = state.sector2Ms();
            boolean invalid = state.invalid();
            int driverStatus = state.driverStatus();
            List<LapHistory> history = state.history();

            if (d < 0 && next >= 0) {
                lapTime = dt * next / (next - d);           // crossed the line for the first time
            }
            if (d < circuit.sector2Start() && next >= circuit.sector2Start()) {
                sector1Ms = (int) Math.round(crossingTime(state.lapTime(), dt, d, next, circuit.sector2Start()) * 1000);
            }
            if (d < circuit.sector3Start() && next >= circuit.sector3Start()) {
                sector2Ms = (int) Math.round(crossingTime(state.lapTime(), dt, d, next, circuit.sector3Start()) * 1000) - sector1Ms;
            }
            if (lapNumber <= laps.size() && laps.get(lapNumber - 1).invalid() && next > circuit.length() * 0.4) {
                invalid = true;
            }
            if (next >= circuit.length()) {
                int lapTimeMs = (int) Math.round(crossingTime(state.lapTime(), dt, d, next, circuit.length()) * 1000);
                List<LapHistory> completed = new ArrayList<>(history);
                completed.add(new LapHistory(lapTimeMs, sector1Ms, sector2Ms, lapTimeMs - sector1Ms - sector2Ms,
                        invalid ? 0x00 : 0x0F));
                history = List.copyOf(completed);
                lastLapTimeMs = lapTimeMs;
                lapNumber++;
                next -= circuit.length();
                lapTime = next / v;
                sector1Ms = 0;
                sector2Ms = 0;
                invalid = false;
                driverStatus = lapNumber > laps.size() ? 2 : 1;  // in-lap after the last lap
            }

            state = new State(nextFrame++, state.sessionTime() + dt, lapNumber, next, lapTime, lastLapTimeMs,
                    sector1Ms, sector2Ms, invalid, driverStatus, history);
            remember(state);
            emit(v, model.throttleAt(next), model.brakeAt(next));
            flashbackIfDue();
        }

        private void flashbackIfDue() {
            for (Flashback flashback : pendingFlashbacks) {
                if (state.lapNumber() == flashback.lapNumber() && state.lapDistance() >= flashback.atFraction() * circuit.length()) {
                    pendingFlashbacks.remove(flashback);
                    State target = recent.stream()
                            .filter(s -> s.sessionTime() <= state.sessionTime() - flashback.seconds())
                            .reduce((a, b) -> b).orElseThrow();
                    sink.send(writer.flashbackEvent(state.frame(), (float) state.sessionTime(), target.frame(),
                            (float) target.sessionTime()), state.sessionTime());
                    while (!recent.isEmpty() && recent.peekLast().frame() > target.frame()) recent.removeLast();
                    state = target;
                    nextFrame = target.frame() + 1;   // the game's frame identifier goes back too
                    nextSessionPacket = state.sessionTime();
                    nextHistoryPacket = state.sessionTime();
                    return;
                }
            }
        }

        private void remember(State s) {
            recent.addLast(s);
            while (recent.size() > hz * 10) recent.removeFirst();
        }

        private void emit(double speed, double throttle, double brake) {
            long frame = state.frame();
            float time = (float) state.sessionTime();
            double d = state.lapDistance();
            LapModel model = models.get(Math.min(state.lapNumber(), laps.size()) - 1);
            double[] position = circuit.position(d, speed == 0 ? 0 : model.offsetAt(d));
            double heading = circuit.headingAt(d);
            float elevation = (float) (4 * Math.sin(2 * Math.PI * Math.floorMod((long) d, (long) circuit.length()) / circuit.length()));

            sink.send(writer.motion(frame, time, new F1CarMotion((float) position[0], elevation, (float) position[1],
                    (float) (Math.cos(heading) * speed), 0, (float) (Math.sin(heading) * speed),
                    (float) Math.cos(heading), 0, (float) Math.sin(heading), 0, 0, 0,
                    0, 0, 1, (float) heading, 0, 0)), state.sessionTime());

            int sector = d < circuit.sector2Start() ? 0 : d < circuit.sector3Start() ? 1 : 2;
            sink.send(writer.lapData(frame, time, new F1LapData(state.lastLapTimeMs(), Math.round(state.lapTime() * 1000),
                    state.sector1Ms() % 60_000, state.sector1Ms() / 60_000, state.sector2Ms() % 60_000, state.sector2Ms() / 60_000,
                    0, 0, 0, 0, (float) d, (float) (d + (state.lapNumber() - 1) * circuit.length()), 0,
                    1, state.lapNumber(), 0, 0, sector, state.invalid() ? 1 : 0, 0, 0, 0, 0, 0, 1,
                    state.driverStatus(), 2, 0, 0, 0, 0, 0f, 255)), state.sessionTime());

            int kph = (int) Math.round(speed * 3.6);
            int gear = gear(kph);
            sink.send(writer.carTelemetry(frame, time, new F1CarTelemetry(kph, (float) throttle,
                    speed == 0 ? 0 : (float) models.getFirst().steerAt(d), (float) brake, 0, gear, rpm(kph, gear),
                    0, 0, 0, new int[]{520, 520, 610, 610}, new int[]{95, 95, 98, 98}, new int[]{100, 100, 102, 102},
                    105, new float[]{22.5f, 22.5f, 24f, 24f}, new int[]{0, 0, 0, 0})), state.sessionTime());

            if (state.sessionTime() >= nextSessionPacket) {
                nextSessionPacket += 0.5;
                sink.send(writer.session(time, new F1SessionData(0, 32, 24, laps.size(), circuit.trackLengthMetres(),
                        sessionType, trackId, 0, weekendId, 0, sessionType == 18 ? 5 : 28,
                        circuit.sector2Start(), circuit.sector3Start())), state.sessionTime());
            }
            if (state.sessionTime() >= nextHistoryPacket) {
                nextHistoryPacket += 0.25;
                List<LapHistory> withCurrentLap = new ArrayList<>(state.history());
                withCurrentLap.add(new LapHistory(0, state.sector1Ms(), state.sector2Ms(), 0, 0));
                sink.send(writer.sessionHistory(time, 0, withCurrentLap), state.sessionTime());
                // Another car's history, which must never be mistaken for the player's
                sink.send(writer.sessionHistory(time, OTHER_CAR_INDEX,
                        List.of(new LapHistory(99_999, 30_000, 30_000, 39_999, 0x0F), new LapHistory(0, 0, 0, 0, 0))),
                        state.sessionTime());
            }
        }
    }

    private static double crossingTime(double lapTimeBefore, double dt, double from, double to, double at) {
        return lapTimeBefore + dt * (at - from) / (to - from);
    }

    private static int gear(int kph) {
        int[] upshiftAt = {95, 130, 165, 200, 235, 270, 300};
        for (int gear = 0; gear < upshiftAt.length; gear++) {
            if (kph < upshiftAt[gear]) return gear + 1;
        }
        return 8;
    }

    private static int rpm(int kph, int gear) {
        int[] bands = {0, 95, 130, 165, 200, 235, 270, 300, 345};
        double fraction = (kph - bands[gear - 1]) / (double) (bands[gear] - bands[gear - 1]);
        return (int) Math.round(Math.max(4000, Math.min(12500, 8500 + fraction * 3800)));
    }

    /** Speed, pedals and line for one lap style, per circuit sample. */
    private static final class LapModel {
        private final SyntheticCircuit circuit;
        private final double[] speed;
        private final double[] throttle;
        private final double[] brake;
        private final double[] offset;
        private final double[] smoothCurvature;

        LapModel(SyntheticCircuit circuit, LapStyle style) {
            this.circuit = circuit;
            int n = circuit.samples();
            double ds = circuit.metresPerSample();
            smoothCurvature = smooth(circuit, 12);
            double[] apexCurvature = smooth(circuit, 45);

            offset = new double[n];
            double[] limit = new double[n];
            for (int i = 0; i < n; i++) {
                offset[i] = style.lineWidth() * Math.clamp(apexCurvature[i] * 45, -1, 1);
                // A wider line opens the corner up a little
                double k = Math.abs(smoothCurvature[i]) * (1 - 0.035 * style.lineWidth());
                limit[i] = Math.min(TOP_SPEED, Math.sqrt(3.0 * G * style.grip() / Math.max(k, 1e-6)));
            }
            if (style.mistakeAtFraction() != null) {
                int at = (int) (style.mistakeAtFraction() * n);
                for (int i = at - 60; i < at + 120; i++) {
                    int j = Math.floorMod(i, n);
                    limit[j] *= 0.72;                                   // locked up, ran wide
                    offset[j] -= Math.signum(apexCurvature[j]) * 6 * Math.sin(Math.PI * (i - at + 60) / 180.0);
                }
            }

            speed = limit.clone();
            for (int round = 0; round < 2; round++) {
                for (int i = n - 1; i >= 0; i--) {
                    double after = speed[(i + 1) % n];
                    speed[i] = Math.min(speed[i], Math.sqrt(after * after + 2 * braking(style, after) * ds));
                }
                for (int i = 0; i < n; i++) {
                    int j = (i + 1) % n;
                    speed[j] = Math.min(speed[j], Math.sqrt(speed[i] * speed[i] + 2 * acceleration(speed[i]) * ds));
                }
            }

            throttle = new double[n];
            brake = new double[n];
            for (int i = 0; i < n; i++) {
                double after = speed[(i + 1) % n];
                double a = (after * after - speed[i] * speed[i]) / (2 * ds);
                if (a > 0.2) {
                    throttle[i] = Math.min(1, 0.35 + 0.65 * a / acceleration(speed[i]));
                } else if (a < -0.5) {
                    brake[i] = Math.min(1, -a / (50 * style.braking()));  // trails off as downforce drops
                } else {
                    throttle[i] = speed[i] >= TOP_SPEED - 0.5 ? 1 : 0.3;
                }
            }
        }

        double speedAt(double distance) {
            return interpolate(speed, distance);
        }

        double throttleAt(double distance) {
            return throttle[index(distance)];
        }

        double brakeAt(double distance) {
            return brake[index(distance)];
        }

        double offsetAt(double distance) {
            return interpolate(offset, distance);
        }

        double steerAt(double distance) {
            return Math.clamp(-smoothCurvature[index(distance)] * 20, -1, 1);
        }

        private static double acceleration(double v) {
            return 13 * (1 - (v / TOP_SPEED) * (v / TOP_SPEED));
        }

        private static double braking(LapStyle style, double v) {
            return style.braking() * (20 + 30 * (v / TOP_SPEED) * (v / TOP_SPEED));
        }

        private int index(double distance) {
            return Math.floorMod((int) Math.floor(distance / circuit.metresPerSample()), speed.length);
        }

        private double interpolate(double[] values, double distance) {
            double exact = distance / circuit.metresPerSample();
            int i = (int) Math.floor(exact);
            double t = exact - i;
            return values[Math.floorMod(i, values.length)] * (1 - t) + values[Math.floorMod(i + 1, values.length)] * t;
        }

        private static double[] smooth(SyntheticCircuit circuit, int radius) {
            int n = circuit.samples();
            double[] out = new double[n];
            for (int i = 0; i < n; i++) {
                double sum = 0;
                for (int j = -radius; j <= radius; j++) sum += circuit.curvatureAt(i + j);
                out[i] = sum / (2 * radius + 1);
            }
            return out;
        }
    }
}
