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

import art.arcane.iris.generation.runtime.Engine;
import art.arcane.iris.generation.runtime.PreservationRegistry;
import art.arcane.iris.spi.IrisServices;
import art.arcane.iris.studio.view.GuiHost;
import art.arcane.iris.studio.view.GuiOverlay;

import javax.swing.JFrame;
import java.awt.Dimension;
import java.awt.GraphicsEnvironment;
import java.awt.Rectangle;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.image.BufferedImage;
import java.util.UUID;
import java.util.concurrent.ExecutorService;

/**
 * What the studio windows need from the server they run in: frames it closes on shutdown, engines and hotload
 * notices from the platform's {@link GuiHost.Provider}, and threads it stops with the plugin.
 */
public final class MapHost {
    private MapHost() {
    }

    static boolean isAvailable() {
        return GuiHost.isAvailable();
    }

    public static JFrame createFrame(String title) {
        XwaylandWindowManagerHint.apply();
        StudioTheme.install();
        JFrame frame = new JFrame(title);
        GuiHost.prepareFrame(frame);
        frame.addWindowListener(new WindowAdapter() {
            @Override
            public void windowActivated(WindowEvent event) {
                StudioTheme.followSystem();
            }
        });
        return frame;
    }

    /**
     * Wraps rendered pixels in an image Java2D draws straight from memory. Left to itself Java2D copies every image it
     * draws into a texture inside the display server; with a map's hundreds of tiles that crashed Xwayland's Intel driver
     * and took every X window on the desktop down with it.
     */
    public static BufferedImage uncachedImage(int[] pixels, int width, int height) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        image.getRaster().setDataElements(0, 0, width, height, pixels);
        image.setAccelerationPriority(0F);
        return image;
    }

    // A scaled-up default size can be bigger than a small or low resolution screen.
    public static Dimension fitToScreen(Dimension preferred) {
        Rectangle usable = GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds();
        return new Dimension(Math.min(preferred.width, usable.width), Math.min(preferred.height, usable.height));
    }

    static Engine findActiveEngine() {
        return usable(GuiHost.get().findActiveEngine());
    }

    /**
     * The engine of the world the window was opened for, or the active one on platforms that can't look worlds up.
     */
    static Engine findEngine(UUID worldId) {
        Engine engine = worldId == null ? null : usable(GuiHost.get().findEngine(worldId));
        return engine != null ? engine : findActiveEngine();
    }

    static GuiOverlay overlayFor(Engine engine, UUID openerId) {
        return engine == null ? null : GuiHost.get().overlayFor(engine, openerId);
    }

    static void registerHotloadHook(Runnable onHotload) {
        GuiHost.get().registerHotloadHook(onHotload);
    }

    static void unregisterHotloadHook(Runnable onHotload) {
        GuiHost.get().unregisterHotloadHook(onHotload);
    }

    static void interruptOnPluginShutdown(ExecutorService executor) {
        PreservationRegistry preservation = IrisServices.getOrNull(PreservationRegistry.class);
        if (preservation != null) {
            preservation.register(executor);
        }
    }

    static void interruptOnPluginShutdown(Thread thread) {
        PreservationRegistry preservation = IrisServices.getOrNull(PreservationRegistry.class);
        if (preservation != null) {
            preservation.register(thread);
        }
    }

    private static Engine usable(Engine engine) {
        return engine == null || engine.isClosed() || engine.isClosing() ? null : engine;
    }
}
