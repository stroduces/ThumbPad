# ThumbPad

A one-handed Android trackpad with a native Windows / macOS companion. Kotlin + Jetpack Compose on your phone; Tauri 2 + a Rust TLS receiver on your desk. No account, browser tab, relay, clipboard sharing, telemetry, or internet dependency after installation.

**Development builds: desktop 0.1.0, Android 0.1.4.** Android now uses a compact connection control and a larger trackpad, with the introductory headings and visible trackpad text removed. Read [the validation record](docs/VALIDATION.md) for what was actually built and tested. Source availability does not establish hardware validation. The current Android target is API 36, minSdk 26; API 37 was not offered by this machine's SDK repository at build time. No signing credentials were supplied.

## Install

### Samsung Android phone
1. Transfer `dist/ThumbPad-Android-debug.apk` to your phone (USB, your own file transfer, or download a CI artifact and extract it).
2. Open it in My Files. If Android asks, allow **Install unknown apps** for the particular file app you are using, then install. The debug build is signed with a development certificate. Do not install the unsigned release APK directly.
3. Connect your phone and computer to the same trusted Wi-Fi. Start the desktop companion.
4. In ThumbPad tap **Find your computer → Scan desktop QR**. Grant camera access when asked; scanning works offline. Alternatively paste the complete pairing invitation from the desktop's **Connect manually** section.
5. Compare the six-character code on both screens; approve your phone on the desktop. Swipe to move, tap to click, use the large left/right buttons, slide the marked side strip to scroll. **Drag lock** holds left down across swipes; tap **Left click** to release. **Precise** lowers sensitivity.
6. Pair your other computer the same way. Tap the computer card to switch. Switching, backgrounding or disconnecting releases dragging. Returning to the app reconnects the last selected trusted computer during the same process lifetime; after a full restart select a saved computer.

**Optional scroll wheel (Android 0.1.1):** open Settings (⚙), enable **Show scroll wheel**, then **Save & close**. Turn the ring with your thumb to scroll; lift and start another turn whenever needed. The wheel follows the left-handed layout and uses the existing scroll speed and natural-direction settings. Tapping its centre never clicks or moves the pointer. Screen readers also expose scroll-up/down actions. The desktop companion does not need an update.

**Optional Back / Play / Forward buttons (Android 0.1.4):** these are hidden by default to leave more space for the trackpad. Enable **Settings → Show Back / Play / Forward → Save & close** to show the row. Your choice is saved between launches. This setting is independent of the extra volume/keyboard controls.

### Windows 10/11, x64
1. Run `dist/ThumbPad-Windows-Setup.exe`. This is a per-user NSIS installer with an uninstaller. It is unsigned; Windows may show publisher/reputation prompts. Verify the checksum against your build before deciding whether to install.
2. Launch ThumbPad from Start. The window can close to the tray; open the tray menu to reopen, immediately disable control, or quit. **Start at login** is opt-in.
3. If Windows asks about network access, permit your private/home network only. No firewall rule or administrator elevation is installed silently. Guest Wi-Fi isolation, VPN routing and firewalls can prevent discovery/control.
4. Scan the QR from the phone and approve. For removal use Windows Settings → Apps → ThumbPad → Uninstall. Trusted identity data remains in your local profile to avoid surprising re-pairing after upgrades; revoke devices in the app before uninstalling when desired.

### macOS, Apple Silicon / Intel
1. Build on a Mac or download `ThumbPad-macOS-AppleSilicon.dmg` / `ThumbPad-macOS-Intel.dmg` from a successful workflow. No DMG can be produced by this Windows environment.
2. Open the disk image and drag ThumbPad into Applications. Start the installed copy. Without a Developer ID signature/notarization, macOS may block it. Review your build's origin and use macOS's explicit **Open Anyway** flow in Privacy & Security if you choose to trust the development build. Do not disable Gatekeeper globally.
3. In ThumbPad choose **Open Accessibility settings** and enable the installed ThumbPad app in System Settings → Privacy & Security → Accessibility. Relaunch if needed. ThumbPad detects this permission; it never bypasses it. Allow Local Network access if macOS asks.
4. Pair as above. Input into locked desktops, security dialogs, or protected applications may be restricted by the OS.

## Build and test

### Build the installer on your Mac

Install Xcode command-line tools (`xcode-select --install`), Node.js 22 or later, Python 3, and Rust through [rustup](https://rustup.rs/). No Android SDK or JDK is needed for the Mac companion alone. Then:

```sh
git clone https://github.com/stroduces/ThumbPad.git
cd ThumbPad
bash scripts/build-macos.sh
```

If you already cloned the repository, run `git pull --ff-only` inside it, then `bash scripts/build-macos.sh`. The script tests the Rust core, installs the locked desktop dependencies and builds a real native DMG. Find `dist/ThumbPad-macOS-AppleSilicon.dmg` on an Apple Silicon Mac, or `dist/ThumbPad-macOS-Intel.dmg` on an Intel Mac. Open it and drag ThumbPad into Applications. Without signing credentials, follow the development-build Gatekeeper instructions above and enable Accessibility for the installed app. If a build fails, retain the complete terminal output for diagnosis; Mac builds have not yet been verified in the initial Windows environment.

Prerequisites: JDK 21, Android SDK API 36 / build-tools 36.0.0, Node 22+, stable Rust, Python 3, PowerShell 7 for the release script, and the native [Tauri platform prerequisites](https://v2.tauri.app/start/prerequisites/) (Windows MSVC C++ tools and WebView2; macOS Xcode command-line tools). On Linux/macOS run `chmod +x android/gradlew` once. The release build has no Node or Rust runtime dependency.

```powershell
# Native PowerShell 7; run from project root, with tools on PATH
./scripts/release.ps1 -Target android
./scripts/release.ps1 -Target windows
# On a Mac with PowerShell 7:
./scripts/release.ps1 -Target macos
```

Or build directly:

```sh
android/gradlew -p android testDebugUnitTest lintDebug assembleDebug assembleRelease
cargo test --locked --manifest-path desktop/core/Cargo.toml
cargo clippy --locked --manifest-path desktop/core/Cargo.toml --all-targets -- -D warnings
cargo build --locked --manifest-path desktop/core/Cargo.toml --example smoke_receiver
node scripts/network-smoke.mjs
npm ci --prefix desktop
npm --prefix desktop run build -- --bundles nsis
# macOS: npm --prefix desktop run build -- --bundles dmg
```

Use `android/gradlew.bat` on Windows. Set `JAVA_HOME` and `ANDROID_HOME` to your installed tools. Do not commit SDK paths, keys, or `.tools/`. Gradle wrapper and npm/Cargo lockfiles are checked in. `npm run dev` inside `desktop` starts the desktop UI against the real native receiver. The phone's **Try the trackpad in demo mode** emits visible local diagnostics without a computer; it does not open a control endpoint or weaken pairing.

## CI and releases

[`.github/workflows/build.yml`](.github/workflows/build.yml) builds APKs on Ubuntu, Windows EXE on Windows, and arm64/x64 DMGs on macOS native runners. It runs protocol/security tests, Android unit tests/lint, emulator UI tests, installer checks, and publishes separate downloadable workflow artifacts. Push a `v0.1.0` style tag to attach installable assets to a GitHub release **only when all required jobs pass**. `workflow_dispatch` supports manual builds. Pushing `main` to [stroduces/ThumbPad](https://github.com/stroduces/ThumbPad) starts the workflow. Check its Actions tab for actual build status and downloads; a configured workflow alone does not establish a successful Mac build.

See [signing](docs/SIGNING.md), [architecture](docs/ARCHITECTURE.md), [security](docs/SECURITY.md), [performance](docs/PERFORMANCE.md), [troubleshooting](docs/TROUBLESHOOTING.md), [test procedures](docs/TESTING.md), and the [wire protocol](protocol/SPEC.md).
