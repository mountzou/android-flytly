#!/usr/bin/env python3
"""Measure process-cold startup through the first fully rendered map, retaining app data."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess
import time

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('--serial', default='emulator-5554')
parser.add_argument('--runs', type=int, default=3)
parser.add_argument('--timeout', type=float, default=180)
parser.add_argument('--output', type=Path, required=True)
parser.add_argument('--apk', type=Path, default=Path('app/build/outputs/apk/debug/app-debug.apk'))
args = parser.parse_args()
sdk = Path(os.environ.get('ANDROID_SDK_ROOT', str(Path.home() / 'Library/Android/sdk')))
adb = [str(sdk / 'platform-tools/adb'), '-s', args.serial]
args.output.mkdir(parents=True, exist_ok=True)

def command(*arguments):
    return subprocess.check_output(adb + list(arguments), text=True)

def app_pid():
    return subprocess.run(adb + ['shell', 'pidof', 'com.mountzou.flytly'], text=True, capture_output=True).stdout.strip()

result = {'method': 'Force-stop app process; retain session, app data, camera and OS page cache. Normal Android frames; no Compose test clock.',
          'serial': args.serial, 'runs': []}
if args.apk.exists():
    result['apkSHA256'] = hashlib.sha256(args.apk.read_bytes()).hexdigest()
pattern = re.compile(r'stage=([\w.]+) durationMs=(\d+) processMs=(\d+)')
for index in range(1, args.runs + 1):
    command('shell', 'am', 'force-stop', 'com.mountzou.flytly')
    stopped = time.monotonic() + 10
    while app_pid() and time.monotonic() < stopped: time.sleep(.2)
    assert not app_pid(), 'Previous app process did not stop'
    launch = command('shell', 'am', 'start', '-W', '-n', 'com.mountzou.flytly/.MainActivity')
    started = time.monotonic() + 10
    pid = app_pid()
    while not pid and time.monotonic() < started:
        time.sleep(.5)
        pid = app_pid()
        if not pid: launch += command('shell', 'am', 'start', '-W', '-n', 'com.mountzou.flytly/.MainActivity')
    assert pid.isdigit(), 'Expected one app process'
    deadline = time.monotonic() + args.timeout
    stages = {}
    while time.monotonic() < deadline:
        events = command('logcat', '-d', '--pid=' + pid, '-v', 'brief', '-s', 'FlytlyPerformance:I', '*:S')
        stages = {name: {'durationMs': int(duration), 'processMs': int(process)}
                  for name, duration, process in pattern.findall(events)}
        if 'startup.firstFrame' in stages:
            break
        time.sleep(.5)
    (args.output / f'run-{index}.txt').write_text(launch + '\n' + events)
    run = {'index': index, 'firstMapFrameReached': 'startup.firstFrame' in stages, 'stages': stages}
    result['runs'].append(run)
    (args.output / 'measurements.json').write_text(json.dumps(result, indent=2) + '\n')
    print(json.dumps(run), flush=True)
    if not run['firstMapFrameReached']:
        raise SystemExit('First map frame timed out; partial measurements preserved')
    with (args.output / f'run-{index}.png').open('wb') as stream:
        subprocess.run(adb + ['exec-out', 'screencap', '-p'], stdout=stream, check=True)
