package io.github.hubertkuch.kona.application;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.foreign.*;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * A Linux-specific implementation of {@link AppWindow} that uses GTK 3 for creating and managing the application window.
 * This class leverages the Foreign Function & Memory API (Project Panama) to interact with the native GTK libraries.
 * <p>
 * It handles window creation, the main event loop, and provides a mechanism for scheduling tasks to run on the main GTK thread.
 * This class must be closed (e.g., via try-with-resources) to free the native resources it allocates.
 */
public class GtkWindow implements AppWindow, AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(GtkWindow.class);
    private static final int GTK_WINDOW_TOPLEVEL = 0;

    private Arena arena;
    private Linker linker;

    private SymbolLookup gtkLib;
    private final ConcurrentLinkedQueue<Runnable> taskQueue = new ConcurrentLinkedQueue<>();

    private MethodHandle gtkInit;
    private MethodHandle gtkWindowNew;
    private MethodHandle gtkWindowSetTitle;
    private MethodHandle gtkWindowSetDefaultSize;
    private SymbolLookup gobjectLib;
    private MethodHandle gtkWidgetShowAll;
    private MethodHandle gtkContainerAdd;
    private MethodHandle gtkWindowSetPosition;
    private MethodHandle gtkMain;
    private MethodHandle gtkMainQuit;
    private MethodHandle gSignalConnect;
    private MethodHandle gIdleAdd;
    private MemorySegment onWindowDestroyStub;
    private MemorySegment idleCallbackStub;
    private MethodHandle gtkWindowUnfullscreen;
    private MethodHandle gtkWindowSetResizable;
    private MethodHandle gtkWindowFullscreen;

    /**
     * Checks if the required native libraries for this windowing implementation are available.
     *
     * @return {@code true} if both GTK3 and GObject libraries are found, {@code false} otherwise.
     */
    public static boolean isSupported() {
        try (Arena checkArena = Arena.ofShared()) {
            SymbolLookup.libraryLookup("libgtk-3.so", checkArena);
            SymbolLookup.libraryLookup("libgobject-2.0.so", checkArena);

            return true;
        } catch (Throwable e) {
            return false;
        }
    }

    /**
     * Callback method invoked from native code when the GTK window is destroyed (e.g., by closing it).
     * This method is an upcall from C, triggered by the "destroy" signal of the GtkWindow.
     * Its primary role is to terminate the GTK main event loop.
     * <p>
     * The method signature MUST match the one expected by the GCallback for the "destroy" signal:
     * {@code (GtkWidget *widget, gpointer user_data)}.
     *
     * @param widget   A pointer to the GtkWidget that emitted the signal (the window).
     * @param userData User data passed to the signal connection (in this case, a pointer to this GtkWindow instance).
     */
    public void onWindowDestroyed(MemorySegment widget, MemorySegment userData) {
        log.info("===> UPCALL: Window is closing! Quitting main loop.");
        try {
            gtkMainQuit.invokeExact();
        } catch (Throwable e) {
            log.error("Error in onWindowDestroyed",e);
        }
    }

    /**
     * Callback method invoked from native code via {@code g_idle_add} to process tasks from the queue.
     * This method runs on the main GTK thread when the event loop is idle.
     * <p>
     * The method signature MUST match the one expected by GLib's GSourceFunc: {@code (gpointer user_data)}.
     *
     * @param userData User data passed to the callback (not used here).
     * @return {@code 0} (G_SOURCE_REMOVE) to ensure the idle source is automatically removed after execution.
     *         A new idle source will be added if more tasks are scheduled.
     */
    public int onIdleCallback(MemorySegment userData) {
        Runnable task = taskQueue.poll();
        if (task != null) {
            try {
                task.run();
            } catch (Exception e) {
                log.error("Error executing scheduled task:", e);
            }
        }
        return 0;
    }

    @Override
    public boolean initialize() {
        try {
            this.arena = Arena.ofShared();
            this.linker = Linker.nativeLinker();

            this.gtkLib = SymbolLookup.libraryLookup("libgtk-3.so", this.arena);
            this.gobjectLib = SymbolLookup.libraryLookup("libgobject-2.0.so", this.arena);

            gtkInit = linker.downcallHandle(
                    gtkLib.find("gtk_init").get(),
                    FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.ADDRESS)
            );
            gtkWindowNew = linker.downcallHandle(
                    gtkLib.find("gtk_window_new").get(),
                    FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.JAVA_INT)
            );
            gtkWindowSetTitle = linker.downcallHandle(
                    gtkLib.find("gtk_window_set_title").get(),
                    FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.ADDRESS)
            );
            gtkWindowSetDefaultSize = linker.downcallHandle(
                    gtkLib.find("gtk_window_set_default_size").get(),
                    FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT)
            );
            gtkWindowSetPosition = linker.downcallHandle(
                    gtkLib.find("gtk_window_set_position").get(),
                    FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.JAVA_INT)
            );
            gtkWidgetShowAll = linker.downcallHandle(
                    gtkLib.find("gtk_widget_show_all").get(),
                    FunctionDescriptor.ofVoid(ValueLayout.ADDRESS)
            );
            gtkContainerAdd = linker.downcallHandle(gtkLib
                    .find("gtk_container_add")
                    .get(), FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.ADDRESS));
            gtkMain = linker.downcallHandle(
                    gtkLib.find("gtk_main").get(),
                    FunctionDescriptor.ofVoid()
            );
            gtkMainQuit = linker.downcallHandle(gtkLib.find("gtk_main_quit").get(), FunctionDescriptor.ofVoid());
            gSignalConnect = linker.downcallHandle(gobjectLib
                    .find("g_signal_connect_data")
                    .get(), FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_INT));

            gIdleAdd = linker.downcallHandle(gobjectLib
                    .find("g_idle_add")
                    .get(), FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS));

            gtkWindowSetResizable = linker.downcallHandle(
                    gtkLib.find("gtk_window_set_resizable").get(),
                    FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.JAVA_BOOLEAN)
            );

            gtkWindowFullscreen = linker.downcallHandle(
                    gtkLib.find("gtk_window_fullscreen").get(),
                    FunctionDescriptor.ofVoid(ValueLayout.ADDRESS)
            );

            gtkWindowUnfullscreen = linker.downcallHandle(
                    gtkLib.find("gtk_window_unfullscreen").get(),
                    FunctionDescriptor.ofVoid(ValueLayout.ADDRESS)
            );

            MethodHandle destroyHandle = MethodHandles
                    .lookup()
                    .findVirtual(GtkWindow.class, "onWindowDestroyed",
                            MethodType.methodType(void.class, MemorySegment.class, MemorySegment.class)
                    )
                    .bindTo(this);

            this.onWindowDestroyStub = linker.upcallStub(destroyHandle, FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.ADDRESS), this.arena);

            MethodHandle idleHandle = MethodHandles
                    .lookup()
                    .findVirtual(GtkWindow.class, "onIdleCallback", MethodType.methodType(int.class, MemorySegment.class))
                    .bindTo(this);

            FunctionDescriptor idleDesc = FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS);

            this.idleCallbackStub = linker.upcallStub(idleHandle, idleDesc, this.arena);

            gtkInit.invokeExact(MemorySegment.NULL, MemorySegment.NULL);
            return true;

        } catch (Throwable e) {
            if (this.arena != null) this.arena.close();
            log.error("Error during initialization", e);
            return false;
        }
    }

    @Override
    public long createWindow(String title, int width, int height) {
        try {
            MemorySegment window = (MemorySegment) gtkWindowNew.invokeExact(GTK_WINDOW_TOPLEVEL);
            MemorySegment cTitle = this.arena.allocateFrom(title);
            gtkWindowSetTitle.invokeExact(window, cTitle);
            gtkWindowSetDefaultSize.invokeExact(window, width, height);
            gtkWindowSetPosition.invokeExact(window, 1);

            MemorySegment cDestroySignal = this.arena.allocateFrom("destroy");

            gSignalConnect.invokeExact(window, cDestroySignal, this.onWindowDestroyStub, MemorySegment.NULL, MemorySegment.NULL, 0);

            return window.address();
        } catch (Throwable e) {
            log.error("Error creating window", e);
            return 0L;
        }
    }

    @Override
    public void addWidget(long windowHandle, long widgetHandle) {
        if (windowHandle == 0L || widgetHandle == 0L) {
            log.error("Invalid handles for addWidget.");
            return;
        }
        try {
            MemorySegment window = MemorySegment.ofAddress(windowHandle);
            MemorySegment widget = MemorySegment.ofAddress(widgetHandle);
            gtkContainerAdd.invokeExact(window, widget);
        } catch (Throwable e) {
            log.error("Error adding widget", e);
        }
    }

    @Override
    public void fullscreen(long windowHandle, boolean fullscreen) {
        if (windowHandle == 0L)
        {
            throw new IllegalArgumentException("Invalid window handle");
        }

        scheduleTask(() -> {
            try {
                log.info("Setting fullscreen");
                MemorySegment window = MemorySegment.ofAddress(windowHandle);

                if (fullscreen) {
                    gtkWindowFullscreen.invokeExact(window);
                } else {
                    gtkWindowUnfullscreen.invokeExact(window);
                }
            } catch (Throwable e) {
                log.error("Error setting fullscreen state:", e);
            }
        });
    }

    @Override
    public void resizable(long windowHandle, boolean fullscreen) {

    }

    @Override
    public void title(long windowHandle, String title) {

    }

    @Override
    public void modal(long windowHandle, boolean modal) {

    }

    @Override
    public void showWindow(long windowHandle) {
        if (windowHandle == 0L) {
            log.error("Cannot show window: invalid window handle.");
            return;
        }
        try {
            MemorySegment window = MemorySegment.ofAddress(windowHandle);
            gtkWidgetShowAll.invokeExact(window);
        } catch (Throwable e) {
            log.error("Error showing window", e);
        }
    }

    /**
     * Schedules a task to be run on the main GTK event loop thread (the "UI thread").
     * This is the safe way to interact with GTK widgets from other threads.
     * The task is added to a queue and processed during the GTK idle phase.
     *
     * @param task The {@link Runnable} task to execute on the GTK main thread.
     */
    public void scheduleTask(Runnable task) {
        taskQueue.offer(task);
        try {
            gIdleAdd.invoke(this.idleCallbackStub, MemorySegment.NULL);
        } catch (Throwable e) {
            log.error("Error scheduling task", e);
        }
    }

    @Override
    public void runEventLoop() {
        try {
            gtkMain.invokeExact();
        } catch (Throwable e) {
            log.error("Error in runEventLoop", e);
        }
    }

    /**
     * Closes the native memory arena, releasing associated resources.
     */
    @Override
    public void close() {
        if (this.arena != null && this.arena.scope().isAlive()) {
            this.arena.close();
            this.arena = null;
        }
    }
}