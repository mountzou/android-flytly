#!/usr/bin/env python3
"""Copy original login assets and pin the measured iOS headline optical instance.
Requires fonttools==4.60.1. Apple mark is exported with export_ios_login_symbol.swift,
then alpha-trimmed to match SwiftUI's resizable SF Symbol canvas (shape unchanged).
"""
import argparse
import hashlib
import json
import shutil
from pathlib import Path
from fontTools.ttLib import TTFont
from fontTools.varLib.instancer import instantiateVariableFont

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('--source', type=Path, default=Path('/Users/mountzou/ios-flytly'))
args = parser.parse_args()
root = Path(__file__).resolve().parents[1]
assets = args.source / 'ios-where-travel/Assets.xcassets'
out = root / 'app/src/main/res/drawable-nodpi'
out.mkdir(exist_ok=True)
files = [('LaunchLogo.imageset/LaunchLogo@3x.png', 'login_launch_logo.png'),
         ('GoogleSignInLogo.imageset/google@3x.png', 'login_google_logo.png')]
manifest = {}
for name, target in files:
    shutil.copyfile(assets / name, out / target)
    manifest[target] = {'source': f'ios-where-travel/Assets.xcassets/{name}',
                        'sha256': hashlib.sha256((out / target).read_bytes()).hexdigest()}
source = args.source / 'ios-where-travel/Resources/Fonts/BricolageGrotesque.ttf'
font = instantiateVariableFont(TTFont(source, recalcTimestamp=False),
                               {'wght': 600, 'wdth': 100, 'opsz': 17}, inplace=True)
font.save(root / 'app/src/main/res/font/bricolage_login_headline.ttf')
manifest['login_apple_logo.png'] = {'source': 'iOS SDK UIImage(systemName: "apple.logo"), exported at 128pt/3x and alpha-trimmed',
    'sha256': hashlib.sha256((out / 'login_apple_logo.png').read_bytes()).hexdigest()}
(root / 'artifacts/login-parity/asset-provenance.json').write_text(json.dumps(manifest, indent=2) + '\n')
