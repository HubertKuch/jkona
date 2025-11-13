package com.example;

import io.github.hubertkuch.kona.message.KonaController;
import io.github.hubertkuch.kona.message.MessageHandler;
import io.github.hubertkuch.kona.message.Payload;

@KonaController(name = "my-controller")
public class CustomController {
    public record MyResponse(String data) implements Payload {}
    public record MyPayload(String name) implements Payload {}

    @MessageHandler(action = "my-action")
    public MyResponse action(MyPayload payload) {
        return new MyResponse(payload.name.toUpperCase());
    }
}
