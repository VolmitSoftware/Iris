/*
 * Iris is a World Generator for Minecraft Bukkit Servers
 * Copyright (c) 2022 Arcane Arts (Volmit Software)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package art.arcane.iris.studio.map;

import art.arcane.iris.spi.IrisLogging;
import org.lwjgl.PointerBuffer;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GLXCapabilities;
import org.lwjgl.system.Callback;
import org.lwjgl.system.CallbackI;
import org.lwjgl.system.Configuration;
import org.lwjgl.system.JNI;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.linux.DynamicLinkLoader;
import org.lwjgl.system.linux.X11;
import org.lwjgl.system.linux.XErrorEvent;

import java.awt.GraphicsEnvironment;
import java.awt.Toolkit;
import java.lang.invoke.MethodHandles;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import static org.lwjgl.opengl.GL11.GL_RENDERER;
import static org.lwjgl.opengl.GL11.glGetString;
import static org.lwjgl.opengl.GLX.glXDestroyContext;
import static org.lwjgl.opengl.GLX.glXMakeCurrent;
import static org.lwjgl.opengl.GLX.glXSwapBuffers;
import static org.lwjgl.opengl.GLX13.GLX_BLUE_SIZE;
import static org.lwjgl.opengl.GLX13.GLX_DEPTH_SIZE;
import static org.lwjgl.opengl.GLX13.GLX_DOUBLEBUFFER;
import static org.lwjgl.opengl.GLX13.GLX_DRAWABLE_TYPE;
import static org.lwjgl.opengl.GLX13.GLX_GREEN_SIZE;
import static org.lwjgl.opengl.GLX13.GLX_RED_SIZE;
import static org.lwjgl.opengl.GLX13.GLX_RENDER_TYPE;
import static org.lwjgl.opengl.GLX13.GLX_RGBA_BIT;
import static org.lwjgl.opengl.GLX13.GLX_VISUAL_ID;
import static org.lwjgl.opengl.GLX13.GLX_WINDOW_BIT;
import static org.lwjgl.opengl.GLX13.GLX_X_RENDERABLE;
import static org.lwjgl.opengl.GLX13.glXChooseFBConfig;
import static org.lwjgl.opengl.GLX13.glXGetFBConfigAttrib;
import static org.lwjgl.opengl.GLX13.glXMakeContextCurrent;
import static org.lwjgl.opengl.GLXARBCreateContext.GLX_CONTEXT_MAJOR_VERSION_ARB;
import static org.lwjgl.opengl.GLXARBCreateContext.GLX_CONTEXT_MINOR_VERSION_ARB;
import static org.lwjgl.opengl.GLXARBCreateContext.glXCreateContextAttribsARB;
import static org.lwjgl.opengl.GLXARBCreateContextProfile.GLX_CONTEXT_CORE_PROFILE_BIT_ARB;
import static org.lwjgl.opengl.GLXARBCreateContextProfile.GLX_CONTEXT_PROFILE_MASK_ARB;
import static org.lwjgl.opengl.GLXEXTSwapControl.glXSwapIntervalEXT;
import static org.lwjgl.system.APIUtil.apiClosureRet;
import static org.lwjgl.system.APIUtil.apiCreateCIF;
import static org.lwjgl.system.MemoryUtil.NULL;
import static org.lwjgl.system.MemoryUtil.memAddress;
import static org.lwjgl.system.MemoryUtil.memGetAddress;
import static org.lwjgl.system.libffi.LibFFI.ffi_type_pointer;
import static org.lwjgl.system.libffi.LibFFI.ffi_type_sint32;

/**
 * A connection of our own to the X server Swing's windows are on, with one OpenGL context that draws straight into
 * those windows, so a frame never has to be copied back and painted by Swing. Only for the GPU thread.
 */
final class GlxDisplay {
    private static final String X11_TOOLKIT = "sun.awt.X11.XToolkit";
    private static final String NATIVE_CONTEXT_API = "native";
    private static final String NVIDIA_DRIVER_VERSION_FILE = "/proc/driver/nvidia/version";
    private static final Set<String> SOFTWARE_RENDERERS = Set.of("llvmpipe", "softpipe", "software rasterizer");
    private static final int OPENGL_MAJOR_VERSION = 3;
    private static final int OPENGL_MINOR_VERSION = 3;
    private static final int COLOR_CHANNEL_BITS = 8;
    private static final int DEPTH_BITS = 24;
    // Frames are only drawn when something moves, and the compositor keeps them from tearing.
    private static final int SWAP_EVERY_FRAME = 0;

    private final long display;
    private final long context;
    private final long visualId;
    private final boolean canSetSwapInterval;
    private final Set<Long> windowsWithSwapIntervalSet = new HashSet<>();
    private final String renderer;
    private long currentWindow;

    private GlxDisplay(long display, long context, long visualId, boolean canSetSwapInterval, String renderer) {
        this.display = display;
        this.context = context;
        this.visualId = visualId;
        this.canSetSwapInterval = canSetSwapInterval;
        this.renderer = renderer;
    }

    /**
     * Opens the connection and makes its context current on this thread, without any window yet.
     *
     * @throws IllegalStateException when Swing doesn't draw on X or no card there draws OpenGL 3.3 in hardware
     */
    static GlxDisplay open() {
        if (!isSwingOnX()) {
            throw new IllegalStateException("Swing doesn't draw on X");
        }
        preferNvidiaCard();
        // LWJGL would take EGL in a Wayland session, even though Swing's windows are on X.
        Configuration.OPENGL_CONTEXT_API.set(NATIVE_CONTEXT_API);
        GL.create();
        long display = X11.nXOpenDisplay(NULL);
        if (display == NULL) {
            GL.destroy();
            throw new IllegalStateException("Can't connect to the X server");
        }
        XErrors.watch(display);
        try {
            return openOn(display);
        } catch (RuntimeException failure) {
            XErrors.forget(display);
            X11.XCloseDisplay(display);
            GL.destroy();
            throw failure;
        }
    }

    static boolean isSwingOnX() {
        return !GraphicsEnvironment.isHeadless() && X11_TOOLKIT.equals(Toolkit.getDefaultToolkit().getClass().getName());
    }

    private static GlxDisplay openOn(long display) {
        int screen = X11.XDefaultScreen(display);
        GLXCapabilities glx = GL.createCapabilitiesGLX(display, screen);
        if (!glx.GLX13 || !glx.GLX_ARB_create_context || !glx.GLX_ARB_create_context_profile) {
            throw new IllegalStateException("GLX can't make an OpenGL " + OPENGL_MAJOR_VERSION + "." + OPENGL_MINOR_VERSION + " context");
        }
        long visualId = Xlib.defaultVisualId(display, screen);
        long config = configFor(display, screen, visualId);
        long context = glXCreateContextAttribsARB(display, config, NULL, true, new int[]{
                GLX_CONTEXT_MAJOR_VERSION_ARB, OPENGL_MAJOR_VERSION,
                GLX_CONTEXT_MINOR_VERSION_ARB, OPENGL_MINOR_VERSION,
                GLX_CONTEXT_PROFILE_MASK_ARB, GLX_CONTEXT_CORE_PROFILE_BIT_ARB,
                0});
        Xlib.sync(display);
        if (context == NULL || XErrors.takeFailed(display)) {
            throw new IllegalStateException("GLX can't make an OpenGL " + OPENGL_MAJOR_VERSION + "." + OPENGL_MINOR_VERSION + " context");
        }
        if (!glXMakeContextCurrent(display, NULL, NULL, context)) {
            glXDestroyContext(display, context);
            throw new IllegalStateException("GLX can't use a context without a window");
        }
        GL.createCapabilities();
        String renderer = glGetString(GL_RENDERER);
        if (isDrawnInSoftware(renderer)) {
            glXMakeContextCurrent(display, NULL, NULL, NULL);
            glXDestroyContext(display, context);
            throw new IllegalStateException("GLX only draws in software, on " + renderer);
        }
        return new GlxDisplay(display, context, visualId, glx.GLX_EXT_swap_control, renderer);
    }

    /**
     * Laptops show X on the built-in card and only lend the faster one out when asked to. Asked the way NVIDIA's own
     * prime-run does, unless whoever started the server asked for something else already.
     */
    private static void preferNvidiaCard() {
        if (!Files.exists(Path.of(NVIDIA_DRIVER_VERSION_FILE))) {
            return;
        }
        LibC.setenvUnlessSet("__NV_PRIME_RENDER_OFFLOAD", "1");
        LibC.setenvUnlessSet("__GLX_VENDOR_LIBRARY_NAME", "nvidia");
    }

    // Windows are only drawn into with the visual they were made with; Swing makes them with the screen's default one.
    private static long configFor(long display, int screen, long visualId) {
        PointerBuffer configs = glXChooseFBConfig(display, screen, new int[]{
                GLX_X_RENDERABLE, 1,
                GLX_DRAWABLE_TYPE, GLX_WINDOW_BIT,
                GLX_RENDER_TYPE, GLX_RGBA_BIT,
                GLX_DOUBLEBUFFER, 1,
                GLX_RED_SIZE, COLOR_CHANNEL_BITS,
                GLX_GREEN_SIZE, COLOR_CHANNEL_BITS,
                GLX_BLUE_SIZE, COLOR_CHANNEL_BITS,
                GLX_DEPTH_SIZE, DEPTH_BITS,
                0});
        if (configs == null) {
            throw new IllegalStateException("GLX has no double-buffered config with a depth buffer");
        }
        try {
            int[] configVisual = new int[1];
            for (int index = 0; index < configs.remaining(); index++) {
                long config = configs.get(index);
                glXGetFBConfigAttrib(display, config, GLX_VISUAL_ID, configVisual);
                if (configVisual[0] == visualId) {
                    return config;
                }
            }
        } finally {
            X11.XFree(configs);
        }
        throw new IllegalStateException("GLX can't draw into windows of the default visual 0x" + Long.toHexString(visualId));
    }

    private static boolean isDrawnInSoftware(String renderer) {
        String lowerCase = renderer == null ? "" : renderer.toLowerCase(Locale.ROOT);
        return SOFTWARE_RENDERERS.stream().anyMatch(lowerCase::contains);
    }

    String renderer() {
        return renderer;
    }

    boolean canDrawInto(long windowVisualId) {
        return windowVisualId == visualId;
    }

    /**
     * Makes the window what the default framebuffer draws into.
     *
     * @return false when the window can't be drawn into
     */
    boolean drawInto(long window) {
        if (window == currentWindow) {
            return true;
        }
        boolean madeCurrent = glXMakeCurrent(display, window, context);
        Xlib.sync(display);
        if (!madeCurrent || XErrors.takeFailed(display)) {
            currentWindow = NULL;
            return false;
        }
        currentWindow = window;
        if (canSetSwapInterval && windowsWithSwapIntervalSet.add(window)) {
            glXSwapIntervalEXT(display, window, SWAP_EVERY_FRAME);
        }
        return true;
    }

    /**
     * Puts what was drawn into the window on screen.
     *
     * @return false when the window couldn't take it, or something drawn into it before failed
     */
    boolean show(long window) {
        glXSwapBuffers(display, window);
        return !XErrors.takeFailed(display);
    }

    /**
     * Stops drawing into a window before it goes away, keeping the context for the rest.
     */
    void forget(long window) {
        windowsWithSwapIntervalSet.remove(window);
        if (window != currentWindow) {
            return;
        }
        glXMakeContextCurrent(display, NULL, NULL, context);
        currentWindow = NULL;
        Xlib.sync(display);
        XErrors.takeFailed(display);
    }

    /**
     * Destroys the context and the connection. Nothing is drawn through this display afterwards.
     */
    void close() {
        glXMakeContextCurrent(display, NULL, NULL, NULL);
        glXDestroyContext(display, context);
        currentWindow = NULL;
        windowsWithSwapIntervalSet.clear();
        Xlib.sync(display);
        XErrors.forget(display);
        X11.XCloseDisplay(display);
    }

    /**
     * Notes X errors on our own connection instead of letting Xlib end the process over them, which is what it does
     * for any connection but Swing's. Errors on other connections go wherever they went before.
     */
    private static final class XErrors {
        private static final Set<Long> watchedDisplays = ConcurrentHashMap.newKeySet();
        private static final Set<Long> failedDisplays = ConcurrentHashMap.newKeySet();
        private static long previousHandler;
        private static ErrorHandler handler;

        static synchronized void watch(long display) {
            if (handler == null) {
                handler = XErrors::handle;
                previousHandler = Xlib.setErrorHandler(handler.address());
            }
            watchedDisplays.add(display);
        }

        static void forget(long display) {
            watchedDisplays.remove(display);
            failedDisplays.remove(display);
        }

        static boolean takeFailed(long display) {
            return failedDisplays.remove(display);
        }

        private static int handle(long display, long errorEvent) {
            if (watchedDisplays.contains(display)) {
                failedDisplays.add(display);
                IrisLogging.debug("X error " + Byte.toUnsignedInt(XErrorEvent.create(errorEvent).error_code()) + " drawing a 3D view");
                return 0;
            }
            return previousHandler == NULL ? 0 : JNI.callPPI(display, errorEvent, previousHandler);
        }
    }

    @FunctionalInterface
    private interface ErrorHandler extends CallbackI {
        Callback.Descriptor DESCRIPTOR = new Callback.Descriptor(ErrorHandler.class, MethodHandles.lookup(),
                apiCreateCIF(ffi_type_sint32, ffi_type_pointer, ffi_type_pointer));

        @Override
        default Callback.Descriptor getDescriptor() {
            return DESCRIPTOR;
        }

        @Override
        default void callback(long ret, long args) {
            apiClosureRet(ret, invoke(memGetAddress(memGetAddress(args)), memGetAddress(memGetAddress(args + Long.BYTES))));
        }

        int invoke(long display, long errorEvent);
    }

    // The few Xlib calls LWJGL has no binding for.
    private static final class Xlib {
        static long defaultVisualId(long display, int screen) {
            long visual = JNI.callPP(display, screen, function("XDefaultVisual"));
            return JNI.callPP(visual, function("XVisualIDFromVisual"));
        }

        static void sync(long display) {
            JNI.callPI(display, 0, function("XSync"));
        }

        static long setErrorHandler(long handler) {
            return JNI.callPP(handler, function("XSetErrorHandler"));
        }

        private static long function(String name) {
            long address = X11.getLibrary().getFunctionAddress(name);
            if (address == NULL) {
                throw new IllegalStateException("Xlib has no " + name);
            }
            return address;
        }
    }

    private static final class LibC {
        private static final int KEEP_EXISTING = 0;
        private static final long EVERYTHING_LOADED = NULL;

        // Looked up among everything the process loaded, which LWJGL's own dlsym won't do.
        static void setenvUnlessSet(String name, String value) {
            try (MemoryStack stack = MemoryStack.stackPush()) {
                long setenv = DynamicLinkLoader.ndlsym(EVERYTHING_LOADED, memAddress(stack.ASCII("setenv")));
                if (setenv == NULL) {
                    return;
                }
                JNI.callPPI(memAddress(stack.ASCII(name)), memAddress(stack.ASCII(value)), KEEP_EXISTING, setenv);
            }
        }
    }
}
