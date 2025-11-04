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
    private static final String CONTROLLER_PACKAGE = "io.github.hubertkuch.kona";
    private static final String BLANK_PAGE_URI = "about:blank";

    @KonaController(name = "test")
    public static class TestController {
        public TestController() {}

        public record TestPayload(String message) implements Payload {}
        public record TestResponse(String response) implements Payload {}

        @MessageHandler(action = "test")
        public TestResponse test(TestPayload payload) {
            log.info("From javascript => {}", payload.message);
            return new TestResponse("Hello from Java!");
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

            Thread.ofVirtual().start(() -> {
                try {
                    log.info("[Worker Thread] Waiting 3 seconds...");
                    Thread.sleep(3000);

                    log.info("[Worker Thread] Scheduling JS tasks on UI thread...");

                    window.scheduleTask(() -> {
                        log.info("[UI Thread] Running JS 1 (Setup Kona API)");
                        webView.runJavaScript(webViewHandle, getKonaApiJs());
                    });

                    window.scheduleTask(() -> {
                        log.info("[UI Thread] Running JS 2 (color)");
                        webView.runJavaScript(webViewHandle,
                                """
                                document.body.style.backgroundColor = '#2a2a2a';
                                document.body.style.color = 'white';
                                document.body.innerHTML = "<h1>Kona Bidirectional Communication Test</h1>";
                                """
                        );
                    });

                    window.scheduleTask(() -> {
                        log.info("[UI Thread] Running JS 3 (Invoke Java and wait for response)");
                        String jsMessage = """
                            kona.call('test', 'test', { message: "Hello from Kona front" })
                                .then(response => {
                                    console.log('Response from Java:', response);
                                    document.body.innerHTML += `<p>Response from Java: ${response.response}</p>`;
                                })
                                .catch(error => console.error('Error from Java:', error));
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

    private static String getKonaApiJs() {
        return "";
    }
}