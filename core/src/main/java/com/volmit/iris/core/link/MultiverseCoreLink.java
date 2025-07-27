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

package com.volmit.iris.core.link;

import lombok.SneakyThrows;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.mvplugins.multiverse.core.MultiverseCoreApi;
import org.mvplugins.multiverse.core.world.MultiverseWorld;
import org.mvplugins.multiverse.core.world.WorldManager;
import org.mvplugins.multiverse.core.world.options.ImportWorldOptions;

public class MultiverseCoreLink {
    private final boolean active;

    public MultiverseCoreLink() {
        active = Bukkit.getPluginManager().getPlugin("Multiverse-Core") != null;
    }

    public void removeFromConfig(World world) {
        removeFromConfig(world.getName());
    }

    public void removeFromConfig(String world) {
        if (!active) return;
        var manager = worldManager();
        manager.removeWorld(world).onSuccess(manager::saveWorldsConfig);
    }

    @SneakyThrows
    public void updateWorld(World bukkitWorld, String pack) {
        if (!active) return;
        var generator = "Iris:" + pack;
        var manager = worldManager();
        var world = manager.getWorld(bukkitWorld).getOrElse(() -> {
            var options = ImportWorldOptions.worldName(bukkitWorld.getName())
                    .generator(generator)
                    .environment(bukkitWorld.getEnvironment())
                    .useSpawnAdjust(false);
            return manager.importWorld(options).get();
        });

        world.setAutoLoad(false);
        if (!generator.equals(world.getGenerator())) {
            var field = MultiverseWorld.class.getDeclaredField("worldConfig");
            field.setAccessible(true);

            var config = field.get(world);
            config.getClass()
                    .getDeclaredMethod("setGenerator", String.class)
                    .invoke(config, generator);
        }

        manager.saveWorldsConfig();
    }

    private WorldManager worldManager() {
        var api = MultiverseCoreApi.get();
        return api.getWorldManager();
    }
}
