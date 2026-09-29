#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
mode=${1:-unit}
runner_args=()
case "$mode" in
  unit) tasks=(:app:testDebugUnitTest) ;;
  build) tasks=(:app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest) ;;
  connected)
    : "${ANDROID_SERIAL:?Set ANDROID_SERIAL to the dedicated test device or emulator}"
    tasks=(:app:testDebugUnitTest :app:connectedDebugAndroidTest)
    ;;
  screenshots)
    : "${ANDROID_SERIAL:?Set ANDROID_SERIAL to the dedicated unenrolled test emulator}"
    : "${TUTORIAL_OUTPUT_DIR:?Set TUTORIAL_OUTPUT_DIR to a new evidence directory}"
    tasks=(:app:testDebugUnitTest :app:connectedDebugAndroidTest)
    capture_run="$(date -u +%Y%m%dT%H%M%S)-$$"
    runner_args=(-Pandroid.testInstrumentationRunnerArguments.tutorialCapture=true "-Pandroid.testInstrumentationRunnerArguments.captureRunId=$capture_run")
    ;;
  *) printf 'Usage: %s [unit|build|connected|screenshots]\n' "$0" >&2; exit 2 ;;
esac
# Keep the launcher/game usable while testing. Native libraries must be built first.
./gradlew --no-daemon --max-workers=1 \
  '-Dorg.gradle.jvmargs=-Xmx768m -XX:MaxMetaspaceSize=384m -Dfile.encoding=UTF-8' \
  -Pkotlin.compiler.execution.strategy=in-process "${runner_args[@]}" "${tasks[@]}"
if [[ "$mode" == screenshots ]]; then
  mkdir -p "$TUTORIAL_OUTPUT_DIR"
  mapfile -t files < <(adb -s "$ANDROID_SERIAL" shell "ls /data/local/tmp/jlucraft-e2e-$capture_run-*.png 2>/dev/null" | tr -d '\r')
  for remote in "${files[@]}"; do
    adb -s "$ANDROID_SERIAL" pull "$remote" "$TUTORIAL_OUTPUT_DIR/$(basename "$remote" | sed "s/^jlucraft-e2e-$capture_run-//")"
  done
fi
