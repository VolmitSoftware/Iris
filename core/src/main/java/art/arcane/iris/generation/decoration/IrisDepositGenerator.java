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

package art.arcane.iris.generation.decoration;

import art.arcane.iris.generation.biome.IrisBiome;
import art.arcane.iris.generation.block.IrisBlockData;
import art.arcane.iris.structure.object.IrisObject;

import art.arcane.iris.pack.loading.IrisData;
import art.arcane.volmlib.util.cache.AtomicCache;
import art.arcane.iris.generation.runtime.Engine;
import art.arcane.iris.pack.schema.annotation.ArrayType;
import art.arcane.volmlib.util.documentation.Description;
import art.arcane.iris.pack.schema.annotation.MaxNumber;
import art.arcane.iris.pack.schema.annotation.MinNumber;
import art.arcane.iris.pack.schema.annotation.Required;
import art.arcane.iris.pack.schema.annotation.Snippet;
import art.arcane.volmlib.nativelib.terrain.NativeBlockState;
import art.arcane.iris.generation.block.B;
import art.arcane.iris.generation.geometry.IrisBlockVector;
import art.arcane.volmlib.util.math.Vector3i;
import art.arcane.volmlib.util.collection.KList;
import art.arcane.volmlib.util.collection.KSet;
import art.arcane.volmlib.util.math.BlockPosition;
import art.arcane.volmlib.util.math.RNG;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

@Snippet("deposit")
@Accessors(chain = true)
@NoArgsConstructor
@AllArgsConstructor
@Description("Creates ore & other block deposits underground")
@Data
public class IrisDepositGenerator {
    private static final int FOOTPRINT_MAXIMUM_SIZE = 64;
    private static final ThreadLocal<FootprintCells> FOOTPRINT_CELLS = ThreadLocal.withInitial(FootprintCells::new);
    private static final double[][] NODE_SINES = nodeSines();
    private static final long FNV_OFFSET_BASIS = 0xcbf29ce484222325L;
    private static final long FNV_PRIME = 0x100000001b3L;

    private final transient ConcurrentMap<ClumpCacheKey, KList<IrisObject>> objects = new ConcurrentHashMap<>();
    private final transient AtomicCache<KList<NativeBlockState>> blockData = new AtomicCache<>();
    private final transient AtomicCache<Boolean> ore = new AtomicCache<>();
    private final transient AtomicCache<KSet<String>> replaceableBlockData = new AtomicCache<>();
    private final transient AtomicCache<KSet<String>> surfaceReplaceableBlockData = new AtomicCache<>();
    private final transient ConcurrentMap<ClumpCacheKey, KList<IrisObject>> scaledObjects = new ConcurrentHashMap<>();
    @Required
    @MinNumber(-8192)
    @MaxNumber(8192)
    @Description("The inclusive minimum origin height in engine-local Y, where 0 is the bottom of the dimension. Negative values let an unclipped vanilla distribution taper into the world floor.")
    private int minHeight = 1;
    @Required
    @MinNumber(-8192)
    @MaxNumber(8192)
    @Description("The inclusive maximum origin height in engine-local Y, where 0 is the bottom of the dimension.")
    private int maxHeight = 75;
    @Description("How origin heights are sampled. CLIPPED_UNIFORM preserves Iris behavior by clipping the band to terrain; UNIFORM and TRIANGLE sample the authored band first and discard cells outside terrain or build height.")
    private IrisDepositHeightDistribution heightDistribution = IrisDepositHeightDistribution.CLIPPED_UNIFORM;
    @Description("TERRAIN places below the generated surface. ABOVE_TERRAIN places only in existing solid hosts above it. FULL_HEIGHT permits existing solid hosts anywhere in the dimension.")
    private IrisDepositPlacementScope placementScope = IrisDepositPlacementScope.TERRAIN;
    @MinNumber(0)
    @MaxNumber(256)
    @Description("Solid blocks kept between a deposit and the terrain surface. Vanilla-like deposits use 0; the Iris default remains 7.")
    private int surfaceClearance = 7;
    @Required
    @MinNumber(0)
    @MaxNumber(8192)
    @Description("The minimum Iris block count or vanilla configured vein size, selected according to shape.")
    private int minSize = 0;
    @Required
    @MinNumber(0)
    @MaxNumber(8192)
    @Description("The maximum Iris block count or vanilla configured vein size, selected according to shape.")
    private int maxSize = 128;
    @Description("IRIS places a fixed-count cube clump. VANILLA_ELLIPSOID uses Minecraft's chained ellipsoid geometry. VANILLA_SCATTERED uses Minecraft's sparse candidate offsets.")
    private IrisDepositShape shape = IrisDepositShape.IRIS;
    @Required
    @MinNumber(0)
    @MaxNumber(2048)
    @Description("The maximum amount of clumps per chunk")
    private int maxPerChunk = 3;
    @Required
    @MinNumber(0)
    @MaxNumber(2048)
    @Description("The minimum amount of clumps per chunk")
    private int minPerChunk = 0;
    @MinNumber(0)
    @MaxNumber(1)
    @Description("The chance of the deposit spawning in a chunk")
    private double spawnChance = 1;
    @MinNumber(0)
    @MaxNumber(1)
    @Description("The chance of each individual clump spawning in a chunk")
    private double perClumpSpawnChance = 1;
    @MinNumber(0)
    @MaxNumber(1)
    @Description("Chance to discard a candidate ore block when it touches air. This matches vanilla ore exposure reduction.")
    private double discardChanceOnAirExposure = 0;
    @Required
    @ArrayType(min = 1, type = IrisBlockData.class)
    @Description("The palette of blocks to be used in this deposit generator. Each entry is picked uniformly; the per-entry weight field is ignored here.")
    private KList<IrisBlockData> palette = new KList<>();
    @MinNumber(1)
    @MaxNumber(64)
    @Description("Ore varience is how many different objects clumps iris will create")
    private int varience = 3;
    @ArrayType(min = 1, type = String.class)
    @Description("Optional block ids this deposit may replace. An empty list retains Iris's any-solid behavior.")
    private KList<String> replaceableBlocks = new KList<>();
    @ArrayType(min = 1, type = String.class)
    @Description("Optional exact host block ids required when an ore candidate occupies an exterior terrain surface. Cave-air walls are unaffected. An empty list retains the normal replaceableBlocks behavior.")
    private KList<String> surfaceReplaceableBlocks = new KList<>();
    @Description("Chooses whether includedBiomes and excludedBiomes inspect the surface biome or the cave biome at the deposit origin.")
    private IrisDepositBiomeScope biomeScope = IrisDepositBiomeScope.CAVE;
    @ArrayType(min = 1, type = String.class)
    @Description("Optional Iris biome keys or vanilla derivative ids allowed for this deposit.")
    private KList<String> includedBiomes = new KList<>();
    @ArrayType(min = 1, type = String.class)
    @Description("Optional Iris biome keys or vanilla derivative ids denied for this deposit.")
    private KList<String> excludedBiomes = new KList<>();
    @Description("If set to true, this deposit will replace bedrock")
    private boolean replaceBedrock = false;

    public IrisObject getClump(Engine engine, RNG rng, IrisData rdata) {
        if (shape != IrisDepositShape.IRIS) {
            return generateConfiguredClumpObject(rng, rdata, minSize, maxSize);
        }

        ClumpCacheKey cacheKey = new ClumpCacheKey(engine.getSeedManager().getDeposit(), minSize, maxSize);
        KList<IrisObject> objects = this.objects.computeIfAbsent(cacheKey, key -> {
            RNG rngv = new RNG(key.depositSeed() + stableClumpSalt(rdata));
            KList<IrisObject> objectsf = new KList<>();

            for (int i = 0; i < varience; i++) {
                objectsf.add(generateClumpObject(
                        rngv.nextParallelRNG(2349 * i + 3598), rdata, key.minSize(), key.maxSize()));
            }

            return objectsf;
        });
        return objects.get(rng.i(0, objects.size()));
    }

    public IrisObject getClump(Engine engine, RNG rng, IrisData rdata, double sizeMultiplier) {
        if (sizeMultiplier == 1D) {
            return getClump(engine, rng, rdata);
        }

        int scaledMinSize = scaledDepositSize(minSize, sizeMultiplier);
        int scaledMaxSize = scaledDepositSize(maxSize, sizeMultiplier);
        if (shape != IrisDepositShape.IRIS) {
            return generateConfiguredClumpObject(rng, rdata, scaledMinSize, scaledMaxSize);
        }

        ClumpCacheKey cacheKey = new ClumpCacheKey(
                engine.getSeedManager().getDeposit(), scaledMinSize, scaledMaxSize);
        KList<IrisObject> objects = scaledObjects.computeIfAbsent(cacheKey, key -> {
            long sizeSeed = ((long) key.minSize() << 32) ^ (key.maxSize() & 0xffffffffL);
            RNG rngv = new RNG(key.depositSeed() + stableClumpSalt(rdata) + sizeSeed);
            KList<IrisObject> generated = new KList<>();

            for (int i = 0; i < varience; i++) {
                generated.add(generateClumpObject(
                        rngv.nextParallelRNG(2349 * i + 3598), rdata, key.minSize(), key.maxSize()));
            }

            return generated;
        });
        return objects.get(rng.i(0, objects.size()));
    }

    public int getMaxDimension() {
        return Math.min(11, (int) Math.ceil(Math.cbrt(maxSize)));
    }

    static int scaledDepositSize(int size, double multiplier) {
        return Math.max(0, Math.min(8192, (int) Math.round(size * multiplier)));
    }

    long stableClumpSalt(IrisData rdata) {
        long hash = FNV_OFFSET_BASIS;
        hash = mix(hash, minHeight);
        hash = mix(hash, maxHeight);
        hash = mixEnum(hash, heightDistribution);
        hash = mixEnum(hash, placementScope);
        hash = mix(hash, surfaceClearance);
        hash = mix(hash, minSize);
        hash = mix(hash, maxSize);
        hash = mixEnum(hash, shape);
        hash = mix(hash, maxPerChunk);
        hash = mix(hash, minPerChunk);
        hash = mix(hash, Double.doubleToLongBits(spawnChance));
        hash = mix(hash, Double.doubleToLongBits(perClumpSpawnChance));
        hash = mix(hash, Double.doubleToLongBits(discardChanceOnAirExposure));
        KList<NativeBlockState> resolvedPalette = getBlockData(rdata);
        hash = mix(hash, resolvedPalette.size());
        for (NativeBlockState block : resolvedPalette) {
            hash = mixString(hash, block == null ? null : block.key());
        }
        hash = mix(hash, varience);
        hash = mixStrings(hash, replaceableBlocks);
        hash = mixEnum(hash, biomeScope);
        hash = mixStrings(hash, includedBiomes);
        hash = mixStrings(hash, excludedBiomes);
        return mix(hash, replaceBedrock ? 1L : 0L);
    }

    private static long mix(long hash, long value) {
        return (hash ^ value) * FNV_PRIME;
    }

    private static long mixEnum(long hash, Enum<?> value) {
        return mixString(hash, value == null ? null : value.name());
    }

    private static long mixString(long hash, String value) {
        if (value == null) {
            return mix(hash, -1L);
        }
        long mixed = mix(hash, value.length());
        for (int i = 0; i < value.length(); i++) {
            mixed = mix(mixed, value.charAt(i));
        }
        return mixed;
    }

    private static long mixStrings(long hash, KList<String> values) {
        if (values == null) {
            return mix(hash, -1L);
        }
        long mixed = mix(hash, values.size());
        for (String value : values) {
            mixed = mixString(mixed, value);
        }
        return mixed;
    }

    private IrisObject generateConfiguredClumpObject(RNG rng, IrisData rdata, int clumpMinSize, int clumpMaxSize) {
        int size = rng.i(clumpMinSize, clumpMaxSize + 1);
        return switch (shape) {
            case VANILLA_ELLIPSOID -> generateVanillaEllipsoid(rng, rdata, size);
            case VANILLA_SCATTERED -> generateVanillaScattered(rng, rdata, size);
            case IRIS -> generateClumpObject(rng, rdata, clumpMinSize, clumpMaxSize);
        };
    }

    IrisObject generateVanillaEllipsoid(RNG rng, IrisData rdata, int size) {
        if (size <= 0) {
            return new IrisObject(1, 1, 1);
        }

        Set<BlockPosition> cells = new LinkedHashSet<>();
        vanillaEllipsoidCells(rng, size, (x, y, z) -> cells.add(new BlockPosition(x, y, z)));
        return objectFromCells(cells, rng, rdata);
    }

    /**
     * Advances {@code rng} exactly as {@link #getClump(Engine, RNG, IrisData)} would and reports the clump's width
     * and lowest block offset without building it, so a caller can reject the clump first and replay the build
     * only for clumps it keeps. Returns null without drawing when the shape has no footprint path.
     */
    public ClumpFootprint sampleClumpFootprint(RNG rng, IrisData rdata) {
        if (!usesClumpFootprints(rdata)) {
            return null;
        }
        KList<NativeBlockState> resolvedPalette = getBlockData(rdata);
        int paletteSize = resolvedPalette.size();
        int size = rng.i(minSize, maxSize + 1);
        if (size <= 0) {
            return ClumpFootprint.EMPTY;
        }
        FootprintCells cells = FOOTPRINT_CELLS.get();
        cells.reset();
        if (paletteSize == 1) {
            emitEllipsoidRows(rng, size, cells);
            if (cells.count == 0) {
                return ClumpFootprint.EMPTY;
            }
            skipUniformPicks(rng, cells.count);
        } else {
            emitEllipsoidCells(rng, size, cells, cells.scratch);
            if (cells.count == 0) {
                return ClumpFootprint.EMPTY;
            }
            for (int cell = 0; cell < cells.count; cell++) {
                cells.picks[cell] = rng.i(0, paletteSize);
            }
        }
        return new ClumpFootprint(cells.extentX * 2 + 1, cells.minY, cells, cells.stamp, resolvedPalette);
    }

    public boolean usesClumpFootprints(IrisData rdata) {
        return shape == IrisDepositShape.VANILLA_ELLIPSOID && minSize <= FOOTPRINT_MAXIMUM_SIZE
                && maxSize <= FOOTPRINT_MAXIMUM_SIZE && !getBlockData(rdata).isEmpty();
    }

    public static int lowestFootprintOffset(int size) {
        return -4 - (Math.max(0, size) + 15) / 16;
    }

    private static void skipUniformPicks(RNG rng, int count) {
        if (rng instanceof DepositRandom random) {
            random.skip(2L * count);
            return;
        }
        for (int cell = 0; cell < count; cell++) {
            rng.nextDouble();
        }
    }

    /**
     * Emits the clump's cells sphere by sphere in x, y, z order, duplicates included. The squared y and z
     * offsets are computed once per sphere row; the sums keep the per-cell evaluation order, so every cell
     * test is bit-identical to evaluating {@code nx * nx + ny * ny + nz * nz} in place.
     */
    static void vanillaEllipsoidCells(RNG rng, int size, CellSink sink) {
        emitEllipsoidCells(rng, size, sink, new EllipsoidScratch());
    }

    private static void emitEllipsoidCells(RNG rng, int size, CellSink sink, EllipsoidScratch scratch) {
        double[] nodes = ellipsoidNodes(rng, size, scratch);
        for (int node = 0; node < size; node++) {
            if (!scratch.sphere(nodes, node)) {
                continue;
            }
            double[] squaredY = scratch.squaredY;
            double[] squaredZ = scratch.squaredZ;
            for (int x = scratch.minX; x <= scratch.maxX; x++) {
                double nx = (x + 0.5D - scratch.centerX) / scratch.radius;
                double squaredX = nx * nx;
                if (squaredX >= 1D) {
                    continue;
                }
                for (int y = 0; y < scratch.spanY; y++) {
                    double squaredXY = squaredX + squaredY[y];
                    if (squaredXY >= 1D) {
                        continue;
                    }
                    for (int z = 0; z < scratch.spanZ; z++) {
                        if (squaredXY + squaredZ[z] < 1D) {
                            sink.add(x, scratch.minY + y, scratch.minZ + z);
                        }
                    }
                }
            }
        }
    }

    private static void emitEllipsoidRows(RNG rng, int size, FootprintCells cells) {
        EllipsoidScratch scratch = cells.scratch;
        double[] nodes = ellipsoidNodes(rng, size, scratch);
        for (int node = 0; node < size; node++) {
            if (!scratch.sphere(nodes, node)) {
                continue;
            }
            double[] squaredY = scratch.squaredY;
            double[] squaredZ = scratch.squaredZ;
            int zShift = scratch.minZ + FootprintCells.OFFSET;
            for (int x = scratch.minX; x <= scratch.maxX; x++) {
                double nx = (x + 0.5D - scratch.centerX) / scratch.radius;
                double squaredX = nx * nx;
                if (squaredX >= 1D) {
                    continue;
                }
                for (int y = 0; y < scratch.spanY; y++) {
                    double squaredXY = squaredX + squaredY[y];
                    if (squaredXY >= 1D) {
                        continue;
                    }
                    long row = 0L;
                    for (int z = 0; z < scratch.spanZ; z++) {
                        if (squaredXY + squaredZ[z] < 1D) {
                            row |= 1L << (zShift + z);
                        }
                    }
                    if (row != 0L) {
                        cells.addRow(x, scratch.minY + y, row);
                    }
                }
            }
        }
        cells.finishRows();
    }

    private static double[] ellipsoidNodes(RNG rng, int size, EllipsoidScratch scratch) {
        float angle = rng.nextFloat() * (float) Math.PI;
        float reach = size / 8F;
        double sine = Math.sin(angle);
        double cosine = Math.cos(angle);
        double startX = sine * reach;
        double endX = -sine * reach;
        double startZ = cosine * reach;
        double endZ = -cosine * reach;
        double startY = rng.nextInt(3) - 2;
        double endY = rng.nextInt(3) - 2;
        double[] nodes = scratch.nodes(size * 4);
        double[] sines = size < NODE_SINES.length ? NODE_SINES[size] : null;

        for (int i = 0; i < size; i++) {
            float progress = (float) i / size;
            double radiusNoise = rng.nextDouble() * size / 16D;
            nodes[i * 4] = startX + (endX - startX) * progress;
            nodes[i * 4 + 1] = startY + (endY - startY) * progress;
            nodes[i * 4 + 2] = startZ + (endZ - startZ) * progress;
            double arc = sines == null ? Math.sin(Math.PI * progress) : sines[i];
            nodes[i * 4 + 3] = ((arc + 1D) * radiusNoise + 1D) / 2D;
        }

        for (int i = 0; i < size - 1; i++) {
            if (nodes[i * 4 + 3] <= 0D) {
                continue;
            }
            for (int j = i + 1; j < size; j++) {
                if (nodes[j * 4 + 3] <= 0D) {
                    continue;
                }
                double dx = nodes[i * 4] - nodes[j * 4];
                double dy = nodes[i * 4 + 1] - nodes[j * 4 + 1];
                double dz = nodes[i * 4 + 2] - nodes[j * 4 + 2];
                double dr = nodes[i * 4 + 3] - nodes[j * 4 + 3];
                if (dr * dr <= dx * dx + dy * dy + dz * dz) {
                    continue;
                }
                if (dr > 0D) {
                    nodes[j * 4 + 3] = -1D;
                } else {
                    nodes[i * 4 + 3] = -1D;
                }
            }
        }
        return nodes;
    }

    private static double[][] nodeSines() {
        double[][] sines = new double[FOOTPRINT_MAXIMUM_SIZE + 1][];
        for (int size = 1; size <= FOOTPRINT_MAXIMUM_SIZE; size++) {
            double[] values = new double[size];
            for (int i = 0; i < size; i++) {
                float progress = (float) i / size;
                values[i] = Math.sin(Math.PI * progress);
            }
            sines[size] = values;
        }
        return sines;
    }

    IrisObject generateVanillaScattered(RNG rng, IrisData rdata, int size) {
        Set<BlockPosition> cells = new LinkedHashSet<>();
        int candidates = rng.nextInt(Math.max(0, size) + 1);
        for (int i = 0; i < candidates; i++) {
            int magnitude = Math.min(i, 7);
            cells.add(new BlockPosition(
                    Math.round((rng.nextFloat() - rng.nextFloat()) * magnitude),
                    Math.round((rng.nextFloat() - rng.nextFloat()) * magnitude),
                    Math.round((rng.nextFloat() - rng.nextFloat()) * magnitude)));
        }
        return objectFromCells(cells, rng, rdata);
    }

    private IrisObject objectFromCells(Set<BlockPosition> cells, RNG rng, IrisData rdata) {
        if (cells.isEmpty()) {
            return new IrisObject(1, 1, 1);
        }

        int minX = Integer.MAX_VALUE;
        int minY = Integer.MAX_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxY = Integer.MIN_VALUE;
        int maxZ = Integer.MIN_VALUE;
        for (BlockPosition cell : cells) {
            minX = Math.min(minX, cell.getX());
            minY = Math.min(minY, cell.getY());
            minZ = Math.min(minZ, cell.getZ());
            maxX = Math.max(maxX, cell.getX());
            maxY = Math.max(maxY, cell.getY());
            maxZ = Math.max(maxZ, cell.getZ());
        }

        int extentX = Math.max(Math.abs(minX), Math.abs(maxX));
        int extentY = Math.max(Math.abs(minY), Math.abs(maxY));
        int extentZ = Math.max(Math.abs(minZ), Math.abs(maxZ));
        IrisObject object = new IrisObject(extentX * 2 + 1, extentY * 2 + 1, extentZ * 2 + 1);
        for (BlockPosition cell : cells) {
            object.setUnsigned(
                    cell.getX() + extentX,
                    cell.getY() + extentY,
                    cell.getZ() + extentZ,
                    nextBlock(rng, rdata));
        }
        return object;
    }

    private IrisObject generateClumpObject(RNG rngv, IrisData rdata, int clumpMinSize, int clumpMaxSize) {
        int s = rngv.i(clumpMinSize, clumpMaxSize + 1);
        if (s == 1) {
            IrisObject o = new IrisObject(1, 1, 1);
            Vector3i center = o.getCenter();
            o.getBlocks().put(new IrisBlockVector(center.getX(), center.getY(), center.getZ()), nextBlock(rngv, rdata));
            return o;
        }

        int dim = Math.min(11, (int) Math.ceil(Math.cbrt(s)));
        IrisObject o = new IrisObject(dim, dim, dim);

        int volume = dim * dim * dim;
        if (s >= volume) {
            int x = 0, y = 0, z = 0;

            while (z < dim) {
                o.setUnsigned(x++, y, z, nextBlock(rngv, rdata));

                if (x == dim) {
                    x = 0;
                    y++;
                }

                if (y == dim) {
                    y = 0;
                    z++;
                }
            }
            return o;
        }

        KSet<BlockPosition> set = new KSet<>();
        while (s > 0) {
            BlockPosition ang = new BlockPosition(
                    rngv.i(0, dim),
                    rngv.i(0, dim),
                    rngv.i(0, dim)
            );
            if (!set.add(ang)) continue;

            s--;
            o.setUnsigned(ang.getX(), ang.getY(), ang.getZ(), nextBlock(rngv, rdata));
        }

        return o;
    }

    private NativeBlockState nextBlock(RNG rngv, IrisData rdata) {
        return getBlockData(rdata).get(rngv.i(0, getBlockData(rdata).size()));
    }

    public KList<NativeBlockState> getBlockData(IrisData rdata) {
        return blockData.aquire(() ->
        {
            KList<NativeBlockState> blockData = new KList<>();

            for (IrisBlockData ix : palette) {
                NativeBlockState bx = ix.getBlockData(rdata);

                if (bx != null) {
                    blockData.add(bx);
                }
            }

            return blockData;
        });
    }

    public boolean isOre(IrisData rdata) {
        return ore.aquire(() -> {
            for (NativeBlockState block : getBlockData(rdata)) {
                if (block.isOre()) {
                    return true;
                }
            }

            return false;
        });
    }

    public boolean canReplace(NativeBlockState state) {
        if (replaceableBlocks == null || replaceableBlocks.isEmpty()) {
            return true;
        }
        return replaceableBlockData.aquire(this::resolveReplaceableBlocks)
                .contains(IrisProceduralBlocks.materialKey(state));
    }

    public IrisDepositGenerator setSurfaceReplaceableBlocks(KList<String> surfaceReplaceableBlocks) {
        this.surfaceReplaceableBlocks = surfaceReplaceableBlocks == null
                ? new KList<>()
                : surfaceReplaceableBlocks;
        surfaceReplaceableBlockData.reset();
        return this;
    }

    public boolean canReplaceSurface(NativeBlockState state) {
        if (surfaceReplaceableBlocks == null || surfaceReplaceableBlocks.isEmpty()) {
            return true;
        }
        return surfaceReplaceableBlockData.aquire(this::resolveSurfaceReplaceableBlocks)
                .contains(IrisProceduralBlocks.materialKey(state));
    }

    public boolean hasSurfaceReplaceableBlocks(IrisBiome surfaceBiome) {
        if (surfaceBiome != null && surfaceBiome.hasSurfaceOreReplaceableBlocks()) {
            return true;
        }
        return surfaceReplaceableBlocks != null && !surfaceReplaceableBlocks.isEmpty();
    }

    public boolean canReplaceSurface(NativeBlockState state, IrisBiome surfaceBiome) {
        if (surfaceBiome != null && surfaceBiome.hasSurfaceOreReplaceableBlocks()) {
            return surfaceBiome.canReplaceSurfaceOre(state);
        }
        return canReplaceSurface(state);
    }

    public boolean matchesBiome(IrisBiome surfaceBiome, IrisBiome caveBiome) {
        IrisBiome selected = biomeScope == IrisDepositBiomeScope.SURFACE ? surfaceBiome : caveBiome;
        if (includedBiomes != null && !includedBiomes.isEmpty() && !matchesAnyBiome(selected, includedBiomes)) {
            return false;
        }
        return excludedBiomes == null || excludedBiomes.isEmpty() || !matchesAnyBiome(selected, excludedBiomes);
    }

    public boolean usesCaveBiomeFilter() {
        return biomeScope == IrisDepositBiomeScope.CAVE
                && ((includedBiomes != null && !includedBiomes.isEmpty())
                || (excludedBiomes != null && !excludedBiomes.isEmpty()));
    }

    private KSet<String> resolveReplaceableBlocks() {
        return resolveBlockKeys(replaceableBlocks);
    }

    private KSet<String> resolveSurfaceReplaceableBlocks() {
        return resolveBlockKeys(surfaceReplaceableBlocks);
    }

    private KSet<String> resolveBlockKeys(KList<String> keys) {
        KSet<String> resolved = new KSet<>();
        for (String key : keys) {
            NativeBlockState state = B.getStateOrNull(key, false);
            if (state != null) {
                resolved.add(IrisProceduralBlocks.materialKey(state));
            }
        }
        return resolved;
    }

    private boolean matchesAnyBiome(IrisBiome biome, KList<String> configuredBiomes) {
        if (biome == null) {
            return false;
        }
        for (String configured : configuredBiomes) {
            String namespaced = configured.contains(":") ? configured : "minecraft:" + configured;
            if (configured.equals(biome.getLoadKey())
                    || namespaced.equals(biome.getDerivativeKey())
                    || namespaced.equals(biome.getVanillaDerivativeKey())) {
                return true;
            }
        }
        return false;
    }

    record ClumpCacheKey(long depositSeed, int minSize, int maxSize) {
    }

    @FunctionalInterface
    interface CellSink {
        void add(int x, int y, int z);
    }

    public static final class ClumpFootprint {
        static final ClumpFootprint EMPTY = new ClumpFootprint(1, 0, null, 0L, null);

        private final int width;
        private final int minY;
        private final FootprintCells source;
        private final long stamp;
        private final KList<NativeBlockState> palette;

        private ClumpFootprint(int width, int minY, FootprintCells source, long stamp, KList<NativeBlockState> palette) {
            this.width = width;
            this.minY = minY;
            this.source = source;
            this.stamp = stamp;
            this.palette = palette;
        }

        public int width() {
            return width;
        }

        public int minY() {
            return minY;
        }

        public boolean empty() {
            return source == null;
        }

        public ClumpCells cells() {
            if (source == null) {
                return ClumpCells.EMPTY;
            }
            if (source.stamp != stamp) {
                throw new IllegalStateException("Deposit footprint cells were resampled before they were read");
            }
            return source.toCells(palette);
        }
    }

    public static final class ClumpCells {
        static final ClumpCells EMPTY = new ClumpCells(new int[0], new NativeBlockState[0], null);

        private final int[] positions;
        private final NativeBlockState[] blocks;
        private final NativeBlockState uniform;

        private ClumpCells(int[] positions, NativeBlockState[] blocks, NativeBlockState uniform) {
            this.positions = positions;
            this.blocks = blocks;
            this.uniform = uniform;
        }

        public int size() {
            return positions.length;
        }

        public int x(int cell) {
            return (positions[cell] >> 12) - FootprintCells.OFFSET;
        }

        public int y(int cell) {
            return ((positions[cell] >> 6) & (FootprintCells.AXIS - 1)) - FootprintCells.OFFSET;
        }

        public int z(int cell) {
            return (positions[cell] & (FootprintCells.AXIS - 1)) - FootprintCells.OFFSET;
        }

        public NativeBlockState block(int cell) {
            return blocks == null ? uniform : blocks[cell];
        }
    }

    private static final class EllipsoidScratch {
        private double[] nodes = new double[256];
        private double[] squaredY = new double[16];
        private double[] squaredZ = new double[16];
        private double centerX;
        private double radius;
        private int minX;
        private int maxX;
        private int minY;
        private int spanY;
        private int minZ;
        private int spanZ;

        private double[] nodes(int length) {
            if (nodes.length < length) {
                nodes = new double[length];
            }
            return nodes;
        }

        private boolean sphere(double[] values, int node) {
            double sphereRadius = values[node * 4 + 3];
            if (sphereRadius < 0D) {
                return false;
            }
            double sphereX = values[node * 4];
            double sphereY = values[node * 4 + 1];
            double sphereZ = values[node * 4 + 2];
            minX = (int) Math.floor(sphereX - sphereRadius);
            maxX = Math.max((int) Math.floor(sphereX + sphereRadius), minX);
            minY = (int) Math.floor(sphereY - sphereRadius);
            int maxY = Math.max((int) Math.floor(sphereY + sphereRadius), minY);
            minZ = (int) Math.floor(sphereZ - sphereRadius);
            int maxZ = Math.max((int) Math.floor(sphereZ + sphereRadius), minZ);
            spanY = maxY - minY + 1;
            spanZ = maxZ - minZ + 1;
            if (spanY > squaredY.length) {
                squaredY = new double[spanY];
            }
            if (spanZ > squaredZ.length) {
                squaredZ = new double[spanZ];
            }
            for (int y = 0; y < spanY; y++) {
                double ny = (minY + y + 0.5D - sphereY) / sphereRadius;
                squaredY[y] = ny * ny;
            }
            for (int z = 0; z < spanZ; z++) {
                double nz = (minZ + z + 0.5D - sphereZ) / sphereRadius;
                squaredZ[z] = nz * nz;
            }
            centerX = sphereX;
            radius = sphereRadius;
            return true;
        }
    }

    private static final class FootprintCells implements CellSink {
        private static final int OFFSET = 32;
        private static final int AXIS = 64;
        private final long[] occupied = new long[AXIS * AXIS * AXIS / 64];
        private final int[] touched = new int[AXIS * AXIS * AXIS / 64];
        private final EllipsoidScratch scratch = new EllipsoidScratch();
        private int[] order = new int[256];
        private int[] picks = new int[256];
        private long[] sortKeys = new long[256];
        private int touchedCount;
        private int count;
        private int extentX;
        private int minY;
        private boolean uniform;
        private long stamp;

        private void reset() {
            for (int index = 0; index < touchedCount; index++) {
                occupied[touched[index]] = 0L;
            }
            touchedCount = 0;
            count = 0;
            extentX = 0;
            minY = Integer.MAX_VALUE;
            uniform = false;
            stamp++;
        }

        @Override
        public void add(int x, int y, int z) {
            int index = ((x + OFFSET) * AXIS + (y + OFFSET)) * AXIS + (z + OFFSET);
            int word = index >>> 6;
            long bit = 1L << (index & 63);
            long current = occupied[word];
            if ((current & bit) != 0L) {
                return;
            }
            if (current == 0L) {
                touched[touchedCount++] = word;
            }
            occupied[word] = current | bit;
            if (count == order.length) {
                order = Arrays.copyOf(order, count * 2);
                picks = Arrays.copyOf(picks, count * 2);
                sortKeys = new long[count * 2];
            }
            order[count++] = index;
            extentX = Math.max(extentX, Math.abs(x));
            minY = Math.min(minY, y);
        }

        private void addRow(int x, int y, long row) {
            int word = (x + OFFSET) * AXIS + (y + OFFSET);
            long current = occupied[word];
            if (current == 0L) {
                touched[touchedCount++] = word;
            }
            occupied[word] = current | row;
        }

        private void finishRows() {
            int cells = 0;
            int extent = 0;
            int lowest = Integer.MAX_VALUE;
            for (int index = 0; index < touchedCount; index++) {
                int word = touched[index];
                cells += Long.bitCount(occupied[word]);
                extent = Math.max(extent, Math.abs(word / AXIS - OFFSET));
                lowest = Math.min(lowest, word % AXIS - OFFSET);
            }
            count = cells;
            extentX = extent;
            minY = lowest;
            uniform = true;
        }

        private ClumpCells toCells(KList<NativeBlockState> palette) {
            if (uniform) {
                return toUniformCells(palette.get(0));
            }
            for (int cell = 0; cell < count; cell++) {
                sortKeys[cell] = ((long) order[cell] << 32) | picks[cell];
            }
            Arrays.sort(sortKeys, 0, count);
            int[] positions = new int[count];
            NativeBlockState[] blocks = new NativeBlockState[count];
            for (int cell = 0; cell < count; cell++) {
                long key = sortKeys[cell];
                positions[cell] = (int) (key >>> 32);
                blocks[cell] = palette.get((int) key);
            }
            return new ClumpCells(positions, blocks, null);
        }

        private ClumpCells toUniformCells(NativeBlockState block) {
            Arrays.sort(touched, 0, touchedCount);
            int[] positions = new int[count];
            int cell = 0;
            for (int index = 0; index < touchedCount; index++) {
                int word = touched[index];
                long row = occupied[word];
                while (row != 0L) {
                    positions[cell++] = (word << 6) | Long.numberOfTrailingZeros(row);
                    row &= row - 1L;
                }
            }
            return new ClumpCells(positions, null, block);
        }
    }
}
