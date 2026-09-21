#!/usr/bin/env bash
set -euo pipefail

java_major=$(java -version 2>&1 | sed -n '1s/.*version "\([0-9][0-9]*\).*/\1/p')
if [[ "$java_major" != "17" ]]; then
  echo "Field Atlas requires JDK 17; found ${java_major:-unknown}." >&2
  exit 1
fi

packages=(
  "platforms;android-36"
  "build-tools;35.0.0"
  "platform-tools"
  "ndk;29.0.13113456"
  "cmake;3.31.6"
)

repo_root=$(cd "$(dirname "$0")/.." && pwd -P)
sdk_root=$(python3 "$repo_root/scripts/android_sdk.py")
missing_packages=()
[[ -f "$sdk_root/platforms/android-36/android.jar" ]] || missing_packages+=("platforms;android-36")
[[ -x "$sdk_root/build-tools/35.0.0/aapt2" ]] || missing_packages+=("build-tools;35.0.0")
[[ -x "$sdk_root/platform-tools/adb" ]] || missing_packages+=("platform-tools")
[[ -f "$sdk_root/ndk/29.0.13113456/build/cmake/android.toolchain.cmake" ]] || missing_packages+=("ndk;29.0.13113456")
[[ -x "$sdk_root/cmake/3.31.6/bin/cmake" ]] || missing_packages+=("cmake;3.31.6")

sdkmanager_path=""

if [[ -x "$sdk_root/cmdline-tools/latest/bin/sdkmanager" ]]; then
  sdkmanager_path="$sdk_root/cmdline-tools/latest/bin/sdkmanager"
elif command -v sdkmanager >/dev/null 2>&1; then
  sdkmanager_path=$(command -v sdkmanager)
fi

if (( ${#missing_packages[@]} == 0 )); then
  :
elif [[ -n "$sdkmanager_path" ]]; then
  "$sdkmanager_path" --sdk_root="$sdk_root" "${missing_packages[@]}"
elif command -v android >/dev/null 2>&1; then
  android sdk install "${missing_packages[@]}"
else
  echo "Install the Android command-line tools and add android or sdkmanager to PATH." >&2
  exit 1
fi

missing_after_install=()
[[ -f "$sdk_root/platforms/android-36/android.jar" ]] || missing_after_install+=("platforms;android-36")
[[ -x "$sdk_root/build-tools/35.0.0/aapt2" ]] || missing_after_install+=("build-tools;35.0.0")
[[ -x "$sdk_root/platform-tools/adb" ]] || missing_after_install+=("platform-tools")
[[ -f "$sdk_root/ndk/29.0.13113456/build/cmake/android.toolchain.cmake" ]] || missing_after_install+=("ndk;29.0.13113456")
[[ -x "$sdk_root/cmake/3.31.6/bin/cmake" ]] || missing_after_install+=("cmake;3.31.6")
if (( ${#missing_after_install[@]} != 0 )); then
  printf 'Android toolchain is incomplete under %s: %s\n' "$sdk_root" "${missing_after_install[*]}" >&2
  exit 1
fi

python3 "$repo_root/scripts/android_sdk.py" --write-local-properties "$repo_root/local.properties" >/dev/null
echo "Android toolchain is ready at $sdk_root."
echo "Next: ./gradlew --offline --no-configuration-cache :app:testDebugUnitTest :app:lintRelease :app:assembleDebug :app:assembleRelease"
