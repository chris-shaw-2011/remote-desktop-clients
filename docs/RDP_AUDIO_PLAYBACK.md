# RDP audio playback on Quest

Status: implemented on August 21, 2026. Physical Quest 3 validation remains
outstanding.

## Symptom and root cause

RDP audio on Quest 3 contained frequent short gaps even though the same host
played continuously in the Windows RDP client. aRDP delegates RDP audio output
to the OpenSL ES backend in its pinned FreeRDP 2.11.7 dependency.

That backend started its player before any PCM was queued and then played each
network-delivered block immediately. Its nominal 20-buffer queue therefore did
not act as a jitter buffer: ordinary Wi-Fi or scheduling variation could empty
the queue between blocks and produce an audible dropout.

FreeRDP's common `rdpsnd` path also defaulted to zero declared latency. Its
overrun guard allowed only about two audio blocks of pending playback and could
discard blocks delivered in a short burst, working against any buffering in the
device backend.

## Fix and decisions

`22_freerdp_buffer_opensles_playback.patch` makes the following paired changes:

- The OpenSL player remains stopped until the first real PCM block arrives.
- At initial playback and after a genuine queue underrun, it queues 150 ms of
  silence immediately before the real block. Playback starts at once, using the
  silence period to accumulate subsequent blocks without losing short sounds.
- Access shared by the FreeRDP receive thread and OpenSL callback is serialized.
  Closing clears and frees only that stream's pending buffers.
- `/sound:latency:150` gives FreeRDP's common overrun detector the same budget,
  preventing it from discarding the burst-delivered blocks that fill the
  cushion.

The 150 ms value is intentional. It adds modest output latency but is large
enough to absorb several typical RDP PCM delivery intervals on a wireless
headset. A count-only prebuffer was rejected because a single short sound could
otherwise remain queued forever. Starting immediately with silence preserves
those sounds while still establishing the cushion.

The change remains in the repository's FreeRDP patch series instead of the
ignored extracted dependency directory. The ARM64 release workflow now rebuilds
the pinned FreeRDP source with all repository patches and verifies that both the
Java latency argument and native marker are present. This is necessary because
dependency archive 17 contains an older precompiled `libfreerdp-client2.so` and
cannot carry the native fix by itself.

`23_freerdp_use_selected_ndk_for_openssl.patch` also makes the native rebuild
portable to GitHub's Android runner. OpenSSL 1.1.1 prefers a pre-existing
`ANDROID_NDK_HOME` over FreeRDP's selected `ANDROID_NDK`; the runner defines the
former globally, while this build installs and selects NDK r25c. The patch keeps
both variables pointed at the selected NDK so OpenSSL finds the same Clang that
FreeRDP put on `PATH`.

The initial implementation stays on FreeRDP 2.11.7 to keep this focused audio
repair source-compatible with the existing application integration. Moving to a
new FreeRDP major version is a separate migration with broader API, behavior,
and security validation requirements.

## Validation

Automated validation established that:

- every repository FreeRDP patch applies cleanly to tag `2.11.7`;
- the ARM64 FreeRDP libraries compile and the native marker is present in
  `libfreerdp-client2.so`;
- the generated Java arguments contain `/sound:latency:150`;
- the aRDP unit tests and ARM64 release APK build still pass.

On Quest 3, test a continuous speech/music source for at least ten minutes on a
representative Wi-Fi connection. Verify there are no recurring short gaps,
audio remains synchronized closely enough for desktop use, playback resumes
after a period of silence, and disconnect/reconnect does not retain old audio.
Repeat with two audible RDP panels because each session owns an independent
OpenSL stream and buffer.
