# Android TV Pie crossfade acceptance protocol

Baseline: PR #32, Android 9 (API 28), remote D-pad, physical television or Android TV emulator.
This protocol is not proof of execution. Record device model, Android version, APK SHA-256, commit, date, tester and observed outcomes.

## Release gates
1. Install the APK; launch using the Leanback tile. Record PASS/FAIL and crash logs.
2. Using only D-pad Up/Down/Left/Right, Select and Back, navigate Library, Online, Watch, Studio, More and Playback options. Each control must have visible focus, and Back must return predictably.
3. Open Playback options > Crossfade settings. Select Off, 2, 4, 6, 8 and 12 seconds using the remote. Reopen the menu and restart the app to confirm service-side persistence.
4. Verify that unsupported duration values are rejected by the service. Verify another app cannot invoke the private settings command.
5. Play two locally stored, authorized audio tracks of at least 30 seconds. Measure overlap from captured output (not merely a visual timer). Record incoming start, outgoing stop and whether both are simultaneously audible.
6. Repeat at 2, 4, 6, 8 and 12 seconds. A setting alone does not prove crossfade.
7. During the transition test Pause, Resume, Next, Previous, Seek, Stop, repeat-one, repeat-all and shuffle. Check queue identity and absence of duplicate playback.
8. Test a track shorter than the fade, unknown-duration streaming content, corrupt file, missing file, audio-focus interruption and Bluetooth disconnection.
9. Repeat 20 consecutive transitions; inspect logcat for crashes, leaks, unexpected concurrent playback and stuck audio focus.
10. Record actual APK hash, CI run, device evidence, audio capture and a PASS/FAIL/UNVERIFIED verdict per gate.

## Current architectural blocker
CrossfadePreloader is not wired to PlaybackService. No secondary stream is scheduled or mixed. Therefore the audible-overlap gate is NOT IMPLEMENTED and must remain FAIL (feature incomplete), not PASS.

## Acceptance policy
CI compile success is necessary but not sufficient. Hardware/emulator remote checks and recorded audible overlap must be observed before declaring release readiness. Keep PR draft and do not alter billing.
