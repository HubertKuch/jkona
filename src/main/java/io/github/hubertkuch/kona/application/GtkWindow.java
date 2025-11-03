package io.github.hubertkuch.kona.application;

import java.lang.foreign.*;
import java.lang.invoke.MethodHandle;
import java.util.Optional;

/**
 * Linux window strategy implementation using GTK 3 and Project Panama.
 * This class must be closed (e.g., via try-with-resources) to free native resources.
 */
public class GtkWindow implements AppWindow, AutoCloseable {

    private static final int GTK_WINDOW_TOPLEVEL = 0;

    // Arena będzie teraz polem klasy, aby żyła tak długo, jak obiekt
    private Arena arena;

    private Linker linker;
    private SymbolLookup gtkLib;

    private MethodHandle gtkInit;
    private MethodHandle gtkWindowNew;
    private MethodHandle gtkWindowSetTitle;
    private MethodHandle gtkWindowSetDefaultSize;
    private MethodHandle gtkWidgetShowAll;
    private MethodHandle gtkMain;
    private MethodHandle gtkWindowSetPosition;

    /**
     * Checks if this strategy can be used on the current system.
     * It performs a fast check by trying to look up the required native library.
     *
     * @return true if GTK 3 library is found, false otherwise.
     */
    public static boolean isSupported() {
        try (Arena checkArena = Arena.ofConfined()) {
            SymbolLookup.libraryLookup("libgtk-3.so", checkArena);
            return true;
        } catch (Throwable e) {
            return false;
        }
    }

    @Override
    public boolean initialize() {
        try {
            this.arena = Arena.ofConfined();

            this.linker = Linker.nativeLinker();

            this.gtkLib = SymbolLookup.libraryLookup("libgtk-3.so", this.arena);

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

            gtkMain = linker.downcallHandle(
                    gtkLib.find("gtk_main").get(),
                    FunctionDescriptor.ofVoid()
            );

            gtkInit.invokeExact(MemorySegment.NULL, MemorySegment.NULL);

            return true;

        } catch (Throwable e) {
            if (this.arena != null) {
                this.arena.close();
            }
            e.printStackTrace();
            return false;
        }
    }

    @Override
    public long createWindow(String title, int width, int height) {
        // 3. NIE twórz nowej areny. Użyj tej z pola klasy.
        try {
            MemorySegment window = (MemorySegment) gtkWindowNew.invokeExact(GTK_WINDOW_TOPLEVEL);

            // 4. Alokuj pamięć używając `this.arena`
            MemorySegment cTitle = this.arena.allocateFrom(title);

            gtkWindowSetTitle.invokeExact(window, cTitle);
            gtkWindowSetDefaultSize.invokeExact(window, width, height);
            gtkWindowSetPosition.invokeExact(window, 1); // 1 = GTK_WIN_POS_CENTER

            return window.address();

        } catch (Throwable e) {
            e.printStackTrace();
            return 0L;
        }
    }

    @Override
    public void showWindow(long windowHandle) {
        if (windowHandle == 0L) {
            System.err.println("Cannot show window: invalid window handle.");
            return;
        }

        try {
            MemorySegment window = MemorySegment.ofAddress(windowHandle);
            gtkWidgetShowAll.invokeExact(window);
        } catch (Throwable e) {
            e.printStackTrace();
        }
    }

    @Override
    public void runEventLoop() {
        try {
            System.out.println("Starting GTK event loop...");
            gtkMain.invokeExact();
            System.out.println("GTK event loop finished.");
        } catch (Throwable e) {
            e.printStackTrace();
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