package io.github.hubertkuch.kona;

import io.github.hubertkuch.kona.application.GtkWebView;
import io.github.hubertkuch.kona.application.GtkWindow;
import io.github.hubertkuch.kona.routing.KonaRouterImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Kona {

    private static final Logger log = LoggerFactory.getLogger(Kona.class);

    private final String controllerPackage;
    private final String initialUri;
    private final String title;
    private final int width;
    private final int height;

    private Kona(Builder builder) {
        this.controllerPackage = builder.controllerPackage;
        this.initialUri = builder.initialUri;
        this.title = builder.title;
        this.width = builder.width;
        this.height = builder.height;
    }

    public void run() {
        if (!GtkWindow.isSupported() || !GtkWebView.isSupported()) {
            throw new RuntimeException("Platform not supported (Missing GTK 3 or WebKitGTK 4.0)");
        }

        try (var window = new GtkWindow(); var webView = new GtkWebView()) {

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

    public static class Builder {
        private String controllerPackage;
        private String initialUri;
        private String title = "Kona App";
        private int width = 800;
        private int height = 600;

        public Builder(String controllerPackage, String initialUri) {
            this.controllerPackage = controllerPackage;
            this.initialUri = initialUri;
        }

        public Builder title(String title) {
            this.title = title;
            return this;
        }

        public Builder width(int width) {
            this.width = width;
            return this;
        }

        public Builder height(int height) {
            this.height = height;
            return this;
        }

        public Kona build() {
            return new Kona(this);
        }
    }
}
