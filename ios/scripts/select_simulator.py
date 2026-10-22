#!/usr/bin/env python3
import json
import subprocess
import sys

kind = sys.argv[1] if len(sys.argv) > 1 else 'phone'
data = json.loads(subprocess.check_output(['xcrun', 'simctl', 'list', 'devices', 'available', '--json']))
candidates = [(runtime, device) for runtime, devices in data['devices'].items() if '.iOS-' in runtime for device in devices if device['isAvailable'] and device['name'].startswith('iPhone' if kind == 'phone' else 'iPad')]
def rank(item):
    runtime, device = item
    version = tuple(int(v) for v in runtime.split('iOS-')[-1].split('-'))
    name = device['name']
    return (version, ('Pro' in name and 'Max' not in name if kind == 'phone' else 'Pro' in name and '13' in name), name)
if not candidates:
    raise SystemExit('No available iOS simulator. Install an iOS runtime in Xcode.')
runtime, selected = max(candidates, key=rank)
print('SIMULATOR_ID=' + selected['udid'])
print('SIMULATOR_NAME=' + selected['name'])
