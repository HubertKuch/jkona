package io.github.hubertkuch.kona;

import io.github.hubertkuch.kona.application.GtkWebView;
import io.github.hubertkuch.kona.application.GtkWindow;

public class byhand {
    public static void main(String[] args) {
        if (!GtkWindow.isSupported() || !GtkWebView.isSupported()) {
            throw new RuntimeException();
        }

        try (var window = new GtkWindow(); var webView = new GtkWebView()) {
            if (!window.initialize() || !webView.initialize()) {
                 throw new RuntimeException("Cannot initialize window");
            }

            var handle = window.createWindow("Test window", 800, 400);
            long webViewHandle = webView.createWebViewWidget();

            window.addWidget(handle, webViewHandle);
            webView.loadUri(webViewHandle, "https://google.com");

            window.showWindow();
            window.runEventLoop();
        }
    }
}
