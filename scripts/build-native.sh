#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
export PATH="${CARGO_HOME:-$HOME/.cargo}/bin:$PATH"
: "${ANDROID_NDK_HOME:?Set ANDROID_NDK_HOME to a host-compatible Android NDK}"
command -v cargo-ndk >/dev/null || { echo 'Install cargo-ndk: cargo install cargo-ndk --locked' >&2; exit 1; }
rustup target add aarch64-linux-android x86_64-linux-android
cd native
cargo ndk --platform 33 -t arm64-v8a -t x86_64 -o ../app/src/main/jniLibs build --release --locked
