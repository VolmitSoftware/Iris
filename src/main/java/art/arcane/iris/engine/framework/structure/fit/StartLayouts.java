/*
 * Iris is a World Generator for Minecraft Servers
 * Copyright (c) 2026 Arcane Arts (Volmit Software)
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

package art.arcane.iris.engine.framework.structure.fit;

import art.arcane.iris.builtin.structure.StructureTerrain;
import art.arcane.iris.builtin.structure.StructureYBand;
import art.arcane.iris.engine.framework.structure.NativeStructureFit;
import art.arcane.volmlib.util.math.RNG;
import com.volmit.mantle.BlockBox;
import com.volmit.vanilla.PlatformStart;
import com.volmit.vanilla.StartReach;

/**
 * Where the platform's own starts sit and how far {@code iris:structure} changes the terrain around a start. A start
 * that keeps the platform's height only takes its shift; a band moves the start's middle into it, chosen per start
 * chunk; an underground start is buried under the lowest terrain column above it; anything else takes its shift. A
 * structure the host aligns itself only takes its shift on top of that alignment.
 */
public final class StartLayouts {
    private static final int MAX_BURIAL_COLUMNS = 2_000_000;
    private static final int UNDERGROUND_SURFACE_CLEARANCE = 1;
    private static final long BAND_IDENTITY_X = 341873128712L;
    private static final long BAND_IDENTITY_Z = 132897987541L;

    private StartLayouts() {
    }

    /** How far {@code start} moves up (negative: down) under {@code fit}, before the host keeps it inside the world. */
    public static int shift(PlatformStart start, NativeStructureFit fit) {
        if (start.aligned() || fit.keepsSourceY()) {
            return fit.yShift();
        }
        if (fit.yBand() != null) {
            return bandShift(start, fit.yBand());
        }
        if (start.underground()) {
            return burialShift(start, fit.yShift());
        }
        return fit.yShift();
    }

    /** How far around its pieces a start with {@code terrain} changes the terrain. */
    public static StartReach reach(StructureTerrain terrain) {
        return switch (terrain.resolvedMode()) {
            case SOURCE -> StartReach.PLATFORM;
            case PRESERVE -> StartReach.blocks(0);
            case VACUUM -> StartReach.blocks(SurfaceFitting.surfaceTerrainRadius());
            case BORE, FORCE_CARVE, ENCASE -> StartReach.blocks(Math.max(0, terrain.horizontalPadding()));
        };
    }

    private static int bandShift(PlatformStart start, StructureYBand band) {
        BlockBox bounds = start.bounds();
        int minY = bounds.minY() + start.bottomY();
        int maxY = bounds.maxY() - 1 + start.bottomY();
        int target = bandTargetMidpointY(minY, maxY, band.resolvedMin(), band.resolvedMax(), start.chunkX(), start.chunkZ());
        return Math.subtractExact(target, minY + (maxY - minY) / 2);
    }

    /** The world height the middle of a start from {@code maxY - minY} high moves to, inside the band, per start chunk. */
    static int bandTargetMidpointY(int minY, int maxY, int bandMin, int bandMax, int chunkX, int chunkZ) {
        int height = Math.subtractExact(maxY, minY);
        int lowest = Math.addExact(bandMin, height / 2);
        int highest = Math.subtractExact(bandMax, height - height / 2);
        if (lowest > highest) {
            // The band is shorter than the structure, so only its midpoint can be honoured.
            return Math.floorDiv(Math.addExact(bandMin, bandMax), 2);
        }
        int span = highest - lowest + 1;
        if (span == 1) {
            return lowest;
        }
        long identity = (long) chunkX * BAND_IDENTITY_X ^ (long) chunkZ * BAND_IDENTITY_Z;
        return lowest + new RNG(identity).nextInt(span);
    }

    /** At most {@code requestedShift}, and low enough that the start's top stays a block under every terrain column. */
    private static int burialShift(PlatformStart start, int requestedShift) {
        BlockBox bounds = start.bounds();
        long width = (long) bounds.maxX() - bounds.minX();
        long depth = (long) bounds.maxZ() - bounds.minZ();
        if (width <= 0L || depth <= 0L || width > MAX_BURIAL_COLUMNS / depth) {
            throw new IllegalStateException("Underground native structure burial footprint is invalid or exceeds "
                    + MAX_BURIAL_COLUMNS + " columns");
        }
        int topY = bounds.maxY() - 1;
        int maximumShift = requestedShift;
        for (int x = bounds.minX(); x < bounds.maxX(); x++) {
            for (int z = bounds.minZ(); z < bounds.maxZ(); z++) {
                int allowedTopY = start.surfaceY(x, z) - UNDERGROUND_SURFACE_CLEARANCE;
                maximumShift = Math.min(maximumShift, allowedTopY - topY);
            }
        }
        return maximumShift;
    }
}
