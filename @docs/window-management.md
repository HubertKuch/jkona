# Window Management

This document explains how to manage windows and the event loop in a Kona application.

## Creating a Window

To create a window, you first need to get an instance of an `AppWindow` implementation (e.g., `GtkWindow`). Then, you can use the following methods to create and show the window:

1.  `initialize()`: Initializes the native windowing system.
2.  `createWindow(String title, int width, int height)`: Creates a new window with the specified title and dimensions.
3.  `showWindow(long handle)`: Makes the window visible.

## The Event Loop

The event loop is the heart of a GUI application. It processes user input, window events, and other system messages. To start the event loop, call the `runEventLoop()` method on your `AppWindow` instance. This is a blocking call that will run until the application is terminated.

## Adding Widgets

You can add widgets (e.g., a `WebView`) to a window using the `addWidget(long windowHandle, long widgetHandle)` method. The `windowHandle` is the handle of the parent window, and the `widgetHandle` is the handle of the widget to be added.

## Example

```java
public class Main {
    public static void main(String[] args) {
        // The application will automatically detect the environment.
        // If /webapp/index.html is found, it will run in production mode.
        // Otherwise, it will fall back to development mode and load from http://localhost:5173.
        new Kona.Builder()
                .title("My App")
                .width(800)
                .height(600)
                .build()
                .run();
    }
}
```
