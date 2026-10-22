#!/usr/bin/env python3
"""Rebuild the iOS icons from the exact Android Lucide vector paths.
Development-only requirements: CairoSVG 2.7.1 and Pillow. Generated assets are committed.
"""
import json
from pathlib import Path
from xml.etree import ElementTree as ET
import cairosvg
from PIL import Image

ROOT = Path(__file__).resolve().parents[2]
ASSETS = ROOT / 'ios/DepoCred/Resources/Assets.xcassets'
ANDROID = '{http://schemas.android.com/apk/res/android}'
INFO = {'author': 'xcode', 'version': 1}

def write_json(path, obj):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(obj, ensure_ascii=False, indent=2) + '\n')

write_json(ASSETS / 'Contents.json', {'info': INFO})
for source in sorted((ROOT / 'android/app/src/main/res/drawable').glob('lucide_*.xml')):
    root = ET.parse(source).getroot()
    paths = ''.join(f'<path d="{p.attrib[ANDROID + "pathData"]}" fill="none" stroke="white" stroke-width="{p.attrib.get(ANDROID + "strokeWidth", "1.8")}" stroke-linecap="round" stroke-linejoin="round"/>' for p in root.iter('path'))
    svg = f'<svg xmlns="http://www.w3.org/2000/svg" width="24" height="24" viewBox="0 0 24 24">{paths}</svg>'
    folder = ASSETS / (source.stem + '.imageset')
    folder.mkdir(parents=True, exist_ok=True)
    cairosvg.svg2pdf(bytestring=svg.encode(), write_to=str(folder / 'icon.pdf'))
    write_json(folder / 'Contents.json', {'images': [{'filename': 'icon.pdf', 'idiom': 'universal'}], 'info': INFO, 'properties': {'preserves-vector-representation': True, 'template-rendering-intent': 'template'}})
    if source.stem == 'lucide_coins':
        icon_svg = f'<svg xmlns="http://www.w3.org/2000/svg" width="1024" height="1024" viewBox="0 0 48 48"><rect width="48" height="48" fill="#F59E0B"/><g transform="translate(12 12)">{paths}</g></svg>'
        folder = ASSETS / 'AppIcon.appiconset'
        folder.mkdir(parents=True, exist_ok=True)
        target = folder / 'AppIcon.png'
        cairosvg.svg2png(bytestring=icon_svg.encode(), write_to=str(target))
        with Image.open(target) as image: image.convert('RGB').save(target)
        write_json(folder / 'Contents.json', {'images': [{'filename': 'AppIcon.png', 'idiom': 'universal', 'platform': 'ios', 'size': '1024x1024'}], 'info': INFO})

write_json(ASSETS / 'StartupOrange.colorset/Contents.json', {'colors': [{'idiom': 'universal', 'color': {'color-space': 'srgb', 'components': {'red': '0.960784', 'green': '0.619608', 'blue': '0.043137', 'alpha': '1.000'}}}], 'info': INFO})
