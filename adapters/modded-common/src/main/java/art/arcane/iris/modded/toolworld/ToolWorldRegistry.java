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

package art.arcane.iris.modded.toolworld;

import net.minecraft.server.level.ServerLevel;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/** The tool worlds that exist, from before their level is made until it is gone. */
final class ToolWorldRegistry {
    static final ToolWorldRegistry INSTANCE = new ToolWorldRegistry();

    private final Map<String, ModdedToolWorld> byDimensionId = new ConcurrentHashMap<>();

    private ToolWorldRegistry() {
    }

    /** Whether any tool world exists; the mixins ask this first so a server with none pays one read. */
    boolean hasWorlds() {
        return !byDimensionId.isEmpty();
    }

    /** Registers {@code world}; false when its pack has a tool world of that name already. */
    synchronized boolean register(ModdedToolWorld world) {
        if (forName(world.packName(), world.name()).isPresent()) {
            return false;
        }
        byDimensionId.put(world.dimensionId(), world);
        return true;
    }

    void unregister(ModdedToolWorld world) {
        byDimensionId.remove(world.dimensionId(), world);
    }

    Optional<ModdedToolWorld> forLevel(ServerLevel level) {
        return Optional.ofNullable(byDimensionId.get(level.dimension().identifier().toString()));
    }

    Optional<ModdedToolWorld> forName(String packName, String name) {
        return byDimensionId.values().stream()
                .filter(world -> world.packName().equals(packName) && world.name().equals(name))
                .findFirst();
    }

    List<ModdedToolWorld> all() {
        return List.copyOf(byDimensionId.values());
    }

    void clear() {
        byDimensionId.clear();
    }
}
