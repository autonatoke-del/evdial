#!/usr/bin/env python3
"""Check every arm64/x86_64 ELF in an APK, AAB or AAR for 16 KB alignment.

This checks artifact structure, not runtime behaviour. Also run Android's
zipalign against bundle-generated APKs and test audio on a 16 KB device.
Reference: https://developer.android.com/guide/practices/page-sizes
"""
import argparse
import hashlib
import json
from pathlib import Path
import struct
import zipfile

PAGE = 16384


def inspect_elf(data):
    if len(data) < 64 or data[:6] != b'\x7fELF\x02\x01':
        raise ValueError('Expected a complete little-endian 64-bit ELF')
    offset = struct.unpack_from('<Q', data, 32)[0]
    size, count = struct.unpack_from('<HH', data, 54)
    if size < 56 or count == 0 or offset + size * count > len(data):
        raise ValueError('Invalid ELF program-header table')
    headers = [struct.unpack_from('<IIQQQQQQ', data, offset + i * size) for i in range(count)]
    loads = [row for row in headers if row[0] == 1]
    if not loads:
        raise ValueError('No ELF LOAD segments')
    aligned = all(row[7] >= PAGE and row[2] % PAGE == row[3] % PAGE for row in loads)
    relro = []
    for row in headers:
        if row[0] != 0x6474E552:
            continue
        start, end = row[3], row[3] + row[6]
        containing = [load for load in loads if load[3] <= start and load[3] + load[6] >= end]
        safe = end % PAGE == 0 or any(load[3] + load[6] == end for load in containing)
        relro.append({'start': start, 'end': end, 'containingLoadEnds': [v[3] + v[6] for v in containing], 'pass': safe})
    return {'loadAlignments': [row[7] for row in loads], 'loadPass': aligned,
            'relro': relro, 'pass': aligned and all(row['pass'] for row in relro)}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('artifact', type=Path)
    parser.add_argument('--output', type=Path)
    args = parser.parse_args()
    findings = []
    with zipfile.ZipFile(args.artifact) as archive:
        for entry in sorted(archive.namelist()):
            if not entry.endswith('.so') or not any('/' + abi + '/' in '/' + entry for abi in ('arm64-v8a', 'x86_64')):
                continue
            data = archive.read(entry)
            finding = {'entry': entry, 'sha256': hashlib.sha256(data).hexdigest(),
                       'zipCompression': archive.getinfo(entry).compress_type}
            try:
                finding.update(inspect_elf(data))
            except (ValueError, struct.error) as error:
                finding.update({'pass': False, 'error': str(error)})
            findings.append(finding)
    report = {'artifact': args.artifact.name, 'sha256': hashlib.sha256(args.artifact.read_bytes()).hexdigest(),
              'pageBytes': PAGE, 'libraries': findings, 'runtimeTested': False,
              'pass': bool(findings) and all(row['pass'] for row in findings)}
    text = json.dumps(report, indent=2) + '\n'
    if args.output:
        args.output.write_text(text)
    print(text, end='')
    if not report['pass']:
        raise SystemExit(1)


if __name__ == '__main__':
    main()
