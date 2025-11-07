# Extending the Code

This document provides a general overview of how to extend the Kona application.

## Project Structure

The project is divided into several key packages:

- `io.github.hubertkuch.kona.application`: Contains the core interfaces for the application window (`AppWindow`) and the web view (`WebView`), as well as their GTK-based implementations.
- `io.github.hubertkuch.kona.message`: Defines the annotations and interfaces for handling messages from the frontend (`KonaController`, `MessageHandler`, `Payload`).
- `io.github.hubertkuch.kona.routing`: Contains the `KonaRouter` interface and its implementation, which is responsible for routing messages to the appropriate handlers.

## Key Concepts

- **`AppWindow` and `WebView`**: These interfaces abstract the underlying native implementations for creating and managing windows and web views. You can create your own implementations for different platforms (e.g., Windows, macOS) by implementing these interfaces.
- **Message Handling**: Communication between the frontend and the backend is done through JSON messages. The frontend sends messages to the backend, which are then routed to the appropriate `@KonaController` and `@MessageHandler` methods.
- **Payloads**: Message payloads are represented by classes or records that implement the `Payload` interface. These are automatically populated from the JSON payload of the incoming message.

## How to Extend

1.  **Create a new controller**: Create a new class and annotate it with `@KonaController`. This class will be responsible for handling a specific set of messages from the frontend.
2.  **Create message handlers**: Within your controller, create methods and annotate them with `@MessageHandler`. These methods will be invoked when a message with a matching action is received.
3.  **Define payloads**: Create classes that implement the `Payload` interface to represent the data that is sent between the frontend and the backend.
4.  **Run the application**: Use the `Kona.Builder` to configure and run your application.

For more detailed information, refer to the other documents in this directory:

- `custom-implementations.md`: For creating your own `AppWindow` and `WebView` implementations.
- `window-management.md`: For managing windows and the event loop.
- `message-handling.md`: For handling messages from the frontend.
