#!/usr/bin/env bash
set -euo pipefail
# Retry only SDK downloads, never application tests or publication.
api="${1:-35}"
for attempt in 1 2 3; do
  if sdkmanager 'platform-tools' 'emulator' "platforms;android-$api" "system-images;android-$api;default;x86_64"; then
    # emulator-runner installs Linux runtime libraries before starting the binary.
    test -x "$ANDROID_HOME/emulator/emulator"
    test -f "$ANDROID_HOME/system-images/android-$api/default/x86_64/build.prop"
    exit 0
  fi
  echo "Android SDK download failed (attempt $attempt/3)"
  if [ "$attempt" -lt 3 ]; then sleep $((attempt * 5)); fi
done
exit 1
