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

package art.arcane.iris.pack;

import art.arcane.volmlib.util.json.JSONObject;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class PackValidatorBorrowedDimensionTypeTest {
    @Test
    public void acceptsAbsentEmptyAndRegistryKeys() {
        assertTrue(validate("{}").isEmpty());
        assertTrue(validate("{\"borrowedDimensionType\":\"\"}").isEmpty());
        assertTrue(validate("{\"borrowedDimensionType\":\"aether:the_aether\"}").isEmpty());
        assertTrue(validate("{\"borrowedDimensionType\":\"minecraft:the_end\"}").isEmpty());
    }

    @Test
    public void rejectsValuesThatAreNotNamespacedRegistryKeys() {
        assertEquals(List.of("Dimension 'main' borrowedDimensionType must be a namespaced dimension type key such as aether:the_aether, but was 'the_aether'."),
                validate("{\"borrowedDimensionType\":\"the_aether\"}"));
        assertEquals(List.of("Dimension 'main' borrowedDimensionType must be a namespaced dimension type key such as aether:the_aether, but was 'Aether:The Aether'."),
                validate("{\"borrowedDimensionType\":\"Aether:The Aether\"}"));
        assertEquals(List.of("Dimension 'main' borrowedDimensionType must be a namespaced dimension type key such as aether:the_aether, but was '42'."),
                validate("{\"borrowedDimensionType\":42}"));
    }

    private static List<String> validate(String json) {
        List<String> errors = new ArrayList<>();
        PackDimensionValidator.validateBorrowedDimensionType("main", new JSONObject(json), errors);
        return errors;
    }
}
