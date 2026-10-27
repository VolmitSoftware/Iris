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

package art.arcane.iris.core.pack;

import art.arcane.iris.builtin.objects.ObjectPlacement;
import art.arcane.volmlib.util.collection.KSet;

/**
 * Shared key collection for the pack packagers. Both the Bukkit re-serializing compiler and the
 * modded verbatim-copy compiler must export the complete ambient-spawning graph: object placements
 * carry markers, markers carry spawners, spawners carry entities, entities carry loot. These
 * helpers keep the two packagers walking placements identically.
 */
public final class PackExportClosure {
    private PackExportClosure() {
    }

    public static KSet<String> collectObjectKeys(Iterable<ObjectPlacement> placements) {
        KSet<String> objectKeys = new KSet<>();
        if (placements == null) {
            return objectKeys;
        }
        for (ObjectPlacement placement : placements) {
            if (placement == null) {
                continue;
            }
            for (String key : placement.place()) {
                if (key == null || key.isBlank()) {
                    continue;
                }
                objectKeys.add(key);
            }
        }
        return objectKeys;
    }
}
