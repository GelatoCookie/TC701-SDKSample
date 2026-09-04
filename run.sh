#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$ROOT_DIR"

GRADLEW="bash ./gradlew"
APP_ID="com.zebra.rfid.demo.sdksample"
MAIN_ACTIVITY=".MainActivity"

run_gradle() {
  $GRADLEW "$@"
}

launch_app() {
  if ! command -v adb >/dev/null 2>&1; then
    echo "adb is required to launch the app after deploy" >&2
    exit 1
  fi

  adb shell am start -n "${APP_ID}/${APP_ID}${MAIN_ACTIVITY}"
}

mode="${1:-run}"

case "$mode" in
  clean)
    run_gradle clean
    ;;
  build)
    run_gradle assembleDebug
    ;;
  deploy)
    run_gradle installDebug
    ;;
  run)
    run_gradle installDebug
    launch_app
    ;;
  all)
    run_gradle clean assembleDebug installDebug
    launch_app
    ;;
  debug)
    run_gradle clean assembleDebug
    ;;
  release)
    run_gradle clean assembleRelease
    ;;
  *)
    echo "Usage: $(basename "$0") [clean|build|deploy|run|all|debug|release]" >&2
    exit 1
    ;;
esac
