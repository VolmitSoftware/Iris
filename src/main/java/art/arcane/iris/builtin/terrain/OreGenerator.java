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

package art.arcane.iris.builtin.terrain;

import com.volmit.block.PaletteSpec;
import com.volmit.config.Desc;
import com.volmit.config.MaxNumber;
import com.volmit.config.MinNumber;
import com.volmit.config.Range;
import com.volmit.noise.NoiseStyle;

import java.util.List;

/**
 * One ore generator of a dimension, region or biome, an entry of its {@code ores} list: single ore blocks scattered
 * through the terrain by a noise field, in a vertical band. A value of IrisApi types only; the terrain module resolves
 * its blocks and builds its noise ({@code ResolvedOre}).
 */
@Desc("Scatters single ore blocks through terrain by sampling a noise field at every block in a vertical band. Defined on dimensions, regions, or biomes; the most specific level wins.")
public final class OreGenerator {
    private static final double DEFAULT_THRESHOLD = 0.5;
    private static final double DEFAULT_RANGE_MIN = 30;
    private static final double DEFAULT_RANGE_MAX = 80;

    @Desc("The ore blocks to place. An empty palette generates nothing, so this must be set for the generator to have any effect.")
    private PaletteSpec palette = PaletteSpec.of(List.of());

    @Desc("The noise style sampled per block to decide where ore appears. STATIC gives independent random speckle; smoother styles give veiny clusters.")
    private NoiseStyle chanceStyle = NoiseStyle.of("STATIC");

    @Desc("When true this generator also replaces surface-layer blocks (it runs before layers and fluid); when false it only replaces underground rock.")
    private boolean generateSurface = false;

    @MinNumber(0)
    @MaxNumber(1)
    @Desc("Noise cutoff: a block becomes ore when the sampled noise is at or below this value, so higher means more ore. 0 disables, 1 replaces everything in range.")
    private double threshold = DEFAULT_THRESHOLD;

    @Desc("Vertical band (min, max) this ore can generate in, in engine-local Y where 0 is the bottom of the dimension, not world Y.")
    private Range range = Range.of(DEFAULT_RANGE_MIN, DEFAULT_RANGE_MAX);

    public OreGenerator() {
    }

    public PaletteSpec palette() {
        return palette;
    }

    public NoiseStyle chanceStyle() {
        return chanceStyle;
    }

    public boolean generateSurface() {
        return generateSurface;
    }

    public double threshold() {
        return threshold;
    }

    public Range range() {
        return range;
    }
}
