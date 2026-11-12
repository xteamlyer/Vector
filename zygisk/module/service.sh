# Extract the directory path and change directory
MODDIR="${0%/*}"
cd "$MODDIR" || exit 1

# A NeoZygisk late start can call this with Android's toybox instead of a root manager's BusyBox.
unshare -m /system/bin/sh -c '
    mount -o rslave none / || exit 1
    exec "$0" "$@"
' "$MODDIR/daemon" --system-server-max-retry=3 "$@" &
