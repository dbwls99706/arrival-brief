#!/usr/bin/env python3
"""Make a clearly labelled 30s overview from actual Android instrumentation captures.

Never implies a continuous recording, a release-build UI run, or a Pleos service
success. Main-source equivalence and APK provenance belong to the runtime record.
"""
import argparse, hashlib, json, subprocess, tempfile
from pathlib import Path

p=argparse.ArgumentParser(description=__doc__)
p.add_argument('--screens', type=Path, required=True)
p.add_argument('--font', type=Path, required=True)
p.add_argument('--output', type=Path, required=True)
p.add_argument('--release-sha256', required=True)
p.add_argument('--debug-sha256', required=True)
a=p.parse_args();a.output.mkdir(parents=True, exist_ok=True)
scenes=[
 ('arrival-confirmed-parked.png','01  현재 행사장·차량 높이·주차 상태를 확인해요'),
 ('arrival-source-conflict-evidence.png','02  공식 주차 안내가 다르면 확정을 보류해요'),
 ('arrival-parking-memo-restored.png','03  실제 주차한 위치를 직접 기록해요'),
 ('arrival-saved-memo-qr.png','04  저장한 메모를 텍스트 QR로 표시해요'),
 ('arrival-confirmed-driving-locked.png','05  주행 체험에서는 입력과 QR을 잠가요')]
foot='자동 UI 시험의 실제 앱 캡처 편집 · 일반 Android 환경 · Pleos 연동 성공 영상이 아닙니다'
proof=[]
with tempfile.TemporaryDirectory(prefix='arrival-native-video-') as td:
 t=Path(td);(t/'foot.txt').write_text(foot)
 for i,(name,title) in enumerate(scenes):
  source=a.screens/name
  if not source.is_file():raise FileNotFoundError(source)
  (t/f'title{i}.txt').write_text(title)
  packaged={
   'arrival-confirmed-parked.png':'android-aosp28-rc1-confirmed.png',
   'arrival-source-conflict-evidence.png':'android-aosp28-rc1-source-conflict.png',
   'arrival-parking-memo-restored.png':'android-aosp28-rc1-memo.png',
   'arrival-saved-memo-qr.png':'android-aosp28-rc1-qr.png',
   'arrival-confirmed-driving-locked.png':'android-aosp28-rc1-driving.png',
  }
  proof.append({'file':name,'packaged_screenshot':'docs/verification/'+packaged[name],'sha256':hashlib.sha256(source.read_bytes()).hexdigest(),'start_seconds':i*6,'duration_seconds':6})
  filters=(f"scale=1024:640:flags=lanczos,pad=1280:720:128:48:color=0x152b3e,"
           f"drawtext=fontfile='{a.font}':textfile='{t/f'title{i}.txt'}':x=32:y=12:fontsize=24:fontcolor=white,"
           f"drawtext=fontfile='{a.font}':textfile='{t/'foot.txt'}':x=(w-text_w)/2:y=697:fontsize=14:fontcolor=0xd1dfeb,"
           "fade=t=in:st=0:d=0.18,fade=t=out:st=5.82:d=0.18,format=yuv420p")
  subprocess.run(['ffmpeg','-hide_banner','-loglevel','error','-y','-loop','1','-i',str(source),'-t','6','-r','30','-vf',filters,'-c:v','libx264','-preset','veryfast','-crf','20','-threads','1',str(t/f'{i}.mp4')],check=True)
 (t/'list.txt').write_text(''.join(f"file '{t/f'{i}.mp4'}'\n" for i in range(len(scenes))))
 target=a.output/'arrival-native-validation.mp4'
 subprocess.run(['ffmpeg','-hide_banner','-loglevel','error','-y','-f','concat','-safe','0','-i',str(t/'list.txt'),'-c','copy','-movflags','+faststart',str(target)],check=True)
 subprocess.run(['ffmpeg','-hide_banner','-loglevel','error','-y','-ss','2','-i',str(target),'-frames:v','1',str(a.output/'arrival-native-validation-poster.jpg')],check=True)
vtt='WEBVTT\n\n'+''.join(f'{i+1}\n00:00:{i*6:02d}.000 --> 00:00:{(i+1)*6:02d}.000\n{title}\n일반 Android의 자동 UI 시험 캡처 편집. 실제 차량 연동 영상이 아닙니다.\n\n' for i,(_,title) in enumerate(scenes))
(a.output/'arrival-native-validation.vtt').write_text(vtt.rstrip()+'\n')
meta={'version':'1.0.0-rc1','kind':'edited_stills_from_actual_android_instrumentation','duration_seconds':30,'width':1280,'height':720,'fps':30,'release_apk_sha256':a.release_sha256,'tested_debug_apk_sha256':a.debug_sha256,'note':'Separate debug instrumentation, same main source as release. No continuous screen recording or live Pleos response is claimed. Phone optical QR scan is unverified.','screenshots':proof,'video_sha256':hashlib.sha256(target.read_bytes()).hexdigest()}
(a.output/'arrival-native-validation.json').write_text(json.dumps(meta,ensure_ascii=False,indent=2)+'\n')
print(json.dumps({'video':str(target),'bytes':target.stat().st_size,'sha256':meta['video_sha256']}))
