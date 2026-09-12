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

import org.bukkit.NamespacedKey;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

public class StructureImporterModeTest {
    @Test
    public void parseModeDefaultsToAddOnlyForNull() {
        assertEquals(StructureImporter.Mode.ADD_ONLY, StructureImporter.parseMode(null));
    }

    @Test
    public void parseModeDefaultsToAddOnlyForEmptyAndRejectsUnknown() {
        assertEquals(StructureImporter.Mode.ADD_ONLY, StructureImporter.parseMode(""));
        assertThrows(IllegalArgumentException.class, () -> StructureImporter.parseMode("garbage"));
        assertEquals(StructureImporter.Mode.OVERWRITE, StructureImporter.parseMode("overwrite"));
        assertEquals(StructureImporter.Mode.OVERWRITE, StructureImporter.parseMode("replace"));
    }

    @Test
    public void parseModeRecognizesAllAddOnlyAliases() {
        assertEquals(StructureImporter.Mode.ADD_ONLY, StructureImporter.parseMode("add"));
        assertEquals(StructureImporter.Mode.ADD_ONLY, StructureImporter.parseMode("addonly"));
        assertEquals(StructureImporter.Mode.ADD_ONLY, StructureImporter.parseMode("add_only"));
        assertEquals(StructureImporter.Mode.ADD_ONLY, StructureImporter.parseMode("add-only"));
    }

    @Test
    public void parseModeIsCaseInsensitive() {
        assertEquals(StructureImporter.Mode.ADD_ONLY, StructureImporter.parseMode("ADD"));
        assertEquals(StructureImporter.Mode.OVERWRITE, StructureImporter.parseMode("OVERWRITE"));
        assertThrows(IllegalArgumentException.class, () -> StructureImporter.parseMode("MERGE"));
    }

    @Test
    public void deriveNameFromStringLowercasesAndNormalizesSeparators() {
        assertEquals("minecraft_village_plains", StructureImporter.deriveName("minecraft:village/plains"));
        assertEquals("nova_structures_temple", StructureImporter.deriveName("Nova_Structures:Temple"));
        assertEquals("minecraft_ancient_city", StructureImporter.deriveName("minecraft:ancient_city"));
    }

    @Test
    public void deriveNameFromNamespacedKeyJoinsNamespaceAndPath() {
        assertEquals("minecraft_village", StructureImporter.deriveName(NamespacedKey.minecraft("village")));
    }

    @Test
    public void deriveNameFromNamespacedKeyNormalizesSlashesInPath() {
        NamespacedKey key = NamespacedKey.fromString("nova_structures:temple/large");
        assertEquals("nova_structures_temple_large", StructureImporter.deriveName(key));
    }

    @Test
    public void normalizesLegacySlabHalfProperties() {
        assertEquals("minecraft:pale_oak_slab[type=top]",
                StructureImporter.normalizeJigsawFinalState("minecraft:pale_oak_slab[half=top]"));
        assertEquals("minecraft:pale_oak_slab[waterlogged=false,type=bottom]",
                StructureImporter.normalizeJigsawFinalState(
                        "minecraft:pale_oak_slab[waterlogged=false,half=bottom]"));
    }

    @Test
    public void normalizesMisspelledPolishedBlackstone() {
        assertEquals("minecraft:chiseled_polished_blackstone",
                StructureImporter.normalizeJigsawFinalState("minecraft:chisled_polished_blackstone"));
        assertEquals("minecraft:chiseled_polished_blackstone[axis=y]",
                StructureImporter.normalizeJigsawFinalState(
                        "minecraft:chisled_polished_blackstone[axis=y]"));
    }

    @Test
    public void leavesNonSlabAndModernStatesUnchanged() {
        assertEquals("minecraft:oak_stairs[half=top]",
                StructureImporter.normalizeJigsawFinalState("minecraft:oak_stairs[half=top]"));
        assertEquals("minecraft:pale_oak_slab[type=bottom]",
                StructureImporter.normalizeJigsawFinalState("minecraft:pale_oak_slab[type=bottom]"));
        assertEquals("minecraft:chisled_polished_blackstone_stairs",
                StructureImporter.normalizeJigsawFinalState(
                        "minecraft:chisled_polished_blackstone_stairs"));
        assertEquals("example:chisled_polished_blackstone",
                StructureImporter.normalizeJigsawFinalState(
                        "example:chisled_polished_blackstone"));
    }
}
