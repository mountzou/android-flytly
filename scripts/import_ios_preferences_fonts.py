#!/usr/bin/env python3
"""Pin Bricolage's measured SwiftUI optical instance. Requires fonttools==4.60.1."""
import argparse
import hashlib
import json
from pathlib import Path
from fontTools.ttLib import TTFont
from fontTools.varLib.instancer import instantiateVariableFont

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('--source', type=Path, default=Path('/Users/mountzou/ios-flytly'))
args = parser.parse_args()
root = Path(__file__).resolve().parents[1]
source = args.source / 'ios-where-travel/Resources/Fonts/BricolageGrotesque.ttf'
manifest = {'source': str(source), 'sourceSHA256': hashlib.sha256(source.read_bytes()).hexdigest(), 'instances': []}
for weight, name in [(400, 'bricolage_preferences_regular'), (500, 'bricolage_preferences_medium')]:
    target = root / f'app/src/main/res/font/{name}.ttf'
    axes = {'wght': weight, 'wdth': 100, 'opsz': 17}
    instantiateVariableFont(TTFont(source, recalcTimestamp=False), axes, inplace=True).save(target)
    manifest['instances'].append({'path': str(target.relative_to(root)), 'axes': axes,
                                  'sha256': hashlib.sha256(target.read_bytes()).hexdigest()})
(root / 'artifacts/onboarding-parity/font-provenance.json').write_text(json.dumps(manifest, indent=2) + '\n')
