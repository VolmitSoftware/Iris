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

import com.volmit.iris.engine.framework.Engine;
import com.volmit.iris.engine.mantle.MantleWriter;
import com.volmit.iris.engine.object.IrisBiome;
import com.volmit.iris.engine.object.IrisNoiseCarving;
import com.volmit.iris.util.documentation.BlockCoordinates;
import com.volmit.iris.util.documentation.ChunkCoordinates;
import com.volmit.iris.util.math.RNG;
import com.volmit.iris.util.matter.slices.CavernMatter;

/**
 * Sculpts the rock behind cliff faces and slopes of biomes with noise carving, one column at a time. The carved blocks
 * become caverns like any other carving, so they get cave walls, floors, ceilings and decoration from the carve pass.
 */
final class NoiseTerrainCarver {
    private static final long NOISE_SEED_SALT = 4_096_217L;
    private static final int BORDER_FADE_BLOCKS = 16;
    private static final int[][] BORDER_PROBES = borderProbes();
    private static final double PROBES_CARVING_AT_BORDER = BORDER_PROBES.length / 2D;
    private static final int FACE_RINGS = 3;
    private static final int[][] RING_DIRECTIONS = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}, {1, 1}, {1, -1}, {-1, 1}, {-1, -1}};

    private final Engine engine;
    private final RNG noiseRandom;
    private final int fluidHeight;

    NoiseTerrainCarver(Engine engine) {
        this.engine = engine;
        this.noiseRandom = new RNG(engine.getSeedManager().getCarve()).nextParallelRNG(NOISE_SEED_SALT);
        this.fluidHeight = engine.getDimension().getFluidHeight();
    }

    @ChunkCoordinates
    void carve(MantleWriter writer, int chunkX, int chunkZ) {
        int startX = chunkX << 4;
        int startZ = chunkZ << 4;
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                carveColumn(writer, startX + x, startZ + z);
            }
        }
    }

    // Carving stays above the sea, where hollows next to the water would flood.
    @BlockCoordinates
    private void carveColumn(MantleWriter writer, int x, int z) {
        IrisNoiseCarving carving = noiseCarvingAt(x, z);
        if (carving == null) {
            return;
        }
        double borderStrength = borderStrength(x, z);
        if (borderStrength <= 0) {
            return;
        }
        int surface = surfaceAt(x, z);
        int[] lowestSurfaceByRing = lowestSurfaceByRing(x, z, carving.getReach());
        int lowest = Math.max(lowestSurfaceByRing[FACE_RINGS - 1] + 1, fluidHeight + 1);
        double steepness = (surface - lowestSurfaceByRing[0]) / (double) ringDistance(carving.getReach(), 0);
        for (int y = lowest; y < surface; y++) {
            double strength = borderStrength * faceNearness(y, lowestSurfaceByRing) * carving.strengthBelowCrust(surface - y, steepness);
            if (carving.carves(noiseRandom, engine.getData(), x, y, z, strength)) {
                writer.setData(x, y, z, CavernMatter.BASIC);
            }
        }
    }

    /**
     * The lowest surface on rings of growing size around the column, innermost first. A block above a ring's lowest
     * surface has open air beside it within that ring's distance.
     */
    private int[] lowestSurfaceByRing(int x, int z, int reach) {
        int[] lowest = new int[FACE_RINGS];
        for (int ring = 0; ring < FACE_RINGS; ring++) {
            int distance = ringDistance(reach, ring);
            int ringLowest = Integer.MAX_VALUE;
            for (int[] direction : RING_DIRECTIONS) {
                ringLowest = Math.min(ringLowest, surfaceAt(x + direction[0] * distance, z + direction[1] * distance));
            }
            lowest[ring] = ring == 0 ? ringLowest : Math.min(ringLowest, lowest[ring - 1]);
        }
        return lowest;
    }

    private static int ringDistance(int reach, int ring) {
        return Math.max(1, Math.round(reach * (ring + 1) / (float) FACE_RINGS));
    }

    /**
     * How close the block is to a cliff face or slope: 1 when open air is right beside it, falling with each ring
     * further out, and 0 deep inside the rock where carving would only make hidden caves.
     */
    private static double faceNearness(int y, int[] lowestSurfaceByRing) {
        for (int ring = 0; ring < lowestSurfaceByRing.length; ring++) {
            if (y > lowestSurfaceByRing[ring]) {
                return 1 - ring / (double) lowestSurfaceByRing.length;
            }
        }
        return 0;
    }

    private int surfaceAt(int x, int z) {
        return (int) Math.round(engine.getComplex().getHeightStream().get(x, z));
    }

    private IrisNoiseCarving noiseCarvingAt(int x, int z) {
        IrisBiome biome = engine.getComplex().getTrueBiomeStream().get(x, z);
        return biome == null ? null : biome.getNoiseCarving();
    }

    /**
     * How fully carving applies at the column: 1 well inside carving biomes, falling to 0 on the border with a biome
     * that doesn't carve, so the hollows taper off instead of ending in a straight wall.
     */
    private double borderStrength(int x, int z) {
        int carvingProbes = 0;
        for (int[] probe : BORDER_PROBES) {
            if (noiseCarvingAt(x + probe[0], z + probe[1]) != null) {
                carvingProbes++;
            }
        }
        double strength = (carvingProbes - PROBES_CARVING_AT_BORDER) / PROBES_CARVING_AT_BORDER;
        return Math.max(0, Math.min(1, strength));
    }

    private static int[][] borderProbes() {
        int diagonal = (int) Math.round(BORDER_FADE_BLOCKS / Math.sqrt(2));
        return new int[][]{
                {BORDER_FADE_BLOCKS, 0}, {-BORDER_FADE_BLOCKS, 0}, {0, BORDER_FADE_BLOCKS}, {0, -BORDER_FADE_BLOCKS},
                {diagonal, diagonal}, {diagonal, -diagonal}, {-diagonal, diagonal}, {-diagonal, -diagonal}
        };
    }
}
