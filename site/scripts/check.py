"""Check the generated site without requiring a browser or third-party packages."""
from html.parser import HTMLParser
from pathlib import Path
from urllib.parse import unquote, urlsplit
import re

ROOT = Path(__file__).resolve().parents[1] / "dist"


class Page(HTMLParser):
    def __init__(self):
        super().__init__()
        self.ids = set()
        self.refs = []
        self.h1 = 0

    def handle_starttag(self, tag, attrs):
        attrs = dict(attrs)
        if "id" in attrs:
            assert attrs["id"] not in self.ids, f"Duplicate ID {attrs['id']}"
            self.ids.add(attrs["id"])
        if tag == "h1":
            self.h1 += 1
        for attr in ["href", "src", "data-zoom"]:
            if attr in attrs:
                self.refs.append(attrs[attr])
        if attrs.get("target") == "_blank":
            assert "noopener" in attrs.get("rel", ""), "External window lacks noopener"


def check():
    text = (ROOT / "index.html").read_text(encoding="utf-8")
    assert "{{" not in text and "<html lang=\"zh-CN\">" in text
    page = Page()
    page.feed(text)
    assert page.h1 == 1
    checked = 0
    for ref in page.refs:
        url = urlsplit(ref)
        if url.scheme:
            assert url.scheme == "https", f"Unexpected URL {ref}"
            continue
        if not url.path:
            assert not url.fragment or url.fragment in page.ids, f"Missing anchor {ref}"
        else:
            target = (ROOT / unquote(url.path)).resolve()
            assert target.is_relative_to(ROOT.resolve()) and target.is_file(), f"Missing local resource {ref}"
        checked += 1
    css = (ROOT / "styles/site.css").read_text(encoding="utf-8")
    for ref in re.findall(r'url\(["\']?([^\)"\']+)', css):
        target = (ROOT / "styles" / ref).resolve()
        assert target.is_relative_to(ROOT.resolve()) and target.is_file(), f"Missing CSS asset {ref}"
        checked += 1
    for name in ["home", "chat", "terminal", "panel", "voice"]:
        assert (ROOT / f"assets/screens/{name}.png").is_file()
    assert (ROOT / "assets/fonts/aos-sans-sc.woff2").read_bytes().startswith(b"wOF2")
    assert not (ROOT / "design").exists()
    assert not (ROOT / "scripts/build.py").exists()
    assert not list(ROOT.rglob("*.apk")) and not list(ROOT.rglob("*.jks"))
    print(f"PASS: {checked} local references, accessible document structure and deploy-artifact boundary")


if __name__ == "__main__":
    check()
