package com.jlucraft.console.ui.navigation

import org.junit.Assert.*
import org.junit.Test

class AppRouteTest {

    // ------------------------------------------------------------------ //
    // Route existence
    // ------------------------------------------------------------------ //

    @Test
    fun `all main tab routes exist`() {
        val mainRoutes = setOf(
            AppRoute.Dashboard,
            AppRoute.Instances,
            AppRoute.League,
            AppRoute.Governance,
            AppRoute.AuditLog,
            AppRoute.Settings,
        )
        assertEquals(6, mainRoutes.size)
        mainRoutes.forEach { route ->
            assertNotNull("Route $route must not be null", route)
        }
    }

    @Test
    fun `all admin sub routes exist`() {
        val adminRoutes = setOf(
            AppRoute.Season,
            AppRoute.Alerts,
            AppRoute.NodeScores,
            AppRoute.DidResolution,
        )
        assertEquals(4, adminRoutes.size)
        adminRoutes.forEach { route ->
            assertNotNull("Admin route $route must not be null", route)
        }
    }

    @Test
    fun `admin routes are not in main tabs`() {
        val tabRoutes = Tabs.map { it.route }.toSet()
        assertFalse("Season must not be a bottom tab", AppRoute.Season in tabRoutes)
        assertFalse("Alerts must not be a bottom tab", AppRoute.Alerts in tabRoutes)
        assertFalse("NodeScores must not be a bottom tab", AppRoute.NodeScores in tabRoutes)
        assertFalse("DidResolution must not be a bottom tab", AppRoute.DidResolution in tabRoutes)
    }

    // ------------------------------------------------------------------ //
    // Route identity
    // ------------------------------------------------------------------ //

    @Test
    fun `route identity is stable`() {
        assertEquals(AppRoute.Season, AppRoute.Season)
        assertEquals(AppRoute.Alerts, AppRoute.Alerts)
        assertEquals(AppRoute.NodeScores, AppRoute.NodeScores)
        assertEquals(AppRoute.DidResolution, AppRoute.DidResolution)
        assertEquals(AppRoute.Dashboard, AppRoute.Dashboard)
        assertEquals(AppRoute.Settings, AppRoute.Settings)
    }

    @Test
    fun `main tab routes are distinct`() {
        val tabRoutes = Tabs.map { it.route }
        assertEquals(tabRoutes.size, tabRoutes.distinct().size)
    }

    @Test
    fun `serializable annotation present on AppRoute`() {
        // Verify the sealed interface has @Serializable
        val annotations = AppRoute::class.java.annotations
        val hasSerializable = annotations.any { it.annotationClass.qualifiedName == "kotlinx.serialization.Serializable" }
        assertTrue("AppRoute must be @Serializable for navigation3 typed routes", hasSerializable)
    }

    // ------------------------------------------------------------------ //
    // Tab consistency
    // ------------------------------------------------------------------ //

    @Test
    fun `tabs have all six entries`() {
        assertEquals(6, Tabs.size)
    }

    @Test
    fun `tabs include dashboard as first entry`() {
        assertEquals(AppRoute.Dashboard, Tabs.first().route)
    }

    @Test
    fun `tabs include settings as last entry`() {
        assertEquals(AppRoute.Settings, Tabs.last().route)
    }

    @Test
    fun `tab titles are not empty`() {
        Tabs.forEach { tab ->
            assertTrue("Tab title for ${tab.route} must not be empty", tab.title.isNotEmpty())
        }
    }

    @Test
    fun `tab icons are non null`() {
        Tabs.forEach { tab ->
            assertNotNull("Tab icon for ${tab.route} must not be null", tab.icon)
            assertNotNull("Tab outlined icon for ${tab.route} must not be null", tab.iconOutlined)
        }
    }
}
