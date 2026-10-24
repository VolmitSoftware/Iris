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

import art.arcane.iris.builtin.IrisSeed;
import art.arcane.iris.builtin.IrisSeeds;
import art.arcane.iris.builtin.deposit.DepositGenerator;
import art.arcane.iris.builtin.deposit.DepositHeightDistribution;
import art.arcane.iris.builtin.deposit.DepositPlacementScope;
import art.arcane.iris.builtin.deposit.DepositVariant;
import art.arcane.iris.builtin.deposit.DepositVariantSettings;
import art.arcane.iris.builtin.deposit.OreFrequencySettings;
import art.arcane.iris.builtin.deposit.OreSizeSettings;
import art.arcane.iris.engine.modifier.ResolvedDeposit.BiomeIdentity;
import art.arcane.volmlib.util.math.IrisBlockVector;
import art.arcane.volmlib.util.math.RNG;
import com.volmit.ModuleLog;
import com.volmit.block.BlockRegistry;
import com.volmit.block.BlockState;
import com.volmit.block.BlockTrait;
import com.volmit.config.FeatureSettings;
import com.volmit.config.Level;
import com.volmit.config.SettingsStack;
import com.volmit.hunk.Hunk;
import com.volmit.mantle.Mantle;
import com.volmit.resource.PackResources;
import com.volmit.resource.ResourceId;
import com.volmit.stage.ChunkStageContext;
import com.volmit.world.BiomeSample;
import com.volmit.world.PackWorld;
import com.volmit.world.TerrainSampler;

import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * Places deposit clumps into one chunk for {@link DepositModule}: one per world, holding the deposit stream's random and each
 * generator made ready for this world ({@link ResolvedDeposit}: resolved blocks and clump shapes). Each generator gets its own
 * random, derived from the chunk and its position in the list, and the generators of a chunk run one after the other.
 */
final class DepositPlacer {
    private static final double UNCHANGED_ORE_MULTIPLIER = 1D;
    private static final int CHUNK_WIDTH = 16;
    private static final long CHUNK_X_SEED_FACTOR = 341873128712L;
    private static final long CHUNK_Z_SEED_FACTOR = 132897987541L;
    private static final int DEFAULT_SURFACE_CLEARANCE = 7;
    private static final BlockState BEDROCK = new BlockState("minecraft:bedrock");

    private final RNG rng;
    private final BlockRegistry registry;
    private final PackResources resources;
    private final ModuleLog log;
    private final boolean tunesOres;
    private final boolean remapsOres;
    private final Map<DepositGenerator, ResolvedDeposit> resolved = new ConcurrentHashMap<>();
    private final Map<DepositVariant, ResolvedDepositVariant> variants = new ConcurrentHashMap<>();
    private final Map<ResourceId, Optional<BiomeIdentity>> biomeIdentities = new ConcurrentHashMap<>();

    DepositPlacer(PackWorld world) {
        this.rng = new RNG(world.extension(IrisSeeds.class).orElseThrow().seed(IrisSeed.DEPOSIT));
        this.resources = world.resources();
        this.registry = resources.blocks();
        this.log = world.log();
        this.tunesOres = declares(world, OreFrequencySettings.class) || declares(world, OreSizeSettings.class);
        this.remapsOres = declares(world, DepositVariantSettings.class);
    }

    /** The world's deposit placer, kept in its state store. */
    static DepositPlacer of(PackWorld world) {
        return world.state(DepositPlacer.class, DepositPlacer::new);
    }

    /** Whether any level of the pack declares {@code settings}; a world that declares none never asks a biome for them. */
    private static boolean declares(PackWorld world, Class<? extends com.volmit.IrisFeature> settings) {
        return !world.settings().declared(settings).isEmpty();
    }

    /** Runs {@code generators}, in order, on the chunk of {@code context}. */
    void generate(ChunkStageContext context, List<DepositGenerator> generators) {
        Placing placing = new Placing(context);
        long seed = context.chunkX() * CHUNK_X_SEED_FACTOR + context.chunkZ() * CHUNK_Z_SEED_FACTOR;
        long mask = 0;
        for (DepositGenerator generator : generators) {
            long finalSeed = seed * ++mask;
            placing.generate(generator, rng.nextParallelRNG(finalSeed));
        }
    }

    private ResolvedDeposit resolved(DepositGenerator generator) {
        return resolved.computeIfAbsent(generator, ignored -> new ResolvedDeposit(generator, rng.getSeed(), registry));
    }

    private Optional<BiomeIdentity> identityOf(ResourceId biome) {
        return biomeIdentities.computeIfAbsent(biome, id -> resources.find(PackResources.BIOMES, id)
                .map(found -> new BiomeIdentity(id.path(), found.platformBiome().toString(), found.vanillaBiome().toString())));
    }

    /** Places deposits into one chunk, with the views of its invocation. */
    private final class Placing {
        private final Hunk<BlockState> blocks;
        private final TerrainSampler terrain;
        private final FeatureSettings settings;
        private final Mantle mantle;
        private final int originX;
        private final int originZ;
        private final int worldHeight;
        private final int minWorldY;
        private final int[] surfaceHeights = new int[CHUNK_WIDTH * CHUNK_WIDTH];
        private boolean surfaceKnown;
        private final Map<BlockState, Boolean> airStates = new IdentityHashMap<>();
        private final Map<BlockState, Boolean> replaceableStates = new IdentityHashMap<>();
        private final Map<BlockState, Map<BlockState, BlockState>> suitedOres = new IdentityHashMap<>();

        Placing(ChunkStageContext context) {
            this.blocks = context.hunk(BlockState.class);
            this.terrain = context.terrain();
            this.settings = context.settings();
            this.mantle = context.mantle();
            this.originX = context.originX();
            this.originZ = context.originZ();
            this.worldHeight = blocks.height();
            this.minWorldY = terrain.minWorldY();
        }

        void generate(DepositGenerator k, RNG rng) {
            ResolvedDeposit deposit = resolved(k);
            if (k.spawnChance() < rng.d())
                return;

            for (int l = 0; l < rng.i(k.minPerChunk(), k.maxPerChunk() + 1); l++) {
                if (k.perClumpSpawnChance() < rng.d())
                    continue;

                DepositClump clump = deposit.clump(rng);

                int dim = clump.width();
                int min = dim / 2;
                int max = (int) (16D - dim / 2D);

                if (min > max || min < 0 || max > 15) {
                    min = 6;
                    max = 9;
                }

                int x = rng.i(min, max + 1);
                int z = rng.i(min, max + 1);
                int terrainSurface = terrainSurface(x, z);
                int height = k.placementScope() == DepositPlacementScope.TERRAIN
                        ? depositSurfaceLimit(terrainSurface, k.surfaceClearance())
                        : worldHeight - 1;

                if (height < 0) {
                    continue;
                }

                int y = sampleHeight(
                        k.heightDistribution(), rng, k.minHeight(), k.maxHeight(),
                        Math.min(height, worldHeight - 1));
                if (y == Integer.MIN_VALUE) {
                    continue;
                }

                boolean clippedHeight = k.heightDistribution() == DepositHeightDistribution.CLIPPED_UNIFORM;
                if (clippedHeight && y > height - 2)
                    continue;

                int biomeY = Math.max(0, Math.min(worldHeight - 1, y));
                boolean oreDeposit = deposit.isOre();
                Supplier<BiomeSample> biomes = biomesAt(x, biomeY, z);
                if (!deposit.matchesBiome(() -> identityOf(biomes.get().surfaceBiome()).orElse(null),
                        () -> biomes.get().caveBiome().flatMap(id -> identityOf(id)).orElse(null))) {
                    continue;
                }

                if (oreDeposit && tunesOres) {
                    Optional<ResourceId> caveBiome = biomes.get().caveBiome();
                    if (caveBiome.isPresent()) {
                        double frequencyMultiplier = oreFrequencyMultiplier(caveBiome.get());
                        if (frequencyMultiplier < 1D
                                && !passesOreFrequency(frequencyMultiplier, rng.d())) {
                            continue;
                        }

                        double sizeMultiplier = oreSizeMultiplier(caveBiome.get());
                        if (sizeMultiplier != 1D) {
                            DepositClump scaledClump = deposit.clump(rng, sizeMultiplier);
                            int scaledDimension = scaledClump.width();
                            x = clampDepositCenter(x, scaledDimension, 16);
                            if (clippedHeight) {
                                y = clampDepositCenter(y, scaledDimension, worldHeight);
                            }
                            z = clampDepositCenter(z, scaledDimension, 16);
                            clump = scaledClump;
                        }
                    }
                }

                placeClump(k, deposit, clump, rng, x, y, z);
            }
        }

        private void placeClump(DepositGenerator k, ResolvedDeposit deposit, DepositClump clump, RNG rng, int x, int y, int z) {
            for (IrisBlockVector j : clump.blocks().keys()) {
                int nx = j.getBlockX() + x;
                int ny = j.getBlockY() + y;
                int nz = j.getBlockZ() + z;

                if (nx > 15 || nx < 0 || ny >= worldHeight || ny < 0 || nz < 0 || nz > 15) {
                    continue;
                }
                int columnSurface = terrainSurface(nx, nz);
                if (!placementSurfaceAllows(
                        k.placementScope(), ny, columnSurface, k.surfaceClearance())) {
                    continue;
                }

                BlockState current = blocks.get(nx, ny, nz);
                if (!canReplaceTarget(current)) {
                    continue;
                }
                if (!deposit.canReplace(current)) {
                    continue;
                }
                if (!k.replaceBedrock() && current.material().equals(BEDROCK.material())) {
                    continue;
                }
                if (shouldDiscardExposed(
                        k.discardChanceOnAirExposure(), rng.d(), isAdjacentToAir(blocks, nx, ny, nz, this::isAir))) {
                    continue;
                }

                if (!mantle.isCarved(originX + nx, ny, originZ + nz)) {
                    BlockState ore = clump.blocks().get(j);
                    BlockState remapped = remapped(nx, ny, nz, ore);
                    BlockState finalBlock = remapped != null
                            ? remapped
                            : oreSuiting(current, ore);
                    blocks.set(nx, ny, nz, finalBlock);
                }
            }
        }

        /** Asks the host for the biomes of the column only if a filter or an ore tuning reads them, and only once. */
        private Supplier<BiomeSample> biomesAt(int localX, int y, int localZ) {
            BiomeSample[] sample = new BiomeSample[1];
            return () -> {
                if (sample[0] == null) {
                    sample[0] = terrain.biome(originX + localX, y, originZ + localZ);
                }
                return sample[0];
            };
        }

        /** {@code ore}, or the variant of it that suits the rock it replaces; the platform knows the variants, so each pair is asked once. */
        private BlockState oreSuiting(BlockState rock, BlockState ore) {
            return suitedOres.computeIfAbsent(rock, known -> new IdentityHashMap<>())
                    .computeIfAbsent(ore, known -> registry.oreFor(rock, known));
        }

        private boolean isAir(BlockState state) {
            return airStates.computeIfAbsent(state, known -> registry.hasTrait(known, BlockTrait.AIR));
        }

        private boolean canReplaceTarget(BlockState state) {
            return replaceableStates.computeIfAbsent(state, known -> canReplaceDepositTarget(registry, known));
        }

        /** The height of the terrain at a column of the chunk, as the stages before the deposits see it. */
        private int terrainSurface(int localX, int localZ) {
            if (!surfaceKnown) {
                for (int x = 0; x < CHUNK_WIDTH; x++) {
                    for (int z = 0; z < CHUNK_WIDTH; z++) {
                        surfaceHeights[x * CHUNK_WIDTH + z] = terrain.surfaceHeight(originX + x, originZ + z);
                    }
                }
                surfaceKnown = true;
            }
            return surfaceHeights[localX * CHUNK_WIDTH + localZ];
        }

        private BlockState remapped(int localX, int localY, int localZ, BlockState ore) {
            if (!remapsOres) {
                return null;
            }
            int worldX = originX + localX;
            int worldZ = originZ + localZ;
            int worldY = absoluteWorldY(minWorldY, localY);

            Optional<ResourceId> caveBiome = terrain.biome(worldX, localY, worldZ).caveBiome();
            if (caveBiome.isPresent()) {
                BlockState match = matchDepositVariant(settings.declaredBy(caveBiome.get(), DepositVariantSettings.class), ore, worldY);
                if (match != null) {
                    return match;
                }
            }

            SettingsStack<DepositVariantSettings> declared = settings.at(DepositVariantSettings.class, worldX, worldZ);
            BlockState match = matchDepositVariant(declared.level(Level.REGION), ore, worldY);
            if (match != null) {
                return match;
            }

            return matchDepositVariant(declared.dimension(), ore, worldY);
        }

        private BlockState matchDepositVariant(Optional<DepositVariantSettings> owner, BlockState ore, int y) {
            for (DepositVariant declared : owner.map(DepositVariantSettings::variants).orElse(List.of())) {
                ResolvedDepositVariant variant = variants.computeIfAbsent(declared, rule -> new ResolvedDepositVariant(rule, registry, log));
                if (!variant.appliesAt(y)) {
                    continue;
                }

                BlockState swapped = variant.remapOrNull(ore);
                if (swapped != null) {
                    return swapped;
                }
            }

            return null;
        }

        private double oreFrequencyMultiplier(ResourceId caveBiome) {
            return settings.declaredBy(caveBiome, OreFrequencySettings.class).map(OreFrequencySettings::multiplier).orElse(UNCHANGED_ORE_MULTIPLIER);
        }

        private double oreSizeMultiplier(ResourceId caveBiome) {
            return settings.declaredBy(caveBiome, OreSizeSettings.class).map(OreSizeSettings::multiplier).orElse(UNCHANGED_ORE_MULTIPLIER);
        }
    }

    static int depositSurfaceLimit(int surfaceY) {
        return depositSurfaceLimit(surfaceY, DEFAULT_SURFACE_CLEARANCE);
    }

    static int depositSurfaceLimit(int surfaceY, int surfaceClearance) {
        return surfaceY - Math.max(0, surfaceClearance);
    }

    static boolean placementSurfaceAllows(
            DepositPlacementScope scope, int candidateY, int surfaceY, int surfaceClearance) {
        int clearance = Math.max(0, surfaceClearance);
        return switch (scope) {
            case ABOVE_TERRAIN -> candidateY > surfaceY + clearance;
            case FULL_HEIGHT -> true;
            case TERRAIN -> candidateY <= surfaceY - clearance;
        };
    }

    static int absoluteWorldY(int minHeight, int localY) {
        return minHeight + localY;
    }

    static boolean canReplaceDepositTarget(BlockRegistry registry, BlockState state) {
        return state != null && !registry.hasTrait(state, BlockTrait.AIR) && !registry.hasTrait(state, BlockTrait.FLUID);
    }

    static int sampleHeight(
            DepositHeightDistribution distribution, RNG rng,
            int configuredMinimum, int configuredMaximum, int clippedMaximum) {
        int minimum = distribution == DepositHeightDistribution.CLIPPED_UNIFORM
                ? Math.max(0, configuredMinimum)
                : configuredMinimum;
        int maximum = distribution == DepositHeightDistribution.CLIPPED_UNIFORM
                ? Math.min(clippedMaximum, configuredMaximum)
                : configuredMaximum;
        if (minimum > maximum) {
            return Integer.MIN_VALUE;
        }
        if (minimum == maximum) {
            return minimum;
        }
        if (distribution != DepositHeightDistribution.TRIANGLE) {
            return minimum + rng.nextInt(maximum - minimum + 1);
        }

        int span = maximum - minimum;
        int lowerHalf = span / 2;
        int upperHalf = span - lowerHalf;
        return minimum + rng.nextInt(upperHalf + 1) + rng.nextInt(lowerHalf + 1);
    }

    static boolean shouldDiscardExposed(double chance, double sample, boolean adjacentToAir) {
        return adjacentToAir && chance > 0D && sample < Math.min(1D, chance);
    }

    static boolean isAdjacentToAir(Hunk<BlockState> data, int x, int y, int z, Predicate<BlockState> isAir) {
        return isAirAt(data, x - 1, y, z, isAir)
                || isAirAt(data, x + 1, y, z, isAir)
                || isAirAt(data, x, y - 1, z, isAir)
                || isAirAt(data, x, y + 1, z, isAir)
                || isAirAt(data, x, y, z - 1, isAir)
                || isAirAt(data, x, y, z + 1, isAir);
    }

    private static boolean isAirAt(Hunk<BlockState> data, int x, int y, int z, Predicate<BlockState> isAir) {
        if (x < 0 || x >= data.width()
                || y < 0 || y >= data.height()
                || z < 0 || z >= data.depth()) {
            return false;
        }
        return isAir.test(data.get(x, y, z));
    }

    static boolean passesOreFrequency(double multiplier, double sample) {
        return multiplier >= 1D || sample < Math.max(0D, multiplier);
    }

    static int clampDepositCenter(int center, int dimension, int limit) {
        int minimum = dimension / 2;
        int maximum = (int) (limit - dimension / 2D);
        return Math.max(minimum, Math.min(center, maximum));
    }
}
