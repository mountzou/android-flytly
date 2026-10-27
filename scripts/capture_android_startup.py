#!/usr/bin/env python3
"""Capture actual native launch windows on the development emulator; retain app data.

Run only after instrumentation has finished. Original device settings are restored.
"""
import json
from pathlib import Path
import subprocess
import time

adb=['/Users/mountzou/Library/Android/sdk/platform-tools/adb','-s','emulator-5554']
root=Path(__file__).resolve().parents[1]/'artifacts/startup-parity'
out=root/'launch-frames';out.mkdir(parents=True,exist_ok=True)
def command(*args):return subprocess.check_output(adb+list(args),text=True)
assert 'Night mode: no' in command('shell','cmd','uimode','night'), 'Expected recorded original night setting'
reports=[]
try:
    command('shell','wm','size','1206x2622');command('shell','wm','density','480')
    for theme in ['light','dark']:
        command('shell','cmd','uimode','night','no' if theme=='light' else 'yes')
        command('shell','am','force-stop','com.mountzou.flytly')
        started=time.monotonic()
        process=subprocess.Popen(adb+['shell','am','start','-W','-n','com.mountzou.flytly/.MainActivity'],stdout=subprocess.PIPE,stderr=subprocess.PIPE)
        frames=[]
        for i in range(16):
            file=out/f'{theme}-{i:02}.png'
            with file.open('wb') as stream:subprocess.run(adb+['exec-out','screencap','-p'],stdout=stream,check=True)
            frames.append({'file':file.name,'elapsedMS':round((time.monotonic()-started)*1000)})
            time.sleep(.08)
        launch,error=process.communicate(timeout=30)
        assert process.returncode==0,error.decode()
        reports.append({'theme':theme,'frames':frames,'launch':launch.decode()})
finally:
    command('shell','cmd','uimode','night','no')
    command('shell','wm','size','reset');command('shell','wm','density','reset')
(root/'launch-capture.json').write_text(json.dumps(reports,indent=2)+'\n')
print('Captured real light/dark Android launch windows; night mode and viewport restored.')
