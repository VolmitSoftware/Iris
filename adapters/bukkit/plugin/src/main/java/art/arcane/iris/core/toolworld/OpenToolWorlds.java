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

package art.arcane.iris.core.toolworld;

import art.arcane.iris.core.IrisWorldStorage;
import org.bukkit.World;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The tool worlds that are open or opening: found by the Bukkit world they live in, and by the tool's name for them
 * within a pack. A world is registered before Bukkit has created it, since chunks load while it is created; until it
 * is bound to its Bukkit world it is found by the logical name of its storage, which the creator fixes beforehand.
 */
final class OpenToolWorlds {
    private final Map<String, BukkitToolWorld> byStorageName = new ConcurrentHashMap<>();
    private final Map<UUID, BukkitToolWorld> byWorldId = new ConcurrentHashMap<>();
    private final Map<String, BukkitToolWorld> byPackAndName = new ConcurrentHashMap<>();

    /**
     * Registers {@code world} under its storage name and the name its tool gave it.
     *
     * @return whether the tool's name was free; when it is not, nothing is registered
     */
    boolean register(BukkitToolWorld world) {
        if (byPackAndName.putIfAbsent(packAndName(world.packName(), world.name()), world) != null) {
            return false;
        }
        byStorageName.put(world.storageName(), world);
        return true;
    }

    /** Records the Bukkit world of {@code world}, which is bound already. */
    void bound(BukkitToolWorld world) {
        byWorldId.put(world.worldId(), world);
    }

    void unregister(BukkitToolWorld world) {
        byStorageName.remove(world.storageName(), world);
        byPackAndName.remove(packAndName(world.packName(), world.name()), world);
        if (world.isBound()) {
            byWorldId.remove(world.worldId(), world);
        }
    }

    /** The tool world that {@code world} is; empty for every other world. */
    Optional<BukkitToolWorld> forWorld(World world) {
        if (world == null) {
            return Optional.empty();
        }
        BukkitToolWorld bound = byWorldId.get(world.getUID());
        return bound != null ? Optional.of(bound) : forOpeningWorld(world);
    }

    Optional<BukkitToolWorld> forName(String packName, String name) {
        return Optional.ofNullable(byPackAndName.get(packAndName(packName, name)));
    }

    List<BukkitToolWorld> all() {
        return new ArrayList<>(byStorageName.values());
    }

    private Optional<BukkitToolWorld> forOpeningWorld(World world) {
        boolean anyOpening = byStorageName.values().stream().anyMatch(candidate -> !candidate.isBound());
        if (!anyOpening) {
            return Optional.empty();
        }
        return Optional.ofNullable(byStorageName.get(IrisWorldStorage.logicalName(world)));
    }

    private static String packAndName(String packName, String name) {
        return packName + "/" + name;
    }
}
