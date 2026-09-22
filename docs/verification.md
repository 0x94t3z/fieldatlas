# Verification

Field Atlas treats release claims as evidence gates. The repository checks unit behavior, deterministic pack creation, manifest safety, native assembly, Android lint, release retention, and APK offline policy.

## Automated checks

```sh
python3 -m unittest discover packtool/tests
python3 -m unittest discover scripts/tests
python3 -m unittest discover benchmarks/tests
./gradlew --no-configuration-cache :app:testDebugUnitTest :app:lintRelease
./gradlew --no-configuration-cache :app:assembleRelease
./scripts/verify_offline.sh app/build/outputs/apk/release/app-release-unsigned.apk
```

The offline-policy audit rejects network permissions, cleartext traffic, common network clients, Firebase and Google Play Services integrations, unexpected ABIs, and missing native inference libraries.

## Resource boundaries

- Android API 33 or newer, ARM64 only.
- `StorageBudget` caps all installed packs at 50,000,000,000 bytes and keeps extraction headroom.
- Model weights remain in app-private storage and are mapped by the local llama.cpp runtime.
- The app requests no network permission and core use does not depend on Google Play Services.

## Physical-device record

The sanitized [Infinix X6840 record](evidence/physical/infinix-x6840-android16/README.md) documents a signed, radios-off model load and cited answer on a 4 GB Android 16 handset. It includes measured memory and timing observations, screenshots, and a device recording without retaining the device serial or user data.

GrapheneOS compatibility follows the standard Android document picker, app-private storage, and bundled ARM64 runtime design. A dedicated physical Pixel/GrapheneOS record should still accompany any device-specific compatibility claim.
