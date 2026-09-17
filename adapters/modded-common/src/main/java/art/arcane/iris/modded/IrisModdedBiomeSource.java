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

package art.arcane.iris.modded;

import art.arcane.iris.engine.object.world.biome.custom.IrisBiomeCustom;
import art.arcane.iris.engine.framework.Engine;
import art.arcane.iris.engine.framework.GenerationSessionException;
import art.arcane.iris.engine.framework.GenerationSessionLease;
import art.arcane.iris.engine.object.world.biome.IrisBiome;
import art.arcane.iris.util.project.context.IrisContext;
import art.arcane.volmlib.util.math.RNG;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.QuartPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureSet;

import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReferenceArray;
import java.util.function.Predicate;
import java.util.stream.Stream;

final class IrisModdedBiomeSource extends BiomeSource {
    private static final int UNRESOLVED_WARN_KEYS_MAX = 256;
    private static final int STRONGHOLD_RING_SEARCH_Y = 0;
    private static final int STRONGHOLD_RING_SEARCH_RADIUS = 112;
    private static final int STRONGHOLD_RING_SEARCH_QUART_STEP = 4;

    private final BiomeSource serializedSource;
    private final Set<String> warnedUnresolvedBiomeKeys = ConcurrentHashMap.newKeySet();
    private final Set<StructureStateBiomeSource> structureStateSources = ConcurrentHashMap.newKeySet();
    // Pack generation. Every cache on this source is keyed by it, which is what keeps memoized tables from
    // surviving a repoint with the previous pack's content.
    private final AtomicLong packGeneration = new AtomicLong();
    private volatile BiomeHolderTable visibleBiomeCache = new BiomeHolderTable();
    private volatile BiomeHolderTable structureBiomeCache = new BiomeHolderTable();
    private volatile BiomeHolderTable surfaceStructureBiomeCache = new BiomeHolderTable();
    private volatile IrisModdedChunkGenerator generator;
    private volatile Set<String> possibleStructureBiomeKeys;
    private volatile BiomeKeySets biomeKeySets;
    private volatile PossibleBiomes possibleBiomesCache;

    IrisModdedBiomeSource(BiomeSource serializedSource) {
        this.serializedSource = serializedSource;
    }

    void bind(IrisModdedChunkGenerator generator) {
        this.generator = generator;
    }

    void clearCaches() {
        // Republish empty tables instead of iterating: a repoint must not walk hundreds of thousands of slots
        // on the calling thread, and every reader is a pure function of the key so a lost entry is only a miss.
        // An empty table allocates no slot array, and a reader that captured the previous table writes its
        // in-flight value there, which is what keeps a pre-repoint holder from ever landing in the new table.
        packGeneration.incrementAndGet();
        visibleBiomeCache = new BiomeHolderTable();
        structureBiomeCache = new BiomeHolderTable();
        surfaceStructureBiomeCache = new BiomeHolderTable();
        warnedUnresolvedBiomeKeys.clear();
        possibleStructureBiomeKeys = null;
        biomeKeySets = null;
        possibleBiomesCache = null;
        for (StructureStateBiomeSource source : structureStateSources) {
            source.clearCache();
        }
    }

    /**
     * Pack generation counter. Platform code that memoizes anything derived from this source (the imported
     * feature table) keys its memo on this value so {@code repoint} cannot leave stale content behind.
     */
    long packGeneration() {
        return packGeneration.get();
    }

    BiomeSource forStructureState(HolderLookup<StructureSet> structureSets) {
        LinkedHashSet<Holder<Biome>> possible = new LinkedHashSet<>();
        Registry<Biome> registry = biomeRegistry();
        if (registry == null) {
            throw new IllegalStateException("Iris cannot create structure state without the biome registry");
        }
        Set<String> generatedBiomeKeys = requireConfiguredStructureBiomeKeys(exactStructureBiomeKeys());
        Set<String> missingBiomeKeys = new LinkedHashSet<>(generatedBiomeKeys);
        missingBiomeKeys.removeAll(registeredBiomeKeys(registry));
        if (!missingBiomeKeys.isEmpty()) {
            throw new IllegalStateException("Iris structure biomes are not registered: " + missingBiomeKeys);
        }
        structureSets.listElements().forEach((Holder.Reference<StructureSet> structureSet) -> {
            for (StructureSet.StructureSelectionEntry entry : structureSet.value().structures()) {
                for (Holder<Biome> biome : entry.structure().value().biomes()) {
                    String key = holderKey(biome);
                    if (isGeneratedBiomeKey(key, generatedBiomeKeys)) {
                        possible.add(biome);
                    }
                }
            }
        });
        StructureStateBiomeSource source = new StructureStateBiomeSource(this, Set.copyOf(possible));
        structureStateSources.add(source);
        return source;
    }

    @Override
    protected MapCodec<? extends BiomeSource> codec() {
        throw new UnsupportedOperationException("IrisModdedBiomeSource is serialized through IrisModdedChunkGenerator");
    }

    @Override
    protected Stream<Holder<Biome>> collectPossibleBiomes() {
        return resolvePossibleBiomes().ordered().stream();
    }

    /**
     * Overridden because {@link BiomeSource#possibleBiomes()} memoizes its answer for the lifetime of the
     * instance, and this source outlives a {@code repoint} to a different pack. The returned set keeps
     * biome-registry iteration order: vanilla builds its feature-per-step table with
     * {@code List.copyOf(possibleBiomes())}, and FeatureSorter's cycle detection walks that list, so an
     * unordered set makes cycle detection depend on JVM hash order.
     */
    @Override
    public Set<Holder<Biome>> possibleBiomes() {
        return resolvePossibleBiomes().set();
    }

    /**
     * Registry-ordered view of {@link #possibleBiomes()} for platform code that has to build a feature table.
     */
    List<Holder<Biome>> orderedPossibleBiomes() {
        return resolvePossibleBiomes().ordered();
    }

    /**
     * Resolves any registered biome holder by key, whether or not this source can emit it. The imported feature
     * pass needs this: a biome's vanilla derivative is where its features come from, and a sea or shore biome's
     * structure derivative is rewritten away from that derivative, so the derivative itself is not always one of
     * the biomes this source claims. Null when the key is unknown or the registry is not up yet.
     */
    Holder<Biome> registeredBiome(String key) {
        Registry<Biome> registry = biomeRegistry();
        return registry == null ? null : resolveHolder(registry, key);
    }

    /**
     * Deliberately lock-free: resolving can bind an engine, which takes the generator monitor, and the
     * generator takes its monitor before invalidating this cache. A lock here would close that cycle. Two
     * threads racing only duplicate idempotent work.
     */
    private PossibleBiomes resolvePossibleBiomes() {
        long generation = packGeneration.get();
        PossibleBiomes cached = possibleBiomesCache;
        if (cached != null && cached.generation() == generation) {
            return cached;
        }
        List<Holder<Biome>> ordered = collectPossibleBiomeHolders();
        PossibleBiomes resolved = new PossibleBiomes(generation, ordered,
                Collections.unmodifiableSet(new LinkedHashSet<>(ordered)));
        possibleBiomesCache = resolved;
        return resolved;
    }

    private List<Holder<Biome>> collectPossibleBiomeHolders() {
        BiomeKeySets keys = biomeKeySets();
        Set<String> generatedBiomeKeys = requireConfiguredStructureBiomeKeys(keys.required());
        Set<String> visibleBiomeKeys = keys.visibleOnly();
        Registry<Biome> registry = biomeRegistry();
        LinkedHashSet<Holder<Biome>> possible = new LinkedHashSet<>();
        if (registry == null) {
            for (Holder<Biome> biome : serializedSource.possibleBiomes()) {
                String key = holderKey(biome);
                if (isGeneratedBiomeKey(key, generatedBiomeKeys)
                        || isGeneratedBiomeKey(key, visibleBiomeKeys)) {
                    possible.add(biome);
                }
            }
        } else {
            Set<String> missingBiomeKeys = new LinkedHashSet<>(generatedBiomeKeys);
            missingBiomeKeys.removeAll(registeredBiomeKeys(registry));
            if (!missingBiomeKeys.isEmpty()) {
                throw new IllegalStateException("Iris structure biomes are not registered: "
                        + missingBiomeKeys);
            }
            // Registry-ordered, never a hash-ordered walk: see possibleBiomes().
            registry.listElements().forEach((Holder.Reference<Biome> reference) -> {
                String key = holderKey(reference);
                if (isGeneratedBiomeKey(key, generatedBiomeKeys)
                        || isGeneratedBiomeKey(key, visibleBiomeKeys)) {
                    possible.add(reference);
                }
            });
            warnUnregisteredVisibleBiomes(registry, visibleBiomeKeys);
        }
        if (possible.isEmpty()) {
            String phase = registry == null ? "serialized biome bootstrap" : "biome registry";
            throw new IllegalStateException("Iris configured structure biomes are absent from the "
                    + phase + ": " + generatedBiomeKeys);
        }
        return List.copyOf(possible);
    }

    /**
     * Scatter and derivative keys are advisory: a typo there must not stop a world from loading the way a
     * missing structure biome does, so they are reported once and skipped.
     */
    private void warnUnregisteredVisibleBiomes(Registry<Biome> registry, Set<String> visibleBiomeKeys) {
        if (visibleBiomeKeys.isEmpty()) {
            return;
        }
        Set<String> missing = new LinkedHashSet<>(visibleBiomeKeys);
        missing.removeAll(registeredBiomeKeys(registry));
        for (String key : missing) {
            if (!warnedUnresolvedBiomeKeys.add(key)) {
                continue;
            }
            if (warnedUnresolvedBiomeKeys.size() > UNRESOLVED_WARN_KEYS_MAX) {
                warnedUnresolvedBiomeKeys.clear();
            }
            ModdedIrisLog.warn("Iris biome " + key + " is referenced by derivative or scatter but is not"
                    + " registered; it is dropped from this dimension's biome source");
        }
    }

    @Override
    public Holder<Biome> getNoiseBiome(int quartX, int quartY, int quartZ, Climate.Sampler sampler) {
        Engine engine = engineOrNull();
        if (engine == null) {
            return serializedSource.getNoiseBiome(quartX, quartY, quartZ, sampler);
        }
        GenerationSessionLease lease = tryAcquireGenerationLease(engine, "modded_structure_biome");
        if (lease == null) {
            throw new IllegalStateException("Iris structure biome lookup was rejected during an engine transition");
        }
        try (lease; IrisContext.Scope ignored = IrisContext.open(engine, lease.sessionId(), null)) {
            if (!isReady(engine)) {
                throw new IllegalStateException("Iris structure biome lookup has no active engine runtime");
            }
            return getNoiseBiome(engine, quartX, quartY, quartZ, sampler);
        }
    }

    private Holder<Biome> getNoiseBiome(Engine engine, int quartX, int quartY, int quartZ,
                                        Climate.Sampler sampler) {
        if (isGuaranteedSurfaceBiome(quartY, engine.getMinHeight())) {
            return getSurfaceStructureBiome(engine, quartX, quartZ, sampler);
        }
        long key = packNoiseKey(quartX, quartY, quartZ);
        BiomeHolderTable cache = structureBiomeCache;
        Holder<Biome> cached = cache.get(key);
        if (cached != null) {
            return cached;
        }
        Holder<Biome> resolved = resolveStructureBiome(engine, quartX, quartY, quartZ, sampler);
        cache.put(key, resolved);
        return resolved;
    }

    Holder<Biome> getVisibleNoiseBiome(int quartX, int quartY, int quartZ, Climate.Sampler sampler) {
        Engine engine = engineOrNull();
        if (engine == null) {
            return serializedSource.getNoiseBiome(quartX, quartY, quartZ, sampler);
        }
        GenerationSessionLease lease = tryAcquireGenerationLease(engine, "modded_visible_biome");
        if (lease == null) {
            throw new IllegalStateException("Iris visible biome lookup was rejected during an engine transition");
        }
        try (lease; IrisContext.Scope ignored = IrisContext.open(engine, lease.sessionId(), null)) {
            if (!isReady(engine)) {
                throw new IllegalStateException("Iris visible biome lookup has no active engine runtime");
            }
            long key = packNoiseKey(quartX, quartY, quartZ);
            BiomeHolderTable cache = visibleBiomeCache;
            Holder<Biome> cached = cache.get(key);
            if (cached != null) {
                return cached;
            }
            Holder<Biome> resolved = resolveVisibleBiome(engine, quartX, quartY, quartZ, sampler);
            cache.put(key, resolved);
            return resolved;
        }
    }

    boolean isStructureReachable(Holder<Structure> structure) {
        Engine engine = engineOrNull();
        if (engine == null) {
            return isStructureReachable(structure, possibleStructureBiomeKeys());
        }
        GenerationSessionLease lease = tryAcquireGenerationLease(engine, "modded_structure_reachability");
        if (lease == null) {
            throw new IllegalStateException("Iris structure reachability was rejected during an engine transition");
        }
        try (lease; IrisContext.Scope ignored = IrisContext.open(engine, lease.sessionId(), null)) {
            return isStructureReachable(structure, possibleStructureBiomeKeys());
        }
    }

    private boolean isStructureReachable(Holder<Structure> structure, Set<String> possible) {
        for (Holder<Biome> biome : structure.value().biomes()) {
            String key = holderKey(biome);
            if (isGeneratedBiomeKey(key, possible)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public Set<Holder<Biome>> getBiomesWithin(int x, int y, int z, int radius, Climate.Sampler sampler) {
        int minQuartY = QuartPos.fromBlock(y - radius);
        Engine engine = engineOrNull();
        if (engine == null) {
            return super.getBiomesWithin(x, y, z, radius, sampler);
        }
        GenerationSessionLease lease = tryAcquireGenerationLease(engine, "modded_biomes_within");
        if (lease == null) {
            throw new IllegalStateException("Iris biome radius lookup was rejected during an engine transition");
        }
        try (lease; IrisContext.Scope ignored = IrisContext.open(engine, lease.sessionId(), null)) {
            if (!isReady(engine)) {
                throw new IllegalStateException("Iris biome radius lookup has no active engine runtime");
            }
            boolean monumentQuery = isMonumentSurfaceBiomeQuery(
                    y, radius, engine.getMinHeight(), engine.getDimension().getFluidHeight());
            if (!monumentQuery && !isGuaranteedSurfaceBiome(minQuartY, engine.getMinHeight())) {
                return super.getBiomesWithin(x, y, z, radius, sampler);
            }
            int minQuartX = QuartPos.fromBlock(x - radius);
            int maxQuartX = QuartPos.fromBlock(x + radius);
            int minQuartZ = QuartPos.fromBlock(z - radius);
            int maxQuartZ = QuartPos.fromBlock(z + radius);
            int columns = (maxQuartX - minQuartX + 1) * (maxQuartZ - minQuartZ + 1);
            Set<Holder<Biome>> biomes = new HashSet<>(columns);
            for (int quartZ = minQuartZ; quartZ <= maxQuartZ; quartZ++) {
                for (int quartX = minQuartX; quartX <= maxQuartX; quartX++) {
                    biomes.add(getSurfaceStructureBiome(engine, quartX, quartZ, sampler));
                }
            }
            return biomes;
        }
    }

    private Holder<Biome> getSurfaceStructureBiome(Engine engine, int quartX, int quartZ,
                                                   Climate.Sampler sampler) {
        long key = packColumnKey(quartX, quartZ);
        BiomeHolderTable cache = surfaceStructureBiomeCache;
        Holder<Biome> cached = cache.get(key);
        if (cached != null) {
            return cached;
        }
        Holder<Biome> resolved = resolveSurfaceStructureBiome(engine, quartX, quartZ, sampler);
        cache.put(key, resolved);
        return resolved;
    }

    private Holder<Biome> resolveSurfaceStructureBiome(Engine engine, int quartX, int quartZ,
                                                       Climate.Sampler sampler) {
        Registry<Biome> registry = biomeRegistry();
        if (!isReady(engine)) {
            throw new IllegalStateException("Iris structure biome lookup ran before engine binding");
        }
        if (registry == null) {
            throw new IllegalStateException("Iris structure biome lookup has no biome registry");
        }
        IrisBiome irisBiome = engine.getComplex().getTrueBiomeStream().get(quartX << 2, quartZ << 2);
        if (irisBiome == null) {
            throw new IllegalStateException("Iris returned no surface structure biome at quart "
                    + quartX + "," + quartZ);
        }
        Holder<Biome> resolved = resolveHolder(registry, irisBiome.getStructureDerivativeKey());
        if (resolved == null) {
            throw new IllegalStateException("Iris structure biome derivative '"
                    + irisBiome.getStructureDerivativeKey() + "' is not registered at quart "
                    + quartX + "," + quartZ);
        }
        return resolved;
    }

    private Holder<Biome> resolveStructureBiome(Engine engine, int quartX, int quartY, int quartZ,
                                                Climate.Sampler sampler) {
        Registry<Biome> registry = biomeRegistry();
        BiomeResolution resolution = resolveBiomeResolution(engine, quartX, quartY, quartZ);
        if (resolution == null) {
            throw new IllegalStateException("Iris returned no structure biome at quart "
                    + quartX + "," + quartY + "," + quartZ);
        }
        if (registry == null) {
            throw new IllegalStateException("Iris structure biome lookup has no biome registry");
        }
        Holder<Biome> resolved = resolveHolder(registry, resolution.irisBiome().getStructureDerivativeKey());
        if (resolved == null) {
            throw new IllegalStateException("Iris structure biome derivative '"
                    + resolution.irisBiome().getStructureDerivativeKey() + "' is not registered at block "
                    + resolution.blockX() + "," + resolution.blockY() + "," + resolution.blockZ());
        }
        return resolved;
    }

    private Holder<Biome> resolveVisibleBiome(Engine engine, int quartX, int quartY, int quartZ,
                                              Climate.Sampler sampler) {
        Registry<Biome> registry = biomeRegistry();
        BiomeResolution resolution = resolveBiomeResolution(engine, quartX, quartY, quartZ);
        if (resolution == null || registry == null) {
            return serializedSource.getNoiseBiome(quartX, quartY, quartZ, sampler);
        }
        String biomeKey;
        if (resolution.irisBiome().isCustom()) {
            IrisBiomeCustom customBiome = resolution.irisBiome().getCustomBiome(
                    resolution.rng(), engine, resolution.blockX(), resolution.blockY(), resolution.blockZ());
            if (customBiome == null) {
                return fallbackBiome(registry, "custom derivative of '"
                        + resolution.irisBiome().getLoadKey() + "'", quartX, quartY, quartZ, sampler);
            }
            biomeKey = ModdedWorldgenIds.biomeRef(engine, customBiome.getId());
        } else if (resolution.underground()) {
            biomeKey = resolution.irisBiome().getGroundBiomeKey(
                    resolution.rng(), engine, resolution.blockX(), resolution.blockY(), resolution.blockZ());
        } else {
            biomeKey = resolution.irisBiome().getSkyBiomeKey(
                    resolution.rng(), engine, resolution.blockX(), resolution.blockY(), resolution.blockZ());
        }
        Holder<Biome> resolved = resolveHolder(registry, biomeKey);
        return resolved == null
                ? fallbackBiome(registry, biomeKey, quartX, quartY, quartZ, sampler)
                : resolved;
    }

    private BiomeResolution resolveBiomeResolution(Engine engine, int quartX, int quartY, int quartZ) {
        if (!isReady(engine)) {
            return null;
        }
        int blockX = quartX << 2;
        int blockY = quartY << 2;
        int blockZ = quartZ << 2;
        int internalY = blockY - engine.getMinHeight();
        int caveSwitchY = Math.max(-8 - engine.getMinHeight(), 40);
        boolean underground = false;
        IrisBiome irisBiome;
        if (internalY <= caveSwitchY) {
            int surfaceY = engine.getComplex().getHeightStream().get(blockX, blockZ).intValue();
            underground = isUnderground(internalY, surfaceY);
            irisBiome = underground
                    ? engine.getCaveBiome(blockX, internalY, blockZ)
                    : engine.getComplex().getTrueBiomeStream().get(blockX, blockZ);
        } else {
            irisBiome = engine.getComplex().getTrueBiomeStream().get(blockX, blockZ);
        }
        if (irisBiome == null && underground) {
            irisBiome = engine.getComplex().getTrueBiomeStream().get(blockX, blockZ);
        }
        if (irisBiome == null) {
            return null;
        }
        IrisModdedChunkGenerator current = generator;
        long worldSeed = current == null ? engine.getWorld().getRawWorldSeed() : current.visibleBiomeSeed();
        long seed = biomeResolutionSeed(worldSeed, blockX, blockY, blockZ);
        return new BiomeResolution(irisBiome, underground, blockX, blockY, blockZ, new RNG(seed));
    }

    private Holder<Biome> resolveRequiredStructureBiome(int quartX, int quartY, int quartZ) {
        IrisModdedChunkGenerator current = generator;
        if (current == null) {
            throw new IllegalStateException("Iris structure biome source is not bound to its generator");
        }
        Engine engine = current.awaitStructureEngine();
        GenerationSessionLease lease = tryAcquireGenerationLease(engine, "modded_structure_state_biome");
        if (lease == null) {
            throw new IllegalStateException("Iris structure biome lookup was rejected during an engine transition");
        }
        try (lease; IrisContext.Scope ignored = IrisContext.open(engine, lease.sessionId(), null)) {
            if (!isReady(engine)) {
                throw new IllegalStateException("Iris structure biome lookup has no active engine runtime");
            }
            Registry<Biome> registry = biomeRegistry();
            if (registry == null) {
                throw new IllegalStateException("Iris structure biome lookup has no biome registry");
            }
            IrisBiome irisBiome;
            if (isGuaranteedSurfaceBiome(quartY, engine.getMinHeight())) {
                irisBiome = engine.getComplex().getTrueBiomeStream().get(quartX << 2, quartZ << 2);
            } else {
                BiomeResolution resolution = resolveBiomeResolution(engine, quartX, quartY, quartZ);
                irisBiome = resolution == null ? null : resolution.irisBiome();
            }
            if (irisBiome == null) {
                throw new IllegalStateException("Iris returned no structure biome at quart "
                        + quartX + "," + quartY + "," + quartZ);
            }
            String derivativeKey = irisBiome.getStructureDerivativeKey();
            Holder<Biome> resolved = resolveHolder(registry, derivativeKey);
            if (resolved == null) {
                throw new IllegalStateException("Iris structure biome derivative '" + derivativeKey
                        + "' is not registered at quart " + quartX + "," + quartY + "," + quartZ);
            }
            return resolved;
        }
    }

    static long biomeResolutionSeed(long worldSeed, int blockX, int blockY, int blockZ) {
        return worldSeed
                ^ ((long) blockX * 341873128712L)
                ^ ((long) blockY * 132897987541L)
                ^ ((long) blockZ * 42317861L);
    }

    static boolean isGeneratedBiomeKey(String key, Set<String> generatedBiomeKeys) {
        return key != null && generatedBiomeKeys.contains(key.toLowerCase(Locale.ROOT));
    }

    static Set<String> requireConfiguredStructureBiomeKeys(Set<String> configuredBiomeKeys) {
        if (configuredBiomeKeys == null || configuredBiomeKeys.isEmpty()) {
            throw new IllegalStateException("Iris has no configured structure biomes");
        }
        return configuredBiomeKeys;
    }

    static boolean isGuaranteedSurfaceBiome(int quartY, int minHeight) {
        int internalY = (quartY << 2) - minHeight;
        int caveSwitchY = Math.max(-8 - minHeight, 40);
        return internalY > caveSwitchY;
    }

    static boolean isUnderground(int internalY, int surfaceY) {
        return internalY <= surfaceY - 8;
    }

    static boolean isMonumentSurfaceBiomeQuery(int blockY, int radius, int minHeight, int fluidHeight) {
        return radius == 29 && blockY == minHeight + fluidHeight;
    }

    static int horizontalBiomeSearchQuartStep(int blockY, int searchRadius) {
        return blockY == STRONGHOLD_RING_SEARCH_Y && searchRadius == STRONGHOLD_RING_SEARCH_RADIUS
                ? STRONGHOLD_RING_SEARCH_QUART_STEP
                : 1;
    }

    private static boolean isReady(Engine engine) {
        return engine != null && !engine.isClosed() && engine.getComplex() != null;
    }

    private Engine engineOrNull() {
        IrisModdedChunkGenerator current = generator;
        return current == null ? null : current.structureEngineOrNull();
    }

    private GenerationSessionLease tryAcquireGenerationLease(Engine engine, String operation) {
        if (engine == null || engine.isClosed()) {
            return null;
        }
        try {
            return engine.acquireGenerationLease(operation);
        } catch (GenerationSessionException e) {
            if (engine.isClosing() || e.isExpectedTeardown()) {
                return null;
            }
            throw new IllegalStateException("Iris biome source could not acquire generation session for "
                    + operation + ".", e);
        }
    }

    private Set<String> possibleStructureBiomeKeys() {
        Set<String> cached = possibleStructureBiomeKeys;
        if (cached != null) {
            return cached;
        }
        Set<String> possible = requireConfiguredStructureBiomeKeys(exactStructureBiomeKeys());
        Registry<Biome> registry = biomeRegistry();
        if (registry == null) {
            throw new IllegalStateException("Iris cannot resolve structure biomes without the biome registry");
        }
        Set<String> missing = new LinkedHashSet<>(possible);
        missing.removeAll(registeredBiomeKeys(registry));
        if (!missing.isEmpty()) {
            throw new IllegalStateException("Iris structure biomes are not registered: " + missing);
        }
        Set<String> resolved = Set.copyOf(possible);
        possibleStructureBiomeKeys = resolved;
        return resolved;
    }

    private Set<String> exactStructureBiomeKeys() {
        return biomeKeySets().required();
    }

    /**
     * Required and visible-only biome keys for the current pack generation. Required keys are the structure
     * derivatives and the generated custom biomes: a missing one is fatal, exactly as before. Visible-only
     * keys are the raw derivative plus every {@code biomeScatter} and {@code biomeSkyScatter} entry - biomes
     * Iris writes into chunk sections but which vanilla previously dropped, because
     * {@code applyBiomeDecoration} intersects the chunk's biomes with {@code possibleBiomes()}.
     */
    private BiomeKeySets biomeKeySets() {
        long generation = packGeneration.get();
        BiomeKeySets cached = biomeKeySets;
        if (cached != null && cached.generation() == generation) {
            return cached;
        }
        BiomeKeySets resolved = collectBiomeKeySets(generation);
        biomeKeySets = resolved;
        return resolved;
    }

    private BiomeKeySets collectBiomeKeySets(long generation) {
        IrisModdedChunkGenerator current = generator;
        if (current == null) {
            return new BiomeKeySets(generation, Set.of(), Set.of());
        }
        Engine engine = current.structureEngineOrNull();
        if (engine == null) {
            return new BiomeKeySets(generation, current.configuredStructureBiomeKeys(), Set.of());
        }
        GenerationSessionLease lease = tryAcquireGenerationLease(engine, "modded_structure_biome_keys");
        if (lease == null) {
            throw new IllegalStateException("Iris structure biome key lookup was rejected during an engine transition");
        }
        try (lease; IrisContext.Scope ignored = IrisContext.open(engine, lease.sessionId(), null)) {
            if (!isReady(engine)) {
                throw new IllegalStateException("Iris structure biome key lookup has no active engine runtime");
            }
            LinkedHashSet<String> required = new LinkedHashSet<>();
            LinkedHashSet<String> visible = new LinkedHashSet<>();
            for (IrisBiome irisBiome : engine.getAllBiomes()) {
                String derivative = normalizeKey(irisBiome.getStructureDerivativeKey());
                if (derivative != null) {
                    required.add(derivative);
                }
                if (irisBiome.isCustom()) {
                    for (IrisBiomeCustom customBiome : irisBiome.getCustomDerivitives()) {
                        required.add(ModdedWorldgenIds.biomeRef(engine, customBiome.getId()));
                    }
                }
                addVisibleBiomeKey(visible, irisBiome.getDerivativeKey());
                for (String scatter : irisBiome.getBiomeScatter()) {
                    addVisibleBiomeKey(visible, scatter);
                }
                for (String scatter : irisBiome.getBiomeSkyScatter()) {
                    addVisibleBiomeKey(visible, scatter);
                }
            }
            visible.removeAll(required);
            return new BiomeKeySets(generation, Set.copyOf(required), Set.copyOf(visible));
        }
    }

    private static void addVisibleBiomeKey(Set<String> target, String key) {
        String normalized = normalizeKey(key);
        if (normalized != null) {
            target.add(normalized);
        }
    }

    private static Set<String> registeredBiomeKeys(Registry<Biome> registry) {
        Set<String> registered = new HashSet<>();
        registry.listElements().forEach((Holder.Reference<Biome> reference) -> {
            String key = holderKey(reference);
            if (key != null) {
                registered.add(key);
            }
        });
        return registered;
    }

    private Registry<Biome> biomeRegistry() {
        MinecraftServer server = ModdedEngineBootstrap.currentServer();
        return server == null ? null : server.registryAccess().lookupOrThrow(Registries.BIOME);
    }

    private static Holder<Biome> resolveHolder(Registry<Biome> registry, String key) {
        if (key == null || key.isBlank()) {
            return null;
        }
        Identifier identifier = Identifier.tryParse(key);
        if (identifier == null) {
            return null;
        }
        return registry.get(identifier).<Holder<Biome>>map((Holder.Reference<Biome> reference) -> reference).orElse(null);
    }

    private static String holderKey(Holder<Biome> holder) {
        return holder.unwrapKey()
                .map((ResourceKey<Biome> key) -> key.identifier().toString().toLowerCase(Locale.ROOT))
                .orElse(null);
    }

    private static String normalizeKey(String key) {
        Identifier identifier = key == null ? null : Identifier.tryParse(key);
        return identifier == null ? null : identifier.toString().toLowerCase(Locale.ROOT);
    }

    private Holder<Biome> fallbackBiome(Registry<Biome> registry, String unresolvedKey,
                                        int quartX, int quartY, int quartZ,
                                        Climate.Sampler sampler) {
        Holder<Biome> plains = resolveHolder(registry, "minecraft:plains");
        warnUnresolvedBiome(unresolvedKey, plains == null ? "the serialized biome source" : "minecraft:plains",
                quartX, quartY, quartZ);
        return plains == null ? serializedSource.getNoiseBiome(quartX, quartY, quartZ, sampler) : plains;
    }

    private void warnUnresolvedBiome(String unresolvedKey, String fallback,
                                     int quartX, int quartY, int quartZ) {
        String key = unresolvedKey == null || unresolvedKey.isBlank() ? "<blank>" : unresolvedKey;
        if (!warnedUnresolvedBiomeKeys.add(key)) {
            return;
        }
        if (warnedUnresolvedBiomeKeys.size() > UNRESOLVED_WARN_KEYS_MAX) {
            warnedUnresolvedBiomeKeys.clear();
        }
        ModdedIrisLog.warn("Iris biome " + key + " is not registered; using " + fallback
                + " at quart " + quartX + "," + quartY + "," + quartZ
                + " (wrong biome generates; regenerate the forced datapack and restart)");
    }

    private static long packNoiseKey(int x, int y, int z) {
        return (((long) x & 67108863L) << 38)
                | (((long) z & 67108863L) << 12)
                | ((long) y & 4095L);
    }

    private static long packColumnKey(int x, int z) {
        return ((long) x << 32) ^ ((long) z & 4294967295L);
    }

    private record BiomeResolution(IrisBiome irisBiome, boolean underground, int blockX, int blockY,
                                   int blockZ, RNG rng) {
    }

    private record BiomeKeySets(long generation, Set<String> required, Set<String> visibleOnly) {
    }

    private record PossibleBiomes(long generation, List<Holder<Biome>> ordered, Set<Holder<Biome>> set) {
    }

    /**
     * Fixed-capacity open-addressed long-to-holder cache. Sized once and never resized, so there is no
     * stop-the-world clear when it fills: a colliding write past the probe limit simply displaces the entry at
     * the home slot, and the displaced key resolves again on its next lookup. Every cached value is a pure
     * function of its key, so displacement can only cost work, never correctness. Entries are published as one
     * immutable record, which is what keeps a concurrent writer from ever pairing one key with another's value.
     *
     * <p>The slot array is installed on the first put, never in the field initializer. One biome source is
     * constructed per emitted world preset at datapack load, and every create-world screen builds them all, so
     * eager arrays cost tens of megabytes of tables that nothing ever reads. An empty table answers every get
     * with a miss, which is the same answer a cold table gives.
     */
    private static final class BiomeHolderTable {
        private static final int SLOTS = 32768;
        private static final int MASK = SLOTS - 1;
        private static final int PROBE_LIMIT = 8;

        private volatile AtomicReferenceArray<Entry> table;

        Holder<Biome> get(long key) {
            AtomicReferenceArray<Entry> slots = table;
            if (slots == null) {
                return null;
            }
            int home = home(key);
            for (int probe = 0; probe < PROBE_LIMIT; probe++) {
                Entry entry = slots.get((home + probe) & MASK);
                if (entry == null) {
                    return null;
                }
                if (entry.key() == key) {
                    return entry.value();
                }
            }
            return null;
        }

        void put(long key, Holder<Biome> value) {
            AtomicReferenceArray<Entry> slots = table;
            if (slots == null) {
                slots = install();
            }
            int home = home(key);
            Entry entry = new Entry(key, value);
            for (int probe = 0; probe < PROBE_LIMIT; probe++) {
                int slot = (home + probe) & MASK;
                Entry existing = slots.get(slot);
                if (existing == null || existing.key() == key) {
                    slots.set(slot, entry);
                    return;
                }
            }
            slots.set(home, entry);
        }

        private synchronized AtomicReferenceArray<Entry> install() {
            AtomicReferenceArray<Entry> existing = table;
            if (existing != null) {
                return existing;
            }
            AtomicReferenceArray<Entry> created = new AtomicReferenceArray<>(SLOTS);
            table = created;
            return created;
        }

        private static int home(long key) {
            long mixed = key * 0x9E3779B97F4A7C15L;
            return (int) ((mixed ^ (mixed >>> 32)) & MASK);
        }

        private record Entry(long key, Holder<Biome> value) {
        }
    }

    private static final class StructureStateBiomeSource extends BiomeSource {
        private final IrisModdedBiomeSource delegate;
        private final Set<Holder<Biome>> possibleBiomes;
        private volatile BiomeHolderTable resolvedBiomes = new BiomeHolderTable();

        private StructureStateBiomeSource(IrisModdedBiomeSource delegate, Set<Holder<Biome>> possibleBiomes) {
            this.delegate = delegate;
            this.possibleBiomes = possibleBiomes;
        }

        private void clearCache() {
            resolvedBiomes = new BiomeHolderTable();
        }

        @Override
        protected MapCodec<? extends BiomeSource> codec() {
            throw new UnsupportedOperationException("Structure state biome sources are not serializable");
        }

        @Override
        protected Stream<Holder<Biome>> collectPossibleBiomes() {
            return possibleBiomes.stream();
        }

        @Override
        public Holder<Biome> getNoiseBiome(int x, int y, int z, Climate.Sampler sampler) {
            long key = packNoiseKey(x, y, z);
            BiomeHolderTable cache = resolvedBiomes;
            Holder<Biome> cached = cache.get(key);
            if (cached != null) {
                return cached;
            }
            Holder<Biome> resolved = delegate.resolveRequiredStructureBiome(x, y, z);
            cache.put(key, resolved);
            return resolved;
        }

        @Override
        public Pair<BlockPos, Holder<Biome>> findBiomeHorizontal(
                int x,
                int y,
                int z,
                int searchRadius,
                Predicate<Holder<Biome>> allowed,
                RandomSource random,
                Climate.Sampler sampler
        ) {
            int quartStep = horizontalBiomeSearchQuartStep(y, searchRadius);
            if (quartStep == 1) {
                return super.findBiomeHorizontal(x, y, z, searchRadius, allowed, random, sampler);
            }
            return super.findBiomeHorizontal(
                    x, y, z, searchRadius, quartStep, allowed, random, false, sampler);
        }

        @Override
        public Set<Holder<Biome>> getBiomesWithin(int x, int y, int z, int radius, Climate.Sampler sampler) {
            return super.getBiomesWithin(x, y, z, radius, sampler);
        }
    }
}
