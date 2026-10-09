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

package art.arcane.iris.engine.modifier;

import art.arcane.iris.engine.UpperDimensionContext;
import art.arcane.iris.builtin.decorant.DecorationPart;
import art.arcane.iris.engine.decorator.BiomeDecorators;
import art.arcane.iris.engine.decorator.IrisCeilingDecorator;
import art.arcane.iris.engine.decorator.IrisSurfaceDecorator;
import art.arcane.iris.engine.framework.Engine;
import art.arcane.iris.engine.object.world.biome.InferredType;
import art.arcane.iris.engine.object.world.biome.IrisBiome;
import art.arcane.iris.engine.object.terrain.carving.IrisDimensionCarvingResolver;
import art.arcane.iris.util.project.context.ChunkContext;
import art.arcane.iris.util.common.data.B;
import art.arcane.volmlib.util.documentation.ChunkCoordinates;
import art.arcane.iris.util.project.hunk.Hunk;
import art.arcane.volmlib.util.collection.KList;
import art.arcane.volmlib.util.mantle.runtime.Mantle;
import art.arcane.volmlib.util.mantle.runtime.MantleChunk;
import art.arcane.volmlib.util.mantle.runtime.TectonicPlate;
import art.arcane.volmlib.util.math.BlockPosition;
import art.arcane.volmlib.util.math.M;
import art.arcane.volmlib.util.math.PowerOfTwoCoordinates;
import art.arcane.volmlib.util.math.RNG;
import art.arcane.volmlib.util.matter.Matter;
import art.arcane.volmlib.util.matter.MatterCavern;
import art.arcane.volmlib.util.matter.slices.MarkerMatter;
import art.arcane.volmlib.util.scheduling.PrecisionStopwatch;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import lombok.Data;
import art.arcane.iris.spi.PlatformBlockState;

import java.util.Map;

/**
 * Applies the caves the mantle planned to a chunk's blocks for {@link CarveModule}: carves each cave zone, paints
 * its floor and ceiling with the cave biomes' layers, fills fluids and lava and decorates the cave surfaces.
 * One per engine runtime, holding the carve stream's random.
 */
public class CaveApplying {
    private static final byte LIQUID_FLUID = 1;
    private static final ThreadLocal<IrisCarveScratch> SCRATCH = ThreadLocal.withInitial(IrisCarveScratch::new);
    private static final int CAVE_BIOME_BLEND_RADIUS = 3;
    private static final int CAVE_BIOME_BLEND_CENTER_WEIGHT = 4;
    private static final int CAVE_BIOME_BLEND_TOTAL_WEIGHT = 8;
    private static final MatterCavern BASIC_CAVERN = new MatterCavern(true, "", (byte) 0);
    private final Engine engine;
    private final RNG rng;
    private final PlatformBlockState AIR = B.getState("CAVE_AIR");
    private final PlatformBlockState LAVA = B.getState("LAVA");
    private final BiomeDecorators decorators;
    private final IrisSurfaceDecorator surfaceDecorator;
    private final IrisCeilingDecorator ceilingDecorator;

    public CaveApplying(Engine engine) {
        this.engine = engine;
        rng = new RNG(engine.getSeedManager().getCarve());
        decorators = BiomeDecorators.of(engine);
        surfaceDecorator = new IrisSurfaceDecorator(engine);
        ceilingDecorator = new IrisCeilingDecorator(engine);
    }

    /** Applies the caves to the chunk at chunk coordinates {@code x}, {@code z}. */
    @ChunkCoordinates
    public void apply(int x, int z, Hunk<PlatformBlockState> output, boolean multicore, ChunkContext context) {
        PrecisionStopwatch caveStopwatch = PrecisionStopwatch.start();
        Mantle<Matter> mantle = engine.getMantle().getMantle();
        IrisDimensionCarvingResolver.State resolverState = new IrisDimensionCarvingResolver.State();
        Long2ObjectOpenHashMap<IrisBiome> caveBiomeCache = new Long2ObjectOpenHashMap<>(2048);
        IrisCarveScratch scratch = SCRATCH.get();
        scratch.reset();
        CarveWallBuffer walls = scratch.walls;
        CarveColumnMask[] columnMasks = scratch.columnMasks;
        CarveColumnMask[] boundaryMasks = scratch.boundaryMasks;
        int[] surfaceHeights = scratch.surfaceHeights;
        Map<String, IrisBiome> customBiomeCache = scratch.customBiomeCache;
        UpperDimensionContext upperCtx = engine.getUpperContext();
        boolean protectUpper = upperCtx != null && !engine.getDimension().isUpperDimensionCarving();
        int[] upperSurfaceHeights = protectUpper ? scratch.getOrCreateUpperSurfaceHeights() : null;
        int chunkBlockX = PowerOfTwoCoordinates.chunkToBlock(x);
        int chunkBlockZ = PowerOfTwoCoordinates.chunkToBlock(z);
        for (int columnIndex = 0; columnIndex < 256; columnIndex++) {
            int localX = PowerOfTwoCoordinates.unpackLocal16X(columnIndex);
            int localZ = columnIndex & 15;
            surfaceHeights[columnIndex] = context.getRoundedHeight(localX, localZ);
            if (protectUpper) {
                int worldX = localX + chunkBlockX;
                int worldZ = localZ + chunkBlockZ;
                int rawUpper = upperCtx.getUpperSurfaceY(worldX, worldZ);
                int gap = engine.getDimension().getUpperDimensionGap();
                upperSurfaceHeights[columnIndex] = Math.max(rawUpper, surfaceHeights[columnIndex] + gap);
            }
        }

        MantleChunk<Matter> mantleChunk = mantle.getChunk(x, z).use();
        try {
            PrecisionStopwatch resolveStopwatch = PrecisionStopwatch.start();
            int worldHeightSpan = engine.getWorld().maxHeight() - engine.getWorld().minHeight();
            int caveLavaHeight = engine.getDimension().getCaveLavaHeight();
            mantleChunk.iterate(MatterCavern.class, (xx, yy, zz, cavern) -> {
                if (cavern == null) {
                    return;
                }

                if (yy >= worldHeightSpan || yy <= 0) {
                    return;
                }

                int rx = xx & 15;
                int rz = zz & 15;
                int columnIndex = PowerOfTwoCoordinates.packLocal16(rx, rz);

                if (upperSurfaceHeights != null && yy >= upperSurfaceHeights[columnIndex]) {
                    return;
                }

                PlatformBlockState current = output.getRaw(rx, yy, rz);
                boolean explicitCarveIntent = hasExplicitCarveIntent(cavern);

                if (shouldPreserveExistingFluid(cavern, current)) {
                    return;
                }

                columnMasks[columnIndex].add(yy);

                if (!cavern.getCustomBiome().isEmpty()) {
                    scratch.customCaveBiomePresent = true;
                }

                if (current.isAir() && !explicitCarveIntent) {
                    return;
                }

                if (explicitCarveIntent) {
                    // Only a fluid cavern consumes the fluid sample, and on the maintenance path that
                    // sample is a full procedural stream evaluation, so never take it per voxel.
                    PlatformBlockState fluid = isFluidIntent(cavern) ? context.getFluid().get(rx, rz) : null;
                    output.setRaw(rx, yy, rz, resolveExplicitCarveState(cavern, fluid, LAVA, AIR));
                } else if (usesDefaultLava(caveLavaHeight, yy)) {
                    output.setRaw(rx, yy, rz, LAVA);
                } else {
                    output.setRaw(rx, yy, rz, AIR);
                }
            });
            if (scratch.customCaveBiomePresent) {
                addInternalWallsFromMantle(mantleChunk, walls, columnMasks);
            } else {
                addInternalWallsFromMasks(walls, columnMasks);
            }
            addCrossChunkBoundaryWalls(mantle, mantleChunk, walls, boundaryMasks, x, z, surfaceHeights);
            engine.getMetrics().getCarveResolve().put(resolveStopwatch.getMilliseconds());

            PrecisionStopwatch applyStopwatch = PrecisionStopwatch.start();
            try {
                walls.forEach((rx, yy, rz, cavern) -> {
                    int worldX = rx + chunkBlockX;
                    int worldZ = rz + chunkBlockZ;
                    String customBiome = cavern.getCustomBiome();
                    IrisBiome biome = customBiome.isEmpty()
                            ? resolveCaveBiome(caveBiomeCache, worldX, yy, worldZ, resolverState)
                            : resolveCustomBiome(customBiomeCache, customBiome);

                    if (biome != null) {
                        PlatformBlockState data = biome.getWall().get(rng, worldX, yy, worldZ, engine.getData());
                        int columnIndex = PowerOfTwoCoordinates.packLocal16(rx, rz);

                        if (data != null && B.isSolid(output.getRaw(rx, yy, rz)) && yy < surfaceHeights[columnIndex]) {
                            output.setRaw(rx, yy, rz, data);
                        }
                    }
                });

                for (int columnIndex = 0; columnIndex < 256; columnIndex++) {
                    processColumnFromMask(output, mantleChunk, mantle, columnMasks[columnIndex], columnIndex, x, z, resolverState, caveBiomeCache, customBiomeCache);
                }

                for (int columnIndex = 0; columnIndex < 256; columnIndex++) {
                    if (boundaryMasks[columnIndex].isEmpty() || !columnMasks[columnIndex].isEmpty()) {
                        continue;
                    }
                    processBoundaryColumnFromMask(output, boundaryMasks[columnIndex], walls, columnIndex, x, z, resolverState, caveBiomeCache, customBiomeCache);
                }

                // Surface-break carving must not leave an ore cap suspended across the opening.
                for (int columnIndex = 0; columnIndex < surfaceHeights.length; columnIndex++) {
                    int surfaceY = surfaceHeights[columnIndex];
                    if (surfaceY <= 0 || surfaceY >= output.getHeight()) {
                        continue;
                    }

                    int belowY = surfaceY - 1;
                    if (!columnMasks[columnIndex].contains(belowY) && !boundaryMasks[columnIndex].contains(belowY)) {
                        continue;
                    }

                    int localX = PowerOfTwoCoordinates.unpackLocal16X(columnIndex);
                    int localZ = columnIndex & 15;
                    PlatformBlockState surface = output.getRaw(localX, surfaceY, localZ);
                    PlatformBlockState below = output.getRaw(localX, belowY, localZ);
                    if (isUnsupportedSurfaceOre(surface, below)) {
                        output.setRaw(localX, surfaceY, localZ, AIR);
                    }
                }
            } finally {
                engine.getMetrics().getCarveApply().put(applyStopwatch.getMilliseconds());
            }
        } finally {
            engine.getMetrics().getCave().put(caveStopwatch.getMilliseconds());
            mantleChunk.release();
        }
    }

    static boolean hasExplicitCarveIntent(MatterCavern cavern) {
        return cavern != null && (isFluidIntent(cavern) || cavern.isLava() || cavern.getLiquid() == 3);
    }

    static boolean isFluidIntent(MatterCavern cavern) {
        return cavern != null && cavern.getLiquid() == LIQUID_FLUID;
    }

    static boolean shouldPreserveExistingFluid(MatterCavern cavern, PlatformBlockState current) {
        return B.isFluid(current) && !hasExplicitCarveIntent(cavern);
    }

    static boolean usesDefaultLava(int caveLavaHeight, int y) {
        return y <= caveLavaHeight;
    }

    static boolean isUnsupportedSurfaceOre(PlatformBlockState surface, PlatformBlockState below) {
        return B.isOre(surface) && !B.isSolid(below);
    }

    static PlatformBlockState resolveExplicitCarveState(MatterCavern cavern, PlatformBlockState fluid,
                                                        PlatformBlockState lava, PlatformBlockState air) {
        if (cavern == null) {
            return null;
        }
        if (isFluidIntent(cavern)) {
            return fluid;
        }
        if (cavern.isLava()) {
            return lava;
        }
        return cavern.getLiquid() == 3 ? air : null;
    }

    private void addInternalWallsFromMasks(CarveWallBuffer walls, CarveColumnMask[] columnMasks) {
        for (int columnIndex = 0; columnIndex < 256; columnIndex++) {
            CarveColumnMask columnMask = columnMasks[columnIndex];
            if (columnMask.isEmpty()) {
                continue;
            }

            int rx = columnIndex >> 4;
            int rz = columnIndex & 15;
            int yy = columnMask.nextSetBit(0);
            while (yy >= 0) {
                if (rz < 15 && !columnMasks[columnIndex + 1].contains(yy)) {
                    walls.put(rx, yy, rz + 1, BASIC_CAVERN);
                }
                if (rx < 15 && !columnMasks[columnIndex + 16].contains(yy)) {
                    walls.put(rx + 1, yy, rz, BASIC_CAVERN);
                }
                if (rz > 0 && !columnMasks[columnIndex - 1].contains(yy)) {
                    walls.put(rx, yy, rz - 1, BASIC_CAVERN);
                }
                if (rx > 0 && !columnMasks[columnIndex - 16].contains(yy)) {
                    walls.put(rx - 1, yy, rz, BASIC_CAVERN);
                }
                yy = columnMask.nextSetBit(yy + 1);
            }
        }
    }

    private void addInternalWallsFromMantle(MantleChunk<Matter> mc, CarveWallBuffer walls, CarveColumnMask[] columnMasks) {
        for (int columnIndex = 0; columnIndex < 256; columnIndex++) {
            CarveColumnMask columnMask = columnMasks[columnIndex];
            if (columnMask.isEmpty()) {
                continue;
            }

            int rx = columnIndex >> 4;
            int rz = columnIndex & 15;
            int yy = columnMask.nextSetBit(0);
            while (yy >= 0) {
                MatterCavern cavern = mc.get(rx, yy, rz, MatterCavern.class);
                if (cavern != null) {
                    if (rz < 15 && mc.get(rx, yy, rz + 1, MatterCavern.class) == null) {
                        walls.put(rx, yy, rz + 1, cavern);
                    }
                    if (rx < 15 && mc.get(rx + 1, yy, rz, MatterCavern.class) == null) {
                        walls.put(rx + 1, yy, rz, cavern);
                    }
                    if (rz > 0 && mc.get(rx, yy, rz - 1, MatterCavern.class) == null) {
                        walls.put(rx, yy, rz - 1, cavern);
                    }
                    if (rx > 0 && mc.get(rx - 1, yy, rz, MatterCavern.class) == null) {
                        walls.put(rx - 1, yy, rz, cavern);
                    }
                }
                yy = columnMask.nextSetBit(yy + 1);
            }
        }
    }

    private void addCrossChunkBoundaryWalls(
            Mantle<Matter> mantle,
            MantleChunk<Matter> mc,
            CarveWallBuffer walls,
            CarveColumnMask[] boundaryMasks,
            int chunkX,
            int chunkZ,
            int[] surfaceHeights
    ) {
        int baseX = PowerOfTwoCoordinates.chunkToBlock(chunkX);
        int baseZ = PowerOfTwoCoordinates.chunkToBlock(chunkZ);
        int maxSurfaceY = 0;
        for (int index = 0; index < surfaceHeights.length; index++) {
            if (surfaceHeights[index] > maxSurfaceY) {
                maxSurfaceY = surfaceHeights[index];
            }
        }
        int maxY = Math.min(engine.getWorld().maxHeight() - engine.getWorld().minHeight() - 1, maxSurfaceY + 1);
        if (maxY < 1) {
            return;
        }

        MantleChunk<Matter> west = existingMantleChunk(mantle, chunkX - 1, chunkZ);
        MantleChunk<Matter> east = existingMantleChunk(mantle, chunkX + 1, chunkZ);
        MantleChunk<Matter> north = existingMantleChunk(mantle, chunkX, chunkZ - 1);
        MantleChunk<Matter> south = existingMantleChunk(mantle, chunkX, chunkZ + 1);
        if (west == null && east == null && north == null && south == null) {
            return;
        }

        for (int yy = 1; yy <= maxY; yy++) {
            for (int offset = 0; offset < 16; offset++) {
                if (west != null) {
                    tryAddBoundaryWall(mc, west, walls, boundaryMasks, 0, yy, offset, 15, offset);
                }
                if (east != null) {
                    tryAddBoundaryWall(mc, east, walls, boundaryMasks, 15, yy, offset, 0, offset);
                }
                if (north != null) {
                    tryAddBoundaryWall(mc, north, walls, boundaryMasks, offset, yy, 0, offset, 15);
                }
                if (south != null) {
                    tryAddBoundaryWall(mc, south, walls, boundaryMasks, offset, yy, 15, offset, 0);
                }
            }
        }
    }

    private void tryAddBoundaryWall(
            MantleChunk<Matter> mc,
            MantleChunk<Matter> neighborChunk,
            CarveWallBuffer walls,
            CarveColumnMask[] boundaryMasks,
            int localX,
            int yy,
            int localZ,
            int neighborX,
            int neighborZ
    ) {
        if (mc.get(localX, yy, localZ, MatterCavern.class) != null) {
            return;
        }

        MatterCavern neighbor = neighborChunk.get(neighborX, yy, neighborZ, MatterCavern.class);
        if (neighbor == null) {
            return;
        }

        walls.put(localX, yy, localZ, neighbor);
        int columnIndex = PowerOfTwoCoordinates.packLocal16(localX, localZ);
        boundaryMasks[columnIndex].add(yy);
    }

    private MantleChunk<Matter> existingMantleChunk(Mantle<Matter> mantle, int chunkX, int chunkZ) {
        TectonicPlate<Matter> plate = mantle.getLoadedRegions().get(Mantle.key(chunkX >> 5, chunkZ >> 5));
        if (plate == null || plate.isClosed()) {
            return null;
        }
        return plate.get(chunkX & 31, chunkZ & 31);
    }

    private void processColumnFromMask(
            Hunk<PlatformBlockState> output,
            MantleChunk<Matter> mc,
            Mantle<Matter> mantle,
            CarveColumnMask columnMask,
            int columnIndex,
            int chunkX,
            int chunkZ,
            IrisDimensionCarvingResolver.State resolverState,
            Long2ObjectOpenHashMap<IrisBiome> caveBiomeCache,
            Map<String, IrisBiome> customBiomeCache
    ) {
        if (columnMask == null || columnMask.isEmpty()) {
            return;
        }

        int firstHeight = columnMask.nextSetBit(0);
        if (firstHeight < 0) {
            return;
        }

        int rx = PowerOfTwoCoordinates.unpackLocal16X(columnIndex);
        int rz = columnIndex & 15;
        int worldX = rx + PowerOfTwoCoordinates.chunkToBlock(chunkX);
        int worldZ = rz + PowerOfTwoCoordinates.chunkToBlock(chunkZ);
        CaveZone zone = new CaveZone();
        zone.setFloor(firstHeight);
        int buf = firstHeight - 1;
        int y = firstHeight;

        while (y >= 0) {
            if (y <= engine.getHeight()) {
                if (y == buf + 1) {
                    buf = y;
                    zone.ceiling = buf;
                } else {
                    if (zone.isValid(engine)) {
                        processZone(output, mc, mantle, zone, rx, rz, worldX, worldZ, resolverState, caveBiomeCache, customBiomeCache);
                    }
                    zone = new CaveZone();
                    zone.setFloor(y);
                    buf = y;
                }
            }

            y = columnMask.nextSetBit(y + 1);
        }

        if (zone.isValid(engine)) {
            processZone(output, mc, mantle, zone, rx, rz, worldX, worldZ, resolverState, caveBiomeCache, customBiomeCache);
        }
    }

    private void processBoundaryColumnFromMask(
            Hunk<PlatformBlockState> output,
            CarveColumnMask boundaryMask,
            CarveWallBuffer walls,
            int columnIndex,
            int chunkX,
            int chunkZ,
            IrisDimensionCarvingResolver.State resolverState,
            Long2ObjectOpenHashMap<IrisBiome> caveBiomeCache,
            Map<String, IrisBiome> customBiomeCache
    ) {
        int firstHeight = boundaryMask.nextSetBit(0);
        if (firstHeight < 0) {
            return;
        }

        int rx = PowerOfTwoCoordinates.unpackLocal16X(columnIndex);
        int rz = columnIndex & 15;
        int worldX = rx + PowerOfTwoCoordinates.chunkToBlock(chunkX);
        int worldZ = rz + PowerOfTwoCoordinates.chunkToBlock(chunkZ);
        int zoneFloor = firstHeight;
        int zoneCeiling = firstHeight;
        int y = boundaryMask.nextSetBit(firstHeight + 1);

        while (y >= 0) {
            if (y == zoneCeiling + 1) {
                zoneCeiling = y;
            } else {
                paintBoundaryZone(output, walls, rx, rz, worldX, worldZ, zoneFloor, zoneCeiling, resolverState, caveBiomeCache, customBiomeCache);
                zoneFloor = y;
                zoneCeiling = y;
            }
            y = boundaryMask.nextSetBit(y + 1);
        }

        paintBoundaryZone(output, walls, rx, rz, worldX, worldZ, zoneFloor, zoneCeiling, resolverState, caveBiomeCache, customBiomeCache);
    }

    private void paintBoundaryZone(
            Hunk<PlatformBlockState> output,
            CarveWallBuffer walls,
            int rx,
            int rz,
            int worldX,
            int worldZ,
            int zoneFloor,
            int zoneCeiling,
            IrisDimensionCarvingResolver.State resolverState,
            Long2ObjectOpenHashMap<IrisBiome> caveBiomeCache,
            Map<String, IrisBiome> customBiomeCache
    ) {
        IrisBiome floorBiome = resolveCaveBoundaryBiome(
                walls.get(rx, zoneFloor, rz), worldX, zoneFloor, worldZ,
                resolverState, caveBiomeCache, customBiomeCache);
        IrisBiome ceilingBiome = resolveCaveBoundaryBiome(
                walls.get(rx, zoneCeiling, rz), worldX, zoneCeiling, worldZ,
                resolverState, caveBiomeCache, customBiomeCache);
        if (floorBiome == null && ceilingBiome == null) {
            return;
        }

        if (floorBiome != null) {
            KList<PlatformBlockState> floorLayers = floorBiome.generateLayers(
                    engine.getDimension(), worldX, worldZ, rng, 3, zoneFloor, engine.getData(), engine.getComplex());
            for (int i = 0; i < zoneFloor - 1; i++) {
                if (!floorLayers.hasIndex(i)) {
                    break;
                }
                int floorY = zoneFloor - i - 1;
                if (floorY < 0) {
                    break;
                }
                PlatformBlockState existing = output.getRaw(rx, floorY, rz);
                PlatformBlockState layer = floorLayers.get(i);
                if (!B.isSolid(existing) || !canReplaceCaveFloorLayer(output, rx, floorY, rz, layer)) {
                    continue;
                }
                if (B.isOre(existing)) {
                    output.setRaw(rx, floorY, rz, B.toDeepSlateOre(existing, layer));
                    continue;
                }
                output.setRaw(rx, floorY, rz, layer);
            }
        }

        if (ceilingBiome != null) {
            int worldMaxY = engine.getWorld().maxHeight() - engine.getWorld().minHeight();
            KList<PlatformBlockState> ceilingLayers = ceilingBiome.generateCeilingLayers(
                    engine.getDimension(), worldX, worldZ, rng, 3, zoneCeiling, engine.getData(), engine.getComplex());
            for (int i = 0; i < ceilingLayers.size(); i++) {
                int ceilingY = zoneCeiling + i + 1;
                if (ceilingY >= worldMaxY) {
                    break;
                }
                PlatformBlockState existing = output.getRaw(rx, ceilingY, rz);
                if (!B.isSolid(existing)) {
                    continue;
                }
                PlatformBlockState layer = ceilingLayers.get(i);
                if (B.isOre(existing)) {
                    output.setRaw(rx, ceilingY, rz, B.toDeepSlateOre(existing, layer));
                    continue;
                }
                output.setRaw(rx, ceilingY, rz, layer);
            }
        }
    }

    /**
     * 1-in-16 marker roll from a SplitMix64 finalizer over (carve seed, block position, salt).
     * Deterministic per seed and position, thread-order independent, allocation free.
     */
    private boolean markerRoll(int x, int y, int z, long salt) {
        long h = (engine.getSeedManager().getCarve() + salt) ^ BlockPosition.toLong(x, y, z);
        h = (h ^ (h >>> 30)) * 0xBF58476D1CE4E5B9L;
        h = (h ^ (h >>> 27)) * 0x94D049BB133111EBL;
        h ^= h >>> 31;
        return (h & 15L) == 0L;
    }

    private void processZone(Hunk<PlatformBlockState> output, MantleChunk<Matter> mc, Mantle<Matter> mantle, CaveZone zone, int rx, int rz, int xx, int zz, IrisDimensionCarvingResolver.State resolverState, Long2ObjectOpenHashMap<IrisBiome> caveBiomeCache, Map<String, IrisBiome> customBiomeCache) {
        int maxY = output.getHeight();

        if (zone.ceiling + 1 < maxY && B.isDecorant(output.getRaw(rx, zone.ceiling + 1, rz))) {
            output.setRaw(rx, zone.ceiling + 1, rz, AIR);
        }

        if (B.isDecorant(output.getRaw(rx, zone.ceiling, rz))) {
            output.setRaw(rx, zone.ceiling, rz, AIR);
        }

        // Seed-derived and position-keyed, never Math.random(): these markers persist into the
        // mantle and drive cave spawning, so the same seed must stamp the same markers on
        // every run and every platform. Inline mix, no allocation on this hot path.
        if (markerRoll(xx, zone.ceiling, zz, 0x9E3779B97F4A7C15L)) {
            mantle.set(xx, zone.ceiling, zz, MarkerMatter.CAVE_CEILING);
        }

        if (markerRoll(xx, zone.floor, zz, 0xC2B2AE3D27D4EB4FL)) {
            mantle.set(xx, zone.floor, zz, MarkerMatter.CAVE_FLOOR);
        }

        IrisBiome floorBiome = resolveCaveBoundaryBiome(mc, rx, zone.floor, rz, xx, zz, resolverState, caveBiomeCache, customBiomeCache);
        IrisBiome ceilingBiome = resolveCaveBoundaryBiome(mc, rx, zone.ceiling, rz, xx, zz, resolverState, caveBiomeCache, customBiomeCache);
        if (floorBiome == null && ceilingBiome == null) {
            return;
        }

        if (floorBiome != null) {
            KList<PlatformBlockState> floorBlocks = floorBiome.generateLayers(engine.getDimension(), xx, zz, rng, 3, zone.floor, engine.getData(), engine.getComplex());
            for (int i = 0; i < zone.floor - 1; i++) {
                if (!floorBlocks.hasIndex(i)) {
                    break;
                }
                int y = zone.floor - i - 1;
                PlatformBlockState block = floorBlocks.get(i);
                PlatformBlockState existing = output.getRaw(rx, y, rz);
                if (!B.isSolid(existing) || !canReplaceCaveFloorLayer(output, rx, y, rz, block)) {
                    continue;
                }
                if (B.isOre(existing)) {
                    output.setRaw(rx, y, rz, B.toDeepSlateOre(existing, block));
                    continue;
                }
                output.setRaw(rx, y, rz, block);
            }
        }

        if (ceilingBiome != null) {
            KList<PlatformBlockState> ceilingBlocks = ceilingBiome.generateCeilingLayers(engine.getDimension(), xx, zz, rng, 3, zone.ceiling, engine.getData(), engine.getComplex());
            for (int i = 0; i < ceilingBlocks.size(); i++) {
                int cy = zone.ceiling + i + 1;
                if (cy >= maxY) {
                    break;
                }
                PlatformBlockState block = ceilingBlocks.get(i);
                PlatformBlockState existing = output.getRaw(rx, cy, rz);
                if (!B.isSolid(existing)) {
                    continue;
                }
                if (B.isOre(existing)) {
                    output.setRaw(rx, cy, rz, B.toDeepSlateOre(existing, block));
                    continue;
                }
                output.setRaw(rx, cy, rz, block);
            }
        }

        if (floorBiome != null && decorators.hasDecorators(floorBiome, DecorationPart.NONE) && hasStableCaveFloorSupport(output, rx, zone.getFloor(), rz)) {
            surfaceDecorator.decorate(rx, rz, xx, xx, xx, zz, zz, zz, output, floorBiome, InferredType.CAVE, zone.getFloor() - 1, zone.airThickness());
        }

        if (ceilingBiome != null && decorators.hasDecorators(ceilingBiome, DecorationPart.CEILING) && zone.getCeiling() + 1 < maxY && B.isSolid(output.getRaw(rx, zone.getCeiling() + 1, rz))) {
            ceilingDecorator.decorate(rx, rz, xx, xx, xx, zz, zz, zz, output, ceilingBiome, InferredType.CAVE, zone.getCeiling(), zone.airThickness());
        }
    }

    IrisBiome resolveCaveBoundaryBiome(MantleChunk<Matter> mantleChunk, int x, int y, int z, int worldX, int worldZ, IrisDimensionCarvingResolver.State resolverState, Long2ObjectOpenHashMap<IrisBiome> caveBiomeCache, Map<String, IrisBiome> customBiomeCache) {
        MatterCavern cavern = dataIfPresent(mantleChunk, x, y, z, MatterCavern.class);
        return resolveCaveBoundaryBiome(
                cavern, worldX, y, worldZ, resolverState, caveBiomeCache, customBiomeCache);
    }

    private static <T> T dataIfPresent(MantleChunk<Matter> mantleChunk, int x, int y, int z, Class<T> type) {
        int section = y >> 4;
        if (y < 0 || !mantleChunk.exists(section)) {
            return null;
        }
        Matter matter = mantleChunk.get(section);
        if (matter == null || !matter.hasSlice(type)) {
            return null;
        }
        return matter.<T>getSlice(type).get(x & 15, y & 15, z & 15);
    }

    IrisBiome resolveCaveBoundaryBiome(MatterCavern cavern, int worldX, int y, int worldZ, IrisDimensionCarvingResolver.State resolverState, Long2ObjectOpenHashMap<IrisBiome> caveBiomeCache, Map<String, IrisBiome> customBiomeCache) {
        if (cavern != null && !cavern.getCustomBiome().isEmpty()) {
            return resolveCustomBiome(customBiomeCache, cavern.getCustomBiome());
        }
        return resolveCaveBiome(caveBiomeCache, worldX, y, worldZ, resolverState);
    }

    static boolean canReplaceCaveFloorLayer(Hunk<PlatformBlockState> output, int x, int y, int z, PlatformBlockState layer) {
        return !isGravityAffected(layer) || y > 0 && B.isSolid(output.getRaw(x, y - 1, z));
    }

    static boolean hasStableCaveFloorSupport(Hunk<PlatformBlockState> output, int x, int floorY, int z) {
        if (floorY <= 0) {
            return false;
        }
        PlatformBlockState support = output.getRaw(x, floorY - 1, z);
        if (!B.isSolid(support)) {
            return false;
        }
        return !isGravityAffected(support) || floorY > 1 && B.isSolid(output.getRaw(x, floorY - 2, z));
    }

    static boolean isGravityAffected(PlatformBlockState state) {
        return state != null && state.hasGravity();
    }

    private IrisBiome resolveCaveBiome(Long2ObjectOpenHashMap<IrisBiome> caveBiomeCache, int x, int y, int z, IrisDimensionCarvingResolver.State resolverState) {
        IrisBiome center = sampleCaveBiome(caveBiomeCache, x, y, z, resolverState);
        if (center == null) {
            return null;
        }

        IrisBiome xPos = sampleCaveBiome(caveBiomeCache, x + CAVE_BIOME_BLEND_RADIUS, y, z, resolverState);
        IrisBiome xNeg = sampleCaveBiome(caveBiomeCache, x - CAVE_BIOME_BLEND_RADIUS, y, z, resolverState);
        IrisBiome zPos = sampleCaveBiome(caveBiomeCache, x, y, z + CAVE_BIOME_BLEND_RADIUS, resolverState);
        IrisBiome zNeg = sampleCaveBiome(caveBiomeCache, x, y, z - CAVE_BIOME_BLEND_RADIUS, resolverState);

        if (xPos == center && xNeg == center && zPos == center && zNeg == center) {
            return center;
        }

        int roll = Math.floorMod(rng.nextParallelRNG(BlockPosition.toLong(x, y, z)).nextInt(), CAVE_BIOME_BLEND_TOTAL_WEIGHT);
        if (roll < CAVE_BIOME_BLEND_CENTER_WEIGHT) {
            return center;
        }
        roll -= CAVE_BIOME_BLEND_CENTER_WEIGHT;
        if (roll == 0) {
            return xPos != null ? xPos : center;
        }
        if (roll == 1) {
            return xNeg != null ? xNeg : center;
        }
        if (roll == 2) {
            return zPos != null ? zPos : center;
        }
        return zNeg != null ? zNeg : center;
    }

    private IrisBiome sampleCaveBiome(Long2ObjectOpenHashMap<IrisBiome> caveBiomeCache, int x, int y, int z, IrisDimensionCarvingResolver.State resolverState) {
        long key = BlockPosition.toLong(x, y, z);
        IrisBiome cachedBiome = caveBiomeCache.get(key);
        if (cachedBiome != null) {
            return cachedBiome;
        }

        IrisBiome resolvedBiome = engine.getCaveBiome(x, y, z, resolverState);
        if (resolvedBiome != null) {
            caveBiomeCache.put(key, resolvedBiome);
        }
        return resolvedBiome;
    }

    private IrisBiome resolveCustomBiome(Map<String, IrisBiome> customBiomeCache, String customBiome) {
        if (customBiomeCache.containsKey(customBiome)) {
            return customBiomeCache.get(customBiome);
        }

        IrisBiome loaded = engine.getData().getBiomeLoader().load(customBiome);
        customBiomeCache.put(customBiome, loaded);
        return loaded;
    }

    @Data
    public static class CaveZone {
        private int ceiling = -1;
        private int floor = -1;

        public int airThickness() {
            return (ceiling - floor) - 1;
        }

        public boolean isValid(Engine engine) {
            return floor < ceiling && ceiling - floor >= 1 && floor >= 0 && ceiling <= engine.getHeight() && airThickness() > 0;
        }

        public String toString() {
            return floor + "-" + ceiling;
        }
    }
}
