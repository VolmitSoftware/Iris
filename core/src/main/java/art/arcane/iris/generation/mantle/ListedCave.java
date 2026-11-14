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

package art.arcane.iris.generation.mantle;

import art.arcane.iris.generation.cave.IrisCave;
import art.arcane.iris.generation.cave.field.AquiferPockets;
import art.arcane.iris.generation.cave.field.CaveCarver;
import art.arcane.iris.generation.cave.field.NoiseFactory;
import art.arcane.volmlib.util.matter.MatterCavern;

import java.util.Arrays;

/**
 * One cave of a pack ready to carve one world: its carver, and the cells it writes into the mantle, which hold the
 * cave's biome. Cells say what fills a carved block: air, fluid from the dimension's palette, lava, or air that no
 * hydrology may fill.
 */
final class ListedCave {
    private static final byte LIQUID_AIR = 0;
    private static final byte LIQUID_FLUID = 1;
    private static final byte LIQUID_LAVA = 2;
    private static final byte LIQUID_FORCED_AIR = 3;

    private final String key;
    private final IrisCave cave;
    private final CaveWorld world;
    private final CaveCarver carver;
    private final AquiferPockets aquiferPockets;
    private final MatterCavern air;
    private final MatterCavern fluid;
    private final MatterCavern lava;
    private final MatterCavern forcedAir;

    /**
     * @param biomeKey the cave biome that paints the cave's walls, or empty to leave them to the cave biome of the place
     */
    ListedCave(String key, IrisCave cave, String biomeKey, CaveWorld world, NoiseFactory noise) {
        long seed = CaveCarver.seedOf(world.carveSeed(), key);
        this.key = key;
        this.cave = cave;
        this.world = world;
        this.carver = new CaveCarver(cave, noise, seed, world.minHeight(), world.height());
        this.aquiferPockets = new AquiferPockets(noise, seed);
        this.air = new MatterCavern(true, biomeKey, LIQUID_AIR);
        this.fluid = new MatterCavern(true, biomeKey, LIQUID_FLUID);
        this.lava = new MatterCavern(true, biomeKey, LIQUID_LAVA);
        this.forcedAir = new MatterCavern(true, biomeKey, LIQUID_FORCED_AIR);
    }

    String key() {
        return key;
    }

    CaveCarver carver() {
        return carver;
    }

    MatterCavern air() {
        return air;
    }

    MatterCavern fluid() {
        return fluid;
    }

    boolean fluidNeedsFloor() {
        return cave.fluidRequiresFloor();
    }

    /** Fills {@code fluidTopY} with the highest height at which the cave may hold fluid in each column, below the surface. */
    void fillFluidTops(int[] surfaceY, int[] fluidTopY) {
        if (!cave.allowFluid()) {
            Arrays.fill(fluidTopY, Integer.MIN_VALUE);
            return;
        }
        int minDepth = cave.fluidMinDepthBelowSurface();
        for (int column = 0; column < fluidTopY.length; column++) {
            fluidTopY[column] = Math.min(world.fluidHeight(), surfaceY[column] - minDepth);
        }
    }

    /** What fills the carved block at height {@code y}, which lies at most {@code fluidTopY} up where the cave may hold fluid. */
    MatterCavern cellAt(int worldX, int y, int worldZ, int fluidTopY) {
        boolean belowLavaHeight = y <= world.caveLavaHeight();
        if (belowLavaHeight && cave.allowLava()) {
            return lava;
        }
        if (y <= fluidTopY && aquiferPockets.holdsFluidAt(worldX, y, worldZ, world.fluidHeight())) {
            return fluid;
        }
        return belowLavaHeight ? forcedAir : air;
    }
}
