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

    /**
     * Mirror frame cap. 30 by default: Android Auto re-encodes every frame,
     * and an uncapped 1080p video can stall its encoder (frozen car display).
     * 60 = uncapped, the original direct path.
     */
    @Volatile
    var maxFps: Int = 30

    /** Longest edge of the captured phone image in the paced path. */
    @Volatile
    var maxCaptureEdge: Int = 1280

    /** Paced/cropping renderer instead of the direct VirtualDisplay → car path. */
    val usesRenderer: Boolean
        get() = fillScreen || maxFps in 1..59

    /** Native player: try software decoders first (car display freezes on some phones). */
    @Volatile
    var preferSoftwareDecoder: Boolean = false

    /** How the car home screen lists the modes. */
    @Volatile
    var menuLayout: MenuLayout = MenuLayout.GRID
}
