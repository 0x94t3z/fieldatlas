import hashlib
import io
import unittest

from verify_download_source import verify, validate_url


class Response(io.BytesIO):
    def __init__(self, payload, status=200, headers=None, url="https://example.org/file"):
        super().__init__(payload)
        self.status = status
        self.headers = headers or {}
        self.url = url

    def geturl(self):
        return self.url


class SourceVerificationTests(unittest.TestCase):
    def run_transfer(self, second, payload=b"complete pinned bytes", expected_hash=None):
        responses = iter([Response(payload), second])
        requests = []
        def opener(request, timeout):
            requests.append(request)
            return next(responses)
        result = verify("https://example.org/file", len(payload), expected_hash or hashlib.sha256(payload).hexdigest(),
                        interruption_bytes=4, open_url=opener)
        self.assertEqual("bytes=4-", requests[1].get_header("Range"))
        return result

    def test_range_resume_verifies_complete_hash_not_just_suffix(self):
        payload = b"complete pinned bytes"
        result = self.run_transfer(Response(payload[4:], 206, {"Content-Range": f"bytes 4-{len(payload)-1}/{len(payload)}"}))
        self.assertTrue(result["fullHashVerified"])
        self.assertEqual("range-resume", result["recovery"])
        self.assertFalse(result["androidAcceptance"])

    def test_server_ignoring_range_restarts_hash_safely(self):
        result = self.run_transfer(Response(b"complete pinned bytes"))
        self.assertEqual("full-restart", result["recovery"])

    def test_incompatible_range_and_hash_are_rejected(self):
        for response in (Response(b"suffix", 206, {"Content-Range": "bytes 0-5/6"}),
                         Response(b"complete pinned bytes", 206), Response(b"complete pinned bytes", 404),
                         Response(b"wrong")):
            with self.assertRaises(ValueError):
                self.run_transfer(response)
        with self.assertRaisesRegex(ValueError, "checksum"):
            self.run_transfer(Response(b"complete pinned bytes"), expected_hash="0" * 64)

    def test_truncated_and_oversized_sources_are_rejected(self):
        for payload in (b"complete pinned byte", b"complete pinned bytes extra"):
            with self.assertRaises(ValueError):
                self.run_transfer(Response(payload))

    def test_https_and_redirect_policy_cannot_be_weakened(self):
        for url in ("http://example.org/file", "https://user:secret@example.org/file", "https://example.org/file#part", "file:///tmp/payload"):
            with self.assertRaises(ValueError):
                validate_url(url)
        with self.assertRaises(ValueError):
            self.run_transfer(Response(b"complete pinned bytes", url="http://example.org/file"))

    def test_invalid_pins_fail_before_network(self):
        def opener(*args, **kwargs):
            self.fail("Invalid pins must not perform network requests")
        for size, digest in ((0, "0" * 64), (True, "0" * 64), (20, "invalid")):
            with self.assertRaises(ValueError):
                verify("https://example.org/file", size, digest, interruption_bytes=4, open_url=opener)


if __name__ == "__main__":
    unittest.main()
