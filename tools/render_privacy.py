#!/usr/bin/env python3
"""Render the repository privacy policies as a static, script-free public page.

Uses only Python's standard library. Relative source links resolve to the public
repository; no analytics, remote fonts or other subresources are included.
"""
import argparse
import html
from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[1]
SOURCE_BASE = 'https://github.com/autonatoke-del/evdial/blob/main/'
TOKEN = re.compile(r'\[([^\]]+)\]\(([^)]+)\)|`([^`]+)`|\*\*([^*]+)\*\*')


def inline(value):
    parts = []
    start = 0
    for match in TOKEN.finditer(value):
        parts.append(html.escape(value[start:match.start()]))
        label, target, code, bold = match.groups()
        if target is not None:
            if not target.startswith('https://'):
                if ':' in target or target.startswith('/') or '..' in Path(target).parts:
                    raise ValueError('Unexpected policy link: ' + target)
                target = SOURCE_BASE + target
            parts.append('<a href="' + html.escape(target, quote=True) + '">' + html.escape(label) + '</a>')
        elif code is not None:
            parts.append('<code>' + html.escape(code) + '</code>')
        else:
            parts.append('<strong>' + html.escape(bold) + '</strong>')
        start = match.end()
    parts.append(html.escape(value[start:]))
    return ''.join(parts)


def body(markdown):
    output = []
    for block in markdown.strip().split('\n\n'):
        lines = block.splitlines()
        if len(lines) == 1 and lines[0].startswith('#'):
            title = re.fullmatch(r'(#{1,3}) (.+)', lines[0])
            if not title:
                raise ValueError('Unsupported policy heading')
            level = len(title[1])
            output.append(f'<h{level}>' + inline(title[2]) + f'</h{level}>')
        elif all(line.startswith('|') and line.endswith('|') for line in lines):
            rows = [[cell.strip() for cell in line.strip('|').split('|')] for line in lines]
            if len(rows) < 3 or not all(re.fullmatch(r':?-+:?', cell) for cell in rows[1]):
                raise ValueError('Unsupported policy table')
            output.append('<div class="table-scroll"><table><thead><tr>'
                          + ''.join('<th scope="col">' + inline(cell) + '</th>' for cell in rows[0])
                          + '</tr></thead><tbody>')
            for row in rows[2:]:
                output.append('<tr>' + ''.join('<td>' + inline(cell) + '</td>' for cell in row) + '</tr>')
            output.append('</tbody></table></div>')
        else:
            output.append('<p>' + inline(' '.join(lines)) + '</p>')
    return '\n'.join(output)


def render():
    english = body((ROOT / 'PRIVACY.md').read_text())
    russian = ROOT / 'PRIVACY.ru.md'
    translations = ''
    navigation = ''
    if russian.exists():
        navigation = '<nav aria-label="Language"><a href="#english">English</a> · <a href="#russian" lang="ru">Русский</a></nav>'
        translations = '<hr><section id="russian" lang="ru">' + body(russian.read_text()) + '</section>'
    return '''<!doctype html>
<html lang="en"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<meta name="description" content="EVDial privacy policy: contacts, calls, SIP, messages, files and updates.">
<title>EVDial · Privacy Policy</title>
<style>
:root{color-scheme:dark;--bg:#071820;--text:#edf6f8;--muted:#b4c9d1;--accent:#83dec4;--line:#29434c}
*{box-sizing:border-box}body{margin:0;background:var(--bg);color:var(--text);font:17px/1.65 system-ui,sans-serif}
main{max-width:900px;margin:auto;padding:28px 24px 70px}header{display:flex;justify-content:space-between;gap:20px;flex-wrap:wrap;padding:12px 0 28px;border-bottom:1px solid var(--line)}
header>a{font-weight:750;font-size:24px;text-decoration:none}a{color:var(--accent);text-underline-offset:3px;overflow-wrap:anywhere}a:focus-visible{outline:2px solid var(--accent);outline-offset:4px}
h1{font-size:clamp(28px,5vw,40px);line-height:1.2;margin:34px 0 24px}h2{font-size:23px;line-height:1.3;margin:36px 0 14px}p{margin:0 0 18px}code{font-size:.92em;overflow-wrap:anywhere}table{width:100%;border-collapse:collapse;font-size:15px}th,td{text-align:left;padding:12px 14px;border:1px solid var(--line);vertical-align:top}th{color:var(--accent)}.table-scroll{overflow-x:auto}hr{border:0;border-top:1px solid var(--line);margin:50px 0}footer{margin-top:40px;color:var(--muted);font-size:14px}
</style></head><body><main><header><a href="/evdial/">EVDial</a>''' + navigation + '''</header>
<section id="english">''' + english + '</section>' + translations + '''
<footer><a href="https://github.com/autonatoke-del/evdial/blob/main/PRIVACY.md">Policy source</a> · <a href="https://github.com/autonatoke-del/evdial/issues">Contact the project</a></footer>
</main></body></html>
'''


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--check', action='store_true')
    args = parser.parse_args()
    output = ROOT / 'web/privacy/index.html'
    rendered = render()
    if args.check:
        if not output.exists() or output.read_text() != rendered:
            raise SystemExit('Privacy page differs from the policy source; run tools/render_privacy.py')
    else:
        output.parent.mkdir(parents=True, exist_ok=True)
        output.write_text(rendered)
    print(output.relative_to(ROOT))


if __name__ == '__main__':
    main()
