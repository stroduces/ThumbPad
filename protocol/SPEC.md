# ThumbPad wire protocol 1

One persistent TLS 1.2/1.3 TCP stream on private IPv4 LAN port 47321; TCP_NODELAY on both ends. Raw framed TCP avoids WebSocket/HTTP parsing on the input path. TLS record authentication protects framing. No unencrypted endpoint is provided.

The QR invitation is `thumbpad://pair?host=<IPv4>&port=47321&fp=<64 hex SHA256 of DER certificate>&secret=<64 random hex>`. Treat the QR as a temporary secret. It expires after 120 seconds, rotates after approval, and never appears in application logs. Manual connection uses the same complete invitation so the user transfers a trusted fingerprint out of band. Discovery is only a hint, never a trust source.

Client hello: u16 little-endian byte length (1–2048), then UTF-8 JSON `{version:1,id:<32 hex persistent ID>,name:<1–80 UTF8 bytes>,secret:<pair invitation>}` OR `token:<trusted device token>`. Handshake has a five-second deadline, pairing confirmation a 60-second deadline. Desktop shows phone name and the first six uppercase invitation hex digits; user explicitly approves. A newly generated 256-bit token is returned only after approval, over pinned TLS. Host stores a SHA-256 token hash. ID identifies a credential, not a hardware attestation. Trusted hello uses the token instead of an invitation.

Server reply: u16 little-endian length + JSON `{ok:true,version:1,token:<new token or null>}`. Receivers supporting type 8 add `key_remapping:true`. Send type 8 only when that flag is present. Authentication failure closes the stream. One controlling client at a time, eight bounded pending TLS sessions, four bounded pairing requests. No pre-auth input processing. No client-supplied executable commands or text injection.

Every subsequent frame is exactly 32 bytes, little endian:

| Offset | Bytes | Meaning |
|---|---:|---|
|0|2|ASCII TP|
|2|1|Version 1|
|3|1|Type|
|4|8|Strictly increasing u64 sequence, reset per connection|
|12|8|Android monotonic microseconds, opaque to host|
|20|4|IEEE754 f32 A|
|24|4|IEEE754 f32 B|
|28|4|Reserved zero|

|Type|A|B|
|---|---|---|
|1 cursor|relative X pixels|relative Y pixels|
|2 button|0 left / 1 right / 2 middle|1 down / 0 up|
|3 scroll|horizontal logical pixels|vertical logical pixels|
|4 shortcut|0 back, 1 forward, 2 play/pause, 3 volume up, 4 volume down, 5 enter, 6 escape, 7 tab, 8 backspace|0|
|5 heartbeat|0|0; server echoes complete frame|
|6 release all|0|0|
|7 capabilities/status request|0|0; reserved, currently accepted without response|
|8 remapped key|USB HID Keyboard/Keypad usage|0 tap. For left/right modifiers, 1 or 2 presses a button slot and −1 or −2 releases it.|

Type 8 accepts usages `04–31`, `33–45`, `49–63`, `68–6F` and `E0–E7` (hex). Only `E0–E7` can be held. Each slot can hold one modifier. A release must match the slot and usage that were pressed. Two slots holding the same modifier keep it down until both release. Release all and session teardown clear both slots along with held mouse buttons.

Nonfinite values, motion/scroll outside ±2048, unknown types, reserved bytes, invalid button payloads and replayed/out-of-order sequences close the connection. Gaps are allowed. TCP orders reliable actions. Repeated button states are idempotent. Heartbeat every second; host releases all held buttons on EOF, malformed packet, disable, revoke, session replacement, or three seconds without valid input. Desktop stop/revoke checks run at most every 50ms. A partially received packet does not reset the watchdog. Normal process exit must allow that cleanup; hard kill cannot guarantee OS cleanup.

Android has a 64-entry bounded action queue and one replaceable motion slot. Motion is flushed before actions, dropped after 50ms in the sender's clock, and superseded by latest pending motion. Reliable queue overflow closes transport, releasing host buttons. No queues survive reconnect. Scroll/button events are never coalesced. Native dispatch is sequential off the desktop UI thread.

Golden vector: cursor sequence 1, timestamp 1000, X 1.5, Y -2:
`545001010100000000000000e8030000000000000000c03f000000c000000000`
