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

package art.arcane.iris.core.studio.saved;

import art.arcane.iris.core.link.CustomBlockLook;
import art.arcane.iris.core.link.Identifier;
import org.junit.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

public class CustomBlocksTest {
    private static final CustomBlocks ETOPIA = new CustomBlocks(List.of(
            new CustomBlockLook(new Identifier("etopia", "grass_block"), "minecraft:tripwire[east=true,attached=false]"),
            new CustomBlockLook(new Identifier("etopia", "mushroom_cap"), "minecraft:red_mushroom_block")));

    @Test
    public void aBorrowedStateIsNamedAsThePluginBlockSavedAsIt() {
        assertEquals("etopia:grass_block", ETOPIA.nameSavedAs("minecraft:tripwire", () -> Map.of("attached", "false", "east", "true")));
        assertEquals("etopia:mushroom_cap", ETOPIA.nameSavedAs("minecraft:red_mushroom_block", Map::of));
    }

    @Test
    public void otherStatesOfABorrowedBlockKeepTheirName() {
        assertEquals("minecraft:tripwire", ETOPIA.nameSavedAs("minecraft:tripwire", () -> Map.of("attached", "true", "east", "true")));
        assertEquals("minecraft:tripwire", ETOPIA.nameSavedAs("minecraft:tripwire", Map::of));
    }

    @Test
    public void propertiesAreOnlyReadForBlocksAPluginBorrows() {
        assertEquals("minecraft:stone", ETOPIA.nameSavedAs("minecraft:stone", () -> {
            fail("Stone is not borrowed");
            return Map.of();
        }));
    }

    @Test
    public void aPluginBlockShowsAsTheVanillaBlockItBorrows() {
        assertEquals(Optional.of("minecraft:tripwire"), ETOPIA.shownBlockOf("etopia:grass_block"));
        assertEquals(Optional.empty(), ETOPIA.shownBlockOf("minecraft:tripwire"));
    }

    @Test
    public void withoutPluginBlocksNothingIsRenamed() {
        assertEquals("minecraft:tripwire", CustomBlocks.NONE.nameSavedAs("minecraft:tripwire", Map::of));
        assertEquals(Optional.empty(), CustomBlocks.NONE.shownBlockOf("etopia:grass_block"));
    }
}
