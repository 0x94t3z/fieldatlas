#!/usr/bin/env python3
"""Verify a pinned HTTPS source and range recovery without storing its payload.

Host transport evidence only: not Android installation, lifecycle, storage peaks,
publisher authentication or a provider recommendation. Output stays in build/.
"""

# Support both direct CLI execution and package imports.
import sys as _sys
from pathlib import Path as _Path
if __package__ in (None, ""):
    _sys.path.insert(0, str(_Path(__file__).resolve().parents[2]))

import argparse
import hashlib
import json
from pathlib import Path
import re
import time
import urllib.parse
import urllib.request

ROOT = Path(__file__).resolve().parents[2]
BLOCK = 1024 * 1024


def validate_url(url):
    parsed = urllib.parse.urlsplit(url)
    if parsed.scheme != "https" or not parsed.hostname or parsed.username is not None or parsed.password is not None or parsed.fragment:
        raise ValueError("Expected HTTPS URL without credentials or fragment")
    return parsed


class HttpsRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, request, response, code, message, headers, new_url):
        validate_url(new_url)
        return super().redirect_request(request, response, code, message, headers, new_url)


def verify(url, expected_bytes, expected_sha256, interruption_bytes=2 * BLOCK, open_url=None, progress=None):
    parsed = validate_url(url)
    if not isinstance(expected_bytes, int) or isinstance(expected_bytes, bool) or expected_bytes < 2:
        raise ValueError("Expected size must be at least two bytes")
    if not re.fullmatch(r"[0-9a-f]{64}", expected_sha256):
        raise ValueError("Expected lowercase SHA-256 required")
    if not 1 <= interruption_bytes < expected_bytes:
        raise ValueError("Interruption must precede completion")
    opener = open_url or urllib.request.build_opener(HttpsRedirect()).open
    headers = {"User-Agent": "FieldAtlas-Source-Verification/1.0", "Accept-Encoding": "identity"}
    started = time.monotonic()
    digest = hashlib.sha256()
    saved = 0
    with opener(urllib.request.Request(url, headers=headers), timeout=30) as response:
        validate_url(response.geturl())
        if response.status != 200:
            raise ValueError("Initial source did not return 200")
        while saved < interruption_bytes:
            block = response.read(min(BLOCK, interruption_bytes - saved))
            if not block:
                raise ValueError("Initial source ended before interruption")
            digest.update(block)
            saved += len(block)
    # New connection after deliberately closing the first incomplete response.
    with opener(urllib.request.Request(url, headers={**headers, "Range": f"bytes={saved}-"}), timeout=30) as response:
        validate_url(response.geturl())
        resumed = response.status == 206
        if resumed:
            expected_range = f"bytes {saved}-{expected_bytes - 1}/{expected_bytes}"
            if response.headers.get("Content-Range") != expected_range:
                raise ValueError("Resumed response returned incompatible Content-Range")
            total = saved
        elif response.status == 200:
            digest = hashlib.sha256()
            total = 0
        else:
            raise ValueError("Source neither resumed nor safely restarted")
        checkpoint = total // (64 * BLOCK)
        while block := response.read(BLOCK):
            total += len(block)
            if total > expected_bytes:
                raise ValueError("Source exceeded pinned size")
            digest.update(block)
            if progress and total // (64 * BLOCK) > checkpoint:
                checkpoint = total // (64 * BLOCK)
                progress(total)
        if total != expected_bytes or digest.hexdigest() != expected_sha256:
            raise ValueError("Complete source size/checksum did not match pinned artifact")
    return {"scope": "Host streaming transport only, no payload installed or retained",
            "sourceHost": parsed.hostname, "verifiedBytes": total, "sha256": digest.hexdigest(),
            "interruptedAtBytes": saved, "recovery": "range-resume" if resumed else "full-restart",
            "elapsedSeconds": time.monotonic() - started, "fullHashVerified": True,
            "androidAcceptance": False, "releaseReady": False}


def main():
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--url", required=True)
    parser.add_argument("--bytes", type=int, required=True)
    parser.add_argument("--sha256", required=True)
    parser.add_argument("--output", required=True, type=Path)
    args = parser.parse_args()
    output = args.output.resolve()
    if not output.is_relative_to(ROOT / "build") or output.exists():
        parser.error("Expected new private output file in build/")
    try:
        result = verify(args.url, args.bytes, args.sha256, progress=lambda n: print(f"Verified stream received {n} bytes", flush=True))
    except Exception as error:
        # Never include redirect URLs, access tokens or credential-bearing exception messages.
        result = {"status": "FAILED", "errorType": type(error).__name__, "fullHashVerified": False,
                  "androidAcceptance": False, "releaseReady": False}
    output.parent.mkdir(parents=True, exist_ok=True)
    with output.open("x") as stream:
        json.dump(result, stream, indent=2)
        stream.write("\n")
    print(output)
    return 0 if result.get("fullHashVerified") else 1


if __name__ == "__main__":
    raise SystemExit(main())
