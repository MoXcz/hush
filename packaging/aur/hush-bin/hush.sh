#!/bin/sh
# A wrapper, not a symlink: the launcher finds its runtime next to its own path.
exec /usr/lib/hush/bin/Hush "$@"
