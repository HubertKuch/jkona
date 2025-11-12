package io.github.hubertkuch.kona.application;

/**
 * Defines the essential operations for creating and managing an application window.
 * This interface abstracts the underlying windowing system, providing a common
 * set of functionalities for window initialization, creation, display, and event handling.
 */
public interface AppWindow extends AutoCloseable {

    /**
     * Initializes the native windowing system and prepares any required resources.
     * This method must be called before any other window operations are performed.
     *
     * @return {@code true} if initialization was successful, {@code false} otherwise.
     */
    boolean initialize();

    /**
     * Creates a new application window with the specified properties.
     *
     * @param title  The title to be displayed in the window's title bar.
     * @param width  The initial width of the window in pixels.
     * @param height The initial height of the window in pixels.
     * @return A native handle or pointer to the created window. Returns 0 or a null-equivalent
     *         if the window could not be created.
     */
    long createWindow(String title, int width, int height);

    /**
     * Makes the specified window and its contents visible on the screen.
     *
     * @param handle The native handle of the window to be shown.
     */
    void showWindow(long handle);

    /**
     * Starts the main event loop for the application. This is a blocking call that
     * will process user input, window events, and other system messages until the
     * application is terminated.
     */
    void runEventLoop();

    /**
     * Adds a widget (e.g., a WebView) to a parent window.
     *
     * @param windowHandle The native handle of the parent window.
     * @param widgetHandle The native handle of the widget to be added.
     */
    void addWidget(long windowHandle, long widgetHandle);

    void fullscreen(long windowHandle, boolean fullscreen);
    void resizable(long windowHandle, boolean fullscreen);
    void title(long windowHandle, String title);
    void modal(long windowHandle, boolean modal);

    void scheduleTask(Runnable task);
    void close();
}
