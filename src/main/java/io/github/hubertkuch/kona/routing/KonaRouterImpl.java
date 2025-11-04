package io.github.hubertkuch.kona.routing;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.reflect.TypeToken;
import io.github.hubertkuch.kona.message.KonaController;
import io.github.hubertkuch.kona.message.MessageHandler;
import io.github.hubertkuch.kona.message.Payload;
import org.reflections.Reflections;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class KonaRouterImpl implements KonaRouter {

    private static final Logger log = LoggerFactory.getLogger(KonaRouterImpl.class);
    private final Gson gson = new Gson();
    private final Type messageType = new TypeToken<Map<String, String>>() {}.getType();

    private record HandlerTarget(Object instance, Method method, Class<?> payloadType) {}
    private final Map<String, Map<String, HandlerTarget>> routes = new HashMap<>();

    public KonaRouterImpl() {
        log.info("[KonaRouter] Initialized.");
    }

    public void registerPackage(String packageName) {
        Reflections reflections = new Reflections(packageName);
        Set<Class<?>> controllerClasses = reflections.getTypesAnnotatedWith(KonaController.class);

        log.info("[KonaRouter] Scanning {}...", packageName);
        for (Class<?> controllerClass : controllerClasses) {
            try {
                KonaController controllerAnnotation = controllerClass.getAnnotation(KonaController.class);
                String controllerName = controllerAnnotation.name();
                Object controllerInstance = controllerClass.getDeclaredConstructor().newInstance();

                Map<String, HandlerTarget> actionMap = new HashMap<>();
                for (Method method : controllerClass.getDeclaredMethods()) {
                    if (method.isAnnotationPresent(MessageHandler.class)) {
                        MessageHandler handlerAnnotation = method.getAnnotation(MessageHandler.class);
                        String actionName = handlerAnnotation.action();

                        Class<?> payloadType = null;
                        if (method.getParameterCount() == 1) {
                            payloadType = method.getParameterTypes()[0];
                            if (! Payload.class.isAssignableFrom(payloadType)) {
                                log.warn("Payload type {} does not implement Payload interface.", payloadType.getName());
                            }
                        } else if (method.getParameterCount() > 1) {
                            log.warn("@MessageHandler {} has > 1 param. Only one (Payload object) or zero params are supported.", actionName);
                        }

                        actionMap.put(actionName, new HandlerTarget(controllerInstance, method, payloadType));
                        log.info("  -> Registered: {} -> {}", controllerName, actionName);
                    }
                }
                routes.put(controllerName, actionMap);

            } catch (Exception e) {
                log.error("Failed to register controller: {}", controllerClass.getName(), e);
            }
        }
    }

    @Override
    public void onMessage(String message) {
        try {
            // 1. Parse the whole message into a generic JSON object
            JsonObject messageObject = JsonParser.parseString(message).getAsJsonObject();

            // 2. Get controller and action as strings
            String controllerName = messageObject.get("controller").getAsString();
            String actionName = messageObject.get("action").getAsString();

            // 3. Get the payload as a JsonElement
            JsonElement payloadElement = messageObject.get("payload");

            if (controllerName == null || actionName == null) {
                log.error("[KonaRouter] Invalid message: 'controller' or 'action' missing.");
                return;
            }

            Map<String, HandlerTarget> actionMap = routes.get(controllerName);
            if (actionMap == null) {
                log.error("[KonaRouter] No controller found: {}", controllerName);
                return;
            }

            HandlerTarget target = actionMap.get(actionName);
            if (target == null) {
                log.error("[KonaRouter] No action found: {} -> {}", controllerName, actionName);
                return;
            }

            // 4. NEW INVOCATION LOGIC
            if (target.payloadType() != null) {
                // Method expects a payload object.
                if (payloadElement == null || payloadElement.isJsonNull()) {
                    log.error("[KonaRouter] Action {} expected a payload, but got null.", actionName);
                    return;
                }
                // Deserialize directly from the JsonElement into the target class
                Object payloadObject = gson.fromJson(payloadElement, target.payloadType());
                target.method().invoke(target.instance(), payloadObject);
            } else {
                // Method expects no parameters
                target.method().invoke(target.instance());
            }

        } catch (Exception e) {
            log.error("[KonaRouter] Error processing message: {}", message, e);
        }
    }
}