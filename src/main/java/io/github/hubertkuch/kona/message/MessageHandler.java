package io.github.hubertkuch.kona.message;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a method within a {@link KonaController} as a handler for a specific message action.
 * The method will be invoked when a message with a matching action is received by the controller.
 * <p>
 * The annotated method must have a single parameter that is a subclass of {@link Payload}.
 * <p>
 * Example:
 * <pre>{@code
 * @KonaController(name = "users")
 * public class UserController {
 *     @MessageHandler(action = "get")
 *     public void getUser(GetUserPayload payload) {
 *         // ... handle the message
 *     }
 * }
 * }</pre>
 */
@Target({ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface MessageHandler {
    /**
     * The action name that this method handles.
     * This name is used in the frontend to specify the target action for a message.
     *
     * @return The action name.
     */
    String action();
}
