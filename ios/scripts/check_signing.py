#!/usr/bin/env python3
"""Fail before archive when a profile belongs to another app/team or has expired."""
import datetime
import hashlib
import os
import plistlib
import subprocess
from pathlib import Path

directory = Path(os.environ['SIGNING_DIR'])
profile = plistlib.loads((directory / 'profile.plist').read_bytes())
team = os.environ['APPLE_TEAM_ID']
bundle = os.environ.get('IOS_BUNDLE_ID') or 'ru.depocred.app'
entitlements = profile['Entitlements']
assert team in profile['TeamIdentifier'], 'Profile belongs to another Apple team'
assert entitlements['application-identifier'].split('.', 1)[1] == bundle, 'Profile Bundle ID does not match IOS_BUNDLE_ID'
assert not entitlements.get('get-task-allow', False), 'Use an App Store distribution profile'
assert not profile.get('ProvisionsAllDevices', False) and 'ProvisionedDevices' not in profile, 'Enterprise/ad-hoc profiles cannot publish to App Store'
assert profile['ExpirationDate'] > datetime.datetime.now(datetime.timezone.utc).replace(tzinfo=None), 'Provisioning profile expired'
identities = subprocess.check_output(['security','find-identity','-v','-p','codesigning',str(directory/'depocred.keychain-db')],text=True)
fingerprints = [hashlib.sha1(c).hexdigest().upper() for c in profile['DeveloperCertificates']]
assert any(f in identities for f in fingerprints), 'The .p12 private key/certificate does not match the provisioning profile'
export = {'method':'app-store-connect','destination':'export','teamID':team,'signingStyle':'manual','signingCertificate':'Apple Distribution','provisioningProfiles':{bundle:profile['UUID']},'uploadSymbols':True,'manageAppVersionAndBuildNumber':False,'stripSwiftSymbols':True}
(directory/'ExportOptions.plist').write_bytes(plistlib.dumps(export))
print('App Store signing profile and certificate match the configured app/team.')
