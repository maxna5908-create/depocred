#!/usr/bin/env python3
"""Generate build metadata. Does not read or write signing secrets."""
import os
import re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
version = re.search(r'BUILD_VERSION\s*=\s*(\d+)', (ROOT / 'src/lib/version.ts').read_text()).group(1)
run = os.environ.get('GITHUB_RUN_NUMBER', '0')
attempt = os.environ.get('GITHUB_RUN_ATTEMPT', '1')
bundle = os.environ.get('IOS_BUNDLE_ID') or 'ru.depocred.app'
store = os.environ.get('IOS_APP_STORE_ID', '')
assert re.fullmatch(r'[A-Za-z0-9-]+(?:\.[A-Za-z0-9-]+)+', bundle), 'Invalid bundle identifier'
assert not store or store.isdecimal(), 'App Store ID must contain only digits'
assert run.isdecimal() and attempt.isdecimal()
target = ROOT / 'ios/Config/Version.xcconfig'
target.parent.mkdir(parents=True, exist_ok=True)
target.write_text(f'MARKETING_VERSION = 1.0.{version}\nCURRENT_PROJECT_VERSION = {version}.{run}.{attempt}\nDEPOCRED_BUNDLE_ID = {bundle}\nDEPOCRED_APP_STORE_ID = {store}\n')
print(f'DepoCred 1.0.{version}, build {version}.{run}.{attempt}, bundle {bundle}')
