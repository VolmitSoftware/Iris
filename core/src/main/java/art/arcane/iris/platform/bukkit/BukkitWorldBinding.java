/*
 * Iris is a World Generator for Minecraft Bukkit Servers
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

package art.arcane.iris.platform.bukkit;

import art.arcane.iris.world.IrisWorldStorage;
import art.arcane.iris.world.IrisWorld;
import art.arcane.iris.spi.IrisLogging;
import art.arcane.volmlib.util.bukkit.WorldIdentity;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.generator.WorldInfo;

import java.util.Collection;
import java.util.List;

public final class BukkitWorldBinding {
    private BukkitWorldBinding() {
    }

    public static IrisWorld bind(IrisWorld target, WorldInfo worldInfo) {
        target.platformIdentity(WorldIdentity.key(worldInfo).toString())
                .name(worldInfo.getName())
                .worldFolder(IrisWorldStorage.dimensionRoot(worldInfo))
                .minHeight(worldInfo.getMinHeight())
                .maxHeight(worldInfo.getMaxHeight());
        if (worldInfo instanceof World world) {
            target.platformWorld(new BukkitWorld(world));
        }
        return target;
    }

    public static boolean tryBind(IrisWorld target) {
        if (target.hasPlatformWorld()) {
            return true;
        }
        NamespacedKey key = NamespacedKey.fromString(target.identity());
        if (key == null) {
            return false;
        }
        World world = WorldIdentity.resolve(key).orElse(null);
        if (world == null) {
            return false;
        }
        bind(target, world);
        return true;
    }

    public static World world(IrisWorld target) {
        if (target == null || !target.hasPlatformWorld()) {
            return null;
        }
        return BukkitPlatform.unwrapWorld(target.platformWorld());
    }

    public static List<Player> players(IrisWorld target) {
        World world = world(target);
        return world == null ? List.of() : world.getPlayers();
    }

    public static <T extends Entity> Collection<? extends T> entities(IrisWorld target, Class<T> type) {
        World world = world(target);
        return world == null ? List.of() : world.getEntitiesByClass(type);
    }

    public static Location spawnLocation(IrisWorld target) {
        World world = world(target);
        if (world != null) {
            return world.getSpawnLocation();
        }
        IrisLogging.error("This world is not real yet, cannot get spawn location! HEADLESS!");
        return null;
    }
}
