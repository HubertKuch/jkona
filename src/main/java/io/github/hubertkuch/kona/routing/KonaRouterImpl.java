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

import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class KonaRouterImpl implements KonaRouter {

    private final Gson gson = new Gson();
    private final Type messageType = new TypeToken<Map<String, String>>() {}.getType();

    private record HandlerTarget(Object instance, Method method, Class<?> payloadType) {}
    private final Map<String, Map<String, HandlerTarget>> routes = new HashMap<>();

    public KonaRouterImpl() {
        System.out.println("[KonaRouter] Initialized.");
    }

    public void registerPackage(String packageName) {
        Reflections reflections = new Reflections(packageName);
        Set<Class<?>> controllerClasses = reflections.getTypesAnnotatedWith(KonaController.class);

        System.out.println("[KonaRouter] Scanning " + packageName + "...");
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
                                System.err.println("WARNING: Payload type " + payloadType.getName() +
                                        " does not implement Payload interface.");
                            }
                        } else if (method.getParameterCount() > 1) {
                            System.err.println("WARNING: @MessageHandler " + actionName +
                                    " has > 1 param. Only one (Payload object) or zero params are supported.");
                        }

                        actionMap.put(actionName, new HandlerTarget(controllerInstance, method, payloadType));
                        System.out.println("  -> Registered: " + controllerName + " -> " + actionName);
                    }
                }
                routes.put(controllerName, actionMap);

            } catch (Exception e) {
                System.err.println("Failed to register controller: " + controllerClass.getName());
                e.printStackTrace();
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
                System.err.println("[KonaRouter] Invalid message: 'controller' or 'action' missing.");
                return;
            }

            Map<String, HandlerTarget> actionMap = routes.get(controllerName);
            if (actionMap == null) {
                System.err.println("[KonaRouter] No controller found: " + controllerName);
                return;
            }

            HandlerTarget target = actionMap.get(actionName);
            if (target == null) {
                System.err.println("[KonaRouter] No action found: " + controllerName + " -> " + actionName);
                return;
            }

            // 4. NEW INVOCATION LOGIC
            if (target.payloadType() != null) {
                // Method expects a payload object.
                if (payloadElement == null || payloadElement.isJsonNull()) {
                    System.err.println("[KonaRouter] Action " + actionName + " expected a payload, but got null.");
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
            System.err.println("[KonaRouter] Error processing message: " + message);
            e.printStackTrace();
        }
    }
}