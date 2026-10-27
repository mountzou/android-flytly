#!/usr/bin/env python3
"""Reproject the existing iOS-derived country outlines for its equirectangular share card."""
from pathlib import Path
import math, re, shutil
ROOT = Path(__file__).resolve().parents[1]
art = ROOT / 'app/src/main/assets/atlas/art'
output = ROOT / 'app/src/main/assets/sharing'
output.mkdir(exist_ok=True)
paths = []
for source in sorted(art.glob('???.svg')):
    original = re.search(r'<path[^>]+d="([^"]+)"', source.read_text())
    def project(match):
        x, y = map(float, match.groups())
        lon = x * 360 - 180
        lat = math.degrees(math.atan(math.sinh(math.pi * (1 - 2*y))))
        return f'{lon:.6f},{-lat:.6f}'
    path = re.sub(r'([\d.]+),([\d.]+)', project, original[1])
    paths.append(f'<path id="{source.stem}" fill="#DFDFE0" fill-rule="evenodd" d="{path}"/>')
(output/'world.svg').write_text('<svg xmlns="http://www.w3.org/2000/svg" viewBox="-180 -90 360 180" preserveAspectRatio="none">'+''.join(paths)+'</svg>\n')
shutil.copyfile('/Users/mountzou/ios-flytly/ios-where-travel/Assets.xcassets/InstagramGlyph.imageset/InstagramGlyph.svg', output/'instagram.svg')
print(f'Prepared {len(paths)} public share outlines and the existing Instagram glyph.')
