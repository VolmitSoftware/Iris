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

package com.volmit.iris.core.gui;

import com.volmit.iris.Iris;
import com.volmit.iris.core.events.IrisEngineHotloadEvent;
import com.volmit.iris.core.service.PreservationSVC;
import com.volmit.iris.core.tools.IrisToolbelt;
import com.volmit.iris.engine.framework.Engine;
import com.volmit.iris.engine.platform.PlatformChunkGenerator;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

import javax.swing.JFrame;
import java.awt.Desktop;
import java.awt.EventQueue;
import java.awt.GraphicsEnvironment;
import java.awt.desktop.QuitResponse;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.ArrayList;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;

public final class GuiHost {
    private static final AtomicBoolean DESKTOP_QUIT_GUARD_INSTALLED = new AtomicBoolean(false);
    private static final Set<JFrame> MANAGED_FRAMES = ConcurrentHashMap.newKeySet();
    private static final Map<Runnable, Listener> HOTLOAD_HOOKS = new ConcurrentHashMap<>();

    private GuiHost() {
    }

    public static boolean isAvailable() {
        return !GraphicsEnvironment.isHeadless();
    }

    public static JFrame createFrame(String title) {
        XwaylandWindowManagerHint.apply();
        StudioTheme.install();
        JFrame frame = new JFrame(title);
        frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        MANAGED_FRAMES.add(frame);
        frame.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosed(WindowEvent event) {
                MANAGED_FRAMES.remove(frame);
            }
        });
        installMacQuitGuard();
        return frame;
    }

    public static Engine findActiveEngine() {
        try {
            for (World world : new ArrayList<>(Bukkit.getWorlds())) {
                Engine engine = engineOf(world);
                if (engine != null) {
                    return engine;
                }
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    public static Engine findEngine(UUID worldId) {
        World world = Bukkit.getWorld(worldId);
        return world == null ? null : engineOf(world);
    }

    public static void registerHotloadHook(Runnable onHotload) {
        Listener listener = new HotloadListener(onHotload);
        HOTLOAD_HOOKS.put(onHotload, listener);
        Iris.instance.registerListener(listener);
    }

    public static void unregisterHotloadHook(Runnable onHotload) {
        Listener listener = HOTLOAD_HOOKS.remove(onHotload);
        if (listener != null) {
            Iris.instance.unregisterListener(listener);
        }
    }

    static void interruptOnPluginShutdown(ExecutorService executor) {
        PreservationSVC preservation = Iris.service(PreservationSVC.class);
        if (preservation != null) {
            preservation.register(executor);
        }
    }

    static void interruptOnPluginShutdown(Thread thread) {
        PreservationSVC preservation = Iris.service(PreservationSVC.class);
        if (preservation != null) {
            preservation.register(thread);
        }
    }

    private static Engine engineOf(World world) {
        try {
            PlatformChunkGenerator access = IrisToolbelt.access(world);
            if (access != null && access.getEngine() != null && !access.getEngine().isClosed()) {
                return access.getEngine();
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    // macOS Quit in the dock menu would otherwise stop the whole server.
    private static void installMacQuitGuard() {
        if (!isAvailable()
                || !System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("mac")
                || !DESKTOP_QUIT_GUARD_INSTALLED.compareAndSet(false, true)) {
            return;
        }

        try {
            if (!Desktop.isDesktopSupported()) {
                return;
            }
            Desktop desktop = Desktop.getDesktop();
            if (!desktop.isSupported(Desktop.Action.APP_QUIT_HANDLER)) {
                return;
            }
            desktop.setQuitHandler((event, response) -> closeWindowsAndCancelQuit(response));
        } catch (Throwable e) {
            Iris.reportError(e);
            Iris.info("Unable to install the Iris desktop quit guard; use the server stop command instead of macOS Quit");
        }
    }

    private static void closeWindowsAndCancelQuit(QuitResponse response) {
        response.cancelQuit();
        EventQueue.invokeLater(() -> {
            for (JFrame frame : MANAGED_FRAMES) {
                frame.dispose();
            }
        });
    }

    private static final class HotloadListener implements Listener {
        private final Runnable onHotload;

        private HotloadListener(Runnable onHotload) {
            this.onHotload = onHotload;
        }

        @EventHandler
        public void on(IrisEngineHotloadEvent event) {
            onHotload.run();
        }
    }
}
