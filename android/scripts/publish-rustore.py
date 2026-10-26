"""Publish verified native Android artifacts to RuStore; never print credentials."""
import argparse
import base64
import datetime
import json
import os
from pathlib import Path
import sys
import time

import requests
from cryptography.hazmat.primitives import hashes, serialization
from cryptography.hazmat.primitives.asymmetric import padding
from PIL import Image, ImageOps

BASE = 'https://public-api.rustore.ru'
PACKAGE = 'ru.depocred.app'
APP_ID = 2063712429


def signature(key_id, content):
    raw = content.strip().encode()
    if not raw.startswith(b'-----BEGIN'):
        raw = base64.b64decode(raw, validate=False)
    if raw.startswith(b'-----BEGIN'):
        key = serialization.load_pem_private_key(raw, password=None)
    else:
        key = serialization.load_der_private_key(raw, password=None)
    timestamp = datetime.datetime.now(datetime.timezone.utc).isoformat(timespec='milliseconds')
    signed = key.sign((key_id + timestamp).encode(), padding.PKCS1v15(), hashes.SHA512())
    return {'keyId': key_id, 'timestamp': timestamp, 'signature': base64.b64encode(signed).decode()}


class API:
    def __init__(self):
        self.session = requests.Session()
        self.expires = 0
        self.token = ''

    def request(self, method, path, **kwargs):
        if time.monotonic() >= self.expires:
            key_id = os.environ.get('RUSTORE_KEY_ID', '').strip()
            private = os.environ.get('RUSTORE_PRIVATE_KEY', '').strip()
            if not key_id or not private:
                raise RuntimeError('Missing RUSTORE_KEY_ID or RUSTORE_PRIVATE_KEY repository secret')
            try:
                payload = signature(key_id, private)
            except Exception:
                raise RuntimeError('RUSTORE_PRIVATE_KEY must contain the complete PEM or Base64 RSA private key') from None
            r = self.session.post(BASE + '/public/auth/', json=payload, timeout=60, allow_redirects=False)
            try:
                result = r.json()
            except ValueError:
                raise RuntimeError(f'RuStore authorization HTTP {r.status_code}, non-JSON response') from None
            if not r.ok or result.get('code') != 'OK':
                raise RuntimeError(f'RuStore authorization failed (HTTP {r.status_code}); check key ID, private key and key permissions')
            self.token = result['body']['jwe']
            if os.environ.get('GITHUB_ACTIONS'):
                print('::add-mask::' + self.token, flush=True)
            self.expires = time.monotonic() + min(780, int(result['body']['ttl']) - 60)
            print('RuStore authorization successful', flush=True)
        # Do not retry mutations: a lost response may already have created/submitted a draft.
        response = self.session.request(method, BASE + path, headers={'Public-Token': self.token},
                                        timeout=(30, 300), allow_redirects=False, **kwargs)
        try:
            result = response.json()
        except ValueError:
            raise RuntimeError(f'{method} {path}: HTTP {response.status_code}, non-JSON response') from None
        if not response.ok or result.get('code') != 'OK':
            message = str(result.get('message', ''))[:1500].replace(self.token, '[redacted]')
            raise RuntimeError(f'{method} {path}: HTTP {response.status_code}: {message}')
        return result.get('body')

    def upload(self, path, file, mime):
        with file.open('rb') as stream:
            return self.request('POST', path, files={'file': (file.name, stream, mime)})


def metadata(root):
    fields = {'appName': ('title.txt', 50), 'shortDescription': ('short_description.txt', 80),
              'fullDescription': ('full_description.txt', 4000), 'whatsNew': ('changelogs/default.txt', 5000)}
    result = {}
    for name, (filename, limit) in fields.items():
        value = (root / filename).read_text(encoding='utf-8').strip()
        if not 0 < len(value) <= limit:
            raise ValueError(f'{filename}: invalid length')
        result[name] = value
    result.update(publishType='INSTANTLY', partialValue=100,
                  moderInfo='Нативное приложение. Кредит, вклад, лизинг, история и PDF. Работает без регистрации и интернета; тестовый аккаунт не нужен.',
                  privacyDataTypes=[], packageSdkDto={'adsSdks': [], 'paymentSdks': []}, inAppPurchaseTypes=[])
    return result


def prepare_images(root, output):
    images = []
    for folder, kind in [('phoneScreenshots', 'SCREENSHOT'), ('tenInchScreenshots', 'TABLET_SCREENSHOT')]:
        files = sorted((root / 'ru-RU/images' / folder).glob('*.jpg'))
        if len(files) != 8:
            raise ValueError(f'Expected eight verified {folder}, found {len(files)}')
        for index, source in enumerate(files):
            with Image.open(source) as im:
                im = im.convert('RGB')
                # RuStore requires 9:16. Preserve the full tablet creative without cropping/stretching.
                size = (1080, 1920) if kind == 'SCREENSHOT' else (1440, 2560)
                im = ImageOps.pad(im, size, method=Image.Resampling.LANCZOS, color=im.getpixel((0, 0)))
                target = output / kind / source.name
                target.parent.mkdir(parents=True, exist_ok=True)
                im.save(target, quality=96, subsampling=0)
            if target.stat().st_size > 5 * 1024 * 1024:
                raise ValueError(f'{target}: exceeds RuStore 5 MB limit')
            images.append((index, kind, target))
    return images


def publish(args):
    payload = metadata(args.metadata)
    images = prepare_images(args.images, Path('rustore-images'))
    if not args.apk.is_file():
        raise ValueError('Signed APK is missing')
    api = API()
    prefix = f'/public/v1/application/{PACKAGE}/version'
    versions = []
    page = 0
    while True:
        body = api.request('GET', prefix, params={'page': page, 'size': 100})
        versions.extend(body['content'])
        page += 1
        if page >= body['totalPages']:
            break
    print('Existing versions: ' + json.dumps([{k: v.get(k) for k in ('versionId', 'versionName', 'versionCode', 'versionStatus', 'publishType')} for v in versions], ensure_ascii=False), flush=True)
    already = [v for v in versions if int(v.get('versionCode') or 0) == args.version_code and v['versionStatus'] in ('ACTIVE', 'PARTIAL_ACTIVE', 'TAKEN_FOR_MODERATION', 'MODERATION', 'AUTO_CHECK', 'READY_FOR_PUBLICATION')]
    if already:
        print('This version is already submitted: ' + json.dumps(already[0], ensure_ascii=False))
        return
    active = [v for v in versions if v['versionStatus'] in ('ACTIVE', 'PARTIAL_ACTIVE')]
    if not active or args.version_code <= max(int(v['versionCode']) for v in active):
        raise RuntimeError('APK versionCode must be higher than the active RuStore version')
    current = max(active, key=lambda v: int(v['versionCode']))
    detail = api.request('GET', f'/public/v2/application/{PACKAGE}/version/{current["versionId"]}')
    if int(detail['appId']) != APP_ID:
        raise RuntimeError('Unexpected RuStore application ID; stopped before modifying anything')
    drafts = [v for v in versions if v['versionStatus'] == 'DRAFT']
    if args.resume_version:
        if not any(int(v['versionId']) == args.resume_version for v in drafts):
            raise RuntimeError('resume_version is not a current draft')
        version = args.resume_version

    else:
        if drafts:
            raise RuntimeError('Existing draft preserved. Inspect it and pass its versionId as resume_version; creating a new draft could delete it.')
        version = api.request('POST', prefix, json=payload)
        if isinstance(version, dict):
            version = version['versionId']
        version = int(version)
    print(f'RuStore draft versionId={version}', flush=True)
    Path('rustore-result.json').write_text(json.dumps({'versionId': version, 'versionCode': args.version_code, 'publishType': 'INSTANTLY', 'partialValue': 100, 'submitted': False}))
    draft = api.request('GET', f'/public/v2/application/{PACKAGE}/version/{version}')
    if args.resume_version and any(draft.get(k) != payload[k] for k in ('appName', 'shortDescription', 'fullDescription', 'whatsNew', 'publishType')):
        api.request('PATCH', f'/public/v2/application/{PACKAGE}/version/{version}', json=payload)
    print('Draft publication settings: ' + json.dumps({k: draft.get(k) for k in ('versionId', 'versionName', 'versionStatus', 'publishType', 'partialValue', 'packages')}, ensure_ascii=False), flush=True)
    packages = draft.get('packages') or []
    listed_draft = next((v for v in drafts if int(v['versionId']) == version), {})
    listed_code = int(listed_draft.get('versionCode') or 0)
    if not packages and args.resume_version and listed_code == args.version_code:
        # API-uploaded APKs can remain absent from v2 packages until commit.
        print('Version status confirms the previously accepted APK; keeping it', flush=True)
    elif packages:
        # Resume only an identical version; never replace a manually uploaded APK silently.
        if not all(int(p.get('verCode', -1)) == args.version_code for p in packages):
            raise RuntimeError('Draft contains a different/unknown APK version; preserved for inspection')
        print('Draft already contains the requested APK version; keeping it', flush=True)
    else:
        api.upload(f'{prefix}/{version}/apk?isMainApk=true&servicesType=Unknown', args.apk, 'application/vnd.android.package-archive')
        print('Signed APK accepted by RuStore', flush=True)
    def screenshot_state():
        state = api.request('GET', f'/public/v2/application/{PACKAGE}/version/{version}/image/screenshot', params={'size': 100, 'page': 0})
        print('Stored screenshots: ' + json.dumps([{k: item.get(k) for k in ('id', 'ordinal', 'type', 'orientation')} for item in state['content']], ensure_ascii=False), flush=True)
        return state['content']
    screenshot_state()
    for index, kind, file in sorted(images, key=lambda image: image[1] == 'SCREENSHOT'):
        # Use the established phone endpoint; v2 is required for tablet screenshots.
        if kind == 'SCREENSHOT':
            endpoint = f'{prefix}/{version}/image/screenshot/PORTRAIT/{index}'
        else:
            endpoint = f'/public/v2/application/{PACKAGE}/version/{version}/image/screenshot/PORTRAIT/{index}/{kind}'
        uploaded = api.upload(endpoint, file, 'image/jpeg')
        print(f'Uploaded {kind} {index + 1}/8: ' + json.dumps({k: uploaded.get(k) for k in ('id', 'ordinal', 'type')} if isinstance(uploaded, dict) else uploaded), flush=True)
    stored = screenshot_state()
    counts = {kind: sum(item.get('type') == kind for item in stored) for kind in ('SCREENSHOT', 'TABLET_SCREENSHOT')}
    if any(count < 8 for count in counts.values()):
        raise RuntimeError('RuStore screenshot persistence check failed: ' + str(counts))
    draft = api.request('GET', f'/public/v2/application/{PACKAGE}/version/{version}')
    for key in ('appName', 'shortDescription', 'fullDescription', 'publishType'):
        if draft.get(key) != payload[key]:
            raise RuntimeError(f'Draft verification failed for {key}; not submitted')
    # RuStore status uses -1 for 100% (documented in the English API reference).
    # The v2 detail response may omit rollout; verify against the version status endpoint.
    status = api.request('GET', prefix, params={'ids': version})['content'][0]
    print('Verified publication settings: ' + json.dumps({k: status.get(k) for k in ('versionId', 'publishType', 'partialValue')}, ensure_ascii=False), flush=True)
    if status.get('publishType') != 'INSTANTLY':
        raise RuntimeError('Automatic publication was not persisted')
    if int(status.get('partialValue') or 0) not in (100, -1):
        api.request('POST', f'{prefix}/{version}/publish-settings', json={'partialValue': 100})
        status = api.request('GET', prefix, params={'ids': version})['content'][0]
        if int(status.get('partialValue') or 0) not in (100, -1):
            raise RuntimeError('100% rollout was not persisted; not submitted')
    api.request('POST', f'{prefix}/{version}/commit', params={'priorityUpdate': 0})
    result = {'versionId': version, 'versionCode': args.version_code, 'publishType': 'INSTANTLY', 'partialValue': 100, 'submitted': True}
    Path('rustore-result.json').write_text(json.dumps(result, indent=2))
    print('Submitted to RuStore moderation; automatic publication to 100% after approval.', flush=True)
    if os.environ.get('GITHUB_STEP_SUMMARY'):
        with open(os.environ['GITHUB_STEP_SUMMARY'], 'a') as stream:
            stream.write(f'RuStore: versionCode **{args.version_code}**, draft **{version}** submitted for moderation. Automatic publication after approval (100%). Eight phone and eight tablet screenshots uploaded.\n')


if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('--apk', type=Path, required=True)
    parser.add_argument('--metadata', type=Path, required=True)
    parser.add_argument('--images', type=Path, required=True)
    parser.add_argument('--version-code', type=int, required=True)
    parser.add_argument('--resume-version', type=int, default=0)
    try:
        publish(parser.parse_args())
    except Exception as exc:
        print(f'RuStore publishing stopped: {exc}', file=sys.stderr)
        sys.exit(1)
