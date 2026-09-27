# F1 25 telemetry backend

Receives the UDP telemetry F1 25 sends while you drive, turns it into laps and stores them for the
dashboard in `../frontend`.

```
F1 25 ──UDP──▶ UDPConfig ──▶ TelemetryService ──▶ FrameAssembler ──▶ LapRecorder ──▶ LapPersistenceService ──▶ PostgreSQL
               (validate,     (player car,        (Motion + Lap Data   (lap state      (one writer thread)       ▲
                route by       decode)             + Car Telemetry      machine)                                 │
                packet id)                         of one frame)                                   DashboardController (/api)
```

## Running

```bash
docker compose up -d database          # PostgreSQL 16 on :5432 (compose.yaml)
./mvnw spring-boot:run                 # API on :8080, UDP on :20777
```

Or without the compose database: `./mvnw spring-boot:test-run -Dspring-boot.run.main-class=com.toine.example.demo.TestDemoApplication`
starts a throwaway PostgreSQL container instead.

In the game: *Settings → Telemetry Settings*: UDP on, **UDP format 2025**, port 20777, the IP of this
machine; a 60 Hz send rate gives the best traces. The dashboard header shows whether packets arrive
(and warns if they're rejected, e.g. because of a different UDP format).

No game at hand? `UdpTelemetrySimulator` (in `src/test/.../tools`) plays a race weekend and a Time Trial
into the running backend:

```bash
./mvnw test-compile && java -cp target/classes:target/test-classes \
  com.toine.example.demo.tools.UdpTelemetrySimulator [host] [port] [speed-up] [hz]
```

## How laps are recorded

`LapRecorder` holds the racing semantics; `LapRecorderTest` documents each rule.

- **Start/finish line to start/finish line.** A lap is stored only if its first sample is within 100 m
  after the line and its last within 100 m before it. Out-laps from the pit exit, laps interrupted by
  the garage, a restart or the session end are discarded (logged with the reason). Garage samples and
  samples before the line is first crossed never enter a lap.
- **Ordered by frame.** Packets are handled one at a time, in arrival order (see the `poolSize(2)`
  comment in `UDPConfig`). The frame identifier goes back after a flashback, so a lower frame rewinds the
  recording, back across the line into the previous lap if needed (that lap is removed and recorded again).
- **Official timing.** Lap and sector times and validity come from the game's Session History, with Lap
  Data as the fallback. If the game changes them later (e.g. a penalty invalidates a lap), the stored lap is
  corrected.
- **Sessions come from the header.** Every packet carries the session UID, so a missed "session started"
  event or starting the app mid-session doesn't lose data.
- **Distance is the primary axis.** Samples that don't move the car forward (standing still, spinning) are
  skipped, so lap distance strictly increases in every stored lap and laps can be compared at the same
  track position; the time lost stays visible in the time channel.

## Storage

PostgreSQL, schema `f1`, managed by Flyway (`src/main/resources/db/migration`):

| table         | one row per | contents |
|---------------|-------------|----------|
| `session`     | session     | track, type, track length, sector boundaries, weekend id |
| `lap`         | lap         | lap/sector times, validity, in/out-lap flags |
| `lap_channel` | lap × channel | the whole lap's samples of one channel as a `real[]` |

Telemetry is written once per lap and always read as a whole lap, so it is stored column-wise: one
array per channel instead of one row per sample. Saving a 60 Hz lap is ~12 rows instead of ~5,000, and
`GET /api/laps/{id}/telemetry` returns it in the same columnar shape (~70 KB gzipped).

The old `public.session`, `public.lap` and `public.telemetry` tables from the previous
`ddl-auto=update` setup are no longer used and can be dropped once you no longer need their data.

## Adding a telemetry channel

1. Add the field to the packet record and `TelemetryParser` (most packets are already fully decoded).
2. Carry it in `FrameSample`.
3. Add a constant to `Channel` and its mapping in `LapTrace.value`.

No migration is needed; laps recorded before simply don't have that channel. For a new packet type,
add it to `PacketId`, route it in `UDPConfig` and add a `@ServiceActivator` in `TelemetryService`.

## API

| endpoint | |
|---|---|
| `GET /api/sessions` | sessions with at least one lap, newest first (lap count, best lap) |
| `GET /api/sessions/{uid}` | one session, incl. track length and sector boundaries |
| `GET /api/sessions/{uid}/laps` | its laps |
| `GET /api/laps/{id}` | one lap |
| `GET /api/laps/{id}/telemetry[?channels=distance,speed]` | the lap's channels, index-aligned |
| `GET /api/live` | is telemetry arriving, what is being recorded |

Session UIDs are unsigned 64-bit numbers and are sent as strings.

## Tests

`./mvnw test` (Docker needed for the Testcontainers PostgreSQL):
`TelemetryParserTest` checks byte offsets against the spec. `LapRecorderTest` covers the lap rules.
`SimulatedSessionRecordingTest` replays scripted sessions packet by packet. `TelemetryPipelineIntegrationTest`
sends real UDP packets and reads the result back through the API.
