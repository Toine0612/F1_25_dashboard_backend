package com.toine.example.demo.config;

import com.toine.example.demo.models.dto.packets.F1Header;
import com.toine.example.demo.service.TelemetryParser;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.integration.dsl.IntegrationFlow;
import org.springframework.integration.ip.dsl.Udp;
import org.springframework.integration.ip.dsl.UdpInboundChannelAdapterSpec;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;

import java.util.concurrent.atomic.AtomicBoolean;

@Configuration
public class UDPConfig {

    private final int port;
    private final String bindAddress;
    private final TelemetryParser parser;
    // Fires once per app run so we can tell "no UDP arriving" apart from
    // "arriving but dropped downstream" without spamming the console per-packet.
    private final AtomicBoolean firstPacketSeen = new AtomicBoolean(false);

    public UDPConfig(TelemetryParser parser,
                      @Value("${udp.telemetry.port:20777}") int port,
                      @Value("${udp.telemetry.bind-address:}") String bindAddress) {
        this.parser = parser;
        this.port = port;
        this.bindAddress = bindAddress;
    }

    @Bean
    public IntegrationFlow udpInboundFlow() {
        return IntegrationFlow.from(udpInboundAdapterSpec())
                .transform(Message.class, message -> {
                    byte[] payload = (byte[]) message.getPayload();
                    F1Header header = parser.parseHeader(payload);
                    if (firstPacketSeen.compareAndSet(false, true)) {
                        System.out.println("UDP telemetry alive: first packet received (packetId="
                                + header.m_packetId() + ", " + payload.length + " bytes)");
                    }
                    return MessageBuilder.withPayload(payload)
                            .setHeader("packetId", header.m_packetId())
                            .build();
                })
                .route(Message.class,
                        message -> message.getHeaders().get("packetId", Short.class),
                        mapping -> mapping
                                .channelMapping((short) 1, "sessionChanel") // Session type / track
                                .channelMapping((short) 2, "lapDataChanel") // Update lap data
                                .channelMapping((short) 3, "eventChanel") // Rewind update
                                .channelMapping((short) 6, "telemetryChanel") // Add telemetry
                                .channelMapping((short) 11, "sessionHistoryChanel") // Session history / lap confirmation
                                .defaultOutputChannel("nullChannel")
                )
                .get();
    }

    private UdpInboundChannelAdapterSpec udpInboundAdapterSpec() {
        UdpInboundChannelAdapterSpec spec = Udp.inboundAdapter(port);
        return (bindAddress != null && !bindAddress.isBlank()) ? spec.localAddress(bindAddress) : spec;
    }
}
