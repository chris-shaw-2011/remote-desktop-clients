# FreeRDP 2 patch disposition in the 3.33 migration

The old numbered series is recorded at commit `f241056c` and in the baseline
document. It is replaced by the tracked build patch, Java adapter patch, slim
`freerdp-client` module and `freerdp3-patches` native series. Extracted dependencies
are generated output. Paths in the table refer to upstream 3.33.0 unless a local
adapter is named. This mapping is source evidence; physical Quest acceptance
remains required.

Tracked replacements: [native monitor/input patch](../remoteClientLib/jni/libs/freerdp3-patches/01_personal_native.patch),
[buffered OpenSL patch](../remoteClientLib/jni/libs/freerdp3-patches/02_buffered_opensl.patch),
[Java adapter patch](../remoteClientLib/freerdp-adapter/java.patch),
[ExternalDeps build patch](../remoteClientLib/jni/libs/freerdp3-build.patch),
[embedded module](../freerdp-client/build.gradle).

| Old patch | Disposition and replacement |
| --- | --- |
| 01 relax_order_checks | Java adapter passes `+relax-order-checks`; `client/common/cmdline.c` sets `FreeRDP_AllowUnanouncedOrdersFromServer`. |
| 02 different OpenH264 NDK | Obsolete shell-build adaptation. OpenH264 remains explicitly off. |
| 03 remove_cert_ignore | Java patch removes upstream's `/cert:ignore`; Ex callbacks prompt in the focused panel. `RdpCertificateTrust` handles matching old host/port pins without deleting old files. |
| 04 finite input-thread wait / fatal signal handlers | Old input-thread teardown is gone. `client/Android/Studio/freeRDPCore/src/main/cpp/android_freerdp.c` uses the 3.x client/event lifecycle. Standard Android crash handling replaces process-wide SIGKILL handlers; no unjoined input thread is deliberately freed after 100 ms. Panel cleanup retains its asynchronous session owner. |
| 05 GFX progressive fallback | Java patch explicitly requests progressive with non-H264 GFX and AVC444/AVC420/RFX/progressive when H264 is selected and supported. |
| 06 IPv6 enable | 3.x `libfreerdp/core/tcp.c` resolves IPv4/IPv6 address lists; Java retains IPv6 literal bracketing. The old IPv6Enabled setting now reports client-address family rather than enabling the resolver. |
| 07 scoped storage | Java adapter redirects the app's external-files directory; no shared-storage path is introduced. |
| 08 compile SDK/tools | Tracked slim module uses shared compile 37 / target 36 / minimum 23. |
| 09 bookmark RDP filename | Java patch adds filename to the ephemeral bookmark and its parcel. No second bookmark database is added. |
| 10 RDP file arguments | Java patch supplies the filename before explicit settings, retaining import overrides. Obsolete `/gt` syntax is removed; upstream gateway defaults apply. |
| 11 desktop scale | Adapter exposes existing upstream desktop-scale settings through the app's percentage API and preserves custom scale argument generation. |
| 12 parallel Android build | New CMake/Ninja wrapper propagates the requested job count to ExternalDeps. |
| 13 namespace | Slim module declares `com.freerdp.freerdpcore` and uses the shared SDK floor. |
| 14 FreeRDP linker flags | Build patch applies 16 KB maximum page size through Android CMake arguments. |
| 15 FFmpeg linker flags | External FFmpeg recipe receives architecture flags and explicit 16 KB alignment. |
| 16 security negotiation | Java adapter translates the NLA plus extended-security selection into valid 3.x `/sec` syntax; TLS/RDP selections remain explicit. |
| 18 DNS diagnostics | Upstream `libfreerdp/core/tcp.c` resolver/error diagnostics replace the custom parameter log; address negotiation behavior is retained. |
| 19 bookmark glyph cache | Ephemeral bookmark extension and parcel retain the setting. |
| 20 glyph cache argument | Java patch emits the 3.x `/cache:glyph:on` or `off` option. |
| 21 H264 startup timeout | Lazy `BoundedCapabilityProbe` runs once after class initialization, waits at most one second initially, and retains the eventual result. Four focused tests cover blocking, caching, failure and interruption. |
| 22 150 ms OpenSL cushion | Native patch 02 ports silence prebuffer, queue capacity notification, failure ownership and teardown to 3.x; Java selects `sys:opensles,latency:150`. AAudio is disabled pending measurements. |
| 23 OpenSSL selected NDK | ExternalDeps uses the same selected NDK/API/ABI as FreeRDP and FFmpeg; provider remains embedded, not a host installation or unshipped module. |
| 24 dynamic resolution | Java wrapper delegates to upstream `freerdp_send_monitor_layout` and Android display-control implementation; multi-monitor layout stays separate. |
| 24 experimental audio codec filter | Already present in 3.33 `libfreerdp/codec/dsp_ffmpeg.c` (`is_experimental`). No duplicate patch. |
| 25 keyboard repeat | Native patch 01 extends key/unicode JNI with repeat flags; existing Java keyboard state/lock/modifier ownership remains. |
| 25 optimized ProGuard default | Old upstream-module build adaptation is obsolete; slim module follows this project's unminified library build. |
| 26 shared multi-monitor / region copy | Native patch 01 uses 3.x monitor settings APIs and clipped source/destination bitmap copying; Java retains monitor count, per-panel routing and shared-session registry. |

The extra native patch 03 supplies the stable MediaCodec keyframe bit value
missing from NDK r25c headers; the installed NDK28 header defines that value as
1. It does not call a newer runtime API or change decoder selection. The new
build retains MediaCodec and FFmpeg explicitly, with OpenH264 off. JSON/AAD,
camera, printer and optional codec stacks are disabled rather than importing
unused standalone-client features or host libraries.

The embedded module retains upstream keyboard integer values and translated
strings referenced by existing app layouts. It compiles only the protocol wrapper,
bookmark value object and session state, plus a small application-owned session
map. Room, upstream persistence, dialogs and standalone application lifecycle
are excluded. Per-handle events still route through `RdpSessionRegistry`.
