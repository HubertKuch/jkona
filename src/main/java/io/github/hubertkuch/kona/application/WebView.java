package io.github.hubertkuch.kona.application;

import io.github.hubertkuch.kona.routing.KonaRouter;

public interface WebView extends AutoCloseable {

    boolean initialize();

    long createWebViewWidget();

    void loadUri(long webViewHandle, String uri);

    void runJavaScript(long webViewHandle, String script);

    void setScriptMessageHandler(KonaRouter handler);

    @Override
    void close();
}