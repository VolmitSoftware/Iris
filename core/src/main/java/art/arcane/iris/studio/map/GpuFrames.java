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

import java.util.Optional;

import static org.lwjgl.opengl.GL33.*;

/**
 * Runs a 3D view's frame on the graphics card, straight into the view's window where frames can go there, or else into
 * a picture read back for Swing. A card that fails once isn't used again, as it would most likely fail every frame,
 * each time only after costing the frame its time.
 */
final class GpuFrames {
    private static final double COLOR_CHANNEL_MAX = 255D;

    private static GpuFramebuffer offScreen;
    private static volatile boolean failed;

    private GpuFrames() {
    }

    /**
     * A frame drawn on the card's thread.
     */
    @FunctionalInterface
    interface Drawing {
        /**
         * @return the frame, or nothing when the card can't draw this scene and it should be drawn without one
         */
        Optional<OrbitPicture> drawInto(GpuFrameTarget target);
    }

    /**
     * @return the picture, or nothing when the caller should draw it without a card
     */
    static Optional<OrbitPicture> drawPicture(Drawing drawing) {
        return onCard(gpu -> drawing.drawInto(offScreenFramebuffer()));
    }

    static boolean canDrawOnScreen() {
        return !failed && GpuContext.drawsIntoWindows();
    }

    /**
     * @return the frame, or nothing when it can't go straight into the window and the caller should draw a picture
     */
    static Optional<OrbitPicture> drawOnScreen(ScreenTarget screen, Drawing drawing) {
        return onCard(gpu -> {
            Optional<GlxDisplay> windows = gpu.windows();
            long window = screen.canvas().window();
            if (windows.isEmpty() || window == 0L) {
                return Optional.empty();
            }
            try {
                if (!windows.get().canDrawInto(screen.canvas().visualId())) {
                    throw new IllegalStateException("The view's window has another visual than the one GLX draws with");
                }
                if (!windows.get().drawInto(window)) {
                    throw new IllegalStateException("GLX can't draw into the view's window");
                }
                return drawing.drawInto(new GpuWindowFrame(windows.get(), window, screen.seeThrough()));
            } catch (RuntimeException failure) {
                gpu.stopDrawingIntoWindows(failure);
                return Optional.empty();
            }
        });
    }

    static void failOnError() {
        int error = glGetError();
        if (error != GL_NO_ERROR) {
            throw new IllegalStateException("OpenGL error 0x" + Integer.toHexString(error));
        }
    }

    /**
     * Clears to the views' background, and sets up for opaque drawing with depth.
     */
    static void clearToBackground() {
        int background = StudioTheme.EDITOR_BACKGROUND.getRGB();
        glClearColor(channel(background, 16), channel(background, 8), channel(background, 0), 1F);
        glClearDepth(1D);
        glDepthMask(true);
        glEnable(GL_DEPTH_TEST);
        glDepthFunc(GL_LESS);
        glDisable(GL_BLEND);
        glDisable(GL_CULL_FACE);
        glDisable(GL_POLYGON_OFFSET_FILL);
        glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
    }

    static float channel(int color, int shift) {
        return (float) (((color >>> shift) & 0xFF) / COLOR_CHANNEL_MAX);
    }

    private static GpuFramebuffer offScreenFramebuffer() {
        if (offScreen == null) {
            offScreen = new GpuFramebuffer();
        }
        return offScreen;
    }

    private static Optional<OrbitPicture> onCard(CardWork work) {
        if (failed) {
            return Optional.empty();
        }
        Optional<GpuContext> gpu = GpuContext.shared();
        if (gpu.isEmpty()) {
            return Optional.empty();
        }
        try {
            return gpu.get().call(() -> work.drawOn(gpu.get()));
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            return Optional.empty();
        } catch (RuntimeException failure) {
            stopUsingCard(failure);
            return Optional.empty();
        }
    }

    private static void stopUsingCard(RuntimeException failure) {
        failed = true;
        IrisLogging.reportError(failure);
        IrisLogging.warn("3D views draw without a graphics card from now on: %s", failure.getCause() == null ? failure : failure.getCause());
    }

    @FunctionalInterface
    private interface CardWork {
        Optional<OrbitPicture> drawOn(GpuContext gpu);
    }
}
