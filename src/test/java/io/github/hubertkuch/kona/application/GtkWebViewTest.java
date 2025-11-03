package io.github.hubertkuch.kona.application;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the GtkWebView strategy.
 * These tests will only run on their respective OS platforms.
 */
class GtkWebViewTest {

    /**
     * Unit test to verify the support-checking logic on Linux.
     */
    @Test
    @DisplayName("isSupported() should return true on Linux")
    @EnabledOnOs(OS.LINUX)
    void isSupported_ShouldReturnTrue_OnLinux() {
        assertTrue(GtkWebView.isSupported(), "GtkWebView.isSupported() should be true on Linux (assuming WebKitGTK is installed)");
    }

    /**
     * Unit test to verify the support-checking logic on non-Linux systems.
     */
    @Test
    @DisplayName("isSupported() should return false on non-Linux OS")
    @EnabledOnOs(value = {OS.WINDOWS, OS.MAC, OS.AIX, OS.SOLARIS})
    void isSupported_ShouldReturnFalse_OnNonLinux() {
        assertFalse(GtkWebView.isSupported(), "GtkWebView.isSupported() should be false on non-Linux systems");
    }

    /**
     * Integration test to ensure the native library initializes correctly.
     */
    @Test
    @DisplayName("initialize() should succeed on Linux")
    @EnabledOnOs(OS.LINUX)
    void initialize_ShouldReturnTrue_OnLinux() {
        // Use try-with-resources to automatically call close()
        try (GtkWebView webView = new GtkWebView()) {
            assertTrue(webView.initialize(), "Initialization should succeed on Linux");
        }
    }

    /**
     * Integration test to ensure a native widget handle is created.
     */
    @Test
    @DisplayName("createWebViewWidget() should return a valid handle on Linux")
    @EnabledOnOs(OS.LINUX)
    void createWebViewWidget_ShouldReturnValidHandle_OnLinux() {
        try (GtkWebView webView = new GtkWebView()) {
            // Given: The WebView manager is initialized
            boolean initSuccess = webView.initialize();
            assertTrue(initSuccess, "Test precondition failed: could not initialize WebKitGTK");

            // When: We create a WebView widget
            long handle = webView.createWebViewWidget();

            // Then: We should get a non-null (non-zero) handle
            assertNotEquals(0L, handle, "createWebViewWidget() should return a non-zero handle");
        }
    }

    /**
     * Integration test to ensure loading a URI does not throw an exception.
     * This test returns void, so we can only check for exceptions.
     */
    @Test
    @DisplayName("loadUri() should not throw exceptions on Linux")
    @EnabledOnOs(OS.LINUX)
    void loadUri_ShouldNotThrow_OnLinux() {
        try (GtkWebView webView = new GtkWebView()) {
            // Given: A fully initialized WebView widget
            assertTrue(webView.initialize(), "Precondition failed: initialize()");
            long handle = webView.createWebViewWidget();
            assertNotEquals(0L, handle, "Precondition failed: createWebViewWidget()");

            // When/Then: Loading a URI should not throw an exception
            assertDoesNotThrow(() -> {
                webView.loadUri(handle, "https://example.com");
            }, "loadUri() should not throw an exception with valid handles.");
        }
    }
}