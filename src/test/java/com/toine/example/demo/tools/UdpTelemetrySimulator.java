package com.toine.example.demo.tools;

import com.toine.example.demo.support.SimulatedSession;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.concurrent.locks.LockSupport;

/**
 * Standalone dev tool (not run by the test suite): plays scripted F1 25 sessions into a running
 * backend over UDP, so the whole pipeline - ingest, lap detection, storage, API, dashboard - can be
 * exercised without the game. The packets are full-size and laid out exactly as in the F1 25 spec.
 * <p>
 * It plays a race weekend (Practice 1 + Race, sharing a weekend id) and a Time Trial, all on a
 * synthetic 3.3 km circuit. Between them they cover: an out-lap that must not be stored, an
 * invalidated lap, a lock-up, a flashback mid-lap and a flashback back across the start/finish line.
 * <p>
 * Run with the backend up: {@code ./mvnw test-compile exec:java
 * -Dexec.mainClass=com.toine.example.demo.tools.UdpTelemetrySimulator -Dexec.classpathScope=test}
 * or run {@code main()} from the IDE. Arguments (all optional):
 * {@code [host] [port] [speed-up factor] [send rate Hz]}, default {@code 127.0.0.1 20777 10 60}.
 */
public class UdpTelemetrySimulator {

    public static void main(String[] args) throws IOException {
        String host = args.length > 0 ? args[0] : "127.0.0.1";
        int port = args.length > 1 ? Integer.parseInt(args[1]) : 20777;
        double speedUp = args.length > 2 ? Double.parseDouble(args[2]) : 10;
        int hz = args.length > 3 ? Integer.parseInt(args[3]) : 60;

        long baseUid = System.currentTimeMillis() * 1000;
        long weekendId = baseUid % 100_000;
        int monza = 11;
        int silverstone = 7;

        try (DatagramSocket socket = new DatagramSocket()) {
            InetAddress address = InetAddress.getByName(host);
            play("Practice 1", SimulatedSession.practice(baseUid + 1, monza, weekendId, hz), socket, address, port, speedUp);
            play("Race", SimulatedSession.race(baseUid + 2, monza, weekendId, hz), socket, address, port, speedUp);
            play("Time Trial", SimulatedSession.timeTrial(baseUid + 3, silverstone, hz), socket, address, port, speedUp);
        }
        System.out.println("Done.");
    }

    private static void play(String name, SimulatedSession session, DatagramSocket socket, InetAddress address,
                             int port, double speedUp) {
        System.out.printf("Playing %s (%.0f m circuit) at %.0fx speed...%n", name, session.circuit().length(), speedUp);
        long startNanos = System.nanoTime();
        int[] sent = {0};
        session.play((packet, sessionTime) -> {
            // Pace the packets so they arrive at speedUp x the game's real rate
            long dueNanos = startNanos + (long) (sessionTime / speedUp * 1e9);
            long waitNanos = dueNanos - System.nanoTime();
            if (waitNanos > 1_000_000) LockSupport.parkNanos(waitNanos);
            try {
                socket.send(new DatagramPacket(packet, packet.length, address, port));
                sent[0]++;
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        });
        System.out.printf("  sent %d packets, laps %s should be recorded%n", sent[0], session.completeLapNumbers());
    }
}
