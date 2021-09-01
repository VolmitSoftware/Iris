/*
 * Iris is a World Generator for Minecraft Bukkit Servers
 * Copyright (c) 2021 Arcane Arts (Volmit Software)
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

import com.volmit.iris.engine.jigsaw.PlannedStructure;
import com.volmit.iris.engine.mantle.EngineMantle;
import com.volmit.iris.engine.mantle.IrisMantleComponent;
import com.volmit.iris.engine.mantle.MantleWriter;
import com.volmit.iris.engine.object.*;
import com.volmit.iris.util.documentation.BlockCoordinates;
import com.volmit.iris.util.documentation.ChunkCoordinates;
import com.volmit.iris.util.mantle.MantleFlag;
import com.volmit.iris.util.math.Position2;
import com.volmit.iris.util.math.RNG;
import com.volmit.iris.util.noise.CNG;

import java.util.List;
import java.util.function.Consumer;

public class MantleJigsawComponent extends IrisMantleComponent {
    private final CNG cng;

    public MantleJigsawComponent(EngineMantle engineMantle) {
        super(engineMantle, MantleFlag.JIGSAW);
        cng = NoiseStyle.STATIC.create(new RNG(engineMantle.getEngine().getWorld().seed() + 24398848585L));
    }

    @Override
    public void generateLayer(MantleWriter writer, int x, int z, Consumer<Runnable> post) {
        RNG rng = new RNG(cng.fit(-Integer.MAX_VALUE, Integer.MAX_VALUE, x, z));
        int xxx = 8 + (x << 4);
        int zzz = 8 + (z << 4);
        IrisRegion region = getComplex().getRegionStream().get(xxx, zzz);
        IrisBiome biome = getComplex().getTrueBiomeStreamNoFeatures().get(xxx, zzz);
        generateJigsaw(writer, rng, x, z, biome, region, post);
    }

    @ChunkCoordinates
    private void generateJigsaw(MantleWriter writer, RNG rng, int x, int z, IrisBiome biome, IrisRegion region, Consumer<Runnable> post) {
        boolean placed = false;

        if (getDimension().getStronghold() != null) {
            List<Position2> poss = getDimension().getStrongholds(seed());

            if (poss != null) {
                for (Position2 pos : poss) {
                    if (x == pos.getX() >> 4 && z == pos.getZ() >> 4) {
                        IrisJigsawStructure structure = getData().getJigsawStructureLoader().load(getDimension().getStronghold());
                        place(writer, pos.toIris(), structure, rng, post);
                        placed = true;
                    }
                }
            }
        }

        if (!placed) {
            for (IrisJigsawStructurePlacement i : biome.getJigsawStructures()) {
                if (rng.nextInt(i.getRarity()) == 0) {
                    IrisPosition position = new IrisPosition((x << 4) + rng.nextInt(15), 0, (z << 4) + rng.nextInt(15));
                    IrisJigsawStructure structure = getData().getJigsawStructureLoader().load(i.getStructure());
                    place(writer, position, structure, rng, post);
                    placed = true;
                }
            }
        }

        if (!placed) {
            for (IrisJigsawStructurePlacement i : region.getJigsawStructures()) {
                if (rng.nextInt(i.getRarity()) == 0) {
                    IrisPosition position = new IrisPosition((x << 4) + rng.nextInt(15), 0, (z << 4) + rng.nextInt(15));
                    IrisJigsawStructure structure = getData().getJigsawStructureLoader().load(i.getStructure());
                    place(writer, position, structure, rng, post);
                    placed = true;
                }
            }
        }

        if (!placed) {
            for (IrisJigsawStructurePlacement i : getDimension().getJigsawStructures()) {
                if (rng.nextInt(i.getRarity()) == 0) {
                    IrisPosition position = new IrisPosition((x << 4) + rng.nextInt(15), 0, (z << 4) + rng.nextInt(15));
                    IrisJigsawStructure structure = getData().getJigsawStructureLoader().load(i.getStructure());
                    place(writer, position, structure, rng, post);
                }
            }
        }
    }

    @BlockCoordinates
    private void place(MantleWriter writer, IrisPosition position, IrisJigsawStructure structure, RNG rng, Consumer<Runnable> post) {
        if (structure.getFeature() != null) {
            if (structure.getFeature().getBlockRadius() == 32) {
                structure.getFeature().setBlockRadius((double) structure.getMaxDimension() / 3);
            }
            writer.setData(position.getX(), 0, position.getZ(),
                    new IrisFeaturePositional(position.getX(), position.getZ(), structure.getFeature()));
        }

        new PlannedStructure(structure, position, rng).place(writer, getMantle(), post);
    }
}
