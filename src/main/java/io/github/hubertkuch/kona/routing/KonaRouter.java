package io.github.hubertkuch.kona.routing;

/**
 * Defines the contract for a message router that processes incoming messages from the frontend.
 */
public interface KonaRouter {

    /**
     * Called when a message is received from the frontend WebView.
     *
     * @param message The raw message string, expected to be in JSON format.
     */
    void onMessage(String message);
}
