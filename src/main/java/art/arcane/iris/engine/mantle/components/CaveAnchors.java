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

package art.arcane.iris.engine.mantle.components;

import art.arcane.iris.builtin.carve.CaveAnchorMode;
import art.arcane.iris.builtin.objects.CarvedSpace;
import art.arcane.iris.builtin.objects.Cavern;
import art.arcane.volmlib.util.cache.CacheKey;
import art.arcane.volmlib.util.math.RNG;
import com.volmit.resource.ResourceId;
import com.volmit.world.BiomeSample;
import com.volmit.world.TerrainSampler;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Finds the places in the carved caves of a chunk where a cave object may be anchored, remembering what it scanned. */
final class CaveAnchors {
    private static final int BEDROCK_CLEARANCE = 6;
    private static final int SOLID_NEIGHBOR_REACH_BLOCKS = 3;
    private static final int MAX_PACKED_SETTING = 65535;

    private final CarvedSpace carved;
    private final TerrainSampler terrain;
    private final int worldHeight;
    private final int caveLavaHeight;
    private final Map<Long, Map<Long, List<Integer>>> anchorsBySetting = new HashMap<>();
    private final Map<Long, byte[]> carvedColumns = new HashMap<>();
    private final Map<Long, Integer> surfaceHeights = new HashMap<>();

    CaveAnchors(CarvedSpace carved, TerrainSampler terrain, int worldHeight, int caveLavaHeight) {
        this.carved = carved;
        this.terrain = terrain;
        this.worldHeight = worldHeight;
        this.caveLavaHeight = caveLavaHeight;
    }

    /** A position to anchor a cave object at. */
    record Anchor(int x, int y, int z) {
    }

    /** How a cave placement looks for its anchor. */
    record Search(CaveAnchorMode mode, int scanStep, int minDepthBelowSurface, int attempts) {
    }

    /**
     * Looks for an anchor in the chunk whose first block is {@code minX}, {@code minZ}: a column picked at random, a height in
     * it picked among those the mode accepts, tried up to the search's attempts.
     *
     * @param expectedCaveBiome the cave biome the anchor has to be in, or null for any
     * @return the anchor, or null when no attempt found one
     */
    Anchor find(RNG rng, int minX, int minZ, Search search, ResourceId expectedCaveBiome, boolean underwater) {
        for (int attempt = 0; attempt < search.attempts(); attempt++) {
            int candidateX = rng.i(minX, minX + 16);
            int candidateZ = rng.i(minZ, minZ + 16);
            int candidateY = anchorY(rng, candidateX, candidateZ, search);
            if (candidateY < 0
                    || biomeConflicts(candidateX, candidateY, candidateZ, expectedCaveBiome)
                    || !acceptsFluid(underwater, carved.cavernAt(candidateX, candidateY, candidateZ), candidateY, caveLavaHeight)) {
                continue;
            }
            return new Anchor(candidateX, candidateY, candidateZ);
        }
        return null;
    }

    static boolean acceptsFluid(boolean underwater, Optional<Cavern> cavern, int y, int lavaHeight) {
        if (cavern.isEmpty()) {
            return false;
        }
        if (underwater || cavern.get().liquid() == Cavern.FORCED_AIR_LIQUID) {
            return true;
        }
        return cavern.get().air() && y > lavaHeight;
    }

    /** One above the highest height an anchor may sit at, or 0 when none may. */
    static int scanUpperBound(int worldHeight, int surfaceY, int minDepthBelowSurface) {
        int maxAnchorY = maxBuriedY(worldHeight, surfaceY, minDepthBelowSurface);
        if (maxAnchorY <= 1) {
            return 0;
        }
        return maxAnchorY + 1;
    }

    /** The highest height a cave object may reach: no deeper than {@code minDepthBelowSurface} under the surface. */
    static int maxBuriedY(int worldHeight, int surfaceY, int minDepthBelowSurface) {
        return Math.min(worldHeight - 1, surfaceY - Math.max(0, minDepthBelowSurface));
    }

    /** Whether the cave biome at the position is not the one expected, nor the surface biome the cave sits under. */
    private boolean biomeConflicts(int x, int y, int z, ResourceId expectedCaveBiome) {
        if (expectedCaveBiome == null) {
            return false;
        }
        BiomeSample sample = terrain.biome(x, y, z);
        return biomeConflicts(sample.caveBiome().orElse(null), sample.surfaceBiome(), expectedCaveBiome);
    }

    static boolean biomeConflicts(ResourceId at, ResourceId surface, ResourceId expectedCaveBiome) {
        if (expectedCaveBiome == null || at == null) {
            return false;
        }
        if (at.equals(expectedCaveBiome)) {
            return false;
        }
        return surface == null || !at.equals(surface);
    }

    private int anchorY(RNG rng, int x, int z, Search search) {
        List<Integer> anchors = anchorsAt(x, z, search);
        if (anchors.isEmpty()) {
            return -1;
        }
        if (anchors.size() == 1) {
            return anchors.get(0);
        }
        return anchors.get(rng.i(anchors.size()));
    }

    private List<Integer> anchorsAt(int x, int z, Search search) {
        if (search.scanStep() > MAX_PACKED_SETTING || search.minDepthBelowSurface() > MAX_PACKED_SETTING) {
            return scanColumn(x, z, search);
        }
        long settingKey = ((long) search.mode().ordinal() << 32) | ((long) search.scanStep() << 16) | search.minDepthBelowSurface();
        Map<Long, List<Integer>> columns = anchorsBySetting.computeIfAbsent(settingKey, key -> new HashMap<>());
        long columnKey = CacheKey.key(x, z);
        List<Integer> anchors = columns.get(columnKey);
        if (anchors != null) {
            return anchors;
        }
        anchors = scanColumn(x, z, search);
        columns.put(columnKey, anchors);
        return anchors;
    }

    private List<Integer> scanColumn(int x, int z, Search search) {
        int step = Math.max(1, search.scanStep());
        int surfaceY = surfaceHeight(x, z);
        int maxAnchorExclusive = scanUpperBound(worldHeight, surfaceY, search.minDepthBelowSurface());
        if (maxAnchorExclusive == 0) {
            return new ArrayList<>();
        }
        int carvedHeight = Math.min(worldHeight, maxAnchorExclusive + 3);
        byte[] carvedColumn = carvedColumn(x, z, carvedHeight);
        return scanRange(search.mode(), step, carvedHeight, maxAnchorExclusive, carvedColumn);
    }

    private List<Integer> scanRange(CaveAnchorMode mode, int step, int height, int maxAnchorY, byte[] carvedColumn) {
        List<Integer> anchors = new ArrayList<>();
        for (int y = BEDROCK_CLEARANCE; y < maxAnchorY; y += step) {
            if (!isCarved(carvedColumn, y)) {
                continue;
            }
            boolean solidBelow = hasSolidNeighbor(carvedColumn, y, height, -1);
            boolean solidAbove = hasSolidNeighbor(carvedColumn, y, height, 1);
            if (matches(mode, solidBelow, solidAbove)) {
                anchors.add(y);
            }
        }
        return anchors;
    }

    private static boolean isCarved(byte[] carvedColumn, int y) {
        return y >= 0 && y < carvedColumn.length && carvedColumn[y] == 1;
    }

    private static boolean hasSolidNeighbor(byte[] carvedColumn, int y, int height, int direction) {
        for (int distance = 1; distance <= SOLID_NEIGHBOR_REACH_BLOCKS; distance++) {
            int neighbor = y + (direction * distance);
            if (neighbor < 0 || neighbor >= height || !isCarved(carvedColumn, neighbor)) {
                return true;
            }
        }
        return false;
    }

    private static boolean matches(CaveAnchorMode mode, boolean solidBelow, boolean solidAbove) {
        return switch (mode) {
            case PROFILE_DEFAULT, FLOOR -> solidBelow;
            case CEILING -> solidAbove;
            case CENTER -> !solidBelow && !solidAbove;
            case ANY -> true;
        };
    }

    private byte[] carvedColumn(int x, int z, int height) {
        long columnKey = CacheKey.key(x, z);
        byte[] column = carvedColumns.get(columnKey);
        if (column != null && column.length == height) {
            return column;
        }
        column = carved.column(x, z, height);
        carvedColumns.put(columnKey, column);
        return column;
    }

    private int surfaceHeight(int x, int z) {
        long columnKey = CacheKey.key(x, z);
        Integer known = surfaceHeights.get(columnKey);
        if (known != null) {
            return known;
        }
        int surfaceY = terrain.surfaceHeight(x, z);
        surfaceHeights.put(columnKey, surfaceY);
        return surfaceY;
    }
}
