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

package art.arcane.iris.studio.view;

import art.arcane.iris.configuration.IrisSettings;
import art.arcane.iris.generation.runtime.Engine;
import art.arcane.iris.spi.IrisLogging;
import art.arcane.iris.studio.bridge.StudioBridges;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** What the studio app needs from the platform that hosts the engines, and the shutdown of what the studio opened. */
public final class GuiHost {
    private static final Set<Runnable> SHUTDOWN_TASKS = ConcurrentHashMap.newKeySet();
    private static volatile Provider provider = new Provider() {
    };
    private static volatile boolean desktopSuppressed = false;

    private GuiHost() {
    }

    /** Suppressed where the studio app must not open, such as inside a game client. */
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

        default Engine findEngine(UUID worldId) {
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
        return !desktopSuppressed;
    }

    /** Runs {@code task} once the studio is closed at shutdown, for resources it shares. */
    public static void runAtShutdown(Runnable task) {
        SHUTDOWN_TASKS.add(task);
    }

    /** Closes the studio's bridges and the app windows the server started before the plugin stops, then runs the shutdown tasks. */
    public static void shutdown() {
        StudioBridges.closeAll();
        for (Runnable task : SHUTDOWN_TASKS) {
            try {
                task.run();
            } catch (Throwable error) {
                IrisLogging.reportError(error);
            }
        }
        SHUTDOWN_TASKS.clear();
    }

    /**
     * Outcome of a server triggered studio launch request.
     */
    public enum ServerGuiLaunch {
        /**
         * The studio was asked for and may open.
         */
        OPEN,
        /**
         * The studio was not asked for, or server launched guis are turned off in settings.
         */
        DISABLED,
        /**
         * The studio was asked for but the desktop is suppressed.
         */
        UNAVAILABLE
    }

    /**
     * Decides whether a server triggered job may open the studio app.
     */
    public static ServerGuiLaunch serverGuiLaunch(boolean requested) {
        if (!requested || !IrisSettings.get().getGui().isUseServerLaunchedGuis()) {
            return ServerGuiLaunch.DISABLED;
        }

        return isAvailable() ? ServerGuiLaunch.OPEN : ServerGuiLaunch.UNAVAILABLE;
    }
}
