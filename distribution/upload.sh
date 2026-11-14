#!/usr/bin/env bash
set -euo pipefail
android_dir=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)
cd "$android_dir"
./gradlew :app:assembleDebug -PkorpaDebugApiUrl=https://korpa.intelisystems.dev
firebase appdistribution:distribute app/build/outputs/apk/debug/app-debug.apk \
  --project intelisystems-potrosacka-korpa \
  --app 1:466611839381:android:7cac04bc6746340de1969c \
  --release-notes-file distribution/release-notes.txt
