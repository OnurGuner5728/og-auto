# OgAuto 0.1.0 — validation (2026-10-18)

- Package: `com.onurguner.ogauto`; label: OgAuto; versionCode: 1.
- Built application source: fa06e128e7d80a0ea2f35c1f0bce9755bf484297.
- `:app:testDebugUnitTest :app:assembleRelease :app:lintRelease`: BUILD SUCCESSFUL.
- 66 JUnit tests, 0 failures, 0 errors, 0 skipped (14 test classes).
- Includes actual-renderer touch mapping tests: requested fill falling back to fit rejects letterbox taps; changing requested preference does not change coordinates until rendering changes.
- Lint: 0 errors, 17 warnings. Categories: GradleDependency 1, Typos 3, ExportedService 1, DataExtractionRules 1, ObsoleteSdkInt 1, StaticFieldLeak 4, VectorPath 2, UnusedResources 2, Autofill 1, SetTextI18n 1.
- Build tool warning: upstream AGP 8.7.3 is tested through SDK 35; this project compiles with SDK 36. Build completed.
- `scripts/verify-apk.py`: application ID, label, version, non-debuggable release, bundled original MIT license, no keystore files, APK v2 signature and zip alignment passed.
- APK size: 2949034 bytes.
- SHA-256: `2b8fa5291029fa040abb5e52d7a62a326d7d540b8d9741db46bbfec30f93de15`.
- Signing certificate SHA-256: `1425dd0f810cac126028d00d38093edeee352e7b465779b1bb7deb7747e4ee8e`.

## Limits

No physical phone, Android Auto head unit, VYPN or MG/Carlinkit end-to-end test was run here. Unit tests exercise pure logic, not native display drivers or video capture. Do not describe the reported freeze as fixed. Android Auto launcher visibility and host acceptance must be checked on the actual setup.

## First vehicle check

Install OgAuto alongside existing apps. Enable its own Accessibility permission and approve screen capture. Open OgAuto → Mirror in Android Auto, initially using fit mode. Test a native video first. Then test landscape lock, fill, touch and returning from the reverse camera. Fill crops the edges and may require a new car surface/connection after changing settings. Protected content may be black. If the whole AA UI freezes, compare direct USB without Carlinkit.

## Signing continuity

The private signing backup is stored separately in the owner's private files, not in this repository. Restore that key for future updates; never publish it. APK v2 is valid for the project's Android 8+ minimum.
