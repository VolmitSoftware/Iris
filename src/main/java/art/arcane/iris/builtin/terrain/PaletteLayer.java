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

import art.arcane.iris.builtin.prepare.SlopeClip;
import com.volmit.block.BlockSpec;
import com.volmit.config.Desc;
import com.volmit.config.MaxNumber;
import com.volmit.config.MinItems;
import com.volmit.config.MinNumber;
import com.volmit.config.Required;
import com.volmit.config.Snippet;
import com.volmit.noise.NoiseStyle;

import java.util.List;

/**
 * One layer of surface or subsurface material of a biome, as packs write it in the biome's layers, sea layers, cave
 * ceiling layers, slab and wall, in a floating island's bottom palette or under {@code snippet/biome-palette/}. A value of
 * IrisApi types only; the terrain module resolves the blocks and builds the noise.
 */
@Snippet("biome-palette")
@Desc("A layer of surface / subsurface material in biomes")
public final class PaletteLayer {
    private static final String STATIC_NOISE = "STATIC";
    private static final String DEFAULT_BLOCK = "grass_block";

    @Desc("The style of noise")
    private NoiseStyle style = NoiseStyle.of(STATIC_NOISE);

    @MinNumber(0)
    @MaxNumber(2032)
    @Desc("The min thickness of this layer")
    private int minHeight = 1;

    @MinNumber(1)
    @MaxNumber(2032)
    @Desc("The max thickness of this layer")
    private int maxHeight = 1;

    @Desc("If set, this layer only shows where the terrain slope is within these bounds; outside them the layer is skipped entirely. Thickness is not scaled by slope.")
    private SlopeClip slopeCondition = new SlopeClip();

    @MinNumber(0.0001)
    @Desc("The terrain zoom mostly for zooming in on a wispy palette")
    private double zoom = 5;

    @Required
    @MinItems(1)
    @Desc("The palette of blocks to be used in this layer")
    private List<BlockSpec> palette = List.of(BlockSpec.of(DEFAULT_BLOCK));

    /** A layer with every option at its default, as packs get it from {@code {}}. */
    public PaletteLayer() {
    }

    /** A layer with the default options and no block to place, which is what a biome without a slab or wall has. */
    public static PaletteLayer withoutBlocks() {
        PaletteLayer layer = new PaletteLayer();
        layer.palette = List.of();
        return layer;
    }

    public NoiseStyle style() {
        return style == null ? NoiseStyle.of(STATIC_NOISE) : style;
    }

    public int minHeight() {
        return minHeight;
    }

    public int maxHeight() {
        return maxHeight;
    }

    public SlopeClip slopeCondition() {
        return slopeCondition == null ? new SlopeClip() : slopeCondition;
    }

    public double zoom() {
        return zoom;
    }

    public List<BlockSpec> palette() {
        return palette == null ? List.of() : palette;
    }
}
