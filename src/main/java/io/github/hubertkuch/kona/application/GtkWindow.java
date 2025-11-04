package io.github.hubertkuch.kona.application;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.foreign.*;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Linux window strategy implementation using GTK 3 and Project Panama.
 * This class must be closed (e.g., via try-with-resources) to free native resources.
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

    /**
     * Checks if this strategy can be used on the current system.
     * @return true if GTK 3 library is found, false otherwise.
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
     * This is the Java method that will be called *by C* when the window is destroyed.
     * It MUST match the C callback signature: (GtkWidget* widget, gpointer user_data)
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
     * This is the Java method called *by C* (via g_idle_add) to run tasks from the queue.
     * It MUST match the C callback signature: (gpointer user_data)
     *
     * @return 0 (G_SOURCE_REMOVE) to ensure the callback only runs once per schedule.
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
     * Schedules a task to be run on the main GTK thread.
     * This method IS thread-safe.
     *
     * @param task The task to execute.
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
        }
    }
}