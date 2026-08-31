# Quest Wireless ADB helper

This personal helper restores Horizon OS's hidden `adb_wifi_enabled` global
setting after the Quest joins Wi-Fi following a reboot. Android's authenticated
wireless-debugging service chooses a random TLS port, which the host discovers
through mDNS; the helper does not expose legacy unauthenticated TCP port 5555.

The app deliberately has no accessibility service, network listener, embedded
ADB client, web server, or automatic trust-dialog interaction. It also restores
aRDP's existing Windows-key accessibility service after boot or an aRDP update;
it does not capture keys itself.

One-time installation requires an already-authorized ADB connection:

```bash
./gradlew :questAdbHelper-app:assembleDebug
adb install -r questAdbHelper-app/build/outputs/apk/debug/questAdbHelper-app-debug.apk
adb shell pm grant com.iiordanov.questadb android.permission.WRITE_SECURE_SETTINGS
adb shell appops set com.iiordanov.aRDP ACCESS_RESTRICTED_SETTINGS allow
adb shell am start -n com.iiordanov.questadb/.MainActivity
```

The secure-settings grant lets the helper preserve other enabled accessibility
services while restoring aRDP's `SystemKeyCaptureService`. Android does not let
a normal app grant a restricted-settings AppOp. That one-time allowance is
expected to survive updates, and `connect.sh` reasserts it as a safety net each
time an authenticated wireless ADB connection is made.

Connect from this Linux host after a reboot (the script discovers the rotating
authenticated port through mDNS):

```bash
questAdbHelper-app/connect.sh
```

Pass a different Quest hostname as the first argument if needed. The script
requires current Android Platform Tools plus the standard Linux `getent` and
`awk` commands. Its stdout remains only the connected endpoint, so it can still
be used in command substitution.

Wireless ADB grants authorized computers shell-level control of the headset.
Use it only on a trusted private network and revoke debugging authorizations if
the host key is no longer trusted.
