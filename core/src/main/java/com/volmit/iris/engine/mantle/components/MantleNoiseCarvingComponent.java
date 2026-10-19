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

package com.volmit.iris.engine.mantle.components;

import com.volmit.iris.engine.data.cache.AtomicCache;
import com.volmit.iris.engine.mantle.ComponentFlag;
import com.volmit.iris.engine.mantle.EngineMantle;
import com.volmit.iris.engine.mantle.IrisMantleComponent;
import com.volmit.iris.engine.mantle.MantleWriter;
import com.volmit.iris.util.context.ChunkContext;
import com.volmit.iris.util.mantle.flag.ReservedFlag;

/**
 * Runs noise carving last and only for the chunk being generated: it never reaches into other chunks, and sampling
 * every column's biome and height is too expensive to repeat across the reach of the other components.
 */
@ComponentFlag(ReservedFlag.NOISE_CARVED)
public class MantleNoiseCarvingComponent extends IrisMantleComponent {
    private static final int RUNS_AFTER_OBJECTS = 2;

    private final AtomicCache<Boolean> packUsesNoiseCarving = new AtomicCache<>();

    public MantleNoiseCarvingComponent(EngineMantle engineMantle) {
        super(engineMantle, ReservedFlag.NOISE_CARVED, RUNS_AFTER_OBJECTS);
    }

    @Override
    public void generateLayer(MantleWriter writer, int x, int z, ChunkContext context) {
        if (!packUsesNoiseCarving()) {
            return;
        }
        new NoiseTerrainCarver(getEngineMantle().getEngine()).carve(writer, x, z);
    }

    @Override
    public void hotload() {
        super.hotload();
        packUsesNoiseCarving.reset();
    }

    private boolean packUsesNoiseCarving() {
        return packUsesNoiseCarving.aquire(() -> getDimension().getAllBiomes(this::getData).stream()
                .anyMatch(biome -> biome.getNoiseCarving() != null));
    }

    @Override
    protected int computeRadius() {
        return 0;
    }
}
