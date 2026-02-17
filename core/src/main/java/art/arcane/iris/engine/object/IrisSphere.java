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

package art.arcane.iris.engine.object;

import art.arcane.iris.engine.data.cache.AtomicCache;
import art.arcane.iris.engine.framework.Engine;
import art.arcane.iris.engine.mantle.MantleWriter;
import art.arcane.iris.engine.object.annotations.*;
import art.arcane.volmlib.util.math.RNG;
import art.arcane.volmlib.util.matter.MatterCavern;
import art.arcane.volmlib.util.matter.slices.CavernMatter;
import lombok.Data;

@Snippet("carving-sphere")
@Desc("Represents an procedural eliptical shape")
@Data
public class IrisSphere implements IRare {
    private transient final AtomicCache<MatterCavern> matterNodeCache = new AtomicCache<>();
    @Required
    @Desc("Typically a 1 in RARITY on a per fork basis")
    @MinNumber(1)
    private int rarity = 1;
    @RegistryListResource(IrisBiome.class)
    @Desc("Force this cave to only generate the specified custom biome")
    private String customBiome = "";
    @Desc("The styled random radius for x")
    private IrisStyledRange radius = new IrisStyledRange(1, 5, new IrisGeneratorStyle(NoiseStyle.STATIC));

    public void generate(RNG rng, Engine engine, MantleWriter writer, int x, int y, int z) {
        writer.setSphere(x, y, z, radius.get(rng, z, y, engine.getData()), true, matterNodeCache.aquire(() -> CavernMatter.get(getCustomBiome(), 0)));
    }

    public double maxSize() {
        return radius.getMax();
    }
}
