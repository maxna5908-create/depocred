#!/usr/bin/env python3
"""Report presence only; never print or persist secret values."""
import os
import sys

NAMES = (
    'APPLE_TEAM_ID', 'APPLE_CERTIFICATE_BASE64', 'APPLE_CERTIFICATE_PASSWORD',
    'APPLE_PROVISIONING_PROFILE_BASE64', 'APP_STORE_CONNECT_KEY_ID',
    'APP_STORE_CONNECT_ISSUER_ID', 'APP_STORE_CONNECT_PRIVATE_KEY', 'IOS_APP_STORE_ID',
)
missing = [name for name in NAMES if not os.environ.get(name, '').strip()]
lines = ['## Apple configuration', '', 'Values are never included in this report.', '']
lines += [f'- {name}: {"missing" if name in missing else "configured"}' for name in NAMES]
lines += ['', 'Presence alone does not validate Apple credentials. Signing and Apple validation run during release.']
if missing:
    lines += ['Repository-level configuration is incomplete. See ios/docs/apple-setup.md. Environment-only secrets are checked by the release job.']
report = '\n'.join(lines) + '\n'
print(report)
if os.environ.get('GITHUB_STEP_SUMMARY'):
    with open(os.environ['GITHUB_STEP_SUMMARY'], 'a') as output:
        output.write(report)
if missing and '--report-only' not in sys.argv:
    raise SystemExit(1)
