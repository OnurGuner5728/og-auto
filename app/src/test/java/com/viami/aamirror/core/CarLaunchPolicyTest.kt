package com.onurguner.ogauto.core

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CarLaunchPolicyTest {
    @Test fun requiresAnAlreadyActiveProjection() {
        assertFalse(CarLaunchPolicy.returnToExistingMirror(ProjectionStatus.NONE))
        assertFalse(CarLaunchPolicy.returnToExistingMirror(ProjectionStatus.REQUESTED))
        assertTrue(CarLaunchPolicy.returnToExistingMirror(ProjectionStatus.ACTIVE))
    }
}
