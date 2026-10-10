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

package art.arcane.iris.engine.loot;

import art.arcane.iris.builtin.loot.BlockDrop;
import art.arcane.iris.engine.UsableBlockPlatform;
import art.arcane.iris.engine.object.TestSettings;
import com.volmit.block.BlockRegistry;
import com.volmit.block.BlockSpec;
import com.volmit.block.BlockState;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Which broken blocks a block drop applies to: the exact state it names, or any state of that block. */
public class ResolvedBlockDropTest {
    private static final String NORTH_STAIRS = "minecraft:oak_stairs[facing=north]";
    private static final String EAST_STAIRS = "minecraft:oak_stairs[facing=east]";
    private static final String COBBLESTONE = "minecraft:cobblestone";

    @BeforeClass
    public static void bindPlatform() {
        UsableBlockPlatform.bind();
    }

    @AfterClass
    public static void unbindPlatform() {
        UsableBlockPlatform.unbind();
    }

    @Test
    public void exactMatchingIncludesPropertiesWhileTypeMatchingDoesNot() {
        ResolvedBlockDrop exact = resolved(true);
        ResolvedBlockDrop byType = resolved(false);

        assertTrue(exact.dropsFor(NORTH_STAIRS));
        assertFalse(exact.dropsFor(EAST_STAIRS));
        assertTrue(byType.dropsFor(NORTH_STAIRS));
        assertTrue(byType.dropsFor(EAST_STAIRS));
        assertFalse(byType.dropsFor(COBBLESTONE));
        assertFalse(exact.dropsFor(COBBLESTONE));
    }

    private static ResolvedBlockDrop resolved(boolean exactBlocks) {
        BlockDrop drop = TestSettings.of(BlockDrop.class, "{\"exactBlocks\": " + exactBlocks + ", \"blocks\": [{\"block\": \"oak_stairs\"}]}");
        BlockRegistry blocks = mock(BlockRegistry.class);
        when(blocks.resolve(any(BlockSpec.class))).thenReturn(BlockState.parse(NORTH_STAIRS));
        return ResolvedBlockDrop.of(drop, blocks);
    }
}
