# Custom Implementations

This document explains how to create your own implementations of the `AppWindow` and `WebView` interfaces. This is useful if you want to support a new platform (e.g., Windows, macOS) or use a different web view engine.

## `AppWindow` Interface

The `AppWindow` interface defines the essential operations for creating and managing an application window. To create a custom implementation, you need to implement the following methods:

- `boolean initialize()`: Initializes the native windowing system.
- `long createWindow(String title, int width, int height)`: Creates a new application window.
- `void showWindow(long handle)`: Makes the specified window visible.
- `void runEventLoop()`: Starts the main event loop.
- `void addWidget(long windowHandle, long widgetHandle)`: Adds a widget to a window.

Your implementation should also include a static `isSupported()` method that checks if the required native libraries are available on the system.

## `WebView` Interface

The `WebView` interface defines the core operations for a web view component. To create a custom implementation, you need to implement the following methods:

- `boolean initialize()`: Initializes the native WebView system.
- `long createWebViewWidget()`: Creates a new native web view widget.
- `void loadUri(long webViewHandle, String uri)`: Loads a specified URI into the web view.
- `void runJavaScript(long webViewHandle, String script)`: Executes a JavaScript script within the context of the web view.
- `void setScriptMessageHandler(KonaRouter handler)`: Registers a handler for messages sent from the JavaScript context.
- `void close()`: Closes the web view and releases any associated native resources.

Your implementation should also include a static `isSupported()` method that checks if the required native libraries are available on the system.

## Example: `GtkWindow` and `GtkWebView`

The `GtkWindow` and `GtkWebView` classes are the GTK-based implementations of `AppWindow` and `WebView`. You can use these classes as a reference when creating your own implementations. They demonstrate how to use the Foreign Function & Memory API (Project Panama) to interact with native libraries.
