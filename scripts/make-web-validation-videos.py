#!/usr/bin/env python3
"""Render two labelled 30-second edits from actual current web UI captures."""
import argparse
import hashlib
import json
from pathlib import Path
import subprocess
import tempfile

p = argparse.ArgumentParser(description=__doc__)
p.add_argument('--font', type=Path, required=True)
p.add_argument('--version', default='1.0.0-rc2')
p.add_argument('--evidence-prefix', default='arrival-rc2-final-web')
a = p.parse_args()
root = Path(__file__).resolve().parents[1]
captures = root / 'docs/verification'
output = root / 'dist/assets/media'
scenarios = [
    ('arrival', '도착 준비: 장소 확인부터 주행 화면까지', [
        ('initial', '01  행사장과 차량 전체 높이를 확인해요', None),
        ('confirmed', '02  현재 행사장·높이·주차 상태를 직접 확인해요', None),
        ('confirmed', '03  확정한 진입문과 추천 주차를 한 장에', [635, 0, 660, 800]),
        ('driving', '04  주행 체험에서는 입력을 잠가요', [45, 0, 1280, 620]),
    ]),
    ('arrival-confirm', '자료 상충과 운영 제한: 확정 보류', [
        ('conflict', '01  공식 주차 자료가 서로 다르면 보류해요', None),
        ('conflict', '02  두 원문의 구역과 확인 날짜를 함께 봐요', [635, 300, 660, 575]),
        ('closed', '03  원문에 휴관 안내가 있어도 보류해요', None),
        ('closed', '04  확인이 필요한 원문을 직접 대조해요', [635, 300, 660, 575]),
    ]),
]

def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()

def stamp(seconds):
    return f'00:00:{seconds:06.3f}'

for prefix, title, scenes in scenarios:
    records = []
    with tempfile.TemporaryDirectory(prefix='arrival-web-video-') as temp:
        temp = Path(temp)
        (temp / 'footer.txt').write_text(a.version + ' 실제 웹 화면 캡처 편집 · 차량 상태 시뮬레이션 · 실차 영상이 아닙니다')
        for i, (name, caption, crop) in enumerate(scenes):
            source = captures / f'{a.evidence_prefix}-{name}.jpg'
            if not source.is_file():
                raise FileNotFoundError(source)
            (temp / f'title{i}.txt').write_text(caption)
            filters = []
            if crop:
                x, y, w, h = crop
                filters.append(f'crop={w}:{h}:{x}:{y}')
            filters += [
                'scale=1160:640:force_original_aspect_ratio=decrease:flags=lanczos',
                'pad=1280:720:(ow-iw)/2:48:color=0x152b3e',
                f"drawtext=fontfile='{a.font}':textfile='{temp / f'title{i}.txt'}':x=32:y=12:fontsize=24:fontcolor=white",
                f"drawtext=fontfile='{a.font}':textfile='{temp / 'footer.txt'}':x=(w-text_w)/2:y=697:fontsize=14:fontcolor=0xd1dfeb",
                'fade=t=in:st=0:d=0.18', 'fade=t=out:st=7.32:d=0.18', 'format=yuv420p',
            ]
            subprocess.run(['ffmpeg', '-hide_banner', '-loglevel', 'error', '-y', '-loop', '1', '-i', str(source),
                            '-t', '7.5', '-r', '24', '-vf', ','.join(filters), '-an', '-c:v', 'libx264',
                            '-preset', 'veryfast', '-crf', '19', '-threads', '1', str(temp / f'{i}.mp4')], check=True)
            records.append({'start': i * 7.5, 'duration': 7.5, 'source': source.name,
                            'source_sha256': sha(source), 'crop': crop, 'title': caption})
        (temp / 'list.txt').write_text(''.join(f"file '{temp / f'{i}.mp4'}'\n" for i in range(len(scenes))))
        target = output / f'{prefix}-reel.mp4'
        subprocess.run(['ffmpeg', '-hide_banner', '-loglevel', 'error', '-y', '-f', 'concat', '-safe', '0',
                        '-i', str(temp / 'list.txt'), '-c', 'copy', '-movflags', '+faststart', str(target)], check=True)
        subprocess.run(['ffmpeg', '-hide_banner', '-loglevel', 'error', '-y', '-ss', '10', '-i', str(target),
                        '-frames:v', '1', str(output / f'{prefix}-poster.jpg')], check=True)
    metadata = json.loads(subprocess.check_output(['ffprobe', '-v', 'error', '-show_entries',
        'stream=codec_name,codec_type,width,height,pix_fmt,r_frame_rate,nb_frames:format=duration,size', '-of', 'json', str(target)]))
    metadata['content'] = {'title': title, 'method': '실제 웹 화면 캡처 편집. 실시간 화면 녹화나 실차 영상이 아님.',
        'app_version': a.version, 'vehicle_state': '웹 시뮬레이션', 'audio': 'none', 'font': 'Noto Sans KR (OFL)',
        'screenshot_fit': '원본 비율 유지. 일부 장면은 실제 결과·출처·원문 확인 영역을 확대.',
        'source_engine_sha256': sha(root / 'dist/arrival/engine.mjs'),
        'source_app_sha256': sha(root / 'dist/arrival/app.mjs'),
        'source_html_sha256': sha(root / 'dist/arrival/index.html'),
        'source_style_sha256': sha(root / 'dist/arrival/style.css'),
        'source_design_sha256': sha(root / 'dist/design.css'),
        'brand_asset_sha256': sha(root / 'dist/assets/arrival-brand-1.0.png'),
        'brand_display_asset_sha256': sha(root / 'dist/assets/arrival-brand-1.0.webp'),
        'scenes': records, 'sha256': sha(target)}
    (output / f'{prefix}-metadata.json').write_text(json.dumps(metadata, ensure_ascii=False, indent=2) + '\n')
    vtt = 'WEBVTT\n\n' + ''.join(f"{i+1}\n{stamp(i*7.5)} --> {stamp((i+1)*7.5)}\n{caption}\n실제 웹 캡처 편집. 차량 상태 시뮬레이션이며 실차 영상이 아닙니다.\n\n" for i, (_, caption, _) in enumerate(scenes))
    (output / f'{prefix}-reel.vtt').write_text(vtt.rstrip() + '\n')
    print(json.dumps({'video': str(target), 'sha256': sha(target), 'bytes': target.stat().st_size}))
