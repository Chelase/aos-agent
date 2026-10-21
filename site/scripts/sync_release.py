"""Validate a GitHub Release response before it can supply public download links."""
import argparse
import json
import re
from datetime import datetime
from pathlib import Path

REPOSITORY = "https://github.com/Chelase/aos-agent"


def from_github(release):
    if release.get("draft") is not False or release.get("prerelease") is not False:
        raise ValueError("Only published stable releases are accepted")
    tag = release.get("tag_name", "")
    if not re.fullmatch(r"v\d+\.\d+\.\d+", tag):
        raise ValueError("Invalid release tag")
    version = tag[1:]
    release_url = f"{REPOSITORY}/releases/tag/{tag}"
    if release.get("html_url") != release_url:
        raise ValueError("Release must belong to the official repository")
    filename = f"aos-agent-{version}.apk"
    matches = [a for a in release.get("assets", []) if a.get("name") == filename]
    if len(matches) != 1:
        raise ValueError("Exactly one matching APK is required")
    asset = matches[0]
    expected = f"{REPOSITORY}/releases/download/{tag}/{filename}"
    if asset.get("browser_download_url") != expected or asset.get("state") != "uploaded":
        raise ValueError("Unexpected APK URL or upload state")
    digest = asset.get("digest", "")
    if not re.fullmatch(r"sha256:[0-9a-f]{64}", digest):
        raise ValueError("A GitHub SHA-256 asset digest is required")
    result = dict(version=version, tag=tag, publishedAt=release.get("published_at"),
                  apkUrl=expected, releaseUrl=release_url, size=asset.get("size"), sha256=digest[7:])
    return validate(result)


def validate(data):
    version = data.get("version", "")
    if not re.fullmatch(r"\d+\.\d+\.\d+", version):
        raise ValueError("Invalid version")
    tag = f"v{version}"
    if data.get("tag") != tag:
        raise ValueError("Version and tag differ")
    if data.get("apkUrl") != f"{REPOSITORY}/releases/download/{tag}/aos-agent-{version}.apk":
        raise ValueError("APK URL is not the official version asset")
    if data.get("releaseUrl") != f"{REPOSITORY}/releases/tag/{tag}":
        raise ValueError("Invalid release URL")
    if type(data.get("size")) is not int or data["size"] <= 0:
        raise ValueError("Invalid APK size")
    if not re.fullmatch(r"[0-9a-f]{64}", data.get("sha256", "")):
        raise ValueError("Invalid APK digest")
    published = datetime.fromisoformat(data["publishedAt"].replace("Z", "+00:00"))
    if published.tzinfo is None:
        raise ValueError("Publication time must have a timezone")
    return data


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--input", type=Path, required=True)
    parser.add_argument("--output", type=Path, default=Path(__file__).resolve().parents[1] / "data/release.json")
    args = parser.parse_args()
    data = from_github(json.loads(args.input.read_text(encoding="utf-8-sig")))
    args.output.write_text(json.dumps(data, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(f"Validated official release {data['tag']}")
