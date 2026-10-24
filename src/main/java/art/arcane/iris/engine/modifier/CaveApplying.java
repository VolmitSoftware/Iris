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

package art.arcane.iris.engine.modifier;

import art.arcane.iris.builtin.MatterMantle;
import art.arcane.iris.builtin.decorant.DecorationPart;
import art.arcane.volmlib.util.mantle.runtime.MantleChunk;
import art.arcane.volmlib.util.math.BlockPosition;
import art.arcane.volmlib.util.math.PowerOfTwoCoordinates;
import art.arcane.volmlib.util.math.RNG;
import art.arcane.volmlib.util.matter.Matter;
import art.arcane.volmlib.util.matter.MatterCavern;
import art.arcane.volmlib.util.matter.MatterMarker;
import art.arcane.volmlib.util.matter.slices.MarkerMatter;
import art.arcane.iris.engine.mantle.components.CarveWorld;
import com.volmit.block.BlockRegistry;
import com.volmit.block.BlockState;
import com.volmit.hunk.Hunk;
import com.volmit.resource.PackResources;
import com.volmit.resource.ResourceId;
import com.volmit.world.PackWorld;
import com.volmit.world.TerrainSampler;
import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * Applies the caves the mantle planned to a chunk's blocks for {@link CarveModule}: carves each cave zone, paints
 * its floor and ceiling with the cave biomes' layers, fills fluids and lava and decorates the cave surfaces.
 * One per world, holding the carve stream's random.
 */
public class CaveApplying {
    private static final byte LIQUID_FLUID = 1;
    private static final byte LIQUID_FORCED_AIR = 3;
    private static final ThreadLocal<IrisCarveScratch> SCRATCH = ThreadLocal.withInitial(IrisCarveScratch::new);
    private static final int CAVE_BIOME_BLEND_RADIUS = 3;
    private static final int CAVE_BIOME_BLEND_CENTER_WEIGHT = 4;
    private static final int CAVE_BIOME_BLEND_TOTAL_WEIGHT = 8;
    private static final int CAVE_BIOME_CACHE_SIZE = 2048;
    private static final int CHUNK_AREA = 256;
    private static final int LAYER_DEPTH = 3;
    private static final long CEILING_MARKER_SALT = 0x9E3779B97F4A7C15L;
    private static final long FLOOR_MARKER_SALT = 0xC2B2AE3D27D4EB4FL;
    private static final MatterCavern BASIC_CAVERN = new MatterCavern(true, "", (byte) 0);

    private final CarveWorld world;
    private final PackResources resources;
    private final BlockRegistry blocks;
    private final boolean upperDimensionCarving;
    private final RNG rng;
    private final BlockState caveAir;
    private final BlockState plainAir;
    private final BlockState lava;

    public CaveApplying(PackWorld packWorld, CarveWorld world, boolean upperDimensionCarving) {
        this.world = world;
        this.resources = packWorld.resources();
        this.blocks = resources.blocks();
        this.upperDimensionCarving = upperDimensionCarving;
        this.rng = new RNG(world.carveSeed());
        this.caveAir = blocks.find("minecraft:cave_air").orElseThrow();
        this.plainAir = blocks.find("minecraft:air").orElseThrow();
        this.lava = blocks.find("minecraft:lava").orElseThrow();
    }

    /**
     * Applies the caves to the chunk at chunk coordinates {@code x}, {@code z}, whose blocks are {@code output}; the caves are in
     * {@code mantle}, {@code terrain} gives the heights and cave biomes around the chunk and {@code dressing} what its caves' walls,
     * floors and ceilings get.
     */
    public void apply(MatterMantle mantle, TerrainSampler terrain, int x, int z, Hunk<BlockState> output, CaveDressing dressing) {
        MantleChunk<Matter> mantleChunk = mantle.chunk(x, z);
        if (mantleChunk == null) {
            return;
        }
        new Application(mantle, mantleChunk, terrain, x, z, output, dressing).run();
    }

    static boolean hasExplicitCarveIntent(MatterCavern cavern) {
        return cavern != null && (isFluidIntent(cavern) || cavern.isLava() || cavern.getLiquid() == LIQUID_FORCED_AIR);
    }

    static boolean isFluidIntent(MatterCavern cavern) {
        return cavern != null && cavern.getLiquid() == LIQUID_FLUID;
    }

    static boolean shouldPreserveExistingFluid(MatterCavern cavern, boolean existingIsFluid) {
        return existingIsFluid && !hasExplicitCarveIntent(cavern);
    }

    static boolean usesDefaultLava(int caveLavaHeight, int y) {
        return y <= caveLavaHeight;
    }

    static boolean isUnsupportedSurfaceOre(boolean surfaceIsOre, boolean belowIsSolid) {
        return surfaceIsOre && !belowIsSolid;
    }

    static BlockState resolveExplicitCarveState(MatterCavern cavern, BlockState fluid, BlockState lava, BlockState air) {
        if (cavern == null) {
            return null;
        }
        if (isFluidIntent(cavern)) {
            return fluid;
        }
        if (cavern.isLava()) {
            return lava;
        }
        return cavern.getLiquid() == LIQUID_FORCED_AIR ? air : null;
    }

    static boolean canReplaceCaveFloorLayer(Hunk<BlockState> output, int x, int y, int z, BlockState layer, CaveBlockClasses classes) {
        return !classes.hasGravity(layer) || y > 0 && classes.isSolid(output.get(x, y - 1, z));
    }

    static boolean hasStableCaveFloorSupport(Hunk<BlockState> output, int x, int floorY, int z, CaveBlockClasses classes) {
        if (floorY <= 0) {
            return false;
        }
        BlockState support = output.get(x, floorY - 1, z);
        if (!classes.isSolid(support)) {
            return false;
        }
        return !classes.hasGravity(support) || floorY > 1 && classes.isSolid(output.get(x, floorY - 2, z));
    }

    /** One chunk's application: the chunk's blocks, mantle and the caches its passes share. */
    final class Application {
        private final MatterMantle mantle;
        private final MantleChunk<Matter> mantleChunk;
        private final TerrainSampler terrain;
        private final int chunkX;
        private final int chunkZ;
        private final Hunk<BlockState> output;
        private final CaveDressing dressing;
        private final CaveBlockClasses classes = new CaveBlockClasses(blocks);
        private final CaveBiomeCache caveBiomeCache = new CaveBiomeCache(CAVE_BIOME_CACHE_SIZE);
        private final IrisCarveScratch scratch = SCRATCH.get();

        Application(MatterMantle mantle, MantleChunk<Matter> mantleChunk, TerrainSampler terrain, int chunkX, int chunkZ, Hunk<BlockState> output,
                    CaveDressing dressing) {
            this.mantle = mantle;
            this.mantleChunk = mantleChunk;
            this.terrain = terrain;
            this.chunkX = chunkX;
            this.chunkZ = chunkZ;
            this.output = output;
            this.dressing = dressing;
        }

        void run() {
            scratch.reset();
            CarveWallBuffer walls = scratch.walls;
            CarveColumnMask[] columnMasks = scratch.columnMasks;
            CarveColumnMask[] boundaryMasks = scratch.boundaryMasks;
            int[] surfaceHeights = scratch.surfaceHeights;
            Map<String, ResourceId> customBiomeCache = scratch.customBiomeCache;
            int chunkBlockX = PowerOfTwoCoordinates.chunkToBlock(chunkX);
            int chunkBlockZ = PowerOfTwoCoordinates.chunkToBlock(chunkZ);
            boolean protectUpper = !upperDimensionCarving && terrain.upperDimensionY(chunkBlockX, chunkBlockZ).isPresent();
            int[] upperSurfaceHeights = protectUpper ? scratch.getOrCreateUpperSurfaceHeights() : null;
            for (int columnIndex = 0; columnIndex < CHUNK_AREA; columnIndex++) {
                int localX = PowerOfTwoCoordinates.unpackLocal16X(columnIndex);
                int localZ = columnIndex & 15;
                int worldX = localX + chunkBlockX;
                int worldZ = localZ + chunkBlockZ;
                surfaceHeights[columnIndex] = terrain.surfaceHeight(worldX, worldZ);
                if (protectUpper) {
                    upperSurfaceHeights[columnIndex] = terrain.upperDimensionY(worldX, worldZ).getAsInt();
                }
            }

            int worldHeightSpan = world.height();
            int caveLavaHeight = world.lavaHeight();
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

                BlockState current = output.get(rx, yy, rz);
                boolean explicitCarveIntent = hasExplicitCarveIntent(cavern);

                if (shouldPreserveExistingFluid(cavern, classes.isFluid(current))) {
                    return;
                }

                columnMasks[columnIndex].add(yy);

                if (!cavern.getCustomBiome().isEmpty()) {
                    scratch.customCaveBiomePresent = true;
                }

                if (classes.isAir(current) && !explicitCarveIntent) {
                    return;
                }

                if (explicitCarveIntent) {
                    // Only a fluid cavern consumes the fluid sample, so never take it per voxel.
                    BlockState fluid = isFluidIntent(cavern) ? dressing.fluidAt(chunkBlockX + rx, chunkBlockZ + rz) : null;
                    BlockState carved = resolveExplicitCarveState(cavern, fluid, lava, caveAir);
                    output.set(rx, yy, rz, carved == null ? plainAir : carved);
                } else if (usesDefaultLava(caveLavaHeight, yy)) {
                    output.set(rx, yy, rz, lava);
                } else {
                    output.set(rx, yy, rz, caveAir);
                }
            });
            if (scratch.customCaveBiomePresent) {
                addInternalWallsFromMantle(walls, columnMasks);
            } else {
                addInternalWallsFromMasks(walls, columnMasks);
            }
            addCrossChunkBoundaryWalls(walls, boundaryMasks, surfaceHeights);

            walls.forEach((rx, yy, rz, cavern) -> {
                int worldX = rx + chunkBlockX;
                int worldZ = rz + chunkBlockZ;
                String customBiome = cavern.getCustomBiome();
                ResourceId biome = customBiome.isEmpty()
                        ? resolveCaveBiome(worldX, yy, worldZ)
                        : resolveCustomBiome(customBiomeCache, customBiome);

                if (biome != null) {
                    BlockState data = dressing.wall(biome, rng, worldX, yy, worldZ);
                    int columnIndex = PowerOfTwoCoordinates.packLocal16(rx, rz);

                    if (data != null && classes.isSolid(output.get(rx, yy, rz)) && yy < surfaceHeights[columnIndex]) {
                        output.set(rx, yy, rz, data);
                    }
                }
            });

            for (int columnIndex = 0; columnIndex < CHUNK_AREA; columnIndex++) {
                processColumnFromMask(columnMasks[columnIndex], columnIndex, customBiomeCache);
            }

            for (int columnIndex = 0; columnIndex < CHUNK_AREA; columnIndex++) {
                if (boundaryMasks[columnIndex].isEmpty() || !columnMasks[columnIndex].isEmpty()) {
                    continue;
                }
                processBoundaryColumnFromMask(boundaryMasks[columnIndex], walls, columnIndex, customBiomeCache);
            }

            // Surface-break carving must not leave an ore cap suspended across the opening.
            for (int columnIndex = 0; columnIndex < surfaceHeights.length; columnIndex++) {
                int surfaceY = surfaceHeights[columnIndex];
                if (surfaceY <= 0 || surfaceY >= output.height()) {
                    continue;
                }

                int belowY = surfaceY - 1;
                if (!columnMasks[columnIndex].contains(belowY) && !boundaryMasks[columnIndex].contains(belowY)) {
                    continue;
                }

                int localX = PowerOfTwoCoordinates.unpackLocal16X(columnIndex);
                int localZ = columnIndex & 15;
                BlockState surface = output.get(localX, surfaceY, localZ);
                BlockState below = output.get(localX, belowY, localZ);
                if (isUnsupportedSurfaceOre(classes.isOre(surface), classes.isSolid(below))) {
                    output.set(localX, surfaceY, localZ, caveAir);
                }
            }
        }

        private void addInternalWallsFromMasks(CarveWallBuffer walls, CarveColumnMask[] columnMasks) {
            for (int columnIndex = 0; columnIndex < CHUNK_AREA; columnIndex++) {
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

        private void addInternalWallsFromMantle(CarveWallBuffer walls, CarveColumnMask[] columnMasks) {
            for (int columnIndex = 0; columnIndex < CHUNK_AREA; columnIndex++) {
                CarveColumnMask columnMask = columnMasks[columnIndex];
                if (columnMask.isEmpty()) {
                    continue;
                }

                int rx = columnIndex >> 4;
                int rz = columnIndex & 15;
                int yy = columnMask.nextSetBit(0);
                while (yy >= 0) {
                    MatterCavern cavern = mantleChunk.get(rx, yy, rz, MatterCavern.class);
                    if (cavern != null) {
                        if (rz < 15 && mantleChunk.get(rx, yy, rz + 1, MatterCavern.class) == null) {
                            walls.put(rx, yy, rz + 1, cavern);
                        }
                        if (rx < 15 && mantleChunk.get(rx + 1, yy, rz, MatterCavern.class) == null) {
                            walls.put(rx + 1, yy, rz, cavern);
                        }
                        if (rz > 0 && mantleChunk.get(rx, yy, rz - 1, MatterCavern.class) == null) {
                            walls.put(rx, yy, rz - 1, cavern);
                        }
                        if (rx > 0 && mantleChunk.get(rx - 1, yy, rz, MatterCavern.class) == null) {
                            walls.put(rx - 1, yy, rz, cavern);
                        }
                    }
                    yy = columnMask.nextSetBit(yy + 1);
                }
            }
        }

        private void addCrossChunkBoundaryWalls(CarveWallBuffer walls, CarveColumnMask[] boundaryMasks, int[] surfaceHeights) {
            int maxSurfaceY = 0;
            for (int index = 0; index < surfaceHeights.length; index++) {
                if (surfaceHeights[index] > maxSurfaceY) {
                    maxSurfaceY = surfaceHeights[index];
                }
            }
            int maxY = Math.min(world.height() - 1, maxSurfaceY + 1);
            if (maxY < 1) {
                return;
            }

            MantleChunk<Matter> west = mantle.loadedChunk(chunkX - 1, chunkZ);
            MantleChunk<Matter> east = mantle.loadedChunk(chunkX + 1, chunkZ);
            MantleChunk<Matter> north = mantle.loadedChunk(chunkX, chunkZ - 1);
            MantleChunk<Matter> south = mantle.loadedChunk(chunkX, chunkZ + 1);
            if (west == null && east == null && north == null && south == null) {
                return;
            }

            for (int yy = 1; yy <= maxY; yy++) {
                for (int offset = 0; offset < 16; offset++) {
                    if (west != null) {
                        tryAddBoundaryWall(west, walls, boundaryMasks, 0, yy, offset, 15, offset);
                    }
                    if (east != null) {
                        tryAddBoundaryWall(east, walls, boundaryMasks, 15, yy, offset, 0, offset);
                    }
                    if (north != null) {
                        tryAddBoundaryWall(north, walls, boundaryMasks, offset, yy, 0, offset, 15);
                    }
                    if (south != null) {
                        tryAddBoundaryWall(south, walls, boundaryMasks, offset, yy, 15, offset, 0);
                    }
                }
            }
        }

        private void tryAddBoundaryWall(MantleChunk<Matter> neighborChunk, CarveWallBuffer walls, CarveColumnMask[] boundaryMasks,
                                        int localX, int yy, int localZ, int neighborX, int neighborZ) {
            if (mantleChunk.get(localX, yy, localZ, MatterCavern.class) != null) {
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

        private void processColumnFromMask(CarveColumnMask columnMask, int columnIndex, Map<String, ResourceId> customBiomeCache) {
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
                if (y <= world.height()) {
                    if (y == buf + 1) {
                        buf = y;
                        zone.ceiling = buf;
                    } else {
                        if (zone.isValid(world.height())) {
                            processZone(zone, rx, rz, worldX, worldZ, customBiomeCache);
                        }
                        zone = new CaveZone();
                        zone.setFloor(y);
                        buf = y;
                    }
                }

                y = columnMask.nextSetBit(y + 1);
            }

            if (zone.isValid(world.height())) {
                processZone(zone, rx, rz, worldX, worldZ, customBiomeCache);
            }
        }

        private void processBoundaryColumnFromMask(CarveColumnMask boundaryMask, CarveWallBuffer walls, int columnIndex,
                                                   Map<String, ResourceId> customBiomeCache) {
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
                    paintBoundaryZone(walls, rx, rz, worldX, worldZ, zoneFloor, zoneCeiling, customBiomeCache);
                    zoneFloor = y;
                    zoneCeiling = y;
                }
                y = boundaryMask.nextSetBit(y + 1);
            }

            paintBoundaryZone(walls, rx, rz, worldX, worldZ, zoneFloor, zoneCeiling, customBiomeCache);
        }

        private void paintBoundaryZone(CarveWallBuffer walls, int rx, int rz, int worldX, int worldZ, int zoneFloor, int zoneCeiling,
                                       Map<String, ResourceId> customBiomeCache) {
            ResourceId floorBiome = resolveCaveBoundaryBiome(walls.get(rx, zoneFloor, rz), worldX, zoneFloor, worldZ, customBiomeCache);
            ResourceId ceilingBiome = resolveCaveBoundaryBiome(walls.get(rx, zoneCeiling, rz), worldX, zoneCeiling, worldZ, customBiomeCache);
            if (floorBiome == null && ceilingBiome == null) {
                return;
            }

            if (floorBiome != null) {
                paintFloorLayers(floorBiome, rx, rz, worldX, worldZ, zoneFloor);
            }

            if (ceilingBiome != null) {
                paintCeilingLayers(ceilingBiome, rx, rz, worldX, worldZ, zoneCeiling, world.height());
            }
        }

        /** Replaces the solid blocks under {@code floorY} with the layers of {@code floorBiome}. */
        private void paintFloorLayers(ResourceId floorBiome, int rx, int rz, int worldX, int worldZ, int floorY) {
            List<BlockState> floorLayers = dressing.floorLayers(floorBiome, worldX, worldZ, rng, LAYER_DEPTH, floorY);
            for (int i = 0; i < floorY - 1; i++) {
                if (i >= floorLayers.size()) {
                    break;
                }
                int y = floorY - i - 1;
                BlockState layer = floorLayers.get(i);
                BlockState existing = output.get(rx, y, rz);
                if (layer == null || !classes.isSolid(existing) || !canReplaceCaveFloorLayer(output, rx, y, rz, layer, classes)) {
                    continue;
                }
                output.set(rx, y, rz, classes.isOre(existing) ? dressing.oreFor(existing, layer) : layer);
            }
        }

        /** Replaces the solid blocks over {@code ceilingY} with the cave ceiling layers of {@code ceilingBiome}, below {@code maxY}. */
        private void paintCeilingLayers(ResourceId ceilingBiome, int rx, int rz, int worldX, int worldZ, int ceilingY, int maxY) {
            List<BlockState> ceilingLayers = dressing.ceilingLayers(ceilingBiome, worldX, worldZ, rng, LAYER_DEPTH);
            for (int i = 0; i < ceilingLayers.size(); i++) {
                int y = ceilingY + i + 1;
                if (y >= maxY) {
                    break;
                }
                BlockState layer = ceilingLayers.get(i);
                BlockState existing = output.get(rx, y, rz);
                if (layer == null || !classes.isSolid(existing)) {
                    continue;
                }
                output.set(rx, y, rz, classes.isOre(existing) ? dressing.oreFor(existing, layer) : layer);
            }
        }

        /**
         * 1-in-16 marker roll from a SplitMix64 finalizer over (carve seed, block position, salt).
         * Deterministic per seed and position, thread-order independent, allocation free.
         */
        private boolean markerRoll(int x, int y, int z, long salt) {
            long h = (world.carveSeed() + salt) ^ BlockPosition.toLong(x, y, z);
            h = (h ^ (h >>> 30)) * 0xBF58476D1CE4E5B9L;
            h = (h ^ (h >>> 27)) * 0x94D049BB133111EBL;
            h ^= h >>> 31;
            return (h & 15L) == 0L;
        }

        private void processZone(CaveZone zone, int rx, int rz, int xx, int zz, Map<String, ResourceId> customBiomeCache) {
            int maxY = output.height();

            if (zone.ceiling + 1 < maxY && classes.isDecorant(output.get(rx, zone.ceiling + 1, rz))) {
                output.set(rx, zone.ceiling + 1, rz, caveAir);
            }

            if (classes.isDecorant(output.get(rx, zone.ceiling, rz))) {
                output.set(rx, zone.ceiling, rz, caveAir);
            }

            // Seed-derived and position-keyed, never Math.random(): these markers persist into the
            // mantle and drive cave spawning, so the same seed must stamp the same markers on
            // every run and every platform. Inline mix, no allocation on this hot path.
            if (markerRoll(xx, zone.ceiling, zz, CEILING_MARKER_SALT)) {
                setMarker(rx, zone.ceiling, rz, MarkerMatter.CAVE_CEILING);
            }

            if (markerRoll(xx, zone.floor, zz, FLOOR_MARKER_SALT)) {
                setMarker(rx, zone.floor, rz, MarkerMatter.CAVE_FLOOR);
            }

            ResourceId floorBiome = resolveCaveBoundaryBiome(rx, zone.floor, rz, xx, zz, customBiomeCache);
            ResourceId ceilingBiome = resolveCaveBoundaryBiome(rx, zone.ceiling, rz, xx, zz, customBiomeCache);
            if (floorBiome == null && ceilingBiome == null) {
                return;
            }

            if (floorBiome != null) {
                paintFloorLayers(floorBiome, rx, rz, xx, zz, zone.floor);
            }

            if (ceilingBiome != null) {
                paintCeilingLayers(ceilingBiome, rx, rz, xx, zz, zone.ceiling, maxY);
            }

            if (floorBiome != null && dressing.hasDecorators(floorBiome, DecorationPart.NONE)
                    && hasStableCaveFloorSupport(output, rx, zone.getFloor(), rz, classes)) {
                dressing.decorateFloor(rx, rz, xx, zz, floorBiome, zone.getFloor() - 1, zone.airThickness());
            }

            if (ceilingBiome != null && dressing.hasDecorators(ceilingBiome, DecorationPart.CEILING) && zone.getCeiling() + 1 < maxY
                    && classes.isSolid(output.get(rx, zone.getCeiling() + 1, rz))) {
                dressing.decorateCeiling(rx, rz, xx, zz, ceilingBiome, zone.getCeiling(), zone.airThickness());
            }
        }

        private void setMarker(int localX, int y, int localZ, MatterMarker marker) {
            Matter section = mantleChunk.getOrCreate(y >> 4);
            section.<MatterMarker>slice(MatterMarker.class).set(localX, y & 15, localZ, marker);
        }

        ResourceId resolveCaveBoundaryBiome(int x, int y, int z, int worldX, int worldZ, Map<String, ResourceId> customBiomeCache) {
            return resolveCaveBoundaryBiome(cavernIfPresent(x, y, z), worldX, y, worldZ, customBiomeCache);
        }

        private MatterCavern cavernIfPresent(int x, int y, int z) {
            int section = y >> 4;
            if (y < 0 || !mantleChunk.exists(section)) {
                return null;
            }
            Matter matter = mantleChunk.get(section);
            if (matter == null || !matter.hasSlice(MatterCavern.class)) {
                return null;
            }
            return matter.<MatterCavern>getSlice(MatterCavern.class).get(x & 15, y & 15, z & 15);
        }

        ResourceId resolveCaveBoundaryBiome(MatterCavern cavern, int worldX, int y, int worldZ, Map<String, ResourceId> customBiomeCache) {
            if (cavern != null && !cavern.getCustomBiome().isEmpty()) {
                return resolveCustomBiome(customBiomeCache, cavern.getCustomBiome());
            }
            return resolveCaveBiome(worldX, y, worldZ);
        }

        private ResourceId resolveCaveBiome(int x, int y, int z) {
            ResourceId center = sampleCaveBiome(x, y, z);
            if (center == null) {
                return null;
            }

            ResourceId xPos = sampleCaveBiome(x + CAVE_BIOME_BLEND_RADIUS, y, z);
            ResourceId xNeg = sampleCaveBiome(x - CAVE_BIOME_BLEND_RADIUS, y, z);
            ResourceId zPos = sampleCaveBiome(x, y, z + CAVE_BIOME_BLEND_RADIUS);
            ResourceId zNeg = sampleCaveBiome(x, y, z - CAVE_BIOME_BLEND_RADIUS);

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

        private ResourceId sampleCaveBiome(int x, int y, int z) {
            long key = BlockPosition.toLong(x, y, z);
            ResourceId cachedBiome = caveBiomeCache.get(key);
            if (cachedBiome != null) {
                return cachedBiome;
            }

            ResourceId resolvedBiome = terrain.caveBiome(x, y, z).orElse(null);
            if (resolvedBiome != null) {
                caveBiomeCache.put(key, resolvedBiome);
            }
            return resolvedBiome;
        }

        /** The id of the pack's biome with this key; null when the pack has none or it cannot be loaded. */
        private ResourceId existingBiomeId(String key) {
            ResourceId id = resources.idOf(key);
            try {
                return resources.find(PackResources.BIOMES, id).isPresent() ? id : null;
            } catch (IllegalStateException unreadable) {
                return null;
            }
        }

        private ResourceId resolveCustomBiome(Map<String, ResourceId> customBiomeCache, String customBiome) {
            if (customBiomeCache.containsKey(customBiome)) {
                return customBiomeCache.get(customBiome);
            }

            ResourceId id = existingBiomeId(customBiome);
            customBiomeCache.put(customBiome, id);
            return id;
        }
    }

    @Data
    public static class CaveZone {
        private int ceiling = -1;
        private int floor = -1;

        public int airThickness() {
            return (ceiling - floor) - 1;
        }

        public boolean isValid(int height) {
            return floor < ceiling && ceiling - floor >= 1 && floor >= 0 && ceiling <= height && airThickness() > 0;
        }

        public String toString() {
            return floor + "-" + ceiling;
        }
    }
}
