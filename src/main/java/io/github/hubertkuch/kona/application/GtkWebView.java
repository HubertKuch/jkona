package io.github.hubertkuch.kona.application;

import io.github.hubertkuch.kona.routing.KonaRouter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.foreign.*;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

/**
 * A {@link WebView} implementation that uses GTK's WebKit port (WebKit2GTK).
 * This class is responsible for creating and managing a native WebView widget,
 * handling the bidirectional communication between Java and JavaScript, and loading web content.
 * It uses the Foreign Function & Memory API (Project Panama) to interact with native GTK and WebKit libraries.
 * <p>
 * This implementation is designed to be used on Linux systems where GTK and WebKit2GTK are available.
 * It must be closed (e.g., via try-with-resources) to release the native resources allocated by the underlying libraries.
 */
public class GtkWebView implements AutoCloseable, WebView {

    private static final Logger log = LoggerFactory.getLogger(GtkWebView.class);

    private Arena arena;
    private Linker linker;
    private SymbolLookup webkitLib;
    private SymbolLookup gobjectLib;
    private SymbolLookup jscLib;
    private SymbolLookup glibLib;

    private MethodHandle webkitWebViewNew;
    private MethodHandle webkitWebViewLoadUri;
    private MethodHandle webkitWebViewEvaluateJavascript;
    private MethodHandle webkitWebViewGetSettings;
    private MethodHandle gObjectSet;

    private MethodHandle webkitWebViewGetUserContentManager;
    private MethodHandle webkitUserContentManagerRegisterScriptMessageHandler;
    private MethodHandle gSignalConnect;
    private MethodHandle webkitJavascriptResultGetJsValue;
    private MethodHandle jscValueToString;
    private MethodHandle gFree;
    private MemorySegment onScriptMessageStub;

    private KonaRouter upCallHandler;


    /**
     * Checks if the required native libraries for this WebView implementation are available on the system.
     *
     * @return {@code true} if all necessary libraries (WebKit2GTK, GObject, JavaScriptCore, and GLib) are found,
     *         {@code false} otherwise.
     */
    public static boolean isSupported() {
        try (Arena checkArena = Arena.ofShared()) {
            SymbolLookup.libraryLookup("libwebkit2gtk-4.0.so", checkArena);
            SymbolLookup.libraryLookup("libgobject-2.0.so", checkArena);
            SymbolLookup.libraryLookup("libjavascriptcoregtk-4.0.so", checkArena);
            SymbolLookup.libraryLookup("libglib-2.0.so", checkArena);
            return true;
        } catch (Throwable e) {
            return false;
        }
    }

    /**
     * Callback method invoked from native code when a JavaScript message is received from the WebView.
     * This method is an upcall from C, triggered when {@code window.webkit.messageHandlers.kona.postMessage(...)}
     * is called in the loaded web page.
     * <p>
     * The method signature MUST match the one expected by the native GTK/WebKit signal connection:
     * {@code (WebKitUserContentManager *manager, WebKitJavascriptResult *result, gpointer user_data)}.
     *
     * @param manager   A pointer to the WebKitUserContentManager that emitted the signal.
     * @param jsResult  A pointer to the WebKitJavascriptResult containing the message from JavaScript.
     * @param userData  User data passed to the signal connection (in this case, a pointer to this GtkWebView instance).
     */
    public void onScriptMessageReceived(MemorySegment manager, MemorySegment jsResult, MemorySegment userData) {
        try {
            MemorySegment jscValue = (MemorySegment) webkitJavascriptResultGetJsValue.invokeExact(jsResult);
            MemorySegment cStringPointer = (MemorySegment) jscValueToString.invokeExact(jscValue);

            if (cStringPointer.equals(MemorySegment.NULL)) {
                log.warn("===> UPCALL (JS->Java): Received NULL string.");
                return;
            }

            MemorySegment cStringData = cStringPointer.reinterpret(Long.MAX_VALUE);
            String message = cStringData.getString(0);

            if (upCallHandler != null) upCallHandler.onMessage(message);

            log.debug("===> UPCALL (JS->Java): {}", message);

            gFree.invokeExact(cStringPointer);
        } catch (Throwable e) {
            log.error("Error in onScriptMessageReceived", e);
        }
    }

    /**
     * Initializes the GtkWebView by loading the required native libraries (WebKit, GObject, etc.)
     * and looking up the necessary function symbols using the Foreign Function & Memory API.
     * It also prepares the upcall stub for handling JavaScript messages.
     *
     * @return {@code true} if all libraries and functions are loaded successfully, {@code false} on any error.
     */
    public boolean initialize() {
        try {
            this.arena = Arena.ofConfined();
            this.linker = Linker.nativeLinker();

            this.webkitLib = SymbolLookup.libraryLookup("libwebkit2gtk-4.0.so", this.arena);
            this.gobjectLib = SymbolLookup.libraryLookup("libgobject-2.0.so", this.arena);
            this.jscLib = SymbolLookup.libraryLookup("libjavascriptcoregtk-4.0.so", this.arena);
            this.glibLib = SymbolLookup.libraryLookup("libglib-2.0.so", this.arena);


            webkitWebViewNew = linker.downcallHandle(
                    webkitLib.find("webkit_web_view_new").get(),
                    FunctionDescriptor.of(ValueLayout.ADDRESS)
            );

            webkitWebViewLoadUri = linker.downcallHandle(
                    webkitLib.find("webkit_web_view_load_uri").get(),
                    FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.ADDRESS)
            );

            FunctionDescriptor evalDescriptor = FunctionDescriptor.ofVoid(
                    ValueLayout.ADDRESS,
                    ValueLayout.ADDRESS,
                    ValueLayout.JAVA_LONG,
                    ValueLayout.ADDRESS,
                    ValueLayout.ADDRESS,
                    ValueLayout.ADDRESS,
                    ValueLayout.ADDRESS
            );

            this.webkitWebViewEvaluateJavascript = linker.downcallHandle(webkitLib
                    .find("webkit_web_view_evaluate_javascript")
                    .get(), evalDescriptor);

            this.webkitWebViewGetSettings = linker.downcallHandle(
                    webkitLib.find("webkit_web_view_get_settings").get(),
                    FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS)
            );

            this.gObjectSet = linker.downcallHandle(
                    gobjectLib.find("g_object_set").get(),
                    FunctionDescriptor.ofVoid(
                            ValueLayout.ADDRESS,
                            ValueLayout.ADDRESS,
                            ValueLayout.JAVA_BOOLEAN,
                            ValueLayout.ADDRESS
                    )
            );

            this.webkitWebViewGetUserContentManager = linker.downcallHandle(
                    webkitLib.find("webkit_web_view_get_user_content_manager").get(),
                    FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS)
            );

            this.webkitUserContentManagerRegisterScriptMessageHandler = linker.downcallHandle(
                    webkitLib.find("webkit_user_content_manager_register_script_message_handler").get(),
                    FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.ADDRESS)
            );

            this.gSignalConnect = linker.downcallHandle(gobjectLib
                    .find("g_signal_connect_data")
                    .get(), FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_INT));

            this.webkitJavascriptResultGetJsValue = linker.downcallHandle(
                    webkitLib.find("webkit_javascript_result_get_js_value").get(),
                    FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS)
            );

            this.jscValueToString = linker.downcallHandle(
                    jscLib.find("jsc_value_to_string").get(),
                    FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS)
            );

            this.gFree = linker.downcallHandle(
                    glibLib.find("g_free").get(),
                    FunctionDescriptor.ofVoid(ValueLayout.ADDRESS)
            );

            MethodHandle messageHandle = MethodHandles
                    .lookup()
                    .findVirtual(GtkWebView.class, "onScriptMessageReceived",
                            MethodType.methodType(void.class, MemorySegment.class, MemorySegment.class, MemorySegment.class)
                    )
                    .bindTo(this);

            FunctionDescriptor messageDesc = FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS);
            this.onScriptMessageStub = linker.upcallStub(messageHandle, messageDesc, this.arena);

            return true;
        } catch (Throwable e) {
            if (this.arena != null) this.arena.close();
            log.error("Error during initialization", e);
            return false;
        }
    }

    /**
     * Asynchronously executes a JavaScript script in the context of the WebView.
     * The script is evaluated, but the result is not returned to the Java side.
     *
     * @param webViewHandle The native handle of the WebView widget.
     * @param script        The JavaScript code to execute.
     */
    public void runJavaScript(long webViewHandle, String script) {
        if (webViewHandle == 0L || this.webkitWebViewEvaluateJavascript == null) {
            log.error("Cannot run JavaScript: invalid handle or not initialized.");
            return;
        }

        try {
            MemorySegment webView = MemorySegment.ofAddress(webViewHandle);
            MemorySegment cScript = this.arena.allocateFrom(script);

            webkitWebViewEvaluateJavascript.invokeExact(
                    webView,
                    cScript,
                    -1L,
                    MemorySegment.NULL,
                    MemorySegment.NULL,
                    MemorySegment.NULL,
                    MemorySegment.NULL
            );
        } catch (Throwable e) {
            log.error("Error running JavaScript", e);
        }
    }

    /**
     * Sets the router that will handle incoming messages from the JavaScript context.
     *
     * @param handler The {@link KonaRouter} instance to process messages.
     */
    @Override
    public void setScriptMessageHandler(KonaRouter handler) {
        this.upCallHandler = handler;
    }

    /**
     * Creates the native WebKitGTK widget.
     * This method also enables developer extras, sets up the script message handler named "kona",
     * and connects the signal for receiving messages.
     *
     * @return The native handle (memory address) of the created WebView widget, or 0L on failure.
     */
    public long createWebViewWidget() {
        try {
            MemorySegment webView = (MemorySegment) webkitWebViewNew.invokeExact();

            MemorySegment settings = (MemorySegment) webkitWebViewGetSettings.invokeExact(webView);
            MemorySegment propName = this.arena.allocateFrom("enable-developer-extras");
            gObjectSet.invokeExact(settings, propName, true, MemorySegment.NULL);

            MemorySegment contentManager = (MemorySegment) webkitWebViewGetUserContentManager.invokeExact(webView);
            MemorySegment handlerName = this.arena.allocateFrom("kona");
            webkitUserContentManagerRegisterScriptMessageHandler.invokeExact(contentManager, handlerName);

            MemorySegment signalName = this.arena.allocateFrom("script-message-received::kona");
            gSignalConnect.invokeExact(contentManager, signalName, this.onScriptMessageStub, MemorySegment.NULL, MemorySegment.NULL, 0);


            return webView.address();
        } catch (Throwable e) {
            log.error("Error creating web view widget", e);
            return 0L;
        }
    }

    /**
     * Loads the specified URI into the WebView.
     *
     * @param webViewHandle The native handle of the WebView widget.
     * @param uri           The URI to load. This can be a remote URL (e.g., "http://example.com")
     *                      or a local file path (e.g., "file:///path/to/index.html").
     */
    public void loadUri(long webViewHandle, String uri) {
        try {
            MemorySegment webView = MemorySegment.ofAddress(webViewHandle);
            MemorySegment cUri = this.arena.allocateFrom(uri);
            webkitWebViewLoadUri.invokeExact(webView, cUri);
        } catch (Throwable e) {
            log.error("Error loading URI", e);
        }
    }

    /**
     * Closes the native memory arena, releasing all resources allocated within it.
     * This is critical for preventing memory leaks.
     */
    @Override
    public void close() {
        if (this.arena != null && this.arena.scope().isAlive()) {
            this.arena.close();
            this.arena = null;
        }
    }
}