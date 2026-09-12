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

package art.arcane.iris.structure.authoring;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

public class StructureKeyTest {
    @Test
    public void parsesExactNamespacedPath() {
        StructureKey key = StructureKey.parse("nova_structures:temple/large");

        assertEquals("nova_structures", key.namespace());
        assertEquals("temple/large", key.path());
        assertEquals("nova_structures:temple/large", key.toString());
    }

    @Test
    public void appliesExplicitDefaultNamespaceOnlyWhenMissing() {
        assertEquals(
                new StructureKey("minecraft", "village/plains"),
                StructureKey.parse("village/plains", "minecraft")
        );
        assertEquals(
                new StructureKey("modid", "tower"),
                StructureKey.parse("modid:tower", "minecraft")
        );
    }

    @Test
    public void rejectsLossyOrAmbiguousKeys() {
        assertThrows(IllegalArgumentException.class, () -> StructureKey.parse("village"));
        assertThrows(IllegalArgumentException.class, () -> StructureKey.parse("Minecraft:village"));
        assertThrows(IllegalArgumentException.class, () -> StructureKey.parse("minecraft:village:plains"));
        assertThrows(IllegalArgumentException.class, () -> StructureKey.parse("minecraft:village//plains"));
        assertThrows(IllegalArgumentException.class, () -> StructureKey.parse("minecraft:village/../plains"));
    }
}
