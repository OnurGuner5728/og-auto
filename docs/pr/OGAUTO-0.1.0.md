# OgAuto 0.1.0 — independent direct-surface mirror

Problem: Onur Auto GPT video freezes on the reported POCO/MG/Carlinkit setup. This branch provides an alternative AndroidX direct-surface rendering architecture to test, rather than claiming that the hardware issue is fixed.

Based on AA Suite e449095 (MIT). Unique application ID com.onurguner.ogauto, independent signing key, Turkish and English resources. Fit mode targets the car Surface directly; fill uses HardwareBuffer and hardware Canvas. Input mapping now follows active rendering mode when fill falls back to fit. Stop releases projection and landscape overlay. Setup scrolls on small screens and captures local acknowledgement. Unneeded LAN URL receiver is disabled. MIT license is bundled in APK assets.

Validation: release build successful; 66 JUnit tests passed; lint completed with 0 errors and 17 warnings; APK signature, package identity and alignment verified. See ../OGAUTO-VALIDATION.md. Real hardware is untested. NavigationTemplate host acceptance depends on Android Auto and cannot be guaranteed by sideloading.
