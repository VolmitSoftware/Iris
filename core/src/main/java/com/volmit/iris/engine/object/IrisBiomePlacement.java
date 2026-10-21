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

import com.volmit.iris.engine.object.annotations.Desc;
import com.volmit.iris.engine.object.annotations.MaxNumber;
import com.volmit.iris.engine.object.annotations.MinNumber;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

@Accessors(chain = true)
@NoArgsConstructor
@AllArgsConstructor
@Desc("Where this biome may be chosen when the dimension uses HYBRID terrain. Ignored by BIOME terrain.")
@Data
public class IrisBiomePlacement {
    @MinNumber(-2032) // TODO: WARNING HEIGHT
    @MaxNumber(2032) // TODO: WARNING HEIGHT
    @Desc("The lowest base terrain height this biome is chosen on, relative to the fluid height. Use negative for oceans.")
    private int minHeight = -2032;

    @MinNumber(-2032) // TODO: WARNING HEIGHT
    @MaxNumber(2032) // TODO: WARNING HEIGHT
    @Desc("The highest base terrain height this biome is chosen on, relative to the fluid height.")
    private int maxHeight = 2032;

    @Desc("Where several biomes may be chosen, each samples this noise and the highest value wins. The biome's rarity divides its value. Zoomed by the dimension and region biome zooms.")
    private IrisGeneratorStyle competitionStyle = NoiseStyle.SIMPLEX.style();

    public boolean allowsHeight(double height) {
        return height >= minHeight && height <= maxHeight;
    }

    public double distanceToHeightRange(double height) {
        if (height < minHeight) {
            return minHeight - height;
        }

        return Math.max(0, height - maxHeight);
    }
}
