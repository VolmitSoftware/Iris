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

package art.arcane.iris.modded.command;

import art.arcane.iris.configuration.IrisSettings;
import art.arcane.iris.studio.view.GuiHost;
import art.arcane.iris.studio.view.GuiOverlay;
import art.arcane.iris.generation.runtime.Engine;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class ModdedGuiHost implements GuiHost.Provider {
    private static final ModdedGuiHost INSTANCE = new ModdedGuiHost();

    private final Map<Engine, ServerLevel> levels = new ConcurrentHashMap<>();
    private final Map<Engine, UUID> openers = new ConcurrentHashMap<>();
    private volatile Engine active;
    private volatile MinecraftServer server;

    private ModdedGuiHost() {
    }

    public static void install() {
        GuiHost.set(INSTANCE);
    }

    public static void bindContext(MinecraftServer server, ServerLevel level, Engine engine, UUID opener) {
        INSTANCE.server = server;
        INSTANCE.active = engine;
        INSTANCE.levels.put(engine, level);
        if (opener == null) {
            INSTANCE.openers.remove(engine);
        } else {
            INSTANCE.openers.put(engine, opener);
        }
    }

    /**
     * Drops the GUI binding for an evicted engine. Without this the host pinned every
     * GUI-bound Engine, its ServerLevel and transitively the MinecraftServer for the process
     * lifetime — there was no remove path at all.
     */
    public static void unbind(Engine engine) {
        if (engine == null) {
            return;
        }
        INSTANCE.levels.remove(engine);
        INSTANCE.openers.remove(engine);
        if (INSTANCE.active == engine) {
            INSTANCE.active = null;
        }
    }

    public static void clear() {
        INSTANCE.levels.clear();
        INSTANCE.openers.clear();
        INSTANCE.active = null;
        INSTANCE.server = null;
    }

    public static boolean isGuiLaunchable() {
        return GuiHost.isAvailable() && IrisSettings.get().getGui().isUseServerLaunchedGuis();
    }

    public static String guiUnavailableReason() {
        if (GuiHost.isDesktopSuppressed()) {
            return "running inside a Minecraft client (desktop GUIs disabled to avoid a client crash)";
        }
        if (!GuiHost.isAvailable()) {
            return "headless JVM (no display)";
        }
        return "gui.useServerLaunchedGuis=false in Iris settings";
    }

    @Override
    public Engine findActiveEngine() {
        Engine current = active;
        if (current != null && !current.isClosed()) {
            return current;
        }
        for (Map.Entry<Engine, ServerLevel> entry : levels.entrySet()) {
            if (!entry.getKey().isClosed()) {
                return entry.getKey();
            }
        }
        return null;
    }

    @Override
    public GuiOverlay overlayFor(Engine engine, UUID openerId) {
        if (engine == null) {
            return null;
        }
        ServerLevel level = levels.get(engine);
        if (level == null || server == null) {
            return null;
        }
        UUID resolvedOpenerId = openerId == null ? openers.get(engine) : openerId;
        return new ModdedVisionOverlay(server, level, engine, resolvedOpenerId);
    }
}
