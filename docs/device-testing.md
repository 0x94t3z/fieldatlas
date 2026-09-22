# Physical-device testing

Run the full matrix on the lowest-memory target first, then repeat the tagged build on a supported Pixel running GrapheneOS. Emulator results do not replace physical-device evidence.

Automated Compose instrumentation has also run on a paired **Infinix X6840, Android 16/API 36, ARM64**. This is not represented as the Smart 20 or GrapheneOS target. The sanitized record is under `docs/evidence/physical/infinix-x6840-android16/`. It proves native installation and UI execution only; it does not replace the offline model/packs matrix below.

Record the exact APK, model, and knowledge-pack SHA-256 values; manufacturer/model; Android version/API; ABIs; OS-reported physical RAM; app memory class; measured PSS/RSS; free and installed bytes; airplane/Wi-Fi/mobile state; first-load seconds; retrieval time; time to first token; generated token count and total time; crashes, ANRs, and thermal observations.

Test at least:

- clean install and two-pack import;
- corrupt pack, duplicate version, and insufficient-space rejection;
- offline launch and research after reboot;
- a hard explanation, comparison, synthesis, and reasoning query;
- exact evidence passage navigation;
- stop during generation, background during generation, unload, and reload;
- repeated queries until thermal throttling is visible or reasonably excluded;
- all 18 frozen benchmark prompts.

Airplane mode alone is not sufficient evidence: explicitly verify Wi-Fi and mobile data are off. Capture `dumpsys meminfo`, logcat crash/ANR checks, diagnostics JSON, and unedited screen recording. Publish failures with successes.

With one USB-connected device, capture the baseline without overwriting prior evidence:

```sh
./scripts/device_acceptance.sh --configure-radio-state \
  app/build/outputs/apk/debug/app-debug.apk \
  evidence/physical/DEVICE-RUN-ID
```

Do not use `--configure-radio-state` over wireless ADB: disabling Wi-Fi terminates the transport before the evidence capture can finish.
