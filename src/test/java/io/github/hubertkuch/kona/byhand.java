package io.github.hubertkuch.kona;

import io.github.hubertkuch.kona.message.KonaController;
import io.github.hubertkuch.kona.message.MessageHandler;
import io.github.hubertkuch.kona.message.Payload;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;

public class byhand {
    private static final Logger log = LoggerFactory.getLogger(byhand.class);

    public static void main(String[] args) {
        // The application will automatically detect the environment.
        // If /webapp/index.html is found, it will run in production mode.
        // Otherwise, it will fall back to development mode and load from http://localhost:5173.
        new Kona.Builder()
                .title("My Kona App")
                .fullscreen(true)
                .resizable(false)
                .modal(false)
                .build()
                .run();

    }

    @KonaController(name = "test")
    public static class TestController {

        public TestController() {}


        public record TestPayload(String message) implements Payload {}

        public record TestResponse(String response) implements Payload {}


        @MessageHandler(action = "test")
        public TestResponse test(TestPayload payload) {
            log.info("From javascript => {}", payload.message);

            return new TestResponse("Hello from Java! Its now %s".formatted(Instant.now().toString()));
        }
    }

}






