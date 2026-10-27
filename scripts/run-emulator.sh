#!/bin/sh
set -eu

# Use explicit DNS for this development emulator; macOS DNS discovery can go stale after sleep.
flytly_sdk_root=${ANDROID_SDK_ROOT:-${ANDROID_HOME:-"$HOME/Library/Android/sdk"}}
flytly_avd=${1:-Medium_Phone_API_37.0}
flytly_adb="$flytly_sdk_root/platform-tools/adb"

for flytly_serial in $("$flytly_adb" devices | awk '/^emulator-[0-9]+[[:space:]]+device$/ { print $1 }'); do
    flytly_running_avd=$("$flytly_adb" -s "$flytly_serial" emu avd name 2>/dev/null | head -n 1 | tr -d '\r')
    if [ "$flytly_running_avd" = "$flytly_avd" ]; then
        printf '%s\n' "$flytly_avd is already running on $flytly_serial."
        exit 0
    fi
done

# Reproduce the measured hardware OpenGL setup if this optional DNS recovery
# launcher is used on an Apple Silicon Mac without the saved emulator settings.
# https://developer.android.com/studio/run/emulator-troubleshooting
case "$(uname -sm)" in
    'Darwin arm64') set -- -gpu host -cores 4 -feature -Vulkan ;;
    *) set -- ;;
esac

exec "$flytly_sdk_root/emulator/emulator" -avd "$flytly_avd" \
    -no-snapshot-load -no-boot-anim -dns-server 8.8.8.8,8.8.4.4 "$@"
