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

package art.arcane.iris.world.pregen;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Keeps the chunks a pregen FULL request loads around itself resident until no later request of the
 * pregen area can need them. Without it the server unloads a chunk as soon as the in-flight requests
 * around it move on, and the next request that reaches it (the next lattice pass over the region, or the
 * neighbouring region's border) deserializes it from disk again.
 *
 * <p>A request needs every chunk within the dependency radius, so a retained chunk is needed by exactly
 * the in-area regions its own radius square overlaps. It is released once all of them have drained: a
 * drained region keeps only the border strips its undrained neighbours still reach. Retained positions
 * are capped; over the cap the strips of the drained regions farthest from the active region are released
 * first (they are needed last), and a released strip is simply reloaded by the request that reaches it, as
 * the pregen did before retention.
 */
final class PregenChunkRetention {
    private static final int REGION_WORDS = 16;

    private final Tickets tickets;
    private final int radius;
    private final int capacity;
    private final Map<Long, long[]> held = new HashMap<>();
    private final Set<Long> drained = new HashSet<>();
    private final int minRegionX;
    private final int minRegionZ;
    private final int maxRegionX;
    private final int maxRegionZ;
    private int size;

    PregenChunkRetention(Tickets tickets, int radius, int capacity,
                         int minRegionX, int minRegionZ, int maxRegionX, int maxRegionZ) {
        if (radius < 0 || radius > 31) {
            throw new IllegalArgumentException("Retention radius must fit inside one region: " + radius);
        }
        this.tickets = tickets;
        this.radius = radius;
        this.capacity = Math.max(1, capacity);
        this.minRegionX = minRegionX;
        this.minRegionZ = minRegionZ;
        this.maxRegionX = maxRegionX;
        this.maxRegionZ = maxRegionZ;
    }

    static long regionKey(int regionX, int regionZ) {
        return (((long) regionX) << 32) | (regionZ & 0xFFFFFFFFL);
    }

    /**
     * Retains the dependency square of a FULL request at the chunk. The request loads every position in it
     * anyway, so retaining loads nothing.
     */
    synchronized void retainAround(int chunkX, int chunkZ) {
        for (int x = chunkX - radius; x <= chunkX + radius; x++) {
            long[] bits = null;
            long bitsKey = 0L;
            for (int z = chunkZ - radius; z <= chunkZ + radius; z++) {
                long key = regionKey(x >> 5, z >> 5);
                if (bits == null || key != bitsKey) {
                    bitsKey = key;
                    bits = held.computeIfAbsent(key, ignored -> new long[REGION_WORDS]);
                }
                int index = ((z & 31) << 5) | (x & 31);
                long mask = 1L << (index & 63);
                if ((bits[index >> 6] & mask) != 0L) {
                    continue;
                }
                bits[index >> 6] |= mask;
                size++;
                tickets.retain(x, z);
            }
        }

        if (size > capacity) {
            releaseFarthestDrained(chunkX >> 5, chunkZ >> 5);
        }
    }

    /**
     * Records a drained region and releases every retained position no undrained in-area region reaches.
     */
    synchronized void drained(int regionX, int regionZ) {
        drained.add(regionKey(regionX, regionZ));
        for (int x = regionX - 1; x <= regionX + 1; x++) {
            for (int z = regionZ - 1; z <= regionZ + 1; z++) {
                long key = regionKey(x, z);
                long[] bits = held.get(key);
                if (bits != null && release(key, bits, true)) {
                    held.remove(key);
                }
            }
        }
    }

    synchronized void releaseAll() {
        for (Map.Entry<Long, long[]> entry : held.entrySet()) {
            release(entry.getKey(), entry.getValue(), false);
        }
        held.clear();
    }

    synchronized int size() {
        return size;
    }

    private void releaseFarthestDrained(int regionX, int regionZ) {
        List<Long> candidates = new ArrayList<>();
        for (Long key : held.keySet()) {
            if (drained.contains(key)) {
                candidates.add(key);
            }
        }
        candidates.sort(Comparator.comparingLong((Long key) -> {
            long dx = (int) (key >> 32) - regionX;
            long dz = key.intValue() - regionZ;
            return dx * dx + dz * dz;
        }).reversed());
        int target = capacity - capacity / 8;
        for (Long key : candidates) {
            if (size <= target) {
                return;
            }
            release(key, held.remove(key), false);
        }
    }

    private boolean release(long key, long[] bits, boolean onlyUnreachable) {
        int baseX = ((int) (key >> 32)) << 5;
        int baseZ = ((int) key) << 5;
        boolean empty = true;
        for (int word = 0; word < REGION_WORDS; word++) {
            long remaining = bits[word];
            while (remaining != 0L) {
                int bit = Long.numberOfTrailingZeros(remaining);
                remaining &= remaining - 1L;
                int index = (word << 6) | bit;
                int x = baseX + (index & 31);
                int z = baseZ + (index >> 5);
                if (onlyUnreachable && reachedByUndrainedRegion(x, z)) {
                    empty = false;
                    continue;
                }
                bits[word] &= ~(1L << bit);
                size--;
                tickets.release(x, z);
            }
        }
        return empty;
    }

    private boolean reachedByUndrainedRegion(int chunkX, int chunkZ) {
        for (int regionX = (chunkX - radius) >> 5; regionX <= (chunkX + radius) >> 5; regionX++) {
            for (int regionZ = (chunkZ - radius) >> 5; regionZ <= (chunkZ + radius) >> 5; regionZ++) {
                if (regionX >= minRegionX && regionX <= maxRegionX && regionZ >= minRegionZ && regionZ <= maxRegionZ
                        && !drained.contains(regionKey(regionX, regionZ))) {
                    return true;
                }
            }
        }
        return false;
    }

    interface Tickets {
        void retain(int chunkX, int chunkZ);

        void release(int chunkX, int chunkZ);
    }
}
