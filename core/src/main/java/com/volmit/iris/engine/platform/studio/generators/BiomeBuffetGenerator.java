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

package com.volmit.iris.engine.platform.studio.generators;

import com.volmit.iris.engine.data.cache.Cache;
import com.volmit.iris.engine.data.chunk.TerrainChunk;
import com.volmit.iris.engine.framework.Engine;
import com.volmit.iris.engine.framework.WrongEngineBroException;
import com.volmit.iris.engine.object.IrisBiome;
import com.volmit.iris.engine.platform.studio.EnginedStudioGenerator;
import org.bukkit.Material;
import org.bukkit.block.data.BlockData;

import java.util.Objects;

public class BiomeBuffetGenerator extends EnginedStudioGenerator {
    private static final BlockData FLOOR = Material.BARRIER.createBlockData();
    private final IrisBiome[] biomes;
    private final int width;
    private final int biomeSize;

    public BiomeBuffetGenerator(Engine engine, int biomeSize) {
        super(engine);
        this.biomeSize = biomeSize;
        biomes = engine.getDimension().getAllBiomes(engine).toArray(new IrisBiome[0]);
        width = Math.max((int) Math.sqrt(biomes.length), 1);
    }

    @Override
    public void generateChunk(Engine engine, TerrainChunk tc, int x, int z) throws WrongEngineBroException {
        int id = Cache.to1D(x / biomeSize, 0, z / biomeSize, width, 1);

        if (id >= 0 && id < biomes.length) {
            IrisBiome biome = biomes[id];
            String foc = engine.getDimension().getFocus();

            if (!Objects.equals(foc, biome.getLoadKey())) {
                engine.getDimension().setFocus(biome.getLoadKey());
                engine.hotloadComplex();
            }

            engine.generate(x << 4, z << 4, tc, true);
        } else {
            tc.setRegion(0, 0, 0, 16, 1, 16, FLOOR);
        }
    }
}
