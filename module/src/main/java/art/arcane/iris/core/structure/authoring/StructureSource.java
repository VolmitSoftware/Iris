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

package art.arcane.iris.core.structure.authoring;

import java.util.Objects;

public record StructureSource(Kind kind, StructureKey key, String version, String contentHash) {
    public StructureSource {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(version, "version");
        Objects.requireNonNull(contentHash, "contentHash");
        if (!contentHash.isEmpty() && !StructureHash.isSha256(contentHash)) {
            throw new IllegalArgumentException("Source content hash must be SHA-256");
        }
    }

    public static StructureSource of(Kind kind, StructureKey key) {
        return new StructureSource(kind, key, "", "");
    }

    public static StructureSource identified(Kind kind, StructureKey key, String version, byte[] content) {
        return new StructureSource(kind, key, version, StructureHash.sha256(content));
    }

    public enum Kind {
        IRIS,
        VANILLA,
        DATAPACK,
        MOD,
        UNKNOWN
    }
}
