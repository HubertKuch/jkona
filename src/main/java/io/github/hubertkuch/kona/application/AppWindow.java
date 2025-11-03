package io.github.hubertkuch.kona.application;

public interface AppWindow {
    boolean initialize();
    long createWindow(String title, int width, int height);
    void showWindow();
    void runEventLoop();
    void addWidget(long windowHandle, long widgetHandle);
}
