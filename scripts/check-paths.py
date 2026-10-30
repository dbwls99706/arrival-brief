"""Validate a repository or the actual static hosting root without leaving it."""
from argparse import ArgumentParser
from html.parser import HTMLParser
from pathlib import Path
from urllib.parse import unquote, urlsplit
import re

parser = ArgumentParser(description=__doc__)
parser.add_argument('--root', type=Path, default=Path(__file__).resolve().parents[1], help='Repository or extracted package root')
parser.add_argument('--static-root', type=Path, help='Hosting root, relative to --root; rejects links escaping this directory')
args = parser.parse_args()
repository = args.root.resolve()
root = ((repository / args.static_root).resolve() if args.static_root else repository)
errors, checked = [], 0


def check(source, value, fragment_required=False):
    global checked
    url = urlsplit(value)
    if url.scheme or url.netloc or '${' in value:
        return
    if not url.path:
        target = source
    else:
        target = ((root / unquote(url.path).lstrip('/')) if url.path.startswith('/') else source.parent / unquote(url.path)).resolve()
    checked += 1
    if not target.is_relative_to(root):
        errors.append(f'{source.relative_to(root)}: outside server root: {value}')
        return
    if not target.exists():
        errors.append(f'{source.relative_to(root)}: missing {value}')
        return
    if target.is_dir():
        target /= 'index.html'
        if not target.is_file():
            errors.append(f'{source.relative_to(root)}: no index.html: {value}')
            return
    if url.fragment and (fragment_required or target.suffix == '.html'):
        text = target.read_text(encoding='utf-8')
        if not re.search(r'\bid=["\x27]' + re.escape(url.fragment) + r'["\x27]', text):
            errors.append(f'{source.relative_to(root)}: missing fragment {value}')


class Assets(HTMLParser):
    def __init__(self, source):
        super().__init__()
        self.source = source

    def handle_starttag(self, tag, attrs):
        for name, value in attrs:
            if name in ('href', 'src', 'poster') and value:
                check(self.source, value, tag == 'use')


if not root.is_dir():
    raise SystemExit('Server root does not exist')
for source in root.rglob('*.html'):
    if any(p in ('build', '.gradle', 'node_modules') for p in source.relative_to(root).parts):
        continue
    Assets(source).feed(source.read_text(encoding='utf-8'))
asset_root = root if args.static_root else root / 'dist'
for source in asset_root.rglob('*'):
    if source.suffix not in ('.css', '.mjs'):
        continue
    text = source.read_text(encoding='utf-8')
    values = (re.findall(r'url\(\s*["\x27]?([^\s\)"\x27]+)', text) if source.suffix == '.css'
              else re.findall(r'(?:from\s*|import\s*)["\x27]([^"\x27]+)["\x27]', text))
    for value in values:
        check(source, value)
if errors:
    raise SystemExit('\n'.join(errors))
print(f'PASS: {checked} local references resolve inside server root {root.name}.')
