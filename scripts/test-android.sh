#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
mode=${1:-unit}
runner_args=()
case "$mode" in
  unit) tasks=(:app:testDebugUnitTest) ;;
  build) tasks=(:app:testDebugUnitTest :app:lintDebug :app:assembleDebug :app:assembleDebugAndroidTest) ;;
  connected)
    : "${ANDROID_SERIAL:?Set ANDROID_SERIAL to the dedicated test device or emulator}"
    tasks=(:app:testDebugUnitTest :app:connectedDebugAndroidTest)
    # The live-office scenario has an explicit external fixture and its own mode.
    runner_args=(-Pandroid.testInstrumentationRunnerArguments.notAnnotation=com.jlucraft.manager.nativecore.RequiresFixture)
    ;;
  office)
    : "${ANDROID_SERIAL:?Set ANDROID_SERIAL to the dedicated device with disposable local-office fixtures}"
    tasks=(:app:testDebugUnitTest :app:connectedDebugAndroidTest)
    runner_args=(-Pandroid.testInstrumentationRunnerArguments.class=com.jlucraft.manager.nativecore.LocalOfficeFlowTest -Pandroid.testInstrumentationRunnerArguments.localOfficeDemo=true)
    ;;
  biometric)
    : "${ANDROID_SERIAL:?Set ANDROID_SERIAL to a dedicated enrolled emulator}"
    [[ "$ANDROID_SERIAL" == emulator-* ]] || { echo 'Biometric automation requires a disposable emulator.' >&2; exit 2; }
    tasks=(:app:testDebugUnitTest :app:connectedDebugAndroidTest)
    runner_args=(-Pandroid.testInstrumentationRunnerArguments.class=com.jlucraft.manager.nativecore.BiometricVaultTest -Pandroid.testInstrumentationRunnerArguments.biometricVault=true)
    (
      while true; do
        adb -s "$ANDROID_SERIAL" emu finger remove >/dev/null 2>&1 || exit
        adb -s "$ANDROID_SERIAL" emu finger touch 1 >/dev/null 2>&1 || exit
        sleep 1
      done
    ) &
    finger_pid=$!
    trap 'kill "$finger_pid" 2>/dev/null || true; adb -s "$ANDROID_SERIAL" emu finger remove >/dev/null 2>&1 || true' EXIT
    ;;
  staging)
    : "${ANDROID_SERIAL:?Set ANDROID_SERIAL to the dedicated test device or emulator}"
    : "${STAGING_FIXTURE:?Set STAGING_FIXTURE to the device directory holding the disposable staging fixture}"
    tasks=(:app:testDebugUnitTest :app:connectedDebugAndroidTest)
    runner_args=(-Pandroid.testInstrumentationRunnerArguments.class=com.jlucraft.manager.nativecore.StagingChairFlowTest "-Pandroid.testInstrumentationRunnerArguments.stagingFixture=$STAGING_FIXTURE")
    if [[ -n "${TUTORIAL_OUTPUT_DIR:-}" ]]; then
      capture_run="$(date -u +%Y%m%dT%H%M%S)-$$"
      runner_args+=(-Pandroid.testInstrumentationRunnerArguments.tutorialCapture=true "-Pandroid.testInstrumentationRunnerArguments.captureRunId=$capture_run")
    fi
    ;;
  screenshots)
    : "${ANDROID_SERIAL:?Set ANDROID_SERIAL to the dedicated unenrolled test emulator}"
    : "${TUTORIAL_OUTPUT_DIR:?Set TUTORIAL_OUTPUT_DIR to a new evidence directory}"
    tasks=(:app:testDebugUnitTest :app:connectedDebugAndroidTest)
    capture_run="$(date -u +%Y%m%dT%H%M%S)-$$"
    runner_args=(-Pandroid.testInstrumentationRunnerArguments.notAnnotation=com.jlucraft.manager.nativecore.RequiresFixture -Pandroid.testInstrumentationRunnerArguments.tutorialCapture=true "-Pandroid.testInstrumentationRunnerArguments.captureRunId=$capture_run")
    ;;
  *) printf 'Usage: %s [unit|build|connected|office|biometric|staging|screenshots]\n' "$0" >&2; exit 2 ;;
esac
# Keep the launcher/game usable while testing. Native libraries must be built first.
./gradlew --no-daemon --max-workers=1 \
  '-Dorg.gradle.jvmargs=-Xmx768m -XX:MaxMetaspaceSize=384m -Dfile.encoding=UTF-8' \
  -Pkotlin.compiler.execution.strategy=in-process "${runner_args[@]}" "${tasks[@]}"
if [[ -n "${capture_run:-}" ]]; then
  mkdir -p "$TUTORIAL_OUTPUT_DIR"
  mapfile -t files < <(adb -s "$ANDROID_SERIAL" shell "ls /data/local/tmp/jlucraft-e2e-$capture_run-*.png 2>/dev/null" | tr -d '\r')
  for remote in "${files[@]}"; do
    adb -s "$ANDROID_SERIAL" pull "$remote" "$TUTORIAL_OUTPUT_DIR/$(basename "$remote" | sed "s/^jlucraft-e2e-$capture_run-//")"
  done
fi
