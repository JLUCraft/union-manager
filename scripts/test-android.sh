#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
mode=${1:-unit}
case "$mode" in
  unit) tasks=(:app:testDebugUnitTest) ;;
  build) tasks=(:app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest) ;;
  connected)
    : "${ANDROID_SERIAL:?Set ANDROID_SERIAL to the dedicated test device or emulator}"
    tasks=(:app:testDebugUnitTest :app:connectedDebugAndroidTest)
    ;;
  *) printf 'Usage: %s [unit|build|connected]\n' "$0" >&2; exit 2 ;;
esac
# Keep the launcher/game usable while testing. Native libraries must be built first.
exec ./gradlew --no-daemon --max-workers=1 \
  '-Dorg.gradle.jvmargs=-Xmx768m -XX:MaxMetaspaceSize=384m -Dfile.encoding=UTF-8' \
  -Pkotlin.compiler.execution.strategy=in-process "${tasks[@]}"
