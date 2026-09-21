#!/usr/bin/env bash
set -euo pipefail

if [[ $# -ne 1 ]]; then
  echo "Usage: $0 path/to/app.apk" >&2
  exit 2
fi

apk=$1
if [[ ! -f "$apk" ]]; then
  echo "APK not found: $apk" >&2
  exit 2
fi

sdk_root=${ANDROID_HOME:-${ANDROID_SDK_ROOT:-/opt/homebrew/share/android-commandlinetools}}
apkanalyzer="$sdk_root/cmdline-tools/latest/bin/apkanalyzer"
if [[ ! -x "$apkanalyzer" ]]; then
  echo "apkanalyzer not found under $sdk_root" >&2
  exit 2
fi

manifest=$($apkanalyzer manifest print "$apk")
permissions=$($apkanalyzer manifest permissions "$apk")
packages=$($apkanalyzer dex packages "$apk")
files=$($apkanalyzer files list "$apk")

if grep -Fq 'android.permission.INTERNET' <<<"$permissions"; then
  echo "FAIL: APK requests INTERNET permission" >&2
  exit 1
fi
if grep -Fq 'android.permission.ACCESS_NETWORK_STATE' <<<"$permissions"; then
  echo "FAIL: APK requests ACCESS_NETWORK_STATE permission" >&2
  exit 1
fi
if ! grep -Eq 'android:usesCleartextTraffic="false"|android:usesCleartextTraffic="0"' <<<"$manifest"; then
  echo "FAIL: merged manifest does not explicitly disable cleartext traffic" >&2
  exit 1
fi
if ! grep -Eq 'android:extractNativeLibs="true"|android:extractNativeLibs="-1"' <<<"$manifest"; then
  echo "FAIL: native libraries must be extracted for dynamic llama.cpp backend loading" >&2
  exit 1
fi

for forbidden in \
  'com.google.android.gms' \
  'com.google.firebase' \
  'okhttp3' \
  'retrofit2' \
  'io.ktor.client' \
  'com.android.volley'
do
  if grep -Fq "$forbidden" <<<"$packages"; then
    echo "FAIL: forbidden network/service package present: $forbidden" >&2
    exit 1
  fi
done

if ! grep -Fq '/lib/arm64-v8a/libai-chat.so' <<<"$files"; then
  echo "FAIL: ARM64 libai-chat.so is missing" >&2
  exit 1
fi
if ! grep -Fq '/lib/arm64-v8a/libggml-cpu-' <<<"$files"; then
  echo "FAIL: ARM64 dynamic CPU backends are missing" >&2
  exit 1
fi
unsupported=$(grep -E '/lib/(armeabi-v7a|x86|x86_64|mips[^/]*)/' <<<"$files" || true)
if [[ -n "$unsupported" ]]; then
  echo "FAIL: APK contains unsupported ABI libraries" >&2
  echo "$unsupported" >&2
  exit 1
fi

bytes=$(wc -c <"$apk" | tr -d ' ')
sha256=$(shasum -a 256 "$apk" | awk '{print $1}')
echo "PASS: offline APK audit"
echo "bytes=$bytes"
echo "sha256=$sha256"
echo "abi=arm64-v8a"
