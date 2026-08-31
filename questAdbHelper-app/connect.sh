#!/usr/bin/env bash
set -euo pipefail

quest_host=${1:-oculus-quest-3.shawhouse.internal}
quest_ip=$(getent ahostsv4 "$quest_host" | awk 'NR == 1 { print $1 }')

if [[ -z $quest_ip ]]; then
    echo "Could not resolve $quest_host" >&2
    exit 1
fi

for _ in {1..30}; do
    endpoint=$(adb mdns services | awk -v prefix="$quest_ip:" \
        '$2 == "_adb-tls-connect._tcp" && index($3, prefix) == 1 { print $3; exit }')
    if [[ -n $endpoint ]]; then
        target="$quest_host:${endpoint##*:}"
        if [[ $(adb -s "$target" get-state 2>/dev/null || true) != device ]]; then
            adb disconnect "$target" >/dev/null 2>&1 || true
            adb connect "$target" >/dev/null
        fi
        if [[ $(adb -s "$target" get-state 2>/dev/null || true) == device ]]; then
            echo "$target"
            exit
        fi
    fi
    sleep 1
done

echo "No authenticated wireless ADB service found for $quest_host" >&2
exit 1
