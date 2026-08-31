# Quest Wireless ADB helper

This personal helper restores Horizon OS's hidden `adb_wifi_enabled` global
setting after the Quest joins Wi-Fi following a reboot. Android's authenticated
wireless-debugging service chooses a random TLS port, which the host discovers
through mDNS; the helper does not expose legacy unauthenticated TCP port 5555.

The app deliberately has no accessibility service, network listener, embedded
ADB client, web server, or automatic trust-dialog interaction. It runs one
persisted network-constrained job after boot and then exits.

One-time installation requires an already-authorized ADB connection:

```bash
./gradlew :questAdbHelper-app:assembleDebug
adb install -r questAdbHelper-app/build/outputs/apk/debug/questAdbHelper-app-debug.apk
adb shell pm grant com.iiordanov.questadb android.permission.WRITE_SECURE_SETTINGS
adb shell am start -n com.iiordanov.questadb/.MainActivity
```

Connect from this Linux host after a reboot (the script discovers the rotating
authenticated port through mDNS):

```bash
questAdbHelper-app/connect.sh
```

Pass a different Quest hostname as the first argument if needed. The script
requires current Android Platform Tools plus the standard Linux `getent` and
`awk` commands.

Wireless ADB grants authorized computers shell-level control of the headset.
Use it only on a trusted private network and revoke debugging authorizations if
the host key is no longer trusted.
