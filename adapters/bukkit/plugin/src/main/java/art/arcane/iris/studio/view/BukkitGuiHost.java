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

import art.arcane.iris.Iris;
import art.arcane.iris.world.event.IrisEngineHotloadEvent;
import art.arcane.iris.world.IrisToolbelt;
import art.arcane.iris.generation.runtime.Engine;
import art.arcane.iris.platform.generation.PlatformChunkGenerator;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

import java.util.ArrayList;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class BukkitGuiHost implements GuiHost.Provider {
    private final Map<Runnable, Listener> hotloadHooks = new ConcurrentHashMap<>();

    public static void install() {
        GuiHost.set(new BukkitGuiHost());
    }

    @Override
    public Engine findActiveEngine() {
        try {
            for (World world : new ArrayList<>(Bukkit.getWorlds())) {
                try {
                    PlatformChunkGenerator access = IrisToolbelt.access(world);
                    if (access != null && access.getEngine() != null && !access.getEngine().isClosed()) {
                        return access.getEngine();
                    }
                } catch (Throwable ignored) {
                }
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    @Override
    public void registerHotloadHook(Runnable onHotload) {
        Listener listener = new HotloadListener(onHotload);
        hotloadHooks.put(onHotload, listener);
        Iris.instance.registerListener(listener);
    }

    @Override
    public void unregisterHotloadHook(Runnable onHotload) {
        Listener listener = hotloadHooks.remove(onHotload);
        if (listener != null) {
            Iris.instance.unregisterListener(listener);
        }
    }

    @Override
    public GuiOverlay overlayFor(Engine engine, UUID openerId) {
        return engine == null ? null : new BukkitVisionOverlay(engine, openerId);
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
