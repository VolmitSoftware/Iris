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

import art.arcane.iris.builtin.decorant.DecorationPart;
import art.arcane.volmlib.util.math.BlockPosition;
import art.arcane.volmlib.util.math.PowerOfTwoCoordinates;
import art.arcane.volmlib.util.math.RNG;
import art.arcane.iris.engine.mantle.components.CarveWorld;
import com.volmit.block.BlockRegistry;
import com.volmit.block.BlockState;
import com.volmit.geometry.BlockBox;
import com.volmit.hunk.Hunk;
import com.volmit.mantle.CarvedVoid;
import com.volmit.mantle.MantleLayer;
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
    private static final ThreadLocal<IrisCarveScratch> SCRATCH = ThreadLocal.withInitial(IrisCarveScratch::new);
    private static final int CAVE_BIOME_BLEND_RADIUS = 3;
    private static final int CAVE_BIOME_BLEND_CENTER_WEIGHT = 4;
    private static final int CAVE_BIOME_BLEND_TOTAL_WEIGHT = 8;
    private static final int CAVE_BIOME_CACHE_SIZE = 2048;
    private static final int CHUNK_SIZE = 16;
    private static final int CHUNK_AREA = 256;
    private static final int LAYER_DEPTH = 3;

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
     * {@code voids}, which reads one block beyond the chunk to wall the caves that cross its border, {@code terrain} gives the
     * heights and cave biomes around the chunk and {@code dressing} what its caves' walls, floors and ceilings get.
     */
    public void apply(MantleLayer<CarvedVoid> voids, TerrainSampler terrain, int x, int z, Hunk<BlockState> output, CaveDressing dressing) {
        new Application(voids, terrain, x, z, output, dressing).run();
    }

    static boolean hasExplicitCarveIntent(CarvedVoid carvedVoid) {
        return carvedVoid != null && carvedVoid.fill() != CarvedVoid.Fill.OPEN;
    }

    static boolean isFluidIntent(CarvedVoid carvedVoid) {
        return carvedVoid != null && carvedVoid.fill() == CarvedVoid.Fill.FLUID;
    }

    static boolean shouldPreserveExistingFluid(CarvedVoid carvedVoid, boolean existingIsFluid) {
        return existingIsFluid && !hasExplicitCarveIntent(carvedVoid);
    }

    static boolean usesDefaultLava(int caveLavaHeight, int y) {
        return y <= caveLavaHeight;
    }

    static boolean isUnsupportedSurfaceOre(boolean surfaceIsOre, boolean belowIsSolid) {
        return surfaceIsOre && !belowIsSolid;
    }

    static BlockState resolveExplicitCarveState(CarvedVoid carvedVoid, BlockState fluid, BlockState lava, BlockState air) {
        if (carvedVoid == null) {
            return null;
        }
        return switch (carvedVoid.fill()) {
            case FLUID -> fluid;
            case LAVA -> lava;
            case AIR -> air;
            case OPEN -> null;
        };
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
        private final MantleLayer<CarvedVoid> voids;
        private final TerrainSampler terrain;
        private final int chunkX;
        private final int chunkZ;
        private final int chunkBlockX;
        private final int chunkBlockZ;
        private final Hunk<BlockState> output;
        private final CaveDressing dressing;
        private final CaveBlockClasses classes = new CaveBlockClasses(blocks);
        private final CaveBiomeCache caveBiomeCache = new CaveBiomeCache(CAVE_BIOME_CACHE_SIZE);
        private final IrisCarveScratch scratch = SCRATCH.get();

        Application(MantleLayer<CarvedVoid> voids, TerrainSampler terrain, int chunkX, int chunkZ, Hunk<BlockState> output,
                    CaveDressing dressing) {
            this.voids = voids;
            this.terrain = terrain;
            this.chunkX = chunkX;
            this.chunkZ = chunkZ;
            this.chunkBlockX = PowerOfTwoCoordinates.chunkToBlock(chunkX);
            this.chunkBlockZ = PowerOfTwoCoordinates.chunkToBlock(chunkZ);
            this.output = output;
            this.dressing = dressing;
        }

        void run() {
            scratch.reset();
            CarveWallBuffer walls = scratch.walls;
            CarveColumnMask[] columnMasks = scratch.columnMasks;
            CarveColumnMask[] boundaryMasks = scratch.boundaryMasks;
            int[] surfaceHeights = scratch.surfaceHeights;
            Map<ResourceId, ResourceId> customBiomeCache = scratch.customBiomeCache;
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
            BlockBox ownColumns = new BlockBox(chunkBlockX, 0, chunkBlockZ, chunkBlockX + CHUNK_SIZE, worldHeightSpan, chunkBlockZ + CHUNK_SIZE);
            voids.visit(ownColumns, (xx, yy, zz, carvedVoid) -> {
                if (yy <= 0) {
                    return;
                }

                int rx = xx - chunkBlockX;
                int rz = zz - chunkBlockZ;
                int columnIndex = PowerOfTwoCoordinates.packLocal16(rx, rz);

                if (upperSurfaceHeights != null && yy >= upperSurfaceHeights[columnIndex]) {
                    return;
                }

                BlockState current = output.get(rx, yy, rz);
                boolean explicitCarveIntent = hasExplicitCarveIntent(carvedVoid);

                if (shouldPreserveExistingFluid(carvedVoid, classes.isFluid(current))) {
                    return;
                }

                columnMasks[columnIndex].add(yy);

                if (carvedVoid.biome().isPresent()) {
                    scratch.customCaveBiomePresent = true;
                }

                if (classes.isAir(current) && !explicitCarveIntent) {
                    return;
                }

                if (explicitCarveIntent) {
                    // Only a fluid cavern consumes the fluid sample, so never take it per voxel.
                    BlockState fluid = isFluidIntent(carvedVoid) ? dressing.fluidAt(chunkBlockX + rx, chunkBlockZ + rz) : null;
                    BlockState carved = resolveExplicitCarveState(carvedVoid, fluid, lava, caveAir);
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

            walls.forEach((rx, yy, rz, carvedVoid) -> {
                int worldX = rx + chunkBlockX;
                int worldZ = rz + chunkBlockZ;
                ResourceId biome = carvedVoid.biome().isEmpty()
                        ? resolveCaveBiome(worldX, yy, worldZ)
                        : resolveCustomBiome(customBiomeCache, carvedVoid.biome().get());

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
                        walls.put(rx, yy, rz + 1, CarvedVoid.OPEN);
                    }
                    if (rx < 15 && !columnMasks[columnIndex + 16].contains(yy)) {
                        walls.put(rx + 1, yy, rz, CarvedVoid.OPEN);
                    }
                    if (rz > 0 && !columnMasks[columnIndex - 1].contains(yy)) {
                        walls.put(rx, yy, rz - 1, CarvedVoid.OPEN);
                    }
                    if (rx > 0 && !columnMasks[columnIndex - 16].contains(yy)) {
                        walls.put(rx - 1, yy, rz, CarvedVoid.OPEN);
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
                    CarvedVoid carvedVoid = ownVoid(rx, yy, rz);
                    if (carvedVoid != null) {
                        if (rz < 15 && ownVoid(rx, yy, rz + 1) == null) {
                            walls.put(rx, yy, rz + 1, carvedVoid);
                        }
                        if (rx < 15 && ownVoid(rx + 1, yy, rz) == null) {
                            walls.put(rx + 1, yy, rz, carvedVoid);
                        }
                        if (rz > 0 && ownVoid(rx, yy, rz - 1) == null) {
                            walls.put(rx, yy, rz - 1, carvedVoid);
                        }
                        if (rx > 0 && ownVoid(rx - 1, yy, rz) == null) {
                            walls.put(rx - 1, yy, rz, carvedVoid);
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

            int westBlockX = chunkBlockX - 1;
            int eastBlockX = chunkBlockX + CHUNK_SIZE;
            int northBlockZ = chunkBlockZ - 1;
            int southBlockZ = chunkBlockZ + CHUNK_SIZE;
            for (int yy = 1; yy <= maxY; yy++) {
                for (int offset = 0; offset < CHUNK_SIZE; offset++) {
                    tryAddBoundaryWall(walls, boundaryMasks, 0, yy, offset, westBlockX, chunkBlockZ + offset);
                    tryAddBoundaryWall(walls, boundaryMasks, CHUNK_SIZE - 1, yy, offset, eastBlockX, chunkBlockZ + offset);
                    tryAddBoundaryWall(walls, boundaryMasks, offset, yy, 0, chunkBlockX + offset, northBlockZ);
                    tryAddBoundaryWall(walls, boundaryMasks, offset, yy, CHUNK_SIZE - 1, chunkBlockX + offset, southBlockZ);
                }
            }
        }

        private void tryAddBoundaryWall(CarveWallBuffer walls, CarveColumnMask[] boundaryMasks,
                                        int localX, int yy, int localZ, int neighborBlockX, int neighborBlockZ) {
            if (ownVoid(localX, yy, localZ) != null) {
                return;
            }

            CarvedVoid neighbor = voids.get(neighborBlockX, yy, neighborBlockZ);
            if (neighbor == null) {
                return;
            }

            walls.put(localX, yy, localZ, neighbor);
            int columnIndex = PowerOfTwoCoordinates.packLocal16(localX, localZ);
            boundaryMasks[columnIndex].add(yy);
        }

        private void processColumnFromMask(CarveColumnMask columnMask, int columnIndex, Map<ResourceId, ResourceId> customBiomeCache) {
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
                                                   Map<ResourceId, ResourceId> customBiomeCache) {
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
                                       Map<ResourceId, ResourceId> customBiomeCache) {
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

        private void processZone(CaveZone zone, int rx, int rz, int xx, int zz, Map<ResourceId, ResourceId> customBiomeCache) {
            int maxY = output.height();

            if (zone.ceiling + 1 < maxY && classes.isDecorant(output.get(rx, zone.ceiling + 1, rz))) {
                output.set(rx, zone.ceiling + 1, rz, caveAir);
            }

            if (classes.isDecorant(output.get(rx, zone.ceiling, rz))) {
                output.set(rx, zone.ceiling, rz, caveAir);
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

        ResourceId resolveCaveBoundaryBiome(int x, int y, int z, int worldX, int worldZ, Map<ResourceId, ResourceId> customBiomeCache) {
            return resolveCaveBoundaryBiome(ownVoid(x, y, z), worldX, y, worldZ, customBiomeCache);
        }

        /** The void at a block of this chunk; null where nothing is carved, and above or below the dimension. */
        private CarvedVoid ownVoid(int localX, int y, int localZ) {
            if (y < 0 || y >= world.height()) {
                return null;
            }
            return voids.get(chunkBlockX + localX, y, chunkBlockZ + localZ);
        }

        ResourceId resolveCaveBoundaryBiome(CarvedVoid carvedVoid, int worldX, int y, int worldZ, Map<ResourceId, ResourceId> customBiomeCache) {
            if (carvedVoid != null && carvedVoid.biome().isPresent()) {
                return resolveCustomBiome(customBiomeCache, carvedVoid.biome().get());
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

        /** The pack's biome with this id; null when the pack has none or it cannot be loaded. */
        private ResourceId existingBiomeId(ResourceId id) {
            try {
                return resources.find(PackResources.BIOMES, id).isPresent() ? id : null;
            } catch (IllegalStateException unreadable) {
                return null;
            }
        }

        private ResourceId resolveCustomBiome(Map<ResourceId, ResourceId> customBiomeCache, ResourceId customBiome) {
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
