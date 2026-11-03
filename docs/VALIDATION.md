# Validation record — 2 November 2026

This is a development candidate, not a claim that every acceptance criterion has been physically verified.

| Deliverable | Actual result |
|---|---|
| `ThumbPad-Android-debug.apk` | Built; Android binary manifest, DEX/resources and ZIP integrity inspected; v2 development signature verified; installed and launched on emulator |
| `ThumbPad-Android-release-unsigned.apk` | Built; binary manifest, DEX/resources and ZIP integrity inspected; deliberately unsigned; must sign before installation |
| `ThumbPad-Windows-Setup.exe` | Built for x64; PE/NSIS structure inspected; silent per-user install and clean uninstall passed twice, including final build; no Authenticode signature |
| Windows companion | Real native window and tray created; pairing QR, private LAN address, controls and accessibility tree inspected; encrypted vault startup verified after correcting Credential Manager size handling |
| macOS DMGs | Not built here. Native Apple Silicon and Intel CI jobs are supplied. No successful Mac CI run or workflow download URL exists |
| Source and CI | Kotlin/Compose, Rust shared core and platform injection, Tauri 2 UI/tray, Gradle wrapper, npm/Cargo lockfiles, release/inspection/smoke scripts and native-runner workflow |

Host: Windows x64, MSVC 2022, Node 26.7.0, project-local Rust 1.99.1, Android Studio JDK 21.0.10, Gradle 9.4.1, AGP 9.1.0, Compose compiler plugin 2.3.20. Android minSdk 26 / target and compileSdk 36 / build-tools 36.0.0. API 37 was not offered by the configured SDK repository. Dependency versions are pinned to a tested compatible set rather than automatically upgraded during the build. Android lint reported zero errors and 18 warnings on the final application build (dependency update notices, portrait ergonomics, explicit pin-trust-manager review and style suggestions).

Actual checks passed:

- 8 Rust unit tests; strict Clippy across all core targets; rustfmt checks on both Rust crates.
- 8 Kotlin/JUnit unit tests; Android debug lint; debug and unsigned release builds; instrumentation test APK compilation.
- 12 encrypted network scenarios: bad invitation, approved pairing, one-controller bound, ordered drag/motion/release, 100 heartbeat round trips, trusted reconnect, replay rejection, disable with held drag and partial read, malformed frame disconnect, heartbeat timeout, active revocation, revoked reconnect rejection. These use a real TLS stream and a dry input dispatcher.
- 2 Android runtime tests on an Android 10 / API 29 x86_64 emulator, WHPX acceleration, 1080×2340 / 420dpi. Both passed in 4.148 seconds: demo click/drag release and actual Android client → pinned TLS → Rust receiver pairing/clicks/drag/reconnect. Test installation and launch succeeded. Screenshot capture succeeded from the running app. The API 35 emulator could not boot with available disk space; the smaller API 29 image was used after reclaiming generated build caches. CI tests API 35 independently.
- Structural inspection of both APKs and the real NSIS installer. Debug APK signing verified using Android apksigner. Windows installer/uninstaller smoke passed for the final installer.

Latest standalone loopback timing: 100 heartbeat samples on this Windows x64 host, median **0.1032 ms**, p95 **0.1329 ms**. This is local TLS software round trip with dry dispatch, not a Samsung Wi-Fi, native pointer or perceived end-to-end latency claim. The Android screenshot's individual 24ms RTT observation is an emulator sample, not a distribution or performance guarantee. See `dist/network-smoke.json` and `docs/PERFORMANCE.md`.

Artifacts and their exact SHA-256 digests are recorded in `dist/artifact-inspection.json`. Test outputs are preserved in `dist/reports/`. Generated Rust compiler/build caches were removed to make room for the emulator; the source, binaries, Gradle outputs and test reports remain. Rebuilding desktop requires installing Rust as described in the README.

Screenshots: `docs/screenshots/android-connected.png` and `android-demo.png` are actual native Compose emulator captures. `desktop-native.jpg` is an actual Windows companion capture; an OS network-permission prompt was present at the screen edge and was not dismissed automatically. That screenshot is evidence of the implemented window, not a polished marketing composite.

Not executed / not proven: macOS compilation, DMG packaging, Gatekeeper / Accessibility onboarding, native pointer injection on either real desktop, Samsung physical device installation, QR camera decoding, real mDNS discovery, multi-monitor/DPI behavior, real Wi-Fi transitions and sleep/wake, treadmill ergonomics, 90/120Hz hardware sampling, battery/CPU profiling, TalkBack audit, signing/notarization and external security review. The attempted macOS cross-check stopped in a dependency build because an Apple C toolchain/SDK is unavailable on Windows. No claim of successful macOS source compilation is made.

Known scope gaps: IPv6-only LANs, automatic receiver interface rebinding / host-address migration, arbitrary control layout or shortcut editing, full text entry, push-to-talk and optional landscape mode. The currently exposed controls, settings and documented basic workflow are implemented. See the manual acceptance list in `docs/TESTING.md` before relying on this as a production daily driver.

No Git repository or remote was present in the empty supplied directory, and no repository destination was specified. Therefore no source was pushed and no GitHub Actions run was triggered. Add the source to the desired GitHub repository and push `main` (or run the workflow manually) to build native macOS artifacts and publish workflow downloads. Tagged releases wait for all required jobs, including Android runtime tests.
