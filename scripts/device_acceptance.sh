#!/usr/bin/env bash
set -euo pipefail

configure=false
if [[ ${1:-} == "--configure-radio-state" ]]; then
  configure=true
  shift
fi
if [[ $# -ne 2 ]]; then
  echo "Usage: $0 [--configure-radio-state] app.apk evidence-directory" >&2
  exit 2
fi

apk=$1
evidence_dir=$2
if [[ ! -f "$apk" ]]; then echo "APK not found: $apk" >&2; exit 2; fi
if [[ -e "$evidence_dir" ]]; then echo "Refusing to overwrite: $evidence_dir" >&2; exit 2; fi

devices=$(adb devices | awk 'NR > 1 && $2 == "device" {print $1}')
device_count=$(adb devices | awk 'NR > 1 && $2 == "device" {count++} END {print count+0}')
if [[ $device_count -ne 1 ]]; then
  echo "Exactly one authorized ADB device is required; found $device_count" >&2
  exit 1
fi
serial=$devices

mkdir -p "$evidence_dir"
if $configure; then
  adb -s "$serial" shell settings put global airplane_mode_on 1
  adb -s "$serial" shell am broadcast -a android.intent.action.AIRPLANE_MODE --ez state true >/dev/null
  adb -s "$serial" shell svc wifi disable
  adb -s "$serial" shell svc data disable
fi

airplane=$(adb -s "$serial" shell settings get global airplane_mode_on | tr -d '\r')
wifi=$(adb -s "$serial" shell settings get global wifi_on | tr -d '\r')
mobile=$(adb -s "$serial" shell settings get global mobile_data | tr -d '\r')
if [[ "$airplane" != "1" || "$wifi" != "0" || "$mobile" != "0" ]]; then
  printf 'Refusing offline label: airplane=%s wifi=%s mobile=%s\n' "$airplane" "$wifi" "$mobile" >&2
  exit 1
fi

shasum -a 256 "$apk" >"$evidence_dir/apk-sha256.txt"
adb -s "$serial" shell getprop >"$evidence_dir/getprop.txt"
printf 'serial=%s\nairplane=%s\nwifi=%s\nmobile=%s\n' "$serial" "$airplane" "$wifi" "$mobile" \
  >"$evidence_dir/radio-state.txt"
adb -s "$serial" install -r "$apk" | tee "$evidence_dir/install.txt"
adb -s "$serial" shell am force-stop xyz.fieldatlas
adb -s "$serial" shell monkey -p xyz.fieldatlas -c android.intent.category.LAUNCHER 1 \
  >"$evidence_dir/launch.txt"
adb -s "$serial" shell dumpsys meminfo xyz.fieldatlas >"$evidence_dir/meminfo-after-launch.txt"
adb -s "$serial" shell df -k /data >"$evidence_dir/data-filesystem.txt"
adb -s "$serial" logcat -d -b crash >"$evidence_dir/crash-buffer.txt"
adb -s "$serial" shell dumpsys activity lastanr >"$evidence_dir/last-anr.txt"

echo "Captured baseline device evidence in $evidence_dir"
echo "Run the interactive matrix in docs/device-testing.md, then export diagnostics and benchmark results there."
