# Signing and reproducible release

The debug APK has a development signature. The release APK is deliberately unsigned when no signing secrets exist. Keep a release key backed up securely; losing it prevents upgrades under the same application identity. Never commit signing material.

## Android manual signing
Use JDK keytool and your installed Android build-tools directory. Run from the project root. Choose your own key password when prompted:
```sh
keytool -genkeypair -v -keystore thumbpad-release.jks -alias thumbpad -keyalg RSA -keysize 3072 -validity 10000
zipalign -p -f 4 dist/ThumbPad-Android-release-unsigned.apk dist/ThumbPad-Android-release-aligned.apk
apksigner sign --ks thumbpad-release.jks --ks-key-alias thumbpad --out dist/ThumbPad-Android-release.apk dist/ThumbPad-Android-release-aligned.apk
apksigner verify --verbose --print-certs dist/ThumbPad-Android-release.apk
```
On Windows use `zipalign.exe` and `apksigner.bat` in `%ANDROID_HOME%\build-tools\36.0.0`. The signed debug and release keys differ: uninstall the debug app before installing a release signed with your own key (this clears saved pairs/settings). In CI supply `ANDROID_KEYSTORE_BASE64`, `ANDROID_STORE_PASSWORD`, `ANDROID_KEY_PASSWORD`, `ANDROID_KEY_ALIAS`. The workflow signs and verifies automatically when the base64 secret exists; otherwise it publishes the unsigned release alongside debug.

## Windows
The NSIS setup and native executable are unsigned development builds. For distribution, obtain an Authenticode certificate or trusted signing service and configure Tauri Windows signing. Sign the native executable before bundling and the final installer after bundling. Verify with `signtool verify /pa /v ThumbPad-Windows-Setup.exe`. No certificate or publisher identity is invented.

## macOS
Without a signing identity, CI emits an unsigned/unnotarized development DMG. Architecture is explicit in the name. For distribution, install a Developer ID Application certificate in the runner's temporary keychain and set `APPLE_SIGNING_IDENTITY`, `APPLE_CERTIFICATE`, `APPLE_CERTIFICATE_PASSWORD`, plus Apple's notarization credentials (`APPLE_ID`, `APPLE_PASSWORD`, `APPLE_TEAM_ID`) supported by Tauri. Use repository/environment secrets, never plaintext workflow fields. Configure these environment variables for the build step before a signed release. This repository does not currently import a certificate from GitHub secrets automatically.

After building on a Mac, verify using `codesign --verify --deep --strict --verbose=2 ThumbPad.app`, `spctl --assess --type execute -vv ThumbPad.app`, `xcrun stapler validate ThumbPad.app`, and `hdiutil verify <image>`. Do not label a build notarized unless these checks pass. Never disable Gatekeeper, SIP or accessibility protection.

`scripts/release.ps1` rebuilds using checked-in dependency lockfiles on native hosts and performs structural inspection. Compiler, SDK and OS updates can change bytes; this is a reproducible procedure, not a claim of bit-for-bit reproducible binaries. Tagged CI releases run required tests and attach actual artifacts only.
