"""Create an anonymous review ZIP and a separate operator handoff checklist.

This prepares files; it never signs consent, invites accounts or sends submissions.
The source digest proves parity between the ZIP and this checkout, not that a binary
was reproduced from source. Build provenance is recorded separately when available.
"""
from argparse import ArgumentParser
from datetime import datetime, timedelta, timezone
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
TEXT = {'.md', '.txt', '.py', '.mjs', '.css', '.html', '.svg', '.xml', '.json', '.properties', '.kts', '.kt', '.bat', '.vtt', '.csv'}
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
        return path.suffix in {'.mjs', '.tsv', '.json'}
    if parts[0] == 'scripts':
        return path.suffix in {'.py', '.mjs'}
    if parts[0] == 'examples':
        return len(parts) >= 3 and parts[1] == 'organizer' and path.suffix in {'.md', '.txt', '.json'}
    if parts[0] == 'screenshots':
        return path.suffix in IMAGE_SUFFIXES | {'.md', '.csv', '.json', '.pdf', '.zip'}
    if parts[0] == 'docs':
        if len(parts) == 2:
            return path.name in {'ANDROID_BUILD.md', 'ARRIVAL_PLAN.md', 'HYUNDAI_FOCUS.md', 'HYUNDAI_VERIFICATION.md', 'USER_EVIDENCE.md', 'BUSINESS_VALIDATION.md', 'REVIEW_START.md', 'SUBMISSION.md', 'design-review.md', 'REVIEW_ROUND2.md', 'PRODUCT_SCOPE.md'} or (path.name.startswith('completion-') and path.suffix in {'.md', '.json', '.py'})
        if len(parts) == 3 and parts[1] == 'verification':
            return (path.name in {'build-inputs.json', 'release-verification.json', 'android-lint.txt', 'android-release-build.log', 'android-release-signature.txt', 'android-release-package.txt', 'android-runtime.md', 'android-v04-screenshots.json', 'brand-icon-generation.json', 'review-cycles.json'}
                    or (path.name.startswith('TEST-') and path.suffix == '.xml')
                    or (path.name.startswith('completion-') and path.suffix in {'.md', '.json', '.py', '.txt', '.log', '.tsv', '.png', '.jpg', '.webp'})
                    or (path.name.startswith(('android-runtime-', 'build-inputs-', 'release-verification-')) and path.suffix == '.json')
                    or (path.name.startswith('web-tests-') and path.suffix == '.log')
                    or (path.name.startswith('web-') and path.suffix == '.json')
                    or (path.name.startswith(('award-scenarios', 'award-independent-review', 'organizer-examples', 'rc3-', 'rc4-', 'qr-display-size-probe-')) and path.suffix in {'.md', '.json', '.txt', '.log'})
                    or (path.name.startswith('android-aosp28-') and path.suffix in {'.png', '.json', '.log', '.txt', '.mp4', '.xml'})
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


def _validate_rc3_qr(version, apk_sha256, runtime, root=ROOT):
    tag = release_tag(version)
    qr_path = Path(f'docs/verification/completion-qr-{tag}.json')
    qr = json.loads((root / qr_path).read_text())
    if qr.get('version') != version or qr.get('release_apk_sha256') != apk_sha256:
        raise ValueError('Current QR report identifies another release')
    records = qr.get('screenshots', [])
    labels = [shot.get('label') for shot in records]
    if len(labels) != len(set(labels)) or any(label not in ('standard', 'large_font_1_3', 'release_journey') for label in labels):
        raise ValueError('Current QR capture labels are duplicated or unknown')
    expected = {'standard': f'docs/verification/android-aosp28-{tag}-qr.png',
                'large_font_1_3': f'docs/verification/android-aosp28-{tag}-large-qr.png'}
    captured = {relative_evidence_path(shot['path']).as_posix(): shot['sha256']
                for shot in runtime.get('screenshots', [])}
    debug_sha = runtime.get('aosp', {}).get('debug_apk_sha256')
    if not debug_sha or any(label not in labels for label in expected):
        raise ValueError('Current standard/large QR capture identities are incomplete')
    passed = 0
    release_passed = 0
    incorrect_count = 0
    for shot in records:
        path = relative_evidence_path(shot['path'])
        if digest((root / path).read_bytes()) != shot['sha256']:
            raise ValueError('Current QR capture hash differs: ' + str(path))
        if shot['label'] in expected:
            if (path.as_posix() != expected[shot['label']] or captured.get(path.as_posix()) != shot['sha256']
                    or shot.get('build_type') != 'debug' or shot.get('apk_sha256') != debug_sha):
                raise ValueError('Current QR capture does not match its runtime/debug APK: ' + shot['label'])
        elif (shot.get('apk_sha256') != apk_sha256 or shot.get('build_type') != 'release'
              or path.as_posix() != f'docs/verification/android-aosp28-{tag}-release-scenario-qr.png'
              or captured.get(path.as_posix()) != shot['sha256']):
            raise ValueError('Release QR capture does not match the current runtime/APK')
        decoded = shot.get('decoded', {})
        matches = shot.get('matches_current_payload', {})
        incorrect = shot.get('incorrect_nonempty', {})
        for decoder in ('opencv', 'jsqr'):
            value, match = decoded.get(decoder), matches.get(decoder)
            if decoder not in decoded or (value is not None and not isinstance(value, str)) or type(match) is not bool:
                raise ValueError('QR decoder observation is missing: ' + decoder)
            if (match and not value) or incorrect.get(decoder) is not (bool(value) and not match):
                raise ValueError('QR decoder observation and classification disagree: ' + decoder)
            if shot['label'] in expected:
                passed += int(match)
            else:
                release_passed += int(match)
            incorrect_count += int(incorrect[decoder])
        agree = bool(decoded['opencv']) and decoded['opencv'] == decoded['jsqr']
        if shot.get('both_decoders_agree') is not agree:
            raise ValueError('QR decoder agreement flag differs from recorded strings')
        if ((all(matches[decoder] for decoder in ('opencv', 'jsqr')) and not agree)
                or (agree and matches['opencv'] != matches['jsqr'])):
            raise ValueError('QR payload-match flags contradict the recorded decoder strings')
    summary = qr.get('summary', {})
    if summary.get('standard_large_comparisons') != 4 or summary.get('standard_large_pass') != passed:
        raise ValueError('Current QR summary differs from individual decoder observations')
    if 'release_journey' in labels:
        if (summary.get('release_comparisons') != 2 or summary.get('release_pass') != release_passed
                or summary.get('all_comparisons') != 6 or summary.get('all_pass') != passed + release_passed
                or summary.get('incorrect_nonempty_decodes') != incorrect_count):
            raise ValueError('Release/total QR summary differs from individual decoder observations')
    return qr_path.as_posix(), qr


def validate_current_qr(version, apk_sha256, runtime, root=ROOT):
    if release_tag(version) == 'rc3':
        return _validate_rc3_qr(version, apk_sha256, runtime, root)
    qr_path = Path(f'docs/verification/completion-qr-{release_tag(version)}.json')
    qr = json.loads((root / qr_path).read_text())
    if qr.get('version') != version or qr.get('release_apk_sha256') != apk_sha256:
        raise ValueError('Current QR report identifies another release')
    records = qr.get('screenshots', [])
    required = {(build, font, mode) for build in ('debug', 'release')
                for font in (1.0, 1.3) for mode in ('standard', 'expanded')}
    combinations = [(shot.get('build_type'), shot.get('font_scale'), shot.get('display_mode')) for shot in records]
    labels = [shot.get('label') for shot in records]
    paths = [shot.get('path') for shot in records]
    if (len(records) != 8 or set(combinations) != required or len(set(labels)) != 8
            or len(set(paths)) != 8 or any(not isinstance(label, str) or not label for label in labels)
            or any(type(shot.get('font_scale')) not in (int, float) for shot in records)
            or any(not isinstance(path, str) or not path for path in paths)):
        raise ValueError('Current QR captures must cover each build/font/display combination exactly once')
    captured = {relative_evidence_path(shot['path']).as_posix(): shot for shot in runtime.get('screenshots', [])}
    identities = {'debug': runtime.get('aosp', {}).get('debug_apk_sha256'), 'release': apk_sha256}
    counts = {'debug': 0, 'release': 0}
    incorrect_count = 0
    for shot in records:
        path = relative_evidence_path(shot['path'])
        observation = captured.get(path.as_posix(), {})
        expected_apk = identities[shot['build_type']]
        if (not expected_apk or shot.get('apk_sha256') != expected_apk
                or digest((root / path).read_bytes()) != shot['sha256']
                or any(observation.get(field) != shot.get(field)
                       for field in ('sha256', 'build_type', 'apk_sha256', 'font_scale', 'display_mode'))):
            raise ValueError('Current QR capture differs from its runtime/build identity: ' + str(path))
        memo = shot.get('expected_memo')
        window = shot.get('saved_at_window_ms', {})
        before, after = window.get('before'), window.get('after')
        if (memo != 'B3 블루존 E12' or type(before) is not int or type(after) is not int
                or before < 0 or after < before or after - before > 120000
                or observation.get('saved_at_window_ms') != window):
            raise ValueError('Current QR capture has no matching bounded device-time save oracle')
        decoded, matches, incorrect = (shot.get(field, {}) for field in
                                       ('decoded', 'matches_current_payload', 'incorrect_nonempty'))
        for decoder in ('opencv', 'jsqr'):
            value, match = decoded.get(decoder), matches.get(decoder)
            if decoder not in decoded or (value is not None and not isinstance(value, str)) or type(match) is not bool:
                raise ValueError('QR decoder observation is missing: ' + decoder)
            payload_match = False
            if value:
                parts = value.split('\n')
                if (len(parts) == 4 and parts[0] == 'Arrival Brief' and parts[1] == '주차 메모: ' + memo
                        and parts[3] == '휴대폰 복사본은 자동 삭제되지 않음.'):
                    try:
                        saved = datetime.strptime(parts[2], '%Y-%m-%d %H:%M:%S KST').replace(tzinfo=timezone(timedelta(hours=9)))
                        stamp = int(saved.timestamp()) * 1000
                        payload_match = (saved.strftime('%Y-%m-%d %H:%M:%S KST') == parts[2]
                                         and before // 1000 * 1000 <= stamp <= after // 1000 * 1000)
                    except (ValueError, OverflowError):
                        pass
            if match is not payload_match or incorrect.get(decoder) is not (bool(value) and not payload_match):
                raise ValueError('QR classification differs from the memo and independent device-time oracle')
            counts[shot['build_type']] += int(match)
            incorrect_count += int(incorrect[decoder])
        agree = bool(decoded['opencv']) and decoded['opencv'] == decoded['jsqr']
        if shot.get('both_decoders_agree') is not agree:
            raise ValueError('QR decoder agreement flag differs from recorded strings')
    summary = qr.get('summary', {})
    expected_summary = {'standard_large_comparisons': 8, 'standard_large_pass': counts['debug'],
                        'release_comparisons': 8, 'release_pass': counts['release'],
                        'all_comparisons': 16, 'all_pass': sum(counts.values()),
                        'incorrect_nonempty_decodes': incorrect_count}
    if any(type(summary.get(key)) is not int or summary.get(key) != value for key, value in expected_summary.items()):
        raise ValueError('Current QR summary differs from individual decoder observations')
    return qr_path.as_posix(), qr


def validate_media_evidence(version, apk_sha256, root=ROOT):
    """Validate each video against its own release, never relabel prior footage."""
    records, images, json_records, archives = [], set(), set(), {}
    separate_media = []
    reframe_path = 'dist/assets/media/web-reframe.json'
    reframes = {}
    if (root / reframe_path).is_file():
        reframe = json.loads((root / reframe_path).read_text())
        entries = reframe.get('videos', [])
        if (reframe.get('schema_version') != 1
                or reframe.get('kind') != 'reframed_historical_web_stills'
                or reframe.get('app_version') != '1.0.0-rc3'
                or reframe.get('scope') != 'Layout-only re-edit of original RC3 web screenshots; not a new app execution or continuous recording'
                or len(entries) != 2
                or {item.get('prefix') for item in entries} != {'arrival', 'arrival-confirm'}):
            raise ValueError('Invalid historical web reframe manifest')
        reframes = {item['prefix']: item for item in entries}
        json_records.add(reframe_path)
    source_fields = {'source_engine_sha256': 'dist/arrival/engine.mjs',
                     'source_app_sha256': 'dist/arrival/app.mjs', 'source_html_sha256': 'dist/arrival/index.html',
                     'source_style_sha256': 'dist/arrival/style.css', 'source_design_sha256': 'dist/design.css',
                     'brand_asset_sha256': 'dist/assets/arrival-brand-1.0.png',
                     'brand_display_asset_sha256': 'dist/assets/arrival-brand-1.0.webp'}

    def base(metadata_path, media_version, media_kind):
        if not isinstance(media_version, str) or not re.fullmatch(r'[A-Za-z0-9][A-Za-z0-9._+-]*', media_version):
            raise ValueError('Invalid media version')
        tag = release_tag(media_version)
        release_path = f'docs/verification/release-verification-{tag}.json'
        runtime_path = f'docs/verification/android-runtime-{tag}.json'
        release = json.loads((root / release_path).read_text())
        runtime = json.loads((root / runtime_path).read_text())
        if (release.get('version') != media_version or runtime.get('version') != media_version
                or runtime.get('release_apk_sha256') != release.get('sha256')
                or runtime.get('aosp', {}).get('installed_apk_sha256') != release.get('sha256')
                or (media_version == version and release.get('sha256') != apk_sha256)):
            raise ValueError('Media release/runtime identities disagree: ' + metadata_path)
        json_records.update((metadata_path, release_path, runtime_path))
        record = {'scope': 'current_release' if media_version == version else 'historical_release',
                  'version': media_version, 'kind': media_kind,
                  'release_apk_sha256': release['sha256'], 'metadata_path': metadata_path,
                  'metadata_sha256': digest((root / metadata_path).read_bytes()),
                  'release_record': release_path, 'release_record_sha256': digest((root / release_path).read_bytes()),
                  'runtime_record': runtime_path, 'runtime_record_sha256': digest((root / runtime_path).read_bytes())}
        if media_version != version:
            archive_path = f'submission/ArrivalBrief-review-{media_version}.zip'
            archive_data = (root / archive_path).read_bytes()
            with zipfile.ZipFile(BytesIO(archive_data)) as archive:
                manifest = json.loads(archive.read('SUBMISSION-MANIFEST.json'))
                if (archive.testzip() is not None or manifest.get('version') != media_version
                        or manifest.get('apk_sha256') != release['sha256']
                        or any(digest(archive.read(path)) != release['sha256'] for path in manifest['apk_files'])
                        or archive.read(metadata_path) != (root / metadata_path).read_bytes()
                        or archive.read(runtime_path) != (root / runtime_path).read_bytes()):
                    raise ValueError('Historical media archive identity differs: ' + archive_path)
            archives[archive_path] = {'original_path': archive_path, 'bytes': len(archive_data),
                                      'sha256': digest(archive_data), 'reason': 'historical_media_source_archive',
                                      'outer_zip_path': 'evidence-history/' + archive_path}
            record['source_archive'] = dict(archives[archive_path])
        return record, runtime

    def capture(path, sha256, captured=None):
        path = relative_evidence_path(path).as_posix()
        if digest((root / path).read_bytes()) != sha256 or (captured is not None and captured.get(path) != sha256):
            raise ValueError('Media capture differs from recorded evidence: ' + path)
        images.add(path)

    def probe(path):
        try:
            return json.loads(subprocess.check_output(
                ['ffprobe', '-v', 'error', '-show_entries',
                 'stream=codec_type,width,height:format=duration', '-of', 'json', str(path)],
                stderr=subprocess.PIPE, timeout=30))
        except (OSError, subprocess.SubprocessError, ValueError) as error:
            raise ValueError('Cannot inspect media dimensions and duration: ' + str(path)) from error

    def rectangle(value, width, height):
        return (isinstance(value, list) and len(value) == 4
                and all(type(n) is int for n in value)
                and value[0] >= 0 and value[1] >= 0 and value[2] > 0 and value[3] > 0
                and value[0] + value[2] <= width and value[1] + value[3] <= height)

    def apply_reframe(prefix, meta, record):
        entry = reframes[prefix]
        original_path = f'dist/assets/media/{prefix}-reel.mp4'
        original_bytes = (root / original_path).read_bytes()
        if (record['scope'] != 'historical_release' or record['version'] != '1.0.0-rc3'
                or entry.get('source_metadata') != {'path': record['metadata_path'], 'sha256': record['metadata_sha256']}
                or entry.get('source_video') != {'path': original_path, 'sha256': meta['sha256']}):
            raise ValueError('Reframe does not identify the unchanged RC3 sources: ' + prefix)
        with zipfile.ZipFile(root / record['source_archive']['original_path']) as archive:
            if archive.read(original_path) != original_bytes:
                raise ValueError('Reframe source video differs from the RC3 source archive: ' + prefix)
        scenes = entry.get('scenes', [])
        if len(scenes) != 4 or len(meta['scenes']) != 4:
            raise ValueError('Reframe must preserve all four original screenshot scenes')
        dimensions = {}
        for index, (scene, original) in enumerate(zip(scenes, meta['scenes'])):
            if (scene.get('source') != original['source']
                    or scene.get('source_sha256') != original['source_sha256']
                    or scene.get('start') != original['start'] or scene.get('start') != index * 7.5
                    or scene.get('duration') != original['duration'] or scene.get('duration') != 7.5
                    or scene.get('fit') != 'contain'
                    or not isinstance(scene.get('title'), str) or not scene['title'].strip()
                    or not rectangle(scene.get('placement'), 1280, 720)):
                raise ValueError('Reframe scene source, timing or placement differs: ' + prefix)
            source_path = relative_evidence_path(scene['source'])
            capture(scene['source'], scene['source_sha256'])
            if scene['source'] not in dimensions:
                streams = probe(root / source_path).get('streams', [])
                if len(streams) != 1 or streams[0].get('codec_type') != 'video':
                    raise ValueError('Reframe source is not one still-image stream')
                dimensions[scene['source']] = (streams[0]['width'], streams[0]['height'])
            if 'crop' not in scene or (scene['crop'] is not None and not rectangle(scene['crop'], *dimensions[scene['source']])):
                raise ValueError('Reframe crop is missing or outside the original screenshot')
        output = entry.get('output', {})
        output_path = f'dist/assets/media/{prefix}-reframed.mp4'
        if (output.get('path') != output_path or output.get('width') != 1280 or output.get('height') != 720
                or output.get('duration_seconds') != 30 or type(output.get('bytes')) is not int):
            raise ValueError('Unexpected reframe delivery path or video format')
        data = (root / output_path).read_bytes()
        if len(data) != output['bytes'] or digest(data) != output.get('sha256'):
            raise ValueError('Reframed video differs from its recorded bytes')
        inspected = probe(root / output_path)
        streams = inspected.get('streams', [])
        if (len(streams) != 1 or streams[0] != {'codec_type': 'video', 'width': 1280, 'height': 720}
                or abs(float(inspected.get('format', {}).get('duration', -1)) - 30.0) > 0.05):
            raise ValueError('Reframed video must be silent 30-second 1280x720 video')
        original = {'path': original_path, 'sha256': meta['sha256'], 'bytes': len(original_bytes)}
        record.update(video_path=output_path, video_sha256=output['sha256'], source_video=original,
                      presentation_manifest={'path': reframe_path, 'sha256': digest((root / reframe_path).read_bytes()), 'prefix': prefix})
        separate_media.append({'original_path': original_path, 'sha256': original['sha256'], 'bytes': original['bytes'],
                               'reason': 'historical_source_video_delivered_separately',
                               'outer_zip_path': 'evidence-history/' + original_path})

    for prefix in ('arrival', 'arrival-confirm'):
        metadata_path = f'dist/assets/media/{prefix}-metadata.json'
        meta = json.loads((root / metadata_path).read_text())['content']
        record, _ = base(metadata_path, meta.get('app_version'), 'edited_web_stills')
        if record['scope'] == 'historical_release':
            with zipfile.ZipFile(root / record['source_archive']['original_path']) as archive:
                for field, path in source_fields.items():
                    if digest(archive.read(path)) != meta.get(field):
                        raise ValueError('Historical web source differs from its preserved archive: ' + path)
        else:
            for field, path in source_fields.items():
                if digest((root / path).read_bytes()) != meta.get(field):
                    raise ValueError('Current web media source differs: ' + path)
        video_path = f'dist/assets/media/{prefix}-reel.mp4'
        if digest((root / video_path).read_bytes()) != meta.get('sha256'):
            raise ValueError('Web video hash differs: ' + video_path)
        for scene in meta['scenes']:
            capture(scene['source'], scene['source_sha256'])
        record.update(video_path=video_path, video_sha256=meta['sha256'], source_files={path: meta[field] for field, path in source_fields.items()})
        if prefix in reframes:
            apply_reframe(prefix, meta, record)
        records.append(record)

    metadata_path = 'dist/assets/media/arrival-native-validation.json'
    native = json.loads((root / metadata_path).read_text())
    record, runtime = base(metadata_path, native.get('version'), native.get('kind'))
    if (native.get('release_apk_sha256') != record['release_apk_sha256']
            or native.get('tested_debug_apk_sha256') != runtime['aosp'].get('debug_apk_sha256')):
        raise ValueError('Native edited video identifies another release/debug build')
    captured = {relative_evidence_path(shot['path']).as_posix(): shot['sha256'] for shot in runtime['screenshots']}
    for shot in native['screenshots']:
        capture(shot['packaged_screenshot'], shot['sha256'], captured)
    video_path = 'dist/assets/media/arrival-native-validation.mp4'
    if digest((root / video_path).read_bytes()) != native.get('video_sha256'):
        raise ValueError('Native edited video hash differs')
    record.update(video_path=video_path, video_sha256=native['video_sha256'])
    records.append(record)

    metadata_path = 'dist/assets/media/arrival-native-journey.json'
    journey = json.loads((root / metadata_path).read_text())
    record, runtime = base(metadata_path, journey.get('version'), journey.get('kind'))
    scenario = runtime['aosp'].get('release_scenario', {})
    if (journey.get('kind') != 'continuous_actual_release_ui' or scenario.get('status') != 'passed'
            or relative_evidence_path(scenario.get('metadata', '')).as_posix() != metadata_path
            or any(value != record['release_apk_sha256'] for value in
                   (journey.get('release_apk_sha256'), journey.get('installed_apk_sha256'),
                    scenario.get('release_apk_sha256'), scenario.get('installed_apk_sha256')))):
        raise ValueError('Continuous journey release/runtime identity differs')
    for field in ('source_video', 'published_video', 'poster', 'captions', 'frame_validation'):
        item = journey[field]
        data = (root / relative_evidence_path(item['path'])).read_bytes()
        if digest(data) != item['sha256'] or ('bytes' in item and len(data) != item['bytes']):
            raise ValueError('Continuous journey artifact identity differs: ' + field)
    json_records.add(journey['frame_validation']['path'])
    if (journey['published_video'].get('no_cuts') is not True
            or float(journey['source_video']['actual_duration_seconds']) <= 0
            or abs(float(journey['source_video']['actual_duration_seconds']) - float(journey['published_video']['duration_seconds'])) > 0.5):
        raise ValueError('Source and published journey timelines differ')
    captured = {relative_evidence_path(shot['path']).as_posix(): shot['sha256'] for shot in runtime['screenshots']}
    events = [event for event in journey.get('events', []) if event.get('path')]
    if not events:
        raise ValueError('Continuous journey has no identifiable captured event')
    for event in events:
        capture(event['path'], event['sha256'], captured)
    record.update(video_path=journey['published_video']['path'], video_sha256=journey['published_video']['sha256'],
                  source_video=journey['source_video'], frame_validation=journey['frame_validation'])
    records.append(record)
    return {'records': records, 'required_images': images, 'json_records': json_records,
            'source_archives': list(archives.values()), 'separate_media': separate_media,
            'journey_metadata': metadata_path}


def delivery_plan(version, apk_sha256, root=ROOT):
    """Keep current proof; inventory historical binaries for the outer bundle."""
    tag = release_tag(version)
    runtime_rel = Path(f'docs/verification/android-runtime-{tag}.json')
    runtime = json.loads((root / runtime_rel).read_text())
    if runtime.get('version') != version or runtime.get('release_apk_sha256') != apk_sha256:
        raise ValueError('Current runtime evidence does not identify the release')
    aosp = runtime.get('aosp', {})
    if (runtime.get('publication_stage') != 'runtime'
            or aosp.get('status') != 'installed_and_launched'
            or aosp.get('installed_apk_sha256') != apk_sha256):
        raise ValueError('The current release has not completed its recorded install/launch checks')
    for suite in ('standard_ui', 'font_1_3_ui'):
        check = aosp.get(suite, {})
        if (check.get('status') != 'passed' or int(check.get('tests', 0)) < 1
                or int(check.get('failures', 0)) or int(check.get('errors', 0))):
            raise ValueError('The current UI evidence is not complete: ' + suite)
        expected = {'rc3': {'standard_ui': 18, 'font_1_3_ui': 6},
                    'rc4': {'standard_ui': 4, 'font_1_3_ui': 4}}.get(tag, {}).get(suite)
        if expected is not None and int(check['tests']) != expected:
            raise ValueError('The current UI evidence count differs from the declared release scope: ' + suite)
    required_images = set()
    for shot in runtime.get('screenshots', []):
        path = relative_evidence_path(shot['path'])
        if digest((root / path).read_bytes()) != shot['sha256']:
            raise ValueError('Current runtime capture hash differs: ' + str(path))
        required_images.add(path.as_posix())
    media = validate_media_evidence(version, apk_sha256, root)
    required_images.update(media['required_images'])
    journey_metadata = media['journey_metadata']
    qr_record, qr = validate_current_qr(version, apk_sha256, runtime, root)
    for shot in qr['screenshots']:
        required_images.add(relative_evidence_path(shot['path']).as_posix())
    gallery_record = f'screenshots/{tag}/manifest.json'
    gallery = json.loads((root / gallery_record).read_text())
    if (gallery.get('version') != version or gallery.get('release_apk_sha256') != apk_sha256
            or gallery.get('runtime_evidence_path') != runtime_rel.as_posix()
            or gallery.get('runtime_evidence_sha256') != digest((root / runtime_rel).read_bytes())):
        raise ValueError('Current gallery release/runtime identity differs')
    current_captured = {relative_evidence_path(shot['path']).as_posix(): shot['sha256']
                        for shot in runtime.get('screenshots', [])}
    for shot in gallery.get('screens', []):
        source = relative_evidence_path(shot['source']).as_posix()
        original = root / 'screenshots' / tag / shot['file']
        if (current_captured.get(source) != shot.get('source_sha256')
                or digest(original.read_bytes()) != shot.get('source_sha256')):
            raise ValueError('Current gallery capture differs from its runtime: ' + source)
        required_images.add(source)
    if not gallery.get('screens'):
        raise ValueError('Current gallery contains no original app captures')
    hash_records = media['json_records'] | {runtime_rel.as_posix(), qr_record, gallery_record}
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
    records = list(media['source_archives']) + media.get('separate_media', [])
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
        elif path.name.startswith(f'android-aosp28-{tag}-release-frame-') and path.suffix in IMAGE_SUFFIXES:
            if name in required_images:
                raise ValueError('A required app capture cannot be separated as a derived frame')
            reason = 'derived_video_analysis_frame_delivered_separately'
        elif path.name in {'android-aosp28-rc1-logcat.txt', 'android-aosp28-rc2-logcat.txt',
                           'pleos-final-04114df-selected-logcat.log'} and tag in ('rc3', 'rc4'):
            reason = 'historical_raw_log_delivered_separately'
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
    # Screenshot handoffs may expose the same current original under a friendly
    # filename. Preserve one byte-identical copy in the review and rewrite only
    # document links; historical screenshot collections remain in the outer ZIP.
    current_by_hash = {digest((root / name).read_bytes()): name for name in sorted(required_images)}
    screenshot_root = root / 'screenshots'
    if screenshot_root.is_dir():
        for path in sorted(screenshot_root.rglob('*')):
            rel = path.relative_to(root)
            if not path.is_file() or path.is_symlink() or not allowed(rel):
                continue
            if path.suffix not in IMAGE_SUFFIXES | {'.pdf', '.zip'}:
                continue
            data = path.read_bytes()
            item = {'original_path': rel.as_posix(), 'bytes': len(data), 'sha256': digest(data)}
            retained = current_by_hash.get(item['sha256'])
            if retained:
                item.update(reason='byte_identical_retained_capture', retained_path=retained)
            elif len(rel.parts) > 2 and rel.parts[1] == tag:
                if path.name == 'overview.png':
                    reason = 'derived_capture_overview_delivered_separately'
                elif (len(rel.parts) == 3 and path.name in {
                        'ArrivalBrief-app-screenshots.pdf', 'ArrivalBrief-app-screenshots.zip'}):
                    reason = 'derived_screenshot_handoff_delivered_separately'
                else:
                    continue
                item.update(reason=reason, outer_zip_path='evidence-history/' + rel.as_posix())
            else:
                item.update(reason='historical_screenshot_collection_delivered_separately',
                            outer_zip_path='evidence-history/' + rel.as_posix())
            records.append(item)
    # Runtime records retain original recording identities even though the bytes
    # travel in the outer bundle. Do not silently accept a stale video record.
    for record in (root / 'docs/verification').glob('android-runtime-*.json'):
        video = json.loads(record.read_text()).get('continuous_ui_video')
        if not video:
            continue
        if not video.get('path'):
            historical_journey = next((item for item in media['records']
                                       if item['kind'] == 'continuous_actual_release_ui'
                                       and item['scope'] == 'historical_release'), None)
            if (tag != 'rc4' or record != root / runtime_rel
                    or video.get('status') != 'not_recorded_for_rc4'
                    or historical_journey is None
                    or video.get('historical_version') != historical_journey['version']
                    or video.get('historical_metadata') != '../../' + historical_journey['metadata_path']):
                raise ValueError('Continuous recording has no path or a valid explicit historical reference: ' + str(record))
            # This current release explicitly has no recording. Its historical
            # journey was already checked against its own APK and source archive.
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
    omitted_names = {item['original_path'] for item in records}
    omitted_directories = []
    if screenshot_root.is_dir():
        for directory in sorted(screenshot_root.rglob('*')):
            if not directory.is_dir() or directory.is_symlink():
                continue
            children = [path.relative_to(root).as_posix() for path in directory.rglob('*')
                        if path.is_file() and not path.is_symlink() and allowed(path.relative_to(root))]
            if children and all(name in omitted_names for name in children):
                omitted_directories.append(directory.relative_to(root).as_posix())
    return {'version': version, 'tag': tag, 'runtime_record': runtime_rel.as_posix(),
            'required_images': sorted(required_images), 'omissions': records,
            'omitted_directories': omitted_directories,
            'current_qr_record': qr_record, 'current_gallery_record': gallery_record,
            'hash_linked_evidence_records': sorted(hash_records), 'media_evidence': media['records'],
            'release_journey_metadata': journey_metadata}


def review_layout(text, rel, plan):
    """Adjust delivery-document paths only; app/build source is never rewritten."""
    if is_core(rel) or rel.suffix not in {'.md', '.json', '.html'}:
        return text
    # Evidence records hash one another (runtime, gallery, scene validation).
    # Keep their canonical paths and bytes; delivery aliases live in the manifest.
    if rel.suffix == '.json':
        return text
    text = text.replace('apk/ArrivalBrief_release.apk', PACKAGED_APK)
    if rel.as_posix() == 'apk/README.md':
        text = text.replace('](ArrivalBrief_release.apk)', '](../' + PACKAGED_APK + ')')
    referenced = [item for item in plan['omissions']
                  if Path(item['original_path']).name in text
                  and item.get('reason') != 'byte_identical_duplicate']
    if rel.suffix == '.md':
        note = os.path.relpath('PACKAGE-CONTENTS.md', rel.parent.as_posix())
        by_path = {item['original_path']: item for item in plan['omissions']}
        def link(match):
            url = urlsplit(match.group(3))
            if url.scheme or url.netloc or not url.path:
                return match.group(0)
            target = unquote(url.path)
            target = os.path.normpath(target.lstrip('/') if target.startswith('/')
                                      else os.path.join(rel.parent.as_posix(), target))
            item = by_path.get(target)
            if target in plan.get('omitted_directories', []):
                return '[' + match.group(2) + ' (별도 보존 자료)](' + note + ')'
            if not item:
                return match.group(0)
            if item.get('retained_path'):
                retained = os.path.relpath(item['retained_path'], rel.parent.as_posix())
                if url.fragment:
                    retained += '#' + url.fragment
                return match.group(1) + '[' + match.group(2) + '](' + retained + ')'
            return '[' + match.group(2) + ' (별도 보존 자료)](' + note + ')'
        text = re.sub(r'(!?)\[([^\]]*)\]\(([^\s)]+)\)', link, text)
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
        if manifest.get('schema_version') == 4:
            media_records = manifest.get('media_evidence', [])
            if len(media_records) != 4 or len({record.get('video_path') for record in media_records}) != 4:
                raise ValueError('The current/historical media inventory is incomplete')
            for record in media_records:
                scope = 'current_release' if record['version'] == manifest['version'] else 'historical_release'
                if record.get('scope') != scope:
                    raise ValueError('A video was assigned to the wrong release scope')
                for path_key, hash_key in (('metadata_path', 'metadata_sha256'), ('video_path', 'video_sha256'),
                                           ('release_record', 'release_record_sha256'), ('runtime_record', 'runtime_record_sha256')):
                    if digest(z.read(record[path_key])) != record[hash_key]:
                        raise ValueError('Packaged media proof differs: ' + record[path_key])
                own_release = json.loads(z.read(record['release_record']))
                own_runtime = json.loads(z.read(record['runtime_record']))
                if (own_release['version'] != record['version'] or own_release['sha256'] != record['release_apk_sha256']
                        or own_runtime['version'] != record['version'] or own_runtime['release_apk_sha256'] != record['release_apk_sha256']):
                    raise ValueError('Packaged video release/runtime identities disagree')
                if scope == 'current_release' and record['release_apk_sha256'] != manifest['apk_sha256']:
                    raise ValueError('Current media does not identify the current APK')
                if scope == 'historical_release' and record.get('source_archive') not in manifest['delivery_omissions']:
                    raise ValueError('Historical media source archive has no preserved delivery identity')
                presentation = record.get('presentation_manifest')
                reframed_paths = {f'dist/assets/media/{prefix}-reframed.mp4': prefix
                                  for prefix in ('arrival', 'arrival-confirm')}
                if record['video_path'] in reframed_paths or presentation:
                    prefix = reframed_paths.get(record['video_path'])
                    if (prefix is None or record['kind'] != 'edited_web_stills'
                            or record['version'] != '1.0.0-rc3' or scope != 'historical_release'
                            or not isinstance(presentation, dict)
                            or presentation.get('path') != 'dist/assets/media/web-reframe.json'
                            or presentation.get('prefix') != prefix
                            or digest(z.read(presentation['path'])) != presentation.get('sha256')):
                        raise ValueError('Packaged historical reframe identity is invalid')
                    reframe = json.loads(z.read(presentation['path']))
                    videos = [item for item in reframe.get('videos', []) if item.get('prefix') == prefix]
                    if len(videos) != 1:
                        raise ValueError('Packaged reframe has no unique video entry')
                    entry = videos[0]
                    source_video = record.get('source_video', {})
                    if (entry.get('source_metadata') != {'path': record['metadata_path'], 'sha256': record['metadata_sha256']}
                            or entry.get('source_video') != {key: source_video.get(key) for key in ('path', 'sha256')}
                            or entry.get('output', {}).get('path') != record['video_path']
                            or entry.get('output', {}).get('sha256') != record['video_sha256']
                            or entry.get('output', {}).get('bytes') != len(z.read(record['video_path']))
                            or not any(item.get('original_path') == source_video.get('path')
                                       and item.get('sha256') == source_video.get('sha256')
                                       and item.get('bytes') == source_video.get('bytes')
                                       and item.get('reason') == 'historical_source_video_delivered_separately'
                                       for item in manifest['delivery_omissions'])):
                        raise ValueError('Packaged reframe and original source delivery disagree')
            for name in manifest.get('hash_linked_evidence_records', []):
                if name not in expected:
                    raise ValueError('Hash-linked evidence is missing: ' + name)
                if compare_root and z.read(name) != (compare_root / name).read_bytes():
                    raise ValueError('Hash-linked evidence JSON bytes differ: ' + name)
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
        media_description = '각 영상의 촬영 버전이 표시된 웹 편집 영상 2편, Android debug 화면 편집 영상 1편, 서명 release 연속 시연 1편'
        notes = ['# 익명 검토 ZIP 구성', '', f'이 전달본은 {version}의 앱 소스·최종 APK·현재 검증 캡처·검증 결과와 {media_description}을 포함합니다. 앱 소스와 빌드 입력은 원본 바이트 그대로입니다. 이전 버전의 캡처·편집 영상·큰 원문 로그, 연속 원본 녹화, 원본에서 파생한 분석 프레임과 화면 모음 overview는 용량을 위해 전체 운영 ZIP 및 별도 공개 저장소에서 제공합니다. 현재 앱 원본 캡처와 갤러리의 개별 화면은 포함합니다.', '',
                 '- APK는 [dist/downloads/ArrivalBrief_release.apk](dist/downloads/ArrivalBrief_release.apk)에 한 번만 들어 있습니다. 정적 서버 루트는 기존대로 `dist`를 사용합니다.',
                 '- 포함한 영상과 현재 런타임 메타데이터가 직접 참조하는 캡처는 모두 포함했습니다. 과거 영상은 당시 버전의 증거이며 현재 APK의 실행 결과로 합산하지 않습니다. 과거 영상의 소스는 당시 검토 ZIP의 원본 바이트와 대조했고 해당 ZIP도 전체 운영 ZIP에 보존했습니다.',
                 '- 별도 보존 자료를 가리키는 Markdown 링크는 이 구성 안내로 연결했습니다. 검증 JSON은 서로 원본 해시를 참조하므로 원래 바이트와 경로를 유지합니다. JSON이 가리키는 중복·별도 제공 파일은 아래 기록 및 manifest의 `retained_path`/`outer_zip_path`에서 찾습니다. 앱 구현·APK·캡처 이미지 내용은 변경하지 않았습니다.', '', '## 영상별 촬영 버전', '',
                 '| 영상 | 촬영 버전 | 현재 전달본과의 관계 |', '| --- | --- | --- |']
        notes += [f"| `{record['video_path']}` | {record['version']} | {'현재 버전 실행' if record['scope'] == 'current_release' else '이전 버전 실행 기록'} |" for record in plan['media_evidence']]
        notes += ['', '## 중복 정리 및 별도 제공 기록', '']
        notes += [f"- `{x['original_path']}` → `{x.get('retained_path', x.get('outer_zip_path'))}` · {x['bytes']} bytes · SHA-256 `{x['sha256']}`" for x in delivery]
        (staging / 'PACKAGE-CONTENTS.md').write_text('\n'.join(notes) + '\n')
        verify_document_links(staging)
        source_digest = digest(json.dumps(source, ensure_ascii=False, sort_keys=True, separators=(',', ':')).encode())
        files = [{'path': f.relative_to(staging).as_posix(), 'bytes': f.stat().st_size, 'sha256': digest(f.read_bytes())} for f in sorted(staging.rglob('*')) if f.is_file()]
        manifest = {'schema_version': 4, 'app': 'Arrival Brief', 'version': version, 'apk_sha256': actual_hash, 'apk_files': [PACKAGED_APK],
                    'source_tree_sha256': source_digest, 'source_files': source, 'files': files,
                    'anonymized_documents': transformed,
                    'delivery_adjusted_documents': adjusted, 'delivery_omissions': delivery,
                    'delivery_omitted_directories': plan.get('omitted_directories', []),
                    'current_runtime_record': plan['runtime_record'], 'required_capture_files': plan['required_images'],
                    'current_qr_record': plan['current_qr_record'],
                    'current_gallery_record': plan['current_gallery_record'],
                    'hash_linked_evidence_records': plan['hash_linked_evidence_records'],
                    'media_evidence': plan['media_evidence'],
                    'release_journey_metadata': plan['release_journey_metadata'],
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
    journey_line = ''
    if plan['release_journey_metadata']:
        journey = next(record for record in plan['media_evidence'] if record['kind'] == 'continuous_actual_release_ui')
        journey_scope = '현재 버전' if journey['scope'] == 'current_release' else '이전 버전 기록'
        journey_line = f"- 실제 서명 release 연속 시연: dist/assets/media/arrival-native-journey.mp4 · {journey['version']} · {journey_scope} (당시 영상·APK·원본 캡처 해시 대조)\n"
    media_versions = ', '.join(sorted({record['version'] for record in plan['media_evidence']}))
    edited_video_paths = ', '.join(record['video_path'] for record in plan['media_evidence']
                                  if record['kind'] != 'continuous_actual_release_ui')
    checklist.write_text(f'''# Arrival Brief 운영용 제출 목록

이 문서는 운영 전달용이며 익명 검토 ZIP에 포함하지 않습니다. 이메일은 발송하지 않았습니다.

## 빠른 검토 순서

1. [README에 연결된 검토 순서]({args.repository_url}/blob/main/docs/REVIEW_START.md)
2. [현재 앱 스크린샷]({args.repository_url}/blob/main/screenshots/{plan['tag']}/README.md)
3. [APK]({args.repository_url}/blob/main/apk/ArrivalBrief_release.apk)

## 파일 대조

- 저장소: {args.repository_url}
- 저장소 APK 경로: apk/ArrivalBrief_release.apk
- 익명 ZIP 내부 APK 경로: dist/downloads/ArrivalBrief_release.apk (동일 APK 1개)
- APK 버전: {version}
- APK SHA-256: {actual_hash}
- 익명 검토 ZIP: {zip_path.name}
- ZIP SHA-256: {zip_hash}
- 앱 소스 목록 SHA-256: {source_digest}
- 편집 영상: {edited_video_paths}
{journey_line}- 포함 영상의 촬영 버전: {media_versions}. 각 metadata와 manifest의 media_evidence에 현재 버전·이전 버전 관계를 명시했습니다. 이전 영상은 현재 APK의 실행 검증이 아닙니다. 두 웹 영상과 Android debug 캡처 편집본, 실제 release 연속 시연을 구분합니다. 어느 영상도 실제 Pleos 신호·실차·실물 휴대폰 광학 QR 성공을 뜻하지 않습니다.
- 현재 화면: screenshots/{plan['tag']}/README.md. 바이트가 같은 이미지 사본은 검토 ZIP에서 원본 한 곳으로 연결합니다.
- 운영자 자료: examples/organizer/README.md. 합성 예시와 준비 양식이며 실제 제휴·채택·매출 성과가 아닙니다.
- 과거 캡처·영상·큰 원문 로그·스크린샷 PDF/ZIP, 연속 원본 녹화와 파생 분석 프레임·overview는 전체 운영 ZIP의 evidence-history/ 및 공개 저장소에 보존합니다. 현재 갤러리의 개별 원본 화면은 검토 ZIP에도 포함합니다. 원래 경로·별도 보존 경로·SHA는 PACKAGE-CONTENTS.md와 manifest에서 대조합니다.

## 제출 전 파일 대조

- 서명한 공식 접수양식 PDF·동일 내용 DOCX·공식명 APK를 기본 첨부로 사용합니다. PDF와 DOCX 동시 첨부는 공식 필수 조건이 아닌 보기용·편집 원본 구성입니다.
- 접수양식 7번의 함께 제공 자료, 실제 첨부 이름, 메일의 저장소·영상 링크가 일치해야 합니다.
- 전체 운영 ZIP과 개별 파일을 모두 중복 첨부하라는 뜻이 아닙니다.
- 신청서·연락처·동의서·서명키 개인 백업은 공개 저장소와 익명 검토 ZIP에 포함하지 않습니다. 서명키 백업은 제출 자료가 아닙니다.
- 공개 저장소의 현재 APK·소스·검토 ZIP 접근과 파일 해시는 최종 게시 뒤 다시 확인합니다. 비공개로 바꾸면 심사 접근 조건도 확인해야 합니다.

## 후속 검증 범위

실제 Pleos 설치·권한·NaviHelper 응답·차량 UX 제한 전환, 실물 휴대폰 광학 QR, 실제 사용자·운영자 과업과 성과는 별도입니다. 일반 Android 실행·합성 사례·사업 가정으로 완료 표시하지 않습니다. 이전 버전의 실패와 미검증 범위도 해당 기록에 보존합니다.

1차 접수 준비와 2차 PT·실차 검증 완료는 구분합니다. 본 파일을 생성한 것만으로 접수나 외부 전송이 이루어지지 않습니다.
''')
    print(json.dumps({'review_zip': str(zip_path), 'operator_checklist': str(checklist), 'version': version, 'files': len(files), 'source_tree_sha256': source_digest, 'apk_sha256': actual_hash, 'zip_sha256': zip_hash, 'zip_bytes': zip_path.stat().st_size, 'runtime_record': plan['runtime_record'], 'separately_delivered_files': sum(bool(x.get('outer_zip_path')) for x in delivery), 'status': 'prepared_not_submitted'}, ensure_ascii=False))


if __name__ == '__main__':
    main()
