# OgAuto 0.1.0 — implementation and handoff

Approved scope: standalone OgAuto APK and branch, based on AA Suite direct-surface architecture, for phone mirroring and car touch input. Native VYPN runs on the phone. No claim of verified MG/POCO compatibility.

- [x] Inspect upstream e449095 and preserve MIT attribution.
- [x] Isolate application ID com.onurguner.ogauto and branding.
- [x] Align touch mapping with actual renderer fallback.
- [x] Clean projection and orientation on service stop.
- [x] Provide Turkish UI and setup guide, consent and support.
- [x] Build signed installable APK; run geometry/input tests and lint.
- [x] Publish source and APK on GitHub (og-auto, main).
- [ ] Real vehicle: VYPN video, touch, rotation, reverse camera, wired/wireless.

Architecture: AndroidX Car App NavigationTemplate surface; MediaProjection creates one VirtualDisplay per approval. Fit renders directly to host Surface. Fill uses HardwareBuffer and hardware Canvas. Preserve fit by default. Do not mix CPU/hardware Canvas ownership. AA launch visibility requires host acceptance and is not guaranteed by an APK.

Release validation: 66 tests passed, 0 failures/errors/skipped. lintRelease completed with 0 errors and 17 warnings. APK v2 signature, zip alignment, application ID, version, label and bundled MIT license verified. See OGAUTO-VALIDATION.md. Hardware acceptance remains open.

## 0.2.0
- [x] Auto landscape lock while mirroring (overlay permission), fill on by default.
- [x] Whole-display capture on API 34+ (createConfigForDefaultDisplay); single-app fallback warning + restart.
- [x] onCapturedContentResize handling, fill rebuild ordering, car reconnect (↻) button.
- [x] In-app self reinstall with EXTRA_INSTALLER_PACKAGE_NAME=com.android.vending via FileProvider.
- [x] CI build workflow (GitHub Actions), optional release signing from secrets.
- [ ] Real vehicle: VYPN stream playback after fix, installer name honoured on target phone.
