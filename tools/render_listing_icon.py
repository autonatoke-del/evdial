#!/usr/bin/env python3
"""Render F-Droid's listing icon from EVDial's existing Android vector source.

Uses Inkscape to rasterize SVG; no downloaded artwork or font is required.
The Android vector and this conversion are part of EVDial's AGPL-3.0 code.
"""
from pathlib import Path
import subprocess
import tempfile
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
A = '{http://schemas.android.com/apk/res/android}'


def main():
    vector = ET.parse(ROOT / 'src/main/res/drawable/phone_mark.xml').getroot()
    paths = []
    for group in vector.findall('group'):
        x, y = group.get(A + 'translateX', '0'), group.get(A + 'translateY', '0')
        paths.append(f'<g transform="translate({x} {y})">')
        for path in group.findall('path'):
            node = ET.Element('path', {'fill': path.get(A + 'fillColor'),
                                       'd': path.get(A + 'pathData')})
            paths.append(ET.tostring(node, encoding='unicode'))
        paths.append('</g>')
    gradient = ET.parse(ROOT / 'src/main/res/drawable/phone_tile.xml').getroot().find('gradient')
    start, end = gradient.get(A + 'startColor'), gradient.get(A + 'endColor')
    svg = f'''<svg xmlns="http://www.w3.org/2000/svg" width="512" height="512" viewBox="0 0 108 108">
<defs><linearGradient id="bg" x1="0" y1="0" x2="1" y2="1"><stop stop-color="{start}"/><stop offset="1" stop-color="{end}"/></linearGradient></defs>
<rect width="108" height="108" rx="22" fill="url(#bg)"/>
{''.join(paths)}
</svg>'''
    output = ROOT / 'fastlane/metadata/android/en-US/images/icon.png'
    output.parent.mkdir(parents=True, exist_ok=True)
    with tempfile.TemporaryDirectory(prefix='evdial-icon-') as folder:
        source = Path(folder) / 'icon.svg'
        source.write_text(svg)
        subprocess.run(['inkscape', str(source), '--export-type=png',
                        '--export-filename=' + str(output), '--export-width=512',
                        '--export-height=512'], check=True)
    print(output.relative_to(ROOT))


if __name__ == '__main__':
    main()
