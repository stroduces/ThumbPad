# Measuring responsiveness

Never promise zero latency or infer Android-to-host one-way latency by subtracting unsynchronized monotonic clocks.

The phone stamps each queued event with `elapsedRealtimeNanos / 1000`. This measures Android's enqueue time after native touch interpretation, not the hardware digitizer timestamp. The packet keeps that value. The host records local elapsed time from packet decode to completion of the native dispatch call; the desktop reports a rolling sample median/p95 for up to 2048 events. These host samples exclude TLS/network transit and OS presentation. They include heartbeats and protocol bookkeeping, so they are diagnostics, not pure cursor-only measurements.

Heartbeat frames echo the original Android timestamp. The phone computes round-trip time on its own monotonic clock. A returned heartbeat shows both directions plus host scheduling; it does not establish one-way input delivery or visual cursor latency. The display shows **round trip**, not a one-way claim.

Run the automated loopback harness:
```sh
cargo build --locked --manifest-path desktop/core/Cargo.toml --example smoke_receiver
node scripts/network-smoke.mjs
```
It starts an isolated loopback-only TLS receiver with volatile identity, automatic test-only approval and dry input dispatch. Its client validates the server with an explicitly trusted certificate and expected fingerprint. It sends real frames, reports 100 heartbeat samples, and exercises pairing/reconnect/replay/revoke. It writes actual median/p95 to `dist/network-smoke.json`, with the OS and architecture. The test cannot establish Samsung Wi-Fi latency or native pointer presentation delay.

For device measurements record: phone model, Android version, refresh-rate mode (60/90/120), Wi-Fi band/access point, desktop OS/architecture, monitor refresh rate/DPI, power mode, distance and load. Collect at least 1000 heartbeat RTT observations and separate host-dispatch samples during cursor motion. Compare idle and busy LAN conditions, fast flicks, sustained scrolling and repeated dragging. Record median/p95 and drop/disconnect counts for each run; never merge cross-platform conditions into one flattering number.

To measure perceived pointer response, film the thumb contact and display with a high-speed camera, or use synchronized clocks with a measured uncertainty bound and instrument compositor presentation. The current harness has neither and makes no such claim. Native Android event-time-to-enqueue tracing and packet-capture timing can be added using Perfetto; do not compare their absolute clock values with host monotonic values without synchronization.

Motion queues are bounded and expired before transmission; reliable actions preserve order. TCP can still accumulate data in kernel/network buffers after the sender writes it, and the host cannot reliably compute event age from Android's independent clock. Congestion can therefore affect delivery even with a tiny application queue. The receiver heartbeat watchdog closes unusable sessions. A future measured optimization could use an authenticated datagram motion channel with ordered reliable actions; that is not implemented or needed to claim the current development build works.
