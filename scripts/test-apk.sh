#!/usr/bin/env bash
# Push a real APK to the connected device and launch it through DCLActivity,
# without installing it and without any host-app rebuild or manifest edit.
#
# Usage:
#   scripts/test-apk.sh <path-to-apk> [activityClassName]
#
# If activityClassName is omitted, DCLActivity auto-resolves the target
# APK's own launcher activity from its manifest.
#
# Env:
#   ADB_SERIAL   adb -s target; required if more than one device is attached.
#   SETTLE_SECS  how long to watch logcat for a crash before declaring pass
#                (default 5).

set -euo pipefail

HOST_PACKAGE="com.mikimn.apkloader"
# /data/local/tmp is plain Linux storage outside the /sdcard FUSE/scoped-storage
# layer: world-readable, so any app can open a file there directly with no
# storage permission needed. (The app's own external files dir looked like the
# obvious choice, but files `adb push`ed there land owned by `shell`/`ext_data_rw`
# and are not visible to the app under scoped storage - confirmed on-device.)
DEVICE_TEST_DIR="/data/local/tmp/dcl-tests"
SETTLE_SECS="${SETTLE_SECS:-5}"

if [[ $# -lt 1 ]]; then
    echo "Usage: $0 <path-to-apk> [activityClassName]" >&2
    exit 2
fi

APK_PATH="$1"
ACTIVITY_CLASS="${2:-}"

if [[ ! -f "$APK_PATH" ]]; then
    echo "No such file: $APK_PATH" >&2
    exit 2
fi

ADB=(adb)
if [[ -n "${ADB_SERIAL:-}" ]]; then
    ADB=(adb -s "$ADB_SERIAL")
fi

if [[ "$("${ADB[@]}" devices | grep -c 'device$')" -eq 0 ]]; then
    echo "No adb device attached (check ADB_SERIAL / adb devices)" >&2
    exit 2
fi

APK_NAME="$(basename "$APK_PATH")"
DEVICE_PATH="$DEVICE_TEST_DIR/$APK_NAME"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
LOG_DIR="$SCRIPT_DIR/logs"
mkdir -p "$LOG_DIR"
LOG_FILE="$LOG_DIR/${APK_NAME%.apk}-$(date +%Y%m%d-%H%M%S).log"

echo "==> Pushing $APK_PATH -> $DEVICE_PATH"
"${ADB[@]}" push "$APK_PATH" "$DEVICE_PATH" >/dev/null

echo "==> Force-stopping $HOST_PACKAGE (DCLActivity is singleTask; a stale"
echo "    instance would silently swallow the new launch intent)"
"${ADB[@]}" shell am force-stop "$HOST_PACKAGE"

"${ADB[@]}" logcat -c

START_ARGS=(-n "$HOST_PACKAGE/.dcl.DCLActivity" --es apkAssetFileName "$DEVICE_PATH")
if [[ -n "$ACTIVITY_CLASS" ]]; then
    START_ARGS+=(--es activityClassName "$ACTIVITY_CLASS")
fi

echo "==> Launching DCLActivity for $APK_NAME"
"${ADB[@]}" shell am start "${START_ARGS[@]}"

echo "==> Watching logcat for ${SETTLE_SECS}s..."
CRASHED=0
for _ in $(seq 1 "$SETTLE_SECS"); do
    sleep 1
    if "${ADB[@]}" logcat -d | grep -q "FATAL EXCEPTION"; then
        CRASHED=1
        break
    fi
done

"${ADB[@]}" logcat -d > "$LOG_FILE"

if [[ "$CRASHED" -eq 1 ]]; then
    echo "FAIL: $APK_NAME crashed. Stack trace:"
    grep -A 25 "FATAL EXCEPTION" "$LOG_FILE" | head -30
    echo "Full log: $LOG_FILE"
    exit 1
fi

PID="$("${ADB[@]}" shell pidof "$HOST_PACKAGE" || true)"
if [[ -z "$PID" ]]; then
    echo "FAIL: $APK_NAME - $HOST_PACKAGE process is not running (crashed without a caught FATAL EXCEPTION, or was killed)."
    echo "Full log: $LOG_FILE"
    exit 1
fi

echo "PASS: $APK_NAME (pid $PID). Full log: $LOG_FILE"
