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

package art.arcane.iris.generation.decoration.ruin;

import art.arcane.iris.pack.loading.IrisData;
import art.arcane.iris.generation.decoration.IrisProceduralBlocks;
import art.arcane.iris.spi.PlatformBlockState;
import art.arcane.volmlib.util.math.Vector3i;
import art.arcane.volmlib.util.math.RNG;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

final class RuinDecoratorApplier {
    private static final int[][] HORIZONTAL = {{1, 0, 0}, {-1, 0, 0}, {0, 0, 1}, {0, 0, -1}};

    private RuinDecoratorApplier() {
    }

    static void apply(RuinBlockCanvas canvas, IrisRuinDecorator decorator, IrisData data, long seed, RNG rng) {
        List<Vector3i> candidates = switch (decorator.getTarget()) {
            case TOP -> topCandidates(canvas);
            case SURFACE -> surfaceCandidates(canvas);
            case BASE_SCATTER -> baseScatterCandidates(canvas, Math.max(1, decorator.getScatterRadius()));
        };

        RNG paletteRng = new RNG(seed);
        for (Vector3i v : candidates) {
            if (!rng.chance(decorator.getChance())) {
                continue;
            }
            PlatformBlockState bd = IrisProceduralBlocks.resolve(decorator.getBlock(), decorator.getPalette(), data, v.getBlockX(), v.getBlockY(), v.getBlockZ(), paletteRng);
            if (bd != null) {
                canvas.accent(v.getBlockX(), v.getBlockY(), v.getBlockZ(), bd);
            }
        }
    }

    private static List<Vector3i> topCandidates(RuinBlockCanvas canvas) {
        Map<Long, Integer> highest = new HashMap<>();
        for (Vector3i v : canvas.cells().keySet()) {
            long key = columnKey(v.getBlockX(), v.getBlockZ());
            Integer current = highest.get(key);
            if (current == null || v.getBlockY() > current) {
                highest.put(key, v.getBlockY());
            }
        }
        List<Vector3i> out = new ArrayList<>();
        for (Map.Entry<Long, Integer> entry : highest.entrySet()) {
            int x = (int) (entry.getKey() >> 32);
            int z = (int) (entry.getKey() & 0xFFFFFFFFL);
            int y = entry.getValue() + 1;
            if (!canvas.has(x, y, z)) {
                out.add(new Vector3i(x, y, z));
            }
        }
        return out;
    }

    private static List<Vector3i> surfaceCandidates(RuinBlockCanvas canvas) {
        List<Vector3i> out = new ArrayList<>();
        for (Vector3i v : new ArrayList<>(canvas.cells().keySet())) {
            for (int[] dir : HORIZONTAL) {
                int nx = v.getBlockX() + dir[0];
                int ny = v.getBlockY();
                int nz = v.getBlockZ() + dir[2];
                if (!canvas.has(nx, ny, nz)) {
                    out.add(new Vector3i(nx, ny, nz));
                }
            }
        }
        return out;
    }

    private static List<Vector3i> baseScatterCandidates(RuinBlockCanvas canvas, int radius) {
        int minX = Integer.MAX_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxZ = Integer.MIN_VALUE;
        int minY = Integer.MAX_VALUE;
        for (Vector3i v : canvas.cells().keySet()) {
            minX = Math.min(minX, v.getBlockX());
            minZ = Math.min(minZ, v.getBlockZ());
            maxX = Math.max(maxX, v.getBlockX());
            maxZ = Math.max(maxZ, v.getBlockZ());
            minY = Math.min(minY, v.getBlockY());
        }

        List<Vector3i> out = new ArrayList<>();
        for (int x = minX - radius; x <= maxX + radius; x++) {
            for (int z = minZ - radius; z <= maxZ + radius; z++) {
                if (!canvas.has(x, minY, z) && !canvas.has(x, minY + 1, z)) {
                    out.add(new Vector3i(x, minY, z));
                }
            }
        }
        return out;
    }

    private static long columnKey(int x, int z) {
        return ((long) x << 32) | (z & 0xFFFFFFFFL);
    }
}
