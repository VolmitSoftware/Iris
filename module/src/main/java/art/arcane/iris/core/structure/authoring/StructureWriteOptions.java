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

public record StructureWriteOptions(StructureWriteMode mode, boolean dryRun, String expectedManifestHash) {
    public StructureWriteOptions {
        Objects.requireNonNull(mode, "mode");
        expectedManifestHash = expectedManifestHash == null ? "" : expectedManifestHash.trim();
        if (!expectedManifestHash.isEmpty() && !StructureHash.isSha256(expectedManifestHash)) {
            throw new IllegalArgumentException("Expected structure manifest hash must be SHA-256");
        }
        if (!expectedManifestHash.isEmpty() && mode != StructureWriteMode.OVERWRITE) {
            throw new IllegalArgumentException("Expected structure manifest hashes require overwrite mode");
        }
    }

    public StructureWriteOptions(StructureWriteMode mode, boolean dryRun) {
        this(mode, dryRun, "");
    }

    public static StructureWriteOptions addOnly() {
        return new StructureWriteOptions(StructureWriteMode.ADD_ONLY, false);
    }

    public static StructureWriteOptions overwrite() {
        return new StructureWriteOptions(StructureWriteMode.OVERWRITE, false);
    }

    public static StructureWriteOptions preview(StructureWriteMode mode) {
        return new StructureWriteOptions(mode, true);
    }

    public static StructureWriteOptions overwriteExpected(String manifestHash) {
        return new StructureWriteOptions(StructureWriteMode.OVERWRITE, false, manifestHash);
    }
}
