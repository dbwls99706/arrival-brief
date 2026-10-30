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
from urllib.parse import urlsplit
import json
import re
import shutil
import subprocess
import sys
import zipfile

ROOT = Path(__file__).resolve().parents[1]
TEXT = {'.md', '.txt', '.py', '.mjs', '.css', '.html', '.svg', '.xml', '.json', '.properties', '.kts', '.kt', '.bat', '.vtt'}
BLOCKED_PARTS = {'.git', '.openai', '.gradle', 'build', 'node_modules', '__pycache__', '.idea'}


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
        return path.name in {'README.md', 'ATTRIBUTIONS.md', 'index.html', '.gitignore'}
    if parts[0] == 'android':
        return path.suffix in {'.kt', '.kts', '.xml', '.properties', '.bat', '.md'} or path.name in {'gradlew', 'gradle-wrapper.jar'}
    if parts[0] == 'apk':
        return path.name in {'ArrivalBrief_release.apk', 'README.md'}
    if parts[0] == 'dist':
        if path.name.lower().startswith('qa-'):
            return False
        return path.suffix in {'.html', '.css', '.mjs', '.svg', '.mp4', '.vtt', '.jpg', '.png', '.json'} or name == 'dist/downloads/ArrivalBrief_release.apk'
    if parts[0] == 'tests':
        return path.suffix in {'.mjs', '.tsv'}
    if parts[0] == 'scripts':
        return path.suffix == '.py'
    if parts[0] == 'docs':
        if len(parts) == 2:
            return path.name in {'ANDROID_BUILD.md', 'ARRIVAL_PLAN.md', 'HYUNDAI_FOCUS.md', 'HYUNDAI_VERIFICATION.md', 'USER_EVIDENCE.md', 'SUBMISSION.md'}
        if len(parts) == 3 and parts[1] == 'verification':
            return (path.name in {'build-inputs.json', 'release-verification.json', 'android-lint.txt', 'android-release-build.log', 'android-release-signature.txt', 'android-release-package.txt', 'android-runtime.md'}
                    or (path.name.startswith('TEST-') and path.suffix == '.xml')
                    or (path.name.startswith('android-aosp28-') and path.suffix == '.png'))
        return False
    if parts[0] == 'research':
        return path.name in {'OFL.txt', 'frontend-design-LICENSE.txt', 'frontend-design-SKILL.md'}
    return False


def redact(text, account, tokens):
    github = r'https?://(?:www\.)?github\.com/' + re.escape(account) + r'(?:[/#?][^\s<>"\)\]]*)?'
    text = re.sub(r'<a\b[^>]*href=["\x27]' + github + r'["\x27][^>]*>.*?</a>',
                  '<span>소스 코드는 제출 ZIP에 포함되어 있습니다.</span>', text, flags=re.I | re.S)
    text = re.sub(r'\[([^\]]*)\]\(' + github + r'\)', r'\1 (운영용 주소 별도 전달)', text, flags=re.I)
    text = re.sub(github, '[운영용 저장소 주소 별도 전달]', text, flags=re.I)
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
        for apk in ['apk/ArrivalBrief_release.apk', 'dist/downloads/ArrivalBrief_release.apk']:
            if digest(z.read(apk)) != manifest['apk_sha256']:
                raise ValueError('APK copy mismatch')
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
    release = json.loads((ROOT / 'docs/verification/release-verification.json').read_text())
    actual_hash = digest(apk.read_bytes())
    if actual_hash != release['sha256'] or apk.stat().st_size != release['bytes']:
        raise ValueError('Canonical APK and release verification disagree')
    gradle = (ROOT / 'android/app/build.gradle.kts').read_text()
    version = re.search(r'versionName\s*=\s*"([^"]+)"', gradle).group(1)
    if version != release['version']:
        raise ValueError('Source version and release verification disagree; rebuild and update evidence first')
    static_apk = ROOT / 'dist/downloads/ArrivalBrief_release.apk'
    static_apk.parent.mkdir(parents=True, exist_ok=True)
    shutil.copy2(apk, static_apk)
    for html in [ROOT / 'dist/arrival-brief/index.html', ROOT / 'dist/arrival-brief/review.html']:
        text = re.sub(r'(<span data-release-version>)[^<]+(</span>)', lambda m: m[1] + version + m[2], html.read_text())
        html.write_text(text)
    subprocess.run([sys.executable, str(ROOT / 'scripts/check-paths.py'), '--static-root', 'dist'], check=True)
    selected = [(f.relative_to(ROOT), f) for f in sorted(ROOT.rglob('*')) if f.is_file() and not f.is_symlink() and allowed(f.relative_to(ROOT))]
    source = [{'path': rel.as_posix(), 'sha256': digest(f.read_bytes())} for rel, f in selected if is_core(rel)]
    with TemporaryDirectory(prefix='arrival-review-', dir=output) as temp:
        staging = Path(temp)
        transformed = []
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
            if not private_check(raw, tokens):
                raise ValueError('Applicant identity remains in review artifact: ' + rel.as_posix())
            target = staging / rel
            target.parent.mkdir(parents=True, exist_ok=True)
            target.write_bytes(raw)
        source_digest = digest(json.dumps(source, ensure_ascii=False, sort_keys=True, separators=(',', ':')).encode())
        files = [{'path': f.relative_to(staging).as_posix(), 'bytes': f.stat().st_size, 'sha256': digest(f.read_bytes())} for f in sorted(staging.rglob('*')) if f.is_file()]
        manifest = {'schema_version': 1, 'app': 'Arrival Brief', 'version': version, 'apk_sha256': actual_hash,
                    'source_tree_sha256': source_digest, 'source_files': source, 'files': files,
                    'anonymized_documents': transformed,
                    'evidence_boundary': 'Source digest verifies checkout/package parity; it is not a claim of byte-reproducible APK build or successful Pleos execution.',
                    'manual_review_remaining': ['Applicant consent/signature/date', 'Official judging account access', 'Organizer-approved anonymous GitHub delivery', 'Visual review of screenshot/video content']}
        (staging / 'SUBMISSION-MANIFEST.json').write_text(json.dumps(manifest, ensure_ascii=False, indent=2) + '\n')
        subprocess.run([sys.executable, str(ROOT / 'scripts/check-paths.py'), '--root', str(staging), '--static-root', 'dist'], check=True)
        zip_path = output / ('ArrivalBrief-review-' + version + '.zip')
        with zipfile.ZipFile(zip_path, 'w', zipfile.ZIP_DEFLATED) as z:
            for f in sorted(staging.rglob('*')):
                if f.is_file():
                    rel = f.relative_to(staging).as_posix()
                    zi = zipfile.ZipInfo(rel, (2026, 1, 1, 0, 0, 0)); zi.compress_type = zipfile.ZIP_DEFLATED
                    zi.external_attr = (0o100755 if rel == 'android/gradlew' else 0o100644) << 16
                    z.writestr(zi, f.read_bytes())
        verify_archive(zip_path, ROOT)
        zip_hash = digest(zip_path.read_bytes())
    checklist = output / 'ArrivalBrief-operator-checklist.md'
    checklist.write_text(f'''# Arrival Brief 운영용 제출 목록\n\n이 문서는 운영 전달용이며 익명 심사용 ZIP에 포함하지 않습니다.\n\n- 저장소: {args.repository_url}\n- APK 경로: apk/ArrivalBrief_release.apk\n- APK 버전: {version}\n- APK SHA-256: {actual_hash}\n- 익명 검토 ZIP: {zip_path.name}\n- ZIP SHA-256: {zip_hash}\n- 앱 소스 목록 SHA-256: {source_digest}\n- 영상: dist/assets/media/arrival-reel.mp4, arrival-confirm-reel.mp4\n- 영상은 0.3.0 시점 웹 캡처 편집본이며 차량 SDK의 실제 응답을 촬영한 영상은 아닙니다.\n\n## 본인 또는 운영 확인이 필요한 항목\n\n- [ ] 공식 접수양식의 동의 여부·본인 서명·서명일자 작성\n- [ ] 공식 파일명 형식과 익명 심사 지침에 맞는 APK 제출명 확인\n- [ ] 공지된 심사용 GitHub 계정 및 비공개 저장소 접근 확인\n- [ ] 개인 계정이 표시되는 GitHub 주소의 익명 전달 방식 확인\n- [ ] 신청서 7번 첨부 파일명과 실제 첨부물 일치 확인\n- [ ] 화면·영상에 개인정보가 없는지 육안 확인\n- [ ] 지원 환경에서 실제 Pleos 설치·SDK·UX 제한 재검증\n\nAPK 단독 제출을 대신하는 ZIP이 아닙니다. 공식 안내는 GitHub에 소스와 APK를 함께 제출하도록 하며, 이 ZIP만으로 접근·익명 요건이 해결됐다고 간주하지 않습니다. 서명키 백업·연락처·동의서는 리뷰 ZIP에 넣지 않았습니다. 이메일은 발송하지 않았습니다.\n''')
    print(json.dumps({'review_zip': str(zip_path), 'operator_checklist': str(checklist), 'version': version, 'files': len(files), 'source_tree_sha256': source_digest, 'apk_sha256': actual_hash, 'zip_sha256': zip_hash, 'status': 'prepared_not_submitted'}, ensure_ascii=False))


if __name__ == '__main__':
    main()
