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

package com.volmit.iris.engine.object;

import com.volmit.iris.core.loader.IrisData;
import com.volmit.iris.engine.data.cache.AtomicCache;
import com.volmit.iris.engine.object.annotations.Desc;
import com.volmit.iris.util.math.RNG;
import com.volmit.iris.util.noise.CNG;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import org.bukkit.block.data.BlockData;

@Accessors(chain = true)
@NoArgsConstructor
@AllArgsConstructor
@Desc("Ore Layer")
@Data
public class IrisOreGenerator {
    @Desc("The palette of 'ore' generated")
    private IrisMaterialPalette palette = new IrisMaterialPalette().qclear();
    @Desc("The generator style for the 'ore'")
    private IrisGeneratorStyle chanceStyle = new IrisGeneratorStyle(NoiseStyle.STATIC);
    @Desc("Will ores generate on the surface of the terrain layer")
    private boolean generateSurface = false;
    @Desc("Threshold for rate of generation")
    private double threshold = 0.5;
    @Desc("Height limit (min, max)")
    private IrisRange range = new IrisRange(30, 80);

    private transient AtomicCache<CNG> chanceCache = new AtomicCache<>();

    public BlockData generate(int x, int y, int z, RNG rng, IrisData data) {
        if (palette.getPalette().isEmpty()) {
            return null;
        }

        if (!range.contains(y)) {
            return null;
        }

        CNG chance = chanceCache.aquire(() -> chanceStyle.create(rng, data));

        if (chance.noise(x, y, z) > threshold) {
            return null;
        }

        return palette.get(rng, x, y, z, data);
    }
}
