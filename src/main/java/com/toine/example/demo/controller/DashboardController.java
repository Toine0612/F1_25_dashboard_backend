package com.toine.example.demo.controller;

import com.toine.example.demo.models.Channel;
import com.toine.example.demo.models.Lap;
import com.toine.example.demo.models.LapChannel;
import com.toine.example.demo.models.SessionUids;
import com.toine.example.demo.models.dto.api.LapTelemetryView;
import com.toine.example.demo.models.dto.api.LapView;
import com.toine.example.demo.models.dto.api.LiveStatusView;
import com.toine.example.demo.models.dto.api.SessionView;
import com.toine.example.demo.reference.F1Appendix;
import com.toine.example.demo.repository.LapChannelRepository;
import com.toine.example.demo.repository.LapRepository;
import com.toine.example.demo.repository.SessionLapStats;
import com.toine.example.demo.repository.SessionRepository;
import com.toine.example.demo.service.TelemetryService;
import com.toine.example.demo.service.recording.RecordedSession;
import com.toine.example.demo.service.recording.RecorderStatus;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api")
public class DashboardController {

    private static final Duration RECEIVING_WINDOW = Duration.ofSeconds(3);

    private final SessionRepository sessionRepository;
    private final LapRepository lapRepository;
    private final LapChannelRepository lapChannelRepository;
    private final TelemetryService telemetryService;

    public DashboardController(SessionRepository sessionRepository,
                               LapRepository lapRepository,
                               LapChannelRepository lapChannelRepository,
                               TelemetryService telemetryService) {
        this.sessionRepository = sessionRepository;
        this.lapRepository = lapRepository;
        this.lapChannelRepository = lapChannelRepository;
        this.telemetryService = telemetryService;
    }

    /** Sessions with at least one recorded lap, newest first. */
    @GetMapping("/sessions")
    public List<SessionView> getSessions() {
        Map<Long, SessionLapStats> stats = lapRepository.findSessionLapStats().stream()
                .collect(Collectors.toMap(SessionLapStats::sessionUid, Function.identity()));
        return sessionRepository.findAllByOrderByStartedAtDesc().stream()
                .filter(session -> stats.containsKey(session.getSessionUid()))
                .map(session -> {
                    SessionLapStats s = stats.get(session.getSessionUid());
                    return SessionView.of(session, s.lapCount(), s.bestLapTimeMs());
                })
                .toList();
    }

    @GetMapping("/sessions/{sessionUid}")
    public SessionView getSession(@PathVariable String sessionUid) {
        long uid = parseSessionUid(sessionUid);
        var session = sessionRepository.findById(uid).orElseThrow(() -> notFound("session " + sessionUid));
        List<Lap> laps = lapRepository.findBySession_SessionUidOrderByLapNumber(uid);
        Integer best = laps.stream().filter(Lap::isValid).map(Lap::getLapTimeMs).min(Integer::compare).orElse(null);
        return SessionView.of(session, laps.size(), best);
    }

    @GetMapping("/sessions/{sessionUid}/laps")
    public List<LapView> getLaps(@PathVariable String sessionUid) {
        long uid = parseSessionUid(sessionUid);
        return lapRepository.findBySession_SessionUidOrderByLapNumber(uid).stream()
                .map(lap -> LapView.of(lap, uid))
                .toList();
    }

    @GetMapping("/laps/{lapId}")
    public LapView getLap(@PathVariable long lapId) {
        Lap lap = lapRepository.findWithSessionById(lapId).orElseThrow(() -> notFound("lap " + lapId));
        return LapView.of(lap, lap.getSession().getSessionUid());
    }

    /**
     * @param channels optional comma-separated channel keys (e.g. {@code distance,speed}); all when omitted
     */
    @GetMapping("/laps/{lapId}/telemetry")
    public LapTelemetryView getLapTelemetry(@PathVariable long lapId,
                                            @RequestParam(required = false) List<String> channels) {
        Lap lap = lapRepository.findById(lapId).orElseThrow(() -> notFound("lap " + lapId));
        List<LapChannel> stored = channels == null
                ? lapChannelRepository.findByLapId(lapId)
                : lapChannelRepository.findByLapIdAndChannelIn(lapId, channels);

        Map<String, Object> values = new LinkedHashMap<>();
        stored.stream()
                .sorted((a, b) -> a.getChannel().compareTo(b.getChannel()))
                .forEach(channel -> values.put(channel.getChannel().key(), jsonValues(channel)));
        return new LapTelemetryView(lapId, lap.getSampleCount(), values);
    }

    @GetMapping("/live")
    public LiveStatusView getLiveStatus() {
        Instant lastPacketAt = telemetryService.lastPacketAt();
        boolean receiving = lastPacketAt != null && lastPacketAt.isAfter(Instant.now().minus(RECEIVING_WINDOW));
        RecorderStatus status = telemetryService.recorderStatus();
        RecordedSession session = status.session();
        return new LiveStatusView(receiving, lastPacketAt,
                telemetryService.packetsReceived(), telemetryService.packetsRejected(),
                session == null ? null : SessionUids.format(session.sessionUid()),
                session == null || session.info() == null ? null : F1Appendix.sessionType(session.info().sessionType()),
                session == null || session.info() == null ? null : F1Appendix.track(session.info().trackId()),
                status.lapNumber(), status.lapDistance(), status.lapsSaved());
    }

    private static Object jsonValues(LapChannel channel) {
        float[] samples = channel.getSamples();
        if (!channel.getChannel().integral()) return samples;
        int[] whole = new int[samples.length];
        Arrays.setAll(whole, i -> Math.round(samples[i]));
        return whole;
    }

    private static long parseSessionUid(String sessionUid) {
        try {
            return SessionUids.parse(sessionUid);
        } catch (NumberFormatException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid session UID: " + sessionUid);
        }
    }

    private static ResponseStatusException notFound(String what) {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "No " + what);
    }
}
