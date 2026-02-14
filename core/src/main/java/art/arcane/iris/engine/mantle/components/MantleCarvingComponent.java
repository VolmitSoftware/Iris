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

package art.arcane.iris.engine.mantle.components;

import art.arcane.iris.engine.data.cache.Cache;
import art.arcane.iris.engine.mantle.ComponentFlag;
import art.arcane.iris.engine.mantle.EngineMantle;
import art.arcane.iris.engine.mantle.IrisMantleComponent;
import art.arcane.iris.engine.mantle.MantleWriter;
import art.arcane.iris.engine.object.IrisBiome;
import art.arcane.iris.engine.object.IrisCarving;
import art.arcane.iris.engine.object.IrisRegion;
import art.arcane.iris.util.context.ChunkContext;
import art.arcane.volmlib.util.documentation.ChunkCoordinates;
import art.arcane.volmlib.util.mantle.flag.ReservedFlag;
import art.arcane.volmlib.util.math.RNG;

@ComponentFlag(ReservedFlag.CARVED)
public class MantleCarvingComponent extends IrisMantleComponent {
    public MantleCarvingComponent(EngineMantle engineMantle) {
        super(engineMantle, ReservedFlag.CARVED, 0);
    }

    @Override
    public void generateLayer(MantleWriter writer, int x, int z, ChunkContext context) {
        RNG rng = new RNG(Cache.key(x, z) + seed());
        int xxx = 8 + (x << 4);
        int zzz = 8 + (z << 4);
        IrisRegion region = getComplex().getRegionStream().get(xxx, zzz);
        IrisBiome biome = getComplex().getTrueBiomeStream().get(xxx, zzz);
        carve(writer, rng, x, z, region, biome);
    }

    @ChunkCoordinates
    private void carve(MantleWriter writer, RNG rng, int cx, int cz, IrisRegion region, IrisBiome biome) {
        carve(getDimension().getCarving(), writer, new RNG((rng.nextLong() * cx) + 490495 + cz), cx, cz);
        carve(biome.getCarving(), writer, new RNG((rng.nextLong() * cx) + 490495 + cz), cx, cz);
        carve(region.getCarving(), writer, new RNG((rng.nextLong() * cx) + 490495 + cz), cx, cz);
    }

    @ChunkCoordinates
    private void carve(IrisCarving carving, MantleWriter writer, RNG rng, int cx, int cz) {
        carving.doCarving(writer, rng, getEngineMantle().getEngine(), cx << 4, -1, cz << 4, 0);
    }

    protected int computeRadius() {
        var dimension = getDimension();
        int max = 0;

        max = Math.max(max, dimension.getCarving().getMaxRange(getData(), 0));

        for (var i : dimension.getAllRegions(this::getData)) {
            max = Math.max(max, i.getCarving().getMaxRange(getData(), 0));
        }

        for (var i : dimension.getAllBiomes(this::getData)) {
            max = Math.max(max, i.getCarving().getMaxRange(getData(), 0));
        }

        return max;
    }
}
