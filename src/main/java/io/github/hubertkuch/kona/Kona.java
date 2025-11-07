package io.github.hubertkuch.kona;

import io.github.hubertkuch.kona.application.GtkWebView;
import io.github.hubertkuch.kona.application.GtkWindow;
import io.github.hubertkuch.kona.platform.Platform;
import io.github.hubertkuch.kona.routing.KonaRouterImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The main entry point for a Kona application.
 * This class provides a high-level abstraction for creating and running a Kona application,
 * handling the initialization of the window, web view, and router.
 */
public class Kona {

    private static final Logger log = LoggerFactory.getLogger(Kona.class);

    private final String controllerPackage;
    private final String initialUri;
    private final String title;
    private final int width;
    private final int height;

    private Kona(Builder builder) {
        this.controllerPackage = builder.controllerPackage != null ? builder.controllerPackage : getCallerPackage();
        this.initialUri = builder.initialUri != null ? builder.initialUri : getDefaultInitialUri();
        this.title = builder.title;
        this.width = builder.width;
        this.height = builder.height;
    }

    private String getCallerPackage() {
        StackTraceElement[] stackTrace = Thread.currentThread().getStackTrace();
        // stack[0] is getStackTrace
        // stack[1] is this method
        // stack[2] is the constructor
        // stack[3] is the build method
        // stack[4] is the main method
        if (stackTrace.length > 4) {
            try {
                return Class.forName(stackTrace[4].getClassName()).getPackage().getName();
            } catch (ClassNotFoundException e) {
                log.error("Could not determine caller package", e);
            }
        }
        return "";
    }

    private String getDefaultInitialUri() {
        try {
            java.net.URL resource = Kona.class.getResource("/webapp/index.html");
            if (resource != null) {
                log.info("Found /webapp/index.html, running in production mode.");
                return resource.toURI().toString();
            }
        } catch (java.net.URISyntaxException e) {
            log.error("Failed to get resource URI", e);
        }
        log.info("Could not find /webapp/index.html, falling back to development mode (http://localhost:5173).");
        return "http://localhost:5173";
    }

    /**
     * Runs the Kona application.
     * This method initializes the GTK window and web view, sets up the router,
     * and starts the GTK event loop.
     */
    public void run() {
        if (! Platform.isWebViewSupported() || !Platform.isWindowSupported()) {
            throw new RuntimeException(Platform.getUnsupportedMessage());
        }

        try (var window = Platform.getAppWindow(); var webView = Platform.getWebView()) {

            if (!window.initialize() || !webView.initialize()) {
                throw new RuntimeException("Cannot initialize window or webview");
            }

            long webViewHandle = webView.createWebViewWidget();
            var router = new KonaRouterImpl(window, webView, webViewHandle);

            router.registerPackage(controllerPackage);
            webView.setScriptMessageHandler(router);

            var handle = window.createWindow(title, width, height);

            window.addWidget(handle, webViewHandle);
            webView.loadUri(webViewHandle, initialUri);
            window.showWindow(handle);

            log.info("[Kona] Starting GTK event loop (blocking)...");
            window.runEventLoop();

            log.info("[Kona] Event loop finished. Exiting.");
        }
    }

    /**
     * A builder for creating a {@link Kona} application instance.
     */
    public static class Builder {
        private String controllerPackage;
        private String initialUri;
        private String title = "Kona App";
        private int width = 800;
        private int height = 600;

        /**
         * Creates a new builder for a Kona application.
         */
        public Builder() {}

        /**
         * Sets the package to scan for @KonaController classes.
         * If not set, the package of the calling class will be used.
         *
         * @param controllerPackage The package name.
         * @return This builder instance.
         */
        public Builder controllerPackage(String controllerPackage) {
            this.controllerPackage = controllerPackage;
            return this;
        }

        /**
         * Sets the initial URI to load in the web view.
         * If not set, the default URI will be used (http://localhost:5173 in dev mode, or a file URI in production mode).
         *
         * @param initialUri The URI to load.
         * @return This builder instance.
         */
        public Builder initialUri(String initialUri) {
            this.initialUri = initialUri;
            return this;
        }

        /**
         * Sets the title of the application window.
         *
         * @param title The title of the window.
         * @return This builder instance.
         */
        public Builder title(String title) {
            this.title = title;
            return this;
        }

        /**
         * Sets the width of the application window.
         *
         * @param width The width of the window.
         * @return This builder instance.
         */
        public Builder width(int width) {
            this.width = width;
            return this;
        }

        /**
         * Sets the height of the application window.
         *
         * @param height The height of the window.
         * @return This builder instance.
         */
        public Builder height(int height) {
            this.height = height;
            return this;
        }

        /**
         * Builds the Kona application instance.
         *
         * @return A new {@link Kona} instance.
         */
        public Kona build() {
            return new Kona(this);
        }
    }
}
