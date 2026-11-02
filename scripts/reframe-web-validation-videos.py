#!/usr/bin/env python3
"""Reframe the preserved RC3 captures without changing their execution provenance."""
import argparse
import hashlib
import json
from pathlib import Path
import subprocess
import tempfile

p = argparse.ArgumentParser(description=__doc__)
p.add_argument('--font', type=Path, required=True)
a = p.parse_args()
root = Path(__file__).resolve().parents[1]
media = root / 'dist/assets/media'
background = '0xecf3fb'


def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def run(args):
    subprocess.run(args, check=True)


report = {
    'schema_version': 1,
    'kind': 'reframed_historical_web_stills',
    'app_version': '1.0.0-rc3',
    'scope': 'Layout-only re-edit of original RC3 web screenshots; not a new app execution or continuous recording',
    'layout': 'Centered titles and captures; equal horizontal margins; source background color; no UI content changes',
    'videos': [],
}
crops = {
    'arrival': [[0, 0, 1332, 926], [0, 0, 1332, 926], [634, 6, 656, 800], [52, 0, 1260, 596]],
    'arrival-confirm': [[0, 0, 1332, 926], [634, 302, 656, 572], [0, 0, 1332, 926], [634, 302, 656, 572]],
}
for prefix, boxes in crops.items():
    meta_path = media / f'{prefix}-metadata.json'
    old = json.loads(meta_path.read_text())
    assert old['content']['app_version'] == report['app_version']
    original = media / f'{prefix}-reel.mp4'
    assert sha(original) == old['content']['sha256']
    item = {
        'prefix': prefix,
        'source_metadata': {'path': meta_path.relative_to(root).as_posix(), 'sha256': sha(meta_path)},
        'source_video': {'path': original.relative_to(root).as_posix(), 'sha256': sha(original)},
        'scenes': [],
    }
    with tempfile.TemporaryDirectory(prefix='arrival-reframe-') as folder:
        folder = Path(folder)
        footer = folder / 'footer.txt'
        footer.write_text('1.0.0-rc3 실제 웹 캡처 편집 · 차량 상태 시뮬레이션 · 실차 영상 아님')
        for i, (scene, crop) in enumerate(zip(old['content']['scenes'], boxes)):
            source = root / 'docs/verification' / scene['source']
            assert sha(source) == scene['source_sha256']
            x, y, w, h = crop
            factor = min(1160 / w, 600 / h)
            width, height = int(w * factor) // 2 * 2, int(h * factor) // 2 * 2
            left, top = (1280 - width) // 2, 66 + (600 - height) // 2
            title = folder / f'title{i}.txt'
            title.write_text(scene['title'])
            filters = [
                f'crop={w}:{h}:{x}:{y}',
                f'scale={width}:{height}:flags=lanczos',
                f'pad=1280:720:{left}:{top}:color={background}',
                f"drawtext=fontfile='{a.font}':textfile='{title}':x=(w-text_w)/2:y=20:fontsize=24:fontcolor=0x152b3e",
                f"drawtext=fontfile='{a.font}':textfile='{footer}':x=(w-text_w)/2:y=690:fontsize=14:fontcolor=0x425b70",
                'fade=t=in:st=0:d=0.18:color=0xecf3fb',
                'fade=t=out:st=7.32:d=0.18:color=0xecf3fb',
                'format=yuv420p',
            ]
            run(['ffmpeg', '-hide_banner', '-loglevel', 'error', '-y', '-loop', '1', '-i', str(source),
                 '-t', '7.5', '-r', '24', '-vf', ','.join(filters), '-an', '-c:v', 'libx264',
                 '-preset', 'veryfast', '-crf', '18', '-threads', '1', str(folder / f'{i}.mp4')])
            item['scenes'].append({**{k: scene[k] for k in ('start', 'duration', 'source', 'source_sha256', 'title')},
                                   'crop': crop, 'placement': [left, top, width, height], 'fit': 'contain'})
        concat = folder / 'list.txt'
        concat.write_text(''.join(f"file '{folder / f'{i}.mp4'}'\n" for i in range(4)))
        target = media / f'{prefix}-reframed.mp4'
        run(['ffmpeg', '-hide_banner', '-loglevel', 'error', '-y', '-f', 'concat', '-safe', '0',
             '-i', str(concat), '-c', 'copy', '-movflags', '+faststart', str(target)])
        poster = media / f'{prefix}-reframed-poster.jpg'
        run(['ffmpeg', '-hide_banner', '-loglevel', 'error', '-y', '-ss', '18' if prefix == 'arrival' else '10',
             '-i', str(target), '-frames:v', '1', '-q:v', '2', str(poster)])
    probe = json.loads(subprocess.check_output(['ffprobe', '-v', 'error', '-show_entries',
        'stream=width,height:format=duration', '-of', 'json', str(target)]))
    assert probe['streams'][0] == {'width': 1280, 'height': 720} and float(probe['format']['duration']) == 30
    item['output'] = {'path': target.relative_to(root).as_posix(), 'sha256': sha(target),
                      'bytes': target.stat().st_size, 'width': 1280, 'height': 720, 'duration_seconds': 30}
    item['poster'] = {'path': poster.relative_to(root).as_posix(), 'sha256': sha(poster)}
    report['videos'].append(item)
(media / 'web-reframe.json').write_text(json.dumps(report, ensure_ascii=False, indent=2) + '\n')
print(json.dumps({'outputs': [x['output'] for x in report['videos']]}, ensure_ascii=False))
