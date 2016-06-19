#!/bin/sh

if [[ $# -ne 1 ]]; then
  echo "Supply keystore as the sole argument to the script."
  exit 1
fi

REPO_DIR="$( cd "$( dirname "$0" )" && pwd )"

UNSIGNED="$REPO_DIR/telecine/build/outputs/apk/telecine-release-unsigned.apk"
UNALIGNED="$REPO_DIR/telecine/build/outputs/apk/telecine-release-unaligned.apk"
FINAL="$REPO_DIR/telecine/build/outputs/apk/telecine-release.apk"

if [[ $(git status 2> /dev/null | tail -n1) != "nothing to commit, working directory clean" ]]; then
  echo "Working directory dirty. Please revert or commit."
  exit 1
fi

set -ex

$REPO_DIR/gradlew -p "$REPO_DIR" clean assemble -Dpre-dex=false

jarsigner -sigalg SHA1withRSA -digestalg SHA1 -sigfile CERT -keystore "$1" -signedjar "$UNALIGNED" "$UNSIGNED" jakewharton

$ANDROID_HOME/build-tools/24.0.0/zipalign 4 "$UNALIGNED" "$FINAL"

open "$REPO_DIR/telecine/build/outputs/apk"
