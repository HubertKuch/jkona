package com.example;

import io.github.hubertkuch.kona.Kona;

public class Main {
    public static void main(String[] args) {
        new Kona.Builder()
                .title("Random window")
                .fullscreen(false)
                .modal(false)
                .resizable(false)
                .build()
                .run();
    }
}