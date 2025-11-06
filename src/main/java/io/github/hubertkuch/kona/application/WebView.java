package io.github.hubertkuch.kona.application;

import io.github.hubertkuch.kona.routing.KonaRouter;

/**
 * Defines the core operations for a web view component, abstracting the underlying native implementation.
 * This interface provides functionalities for initialization, widget creation, content loading,
 * JavaScript execution, and message handling.
 */
public interface WebView extends AutoCloseable {

    /**
     * Initializes the native WebView system and prepares any required resources.
     * This method must be called before any other WebView operations are performed.
     *
     * @return {@code true} if initialization was successful, {@code false} otherwise.
     */
    boolean initialize();

    /**
     * Creates a new native web view widget.
     *
     * @return A native handle or pointer to the created widget. Returns 0 or a null-equivalent
     *         if the widget could not be created.
     */
    long createWebViewWidget();

    /**
     * Loads a specified URI into the web view.
     *
     * @param webViewHandle The native handle of the web view widget.
     * @param uri           The URI to load, which can be a remote URL or a local file path.
     */
    void loadUri(long webViewHandle, String uri);

    /**
     * Executes a JavaScript script within the context of the web view.
     *
     * @param webViewHandle The native handle of the web view widget.
     * @param script        The JavaScript code to be executed.
     */
    void runJavaScript(long webViewHandle, String script);

    /**
     * Registers a handler for messages sent from the JavaScript context of the web view.
     *
     * @param handler The {@link KonaRouter} that will process the incoming messages.
     */
    void setScriptMessageHandler(KonaRouter handler);

    /**
     * Closes the web view and releases any associated native resources.
     */
    @Override
    void close();
}