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

import art.arcane.iris.builtin.deposit.DepositBiomeScope;
import art.arcane.iris.builtin.deposit.DepositGenerator;
import art.arcane.iris.builtin.deposit.DepositShape;
import art.arcane.iris.engine.framework.Engine;
import art.arcane.iris.engine.object.prefab.IrisObject;
import art.arcane.iris.engine.object.procedural.IrisProceduralBlocks;
import art.arcane.iris.engine.object.world.biome.IrisBiome;
import art.arcane.iris.spi.PlatformBlockState;
import art.arcane.iris.util.common.data.B;
import art.arcane.volmlib.util.math.IrisBlockVector;
import art.arcane.volmlib.util.math.Vector3i;
import art.arcane.volmlib.util.collection.KSet;
import art.arcane.volmlib.util.math.BlockPosition;
import art.arcane.volmlib.util.math.RNG;
import com.volmit.block.BlockRegistry;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * One {@link DepositGenerator} made ready for one engine: its palette resolved to the platform's blocks, whether it is
 * an ore deposit, the blocks it may replace, and the clump shapes it places, built once per size. What the settings
 * describe stays in the settings; everything that needs the platform, the pack or the world seed is here.
 */
final class ResolvedDeposit {
    private static final long FNV_OFFSET_BASIS = 0xcbf29ce484222325L;
    private static final long FNV_PRIME = 0x100000001b3L;
    private static final int MAX_CLUMP_DIMENSION = 11;
    private static final int MAX_DEPOSIT_SIZE = 8192;
    private static final int VARIANT_SEED_STEP = 2349;
    private static final int VARIANT_SEED_OFFSET = 3598;
    private static final int MAX_SCATTER_MAGNITUDE = 7;
    private static final String MINECRAFT_NAMESPACE = "minecraft:";

    private final DepositGenerator generator;
    private final long depositSeed;
    private final List<PlatformBlockState> palette;
    private final boolean ore;
    private final Set<String> replaceableMaterials;
    private final Map<ClumpSizes, List<IrisObject>> clumps = new ConcurrentHashMap<>();

    /** @param palette the generator's palette as the platform's blocks, in order */
    ResolvedDeposit(DepositGenerator generator, long depositSeed, List<PlatformBlockState> palette) {
        this.generator = generator;
        this.depositSeed = depositSeed;
        this.palette = List.copyOf(palette);
        this.ore = palette.stream().anyMatch(PlatformBlockState::isOre);
        this.replaceableMaterials = replaceableMaterials(generator.replaceableBlocks());
    }

    /** {@code generator} ready for {@code engine}: its blocks resolved through the pack, clumps seeded from the world's deposit seed. */
    static ResolvedDeposit of(DepositGenerator generator, Engine engine) {
        BlockRegistry blocks = engine.getData().getPackResources().blocks();
        List<PlatformBlockState> palette = generator.palette().stream()
                .map(spec -> B.getState(blocks.resolve(spec).key()))
                .toList();
        return new ResolvedDeposit(generator, engine.getSeedManager().getDeposit(), palette);
    }

    DepositGenerator generator() {
        return generator;
    }

    /** Whether any block of the palette is an ore: ore deposits follow the cave biome's ore tuning. */
    boolean isOre() {
        return ore;
    }

    /** Whether the deposit may replace {@code state}: any block when it names none, otherwise only the named ones. */
    boolean canReplace(PlatformBlockState state) {
        return generator.replaceableBlocks().isEmpty() || replaceableMaterials.contains(IrisProceduralBlocks.materialKey(state));
    }

    /** Whether the biome the generator's scope picks passes its include and exclude filters. */
    boolean matchesBiome(IrisBiome surfaceBiome, IrisBiome caveBiome) {
        IrisBiome selected = generator.biomeScope() == DepositBiomeScope.SURFACE ? surfaceBiome : caveBiome;
        if (!generator.includedBiomes().isEmpty() && !matchesAnyBiome(selected, generator.includedBiomes())) {
            return false;
        }
        return generator.excludedBiomes().isEmpty() || !matchesAnyBiome(selected, generator.excludedBiomes());
    }

    /** One clump to place, picked with {@code rng}. */
    IrisObject clump(RNG rng) {
        return clump(rng, generator.minSize(), generator.maxSize(), 0L);
    }

    /** One clump to place with every size scaled by {@code sizeMultiplier}, as a biome's ore size tuning asks. */
    IrisObject clump(RNG rng, double sizeMultiplier) {
        if (sizeMultiplier == 1D) {
            return clump(rng);
        }
        int minSize = scaledDepositSize(generator.minSize(), sizeMultiplier);
        int maxSize = scaledDepositSize(generator.maxSize(), sizeMultiplier);
        long sizeSeed = ((long) minSize << 32) ^ (maxSize & 0xffffffffL);
        return clump(rng, minSize, maxSize, sizeSeed);
    }

    private IrisObject clump(RNG rng, int minSize, int maxSize, long sizeSeed) {
        if (generator.shape() != DepositShape.IRIS) {
            return configuredClump(rng, minSize, maxSize);
        }
        List<IrisObject> variants = clumps.computeIfAbsent(new ClumpSizes(minSize, maxSize), sizes -> {
            RNG variantRng = new RNG(depositSeed + stableClumpSalt() + sizeSeed);
            List<IrisObject> built = new ArrayList<>();
            for (int i = 0; i < generator.varience(); i++) {
                built.add(irisClump(variantRng.nextParallelRNG(VARIANT_SEED_STEP * i + VARIANT_SEED_OFFSET), sizes.minSize(), sizes.maxSize()));
            }
            return List.copyOf(built);
        });
        return variants.get(rng.i(0, variants.size()));
    }

    static int scaledDepositSize(int size, double multiplier) {
        return Math.max(0, Math.min(MAX_DEPOSIT_SIZE, (int) Math.round(size * multiplier)));
    }

    /** A hash of everything the generator describes and its resolved palette, so equal generators share clump shapes. */
    long stableClumpSalt() {
        long hash = FNV_OFFSET_BASIS;
        hash = mix(hash, generator.minHeight());
        hash = mix(hash, generator.maxHeight());
        hash = mixEnum(hash, generator.heightDistribution());
        hash = mixEnum(hash, generator.placementScope());
        hash = mix(hash, generator.surfaceClearance());
        hash = mix(hash, generator.minSize());
        hash = mix(hash, generator.maxSize());
        hash = mixEnum(hash, generator.shape());
        hash = mix(hash, generator.maxPerChunk());
        hash = mix(hash, generator.minPerChunk());
        hash = mix(hash, Double.doubleToLongBits(generator.spawnChance()));
        hash = mix(hash, Double.doubleToLongBits(generator.perClumpSpawnChance()));
        hash = mix(hash, Double.doubleToLongBits(generator.discardChanceOnAirExposure()));
        hash = mix(hash, palette.size());
        for (PlatformBlockState block : palette) {
            hash = mixString(hash, block == null ? null : block.key());
        }
        hash = mix(hash, generator.varience());
        hash = mixStrings(hash, generator.replaceableBlocks());
        hash = mixEnum(hash, generator.biomeScope());
        hash = mixStrings(hash, generator.includedBiomes());
        hash = mixStrings(hash, generator.excludedBiomes());
        return mix(hash, generator.replaceBedrock() ? 1L : 0L);
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

    private static long mixStrings(long hash, List<String> values) {
        long mixed = mix(hash, values.size());
        for (String value : values) {
            mixed = mixString(mixed, value);
        }
        return mixed;
    }

    private IrisObject configuredClump(RNG rng, int minSize, int maxSize) {
        int size = rng.i(minSize, maxSize + 1);
        return switch (generator.shape()) {
            case VANILLA_ELLIPSOID -> vanillaEllipsoid(rng, size);
            case VANILLA_SCATTERED -> vanillaScattered(rng, size);
            case IRIS -> irisClump(rng, minSize, maxSize);
        };
    }

    /** Minecraft's ordinary ore vein: a chain of overlapping ellipsoids whose reach grows with {@code size}. */
    IrisObject vanillaEllipsoid(RNG rng, int size) {
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

        return objectFromCells(cells, rng);
    }

    /** Minecraft's scattered ore: up to {@code size} candidate offsets, the later ones further from the centre. */
    IrisObject vanillaScattered(RNG rng, int size) {
        KSet<BlockPosition> cells = new KSet<>();
        int candidates = rng.nextInt(Math.max(0, size) + 1);
        for (int i = 0; i < candidates; i++) {
            int magnitude = Math.min(i, MAX_SCATTER_MAGNITUDE);
            cells.add(new BlockPosition(
                    Math.round((rng.nextFloat() - rng.nextFloat()) * magnitude),
                    Math.round((rng.nextFloat() - rng.nextFloat()) * magnitude),
                    Math.round((rng.nextFloat() - rng.nextFloat()) * magnitude)));
        }
        return objectFromCells(cells, rng);
    }

    private IrisObject objectFromCells(KSet<BlockPosition> cells, RNG rng) {
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
            object.setUnsigned(cell.getX() + extentX, cell.getY() + extentY, cell.getZ() + extentZ, nextBlock(rng));
        }
        return object;
    }

    /** The traditional Iris clump: a cube just large enough for the block count, filled at random cells. */
    private IrisObject irisClump(RNG rng, int minSize, int maxSize) {
        int remaining = rng.i(minSize, maxSize + 1);
        if (remaining == 1) {
            IrisObject single = new IrisObject(1, 1, 1);
            Vector3i center = single.getCenter();
            single.getBlocks().put(new IrisBlockVector(center.getX(), center.getY(), center.getZ()), nextBlock(rng));
            return single;
        }

        int dimension = Math.min(MAX_CLUMP_DIMENSION, (int) Math.ceil(Math.cbrt(remaining)));
        IrisObject clump = new IrisObject(dimension, dimension, dimension);
        if (remaining >= dimension * dimension * dimension) {
            fillCube(clump, dimension, rng);
            return clump;
        }

        KSet<BlockPosition> filled = new KSet<>();
        while (remaining > 0) {
            BlockPosition cell = new BlockPosition(rng.i(0, dimension), rng.i(0, dimension), rng.i(0, dimension));
            if (!filled.add(cell)) {
                continue;
            }
            remaining--;
            clump.setUnsigned(cell.getX(), cell.getY(), cell.getZ(), nextBlock(rng));
        }
        return clump;
    }

    private void fillCube(IrisObject clump, int dimension, RNG rng) {
        for (int z = 0; z < dimension; z++) {
            for (int y = 0; y < dimension; y++) {
                for (int x = 0; x < dimension; x++) {
                    clump.setUnsigned(x, y, z, nextBlock(rng));
                }
            }
        }
    }

    private PlatformBlockState nextBlock(RNG rng) {
        return palette.get(rng.i(0, palette.size()));
    }

    private static Set<String> replaceableMaterials(List<String> keys) {
        Set<String> materials = new KSet<>();
        for (String key : keys) {
            PlatformBlockState state = B.getStateOrNull(key, false);
            if (state != null) {
                materials.add(IrisProceduralBlocks.materialKey(state));
            }
        }
        return materials;
    }

    private static boolean matchesAnyBiome(IrisBiome biome, List<String> configuredBiomes) {
        if (biome == null) {
            return false;
        }
        for (String configured : configuredBiomes) {
            String namespaced = configured.contains(":") ? configured : MINECRAFT_NAMESPACE + configured;
            if (configured.equals(biome.getLoadKey())
                    || namespaced.equals(biome.getDerivativeKey())
                    || namespaced.equals(biome.getVanillaDerivativeKey())) {
                return true;
            }
        }
        return false;
    }

    /** The sizes one set of clump variants is built for: the generator's own, or scaled by a biome's ore tuning. */
    record ClumpSizes(int minSize, int maxSize) {
    }
}
