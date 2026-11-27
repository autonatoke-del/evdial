#!/usr/bin/env python3
"""Render Google Play feature graphics from EVDial's existing vector identity.

The Android icon paths and these original SVG shapes are project-native assets.
No raster wallpaper, personal information, external artwork or downloaded font
is used. Inkscape exports an opaque 24-bit PNG at Google's 1024 x 500 size.
"""
from pathlib import Path
import subprocess
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
ANDROID = '{http://schemas.android.com/apk/res/android}'
COPY = {
    'en-US': ('SIM & SIP calling', 'EVDial — SIM and SIP calling'),
    'ru-RU': ('Звонки через SIM и SIP', 'EVDial — звонки через SIM и SIP'),
}


def icon_paths():
    vector = ET.parse(ROOT / 'src/main/res/drawable/phone_mark.xml').getroot()
    output = []
    for group in vector.findall('group'):
        x = group.get(ANDROID + 'translateX', '0')
        y = group.get(ANDROID + 'translateY', '0')
        output.append(f'<g transform="translate({x} {y})">')
        for path in group.findall('path'):
            node = ET.Element('path', {
                'fill': path.get(ANDROID + 'fillColor'),
                'd': path.get(ANDROID + 'pathData'),
            })
            output.append(ET.tostring(node, encoding='unicode'))
        output.append('</g>')
    return ''.join(output)


def graphic(subtitle, title):
    import html
    return f'''<svg xmlns="http://www.w3.org/2000/svg" width="1024" height="500" viewBox="0 0 1024 500">
<title>{html.escape(title)}</title>
<desc>Original EVDial feature graphic using the application's vector telephone and lightning mark.</desc>
<defs>
  <linearGradient id="background" x1="0" y1="1" x2="1" y2="0">
    <stop stop-color="#08171d"/><stop offset="1" stop-color="#122f34"/>
  </linearGradient>
  <radialGradient id="ambient" cx="78%" cy="50%" r="65%">
    <stop stop-color="#3b997f" stop-opacity=".22"/><stop offset="1" stop-color="#0b1b21" stop-opacity="0"/>
  </radialGradient>
  <linearGradient id="tile" x1="0" y1="0" x2="1" y2="1">
    <stop stop-color="#183e40"/><stop offset="1" stop-color="#0b191e"/>
  </linearGradient>
  <linearGradient id="edge" x1="0" y1="0" x2="1" y2="1">
    <stop stop-color="#8cebb3" stop-opacity=".75"/><stop offset="1" stop-color="#50c2d7" stop-opacity=".22"/>
  </linearGradient>
</defs>
<rect width="1024" height="500" fill="url(#background)"/>
<rect width="1024" height="500" fill="url(#ambient)"/>
<!-- Quiet circuit traces are decoration, not a screenshot or a product control. -->
<g fill="none" stroke="#70dabb" stroke-width="1.5" opacity=".13">
  <path d="M541 -30 V73 Q541 96 564 96 H958 Q980 96 980 118 V533"/>
  <path d="M590 -30 V28 Q590 48 612 48 H879 Q902 48 902 26 V-30"/>
  <path d="M577 500 V412 Q577 389 601 389 H957 Q980 389 980 365 V294"/>
  <path d="M1024 437 H714 Q692 437 692 460 V500"/>
  <circle cx="541" cy="75" r="4"/><circle cx="577" cy="412" r="4"/>
</g>
<text x="76" y="248" font-family="DejaVu Sans, sans-serif" font-size="98" font-weight="700" letter-spacing="-3">
  <tspan fill="#a5f9c4">EV</tspan><tspan fill="#f1fbff">Dial</tspan>
</text>
<text x="80" y="309" font-family="DejaVu Sans, sans-serif" font-size="28" font-weight="400" fill="#bdd6db">{html.escape(subtitle)}</text>
<path d="M81 351 H143" stroke="#79f4a6" stroke-width="4" stroke-linecap="round"/>
<g transform="translate(659 116) scale(2.5)">
  <rect x="0" y="0" width="108" height="108" rx="24" fill="url(#tile)"/>
  <rect x=".4" y=".4" width="107.2" height="107.2" rx="23.6" fill="none" stroke="url(#edge)" stroke-width=".8"/>
  {icon_paths()}
</g>
</svg>
'''


def main():
    for locale, (subtitle, title) in COPY.items():
        folder = ROOT / 'play/metadata' / locale / 'images'
        folder.mkdir(parents=True, exist_ok=True)
        source = folder / 'featureGraphic.svg'
        target = folder / 'featureGraphic.png'
        source.write_text(graphic(subtitle, title))
        subprocess.run([
            'inkscape', str(source), '--export-type=png',
            '--export-filename=' + str(target), '--export-width=1024',
            '--export-height=500', '--export-png-color-mode=RGB_8',
            '--export-background=#08171d', '--export-background-opacity=1',
        ], check=True)
        print(target.relative_to(ROOT))


if __name__ == '__main__':
    main()
