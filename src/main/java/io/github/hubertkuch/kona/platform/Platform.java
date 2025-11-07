package io.github.hubertkuch.kona.platform;

import io.github.hubertkuch.kona.application.AppWindow;
import io.github.hubertkuch.kona.application.GtkWebView;
import io.github.hubertkuch.kona.application.GtkWindow;
import io.github.hubertkuch.kona.application.WebView;

public class Platform {
    public static boolean isLinux() {
        return System.getProperty("os.name").toLowerCase().contains("linux");
    }

    public static boolean isWindows() {
        return System.getProperty("os.name").toLowerCase().contains("windows");
    }

    public static boolean isMac() {
        return System.getProperty("os.name").toLowerCase().contains("mac");
    }

    public static boolean isWebViewSupported() {
        if (isLinux()) {
            return GtkWebView.isSupported();
        } else {
            return false;
        }
    }

    public static boolean isWindowSupported() {
        if (isLinux()) {
            return GtkWindow.isSupported();
        } else {
            return false;
        }
    }

    public static AppWindow getAppWindow() {
        if (isLinux()) {
            return new GtkWindow();
        } else {
            throw new UnsupportedOperationException("For now, only Linux GTK is supported");
        }
    }

    public static WebView getWebView() {
        if (isLinux()) {
            return new GtkWebView();
        } else {
            throw new UnsupportedOperationException("For now, only Linux GTK is supported");
        }
    }

    public static String getUnsupportedMessage() {
        if (isLinux()) {
            return "Missing Missing GTK 3 or WebKitGTK 4.0";
        } else {
            return "Platform not supported";
        }
    }
}
