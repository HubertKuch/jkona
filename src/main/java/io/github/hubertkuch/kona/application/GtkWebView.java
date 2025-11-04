package io.github.hubertkuch.kona.application;

import io.github.hubertkuch.kona.routing.KonaRouter;

import java.lang.foreign.*;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

public class GtkWebView implements AutoCloseable, WebView {

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


    public static boolean isSupported() {
        try (Arena checkArena = Arena.ofConfined()) {
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
     * This is the Java method called *by C* when a JavaScript message is received.
     * It MUST match the C callback signature:
     * (WebKitUserContentManager* manager, WebKitJavascriptResult* result, gpointer user_data)
     */
    public void onScriptMessageReceived(MemorySegment manager, MemorySegment jsResult, MemorySegment userData) {
        try {
            MemorySegment jscValue = (MemorySegment) webkitJavascriptResultGetJsValue.invokeExact(jsResult);
            MemorySegment cStringPointer = (MemorySegment) jscValueToString.invokeExact(jscValue);

            if (cStringPointer.equals(MemorySegment.NULL)) {
                System.err.println("===> UPCALL (JS->Java): Received NULL string.");
                return;
            }

            MemorySegment cStringData = cStringPointer.reinterpret(Long.MAX_VALUE);
            String message = cStringData.getString(0);

            if (upCallHandler != null) upCallHandler.onMessage(message);

            System.out.println("===> UPCALL (JS->Java): " + message);

            gFree.invokeExact(cStringPointer);
        } catch (Throwable e) {
            e.printStackTrace();
        }
    }

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
                    -1L,
                    MemorySegment.NULL,
                    MemorySegment.NULL,
                    MemorySegment.NULL,
                    MemorySegment.NULL
            );
        } catch (Throwable e) {
            e.printStackTrace();
        }
    }

    @Override
    public void setScriptMessageHandler(KonaRouter handler) {
        this.upCallHandler = handler;
    }

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