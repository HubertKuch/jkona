package io.github.hubertkuch.kona.application;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the GtkWindow strategy.
 * These tests will only run on their respective OS platforms.
 */
class GtkWindowTest {

    private GtkWindow window;

    @BeforeEach
    void setUp() {
        window = new GtkWindow();
    }

    /**
     * This is a unit test that verifies the support-checking logic.
     * It should pass on any Linux system with GTK 3 installed.
     */
    @Test
    @DisplayName("isSupported() should return true on Linux")
    @EnabledOnOs(OS.LINUX)
    void isSupported_ShouldReturnTrue_OnLinux() {
        assertTrue(GtkWindow.isSupported(), "GtkWindow.isSupported() should be true on Linux (assuming GTK 3 is installed)");
    }

    /**
     * This is a unit test that verifies the support-checking logic.
     * It should pass on non-Linux systems.
     */
    @Test
    @DisplayName("isSupported() should return false on non-Linux OS")
    @EnabledOnOs(value = {OS.WINDOWS, OS.MAC, OS.AIX, OS.SOLARIS})
    void isSupported_ShouldReturnFalse_OnNonLinux() {
        assertFalse(GtkWindow.isSupported(), "GtkWindow.isSupported() should be false on non-Linux systems");
    }

    /**
     * This is an integration test that attempts to initialize the native library.
     * It will only run on Linux and requires a graphical environment.
     */
    @Test
    @DisplayName("initialize() should succeed on Linux")
    @EnabledOnOs(OS.LINUX)
    void initialize_ShouldReturnTrue_OnLinux() {
        // We only test initialization, as other methods depend on it.
        assertTrue(window.initialize(), "Initialization should succeed on Linux");
    }

    /**
     * This is an integration test that checks the full window creation lifecycle.
     * It will only run on Linux.
     */
    @Test
    @DisplayName("createWindow() should return a valid handle on Linux")
    @EnabledOnOs(OS.LINUX)
    void createWindow_ShouldReturnValidHandle_OnLinux() {
        // Given: The window is initialized
        boolean initSuccess = window.initialize();
        assertTrue(initSuccess, "Test precondition failed: could not initialize GTK");

        // When: We create a window
        long handle = window.createWindow("Test Window", 800, 600);

        // Then: We should get a non-null (non-zero) handle
        assertNotEquals(0L, handle, "createWindow() should return a non-zero handle");
    }

}