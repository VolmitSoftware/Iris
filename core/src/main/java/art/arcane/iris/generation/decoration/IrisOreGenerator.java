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

package art.arcane.iris.generation.decoration;

import art.arcane.iris.generation.noise.IrisGeneratorStyle;
import art.arcane.iris.generation.noise.NoiseStyle;
import art.arcane.iris.generation.terrain.IrisMaterialPalette;
import art.arcane.iris.pack.value.IrisRange;

import art.arcane.iris.pack.loading.IrisData;
import art.arcane.iris.generation.cache.AtomicCache;
import art.arcane.volmlib.util.documentation.Description;
import art.arcane.iris.pack.schema.annotation.MaxNumber;
import art.arcane.iris.pack.schema.annotation.MinNumber;
import art.arcane.volmlib.util.math.RNG;
import art.arcane.volmlib.util.noise.CNG;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import art.arcane.iris.spi.PlatformBlockState;
import lombok.experimental.Accessors;

@Accessors(chain = true)
@NoArgsConstructor
@AllArgsConstructor
@Description("Scatters single ore blocks through terrain by sampling a noise field at every block in a vertical band. Defined on dimensions, regions, or biomes; the most specific level wins.")
@Data
public class IrisOreGenerator {
    @Description("The ore blocks to place. An empty palette generates nothing, so this must be set for the generator to have any effect.")
    private IrisMaterialPalette palette = new IrisMaterialPalette().qclear();
    @Description("The noise style sampled per block to decide where ore appears. STATIC gives independent random speckle; smoother styles give veiny clusters.")
    private IrisGeneratorStyle chanceStyle = new IrisGeneratorStyle(NoiseStyle.STATIC);
    @Description("When true this generator also replaces surface-layer blocks (it runs before layers and fluid); when false it only replaces underground rock.")
    private boolean generateSurface = false;
    @MinNumber(0)
    @MaxNumber(1)
    @Description("Noise cutoff: a block becomes ore when the sampled noise is at or below this value, so higher means more ore. 0 disables, 1 replaces everything in range.")
    private double threshold = 0.5;
    @Description("Vertical band (min, max) this ore can generate in, in engine-local Y where 0 is the bottom of the dimension, not world Y.")
    private IrisRange range = new IrisRange(30, 80);

    private transient AtomicCache<CNG> chanceCache = new AtomicCache<>();

    public void warm(RNG rng, IrisData data) {
        chanceCache.aquire(() -> chanceStyle.create(rng, data));
        palette.getLayerGenerator(rng, data);
    }

    public PlatformBlockState generate(int x, int y, int z, RNG rng, IrisData data) {
        if (threshold <= 0 || !range.contains(y)) {
            return null;
        }

        if (palette.getPalette().isEmpty()) {
            return null;
        }

        CNG chance = chanceCache.aquire(() -> chanceStyle.create(rng, data));

        if (chance.noise(x, y, z) > threshold) {
            return null;
        }

        return palette.get(rng, x, y, z, data);
    }
}
