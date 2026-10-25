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
import art.arcane.iris.studio.view.GuiHost;
import org.lwjgl.PointerBuffer;
import org.lwjgl.egl.EGL;
import org.lwjgl.opengl.GL;
import org.lwjgl.system.Configuration;

import java.util.Arrays;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.lwjgl.egl.EGL15.EGL_CONTEXT_MAJOR_VERSION;
import static org.lwjgl.egl.EGL15.EGL_CONTEXT_MINOR_VERSION;
import static org.lwjgl.egl.EGL15.EGL_CONTEXT_OPENGL_CORE_PROFILE_BIT;
import static org.lwjgl.egl.EGL15.EGL_CONTEXT_OPENGL_PROFILE_MASK;
import static org.lwjgl.egl.EGL15.EGL_EXTENSIONS;
import static org.lwjgl.egl.EGL15.EGL_NONE;
import static org.lwjgl.egl.EGL15.EGL_NO_CONTEXT;
import static org.lwjgl.egl.EGL15.EGL_NO_DISPLAY;
import static org.lwjgl.egl.EGL15.EGL_NO_SURFACE;
import static org.lwjgl.egl.EGL15.EGL_OPENGL_API;
import static org.lwjgl.egl.EGL15.EGL_OPENGL_BIT;
import static org.lwjgl.egl.EGL15.EGL_PBUFFER_BIT;
import static org.lwjgl.egl.EGL15.EGL_RENDERABLE_TYPE;
import static org.lwjgl.egl.EGL15.EGL_SURFACE_TYPE;
import static org.lwjgl.egl.EGL15.eglBindAPI;
import static org.lwjgl.egl.EGL15.eglChooseConfig;
import static org.lwjgl.egl.EGL15.eglCreateContext;
import static org.lwjgl.egl.EGL15.eglDestroyContext;
import static org.lwjgl.egl.EGL15.eglInitialize;
import static org.lwjgl.egl.EGL15.eglMakeCurrent;
import static org.lwjgl.egl.EGL15.eglQueryString;
import static org.lwjgl.egl.EGL15.eglReleaseThread;
import static org.lwjgl.egl.EGL15.eglTerminate;
import static org.lwjgl.egl.EXTDeviceEnumeration.eglQueryDevicesEXT;
import static org.lwjgl.egl.EXTDeviceQuery.eglQueryDeviceStringEXT;
import static org.lwjgl.egl.EXTPlatformBase.eglGetPlatformDisplayEXT;
import static org.lwjgl.egl.EXTPlatformDevice.EGL_PLATFORM_DEVICE_EXT;
import static org.lwjgl.opengl.GL11.GL_RENDERER;
import static org.lwjgl.opengl.GL11.glGetString;

/**
 * An OpenGL context on a graphics card, and the one thread allowed to use it. Where Swing draws on X, the context draws
 * straight into Swing's windows through GLX. Anywhere else, or when GLX only draws in software, it is made through
 * EGL without any window: EGL lists the cards and the first one that isn't drawn in software is taken.
 */
final class GpuContext {
    private static final String THREAD_NAME = "Iris GPU";
    private static final long RELEASE_WAIT_SECONDS = 5L;
    private static final int OPENGL_MAJOR_VERSION = 3;
    private static final int OPENGL_MINOR_VERSION = 3;
    private static final String SOFTWARE_DEVICE_EXTENSION = "EGL_MESA_device_software";
    private static final Set<String> REQUIRED_CLIENT_EXTENSIONS = Set.of("EGL_EXT_device_enumeration", "EGL_EXT_device_query",
            "EGL_EXT_platform_base", "EGL_EXT_platform_device");

    private static volatile GpuContext shared;
    private static boolean unavailable;

    private final ExecutorService thread = Executors.newSingleThreadExecutor(runnable -> {
        Thread gpuThread = new Thread(runnable, THREAD_NAME);
        gpuThread.setDaemon(true);
        return gpuThread;
    });
    private String renderer;
    private GlxDisplay windows;
    private volatile boolean drawsIntoWindows;
    private long eglDisplay = EGL_NO_DISPLAY;
    private long eglContext = EGL_NO_CONTEXT;

    private GpuContext() {
    }

    /**
     * Whether frames can go straight into windows, as far as is known without opening the context.
     */
    static boolean drawsIntoWindows() {
        GpuContext context = shared;
        return context != null && context.drawsIntoWindows;
    }

    /**
     * Only for the context's thread.
     *
     * @return the connection drawing into windows, or nothing when frames are only drawn off screen
     */
    Optional<GlxDisplay> windows() {
        return drawsIntoWindows ? Optional.of(windows) : Optional.empty();
    }

    /**
     * Stops drawing into a window about to be destroyed, waiting for a frame going into it to finish first.
     */
    static void forgetWindow(long window) {
        GpuContext context = shared;
        if (context == null || context.windows == null) {
            return;
        }
        try {
            context.call(() -> {
                context.windows.forget(window);
                return null;
            });
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        } catch (RuntimeException failure) {
            IrisLogging.reportError(failure);
        }
    }

    // Off-screen frames still work in the same context, read back like on a machine without GLX.
    void stopDrawingIntoWindows(RuntimeException failure) {
        if (!drawsIntoWindows) {
            return;
        }
        drawsIntoWindows = false;
        IrisLogging.reportError(failure);
        IrisLogging.warn("3D views stop drawing straight into windows: %s", failure.getMessage());
    }

    /**
     * @return the context every view shares, opened on first use, or nothing when this machine has no usable card
     */
    static synchronized Optional<GpuContext> shared() {
        if (shared == null && !unavailable) {
            shared = open();
            unavailable = shared == null;
        }
        return Optional.ofNullable(shared);
    }

    private static GpuContext open() {
        GpuContext context = new GpuContext();
        try {
            context.thread.submit(context::openOnCard).get();
            IrisLogging.info(context.drawsIntoWindows ? "3D views draw on %s, straight into their windows" : "3D views draw on %s",
                    context.renderer);
            releaseOnShutdown();
            return context;
        } catch (ExecutionException failure) {
            IrisLogging.warn("3D views draw without a graphics card: %s", failure.getCause());
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
        context.thread.shutdownNow();
        return null;
    }

    // NVIDIA's driver crashes the JVM on its way out when a context is still current on some thread.
    private static void releaseOnShutdown() {
        GuiHost.runAtShutdown(GpuContext::releaseShared);
    }

    /**
     * Lets go of the card once frames already asked for are drawn. Frames asked for later are drawn without it.
     */
    static synchronized void releaseShared() {
        GpuContext context = shared;
        if (context == null) {
            return;
        }
        context.thread.execute(context::release);
        context.thread.shutdown();
        try {
            if (!context.thread.awaitTermination(RELEASE_WAIT_SECONDS, TimeUnit.SECONDS)) {
                IrisLogging.warn("3D views didn't let go of the graphics card in time");
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }

    private void release() {
        drawsIntoWindows = false;
        if (windows != null) {
            windows.close();
            return;
        }
        eglMakeCurrent(eglDisplay, EGL_NO_SURFACE, EGL_NO_SURFACE, EGL_NO_CONTEXT);
        eglDestroyContext(eglDisplay, eglContext);
        eglTerminate(eglDisplay);
        eglReleaseThread();
    }

    /**
     * Runs {@code work} on the context's thread and waits for it.
     */
    <T> T call(Callable<T> work) throws InterruptedException {
        try {
            return thread.submit(work).get();
        } catch (ExecutionException failure) {
            throw new IllegalStateException("Drawing on the graphics card failed", failure.getCause());
        }
    }

    // GL would otherwise look for a GLX library as soon as it loads, which a context from EGL doesn't use.
    private Void openOnCard() {
        Configuration.OPENGL_EXPLICIT_INIT.set(true);
        try {
            windows = GlxDisplay.open();
            renderer = windows.renderer();
            drawsIntoWindows = true;
            return null;
        } catch (RuntimeException | LinkageError noWindows) {
            IrisLogging.info("3D views can't draw straight into windows: %s", noWindows);
        }
        openWithoutWindows();
        return null;
    }

    private void openWithoutWindows() {
        if (!canListDevices()) {
            throw new IllegalStateException("EGL can't list graphics cards");
        }
        GL.create(EGL.getFunctionProvider());
        for (long device : devices()) {
            if (!isDrawnInSoftware(device) && makeCurrentOn(device)) {
                GL.createCapabilities();
                renderer = glGetString(GL_RENDERER);
                return;
            }
        }
        throw new IllegalStateException("No graphics card supports OpenGL " + OPENGL_MAJOR_VERSION + "." + OPENGL_MINOR_VERSION);
    }

    // LWJGL's own capability flags read false for these even where the library has them, so the list is asked for.
    private static boolean canListDevices() {
        String extensions = eglQueryString(EGL_NO_DISPLAY, EGL_EXTENSIONS);
        return extensions != null && Arrays.asList(extensions.split(" ")).containsAll(REQUIRED_CLIENT_EXTENSIONS);
    }

    private static long[] devices() {
        int[] count = new int[1];
        eglQueryDevicesEXT(null, count);
        PointerBuffer found = PointerBuffer.allocateDirect(count[0]);
        eglQueryDevicesEXT(found, count);
        long[] devices = new long[count[0]];
        for (int device = 0; device < devices.length; device++) {
            devices[device] = found.get(device);
        }
        return devices;
    }

    private static boolean isDrawnInSoftware(long device) {
        String extensions = eglQueryDeviceStringEXT(device, EGL_EXTENSIONS);
        return extensions != null && extensions.contains(SOFTWARE_DEVICE_EXTENSION);
    }

    // Nothing is drawn to a surface, only to framebuffers of the context's own, so the context needs none. Configs
    // default to window surfaces, which a card without a display has none of.
    private boolean makeCurrentOn(long device) {
        long display = eglGetPlatformDisplayEXT(EGL_PLATFORM_DEVICE_EXT, device, (int[]) null);
        if (display == EGL_NO_DISPLAY || !eglInitialize(display, new int[1], new int[1])) {
            return false;
        }
        PointerBuffer config = PointerBuffer.allocateDirect(1);
        int[] configCount = new int[1];
        int[] wanted = {EGL_SURFACE_TYPE, EGL_PBUFFER_BIT, EGL_RENDERABLE_TYPE, EGL_OPENGL_BIT, EGL_NONE};
        if (!eglChooseConfig(display, wanted, config, configCount) || configCount[0] == 0
                || !eglBindAPI(EGL_OPENGL_API)) {
            eglTerminate(display);
            return false;
        }
        long context = eglCreateContext(display, config.get(0), EGL_NO_CONTEXT, new int[]{
                EGL_CONTEXT_MAJOR_VERSION, OPENGL_MAJOR_VERSION,
                EGL_CONTEXT_MINOR_VERSION, OPENGL_MINOR_VERSION,
                EGL_CONTEXT_OPENGL_PROFILE_MASK, EGL_CONTEXT_OPENGL_CORE_PROFILE_BIT,
                EGL_NONE});
        if (context == EGL_NO_CONTEXT || !eglMakeCurrent(display, EGL_NO_SURFACE, EGL_NO_SURFACE, context)) {
            eglTerminate(display);
            return false;
        }
        eglDisplay = display;
        eglContext = context;
        return true;
    }
}
