#!/usr/bin/env python3
"""Make labeled contact sheets from real iOS/Android place UI captures (Pillow)."""
from pathlib import Path
from PIL import Image, ImageDraw, ImageFont

root = Path(__file__).resolve().parents[1] / 'artifacts/place-parity'
width, height, label, gap = 402, 874, 30, 16
font = ImageFont.truetype('/System/Library/Fonts/Helvetica.ttc', 15)
for theme in ['light', 'dark']:
    for group, screens in [('pages', ['country', 'city', 'travels', 'wishlist']),
                           ('states', ['city-saved', 'city-visited'])]:
        rows = (len(screens) + 1) // 2
        sheet = Image.new('RGB', ((width * 2 + gap) * 2 - gap, (height + label + gap) * rows - gap), '#d6d6d6')
        draw = ImageDraw.Draw(sheet)
        for index, screen in enumerate(screens):
            for side, platform in enumerate(['ios', 'android']):
                path = root / f'{platform}-{theme}-{screen}.png'
                image = Image.open(path).convert('RGB')
                # Both captures use a 402 x 874 logical viewport. Preserve the
                # platform bars and real content, and scale uniformly for review.
                assert image.width * height == image.height * width, f'Viewport differs: {path}'
                x = (index % 2) * (width * 2 + gap) + side * width
                y = (index // 2) * (height + label + gap)
                draw.text((x + 10, y + 7), f'{platform.upper()} · {screen} · {theme}', font=font, fill='#111111')
                sheet.paste(image.resize((width, height), Image.Resampling.LANCZOS), (x, y + label))
        sheet.save(root / f'comparison-{theme}-{group}.jpg', quality=94)
        print(f'comparison-{theme}-{group}.jpg')
