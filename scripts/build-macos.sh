#!/usr/bin/env bash
# Run from any directory; all output goes to this checkout's dist folder.
set -euo pipefail
if [[ "$(uname -s)" != Darwin ]]; then
  echo 'Run this script on your Mac.' >&2
  exit 1
fi
cd "$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
for tool in cargo rustup node npm python3 xcrun; do
  if ! command -v "$tool" >/dev/null 2>&1; then
    echo "Missing prerequisite: $tool. See README.md for Mac build setup." >&2
    exit 1
  fi
done
xcrun --find clang >/dev/null
case "$(uname -m)" in
  arm64) target=aarch64-apple-darwin ;;
  x86_64) target=x86_64-apple-darwin ;;
  *) echo 'Unsupported Mac architecture.' >&2; exit 1 ;;
esac
rustup target add "$target"
cargo test --locked --manifest-path desktop/core/Cargo.toml
npm ci --prefix desktop
npm --prefix desktop run build -- --target "$target" --bundles dmg
python3 scripts/collect-artifacts.py "$target"
python3 scripts/inspect-artifacts.py dist
printf '\nInstaller ready in %s/dist/\n' "$PWD"
printf 'Without signing credentials this is an unsigned, unnotarized development build.\n'
