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

package art.arcane.iris.engine.object;

import art.arcane.iris.spi.IrisLogging;
import art.arcane.iris.spi.PlatformBlockState;
import art.arcane.iris.util.common.data.VectorMap;
import art.arcane.iris.util.common.math.IrisBlockVector;
import art.arcane.iris.util.common.math.Vector3i;
import art.arcane.iris.util.common.parallel.BurstExecutor;
import art.arcane.iris.util.common.parallel.MultiBurst;
import art.arcane.volmlib.util.format.Form;
import art.arcane.volmlib.util.scheduling.PrecisionStopwatch;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Volume shaping for {@link IrisObject}: smart boring, shrinkwrapping, compaction and the shared block
 * classification helpers used by placement.
 */
final class IrisObjectShaping {
    private IrisObjectShaping() {
    }

    static void ensureSmartBored(IrisObject self) {
        if (self.smartBored) {
            return;
        }

        // Whole computation under the object's write lock: two racing callers would otherwise
        // both compute, with the loser reading self.blocks while the winner merges into it.
        self.writeLock.lock();
        try {
            if (self.smartBored) {
                return;
            }
            ensureSmartBoredLocked(self);
        } finally {
            self.writeLock.unlock();
        }
    }

    private static void ensureSmartBoredLocked(IrisObject self) {
        PrecisionStopwatch p = PrecisionStopwatch.start();
        PlatformBlockState vair = IrisObject.States.VAIR;
        AtomicInteger applied = new AtomicInteger();
        IrisBlockVector max = new IrisBlockVector(Double.MIN_VALUE, Double.MIN_VALUE, Double.MIN_VALUE);
        IrisBlockVector min = new IrisBlockVector(Double.MAX_VALUE, Double.MAX_VALUE, Double.MAX_VALUE);
        VectorMap<PlatformBlockState> source;
        self.readLock.lock();
        try {
            if (self.blocks.isEmpty()) {
                IrisLogging.warn("Cannot Smart Bore " + self.getLoadKey() + " because it has 0 blocks in it.");
                self.smartBored = true;
                return;
            }

            source = self.blocks;

            for (IrisBlockVector i : self.blocks.keys()) {
                max.setX(Math.max(i.getX(), max.getX()));
                min.setX(Math.min(i.getX(), min.getX()));
                max.setY(Math.max(i.getY(), max.getY()));
                min.setY(Math.min(i.getY(), min.getY()));
                max.setZ(Math.max(i.getZ(), max.getZ()));
                min.setZ(Math.min(i.getZ(), min.getZ()));
            }
        } finally {
            self.readLock.unlock();
        }

        VectorMap<PlatformBlockState> bore = new VectorMap<>();
        // Inline on purpose: the three axis passes read AND write the shared bore map, so the
        // bored volume must come from a fixed X->Y->Z order on the calling (lock-owning)
        // thread, not from pool scheduling. This matches what burst-worker callers (studio,
        // generation threads) already produced, so output is unchanged where it was stable.
        BurstExecutor burst = MultiBurst.burst.burst();
        burst.setMulticore(false);

        // Smash X
        for (int rayY = min.getBlockY(); rayY <= max.getBlockY(); rayY++) {
            int finalRayY = rayY;
            burst.queue(() -> {
                for (int rayZ = min.getBlockZ(); rayZ <= max.getBlockZ(); rayZ++) {
                    int start = Integer.MAX_VALUE;
                    int end = Integer.MIN_VALUE;

                    for (int ray = min.getBlockX(); ray <= max.getBlockX(); ray++) {
                        if (boreContains(source, bore, new IrisBlockVector(ray, finalRayY, rayZ))) {
                            start = Math.min(ray, start);
                            end = Math.max(ray, end);
                        }
                    }

                    if (start != Integer.MAX_VALUE && end != Integer.MIN_VALUE) {
                        for (int i = start; i <= end; i++) {
                            boreCell(source, bore, new IrisBlockVector(i, finalRayY, rayZ), vair, applied);
                        }
                    }
                }
            });
        }

        // Smash Y
        for (int rayX = min.getBlockX(); rayX <= max.getBlockX(); rayX++) {
            int finalRayX = rayX;
            burst.queue(() -> {
                for (int rayZ = min.getBlockZ(); rayZ <= max.getBlockZ(); rayZ++) {
                    int start = Integer.MAX_VALUE;
                    int end = Integer.MIN_VALUE;

                    for (int ray = min.getBlockY(); ray <= max.getBlockY(); ray++) {
                        if (boreContains(source, bore, new IrisBlockVector(finalRayX, ray, rayZ))) {
                            start = Math.min(ray, start);
                            end = Math.max(ray, end);
                        }
                    }

                    if (start != Integer.MAX_VALUE && end != Integer.MIN_VALUE) {
                        for (int i = start; i <= end; i++) {
                            boreCell(source, bore, new IrisBlockVector(finalRayX, i, rayZ), vair, applied);
                        }
                    }
                }
            });
        }

        // Smash Z
        for (int rayX = min.getBlockX(); rayX <= max.getBlockX(); rayX++) {
            int finalRayX = rayX;
            burst.queue(() -> {
                for (int rayY = min.getBlockY(); rayY <= max.getBlockY(); rayY++) {
                    int start = Integer.MAX_VALUE;
                    int end = Integer.MIN_VALUE;

                    for (int ray = min.getBlockZ(); ray <= max.getBlockZ(); ray++) {
                        if (boreContains(source, bore, new IrisBlockVector(finalRayX, rayY, ray))) {
                            start = Math.min(ray, start);
                            end = Math.max(ray, end);
                        }
                    }

                    if (start != Integer.MAX_VALUE && end != Integer.MIN_VALUE) {
                        for (int i = start; i <= end; i++) {
                            boreCell(source, bore, new IrisBlockVector(finalRayX, rayY, i), vair, applied);
                        }
                    }
                }
            });
        }

        burst.complete();

        self.writeLock.lock();
        try {
            if (!self.smartBored) {
                bore.forEach((v, s) -> self.blocks.computeIfAbsent(v, (vv) -> s));
                self.smartBored = true;
            }
        } finally {
            self.writeLock.unlock();
        }

        IrisLogging.debug("Smart Bore: " + self.getLoadKey() + " in " + Form.duration(p.getMilliseconds(), 2) + " (" + Form.f(applied.get()) + ")");
    }

    private static boolean boreContains(VectorMap<PlatformBlockState> source, VectorMap<PlatformBlockState> bore, IrisBlockVector v) {
        return source.containsKey(v) || bore.containsKey(v);
    }

    private static void boreCell(VectorMap<PlatformBlockState> source, VectorMap<PlatformBlockState> bore, IrisBlockVector v, PlatformBlockState vair, AtomicInteger applied) {
        PlatformBlockState existing = source.get(v);

        if (existing == null) {
            if (vair.equals(bore.get(v))) {
                return;
            }

            bore.computeIfAbsent(v, (vv) -> vair);
        } else if (vair.equals(existing)) {
            return;
        }

        applied.getAndIncrement();
    }

    static void shrinkwrap(IrisObject self) {
        if (self.blocks.isEmpty()) return;
        IrisBlockVector min = new IrisBlockVector(Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE);
        IrisBlockVector max = new IrisBlockVector(Integer.MIN_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE);

        for (IrisBlockVector i : self.blocks.keys()) {
            min.setX(Math.min(min.getX(), i.getX()));
            min.setY(Math.min(min.getY(), i.getY()));
            min.setZ(Math.min(min.getZ(), i.getZ()));
            max.setX(Math.max(max.getX(), i.getX()));
            max.setY(Math.max(max.getY(), i.getY()));
            max.setZ(Math.max(max.getZ(), i.getZ()));
        }

        // Compute into locals and assign every field in one block at the end, so a reader can
        // never observe post-shrink dimensions paired with pre-shrink volume (or vice versa).
        int w = max.getBlockX() - min.getBlockX() + 1;
        int h = max.getBlockY() - min.getBlockY() + 1;
        int d = max.getBlockZ() - min.getBlockZ() + 1;
        Vector3i center = new Vector3i(w / 2, h / 2, d / 2);

        Vector3i offset = new Vector3i(
                -center.getBlockX() - min.getBlockX(),
                -center.getBlockY() - min.getBlockY(),
                -center.getBlockZ() - min.getBlockZ()
        );
        if (offset.getBlockX() == 0 && offset.getBlockY() == 0 && offset.getBlockZ() == 0) {
            self.w = w;
            self.h = h;
            self.d = d;
            self.center = center;
            return;
        }

        VectorMap<PlatformBlockState> b = new VectorMap<>();
        VectorMap<TileData> s = new VectorMap<>();
        IrisBlockVector shift = new IrisBlockVector(offset.getX(), offset.getY(), offset.getZ());

        self.blocks.forEach((vector, data) -> {
            vector.add(shift);
            b.put(vector, data);
        });

        self.states.forEach((vector, data) -> {
            vector.add(shift);
            s.put(vector, data);
        });

        self.w = w;
        self.h = h;
        self.d = d;
        self.center = center;
        self.shrinkOffset = offset;
        self.blocks = b;
        self.states = s;
        self.surfaceSupportOffsets.reset();
        self.floatingFootprint.reset();
    }

    static void clean(IrisObject self) {
        VectorMap<PlatformBlockState> d = new VectorMap<>();
        d.putAll(self.blocks);

        VectorMap<TileData> dx = new VectorMap<>();
        dx.putAll(self.states);

        self.blocks = d;
        self.states = dx;
        self.surfaceSupportOffsets.reset();
        self.floatingFootprint.reset();
    }

    static boolean shouldStilt(PlatformBlockState state) {
        if (!state.isOccluding()) {
            return false;
        }
        String material = materialKey(state);
        if (material.endsWith("_stairs") || material.endsWith("_slab")) {
            return false;
        }
        return !material.equals("minecraft:dirt_path");
    }

    static String materialKey(PlatformBlockState state) {
        return IrisProceduralBlocks.materialKey(state);
    }
}
