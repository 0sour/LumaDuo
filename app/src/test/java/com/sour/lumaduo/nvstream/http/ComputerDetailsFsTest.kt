package com.sour.lumaduo.nvstream.http

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ComputerDetailsFsTest {
    @Test
    fun copyKeepsFoundationProfileAndCapabilities() {
        val source = ComputerDetails().apply {
            fsCapabilityVersion = 1
            fsFeatures = setOf("profile", "appfacts", "art", "poster_variants")
            posterVariants = true
            fsPlatform = "windows"
            fsOsVersion = "Windows 11"
            fsGpu = "Test GPU"
            fsDisplayModes = "1920x1080@60"
            fsHostIcon = "icon.png"
        }

        val copy = ComputerDetails(source)

        assertEquals(1, copy.fsCapabilityVersion)
        assertEquals(setOf("profile", "appfacts", "art", "poster_variants"), copy.fsFeatures)
        assertTrue(copy.posterVariants)
        assertEquals("windows", copy.fsPlatform)
        assertEquals("Windows 11", copy.fsOsVersion)
        assertEquals("Test GPU", copy.fsGpu)
        assertEquals("1920x1080@60", copy.fsDisplayModes)
        assertEquals("icon.png", copy.fsHostIcon)
        assertTrue(copy.isFoundation)
        assertTrue(copy.supportsFsFeature("appfacts"))
    }
}
