#!/usr/bin/env bash
set -euo pipefail
for image in dist/*.dmg; do
  hdiutil verify "$image"
  mount="$RUNNER_TEMP/thumbpad-mount"
  mkdir -p "$mount"
  hdiutil attach "$image" -readonly -nobrowse -mountpoint "$mount"
  trap 'hdiutil detach "$mount" || true' EXIT
  test -d "$mount/ThumbPad.app"
  test -L "$mount/Applications"
  file "$mount/ThumbPad.app/Contents/MacOS/thumbpad"
  plutil -lint "$mount/ThumbPad.app/Contents/Info.plist"
  codesign --verify --deep "$mount/ThumbPad.app" || echo 'Unsigned development build: expected without signing secrets'
  hdiutil detach "$mount"
  trap - EXIT
done
