/*
 * Iris is a World Generator for Minecraft Servers
 * Copyright (c) 2026 Arcane Arts (Volmit Software)
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

package art.arcane.iris.studio.view;

import art.arcane.iris.configuration.IrisSettings;
import art.arcane.iris.spi.IrisLogging;
import art.arcane.iris.generation.runtime.Engine;

import javax.swing.JFrame;
import java.awt.Desktop;
import java.awt.EventQueue;
import java.awt.GraphicsEnvironment;
import java.awt.desktop.QuitResponse;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

public final class GuiHost {
    private static final AtomicBoolean DESKTOP_QUIT_GUARD_INSTALLED = new AtomicBoolean(false);
    private static final Set<JFrame> MANAGED_FRAMES = ConcurrentHashMap.newKeySet();
    private static volatile Provider provider = new Provider() {
    };
    private static volatile boolean desktopSuppressed = false;

    private GuiHost() {
    }

    public static void suppressDesktop(boolean suppress) {
        desktopSuppressed = suppress;
    }

    public static boolean isDesktopSuppressed() {
        return desktopSuppressed;
    }

    public interface Provider {
        default Engine findActiveEngine() {
            return null;
        }

        default void registerHotloadHook(Runnable onHotload) {
        }

        default void unregisterHotloadHook(Runnable onHotload) {
        }

        default GuiOverlay overlayFor(Engine engine, UUID openerId) {
            return null;
        }
    }

    public static void set(Provider boundProvider) {
        if (boundProvider != null) {
            provider = boundProvider;
        }
    }

    public static Provider get() {
        return provider;
    }

    public static boolean isAvailable() {
        return !desktopSuppressed && !GraphicsEnvironment.isHeadless();
    }

    public static void prepareFrame(JFrame frame) {
        frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        MANAGED_FRAMES.add(frame);
        frame.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosed(WindowEvent event) {
                MANAGED_FRAMES.remove(frame);
            }
        });
        prepareServerDesktop();
    }

    private static void prepareServerDesktop() {
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
            desktop.setQuitHandler((event, response) -> closeDesktopWindowsAndCancelQuit(response));
        } catch (Throwable error) {
            IrisLogging.reportError(error);
            IrisLogging.info("Unable to install the Iris desktop quit guard; use the server stop command instead of macOS Quit");
        }
    }

    static void closeDesktopWindowsAndCancelQuit(QuitResponse response) {
        response.cancelQuit();
        EventQueue.invokeLater(() -> {
            for (JFrame frame : MANAGED_FRAMES) {
                frame.dispose();
            }
        });
    }

    /**
     * Outcome of a server triggered desktop gui launch request.
     */
    public enum ServerGuiLaunch {
        /**
         * The gui was asked for and a display environment exists.
         */
        OPEN,
        /**
         * The gui was not asked for, or server launched guis are turned off in settings.
         */
        DISABLED,
        /**
         * The gui was asked for but the jvm is headless or the desktop is suppressed.
         */
        UNAVAILABLE
    }

    /**
     * Decides whether a server triggered job may open a desktop gui. Callers must not attempt an
     * awt launch on anything other than {@link ServerGuiLaunch#OPEN}, since awt throws on a headless jvm.
     */
    public static ServerGuiLaunch serverGuiLaunch(boolean requested) {
        if (!requested || !IrisSettings.get().getGui().isUseServerLaunchedGuis()) {
            return ServerGuiLaunch.DISABLED;
        }

        return isAvailable() ? ServerGuiLaunch.OPEN : ServerGuiLaunch.UNAVAILABLE;
    }
}
