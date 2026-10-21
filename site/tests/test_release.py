import copy
import json
import sys
import unittest
from pathlib import Path

SITE = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(SITE / "scripts"))
from sync_release import from_github, validate


class ReleaseMetadataTest(unittest.TestCase):
    def setUp(self):
        self.data = json.loads((SITE / "data/release.json").read_text(encoding="utf-8"))
        self.release = {"draft": False, "prerelease": False, "tag_name": self.data["tag"],
                        "html_url": self.data["releaseUrl"], "published_at": self.data["publishedAt"],
                        "assets": [{"name": f"aos-agent-{self.data['version']}.apk", "state": "uploaded",
                                    "browser_download_url": self.data["apkUrl"], "size": self.data["size"],
                                    "digest": "sha256:" + self.data["sha256"]}]}

    def test_published_official_release_is_extracted(self):
        self.assertEqual(self.data, from_github(self.release))

    def test_draft_and_prerelease_cannot_become_download(self):
        for key in ["draft", "prerelease"]:
            value = copy.deepcopy(self.release)
            value[key] = True
            with self.subTest(key=key), self.assertRaises(ValueError):
                from_github(value)

    def test_url_cannot_leave_repository_or_protocol(self):
        for bad in ["http://github.com/Chelase/aos-agent/releases/download/v1.1.0/aos-agent-1.1.0.apk",
                    "https://github.com.attacker.example/Chelase/aos-agent/releases/download/v1.1.0/aos-agent-1.1.0.apk",
                    "https://github.com/other/repo/releases/download/v1.1.0/aos-agent-1.1.0.apk"]:
            value = copy.deepcopy(self.release)
            value["assets"][0]["browser_download_url"] = bad
            with self.subTest(url=bad), self.assertRaises(ValueError):
                from_github(value)

    def test_missing_duplicate_and_unfinished_apk_rejected(self):
        for assets in [[], self.release["assets"] * 2, [dict(self.release["assets"][0], state="new")]]:
            value = dict(self.release, assets=assets)
            with self.subTest(assets=assets), self.assertRaises(ValueError):
                from_github(value)

    def test_missing_and_invalid_checksum_rejected(self):
        for digest in ["", "md5:" + "a" * 32, "sha256:<script>"]:
            value = copy.deepcopy(self.release)
            value["assets"][0]["digest"] = digest
            with self.subTest(digest=digest), self.assertRaises(ValueError):
                from_github(value)

    def test_version_tag_and_size_must_be_coherent(self):
        for patch in [{"version": "<script>"}, {"tag": "v9.9.9"}, {"size": 0}, {"size": True}]:
            value = dict(self.data, **patch)
            with self.subTest(patch=patch), self.assertRaises(ValueError):
                validate(value)

    def test_publication_time_must_be_real_and_zoned(self):
        for date in ["2026-99-99T00:00:00Z", "2026-10-20T14:20:42"]:
            with self.subTest(date=date), self.assertRaises(ValueError):
                validate(dict(self.data, publishedAt=date))


if __name__ == "__main__":
    unittest.main()
