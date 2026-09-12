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

package art.arcane.iris.structure.object;

import art.arcane.iris.structure.object.IrisObject;

import java.util.HashMap;
import java.util.Map;

public class FloatingObjectFootprint {
    private final int lowestSolidKeyY;
    private final int highestSolidKeyY;
    private final int centerX;
    private final int centerY;
    private final int centerZ;
    private final int tallestKx;
    private final int tallestKz;
    private final int tallestKxBottom;
    private final int tallestKzBottom;
    private final long[] footprintXZ;

    private FloatingObjectFootprint(int lowestSolidKeyY, int highestSolidKeyY, int centerX, int centerY, int centerZ, int tallestKx, int tallestKz, int tallestKxBottom, int tallestKzBottom, long[] footprintXZ) {
        this.lowestSolidKeyY = lowestSolidKeyY;
        this.highestSolidKeyY = highestSolidKeyY;
        this.centerX = centerX;
        this.centerY = centerY;
        this.centerZ = centerZ;
        this.tallestKx = tallestKx;
        this.tallestKz = tallestKz;
        this.tallestKxBottom = tallestKxBottom;
        this.tallestKzBottom = tallestKzBottom;
        this.footprintXZ = footprintXZ;
    }

    /**
     * Memoized per object instance, never in a global map: loadKey + declared dimensions is
     * not unique across packs or across hotloads (a block edit keeps the header W/H/D), so a
     * shared key space served stale or foreign footprints. A reload builds a new IrisObject,
     * which drops the memo for free.
     */
    public static FloatingObjectFootprint compute(IrisObject obj) {
        return obj.floatingFootprint.aquire(() -> doCompute(obj));
    }

    private static FloatingObjectFootprint doCompute(IrisObject obj) {
        int cx = obj.getCenter().getBlockX();
        int cy = obj.getCenter().getBlockY();
        int cz = obj.getCenter().getBlockZ();
        Map<Long, int[]> columnStats = new HashMap<>();

        int[] globalHighestY = {Integer.MIN_VALUE};
        int[] globalHighestKx = {0};
        int[] globalHighestKz = {0};

        obj.getBlocks().forEach((key, bd) -> {
            if (!bd.isSolid()) {
                return;
            }
            int kx = key.getBlockX();
            int ky = key.getBlockY();
            int kz = key.getBlockZ();
            long packed = ((long) kx << 32) | (kz & 0xFFFFFFFFL);
            int[] stats = columnStats.get(packed);
            if (stats == null) {
                stats = new int[]{ky, 1};
                columnStats.put(packed, stats);
            } else {
                if (ky < stats[0]) {
                    stats[0] = ky;
                }
                stats[1]++;
            }
            if (ky > globalHighestY[0]) {
                globalHighestY[0] = ky;
                globalHighestKx[0] = kx;
                globalHighestKz[0] = kz;
            }
        });

        long[] footprintArray = new long[columnStats.size()];
        int idx = 0;
        for (Long packed : columnStats.keySet()) {
            footprintArray[idx++] = packed;
        }

        long tallestPacked = resolveTallestColumn(columnStats);
        int lowestSolidKeyY = columnStats.isEmpty() ? cy : columnStats.get(tallestPacked)[0];
        int highestSolidKeyY = columnStats.isEmpty() ? cy : globalHighestY[0];
        int tallestKx = columnStats.isEmpty() ? 0 : (int) (tallestPacked >> 32);
        int tallestKz = columnStats.isEmpty() ? 0 : (int) (tallestPacked & 0xFFFFFFFFL);
        int tallestKxBottom = columnStats.isEmpty() ? 0 : globalHighestKx[0];
        int tallestKzBottom = columnStats.isEmpty() ? 0 : globalHighestKz[0];
        return new FloatingObjectFootprint(lowestSolidKeyY, highestSolidKeyY, cx, cy, cz, tallestKx, tallestKz, tallestKxBottom, tallestKzBottom, footprintArray);
    }

    private static long resolveTallestColumn(Map<Long, int[]> columnStats) {
        long bestPacked = 0L;
        int tallestCount = 0;
        int tallestLow = Integer.MAX_VALUE;
        for (Map.Entry<Long, int[]> e : columnStats.entrySet()) {
            int[] stats = e.getValue();
            int count = stats[1];
            int low = stats[0];
            if (count > tallestCount || (count == tallestCount && low < tallestLow)) {
                tallestCount = count;
                tallestLow = low;
                bestPacked = e.getKey();
            }
        }
        return bestPacked;
    }

    public int lowestSolidRelCenterY() {
        return lowestSolidKeyY - centerY;
    }

    public int getLowestSolidKeyY() {
        return lowestSolidKeyY;
    }

    public int getCenterX() {
        return centerX;
    }

    public int getCenterY() {
        return centerY;
    }

    public int getCenterZ() {
        return centerZ;
    }

    public int getTallestKx() {
        return tallestKx;
    }

    public int getTallestKz() {
        return tallestKz;
    }

    public int getHighestSolidKeyY() {
        return highestSolidKeyY;
    }

    public int getTallestKxBottom() {
        return tallestKxBottom;
    }

    public int getTallestKzBottom() {
        return tallestKzBottom;
    }

    public long[] footprintXZ() {
        return footprintXZ;
    }
}
