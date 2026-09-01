package com.yuki.yukihub.launcher

import org.junit.Assert.assertEquals
import org.junit.Test

class ArtemisVersionTest {
    @Test
    fun storedPackagesMapToFourExplicitChoices() {
        assertEquals(ArtemisVersion.AUTO, ArtemisVersion.fromStored("internal.artemis"))
        assertEquals(ArtemisVersion.V1, ArtemisVersion.fromStored("internal.artemis.v1"))
        assertEquals(ArtemisVersion.V2, ArtemisVersion.fromStored("internal.artemis.compat"))
        assertEquals(ArtemisVersion.V3, ArtemisVersion.fromStored("internal.artemis.compat.v2"))
    }

    @Test
    fun emptyAndUnknownValuesFallBackToAuto() {
        assertEquals(ArtemisVersion.AUTO, ArtemisVersion.fromStored(null))
        assertEquals(ArtemisVersion.AUTO, ArtemisVersion.fromStored("third.party"))
    }
}
