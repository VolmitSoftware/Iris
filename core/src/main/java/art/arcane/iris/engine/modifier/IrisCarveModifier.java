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

import art.arcane.iris.engine.actuator.IrisDecorantActuator;
import art.arcane.iris.engine.framework.Engine;
import art.arcane.iris.engine.framework.EngineAssignedModifier;
import art.arcane.iris.engine.object.InferredType;
import art.arcane.iris.engine.object.IrisBiome;
import art.arcane.iris.engine.object.IrisDecorationPart;
import art.arcane.iris.engine.object.IrisDecorator;
import art.arcane.iris.engine.object.IrisDimensionCarvingResolver;
import art.arcane.iris.util.project.context.ChunkContext;
import art.arcane.iris.util.common.data.B;
import art.arcane.volmlib.util.documentation.ChunkCoordinates;
import art.arcane.iris.util.project.hunk.Hunk;
import art.arcane.volmlib.util.collection.KList;
import art.arcane.volmlib.util.mantle.runtime.Mantle;
import art.arcane.volmlib.util.mantle.runtime.MantleChunk;
import art.arcane.volmlib.util.math.BlockPosition;
import art.arcane.volmlib.util.math.M;
import art.arcane.volmlib.util.math.RNG;
import art.arcane.volmlib.util.matter.Matter;
import art.arcane.volmlib.util.matter.MatterCavern;
import art.arcane.volmlib.util.matter.slices.MarkerMatter;
import art.arcane.volmlib.util.scheduling.PrecisionStopwatch;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import lombok.Data;
import org.bukkit.Material;
import org.bukkit.block.data.BlockData;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

public class IrisCarveModifier extends EngineAssignedModifier<BlockData> {
    private static final ThreadLocal<CarveScratch> SCRATCH = ThreadLocal.withInitial(CarveScratch::new);
    private final RNG rng;
    private final BlockData AIR = Material.CAVE_AIR.createBlockData();
    private final BlockData LAVA = Material.LAVA.createBlockData();
    private final IrisDecorantActuator decorant;

    public IrisCarveModifier(Engine engine) {
        super(engine, "Carve");
        rng = new RNG(getEngine().getSeedManager().getCarve());
        decorant = new IrisDecorantActuator(engine);
    }

    @Override
    @ChunkCoordinates
    public void onModify(int x, int z, Hunk<BlockData> output, boolean multicore, ChunkContext context) {
        PrecisionStopwatch p = PrecisionStopwatch.start();
        Mantle<Matter> mantle = getEngine().getMantle().getMantle();
        MantleChunk<Matter> mc = mantle.getChunk(x, z).use();
        IrisDimensionCarvingResolver.State resolverState = new IrisDimensionCarvingResolver.State();
        Long2ObjectOpenHashMap<IrisBiome> caveBiomeCache = new Long2ObjectOpenHashMap<>(2048);
        CarveScratch scratch = SCRATCH.get();
        scratch.reset();
        PackedWallBuffer walls = scratch.walls;
        ColumnMask[] columnMasks = scratch.columnMasks;
        Map<String, IrisBiome> customBiomeCache = scratch.customBiomeCache;

        try {
            PrecisionStopwatch resolveStopwatch = PrecisionStopwatch.start();
            mc.iterate(MatterCavern.class, (xx, yy, zz, c) -> {
                if (c == null) {
                    return;
                }

                if (yy >= getEngine().getWorld().maxHeight() - getEngine().getWorld().minHeight() || yy <= 0) {
                    return;
                }

                int rx = xx & 15;
                int rz = zz & 15;
                int columnIndex = (rx << 4) | rz;
                BlockData current = output.get(rx, yy, rz);

                if (B.isFluid(current)) {
                    return;
                }

                columnMasks[columnIndex].add(yy);

                if (rz < 15 && mc.get(xx, yy, zz + 1, MatterCavern.class) == null) {
                    walls.put(rx, yy, rz + 1, c);
                }

                if (rx < 15 && mc.get(xx + 1, yy, zz, MatterCavern.class) == null) {
                    walls.put(rx + 1, yy, rz, c);
                }

                if (rz > 0 && mc.get(xx, yy, zz - 1, MatterCavern.class) == null) {
                    walls.put(rx, yy, rz - 1, c);
                }

                if (rx > 0 && mc.get(xx - 1, yy, zz, MatterCavern.class) == null) {
                    walls.put(rx - 1, yy, rz, c);
                }

                if (current.getMaterial().isAir()) {
                    return;
                }

                if (c.isWater()) {
                    output.set(rx, yy, rz, context.getFluid().get(rx, rz));
                } else if (c.isLava()) {
                    output.set(rx, yy, rz, LAVA);
                } else if (c.getLiquid() == 3) {
                    output.set(rx, yy, rz, AIR);
                } else if (getEngine().getDimension().getCaveLavaHeight() > yy) {
                    output.set(rx, yy, rz, LAVA);
                } else {
                    output.set(rx, yy, rz, AIR);
                }
            });
            getEngine().getMetrics().getCarveResolve().put(resolveStopwatch.getMilliseconds());

            PrecisionStopwatch applyStopwatch = PrecisionStopwatch.start();
            try {
                walls.forEach((rx, yy, rz, cavern) -> {
                    int worldX = rx + (x << 4);
                    int worldZ = rz + (z << 4);
                    String customBiome = cavern.getCustomBiome();
                    IrisBiome biome = customBiome.isEmpty()
                            ? resolveCaveBiome(caveBiomeCache, worldX, yy, worldZ, resolverState)
                            : resolveCustomBiome(customBiomeCache, customBiome);

                    if (biome != null) {
                        biome.setInferredType(InferredType.CAVE);
                        BlockData data = biome.getWall().get(rng, worldX, yy, worldZ, getData());

                        if (data != null && B.isSolid(output.get(rx, yy, rz)) && yy <= context.getHeight().get(rx, rz)) {
                            output.set(rx, yy, rz, data);
                        }
                    }
                });

                for (int columnIndex = 0; columnIndex < 256; columnIndex++) {
                    processColumnFromMask(output, mc, mantle, columnMasks[columnIndex], columnIndex, x, z, resolverState, caveBiomeCache);
                }
            } finally {
                getEngine().getMetrics().getCarveApply().put(applyStopwatch.getMilliseconds());
            }
        } finally {
            getEngine().getMetrics().getCave().put(p.getMilliseconds());
            mc.release();
        }
    }

    private void processColumnFromMask(
            Hunk<BlockData> output,
            MantleChunk<Matter> mc,
            Mantle<Matter> mantle,
            ColumnMask columnMask,
            int columnIndex,
            int chunkX,
            int chunkZ,
            IrisDimensionCarvingResolver.State resolverState,
            Long2ObjectOpenHashMap<IrisBiome> caveBiomeCache
    ) {
        if (columnMask == null || columnMask.isEmpty()) {
            return;
        }

        int firstHeight = columnMask.nextSetBit(0);
        if (firstHeight < 0) {
            return;
        }

        int rx = columnIndex >> 4;
        int rz = columnIndex & 15;
        int worldX = rx + (chunkX << 4);
        int worldZ = rz + (chunkZ << 4);
        CaveZone zone = new CaveZone();
        zone.setFloor(firstHeight);
        int buf = firstHeight - 1;
        int y = firstHeight;

        while (y >= 0) {
            if (y >= 0 && y <= getEngine().getHeight()) {
                if (y == buf + 1) {
                    buf = y;
                    zone.ceiling = buf;
                } else if (zone.isValid(getEngine())) {
                    processZone(output, mc, mantle, zone, rx, rz, worldX, worldZ, resolverState, caveBiomeCache);
                    zone = new CaveZone();
                    zone.setFloor(y);
                    buf = y;
                } else {
                    zone = new CaveZone();
                    zone.setFloor(y);
                    buf = y;
                }
            }

            y = columnMask.nextSetBit(y + 1);
        }

        if (zone.isValid(getEngine())) {
            processZone(output, mc, mantle, zone, rx, rz, worldX, worldZ, resolverState, caveBiomeCache);
        }
    }

    private void processZone(Hunk<BlockData> output, MantleChunk<Matter> mc, Mantle<Matter> mantle, CaveZone zone, int rx, int rz, int xx, int zz, IrisDimensionCarvingResolver.State resolverState, Long2ObjectOpenHashMap<IrisBiome> caveBiomeCache) {
        int center = (zone.floor + zone.ceiling) / 2;
        String customBiome = "";

        if (B.isDecorant(output.getClosest(rx, zone.ceiling + 1, rz))) {
            output.set(rx, zone.ceiling + 1, rz, AIR);
        }

        if (B.isDecorant(output.get(rx, zone.ceiling, rz))) {
            output.set(rx, zone.ceiling, rz, AIR);
        }

        if (M.r(1D / 16D)) {
            mantle.set(xx, zone.ceiling, zz, MarkerMatter.CAVE_CEILING);
        }

        if (M.r(1D / 16D)) {
            mantle.set(xx, zone.floor, zz, MarkerMatter.CAVE_FLOOR);
        }

        for (int i = zone.floor; i <= zone.ceiling; i++) {
            MatterCavern cavernData = (MatterCavern) mc.getOrCreate(i >> 4).slice(MatterCavern.class)
                    .get(rx, i & 15, rz);

            if (cavernData != null && !cavernData.getCustomBiome().isEmpty()) {
                customBiome = cavernData.getCustomBiome();
                break;
            }
        }

        IrisBiome biome = customBiome.isEmpty()
                ? resolveCaveBiome(caveBiomeCache, xx, center, zz, resolverState)
                : getEngine().getData().getBiomeLoader().load(customBiome);

        if (biome == null) {
            return;
        }

        biome.setInferredType(InferredType.CAVE);

        KList<BlockData> blocks = biome.generateLayers(getDimension(), xx, zz, rng, 3, zone.floor, getData(), getComplex());

        for (int i = 0; i < zone.floor - 1; i++) {
            if (!blocks.hasIndex(i)) {
                break;
            }
            int y = zone.floor - i - 1;

            BlockData b = blocks.get(i);
            BlockData down = output.get(rx, y, rz);

            if (!B.isSolid(down)) {
                continue;
            }

            if (B.isOre(down)) {
                output.set(rx, y, rz, B.toDeepSlateOre(down, b));
                continue;
            }

            output.set(rx, y, rz, blocks.get(i));
        }

        blocks = biome.generateCeilingLayers(getDimension(), xx, zz, rng, 3, zone.ceiling, getData(), getComplex());

        if (zone.ceiling + 1 < mantle.getWorldHeight()) {
            for (int i = 0; i < zone.ceiling + 1; i++) {
                if (!blocks.hasIndex(i)) {
                    break;
                }

                BlockData b = blocks.get(i);
                BlockData up = output.get(rx, zone.ceiling + i + 1, rz);

                if (!B.isSolid(up)) {
                    continue;
                }

                if (B.isOre(up)) {
                    output.set(rx, zone.ceiling + i + 1, rz, B.toDeepSlateOre(up, b));
                    continue;
                }

                output.set(rx, zone.ceiling + i + 1, rz, b);
            }
        }

        for (IrisDecorator i : biome.getDecorators()) {
            if (i.getPartOf().equals(IrisDecorationPart.NONE) && B.isSolid(output.get(rx, zone.getFloor() - 1, rz))) {
                decorant.getSurfaceDecorator().decorate(rx, rz, xx, xx, xx, zz, zz, zz, output, biome, zone.getFloor() - 1, zone.airThickness());
            } else if (i.getPartOf().equals(IrisDecorationPart.CEILING) && B.isSolid(output.get(rx, zone.getCeiling() + 1, rz))) {
                decorant.getCeilingDecorator().decorate(rx, rz, xx, xx, xx, zz, zz, zz, output, biome, zone.getCeiling(), zone.airThickness());
            }
        }
    }

    private IrisBiome resolveCaveBiome(Long2ObjectOpenHashMap<IrisBiome> caveBiomeCache, int x, int y, int z, IrisDimensionCarvingResolver.State resolverState) {
        long key = BlockPosition.toLong(x, y, z);
        IrisBiome cachedBiome = caveBiomeCache.get(key);
        if (cachedBiome != null) {
            return cachedBiome;
        }

        IrisBiome resolvedBiome = getEngine().getCaveBiome(x, y, z, resolverState);
        if (resolvedBiome != null) {
            caveBiomeCache.put(key, resolvedBiome);
        }
        return resolvedBiome;
    }

    private IrisBiome resolveCustomBiome(Map<String, IrisBiome> customBiomeCache, String customBiome) {
        if (customBiomeCache.containsKey(customBiome)) {
            return customBiomeCache.get(customBiome);
        }

        IrisBiome loaded = getEngine().getData().getBiomeLoader().load(customBiome);
        customBiomeCache.put(customBiome, loaded);
        return loaded;
    }

    private static final class PackedWallBuffer {
        private static final int EMPTY_KEY = -1;
        private static final double LOAD_FACTOR = 0.75D;

        private int[] keys;
        private MatterCavern[] values;
        private int mask;
        private int resizeAt;
        private int size;

        private PackedWallBuffer(int expectedSize) {
            int capacity = 1;
            int minimumCapacity = Math.max(8, expectedSize);
            while (capacity < minimumCapacity) {
                capacity <<= 1;
            }

            this.keys = new int[capacity];
            Arrays.fill(this.keys, EMPTY_KEY);
            this.values = new MatterCavern[capacity];
            this.mask = capacity - 1;
            this.resizeAt = Math.max(1, (int) (capacity * LOAD_FACTOR));
        }

        private void put(int x, int y, int z, MatterCavern value) {
            int key = pack(x, y, z);
            int index = mix(key) & mask;

            while (true) {
                int existingKey = keys[index];
                if (existingKey == EMPTY_KEY) {
                    keys[index] = key;
                    values[index] = value;
                    size++;
                    if (size >= resizeAt) {
                        resize();
                    }
                    return;
                }

                if (existingKey == key) {
                    values[index] = value;
                    return;
                }

                index = (index + 1) & mask;
            }
        }

        private void forEach(PackedWallConsumer consumer) {
            for (int index = 0; index < keys.length; index++) {
                int key = keys[index];
                if (key == EMPTY_KEY) {
                    continue;
                }

                MatterCavern cavern = values[index];
                if (cavern == null) {
                    continue;
                }

                consumer.accept(unpackX(key), unpackY(key), unpackZ(key), cavern);
            }
        }

        private void clear() {
            Arrays.fill(keys, EMPTY_KEY);
            Arrays.fill(values, null);
            size = 0;
        }

        private void resize() {
            int[] oldKeys = keys;
            MatterCavern[] oldValues = values;
            int nextCapacity = oldKeys.length << 1;
            keys = new int[nextCapacity];
            Arrays.fill(keys, EMPTY_KEY);
            values = new MatterCavern[nextCapacity];
            mask = nextCapacity - 1;
            resizeAt = Math.max(1, (int) (nextCapacity * LOAD_FACTOR));
            size = 0;

            for (int index = 0; index < oldKeys.length; index++) {
                int key = oldKeys[index];
                if (key == EMPTY_KEY) {
                    continue;
                }

                MatterCavern value = oldValues[index];
                if (value == null) {
                    continue;
                }

                reinsert(key, value);
            }
        }

        private void reinsert(int key, MatterCavern value) {
            int index = mix(key) & mask;
            while (keys[index] != EMPTY_KEY) {
                index = (index + 1) & mask;
            }

            keys[index] = key;
            values[index] = value;
            size++;
        }

        private int pack(int x, int y, int z) {
            return (y << 8) | ((x & 15) << 4) | (z & 15);
        }

        private int unpackX(int key) {
            return (key >> 4) & 15;
        }

        private int unpackY(int key) {
            return key >> 8;
        }

        private int unpackZ(int key) {
            return key & 15;
        }

        private int mix(int value) {
            int mixed = value * 0x9E3779B9;
            return mixed ^ (mixed >>> 16);
        }
    }

    private static final class CarveScratch {
        private final ColumnMask[] columnMasks = new ColumnMask[256];
        private final PackedWallBuffer walls = new PackedWallBuffer(512);
        private final Map<String, IrisBiome> customBiomeCache = new HashMap<>();

        private CarveScratch() {
            for (int index = 0; index < columnMasks.length; index++) {
                columnMasks[index] = new ColumnMask();
            }
        }

        private void reset() {
            for (int index = 0; index < columnMasks.length; index++) {
                columnMasks[index].clear();
            }
            walls.clear();
            customBiomeCache.clear();
        }
    }

    private static final class ColumnMask {
        private long[] words = new long[8];
        private int maxWord = -1;

        private void add(int y) {
            if (y < 0) {
                return;
            }

            int wordIndex = y >> 6;
            if (wordIndex >= words.length) {
                words = Arrays.copyOf(words, Math.max(words.length << 1, wordIndex + 1));
            }

            words[wordIndex] |= 1L << (y & 63);
            if (wordIndex > maxWord) {
                maxWord = wordIndex;
            }
        }

        private int nextSetBit(int fromBit) {
            if (maxWord < 0) {
                return -1;
            }

            int startBit = Math.max(0, fromBit);
            int wordIndex = startBit >> 6;
            if (wordIndex > maxWord) {
                return -1;
            }

            long word = words[wordIndex] & (-1L << (startBit & 63));
            while (true) {
                if (word != 0L) {
                    return (wordIndex << 6) + Long.numberOfTrailingZeros(word);
                }

                wordIndex++;
                if (wordIndex > maxWord) {
                    return -1;
                }
                word = words[wordIndex];
            }
        }

        private boolean isEmpty() {
            return maxWord < 0;
        }

        private void clear() {
            if (maxWord < 0) {
                return;
            }

            for (int index = 0; index <= maxWord; index++) {
                words[index] = 0L;
            }
            maxWord = -1;
        }
    }

    @FunctionalInterface
    private interface PackedWallConsumer {
        void accept(int x, int y, int z, MatterCavern cavern);
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
