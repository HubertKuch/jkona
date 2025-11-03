package io.github.hubertkuch.kona.application;

import java.lang.foreign.*;
import java.lang.invoke.MethodHandle;

public class GtkWebView implements AutoCloseable {

    private Arena arena;
    private Linker linker;
    private SymbolLookup webkitLib;

    private MethodHandle webkitWebViewNew;
    private MethodHandle webkitWebViewLoadUri;
    private MethodHandle webkitWebViewEvaluateJavascript;

    public static boolean isSupported() {
        try (Arena checkArena = Arena.ofConfined()) {
            SymbolLookup.libraryLookup("libwebkit2gtk-4.0.so", checkArena);
            return true;
        } catch (Throwable e) {
            return false;
        }
    }

    public boolean initialize() {
        try {
            this.arena = Arena.ofConfined();
            this.linker = Linker.nativeLinker();

            this.webkitLib = SymbolLookup.libraryLookup("libwebkit2gtk-4.0.so", this.arena);

            webkitWebViewNew = linker.downcallHandle(
                    webkitLib.find("webkit_web_view_new").get(),
                    FunctionDescriptor.of(ValueLayout.ADDRESS)
            );

            webkitWebViewLoadUri = linker.downcallHandle(
                    webkitLib.find("webkit_web_view_load_uri").get(),
                    FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.ADDRESS)
            );

            // C: void webkit_web_view_evaluate_javascript(
            //        WebKitWebView* web_view,
            //        const gchar* script,
            //        gssize length,
            //        const gchar* world_name,
            //        const gchar* source_uri,
            //        GAsyncReadyCallback callback,
            //        gpointer user_data
            //    );
            FunctionDescriptor evalDescriptor = FunctionDescriptor.ofVoid(
                    ValueLayout.ADDRESS,    // web_view
                    ValueLayout.ADDRESS,    // script
                    ValueLayout.JAVA_LONG,  // length
                    ValueLayout.ADDRESS,    // world_name
                    ValueLayout.ADDRESS,    // source_uri
                    ValueLayout.ADDRESS,    // callback
                    ValueLayout.ADDRESS     // user_data
            );

            this.webkitWebViewEvaluateJavascript = linker.downcallHandle(webkitLib
                    .find("webkit_web_view_evaluate_javascript")
                    .get(), evalDescriptor);

            return true;
        } catch (Throwable e) {
            if (this.arena != null) this.arena.close();
            e.printStackTrace();
            return false;
        }
    }

    public void runJavaScript(long webViewHandle, String script) {
        if (webViewHandle == 0L || this.webkitWebViewEvaluateJavascript == null) {
            System.err.println("Cannot run JavaScript: invalid handle or not initialized.");
            return;
        }

        try {
            MemorySegment webView = MemorySegment.ofAddress(webViewHandle);
            MemorySegment cScript = this.arena.allocateFrom(script);

            webkitWebViewEvaluateJavascript.invokeExact(
                    webView,
                    cScript,
                    -1L,                // length (-1 means null-terminated)
                    MemorySegment.NULL, // world_name
                    MemorySegment.NULL, // source_uri
                    MemorySegment.NULL, // callback
                    MemorySegment.NULL  // user_data
            );
        } catch (Throwable e) {
            e.printStackTrace();
        }
    }

    public long createWebViewWidget() {
        try {
            MemorySegment webViewHandle = (MemorySegment) webkitWebViewNew.invokeExact();
            return webViewHandle.address();
        } catch (Throwable e) {
            e.printStackTrace();
            return 0L;
        }
    }

    public void loadUri(long webViewHandle, String uri) {
        try {
            MemorySegment webView = MemorySegment.ofAddress(webViewHandle);
            MemorySegment cUri = this.arena.allocateFrom(uri);
            webkitWebViewLoadUri.invokeExact(webView, cUri);
        } catch (Throwable e) {
            e.printStackTrace();
        }
    }

    @Override
    public void close() {
        if (this.arena != null && this.arena.scope().isAlive()) {
            this.arena.close();
        }
    }
}