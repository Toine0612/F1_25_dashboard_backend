package com.toine.example.demo;

import com.toine.example.demo.support.SimulatedSession;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.time.Duration;
import java.util.concurrent.locks.LockSupport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * End to end: full-size F1 25 packets over a real UDP socket -> lap detection -> PostgreSQL -> REST API.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class TelemetryPipelineIntegrationTest {

    private static final int UDP_PORT = freeUdpPort();
    private static final long SESSION_UID = 0xF000_0000_0000_0001L;   // above Long.MAX_VALUE as unsigned
    private static final int SILVERSTONE = 7;

    @DynamicPropertySource
    static void udpPort(DynamicPropertyRegistry registry) {
        registry.add("udp.telemetry.port", () -> UDP_PORT);
    }

    @Autowired
    private MockMvcTester mvc;

    @Autowired
    private JdbcClient jdbc;

    private final JsonMapper json = JsonMapper.builder().build();

    @Test
    void lapsSentOverUdpAreStoredAndServedByTheApi() throws Exception {
        SimulatedSession session = SimulatedSession.timeTrial(SESSION_UID, SILVERSTONE, 20);
        sendOverUdp(session, 150);

        String sessionUid = Long.toUnsignedString(SESSION_UID);
        JsonNode laps = await().atMost(Duration.ofSeconds(30)).until(
                () -> get("/api/sessions/" + sessionUid + "/laps"), body -> body.size() == 3);

        assertThat(laps.get(0).get("lapNumber").isInt()).isTrue();
        assertThat(laps.get(0).get("lapNumber").asInt()).isEqualTo(1);
        assertThat(laps.get(2).get("lapNumber").asInt()).isEqualTo(3);
        for (JsonNode lap : laps) {
            assertThat(lap.get("lapTimeMs").asInt()).isBetween(50_000, 120_000);
            assertThat(lap.get("valid").asBoolean()).isTrue();
            assertThat(lap.get("sessionUid").asString()).isEqualTo(sessionUid);
        }

        JsonNode sessions = get("/api/sessions");
        JsonNode recorded = sessions.get(0);
        assertThat(recorded.get("sessionUid").asString()).isEqualTo(sessionUid);
        assertThat(recorded.get("trackName").asString()).isEqualTo("Silverstone");
        assertThat(recorded.get("sessionTypeName").asString()).isEqualTo("Time Trial");
        assertThat(recorded.get("trackLengthM").asInt()).isEqualTo(session.circuit().trackLengthMetres());
        assertThat(recorded.get("lapCount").asInt()).isEqualTo(3);
        assertThat(recorded.get("bestLapTimeMs").asInt())
                .isEqualTo(Math.min(laps.get(0).get("lapTimeMs").asInt(),
                        Math.min(laps.get(1).get("lapTimeMs").asInt(), laps.get(2).get("lapTimeMs").asInt())));

        long lapId = laps.get(0).get("id").asLong();
        JsonNode telemetry = get("/api/laps/" + lapId + "/telemetry");
        int samples = telemetry.get("sampleCount").asInt();
        JsonNode channels = telemetry.get("channels");
        assertThat(samples).isEqualTo(laps.get(0).get("sampleCount").asInt()).isGreaterThan(1000);
        for (String channel : new String[]{"distance", "time", "speed", "throttle", "brake", "gear", "x", "y", "z"}) {
            assertThat(channels.get(channel).size()).as(channel).isEqualTo(samples);
        }
        assertThat(channels.get("speed").get(0).isInt()).isTrue();
        assertThat(channels.get("distance").get(0).asDouble()).isLessThan(100);

        JsonNode speedOnly = get("/api/laps/" + lapId + "/telemetry?channels=distance,speed");
        assertThat(speedOnly.get("channels").propertyNames()).containsExactly("distance", "speed");

        // A channel this version doesn't know (e.g. stored by an older build) is left out instead of failing
        jdbc.sql("insert into f1.lap_channel (lap_id, channel, samples) values (?, 'retiredChannel', array[1.0]::real[])")
                .param(lapId)
                .update();
        JsonNode withUnknownChannel = get("/api/laps/" + lapId + "/telemetry");
        assertThat(withUnknownChannel.get("channels").has("retiredChannel")).isFalse();
        assertThat(withUnknownChannel.get("channels").has("speed")).isTrue();

        JsonNode live = get("/api/live");
        assertThat(live.get("packetsReceived").asLong()).isGreaterThan(10_000);
        assertThat(live.get("packetsRejected").asLong()).isZero();

        assertThat(mvc.get().uri("/api/sessions/not-a-number/laps")).hasStatus(400);
        assertThat(mvc.get().uri("/api/laps/999999")).hasStatus(404);
    }

    private JsonNode get(String uri) throws Exception {
        var result = mvc.get().uri(uri).exchange();
        assertThat(result).hasStatusOk();
        return json.readTree(result.getResponse().getContentAsString());
    }

    private static void sendOverUdp(SimulatedSession session, double speedUp) throws IOException {
        try (DatagramSocket socket = new DatagramSocket()) {
            InetAddress localhost = InetAddress.getLoopbackAddress();
            long start = System.nanoTime();
            session.play((packet, sessionTime) -> {
                long wait = start + (long) (sessionTime / speedUp * 1e9) - System.nanoTime();
                if (wait > 500_000) LockSupport.parkNanos(wait);
                try {
                    socket.send(new DatagramPacket(packet, packet.length, localhost, UDP_PORT));
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            });
        }
    }

    private static int freeUdpPort() {
        try (DatagramSocket socket = new DatagramSocket(0)) {
            return socket.getLocalPort();
        } catch (SocketException e) {
            throw new UncheckedIOException(e);
        }
    }
}
