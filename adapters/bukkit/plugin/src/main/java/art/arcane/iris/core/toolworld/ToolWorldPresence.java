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

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Who stands in one tool world, so that the platform's several ways of arriving and leaving (join, teleport, world
 * change, respawn, quit) tell the tool once for each stay: {@code entered} on the first of them, {@code left} on the
 * first of them after.
 */
final class ToolWorldPresence {
    private final Set<UUID> present = ConcurrentHashMap.newKeySet();

    /** Whether the player was not here before, which is when the tool hears {@code entered}. */
    boolean enter(UUID player) {
        return present.add(player);
    }

    /** Whether the player was here, which is when the tool hears {@code left}. */
    boolean leave(UUID player) {
        return present.remove(player);
    }

    boolean isPresent(UUID player) {
        return present.contains(player);
    }
}
