package io.github.hubertkuch.kona;

import io.github.hubertkuch.kona.application.GtkWebView;
import io.github.hubertkuch.kona.application.GtkWindow;

public class byhand {
    public static void main(String[] args) {

        if (!GtkWindow.isSupported() || !GtkWebView.isSupported()) {
            throw new RuntimeException("Platform not supported (Missing GTK 3 or WebKitGTK 4.0)");
        }

        try (var window = new GtkWindow(); var webView = new GtkWebView()) {

            if (!window.initialize() || !webView.initialize()) {
                throw new RuntimeException("Cannot initialize window or webview");
            }

            var handle = window.createWindow("Test window", 800, 400);
            long webViewHandle = webView.createWebViewWidget();

            window.addWidget(handle, webViewHandle);
            webView.loadUri(webViewHandle, "about:blank");
            window.showWindow(handle);

            Thread.ofVirtual().start(() -> {
                try {
                    System.out.println("[Worker Thread] Waiting 3 seconds...");
                    Thread.sleep(3000);

                    System.out.println("[Worker Thread] Scheduling JS tasks on UI thread...");

                    window.scheduleTask(() -> {
                        System.out.println("[UI Thread] Running JS 1 (color)");
                        webView.runJavaScript(webViewHandle,
                                """
                    document.body.style.backgroundColor = '#2a2a2a';
                    document.body.style.color = 'white';
                    document.body.innerHTML = "<h1>test</h1>";
                    """
                        );
                    });

                    window.scheduleTask(() -> {
                        System.out.println("[UI Thread] Running JS 2 (alert)");
                        webView.runJavaScript(webViewHandle, "alert('Message from Java!');");
                    });

                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            });

            System.out.println("[Main Thread] Starting GTK event loop (blocking)...");
            window.runEventLoop();

            System.out.println("[Main Thread] Event loop finished. Exiting.");
        }
    }
}