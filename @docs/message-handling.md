# Message Handling

This document explains how to handle messages from the frontend in a Kona application.

## Communication Flow

Communication between the frontend and the backend is done through JSON messages. The frontend sends messages to the backend, which are then routed to the appropriate handlers. The backend can also send messages back to the frontend.

## Frontend (JavaScript)

In the frontend, you can send a message to the backend using the `window.kona.sendMessage` function. This function takes a message object as an argument. The message object must have the following properties:

- `controller`: The name of the controller to handle the message.
- `action`: The name of the action to be performed.
- `payload`: The data to be sent to the backend.
- `callbackId` (optional): A unique identifier for the message. If provided, the backend will send a response to this callback.

```javascript
// Send a message to the backend
window.kona.sendMessage({
    controller: 'users',
    action: 'get',
    payload: { userId: '123' },
    callbackId: 'my-callback-id'
});

// Register a callback to handle the response
window.kona.addCallback('my-callback-id', (response) => {
    console.log('Response from backend:', response);
});
```

## Backend (Java)

In the backend, you can handle messages from the frontend by creating classes annotated with `@KonaController` and methods annotated with `@MessageHandler`.

### `@KonaController`

The `@KonaController` annotation marks a class as a message controller. It has a `name` property that specifies the name of the controller.

```java
@KonaController(name = "users")
public class UserController {
    // ...
}
```

### `@MessageHandler`

The `@MessageHandler` annotation marks a method as a message handler. It has an `action` property that specifies the name of the action to be handled.

The method must have a single parameter that is a subclass of `Payload`.

```java
public record GetUserPayload(String userId) implements Payload {}

@MessageHandler(action = "get")
public GetUserResponse getUser(GetUserPayload payload) {
    // ...
}
```

### `Payload`

The `Payload` interface is a marker interface for message payloads. The fields of the implementing class or record will be populated from the JSON payload of the incoming message.

```java
// Using a class
public class GetUserPayload implements Payload {
    private String userId;

    // getters and setters
}

// Using a record
public record GetUserPayload(String userId) implements Payload {}
```

### `KonaRouter`

The `KonaRouter` is responsible for routing messages to the appropriate handlers. You need to register your controller packages with the router in your main application class.

```java
var router = new KonaRouterImpl(window, webView, webViewHandle);
router.registerPackage("io.github.hubertkuch.kona.controllers");
```
