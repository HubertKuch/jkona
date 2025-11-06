package io.github.hubertkuch.kona.message;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a class as a message controller that can receive and handle messages from the frontend.
 * Each controller is identified by a unique name, which is used for routing incoming messages.
 * <p>
 * Example:
 * <pre>{@code
 * @KonaController(name = "users")
 * public class UserController {
 *     // ... message handler methods
 * }
 * }</pre>
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface KonaController {
    /**
     * The unique name of the controller.
     * This name is used in the frontend to specify the target controller for a message.
     *
     * @return The name of the controller.
     */
    String name();
}
