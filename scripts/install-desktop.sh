#!/usr/bin/env bash
# SPDX-License-Identifier: GPL-3.0-or-later
# Builds the desktop app and installs it as the Arch package `hush` (pacman asks for sudo).
#   scripts/install-desktop.sh              build, package and install or update
#   scripts/install-desktop.sh --uninstall  remove the package (settings and data stay)
set -euo pipefail

repo="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
build="$repo/build/arch"

case "${1:-}" in
  "") ;;
  --uninstall)
    sudo pacman -R hush
    exit 0
    ;;
  *)
    echo "Usage: $0 [--uninstall]" >&2
    exit 2
    ;;
esac

if ! command -v makepkg >/dev/null; then
  echo "makepkg not found: this script needs Arch Linux (pacman)." >&2
  exit 1
fi

if pgrep -x Hush >/dev/null; then
  echo "Hush is running. Close it after the install and start it again to use the new version." >&2
fi

(cd "$repo" && ./gradlew :desktopApp:createReleaseDistributable)

# makepkg runs next to the PKGBUILD; its work files and the package go under build/.
mkdir -p "$build"
(cd "$repo/packaging/arch" && BUILDDIR="$build" PKGDEST="$build" SRCDEST="$build" makepkg -fsi --cleanbuild)

echo "Installed as /usr/bin/hush."
