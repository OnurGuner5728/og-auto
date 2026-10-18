package com.onurguner.ogauto.core

/** Mode flags, toggled from the car settings screen. */
object MirrorSettings {
    /** Actual renderer, not the requested preference; used by input mapping. */
    @Volatile var activeFill: Boolean = false

    /**
     * true = center-crop the phone screen so it fills the whole car display.
     * On by default: a landscape phone playing 16:9 video carries its own
     * side bars, and cropping them is what makes the video full screen.
     */
    @Volatile
    var fillScreen: Boolean = true

    /** Force the phone into landscape for as long as mirroring runs. */
    @Volatile
    var autoLandscape: Boolean = true

    /** Native player: try software decoders first (car display freezes on some phones). */
    @Volatile
    var preferSoftwareDecoder: Boolean = false

    /** How the car home screen lists the modes. */
    @Volatile
    var menuLayout: MenuLayout = MenuLayout.GRID
}
