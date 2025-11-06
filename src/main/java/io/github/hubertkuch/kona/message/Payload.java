package io.github.hubertkuch.kona.message;

/**
 * A marker interface for message payloads.
 * All classes that are used as parameters for methods annotated with {@link MessageHandler}
 * must implement this interface.
 * <p>
 * The fields of the implementing class will be populated from the JSON payload of the incoming message.
 * <p>
 * Example:
 * <pre>{@code
 * public class GetUserPayload implements Payload {
 *     private String userId;
 *
 *     // ... getters and setters
 * }
 * }</pre>
 */
public interface Payload {
}
