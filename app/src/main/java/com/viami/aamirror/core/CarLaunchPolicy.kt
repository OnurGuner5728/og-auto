package com.onurguner.ogauto.core

object CarLaunchPolicy {
    /** Reuse an already approved projection, never request a new one from app selection. */
    fun returnToExistingMirror(status: ProjectionStatus): Boolean = status == ProjectionStatus.ACTIVE
}
