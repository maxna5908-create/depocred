#!/usr/bin/env python3
"""Expose concise XCTest results without requiring the large diagnostic archive."""
import json
import os
import sys
from pathlib import Path

source = Path(sys.argv[1])
if not source.exists() or not source.stat().st_size:
    print('No XCTest summary was produced; inspect the build log.')
    raise SystemExit(0)
summary = json.loads(source.read_text())
lines = [
    '## XCTest results', '',
    f"Result: **{summary.get('result', 'Unknown')}**",
    f"Passed: {summary.get('passedTests', 0)}; failed: {summary.get('failedTests', 0)}; skipped: {summary.get('skippedTests', 0)}.",
    '',
]
for configuration in summary.get('devicesAndConfigurations', []):
    device = configuration.get('device', {})
    lines.append(f"Device: {device.get('deviceName', '?')}, iOS {device.get('osVersion', '?')}.")
for failure in summary.get('testFailures', []):
    title = failure.get('testName', 'XCTest failure')
    message = failure.get('failureText', '')
    lines += ['', f'### {title}', '', message]
    def escape(value):
        return value.replace('%', '%25').replace('\r', '%0D').replace('\n', '%0A')
    print(f'::error::{escape(title + ": " + message[:4000])}')
report = '\n'.join(lines) + '\n'
print(report)
if os.environ.get('GITHUB_STEP_SUMMARY'):
    with open(os.environ['GITHUB_STEP_SUMMARY'], 'a') as output:
        output.write(report)
