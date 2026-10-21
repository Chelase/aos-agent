"""Build the self-contained static site; no JS framework or server runtime is needed."""
import hashlib
import html
import json
import os
import re
import shutil
from datetime import datetime
from pathlib import Path
from urllib.parse import urlparse
from sync_release import validate

SITE = Path(__file__).resolve().parents[1]
DIST = SITE / "dist"


def build():
    data = validate(json.loads((SITE / "data/release.json").read_text(encoding="utf-8")))
    site_url = os.environ.get("SITE_URL", "https://chelase.github.io/aos-agent/").rstrip("/") + "/"
    parsed = urlparse(site_url)
    if parsed.scheme != "https" or not parsed.hostname or parsed.username or parsed.password or parsed.query or parsed.fragment:
        raise ValueError("SITE_URL must be a public HTTPS URL without credentials, query or fragment")
    if DIST.resolve().parent != SITE or DIST.name != "dist":
        raise ValueError("Unexpected output directory")
    if DIST.exists():
        shutil.rmtree(DIST)
    DIST.mkdir()
    for name in ["assets", "styles"]:
        shutil.copytree(SITE / name, DIST / name)
    (DIST / "scripts").mkdir()
    shutil.copy2(SITE / "scripts/site.js", DIST / "scripts/site.js")
    checksum = lambda name: hashlib.sha256((SITE / name).read_bytes()).hexdigest()[:12]
    values = {"SITE_URL": site_url, "VERSION": data["version"], "APK_URL": data["apkUrl"],
              "RELEASE_URL": data["releaseUrl"], "PUBLISHED_AT": data["publishedAt"],
              "PUBLISHED_DATE": datetime.fromisoformat(data["publishedAt"].replace("Z", "+00:00")).date().isoformat(),
              "CSS_HASH": checksum("styles/site.css"), "JS_HASH": checksum("scripts/site.js")}
    output = (SITE / "index.html").read_text(encoding="utf-8")
    for key, value in values.items():
        output = output.replace("{{" + key + "}}", html.escape(value, quote=True))
    if re.search(r"\{\{[^}]+\}\}", output):
        raise ValueError("Unresolved template token")
    (DIST / "index.html").write_text(output, encoding="utf-8")
    (DIST / "release.json").write_text(json.dumps(data, indent=2) + "\n", encoding="utf-8")
    (DIST / ".nojekyll").touch()
    (DIST / "robots.txt").write_text(f"User-agent: *\nAllow: /\nSitemap: {site_url}sitemap.xml\n", encoding="utf-8")
    (DIST / "sitemap.xml").write_text(f'<?xml version="1.0" encoding="UTF-8"?><urlset xmlns="http://www.sitemaps.org/schemas/sitemap/0.9"><url><loc>{html.escape(site_url)}</loc></url></urlset>\n', encoding="utf-8")
    print(f"Built {data['tag']} website in {DIST}")


if __name__ == "__main__":
    build()
