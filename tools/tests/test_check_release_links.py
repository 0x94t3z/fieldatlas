"""Docs and the website may only link to releases, assets and files a reader can get."""

import unittest

from tools.maintenance.check_release_links import links, problems

BASE = "https://github.com/0x94t3z/fieldatlas"


class FakeGitHub:
    """Published v1.4.1 with fieldatlas.apk; v1.1.9 a tag without a release; v1.5.0 a draft; MODELS.md at v1.4.1."""

    def __init__(self, offline=False):
        self.offline = offline
        self.heads = []

    def head(self, url):
        if self.offline:
            raise OSError("no network")
        self.heads.append(url)
        if url == f"{BASE}/releases/download/v1.4.1/fieldatlas.apk":
            return 302
        if url == f"{BASE}/blob/v1.4.1/MODELS.md":
            return 200
        return 404

    def release(self, tag):
        if self.offline:
            raise OSError("no network")
        return {"v1.4.1": {"draft": False}, "v1.5.0": {"draft": True}}.get(tag)


class ReleaseLinkTests(unittest.TestCase):
    def test_finds_downloads_release_pages_and_files_at_a_tag(self):
        text = (f"[Download]({BASE}/releases/download/v1.4.2/fieldatlas.apk).\n"
                f"<a href=\"{BASE}/releases/tag/v1.4.1\">v1.4.1</a>\n"
                f"[guide]({BASE}/blob/v1.4.0/MODELS.md#models) [rc]({BASE}/releases/tag/v1.2.0-rc.1)\n"
                f"[latest]({BASE}/releases/latest) [other](https://github.com/x/y/releases/tag/v9.9.9)\n")
        self.assertEqual(links(text), [
            (1, "download", "v1.4.2", f"{BASE}/releases/download/v1.4.2/fieldatlas.apk"),
            (2, "release", "v1.4.1", f"{BASE}/releases/tag/v1.4.1"),
            (3, "file", "v1.4.0", f"{BASE}/blob/v1.4.0/MODELS.md"),
            (3, "release", "v1.2.0-rc.1", f"{BASE}/releases/tag/v1.2.0-rc.1"),
        ])

    def test_a_published_release_and_its_asset_pass(self):
        gh = FakeGitHub()
        files = {"README.md": f"{BASE}/releases/download/v1.4.1/fieldatlas.apk\n{BASE}/releases/tag/v1.4.1\n{BASE}/blob/v1.4.1/MODELS.md\n"}
        self.assertEqual(problems(files, gh.head, gh.release), [])
        # The redirect to the asset is not followed: only the link itself is asked for.
        self.assertNotIn("objects.githubusercontent.com", " ".join(gh.heads))

    def test_a_tag_without_a_release_fails_though_github_shows_its_page(self):
        gh = FakeGitHub()
        self.assertEqual(problems({"MODELS.md": f"{BASE}/releases/tag/v1.1.9\n"}, gh.head, gh.release),
                         [f"MODELS.md:1 {BASE}/releases/tag/v1.1.9: no published release"])

    def test_a_draft_release_and_a_missing_asset_fail(self):
        gh = FakeGitHub()
        found = problems({"index.html": f"{BASE}/releases/tag/v1.5.0\n{BASE}/releases/download/v1.4.1/missing.apk\n"},
                         gh.head, gh.release)
        self.assertEqual(found, [f"index.html:1 {BASE}/releases/tag/v1.5.0: the release is a draft",
                                 f"index.html:2 {BASE}/releases/download/v1.4.1/missing.apk: no published asset (HTTP 404)"])

    def test_an_unpublished_release_fails_everywhere_it_is_linked(self):
        gh = FakeGitHub()
        found = problems({"README.md": f"{BASE}/releases/download/v1.4.2/fieldatlas.apk\n{BASE}/releases/tag/v1.4.2\n",
                          "docs/installation.md": f"{BASE}/blob/v1.4.2/MODELS.md\n"}, gh.head, gh.release)
        self.assertEqual(len(found), 3)
        self.assertTrue(all("v1.4.2" in line for line in found))

    def test_a_check_that_cannot_reach_github_fails_rather_than_passes(self):
        gh = FakeGitHub(offline=True)
        found = problems({"README.md": f"{BASE}/releases/tag/v1.4.1\n{BASE}/blob/v1.4.1/MODELS.md\n"}, gh.head, gh.release)
        self.assertEqual(len(found), 2)
        self.assertTrue(all("could not be checked" in line for line in found))


if __name__ == "__main__":
    unittest.main()
