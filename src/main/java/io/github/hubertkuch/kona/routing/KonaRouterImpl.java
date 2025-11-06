package io.github.hubertkuch.kona.routing;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.hubertkuch.kona.message.KonaController;
import io.github.hubertkuch.kona.message.MessageHandler;
import io.github.hubertkuch.kona.message.Payload;
import org.reflections.Reflections;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.github.hubertkuch.kona.application.GtkWindow;
import io.github.hubertkuch.kona.application.WebView;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * The default implementation of the {@link KonaRouter} interface.
 * This class is responsible for scanning for {@link KonaController} annotations,
 * routing incoming messages to the appropriate {@link MessageHandler} methods,
 * and sending back responses to the frontend.
 */
public class KonaRouterImpl implements KonaRouter {

    private static final Logger log = LoggerFactory.getLogger(KonaRouterImpl.class);
    private final Gson gson = new Gson();
    private final GtkWindow window;
    private final WebView webView;
    private final long webViewHandle;

    private record HandlerTarget(Object instance, Method method, Class<?> payloadType) {}
    private final Map<String, Map<String, HandlerTarget>> routes = new HashMap<>();

    /**
     * Constructs a new KonaRouterImpl.
     *
     * @param window        The main application window, used for scheduling tasks on the UI thread.
     * @param webView       The WebView instance, used for running JavaScript.
     * @param webViewHandle The native handle of the WebView widget.
     */
    public KonaRouterImpl(GtkWindow window, WebView webView, long webViewHandle) {
        this.window = window;
        this.webView = webView;
        this.webViewHandle = webViewHandle;
        log.info("[KonaRouter] Initialized.");
    }

    /**
     * Scans the specified package for classes annotated with {@link KonaController}
     * and registers them as message handlers.
     *
     * @param packageName The name of the package to scan (e.g., "com.example.controllers").
     */
    public void registerPackage(String packageName) {
        Reflections reflections = new Reflections(packageName);
        Set<Class<?>> controllerClasses = reflections.getTypesAnnotatedWith(KonaController.class);

        log.info("[KonaRouter] Scanning {}...", packageName);
        if (controllerClasses.isEmpty()) {
            log.warn("[KonaRouter] No @KonaController classes found in package: {}", packageName);
        }
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

    /**
     * The main entry point for messages coming from the frontend. This method is called by the
     * {@link GtkWebView} when a script message is received.
     * <p>
     * It parses the JSON message, identifies the target controller and action, deserializes the payload,
     * invokes the appropriate handler method, and sends back a response if a callback ID is provided.
     *
     * @param message The raw JSON message string from the frontend.
     *                The expected format is:
     *                <pre>{@code
     *                {
     *                  "controller": "controllerName",
     *                  "action": "actionName",
     *                  "payload": { ... },
     *                  "callbackId": "uniqueId"
     *                }
     *                }</pre>
     */
    @Override
    public void onMessage(String message) {
        try {
            JsonObject messageObject = JsonParser.parseString(message).getAsJsonObject();

            String controllerName = messageObject.get("controller").getAsString();
            String actionName = messageObject.get("action").getAsString();
            String callbackId = messageObject.has("callbackId") ? messageObject.get("callbackId").getAsString() : null;
            JsonElement payloadElement = messageObject.get("payload");

            if (controllerName == null || actionName == null) {
                log.error("[KonaRouter] Invalid message: 'controller' or 'action' missing.");
                return;
            }

            HandlerTarget target = findHandler(controllerName, actionName);
            if (target == null) return; // Error already logged in findHandler

            Object result = invokeHandler(target, payloadElement);

            if (callbackId != null && result != null) {
                sendResponse(callbackId, result);
            }

        } catch (Exception e) {
            log.error("[KonaRouter] Error processing message: {}", message, e);
        }
    }

    private HandlerTarget findHandler(String controllerName, String actionName) {
        Map<String, HandlerTarget> actionMap = routes.get(controllerName);
        if (actionMap == null) {
            log.error("[KonaRouter] No controller found: {}", controllerName);
            return null;
        }
        HandlerTarget target = actionMap.get(actionName);
        if (target == null) {
            log.error("[KonaRouter] No action found: {} -> {}", controllerName, actionName);
            return null;
        }
        return target;
    }

    private Object invokeHandler(HandlerTarget target, JsonElement payloadElement) throws Exception {
        if (target.payloadType() != null) {
            if (payloadElement == null || payloadElement.isJsonNull()) {
                log.error("[KonaRouter] Action {} expected a payload, but got null.", target.method().getName());
                return null;
            }
            Object payloadObject = gson.fromJson(payloadElement, target.payloadType());
            return target.method().invoke(target.instance(), payloadObject);
        } else {
            return target.method().invoke(target.instance());
        }
    }

    private void sendResponse(String callbackId, Object result) {
        try {
            String jsonResult = gson.toJson(result);
            String escapedJson = jsonResult.replace("\\", "\\\\").replace("'", "\\'");

            String js = String.format("window.kona.resolveCallback('%s', '%s');", callbackId, escapedJson);

            window.scheduleTask(() -> {
                webView.runJavaScript(webViewHandle, js);
            });
        } catch (Exception e) {
            log.error("[KonaRouter] Failed to send response for callbackId: {}", callbackId, e);
        }
    }
}