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
import art.arcane.iris.generation.cache.AtomicCache;
import art.arcane.iris.generation.runtime.Engine;
import art.arcane.iris.pack.schema.annotation.ArrayType;
import art.arcane.volmlib.util.documentation.Description;
import art.arcane.iris.pack.schema.annotation.MaxNumber;
import art.arcane.iris.pack.schema.annotation.MinNumber;
import art.arcane.iris.pack.schema.annotation.Required;
import art.arcane.iris.pack.schema.annotation.Snippet;
import art.arcane.iris.spi.PlatformBlockState;
import art.arcane.iris.generation.block.B;
import art.arcane.iris.generation.geometry.IrisBlockVector;
import art.arcane.volmlib.util.math.Vector3i;
import art.arcane.volmlib.util.collection.KList;
import art.arcane.volmlib.util.collection.KSet;
import art.arcane.volmlib.util.math.BlockPosition;
import art.arcane.volmlib.util.math.RNG;
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
    private static final long FNV_OFFSET_BASIS = 0xcbf29ce484222325L;
    private static final long FNV_PRIME = 0x100000001b3L;

    private final transient ConcurrentMap<ClumpCacheKey, KList<IrisObject>> objects = new ConcurrentHashMap<>();
    private final transient AtomicCache<KList<PlatformBlockState>> blockData = new AtomicCache<>();
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
        KList<PlatformBlockState> resolvedPalette = getBlockData(rdata);
        hash = mix(hash, resolvedPalette.size());
        for (PlatformBlockState block : resolvedPalette) {
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

        float angle = rng.nextFloat() * (float) Math.PI;
        float reach = size / 8F;
        double startX = Math.sin(angle) * reach;
        double endX = -Math.sin(angle) * reach;
        double startZ = Math.cos(angle) * reach;
        double endZ = -Math.cos(angle) * reach;
        double startY = rng.nextInt(3) - 2;
        double endY = rng.nextInt(3) - 2;
        double[] nodes = new double[size * 4];

        for (int i = 0; i < size; i++) {
            float progress = (float) i / size;
            double radiusNoise = rng.nextDouble() * size / 16D;
            nodes[i * 4] = startX + (endX - startX) * progress;
            nodes[i * 4 + 1] = startY + (endY - startY) * progress;
            nodes[i * 4 + 2] = startZ + (endZ - startZ) * progress;
            nodes[i * 4 + 3] = ((Math.sin(Math.PI * progress) + 1D) * radiusNoise + 1D) / 2D;
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

        KSet<BlockPosition> cells = new KSet<>();
        for (int i = 0; i < size; i++) {
            double radius = nodes[i * 4 + 3];
            if (radius < 0D) {
                continue;
            }
            double centerX = nodes[i * 4];
            double centerY = nodes[i * 4 + 1];
            double centerZ = nodes[i * 4 + 2];
            int minX = (int) Math.floor(centerX - radius);
            int maxX = Math.max((int) Math.floor(centerX + radius), minX);
            int minY = (int) Math.floor(centerY - radius);
            int maxY = Math.max((int) Math.floor(centerY + radius), minY);
            int minZ = (int) Math.floor(centerZ - radius);
            int maxZ = Math.max((int) Math.floor(centerZ + radius), minZ);

            for (int x = minX; x <= maxX; x++) {
                double nx = (x + 0.5D - centerX) / radius;
                if (nx * nx >= 1D) {
                    continue;
                }
                for (int y = minY; y <= maxY; y++) {
                    double ny = (y + 0.5D - centerY) / radius;
                    if (nx * nx + ny * ny >= 1D) {
                        continue;
                    }
                    for (int z = minZ; z <= maxZ; z++) {
                        double nz = (z + 0.5D - centerZ) / radius;
                        if (nx * nx + ny * ny + nz * nz < 1D) {
                            cells.add(new BlockPosition(x, y, z));
                        }
                    }
                }
            }
        }

        return objectFromCells(cells, rng, rdata);
    }

    IrisObject generateVanillaScattered(RNG rng, IrisData rdata, int size) {
        KSet<BlockPosition> cells = new KSet<>();
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

    private IrisObject objectFromCells(KSet<BlockPosition> cells, RNG rng, IrisData rdata) {
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

    private PlatformBlockState nextBlock(RNG rngv, IrisData rdata) {
        return getBlockData(rdata).get(rngv.i(0, getBlockData(rdata).size()));
    }

    public KList<PlatformBlockState> getBlockData(IrisData rdata) {
        return blockData.aquire(() ->
        {
            KList<PlatformBlockState> blockData = new KList<>();

            for (IrisBlockData ix : palette) {
                PlatformBlockState bx = ix.getBlockData(rdata);

                if (bx != null) {
                    blockData.add(bx);
                }
            }

            return blockData;
        });
    }

    public boolean isOre(IrisData rdata) {
        return ore.aquire(() -> {
            for (PlatformBlockState block : getBlockData(rdata)) {
                if (block.isOre()) {
                    return true;
                }
            }

            return false;
        });
    }

    public boolean canReplace(PlatformBlockState state) {
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

    public boolean canReplaceSurface(PlatformBlockState state) {
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

    public boolean canReplaceSurface(PlatformBlockState state, IrisBiome surfaceBiome) {
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
            PlatformBlockState state = B.getStateOrNull(key, false);
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
}
