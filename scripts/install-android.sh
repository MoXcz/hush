#!/usr/bin/env bash
# SPDX-License-Identifier: GPL-3.0-or-later
# Builds and installs the Android app on a phone over adb.
#   scripts/install-android.sh [--debug] [--pair HOST:PORT CODE] [--connect HOST:PORT]
# Without --pair/--connect it uses ANDROID_SERIAL, else the one device adb sees, else a phone
# advertising wireless debugging (adb mdns).
set -euo pipefail

repo="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

usage() {
  echo "Usage: $0 [--debug] [--pair HOST:PORT CODE] [--connect HOST:PORT]" >&2
  exit 2
}

debug=false
pair_target=""
pair_code=""
connect_target=""
while [[ $# -gt 0 ]]; do
  case "$1" in
    --debug) debug=true; shift ;;
    --pair) [[ $# -ge 3 ]] || usage; pair_target="$2"; pair_code="$3"; shift 3 ;;
    --connect) [[ $# -ge 2 ]] || usage; connect_target="$2"; shift 2 ;;
    -h | --help) usage ;;
    *) usage ;;
  esac
done

find_adb() {
  local sdk
  for sdk in "${ANDROID_HOME:-}" "${ANDROID_SDK_ROOT:-}" "$HOME/Android/Sdk"; do
    if [[ -n "$sdk" && -x "$sdk/platform-tools/adb" ]]; then
      echo "$sdk/platform-tools/adb"
      return
    fi
  done
  command -v adb || { echo "adb not found: install platform-tools or set ANDROID_HOME." >&2; exit 1; }
}
adb="$(find_adb)"

if $debug; then
  package=app.hush.debug
  task=:androidApp:installDebug
else
  package=app.hush
  task=:androidApp:installRelease
  if [[ ! -f "$repo/keystore.properties" ]]; then
    echo "No keystore.properties at $repo: the release build would be unsigned and can't be installed." >&2
    echo "Create it as docs/building.md (\"Signing the release\") describes, or use --debug." >&2
    exit 1
  fi
fi

if [[ -n "$pair_target" ]]; then
  "$adb" pair "$pair_target" "$pair_code"
fi

# One line per phone: a wireless phone can be listed twice (by address and by mDNS name).
online_devices() {
  local device serialno
  local -A seen=()
  for device in $("$adb" devices | awk 'NR > 1 && $2 == "device" { print $1 }'); do
    serialno="$("$adb" -s "$device" shell getprop ro.serialno 2>/dev/null || true)"
    serialno="${serialno:-$device}"
    if [[ -z "${seen[$serialno]:-}" ]]; then
      seen[$serialno]=1
      echo "$device"
    fi
  done
}

if [[ -n "$connect_target" ]]; then
  "$adb" connect "$connect_target"
  serial="$connect_target"
elif [[ -n "${ANDROID_SERIAL:-}" ]]; then
  serial="$ANDROID_SERIAL"
else
  mapfile -t devices < <(online_devices)
  if [[ ${#devices[@]} -eq 0 ]]; then
    # Wireless debugging advertises _adb-tls-connect._tcp once the phone was paired.
    # The adb server may need a moment to collect mDNS answers.
    found=()
    for _ in 1 2 3; do
      mapfile -t found < <("$adb" mdns services | awk '$2 ~ /_adb-tls-connect\._tcp/ { print $3 }' | sort -u)
      [[ ${#found[@]} -gt 0 ]] && break
      sleep 1
    done
    if [[ ${#found[@]} -eq 1 ]]; then
      "$adb" connect "${found[0]}"
      devices=("${found[0]}")
    elif [[ ${#found[@]} -gt 1 ]]; then
      echo "Several phones offer wireless debugging: ${found[*]}. Pick one with --connect." >&2
      exit 1
    fi
  fi
  if [[ ${#devices[@]} -eq 0 ]]; then
    echo "No device found. Plug in the phone, or turn on wireless debugging and use --pair/--connect." >&2
    exit 1
  elif [[ ${#devices[@]} -gt 1 ]]; then
    echo "Several devices: ${devices[*]}. Set ANDROID_SERIAL or use --connect." >&2
    exit 1
  fi
  serial="${devices[0]}"
fi

# A device just connected or paired can take a moment to come online.
timeout 20 "$adb" -s "$serial" wait-for-device || true
if [[ "$("$adb" -s "$serial" get-state 2>/dev/null)" != device ]]; then
  echo "Device $serial isn't ready (check that it's unlocked and debugging is allowed)." >&2
  exit 1
fi
echo "Installing $package on $serial"

(cd "$repo" && ANDROID_SERIAL="$serial" ./gradlew "$task")

# Lets the blocking switch in Settings turn the blocker back on.
if ! "$adb" -s "$serial" shell pm grant "$package" android.permission.WRITE_SECURE_SETTINGS; then
  echo "Warning: couldn't grant WRITE_SECURE_SETTINGS; the blocking switch can turn blocking off but not on." >&2
fi

"$adb" -s "$serial" logcat -b crash -c || true
"$adb" -s "$serial" shell monkey -p "$package" -c android.intent.category.LAUNCHER 1 >/dev/null 2>&1
for _ in $(seq 15); do
  sleep 1
  "$adb" -s "$serial" shell pidof "$package" >/dev/null && break
done
crash="$("$adb" -s "$serial" logcat -b crash -d 2>/dev/null | grep -F -A20 "Process: $package," || true)"
if [[ -n "$crash" ]]; then
  echo "Hush crashed on start:" >&2
  echo "$crash" >&2
  exit 1
elif "$adb" -s "$serial" shell pidof "$package" >/dev/null; then
  echo "Hush is installed and running on $serial."
else
  echo "Warning: Hush isn't running after start, but no crash was logged." >&2
fi
