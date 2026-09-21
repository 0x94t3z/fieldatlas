from __future__ import annotations

import argparse
from collections.abc import Mapping
import os
from pathlib import Path
import shutil
import tempfile


def discover_sdk_root(env: Mapping[str, str], sdkmanager: Path | None) -> Path:
    android_home = env.get("ANDROID_HOME", "").strip()
    android_sdk_root = env.get("ANDROID_SDK_ROOT", "").strip()
    if android_home and android_sdk_root:
        first = Path(android_home).expanduser().resolve()
        second = Path(android_sdk_root).expanduser().resolve()
        if first != second:
            raise ValueError("ANDROID_HOME and ANDROID_SDK_ROOT disagree")
    if android_home or android_sdk_root:
        return Path(android_home or android_sdk_root).expanduser()
    if sdkmanager is not None:
        resolved = sdkmanager.expanduser().resolve()
        try:
            marker = resolved.parts.index("cmdline-tools")
        except ValueError as error:
            raise ValueError(f"Cannot infer Android SDK root from {resolved}") from error
        return Path(*resolved.parts[:marker])
    homebrew = Path("/opt/homebrew/share/android-commandlinetools")
    if homebrew.is_dir():
        return homebrew
    raise ValueError("Android SDK not found; set ANDROID_HOME")


def render_local_properties(sdk_root: Path) -> str:
    escaped = str(sdk_root).replace("\\", "\\\\").replace(" ", "\\ ").replace(":", "\\:")
    return f"sdk.dir={escaped}\n"


def write_local_properties(sdk_root: Path, destination: Path) -> None:
    destination.parent.mkdir(parents=True, exist_ok=True)
    handle, temporary_name = tempfile.mkstemp(prefix=f".{destination.name}.", dir=destination.parent)
    temporary = Path(temporary_name)
    try:
        with os.fdopen(handle, "w", encoding="utf-8", newline="\n") as output:
            output.write(render_local_properties(sdk_root))
            output.flush()
            os.fsync(output.fileno())
        os.replace(temporary, destination)
    except BaseException:
        temporary.unlink(missing_ok=True)
        raise


def main() -> None:
    parser = argparse.ArgumentParser(description="Resolve the Android SDK used by Field Atlas")
    parser.add_argument("--write-local-properties", type=Path)
    args = parser.parse_args()
    executable = shutil.which("sdkmanager")
    sdk_root = discover_sdk_root(os.environ, Path(executable) if executable else None)
    if args.write_local_properties is not None:
        write_local_properties(sdk_root, args.write_local_properties)
    print(sdk_root)


if __name__ == "__main__":
    main()
