#!/usr/bin/env python3
"""Fail when the docs or the website link to a release, asset or file that GitHub does not serve.

A tag is not a release: github.com shows a page for a tag with no release, and a draft release is
invisible to readers. So each kind of link is checked for what a reader would get:

- a download (`releases/download/vX/fieldatlas.apk`): GitHub must answer it with the file (200)
  or a redirect to it (30x), which it does only for an uploaded asset of a published release;
- a release page (`releases/tag/vX`): the release must be published (not a draft), read from the
  GitHub API, unless a download of the same tag already proved it;
- a file at a tag (`blob/vX/MODELS.md`): the page must exist (200).

Redirects are not followed, so no asset is downloaded. Set GITHUB_TOKEN to avoid the API's
unauthenticated rate limit. This script blocks nothing by itself: CI runs it after a push, the
website's build runs its own copy (website/tools/check-release-links.mjs) before a deployment, and a
person runs it before pushing.

    python3 tools/maintenance/check_release_links.py
    python3 tools/maintenance/check_release_links.py README.md docs/installation.md
"""

from __future__ import annotations

import argparse
import json
import os
import re
import sys
import urllib.error
import urllib.request
from collections.abc import Callable
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
REPO = "0x94t3z/fieldatlas"
TAG = r"v\d+\.\d+\.\d+(?:-[0-9A-Za-z.]+)?"
DEFAULT_FILES = ("README.md", "MODELS.md", "docs/installation.md", "docs/releases/current.md", "website/index.html",
                 "website/README.md")


def link_pattern(repo: str) -> re.Pattern[str]:
    return re.compile(r"https://github\.com/" + re.escape(repo) +
                      r"/(?:(releases/download)/(" + TAG + r")/([^\s\"'<>)]+)|(releases/tag)/(" + TAG + r")|"
                      r"(blob|tree)/(" + TAG + r")/([^\s\"'<>)#]+))")


def links(text: str, repo: str = REPO) -> list[tuple[int, str, str, str]]:
    """Each (line number, kind, tag, url): kind is "download", "release" or "file"."""
    found = []
    for number, line in enumerate(text.splitlines(), start=1):
        for match in link_pattern(repo).finditer(line):
            # A link at the end of a sentence keeps its full stop out: "…/fieldatlas.apk."
            url = match.group(0).rstrip(".,;:")
            if match.group(1):
                found.append((number, "download", match.group(2), url))
            elif match.group(4):
                found.append((number, "release", match.group(5), url))
            else:
                found.append((number, "file", match.group(7), url))
    return found


Head = Callable[[str], int]
"""The HTTP status a HEAD request gets, without following redirects; raises OSError when it cannot be asked."""
Release = Callable[[str], "dict | None"]
"""The published or draft release for a tag as the API returns it, None when there is none."""


def problems(files: dict[str, str], head: Head, release: Release, repo: str = REPO) -> list[str]:
    """'path:line url: why' for every link a reader would not get."""
    found = [(path, number, kind, tag, url) for path, text in files.items() for number, kind, tag, url in links(text, repo)]
    status: dict[str, str | None] = {}

    def checked_head(url: str, ok: Callable[[int], bool], what: str) -> str | None:
        if url not in status:
            try:
                code = head(url)
                status[url] = None if ok(code) else f"{what} (HTTP {code})"
            except OSError as error:
                status[url] = f"could not be checked ({error})"
        return status[url]

    proven: set[str] = set()
    for _, _, kind, tag, url in found:
        if kind == "download" and checked_head(url, lambda c: 200 <= c < 400, "no published asset") is None:
            proven.add(tag)
    releases: dict[str, str | None] = {}
    for _, _, kind, tag, _ in found:
        if kind != "release" or tag in proven or tag in releases:
            continue
        try:
            data = release(tag)
            releases[tag] = ("no published release" if data is None else
                             "the release is a draft" if data.get("draft") else None)
        except OSError as error:
            releases[tag] = f"could not be checked ({error})"
    out = []
    for path, number, kind, tag, url in found:
        why = (checked_head(url, lambda c: 200 <= c < 400, "no published asset") if kind == "download" else
               None if kind == "release" and tag in proven else
               releases.get(tag) if kind == "release" else
               checked_head(url, lambda c: c == 200, "no such file at this tag"))
        if why:
            out.append(f"{path}:{number} {url}: {why}")
    return out


class _NoRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, req, fp, code, msg, headers, newurl):  # noqa: D401 - urllib's signature
        return None


def http_head(url: str) -> int:
    opener = urllib.request.build_opener(_NoRedirect)
    request = urllib.request.Request(url, method="HEAD", headers={"User-Agent": "fieldatlas-release-links"})
    try:
        with opener.open(request, timeout=30) as response:
            return response.status
    except urllib.error.HTTPError as error:
        return error.code


def api_release(repo: str) -> Release:
    def fetch(tag: str) -> dict | None:
        headers = {"Accept": "application/vnd.github+json", "User-Agent": "fieldatlas-release-links"}
        if os.environ.get("GITHUB_TOKEN"):
            headers["Authorization"] = "Bearer " + os.environ["GITHUB_TOKEN"]
        request = urllib.request.Request(f"https://api.github.com/repos/{repo}/releases/tags/{tag}", headers=headers)
        try:
            with urllib.request.urlopen(request, timeout=30) as response:
                return json.load(response)
        except urllib.error.HTTPError as error:
            if error.code == 404:
                return None
            raise OSError(f"GitHub API answered {error.code}") from error
    return fetch


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description="Fail when docs or the website link to a release, asset or file GitHub does not serve.")
    parser.add_argument("files", nargs="*", help="files to check, relative to the repository (default: the release docs and website)")
    parser.add_argument("--repo", default=REPO, help="owner/name of the GitHub repository the links point at")
    args = parser.parse_args(argv)
    paths = args.files or [p for p in DEFAULT_FILES if (ROOT / p).is_file()]
    found = problems({p: (ROOT / p).read_text(encoding="utf-8") for p in paths}, http_head, api_release(args.repo), args.repo)
    for problem in found:
        print(f"not served: {problem}")
    if found:
        print(f"{len(found)} link(s) a reader would not get. Publish the release first (not to make this pass), or keep these docs local.")
        return 1
    print(f"ok: every release, asset and file linked in {len(paths)} file(s) is served")
    return 0


if __name__ == "__main__":
    sys.exit(main())
