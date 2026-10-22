#!/usr/bin/env bash
set -euo pipefail
# Secrets are passed only through the environment; never enable shell tracing.
for name in APPLE_TEAM_ID APPLE_CERTIFICATE_BASE64 APPLE_CERTIFICATE_PASSWORD APPLE_PROVISIONING_PROFILE_BASE64 APP_STORE_CONNECT_KEY_ID APP_STORE_CONNECT_ISSUER_ID APP_STORE_CONNECT_PRIVATE_KEY IOS_APP_STORE_ID; do
  if [ -z "${!name:-}" ]; then
    echo "::error::Missing $name. Follow ios/docs/apple-setup.md."
    exit 1
  fi
done
SIGNING_DIR="$(mktemp -d "$RUNNER_TEMP/depocred-signing.XXXXXX")"
export SIGNING_DIR
IOS_KEYCHAIN_PATH="$SIGNING_DIR/depocred.keychain-db"
IOS_KEYCHAIN_PASSWORD="$(openssl rand -base64 32)"
INSTALLED_PROFILE=''
cleanup() {
  security delete-keychain "$IOS_KEYCHAIN_PATH" >/dev/null 2>&1 || true
  if [ -n "$INSTALLED_PROFILE" ]; then rm -f "$INSTALLED_PROFILE"; fi
  rm -rf "$SIGNING_DIR"
}
trap cleanup EXIT
umask 077
python3 - <<'PY'
import base64,os,re
from pathlib import Path
folder=Path(os.environ['SIGNING_DIR'])
assert re.fullmatch(r'[A-Z0-9]{10}',os.environ['APPLE_TEAM_ID']), 'Invalid Apple Team ID'
assert re.fullmatch(r'[A-Z0-9]{10}',os.environ['APP_STORE_CONNECT_KEY_ID']), 'Invalid API Key ID'
assert re.fullmatch(r'[A-Fa-f0-9-]{36}',os.environ['APP_STORE_CONNECT_ISSUER_ID']), 'Invalid API Issuer ID'
for name, filename in [('APPLE_CERTIFICATE_BASE64','distribution.p12'),('APPLE_PROVISIONING_PROFILE_BASE64','profile.mobileprovision')]:
    raw=''.join(os.environ[name].split())
    (folder/filename).write_bytes(base64.b64decode(raw,validate=True))
key=os.environ['APP_STORE_CONNECT_PRIVATE_KEY'].strip()+'\n'
assert key.startswith('-----BEGIN PRIVATE KEY-----'), 'Paste the complete .p8 file into APP_STORE_CONNECT_PRIVATE_KEY'
(folder/('AuthKey_'+os.environ['APP_STORE_CONNECT_KEY_ID']+'.p8')).write_text(key)
PY
security create-keychain -p "$IOS_KEYCHAIN_PASSWORD" "$IOS_KEYCHAIN_PATH"
security set-keychain-settings -lut 21600 "$IOS_KEYCHAIN_PATH"
security unlock-keychain -p "$IOS_KEYCHAIN_PASSWORD" "$IOS_KEYCHAIN_PATH"
security import "$SIGNING_DIR/distribution.p12" -P "$APPLE_CERTIFICATE_PASSWORD" -A -t cert -f pkcs12 -k "$IOS_KEYCHAIN_PATH"
security set-key-partition-list -S apple-tool:,apple: -k "$IOS_KEYCHAIN_PASSWORD" "$IOS_KEYCHAIN_PATH" >/dev/null
security list-keychains -d user -s "$IOS_KEYCHAIN_PATH" "$HOME/Library/Keychains/login.keychain-db"
security cms -D -i "$SIGNING_DIR/profile.mobileprovision" > "$SIGNING_DIR/profile.plist"
python3 ios/scripts/check_signing.py
PROFILE_UUID="$(/usr/libexec/PlistBuddy -c 'Print UUID' "$SIGNING_DIR/profile.plist")"
mkdir -p "$HOME/Library/MobileDevice/Provisioning Profiles"
INSTALLED_PROFILE="$HOME/Library/MobileDevice/Provisioning Profiles/$PROFILE_UUID.mobileprovision"
cp "$SIGNING_DIR/profile.mobileprovision" "$INSTALLED_PROFILE"
mkdir -p ios/build
xcodebuild archive -project ios/DepoCred.xcodeproj -scheme DepoCred -configuration Release \
  -destination 'generic/platform=iOS' -archivePath ios/build/DepoCred.xcarchive \
  DEVELOPMENT_TEAM="$APPLE_TEAM_ID" CODE_SIGN_STYLE=Manual CODE_SIGN_IDENTITY='Apple Distribution' \
  PROVISIONING_PROFILE_SPECIFIER="$PROFILE_UUID" OTHER_CODE_SIGN_FLAGS="--keychain $IOS_KEYCHAIN_PATH"
xcodebuild -exportArchive -archivePath ios/build/DepoCred.xcarchive \
  -exportPath ios/build/export -exportOptionsPlist "$SIGNING_DIR/ExportOptions.plist"
IPA_PATH="$(find ios/build/export -maxdepth 1 -name '*.ipa' -print -quit)"
test -n "$IPA_PATH"
codesign --verify --deep --strict ios/build/DepoCred.xcarchive/Products/Applications/DepoCred.app
# Apple's uploader discovers API private keys here. The file is removed by the EXIT trap.
export API_PRIVATE_KEYS_DIR="$SIGNING_DIR"
xcrun altool --validate-app --type ios --file "$IPA_PATH" \
  --apiKey "$APP_STORE_CONNECT_KEY_ID" --apiIssuer "$APP_STORE_CONNECT_ISSUER_ID"
xcrun altool --upload-app --type ios --file "$IPA_PATH" \
  --apiKey "$APP_STORE_CONNECT_KEY_ID" --apiIssuer "$APP_STORE_CONNECT_ISSUER_ID"
python3 - <<'PY'
import hashlib,json,os,plistlib
from pathlib import Path
p=next(Path('ios/build/export').glob('*.ipa'))
info=plistlib.loads(Path('ios/build/DepoCred.xcarchive/Products/Applications/DepoCred.app/Info.plist').read_bytes())
Path('ios/build/release-summary.json').write_text(json.dumps({'bundle_id':info['CFBundleIdentifier'],'version':info['CFBundleShortVersionString'],'build':info['CFBundleVersion'],'sha256':hashlib.sha256(p.read_bytes()).hexdigest(),'app_store_id':os.environ['IOS_APP_STORE_ID'],'upload':'accepted; processing in App Store Connect','public_release':'not submitted'},indent=2)+'\n')
PY
echo 'Signed IPA uploaded. Check processing and TestFlight in your App Store Connect account.'
