# Development scripts

Run commands from the repository root. Build and release entry points stay here;
research evaluations live in their own folder.

| Location | Purpose |
| --- | --- |
| `install_toolchain.sh`, `android_sdk.py` | Android SDK setup and discovery |
| `build_starter_pack.sh`, `build_reference_pack.sh` | Small bundled/demo pack builds |
| `model_manifest.py` | Model manifest generation |
| `verify_offline.sh`, `check_native_alignment.py`, `verify_release_reflection.py` | APK checks |
| `device_acceptance.sh` | Physical-device acceptance evidence |
| [research/](research/) | Desktop research, claim checks, answer review and baseline comparisons |
| [patches/](patches/) | Reproducible patches for the pinned inference runtime |
| [tests/](tests/) | Script regression tests |

```sh
python3 -m unittest discover -s scripts/tests -v
python3 scripts/research/run_desktop_research.py --help
```

Pack builders, native retrieval helpers and evaluation inputs are documented in
[the tools guide](../tools/README.md). Generated packs, reports and device captures
belong under ignored `build/` directories, not beside source code.

Research commands previously at `scripts/<name>.py` now live at
`scripts/research/<name>.py`. Update local commands accordingly; their arguments
are unchanged.
