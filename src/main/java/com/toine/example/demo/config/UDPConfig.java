package com.toine.example.demo.config;

import com.toine.example.demo.models.dto.packets.F1Header;
import com.toine.example.demo.models.dto.packets.PacketId;
import com.toine.example.demo.service.TelemetryParser;
import com.toine.example.demo.service.TelemetryService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.integration.dsl.IntegrationFlow;
import org.springframework.integration.ip.dsl.Udp;
import org.springframework.integration.ip.dsl.UdpInboundChannelAdapterSpec;
import org.springframework.messaging.Message;

import static com.toine.example.demo.service.TelemetryService.F1_HEADER;

@Configuration
public class UDPConfig {

    // At 60 Hz the game sends ~400 packets/s (~0.5 MB/s); a large socket buffer absorbs GC pauses.
    private static final int SOCKET_RECEIVE_BUFFER_BYTES = 4 * 1024 * 1024;
    // Largest F1 25 packet (Session History) is 1460 bytes.
    private static final int MAX_PACKET_BYTES = 2048;

    private final int port;
    private final String bindAddress;
    private final TelemetryParser parser;
    private final TelemetryService telemetryService;

    public UDPConfig(TelemetryParser parser,
                     TelemetryService telemetryService,
                     @Value("${udp.telemetry.port:20777}") int port,
                     @Value("${udp.telemetry.bind-address:}") String bindAddress) {
        this.parser = parser;
        this.telemetryService = telemetryService;
        this.port = port;
        this.bindAddress = bindAddress;
    }

    @Bean
    public IntegrationFlow udpInboundFlow() {
        return IntegrationFlow.from(udpInboundAdapterSpec())
                .filter(byte[].class, telemetryService::accept)
                .enrichHeaders(headers -> headers.headerFunction(F1_HEADER,
                        (Message<byte[]> message) -> parser.parseHeader(message.getPayload())))
                .route(Message.class,
                        message -> message.getHeaders().get(F1_HEADER, F1Header.class).packetId(),
                        mapping -> mapping
                                .channelMapping(PacketId.MOTION.id(), "motionChannel")
                                .channelMapping(PacketId.SESSION.id(), "sessionChannel")
                                .channelMapping(PacketId.LAP_DATA.id(), "lapDataChannel")
                                .channelMapping(PacketId.EVENT.id(), "eventChannel")
                                .channelMapping(PacketId.CAR_TELEMETRY.id(), "carTelemetryChannel")
                                .channelMapping(PacketId.SESSION_HISTORY.id(), "sessionHistoryChannel")
                                .defaultOutputChannel("nullChannel")
                )
                .get();
    }

    private UdpInboundChannelAdapterSpec udpInboundAdapterSpec() {
        UdpInboundChannelAdapterSpec spec = Udp.inboundAdapter(port)
                // The adapter runs its receive loop on one pool thread and hands every packet to the
                // others. The default pool of 5 handles packets concurrently and out of order; with 2
                // there is exactly one handler thread, so packets are processed one by one in arrival
                // order - which lap and flashback detection rely on.
                .poolSize(2)
                .soReceiveBufferSize(SOCKET_RECEIVE_BUFFER_BYTES)
                .receiveBufferSize(MAX_PACKET_BYTES);
        return (bindAddress != null && !bindAddress.isBlank()) ? spec.localAddress(bindAddress) : spec;
    }
}
