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

package art.arcane.iris.engine.modifier;

import com.volmit.block.BlockState;
import org.junit.Test;

import static art.arcane.iris.engine.modifier.ApiChunk.AIR;
import static art.arcane.iris.engine.modifier.ApiChunk.block;
import static org.junit.Assert.assertEquals;

/** iris:perfection through IrisApi alone: ores hide when the dimension asks, decorations without ground go. */
public class PerfectionModuleTest {
    private static final int HEIGHT = 8;
    private static final BlockState GRASS = block("minecraft:grass_block");
    private static final BlockState STONE = block("minecraft:stone");
    private static final BlockState POPPY = block("minecraft:poppy");

    @Test
    public void decorationsTheirGroundCannotHoldGo() {
        ApiChunk chunk = new ApiChunk(0, 0, HEIGHT).set(0, 0, 0, GRASS).set(0, 1, 0, POPPY)
                .set(1, 0, 0, STONE).set(1, 1, 0, POPPY);

        chunk.generate(new PerfectionModule());

        assertEquals(POPPY, chunk.get(0, 1, 0));
        assertEquals(AIR, chunk.get(1, 1, 0));
        assertEquals(STONE, chunk.get(1, 0, 0));
    }

    @Test
    public void bothHalvesOfATallPlantWithoutGroundGo() {
        BlockState lower = block("minecraft:tall_grass[half=lower]");
        BlockState upper = block("minecraft:tall_grass[half=upper]");
        ApiChunk chunk = new ApiChunk(0, 0, HEIGHT).set(0, 0, 0, STONE).set(0, 1, 0, lower).set(0, 2, 0, upper);

        chunk.generate(new PerfectionModule());

        assertEquals(AIR, chunk.get(0, 1, 0));
        assertEquals(AIR, chunk.get(0, 2, 0));
    }

    @Test
    public void oresHideOnlyWhenTheDimensionAsks() {
        BlockState ore = block("minecraft:deepslate_iron_ore");
        ApiChunk shown = new ApiChunk(0, 0, HEIGHT).set(0, 0, 0, ore);
        ApiChunk hidden = new ApiChunk(0, 0, HEIGHT).set(0, 0, 0, ore);
        hidden.pending().hidingOres();

        shown.generate(new PerfectionModule());
        hidden.generate(new PerfectionModule());

        assertEquals(ore, shown.get(0, 0, 0));
        assertEquals(block("minecraft:deepslate"), hidden.get(0, 0, 0));
    }
}
