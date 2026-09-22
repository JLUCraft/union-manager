#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
export CARGO_TARGET_DIR="${CARGO_TARGET_DIR:-$PWD/../target}"
cargo build --manifest-path native/Cargo.toml --locked
jlu_test_classes=$(mktemp -d)
trap 'rm -rf "$jlu_test_classes"' EXIT
javac -d "$jlu_test_classes" native/tests/com/jlucraft/console/nativecore/UnionNative.java
java --enable-native-access=ALL-UNNAMED -Djava.library.path="$CARGO_TARGET_DIR/debug" -cp "$jlu_test_classes" com.jlucraft.console.nativecore.UnionNative
