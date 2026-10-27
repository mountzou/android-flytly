#!/usr/bin/env python3
"""Derive Android branding and the exact iOS metaball masks from the owned sources.

Requires Pillow. No runtime image generation or new artwork is involved.
"""
import hashlib
import json
import math
from pathlib import Path
import shutil
from PIL import Image

ROOT = Path(__file__).resolve().parents[1]
IOS = Path('/Users/mountzou/ios-flytly')
RES = ROOT / 'app/src/main/res'
QA = ROOT / 'artifacts/startup-parity'
SOURCES = {}

def source(relative):
    p = IOS / relative
    SOURCES[relative] = hashlib.sha256(p.read_bytes()).hexdigest()
    return p

def save(im, relative):
    p = RES / relative
    p.parent.mkdir(parents=True, exist_ok=True)
    im.save(p, lossless=True) if p.suffix == '.webp' else im.save(p)

for relative in ['ios-where-travel/CruiseGlass.icon/icon.json','ios-where-travel/LaunchScreen.storyboard',
                 'ios-where-travel/Assets.xcassets/LaunchBackground.colorset/Contents.json',
                 'ios-where-travel/Assets.xcassets/LaunchForeground.colorset/Contents.json']:
    source(relative)

# Retain Icon Composer's rendered shading, with the original separate airplane alpha.
# Android supplies the outer mask/background rather than nesting an Apple rounded tile.
alpha = Image.open(source('ios-where-travel/CruiseGlass.icon/Assets/airplane.png')).getchannel('A')
for mode, appearance in [('light', 'Default'), ('dark', 'Dark')]:
    rendered = Image.open(source(f'artifacts/liquid-glass-icon/CruiseGlass Exports/CruiseGlass-iOS-{appearance}-1024x1024@1x.png')).convert('RGBA')
    rendered.putalpha(alpha)
    canvas = Image.new('RGBA', (432,432))
    canvas.alpha_composite(rendered.resize((288,288),Image.Resampling.LANCZOS),(72,72))
    qualifier = 'drawable-nodpi' if mode == 'light' else 'drawable-night-nodpi'
    save(canvas, f'{qualifier}/flytly_icon_foreground.png')
    # Legacy launchers (the minimum SDK uses adaptive icons, retained for external tools).
    background = '#FAFAFA' if mode == 'light' else '#171717'
    legacy = Image.new('RGBA', (1024,1024), background)
    legacy.alpha_composite(rendered)
    if mode == 'light':
        for density,size in [('mdpi',48),('hdpi',72),('xhdpi',96),('xxhdpi',144),('xxxhdpi',192)]:
            for name in ('ic_launcher','ic_launcher_round'):
                icon = legacy.resize((size,size),Image.Resampling.LANCZOS)
                if name.endswith('round'):
                    from PIL import ImageDraw
                    mask = Image.new('L',(size,size));ImageDraw.Draw(mask).ellipse((0,0,size-1,size-1),fill=255)
                    icon.putalpha(mask)
                save(icon,f'mipmap-{density}/{name}.webp')
mono = Image.new('RGBA',(1024,1024),'white');mono.putalpha(alpha)
canvas = Image.new('RGBA',(432,432));canvas.alpha_composite(mono.resize((288,288),Image.Resampling.LANCZOS),(72,72))
save(canvas,'drawable-nodpi/flytly_icon_monochrome.png')

# The storyboard centers a 224pt image. Android's no-background splash canvas is
# 288dp; the 32dp inset preserves its apparent size and the system safe area.
launch = Image.open(source('ios-where-travel/Assets.xcassets/LaunchLogo.imageset/LaunchLogo@3x.png')).convert('RGBA')
for qualifier,ink in [('drawable-nodpi',(17,17,17)),('drawable-night-nodpi',(242,242,242))]:
    plane = Image.new('RGBA',launch.size,ink);plane.putalpha(launch.getchannel('A'))
    splash = Image.new('RGBA',(864,864));splash.alpha_composite(plane.resize((672,672),Image.Resampling.LANCZOS),(96,96))
    save(splash,f'{qualifier}/flytly_splash_logo.png')

def cubic(t,a,b):
    return 3*(1-t)**2*t*a + 3*(1-t)*t*t*b + t**3

def ease(x):
    lo,hi = 0.,1.
    for _ in range(48):
        mid=(lo+hi)/2
        if cubic(mid,.77,.175)<x:lo=mid
        else:hi=mid
    return cubic((lo+hi)/2,0.,1.)

source('ios-where-travel/AtlasLoadingView.swift')
frames=[]
for frame in range(96):
    phase=frame/48
    progress=ease(phase) if phase<1 else 1-ease(phase-1)
    offset=96*.27125*(1-2*progress);radius=96*.15;sigma=96*.05
    pixels=[]
    for y in range(96):
        for x in range(96):
            dx=x+.5-48;dy=y+.5-48
            a=.5*math.erfc((math.hypot(dx-offset,dy)-radius)/(sigma*math.sqrt(2)))
            b=.5*math.erfc((math.hypot(dx+offset,dy)-radius)/(sigma*math.sqrt(2)))
            pixels.append(int(min(1,max(0,(a+b)*20-8))*255))
    mask=Image.new('L',(96,96));mask.putdata(pixels)
    image=Image.new('RGBA',(96,96),'white');image.putalpha(mask);frames.append(image)
durations=[round((i+1)*1600/96)-round(i*1600/96) for i in range(96)]
animation=RES/'drawable-nodpi/flytly_metaballs.webp'
frames[0].save(animation,save_all=True,append_images=frames[1:],duration=durations,loop=0,lossless=True,minimize_size=False)
save(frames[12],'drawable-nodpi/flytly_metaball_reduced.png')
sprite=Image.new('RGBA',(96*8,96*12))
for i,frame in enumerate(frames):sprite.paste(frame,((i%8)*96,(i//8)*96))
save(sprite,'drawable-nodpi/flytly_metaball_frames.png')
license_source=source('ios-where-travel/Resources/Licenses/beUI.txt')
license_dest=ROOT/'app/src/main/assets/atlas/licenses/beUI.txt'
shutil.copyfile(license_source,license_dest)
QA.mkdir(parents=True,exist_ok=True)
manifest={'sources':SOURCES,'loader':{'frames':96,'pixels':96,'logicalSize':32,'cycleMilliseconds':sum(durations),'curve':[.77,0,.175,1],'reducedMotionFrame':12},'splash':{'canvasDP':288,'storyboardImageDP':224},'icon':'Original Icon Composer airplane alpha with rendered Default/Dark shading; Android adaptive mask and background.'}
(QA/'asset-provenance.json').write_text(json.dumps(manifest,indent=2)+'\n')
print('Imported Flytly icon, launch logo, 96-frame loader and beUI notice.')
