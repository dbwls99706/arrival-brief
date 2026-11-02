"""Create an anonymous review ZIP and a separate operator handoff checklist.

This prepares files; it never signs consent, invites accounts or sends submissions.
The source digest proves parity between the ZIP and this checkout, not that a binary
was reproduced from source. Build provenance is recorded separately when available.
"""
from argparse import ArgumentParser
from hashlib import sha256
from io import BytesIO
from pathlib import Path
from tempfile import TemporaryDirectory
from urllib.parse import unquote, urlsplit
import json
import os
import re
import subprocess
import sys
import zipfile

ROOT = Path(__file__).resolve().parents[1]
TEXT = {'.md', '.txt', '.py', '.mjs', '.css', '.html', '.svg', '.xml', '.json', '.properties', '.kts', '.kt', '.bat', '.vtt'}
BLOCKED_PARTS = {'.git', '.openai', '.gradle', '.kotlin', 'build', 'node_modules', '__pycache__', '.idea', '.rsync-tmp'}
PACKAGED_APK = 'dist/downloads/ArrivalBrief_release.apk'
IMAGE_SUFFIXES = {'.png', '.jpg', '.jpeg', '.webp'}
MAX_REVIEW_BYTES = 12_200_000


def digest(data):
    return sha256(data).hexdigest()


def is_core(path):
    name = path.as_posix()
    return (name.startswith('android/') and path.name != 'README.md') or name.startswith('dist/arrival/') or name.startswith('tests/')


def allowed(path):
    parts, name = path.parts, path.as_posix()
    if any(p in BLOCKED_PARTS for p in parts) or path.is_absolute() or '..' in parts:
        return False
    if path.name in {'local.properties', '.env'} or any(x in path.name.lower() for x in ('private', 'keystore', 'password', 'debug.apk', 'unsigned')):
        return False
    if len(parts) == 1:
        return path.name in {'README.md', 'ATTRIBUTIONS.md', 'index.html', '.gitignore', 'PACKAGE-CONTENTS.md'}
    if parts[0] == 'android':
        if name.startswith('android/app/src/main/assets/third_party/'):
            return path.name in {'ZXing-LICENSE.txt', 'ZXing-NOTICE.txt'}
        if name.startswith('android/app/src/main/res/'):
            return path.suffix in {'.xml', '.png', '.webp', '.jpg'}
        return path.suffix in {'.kt', '.kts', '.xml', '.properties', '.bat', '.md'} or path.name in {'gradlew', 'gradle-wrapper.jar'}
    if parts[0] == 'apk':
        return path.name in {'ArrivalBrief_release.apk', 'README.md'}
    if parts[0] == 'dist':
        if path.name.lower().startswith('qa-'):
            return False
        return path.suffix in {'.html', '.css', '.mjs', '.svg', '.mp4', '.vtt', '.jpg', '.png', '.webp', '.woff2', '.json'} or name == 'dist/downloads/ArrivalBrief_release.apk'
    if parts[0] == 'tests':
        return path.suffix in {'.mjs', '.tsv'}
    if parts[0] == 'scripts':
        return path.suffix == '.py'
    if parts[0] == 'docs':
        if len(parts) == 2:
            return path.name in {'ANDROID_BUILD.md', 'ARRIVAL_PLAN.md', 'HYUNDAI_FOCUS.md', 'HYUNDAI_VERIFICATION.md', 'USER_EVIDENCE.md', 'SUBMISSION.md', 'design-review.md', 'REVIEW_ROUND2.md', 'PRODUCT_SCOPE.md'} or (path.name.startswith('completion-') and path.suffix in {'.md', '.json', '.py'})
        if len(parts) == 3 and parts[1] == 'verification':
            return (path.name in {'build-inputs.json', 'release-verification.json', 'android-lint.txt', 'android-release-build.log', 'android-release-signature.txt', 'android-release-package.txt', 'android-runtime.md', 'android-v04-screenshots.json', 'brand-icon-generation.json', 'review-cycles.json'}
                    or (path.name.startswith('TEST-') and path.suffix == '.xml')
                    or (path.name.startswith('completion-') and path.suffix in {'.md', '.json', '.py', '.txt', '.log', '.tsv', '.png', '.jpg', '.webp'})
                    or (path.name.startswith(('android-runtime-', 'build-inputs-', 'release-verification-')) and path.suffix == '.json')
                    or (path.name.startswith('web-tests-') and path.suffix == '.log')
                    or (path.name.startswith('web-') and path.suffix == '.json')
                    or (path.name.startswith('android-aosp28-') and path.suffix in {'.png', '.json', '.log', '.txt', '.mp4'})
                    or (path.name.startswith('pleos-') and path.suffix in {'.json', '.log', '.txt', '.png'})
                    or (path.name.startswith(('judging-round-', 'resume-review-')) and path.suffix == '.md')
                    or (path.name.startswith(('arrival-v04-', 'arrival-v05-', 'arrival-rc', 'arrival-resume-')) and path.suffix in {'.jpg', '.png', '.json', '.mp4', '.vtt'}))
        return False
    if parts[0] == 'research':
        return path.name in {'OFL.txt', 'frontend-design-LICENSE.txt', 'frontend-design-SKILL.md', 'design-reference-2026-10-31.md', 'design-reference-2026-11-01.md'}
    return False


def redact(text, account, tokens):
    github = r'https?://(?:www\.)?github\.com/' + re.escape(account) + r'(?:[/#?][^\s<>"\)\]]*)?'
    text = re.sub(r'<a\b[^>]*href=["\x27]' + github + r'["\x27][^>]*>.*?</a>',
                  '<span>소스 코드는 제출 ZIP에 포함되어 있습니다.</span>', text, flags=re.I | re.S)
    text = re.sub(r'\[([^\]]*)\]\(' + github + r'\)', r'\1 (운영용 주소 별도 전달)', text, flags=re.I)
    text = re.sub(github, '[운영용 저장소 주소 별도 전달]', text, flags=re.I)
    text = re.sub(r'\[([^\]]+)\]\((?:\.?\.?/)*submission/ArrivalBrief-operator-checklist\.md\)',
                  r'\1 (운영용 자료로 별도 전달)', text)
    for token in sorted(tokens, key=len, reverse=True):
        text = re.sub(re.escape(token), '[운영용 정보 별도 전달]', text, flags=re.I)
    return text


def private_check(data, tokens):
    # Case-insensitive plain UTF-8 and UTF-16 checks, including embedded APK files.
    haystacks = [data.lower()]
    for token in tokens:
        if any(token.lower().encode(enc) in haystacks[0] for enc in ('utf-8', 'utf-16le')):
            return False
    if data.startswith(b'PK\x03\x04'):
        with zipfile.ZipFile(BytesIO(data)) as z:
            for item in z.infolist():
                if item.file_size > 100 * 1024 * 1024:
                    raise ValueError('Unexpected oversized embedded archive entry')
                if not item.is_dir() and not private_check(z.read(item), tokens):
                    return False
    return True


def verify_document_links(root):
    """The review copy must not link to omitted operating or evidence files."""
    for source in root.rglob('*.md'):
        for value in re.findall(r'\]\(([^\s)]+)\)', source.read_text()):
            url = urlsplit(value)
            if url.scheme or url.netloc or not url.path:
                continue
            target = (source.parent / unquote(url.path)).resolve()
            if not target.is_relative_to(root.resolve()) or not target.exists():
                raise ValueError('Review document references an omitted file: '
                                 + source.relative_to(root).as_posix() + ' -> ' + value)


def release_tag(version):
    match = re.search(r'rc\d+$', version)
    return match.group(0) if match else re.sub(r'[^A-Za-z0-9]+', '-', version)


def relative_evidence_path(value):
    path = Path(value)
    if path.is_absolute() or '..' in path.parts:
        raise ValueError('Unsafe evidence path: ' + value)
    return path if len(path.parts) > 1 else Path('docs/verification') / path


def delivery_plan(version, apk_sha256, root=ROOT):
    """Keep current proof; inventory historical binaries for the outer bundle."""
    tag = release_tag(version)
    runtime_rel = Path(f'docs/verification/android-runtime-{tag}.json')
    runtime = json.loads((root / runtime_rel).read_text())
    if runtime.get('version') != version or runtime.get('release_apk_sha256') != apk_sha256:
        raise ValueError('Current runtime evidence does not identify the release')
    required_images = set()
    for shot in runtime.get('screenshots', []):
        path = relative_evidence_path(shot['path'])
        if digest((root / path).read_bytes()) != shot['sha256']:
            raise ValueError('Current runtime capture hash differs: ' + str(path))
        required_images.add(path.as_posix())
    for prefix in ('arrival', 'arrival-confirm'):
        meta = json.loads((root / f'dist/assets/media/{prefix}-metadata.json').read_text())['content']
        if meta.get('app_version') != version:
            raise ValueError('Web media belongs to another version: ' + prefix)
        for scene in meta['scenes']:
            path = relative_evidence_path(scene['source'])
            if digest((root / path).read_bytes()) != scene['source_sha256']:
                raise ValueError('Web video capture hash differs: ' + str(path))
            required_images.add(path.as_posix())
    native = json.loads((root / 'dist/assets/media/arrival-native-validation.json').read_text())
    if native.get('version') != version or native.get('release_apk_sha256') != apk_sha256:
        raise ValueError('Native media belongs to another release')
    for shot in native['screenshots']:
        path = relative_evidence_path(shot['packaged_screenshot'])
        if digest((root / path).read_bytes()) != shot['sha256']:
            raise ValueError('Native video capture hash differs: ' + str(path))
        required_images.add(path.as_posix())
    for record in (root / 'docs/verification').glob('*.json'):
        if not record.name.startswith(('web-', 'completion-')):
            continue
        data = json.loads(record.read_text())
        if not isinstance(data, dict) or data.get('app_version', data.get('version')) != version:
            continue
        for check in data.get('checks', []):
            if isinstance(check, dict) and check.get('image'):
                required_images.add(relative_evidence_path(check['image']).as_posix())
    for name in required_images:
        if not (root / name).is_file() or not allowed(Path(name)):
            raise ValueError('Current proof capture is missing or excluded: ' + name)
    records = []
    apk_data = (root / PACKAGED_APK).read_bytes()
    canonical = root / 'apk/ArrivalBrief_release.apk'
    if canonical.is_file() and canonical.read_bytes() != apk_data:
        raise ValueError('Canonical and packaged APK copies differ')
    records.append({'original_path': 'apk/ArrivalBrief_release.apk', 'retained_path': PACKAGED_APK,
                    'bytes': len(apk_data), 'sha256': digest(apk_data), 'reason': 'byte_identical_duplicate'})
    for path in sorted((root / 'docs/verification').iterdir()):
        rel = path.relative_to(root)
        if not path.is_file() or not allowed(rel):
            continue
        name = rel.as_posix()
        current_image = (name in required_images or f'-{tag}-' in path.name
                         or path.name.startswith(('completion-', 'pleos-')))
        if path.suffix == '.mp4':
            reason = 'recording_delivered_separately'
        elif path.suffix in IMAGE_SUFFIXES and not current_image:
            reason = 'historical_capture_delivered_separately'
        else:
            continue
        data = path.read_bytes()
        records.append({'original_path': name, 'bytes': len(data), 'sha256': digest(data),
                        'reason': reason, 'outer_zip_path': 'evidence-history/' + name})
    # Frozen media snapshots are historical even when they live beside the
    # current static-page media. The canonical dist files are never changed.
    for path in sorted((root / 'dist/assets/media').iterdir()):
        marker = re.search(r'-(rc\d+)(?:[-.]|$)', path.name)
        rel = path.relative_to(root)
        if not path.is_file() or not marker or marker.group(1) == tag or not allowed(rel):
            continue
        data = path.read_bytes()
        records.append({'original_path': rel.as_posix(), 'bytes': len(data), 'sha256': digest(data),
                        'reason': 'historical_media_delivered_separately',
                        'outer_zip_path': 'evidence-history/' + rel.as_posix()})
    # Runtime records retain original recording identities even though the bytes
    # travel in the outer bundle. Do not silently accept a stale video record.
    for record in (root / 'docs/verification').glob('android-runtime-*.json'):
        video = json.loads(record.read_text()).get('continuous_ui_video')
        if not video:
            continue
        path = relative_evidence_path(video['path'])
        if (root / path).is_file():
            raw = (root / path).read_bytes()
            if digest(raw) != video['sha256'] or len(raw) != video['bytes']:
                raise ValueError('Continuous recording and evidence disagree: ' + str(path))
        elif record == root / runtime_rel:
            raise ValueError('Current continuous recording is missing: ' + str(path))
    prior_manifest = root / 'SUBMISSION-MANIFEST.json'
    if prior_manifest.is_file():
        known = {item['original_path'] for item in records}
        for item in json.loads(prior_manifest.read_text()).get('delivery_omissions', []):
            original = Path(item['original_path'])
            if original.is_absolute() or '..' in original.parts:
                raise ValueError('Unsafe prior delivery path')
            if item.get('outer_zip_path') and item['original_path'] not in known and not (root / original).exists():
                records.append(item)
    return {'version': version, 'tag': tag, 'runtime_record': runtime_rel.as_posix(),
            'required_images': sorted(required_images), 'omissions': records}


def review_layout(text, rel, plan):
    """Adjust delivery-document paths only; app/build source is never rewritten."""
    if is_core(rel) or rel.suffix not in {'.md', '.json', '.html'}:
        return text
    text = text.replace('apk/ArrivalBrief_release.apk', PACKAGED_APK)
    if rel.as_posix() == 'apk/README.md':
        text = text.replace('](ArrivalBrief_release.apk)', '](../' + PACKAGED_APK + ')')
    external = [item for item in plan['omissions'] if item.get('outer_zip_path')]
    referenced = [item for item in external if Path(item['original_path']).name in text]
    if rel.suffix == '.md':
        note = os.path.relpath('PACKAGE-CONTENTS.md', rel.parent.as_posix())
        names = {Path(item['original_path']).name for item in referenced}
        def link(match):
            target = unquote(urlsplit(match.group(3)).path)
            if Path(target).name not in names:
                return match.group(0)
            return '[' + match.group(2) + ' (별도 보존 자료)](' + note + ')'
        text = re.sub(r'(!?)\[([^\]]*)\]\(([^\s)]+)\)', link, text)
    if rel.suffix == '.json' and referenced:
        data = json.loads(text)
        if isinstance(data, dict):
            data['review_delivery'] = {'note': 'Original evidence paths below are preserved in the outer bundle and published repository, not as local files in this review ZIP. See PACKAGE-CONTENTS.md.',
                                       'external_references': referenced}
        if isinstance(data, dict) and data.get('continuous_ui_video'):
            data['continuous_ui_video']['included_in_review_zip'] = False
            data['continuous_ui_video']['delivery'] = 'Preserved separately; see review_delivery and PACKAGE-CONTENTS.md.'
        text = json.dumps(data, ensure_ascii=False, indent=2) + '\n'
    return text


def verify_archive(path, compare_root=None):
    with zipfile.ZipFile(path) as z:
        names = z.namelist()
        if len(names) != len(set(names)) or any(Path(n).is_absolute() or '..' in Path(n).parts for n in names):
            raise ValueError('Unsafe or duplicate ZIP path')
        manifest_name = 'SUBMISSION-MANIFEST.json'
        manifest = json.loads(z.read(manifest_name))
        expected = {x['path']: x for x in manifest['files']}
        if set(names) != set(expected) | {manifest_name}:
            raise ValueError('ZIP inventory mismatch')
        for name, info in expected.items():
            data = z.read(name)
            if len(data) != info['bytes'] or digest(data) != info['sha256']:
                raise ValueError('Artifact hash mismatch: ' + name)
        for info in manifest['source_files']:
            if info['path'] not in expected or not is_core(Path(info['path'])):
                raise ValueError('Source inventory references a non-app or missing file')
            if digest(z.read(info['path'])) != info['sha256']:
                raise ValueError('Packaged app source mismatch: ' + info['path'])
            if compare_root and digest((compare_root / info['path']).read_bytes()) != info['sha256']:
                raise ValueError('Checkout app source has changed: ' + info['path'])
        source_digest = digest(json.dumps(manifest['source_files'], ensure_ascii=False, sort_keys=True, separators=(',', ':')).encode())
        if source_digest != manifest['source_tree_sha256']:
            raise ValueError('Source inventory digest mismatch')
        for apk in manifest.get('apk_files', ['apk/ArrivalBrief_release.apk', PACKAGED_APK]):
            if digest(z.read(apk)) != manifest['apk_sha256']:
                raise ValueError('APK copy mismatch')
        for item in manifest.get('delivery_omissions', []):
            for key in ('original_path', 'retained_path', 'outer_zip_path'):
                if item.get(key):
                    rel = Path(item[key])
                    if rel.is_absolute() or '..' in rel.parts:
                        raise ValueError('Unsafe delivery evidence path')
            if item['original_path'] in expected:
                raise ValueError('Omitted delivery file is unexpectedly present')
            if item.get('retained_path') and digest(z.read(item['retained_path'])) != item['sha256']:
                raise ValueError('Deduplicated evidence bytes differ')
            if compare_root:
                original = compare_root / item['original_path']
                if original.is_file() and (digest(original.read_bytes()) != item['sha256'] or original.stat().st_size != item['bytes']):
                    raise ValueError('Separately delivered original has changed: ' + item['original_path'])
        build = json.loads(z.read('docs/verification/build-inputs.json'))
        if build.get('apk_sha256') != manifest['apk_sha256'] or build.get('version') != manifest['version']:
            raise ValueError('Build provenance and package disagree')
        for item in build['files']:
            if item['path'] not in expected or digest(z.read(item['path'])) != item['sha256']:
                raise ValueError('Packaged build input mismatch: ' + item['path'])
        reviews = json.loads(z.read('docs/verification/review-cycles.json'))
        for review in reviews.get('rounds', []) + reviews.get('completion_reviews', []):
            references = [review['report']]
            evidence = review.get('evidence', [])
            references += [evidence] if isinstance(evidence, str) else evidence
            for value in references:
                path = relative_evidence_path(value).as_posix()
                if path not in expected:
                    raise ValueError('Completed review references missing evidence: ' + path)
    return manifest


def main():
    p = ArgumentParser(description=__doc__)
    p.add_argument('--output', type=Path, help='Output outside the repository')
    p.add_argument('--repository-url', help='Actual operator-facing GitHub repository URL; excluded from review ZIP')
    p.add_argument('--private-token', action='append', default=[], help='Applicant identity/contact literal to exclude; repeat as needed')
    p.add_argument('--verify', type=Path, help='Verify an existing ZIP against the current app source')
    args = p.parse_args()
    if args.verify:
        m = verify_archive(args.verify.resolve(), ROOT)
        print(f"PASS: {len(m['files'])} artifacts, source parity and APK hashes verified.")
        return
    if not args.output or not args.repository_url:
        p.error('--output and --repository-url are required when creating a package')
    u = urlsplit(args.repository_url)
    parts = u.path.strip('/').split('/')
    if u.scheme != 'https' or u.netloc != 'github.com' or len(parts) != 2 or not all(parts):
        p.error('Use the exact https://github.com/owner/repository URL')
    output = args.output.resolve()
    if output.is_relative_to(ROOT):
        p.error('Output must be outside the source repository')
    output.mkdir(parents=True, exist_ok=True)
    account = parts[0]
    tokens = list(dict.fromkeys([account, args.repository_url, *args.private_token]))
    if any(not x.strip() or len(x) < 3 for x in tokens):
        p.error('Private tokens must contain at least three characters')
    apk = ROOT / 'apk/ArrivalBrief_release.apk'
    if not apk.is_file():
        apk = ROOT / PACKAGED_APK
    release = json.loads((ROOT / 'docs/verification/release-verification.json').read_text())
    actual_hash = digest(apk.read_bytes())
    if actual_hash != release['sha256'] or apk.stat().st_size != release['bytes']:
        raise ValueError('Canonical APK and release verification disagree')
    gradle = (ROOT / 'android/app/build.gradle.kts').read_text()
    version = re.search(r'versionName\s*=\s*"([^"]+)"', gradle).group(1)
    if not re.fullmatch(r'[A-Za-z0-9][A-Za-z0-9._+-]*', version):
        raise ValueError('Release version is not safe for an artifact filename')
    if version != release['version']:
        raise ValueError('Source version and release verification disagree; rebuild and update evidence first')
    build = json.loads((ROOT / 'docs/verification/build-inputs.json').read_text())
    if build.get('apk_sha256') != actual_hash or build.get('version') != version or not build.get('source_snapshot_match'):
        raise ValueError('Build provenance does not identify this APK and source snapshot')
    tag = release_tag(version)
    for stem, hash_field in (('build-inputs', 'apk_sha256'), ('release-verification', 'sha256')):
        snapshot = ROOT / f'docs/verification/{stem}-{tag}.json'
        if snapshot.is_file():
            record = json.loads(snapshot.read_text())
            if record.get('version') != version or record.get(hash_field) != actual_hash:
                raise ValueError('Current-version snapshot identifies a different APK: ' + snapshot.name)
    build_paths = [item['path'] for item in build['files']]
    if not build_paths or len(build_paths) != len(set(build_paths)):
        raise ValueError('Build provenance has an empty or duplicate input inventory')
    for item in build['files']:
        rel = Path(item['path'])
        if rel.is_absolute() or '..' in rel.parts or not (ROOT / rel).is_file():
            raise ValueError('Invalid build input path: ' + item['path'])
        if digest((ROOT / rel).read_bytes()) != item['sha256']:
            raise ValueError('Source changed after the recorded APK build: ' + item['path'])
    static_apk = ROOT / 'dist/downloads/ArrivalBrief_release.apk'
    if not static_apk.is_file() or digest(static_apk.read_bytes()) != actual_hash:
        raise ValueError('Static APK copy does not match the verified release; synchronize it before packaging')
    for html in [ROOT / 'dist/arrival-brief/index.html', ROOT / 'dist/arrival-brief/review.html']:
        versions = re.findall(r'<span data-release-version>([^<]+)</span>', html.read_text())
        if not versions or any(value != version for value in versions):
            raise ValueError('Displayed release version is stale: ' + html.relative_to(ROOT).as_posix())
    subprocess.run([sys.executable, str(ROOT / 'scripts/check-paths.py'), '--static-root', 'dist'], check=True)
    plan = delivery_plan(version, actual_hash)
    delivery = plan['omissions']
    omitted = {item['original_path'] for item in delivery}
    selected = [(f.relative_to(ROOT), f) for f in sorted(ROOT.rglob('*')) if f.is_file() and not f.is_symlink()
                and allowed(f.relative_to(ROOT)) and f.relative_to(ROOT).as_posix() not in omitted]
    source = [{'path': rel.as_posix(), 'sha256': digest(f.read_bytes())} for rel, f in selected if is_core(rel)]
    with TemporaryDirectory(prefix='arrival-review-', dir=output) as temp:
        staging = Path(temp)
        transformed = []
        adjusted = []
        for rel, f in selected:
            if any(token.casefold() in rel.as_posix().casefold() for token in tokens):
                raise ValueError('Applicant identity appears in an artifact filename; rename it before packaging')
            raw = f.read_bytes()
            if f.suffix in TEXT or f.name in {'gradlew', '.gitignore'}:
                text = raw.decode('utf-8')
                changed = redact(text, account, tokens).encode('utf-8')
                if changed != raw:
                    if is_core(rel):
                        raise ValueError('Identity present in app source; fix source before packaging: ' + rel.as_posix())
                    transformed.append(rel.as_posix())
                raw = changed
                layout = review_layout(raw.decode('utf-8'), rel, plan).encode('utf-8')
                if layout != raw:
                    adjusted.append(rel.as_posix())
                raw = layout
            if not private_check(raw, tokens):
                raise ValueError('Applicant identity remains in review artifact: ' + rel.as_posix())
            target = staging / rel
            target.parent.mkdir(parents=True, exist_ok=True)
            target.write_bytes(raw)
        notes = ['# 익명 검토 ZIP 구성', '', f'이 전달본은 {version}의 앱 소스·최종 APK·현재 검증 캡처·검증 결과와 설명 영상 3편을 포함합니다. 앱 소스와 빌드 입력은 원본 바이트 그대로입니다. 이전 버전의 캡처·편집 영상 보존본과 연속 원본 녹화는 용량을 위해 전체 운영 ZIP 및 별도 공개 저장소에서 제공합니다.', '',
                 '- APK는 [dist/downloads/ArrivalBrief_release.apk](dist/downloads/ArrivalBrief_release.apk)에 한 번만 들어 있습니다. 정적 서버 루트는 기존대로 `dist`를 사용합니다.',
                 '- 현재 영상과 런타임 메타데이터가 직접 참조하는 캡처는 모두 포함했습니다. 이전 캡처·원본 녹화의 경로·크기·해시는 아래에 보존했습니다. 전체 운영 ZIP의 `evidence-history/` 아래에서 같은 파일을 찾을 수 있습니다.',
                 '- 별도 보존 자료를 가리키는 문서 링크는 이 구성 안내로 연결했습니다. JSON에는 원래 경로를 유지하고 `review_delivery`에 별도 제공 위치와 해시를 명시했습니다. 앱 구현·APK·캡처 이미지 내용은 변경하지 않았습니다.', '', '## 중복 정리 및 별도 제공 기록', '']
        notes += [f"- `{x['original_path']}` → `{x.get('retained_path', x.get('outer_zip_path'))}` · {x['bytes']} bytes · SHA-256 `{x['sha256']}`" for x in delivery]
        (staging / 'PACKAGE-CONTENTS.md').write_text('\n'.join(notes) + '\n')
        verify_document_links(staging)
        source_digest = digest(json.dumps(source, ensure_ascii=False, sort_keys=True, separators=(',', ':')).encode())
        files = [{'path': f.relative_to(staging).as_posix(), 'bytes': f.stat().st_size, 'sha256': digest(f.read_bytes())} for f in sorted(staging.rglob('*')) if f.is_file()]
        manifest = {'schema_version': 3, 'app': 'Arrival Brief', 'version': version, 'apk_sha256': actual_hash, 'apk_files': [PACKAGED_APK],
                    'source_tree_sha256': source_digest, 'source_files': source, 'files': files,
                    'anonymized_documents': transformed,
                    'delivery_adjusted_documents': adjusted, 'delivery_omissions': delivery,
                    'current_runtime_record': plan['runtime_record'], 'required_capture_files': plan['required_images'],
                    'evidence_boundary': 'Source digest verifies checkout/package parity; it is not a claim of byte-reproducible APK build or successful Pleos execution.',
                    'operator_checks': ['Signed official application delivered separately', 'Public repository and APK access', 'Organizer instructions for applicant-identifying repository links', 'Attachment names and media content'],
                    'not_completed_by_packaging': ['Email submission', 'Pleos vehicle runtime verification']}
        (staging / 'SUBMISSION-MANIFEST.json').write_text(json.dumps(manifest, ensure_ascii=False, indent=2) + '\n')
        subprocess.run([sys.executable, str(ROOT / 'scripts/check-paths.py'), '--root', str(staging), '--static-root', 'dist'], check=True)
        zip_path = output / ('ArrivalBrief-review-' + version + '.zip')
        candidate = output / ('ArrivalBrief-review-' + version + '.pending.zip')
        with zipfile.ZipFile(candidate, 'w', zipfile.ZIP_DEFLATED) as z:
            for f in sorted(staging.rglob('*')):
                if f.is_file():
                    rel = f.relative_to(staging).as_posix()
                    zi = zipfile.ZipInfo(rel, (2026, 1, 1, 0, 0, 0)); zi.compress_type = zipfile.ZIP_DEFLATED
                    zi.external_attr = (0o100755 if rel == 'android/gradlew' else 0o100644) << 16
                    z.writestr(zi, f.read_bytes(), compresslevel=9)
        verify_archive(candidate, ROOT)
        if candidate.stat().st_size > MAX_REVIEW_BYTES:
            raise ValueError('Review ZIP exceeds the 12,200,000-byte delivery budget')
        candidate.replace(zip_path)
        zip_hash = digest(zip_path.read_bytes())
    checklist = output / 'ArrivalBrief-operator-checklist.md'
    checklist.write_text(f'''# Arrival Brief 운영용 제출 목록\n\n이 문서는 운영 전달용이며 익명 심사용 ZIP에 포함하지 않습니다.\n\n- 저장소: {args.repository_url}\n- 저장소 APK 경로: apk/ArrivalBrief_release.apk\n- 익명 ZIP 내부 APK 경로: dist/downloads/ArrivalBrief_release.apk (동일 APK 1개만 포함)\n- 이전 버전 캡처와 연속 원본 녹화는 익명 ZIP에서 분리해 전체 운영 ZIP의 evidence-history/ 및 공개 저장소에 보존합니다. 대상 경로·해시와 문서 링크 조정은 익명 ZIP의 PACKAGE-CONTENTS.md 및 manifest에 기록했습니다.\n- APK 버전: {version}\n- APK SHA-256: {actual_hash}\n- 익명 검토 ZIP: {zip_path.name}\n- ZIP SHA-256: {zip_hash}\n- 앱 소스 목록 SHA-256: {source_digest}\n- 영상: dist/assets/media/arrival-reel.mp4, arrival-confirm-reel.mp4, arrival-native-validation.mp4\n- 앞의 두 영상은 {version} 실제 웹 화면 편집본입니다. 마지막 영상은 같은 {version} main 소스의 일반 Android debug 자동 UI 시험 실제 캡처 편집본입니다. 모두 연속 실차 녹화나 Pleos SDK 실제 응답 영상이 아닙니다.\n\n## 본인 또는 운영 확인이 필요한 항목\n\n- [ ] 별도 공식 접수양식의 동의 여부·본인 서명·서명일자 확인\n- [ ] 공식 파일명 형식과 익명 심사 지침에 맞는 APK 제출명 확인\n- [ ] 공개 저장소의 소스·APK 접근 확인(비공개 전환 시 심사용 계정 접근 확인)\n- [ ] 개인 계정이 표시되는 GitHub 주소의 익명 전달 방식 확인\n- [ ] 신청서 7번 첨부 파일명과 실제 첨부물 일치 확인\n- [ ] 화면·영상에 개인정보가 없는지 육안 확인\n- [ ] 지원 환경에서 실제 Pleos 설치·SDK·UX 제한 재검증\n\nAPK 단독 제출을 대신하는 ZIP이 아닙니다. 공식 안내는 GitHub에 소스와 APK를 함께 제출하도록 하며, 이 ZIP만으로 접근·익명 요건이 해결됐다고 간주하지 않습니다. 서명키 백업·연락처·동의서는 리뷰 ZIP에 넣지 않았습니다. 이메일은 발송하지 않았습니다.\n''')
    print(json.dumps({'review_zip': str(zip_path), 'operator_checklist': str(checklist), 'version': version, 'files': len(files), 'source_tree_sha256': source_digest, 'apk_sha256': actual_hash, 'zip_sha256': zip_hash, 'zip_bytes': zip_path.stat().st_size, 'runtime_record': plan['runtime_record'], 'separately_delivered_files': sum(bool(x.get('outer_zip_path')) for x in delivery), 'status': 'prepared_not_submitted'}, ensure_ascii=False))


if __name__ == '__main__':
    main()
