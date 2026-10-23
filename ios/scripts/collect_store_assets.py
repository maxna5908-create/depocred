#!/usr/bin/env python3
"""Collect named XCTest screenshots/PDFs without altering their pixels or content."""
import hashlib
import json
import re
import shutil
import struct
import sys
from pathlib import Path

source, target = map(Path, sys.argv[1:3])
manifest = source / 'manifest.json'
if not manifest.exists():
    print('No test attachments available.')
    raise SystemExit(0)
target.mkdir(parents=True, exist_ok=True)
index = []
for test in json.loads(manifest.read_text()):
    for attachment in test.get('attachments', []):
        label = attachment.get('suggestedHumanReadableName', '')
        match = re.match(r'((?:\d{2}-|report-)[a-z0-9-]+)(?:_|\.)', label)
        if not match:
            continue
        file = source / attachment['exportedFileName']
        if file.suffix not in ('.png', '.pdf'):
            continue
        output = target / (match[1] + file.suffix)
        shutil.copyfile(file, output)
        data = output.read_bytes()
        item = {'file': output.name, 'device': attachment.get('deviceName'), 'sha256': hashlib.sha256(data).hexdigest()}
        if file.suffix == '.png':
            if data[:8] != b'\x89PNG\r\n\x1a\n':
                raise ValueError('Invalid PNG attachment')
            item['width'], item['height'] = struct.unpack('>II', data[16:24])
        index.append(item)
(target / 'manifest.json').write_text(json.dumps(index, ensure_ascii=False, indent=2) + '\n')
print(f'Collected {len(index)} screenshots/PDFs.')
