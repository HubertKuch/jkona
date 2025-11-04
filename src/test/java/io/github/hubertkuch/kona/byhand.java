package io.github.hubertkuch.kona;

import io.github.hubertkuch.kona.application.GtkWebView;
import io.github.hubertkuch.kona.application.GtkWindow;
import io.github.hubertkuch.kona.message.KonaController;
import io.github.hubertkuch.kona.message.MessageHandler;
import io.github.hubertkuch.kona.message.Payload;
import io.github.hubertkuch.kona.routing.KonaRouterImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.time.temporal.TemporalField;

public class byhand {

    private static final Logger log = LoggerFactory.getLogger(byhand.class);
    private static final String CONTROLLER_PACKAGE = "io.github.hubertkuch.kona";
    private static final String BLANK_PAGE_URI = "http://localhost:5173/";

    @KonaController(name = "test")
    public static class TestController {
        public TestController() {}

        public record TestPayload(String message) implements Payload {}
        public record TestResponse(String response) implements Payload {}

        @MessageHandler(action = "test")
        public TestResponse test(TestPayload payload) {
            log.info("From javascript => {}", payload.message);
            return new TestResponse("Hello from Java! Its now %s".formatted(Instant.now().toString()));
        }
    }

    public static void main(String[] args) {

        if (!GtkWindow.isSupported() || !GtkWebView.isSupported()) {
            throw new RuntimeException("Platform not supported (Missing GTK 3 or WebKitGTK 4.0)");
        }

        try (var window = new GtkWindow(); var webView = new GtkWebView()) {

            if (!window.initialize() || !webView.initialize()) {
                throw new RuntimeException("Cannot initialize window or webview");
            }

            long webViewHandle = webView.createWebViewWidget();
            var router = new KonaRouterImpl(window, webView, webViewHandle);

            router.registerPackage(CONTROLLER_PACKAGE);
            webView.setScriptMessageHandler(router);

            var handle = window.createWindow("Test window", 800, 400);

            window.addWidget(handle, webViewHandle);
            webView.loadUri(webViewHandle, BLANK_PAGE_URI);
            window.showWindow(handle);

            log.info("[Main Thread] Starting GTK event loop (blocking)...");
            window.runEventLoop();

            log.info("[Main Thread] Event loop finished. Exiting.");
        }
    }
}