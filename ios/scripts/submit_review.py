#!/usr/bin/env python3
"""Submit exactly the IPA recorded by release.sh. Only called with explicit review opt-in."""
import argparse
import json
import os
from pathlib import Path
import subprocess
import tempfile
import time
import urllib.parse
import urllib.request


def command(summary, key_path, screenshots=None):
    if summary['bundle_id'] != 'com.depocred.app' or str(summary['app_store_id']) != '6766194530':
        raise ValueError('Unexpected Apple application')
    args = ['fastlane', 'deliver', '--api_key_path', str(key_path),
            '--app_identifier', summary['bundle_id'], '--app_version', summary['version'],
            '--build_number', summary['build'], '--skip_binary_upload', 'true',
            '--metadata_path', 'ios/fastlane/metadata', '--submit_for_review', 'true',
            '--automatic_release', 'true', '--phased_release', 'false', '--force', 'true',
            '--precheck_include_in_app_purchases', 'false']
    if screenshots:
        args += ['--screenshots_path', str(screenshots), '--overwrite_screenshots', 'true']
    else:
        args += ['--skip_screenshots', 'true']
    return args


def wait_for_build(summary):
    import jwt
    for _ in range(40):
        now = int(time.time())
        token = jwt.encode({'iss': os.environ['APP_STORE_CONNECT_ISSUER_ID'], 'iat': now,
                            'exp': now + 120, 'aud': 'appstoreconnect-v1'},
                           os.environ['APP_STORE_CONNECT_PRIVATE_KEY'], algorithm='ES256',
                           headers={'kid': os.environ['APP_STORE_CONNECT_KEY_ID'], 'typ': 'JWT'})
        query = urllib.parse.urlencode({'filter[app]': summary['app_store_id'],
                                       'filter[version]': summary['build'], 'include': 'preReleaseVersion'})
        req = urllib.request.Request('https://api.appstoreconnect.apple.com/v1/builds?' + query,
                                     headers={'Authorization': 'Bearer ' + token})
        with urllib.request.urlopen(req, timeout=60) as response:
            data = json.load(response)
        for build in data['data']:
            state = build['attributes']['processingState']
            if state in ('FAILED', 'INVALID'):
                raise RuntimeError('Apple rejected binary processing: ' + state)
            if state == 'VALID':
                ref = build['relationships']['preReleaseVersion']['data']['id']
                version = next(v for v in data.get('included', []) if v['id'] == ref and v['type'] == 'preReleaseVersions')
                if version['attributes']['version'] != summary['version']:
                    raise RuntimeError('Build marketing version mismatch')
                return
        print('Waiting for Apple build processing...', flush=True)
        time.sleep(30)
    raise RuntimeError('Apple processing timeout; build retained in TestFlight, review not submitted')


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--screenshots', type=Path)
    parser.add_argument('--check-only', action='store_true')
    args = parser.parse_args()
    summary = json.loads(Path('ios/build/release-summary.json').read_text())
    command(summary, 'unused.json', args.screenshots)  # Validate identity before networking.
    if args.check_only:
        print('Submission plan validated; no Apple requests made')
        return
    if os.environ.get('SUBMIT_REVIEW') != 'true':
        raise RuntimeError('Explicit SUBMIT_REVIEW=true is required')
    wait_for_build(summary)
    with tempfile.TemporaryDirectory(prefix='depocred-review-') as folder:
        key_path = Path(folder) / 'api-key.json'
        key_path.write_text(json.dumps({'key_id': os.environ['APP_STORE_CONNECT_KEY_ID'],
                                       'issuer_id': os.environ['APP_STORE_CONNECT_ISSUER_ID'],
                                       'key': os.environ['APP_STORE_CONNECT_PRIVATE_KEY'], 'in_house': False}))
        key_path.chmod(0o600)
        subprocess.run(command(summary, key_path, args.screenshots), check=True)
    summary['public_release'] = 'submitted for review; automatic release after approval'
    Path('ios/build/release-summary.json').write_text(json.dumps(summary, indent=2)+'\n')


if __name__ == '__main__':
    main()
