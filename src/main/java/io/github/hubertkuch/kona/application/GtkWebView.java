package io.github.hubertkuch.kona.application;

import java.lang.foreign.*;
import java.lang.invoke.MethodHandle;

/**
 * Manages the native WebKitGTK WebView component.
 * This class must be closed to free native resources.
 */
public class GtkWebView implements AutoCloseable {

    private Arena arena;
    private Linker linker;
    private SymbolLookup webkitLib;

    private MethodHandle webkitWebViewNew;
    private MethodHandle webkitWebViewLoadUri;

    /**
     * Checks if this strategy can be used on the current system.
     *
     * @return true if WebKitGTK 4.0 library is found, false otherwise.
     */
    public static boolean isSupported() {
        try (Arena checkArena = Arena.ofConfined()) {
            SymbolLookup.libraryLookup("libwebkit2gtk-4.0.so", checkArena);

            return true;
        } catch (Throwable e) {
            return false;
        }
    }

    /**
     * Initializes the WebView manager by loading the native library
     * and linking the required functions.
     *
     * @return true on success, false on failure.
     */
    public boolean initialize() {
        try {
            this.arena = Arena.ofConfined();
            this.linker = Linker.nativeLinker();

            this.webkitLib = SymbolLookup.libraryLookup("libwebkit2gtk-4.0.so", this.arena);

            // C: GtkWidget* webkit_web_view_new(void);
            webkitWebViewNew = linker.downcallHandle(
                    webkitLib.find("webkit_web_view_new").get(),
                    FunctionDescriptor.of(ValueLayout.ADDRESS)
            );

            // C: void webkit_web_view_load_uri(WebKitWebView* web_view, const char* uri);
            webkitWebViewLoadUri = linker.downcallHandle(
                    webkitLib.find("webkit_web_view_load_uri").get(),
                    FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.ADDRESS)
            );

            return true;
        } catch (Throwable e) {
            if (this.arena != null) this.arena.close();
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Creates a new native WebView widget instance.
     *
     * @return A native handle (pointer address) to the new GtkWidget (WebView).
     */
    public long createWebViewWidget() {
        try {
            MemorySegment webViewHandle = (MemorySegment) webkitWebViewNew.invokeExact();
            return webViewHandle.address();
        } catch (Throwable e) {
            e.printStackTrace();
            return 0L;
        }
    }

    /**
     * Loads a specific URI into the given WebView widget.
     *
     * @param webViewHandle The handle to the WebView widget.
     * @param uri The URI to load (e.g., "https://example.com").
     */
    public void loadUri(long webViewHandle, String uri) {
        if (webViewHandle == 0L) {
            System.err.println("Cannot load URI: invalid WebView handle.");
            return;
        }

        try {
            MemorySegment webView = MemorySegment.ofAddress(webViewHandle);
            MemorySegment cUri = this.arena.allocateFrom(uri);
            webkitWebViewLoadUri.invokeExact(webView, cUri);
        } catch (Throwable e) {
            e.printStackTrace();
        }
    }

    /**
     * Closes the native memory arena.
     */
    @Override
    public void close() {
        if (this.arena != null && this.arena.scope().isAlive()) {
            this.arena.close();
        }
    }
}