package io.github.hubertkuch.kona;

import io.github.hubertkuch.kona.application.GtkWebView;
import io.github.hubertkuch.kona.application.GtkWindow;
import io.github.hubertkuch.kona.message.KonaController;
import io.github.hubertkuch.kona.message.MessageHandler;
import io.github.hubertkuch.kona.message.Payload;
import io.github.hubertkuch.kona.routing.KonaRouterImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class byhand {

    private static final Logger log = LoggerFactory.getLogger(byhand.class);

    @KonaController(name = "test")
    public static class TestController {
        public TestController() {}

        public record TestPayload(String message) implements Payload {}

        @MessageHandler(action = "test")
        public void test(TestPayload payload) {
            log.info("From javascript => {}", payload.message);
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

            var router = new KonaRouterImpl();

            router.registerPackage("io.github.hubertkuch.kona");
            webView.setScriptMessageHandler(router);

            var handle = window.createWindow("Test window", 800, 400);
            long webViewHandle = webView.createWebViewWidget();

            window.addWidget(handle, webViewHandle);
            webView.loadUri(webViewHandle, "about:blank");
            window.showWindow(handle);

            Thread.ofVirtual().start(() -> {
                try {
                    log.info("[Worker Thread] Waiting 3 seconds...");
                    Thread.sleep(3000);

                    log.info("[Worker Thread] Scheduling JS tasks on UI thread...");

                    window.scheduleTask(() -> {
                        log.info("[UI Thread] Running JS 1 (color)");
                        webView.runJavaScript(webViewHandle,
                                """
                                document.body.style.backgroundColor = '#2a2a2a';
                                document.body.style.color = 'white';
                                document.body.innerHTML = "<h1>test</h1>";
                    """
                        );
                    });

                    window.scheduleTask(() -> {
                        log.info("[UI Thread] Running JS 3 (test test)");

                        String jsMessage = """
                            const msg = {
                                controller: 'test',
                                action: 'test',
                                payload: { message: "Hello from Kona front" }
                            };
                            window.webkit.messageHandlers.kona.postMessage(JSON.stringify(msg));
                        """;
                        webView.runJavaScript(webViewHandle, jsMessage);
                    });

                } catch (InterruptedException e) {
                    log.error("Error in worker thread", e);
                }
            });

            log.info("[Main Thread] Starting GTK event loop (blocking)...");
            window.runEventLoop();

            log.info("[Main Thread] Event loop finished. Exiting.");
        }
    }
}