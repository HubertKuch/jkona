package io.github.hubertkuch.kona.routing;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import io.github.hubertkuch.kona.message.KonaController;
import io.github.hubertkuch.kona.message.MessageHandler;
import org.reflections.Reflections;

import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class KonaRouterImpl implements KonaRouter {

    private final Gson gson = new Gson();
    private final Type messageType = new TypeToken<Map<String, String>>() {}.getType();

    private record HandlerTarget(Object instance, Method method) {}
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

                        if (method.getParameterCount() > 1) {
                            System.err.println("WARNING: @MessageHandler " + actionName + " has > 1 param. Only one (String payload) is supported.");
                        }

                        actionMap.put(actionName, new HandlerTarget(controllerInstance, method));
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
            Map<String, String> messageData = gson.fromJson(message, messageType);
            String controllerName = messageData.get("controller");
            String actionName = messageData.get("action");
            String payload = messageData.get("payload");

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

            if (target.method().getParameterCount() == 1) {
                target.method().invoke(target.instance(), payload);
            } else {
                target.method().invoke(target.instance());
            }

        } catch (Exception e) {
            System.err.println("[KonaRouter] Error processing message: " + message);
            e.printStackTrace();
        }
    }
}