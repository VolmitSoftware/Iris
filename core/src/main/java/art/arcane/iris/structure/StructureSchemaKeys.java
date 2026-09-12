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

package art.arcane.iris.structure;

import art.arcane.volmlib.util.collection.KList;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.TreeSet;

public final class StructureSchemaKeys {
    private StructureSchemaKeys() {
    }

    public static KList<String> collect(Collection<String> structureKeys, Collection<String> jigsawPieceKeys) {
        Set<String> pieces = new HashSet<>();
        if (jigsawPieceKeys != null) {
            for (String piece : jigsawPieceKeys) {
                if (piece != null && !piece.isBlank()) {
                    pieces.add(piece);
                }
            }
        }

        TreeSet<String> merged = new TreeSet<>();
        if (structureKeys != null) {
            for (String key : structureKeys) {
                if (key == null || key.isBlank()) {
                    continue;
                }
                if (pieces.contains(key)) {
                    continue;
                }
                merged.add(key);
            }
        }

        return new KList<>(merged);
    }
}
