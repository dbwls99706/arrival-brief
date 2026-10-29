"""Validate local HTML assets, SVG symbols, CSS URLs and JS imports."""
from html.parser import HTMLParser
from pathlib import Path
from urllib.parse import unquote, urlsplit
import re

ROOT = Path(__file__).resolve().parents[1]
errors = []
checked = 0


def check(source, value, fragment_required=False):
    global checked
    url = urlsplit(value)
    if url.scheme or url.netloc or not url.path or "${" in value:
        return
    target = ((ROOT / url.path.lstrip("/")) if url.path.startswith("/")
              else source.parent / unquote(url.path)).resolve()
    checked += 1
    if not target.is_relative_to(ROOT):
        errors.append(f"{source.relative_to(ROOT)}: outside repository: {value}")
    elif not target.exists():
        errors.append(f"{source.relative_to(ROOT)}: missing {value}")
    elif target.is_dir() and not (target / "index.html").exists():
        errors.append(f"{source.relative_to(ROOT)}: no index.html: {value}")
    elif fragment_required and url.fragment and target.is_file():
        text = target.read_text(encoding="utf-8")
        if not re.search(r'\bid=["\x27]' + re.escape(url.fragment) + r'["\x27]', text):
            errors.append(f"{source.relative_to(ROOT)}: missing symbol {value}")


class Assets(HTMLParser):
    def __init__(self, source):
        super().__init__()
        self.source = source

    def handle_starttag(self, tag, attrs):
        for name, value in attrs:
            if name in ("href", "src", "poster") and value:
                check(self.source, value, tag == "use")


for source in ROOT.rglob("*.html"):
    Assets(source).feed(source.read_text(encoding="utf-8"))
for source in (ROOT / "dist").rglob("*"):
    if source.suffix not in (".css", ".mjs"):
        continue
    text = source.read_text(encoding="utf-8")
    if source.suffix == ".css":
        values = re.findall(r'url\(\s*["\x27]?([^\s\)"\x27]+)', text)
    else:
        values = re.findall(r'(?:from\s*|import\s*)["\x27]([^"\x27]+)["\x27]', text)
    for value in values:
        check(source, value)

if errors:
    raise SystemExit("\n".join(errors))
print(f"PASS: {checked} local references resolve inside this repository.")
