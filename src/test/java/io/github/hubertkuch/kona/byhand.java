package io.github.hubertkuch.kona;

import io.github.hubertkuch.kona.application.GtkWindow;

public class byhand {
    public static void main(String[] args) {
        if (!GtkWindow.isSupported()) {
            throw new RuntimeException();
        }

        try (var window = new GtkWindow()) {
            if (!window.initialize()) {
                 throw new RuntimeException("Cannot initialize window");
            }

            var handle = window.createWindow("Test window", 800, 400);

            window.showWindow(handle);
            window.runEventLoop();
        }
    }
}
