package com.kireibox.ui

import android.app.Application
import org.junit.Assert.assertArrayEquals
import org.junit.Test

class KireiViewModelFactoryContractTest {
    @Test
    fun `android view model factory can find public application constructor`() {
        val constructor = KireiViewModel::class.java.getConstructor(Application::class.java)

        assertArrayEquals(arrayOf(Application::class.java), constructor.parameterTypes)
    }
}
