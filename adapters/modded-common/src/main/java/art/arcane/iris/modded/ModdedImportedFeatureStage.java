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

import art.arcane.iris.engine.DimensionStackContext;
import art.arcane.iris.engine.DimensionStackLayout;
import art.arcane.iris.nativegen.NativeGenerationWriteGuard;
import art.arcane.iris.engine.framework.Engine;
import art.arcane.iris.engine.framework.GenerationSessionLease;
import art.arcane.iris.engine.framework.NativeFeatureGenerationPolicy;
import art.arcane.iris.engine.object.IrisBiome;
import art.arcane.iris.engine.object.IrisBiomeCustom;
import art.arcane.iris.engine.object.IrisDecorationStep;
import art.arcane.iris.engine.object.IrisImportedFeatureControl;
import art.arcane.iris.engine.object.IrisStaticObjectLayer;
import art.arcane.iris.util.project.context.IrisContext;
import it.unimi.dsi.fastutil.ints.IntArraySet;
import it.unimi.dsi.fastutil.ints.IntSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.Registry;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeGenerationSettings;
import net.minecraft.world.level.biome.FeatureSorter;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.levelgen.RandomSupport;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.WorldgenRandom;
import net.minecraft.world.level.levelgen.XoroshiroRandomSource;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.IntBinaryOperator;

/**
 * Native placed-feature passthrough for one Iris dimension, gated on {@code importedFeatures.enabled}.
 *
 * <p>This runs the FEATURES half of vanilla's decoration pass and nothing else. Iris places native structures
 * itself, with its own vertical fitting and vegetation clearing, so calling {@code super.applyBiomeDecoration}
 * would place every structure a second time. The feature half is reproduced here off the same decoration and
 * feature seeds vanilla derives, so an imported feature lands where vanilla would have put it.
 *
 * <p>Threading: {@link #run} runs on the worldgen thread that is generating the chunk, never on
 * {@link ModdedGenPool}. The FEATURES chunk step is not parallel-safe - it writes into the eight neighbouring
 * chunks through {@code WorldGenLevel}, and vanilla and every threaded chunk system serialize it. Terrain is
 * the only Iris step that may fan out.
 *
 * <p>Everything here is inert while the control is disabled: no table is built, no registry is walked, and
 * {@link #generationSettings} answers exactly what vanilla's default getter answers.
 */
final class ModdedImportedFeatureStage {
    private static final String CYCLE_MARKER = "Feature order cycle found";
    private final IrisModdedBiomeSource biomeSource;
    private final ReentrantLock buildLock = new ReentrantLock();
    private final ConcurrentHashMap<Integer, FeatureTable> featureTables = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Integer, Long> inertGenerations = new ConcurrentHashMap<>();
    private volatile IrisModdedChunkGenerator generator;

    ModdedImportedFeatureStage(IrisModdedBiomeSource biomeSource) {
        this.biomeSource = biomeSource;
    }

    void bind(IrisModdedChunkGenerator generator) {
        this.generator = generator;
    }

    /**
     * Drops the feature table. Called from every repoint, hotload and unbind path so a table built for one
     * pack can never serve another.
     */
    void invalidate() {
        featureTables.clear();
        inertGenerations.clear();
    }

    boolean active() {
        return !featureTables.isEmpty();
    }

    /**
     * The generation-settings getter handed to {@code ChunkGenerator}'s two-argument constructor. Maps an Iris
     * custom biome holder onto the generation settings of the vanilla biome its Iris biome derives from, so
     * the per-step feature lists and {@code BiomeFilter}'s hasFeature gate both see real features for a biome
     * whose datapack JSON declares none by design. Real registry biomes pass straight through.
     *
     * <p>With {@code importedFeatures} disabled there is no table and this is vanilla's default getter.
     */
    BiomeGenerationSettings generationSettings(Holder<Biome> biome) {
        Engine engine = generator == null ? null : generator.engineOrNull();
        FeatureTable table = engine == null ? null : featureTables.get(engine.getCacheID());
        if (table == null) {
            return biome.value().getGenerationSettings();
        }
        return settingsFor(biome, table.derivatives());
    }

    /**
     * Chunk-path prepare. The volatile fast path is unlocked, so a prepared stage costs two reads per chunk;
     * the build itself is serialized because {@code applyBiomeDecoration} calls this from every worldgen
     * thread, and two threads that both found the stage unprepared would each run {@code FeatureSorter}, whose
     * cycle detection is the expensive part. Waiting here is safe: this caller holds no generator monitor.
     */
    void prepare(Engine engine) {
        prepare(engine, true);
    }

    /**
     * Bind and repoint prepare, which is what makes a feature-order cycle a single bind-time ERROR instead of
     * a chunk-generation crash. Those callers hold the generator monitor and the build path can need it (the
     * biome source may bind an engine while resolving), so this one never waits for another thread's build: it
     * builds now or leaves it to the next chunk's prepare.
     */
    void prepareWithoutWaiting(Engine engine) {
        prepare(engine, false);
    }

    private void prepare(Engine engine, boolean waitForBuild) {
        if (engine == null || engine.isClosed() || engine.isClosing()) {
            return;
        }
        int runtimeIdentity = engine.getCacheID();
        long generation = biomeSource.packGeneration();
        if (settled(runtimeIdentity, generation)) {
            return;
        }
        if (waitForBuild) {
            buildLock.lock();
        } else if (!buildLock.tryLock()) {
            return;
        }
        try {
            if (settled(runtimeIdentity, generation)) {
                return;
            }
            build(engine, runtimeIdentity, generation);
        } finally {
            buildLock.unlock();
        }
    }

    private boolean settled(int runtimeIdentity, long generation) {
        FeatureTable current = featureTables.get(runtimeIdentity);
        if (current != null && current.generation() == generation) {
            return true;
        }
        return Long.valueOf(generation).equals(inertGenerations.get(runtimeIdentity));
    }

    private void build(Engine engine, int runtimeIdentity, long generation) {
        IrisImportedFeatureControl control;
        try {
            control = NativeFeatureGenerationPolicy.control(engine);
        } catch (RuntimeException error) {
            ModdedIrisLog.error("Iris could not read importedFeatures for this dimension; features off: {}",
                    error.toString());
            markInert(runtimeIdentity, generation);
            return;
        }
        if (!control.shouldGenerateFeatures()) {
            markInert(runtimeIdentity, generation);
            return;
        }
        FeatureTable built;
        try (GenerationSessionLease lease = engine.acquireGenerationLease("modded_imported_features");
             IrisContext.Scope ignored = IrisContext.open(engine, lease.sessionId(), null)) {
            built = buildTable(engine, control, generation);
        } catch (Throwable error) {
            ModdedIrisLog.error("Iris importedFeatures is off for {}: feature table construction failed: {}",
                    dimensionKey(engine), error.toString());
            markInert(runtimeIdentity, generation);
            return;
        }
        if (built == null) {
            markInert(runtimeIdentity, generation);
            return;
        }
        featureTables.put(runtimeIdentity, built);
        inertGenerations.remove(runtimeIdentity);
        // Arm the worldcheck log watch here, before any chunk decorates: arming from the first pass instead
        // missed every far-chunk write the first chunk made. No-op unless -Diris.worldcheck is set.
        WorldCheckFeaturePlacement.arm();
        ModdedIrisLog.info("Iris importedFeatures on for {}: {} biomes, {} steps, {} custom-biome derivative maps",
                dimensionKey(engine), built.biomes().size(), built.steps().size(),
                built.derivatives().size());
    }

    private void markInert(int runtimeIdentity, long generation) {
        featureTables.remove(runtimeIdentity);
        inertGenerations.put(runtimeIdentity, generation);
    }

    private FeatureTable buildTable(Engine engine, IrisImportedFeatureControl control, long generation) {
        // Registry-ordered biome list. FeatureSorter's cycle detection walks it, so an unordered list makes
        // detection depend on JVM hash order and turns a real cycle into an intermittent one.
        Set<String> hostBiomeKeys = hostBiomeKeys(engine);
        List<Holder<Biome>> biomes = new ArrayList<>();
        for (Holder<Biome> biome : biomeSource.orderedPossibleBiomes()) {
            String key = holderKey(biome);
            if (key != null && hostBiomeKeys.contains(key)) {
                biomes.add(biome);
            }
        }
        if (biomes.isEmpty()) {
            ModdedIrisLog.error("Iris importedFeatures is on but {} exposes no biomes; features off",
                    dimensionKey(engine));
            return null;
        }
        Map<String, Holder<Biome>> byKey = new HashMap<>(biomes.size());
        for (Holder<Biome> biome : biomes) {
            String key = holderKey(biome);
            if (key != null) {
                byKey.put(key, biome);
            }
        }
        Map<String, Holder<Biome>> derivatives = customBiomeDerivatives(engine, byKey);
        List<FeatureSorter.StepFeatureData> steps;
        try {
            // Same inputs as vanilla's own memo, built here so it can be keyed on the Iris pack generation and
            // so the cycle failure lands at bind time.
            steps = FeatureSorter.buildFeaturesPerStep(biomes,
                    (Holder<Biome> biome) -> settingsFor(biome, derivatives).features(), true);
        } catch (IllegalStateException error) {
            String message = error.getMessage();
            if (message == null || !message.contains(CYCLE_MARKER)) {
                throw error;
            }
            ModdedIrisLog.error("Iris importedFeatures is off for {}: the registered placed features cannot be ordered."
                            + " {}. Remove or reorder the conflicting content, or leave"
                            + " importedFeatures.enabled false.",
                    dimensionKey(engine), message);
            return null;
        }
        boolean filtered = control.getDisabled() != null && !control.getDisabled().isEmpty();
        return new FeatureTable(generation, control, List.copyOf(biomes), Set.copyOf(biomes),
                Map.copyOf(byKey), steps, Map.copyOf(derivatives), filtered);
    }

    /**
     * Maps every generated Iris custom biome key onto the registry holder of its Iris biome's vanilla
     * derivative. The custom biome's own datapack JSON carries no features by design; this is the only place
     * the vanilla feature set enters.
     */
    private Map<String, Holder<Biome>> customBiomeDerivatives(Engine engine, Map<String, Holder<Biome>> byKey) {
        Map<String, Holder<Biome>> derivatives = new HashMap<>();
        for (IrisBiome irisBiome : engine.getDimension().getReachableBiomes(engine)) {
            if (!irisBiome.isCustom()) {
                continue;
            }
            String derivativeKey = normalizeKey(irisBiome.getVanillaDerivativeKey());
            // Resolve from the registry, not only from this source's own biome set: a sea or shore biome's
            // structure derivative is rewritten away from its vanilla derivative, so the derivative whose
            // features we want is not always a biome this source can emit.
            Holder<Biome> derivative = byKey.get(derivativeKey);
            if (derivative == null) {
                derivative = biomeSource.registeredBiome(derivativeKey);
            }
            if (derivative == null) {
                ModdedIrisLog.warn("Iris importedFeatures: vanilla derivative {} of biome {} is not registered;"
                        + " its custom biomes generate no imported features",
                        derivativeKey, irisBiome.getLoadKey());
                continue;
            }
            for (IrisBiomeCustom customBiome : irisBiome.getCustomDerivitives()) {
                derivatives.put(ModdedWorldgenIds.biomeRef(engine, customBiome.getId()), derivative);
            }
        }
        return derivatives;
    }

    private static BiomeGenerationSettings settingsFor(Holder<Biome> biome,
                                                       Map<String, Holder<Biome>> derivatives) {
        Holder<Biome> mapped = derivatives.get(holderKey(biome));
        return mapped == null
                ? biome.value().getGenerationSettings()
                : mapped.value().getGenerationSettings();
    }

    /**
     * Runs the vanilla placed-feature pass for one chunk, on the calling worldgen thread. A no-op while the
     * control is disabled or the table degraded.
     */
    void run(WorldGenLevel level, ChunkAccess chunk, Engine engine) {
        int runtimeIdentity = engine.getCacheID();
        FeatureTable table = featureTables.get(runtimeIdentity);
        if (table == null) {
            WorldCheckFeaturePlacement.recordFeaturesOff();
            return;
        }
        if (table.generation() != biomeSource.packGeneration()) {
            // A repoint landed between the prepare above and this chunk. Refuse stale content outright; the
            // next chunk's prepare rebuilds against the new pack.
            featureTables.remove(runtimeIdentity, table);
            inertGenerations.remove(runtimeIdentity);
            return;
        }
        IrisModdedChunkGenerator owner = generator;
        if (owner == null) {
            return;
        }
        ChunkPos centerPos = chunk.getPos();
        SectionPos sectionPos = SectionPos.of(centerPos, level.getMinSectionY());
        BlockPos origin = sectionPos.origin();
        Registry<PlacedFeature> featureRegistry = level.registryAccess().lookupOrThrow(Registries.PLACED_FEATURE);
        List<FeatureSorter.StepFeatureData> steps = table.steps();
        WorldgenRandom random = new WorldgenRandom(new XoroshiroRandomSource(RandomSupport.generateUniqueSeed()));
        long decorationSeed = random.setDecorationSeed(level.getSeed(), origin.getX(), origin.getZ());
        IrisStaticObjectLayer staticObjects = engine.getDimension().getStaticObjectLayer(engine.getData());
        int staticMinY = engine.getMinHeight();
        DimensionStackContext stackContext = engine.getDimensionStackContext();
        Set<Holder<Biome>> chunkBiomes = chunkBiomes(level, sectionPos, table, stackContext);
        IntBinaryOperator surfaceFirstFreeY = stackContext == null
                ? (x, z) -> level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, x, z)
                : (x, z) -> Engine.hostHeight(engine, x, z, false) + staticMinY + 1;
        IntBinaryOperator floorFirstFreeY = stackContext == null
                ? (x, z) -> level.getHeight(Heightmap.Types.OCEAN_FLOOR_WG, x, z)
                : (x, z) -> Engine.hostHeight(engine, x, z, true) + staticMinY + 1;
        WorldGenLevel placementLevel = staticObjects.isEmpty() && stackContext == null
                ? level
                : ModdedNativeStructureWorldgenAccess.create(
                level, centerPos,
                surfaceFirstFreeY,
                floorFirstFreeY,
                stackContext != null,
                NativeGenerationWriteGuard.protectedPositions(staticObjects, stackContext, staticMinY));

        try {
            for (int stepIndex = 0; stepIndex < steps.size(); stepIndex++) {
                if (!table.control().shouldGenerateStep(IrisDecorationStep.byOrdinal(stepIndex))) {
                    continue;
                }
                placeStep(placementLevel, table, steps.get(stepIndex), featureRegistry, chunkBiomes, owner,
                        random, decorationSeed, origin, stepIndex);
            }
        } catch (Throwable error) {
            WorldCheckFeaturePlacement.recordPlacementFailure(centerPos, error);
            throw new IllegalStateException("Iris imported feature placement failed for chunk "
                    + centerPos.x() + "," + centerPos.z(), error);
        } finally {
            level.setCurrentlyGenerating(null);
        }
        WorldCheckFeaturePlacement.recordPlacementPass();
    }

    private static Set<String> hostBiomeKeys(Engine engine) {
        Set<String> keys = new LinkedHashSet<>();
        for (IrisBiome irisBiome : engine.getDimension().getReachableBiomes(engine)) {
            addKey(keys, irisBiome.getStructureDerivativeKey());
            addKey(keys, irisBiome.getDerivativeKey());
            for (String scatter : irisBiome.getBiomeScatter()) {
                addKey(keys, scatter);
            }
            for (String scatter : irisBiome.getBiomeSkyScatter()) {
                addKey(keys, scatter);
            }
            if (!irisBiome.isCustom()) {
                continue;
            }
            for (IrisBiomeCustom customBiome : irisBiome.getCustomDerivitives()) {
                addKey(keys, ModdedWorldgenIds.biomeRef(engine, customBiome.getId()));
            }
        }
        return keys;
    }

    private static void addKey(Set<String> keys, String key) {
        String normalized = normalizeKey(key);
        if (normalized != null) {
            keys.add(normalized);
        }
    }

    void evictRuntime(int runtimeIdentity) {
        featureTables.remove(runtimeIdentity);
        inertGenerations.remove(runtimeIdentity);
    }

    private void placeStep(WorldGenLevel level, FeatureTable table, FeatureSorter.StepFeatureData stepData,
                           Registry<PlacedFeature> featureRegistry, Set<Holder<Biome>> chunkBiomes,
                           IrisModdedChunkGenerator owner, WorldgenRandom random, long decorationSeed,
                           BlockPos origin, int stepIndex) {
        IntSet stepFeatures = new IntArraySet();
        for (Holder<Biome> biome : chunkBiomes) {
            List<HolderSet<PlacedFeature>> biomeFeatures = settingsFor(biome, table.derivatives()).features();
            if (stepIndex >= biomeFeatures.size()) {
                continue;
            }
            for (Holder<PlacedFeature> feature : biomeFeatures.get(stepIndex)) {
                stepFeatures.add(stepData.indexMapping().applyAsInt(feature.value()));
            }
        }
        if (stepFeatures.isEmpty()) {
            return;
        }
        // Sorted global indices: identical ordering to vanilla, and each feature's seed comes from its own
        // global index, so denying one feature never shifts another.
        int[] featureIndices = stepFeatures.toIntArray();
        Arrays.sort(featureIndices);
        for (int globalIndex : featureIndices) {
            PlacedFeature feature = stepData.features().get(globalIndex);
            if (table.filtered()) {
                Identifier featureId = featureRegistry.getKey(feature);
                if (featureId != null && !table.control().shouldGenerate(featureId.toString())) {
                    continue;
                }
            }
            random.setFeatureSeed(decorationSeed, globalIndex, stepIndex);
            level.setCurrentlyGenerating(() -> describeFeature(featureRegistry, feature));
            feature.placeWithBiomeCheck(level, owner, random, origin);
        }
    }

    private static String describeFeature(Registry<PlacedFeature> registry, PlacedFeature feature) {
        Identifier id = registry.getKey(feature);
        return id == null ? feature.toString() : id.toString();
    }

    /**
     * Biomes actually present in the 3x3 chunk neighbourhood, intersected with the dimension's biome set. Same
     * shape as vanilla, which drops any section biome its biome source does not claim. Holders are canonicalised
     * back onto the table's own holders so the index mapping cannot be handed a holder it never saw.
     */
    private Set<Holder<Biome>> chunkBiomes(
            WorldGenLevel level,
            SectionPos sectionPos,
            FeatureTable table,
            DimensionStackContext stackContext
    ) {
        List<Holder<Biome>> collected = new ArrayList<>();
        ChunkPos.rangeClosed(sectionPos.chunk(), 1).forEach((ChunkPos chunkPos) -> {
            ChunkAccess neighbour = level.getChunk(chunkPos.x(), chunkPos.z());
            LevelChunkSection[] sections = neighbour.getSections();
            for (int sectionIndex = 0; sectionIndex < sections.length; sectionIndex++) {
                LevelChunkSection section = sections[sectionIndex];
                if (stackContext == null) {
                    section.getBiomes().getAll(collected::add);
                    continue;
                }
                int sectionMinimumY = neighbour.getSectionYFromSectionIndex(sectionIndex) << 4;
                for (int quartY = 0; quartY < 4; quartY++) {
                    int internalY = sectionMinimumY + (quartY << 2) - level.getMinY();
                    for (int quartX = 0; quartX < 4; quartX++) {
                        int blockX = chunkPos.getMinBlockX() + (quartX << 2);
                        for (int quartZ = 0; quartZ < 4; quartZ++) {
                            int blockZ = chunkPos.getMinBlockZ() + (quartZ << 2);
                            DimensionStackLayout.Layer layer = stackContext.getLayerAt(
                                    blockX, internalY, blockZ);
                            if (layer.terrainContext().isSelfReferencing()) {
                                collected.add(section.getBiomes().get(quartX, quartY, quartZ));
                            }
                        }
                    }
                }
            }
        });
        Set<Holder<Biome>> present = new LinkedHashSet<>();
        for (Holder<Biome> biome : collected) {
            if (table.biomeSet().contains(biome)) {
                present.add(biome);
                continue;
            }
            String key = holderKey(biome);
            Holder<Biome> canonical = key == null ? null : table.byKey().get(key);
            if (canonical != null) {
                present.add(canonical);
            }
        }
        return present;
    }

    private static String dimensionKey(Engine engine) {
        return engine.getDimension() == null ? "<unbound>" : engine.getDimension().getLoadKey();
    }

    private static String holderKey(Holder<Biome> holder) {
        return holder.unwrapKey()
                .map(key -> key.identifier().toString().toLowerCase(Locale.ROOT))
                .orElse(null);
    }

    private static String normalizeKey(String key) {
        Identifier identifier = key == null ? null : Identifier.tryParse(key);
        return identifier == null ? null : identifier.toString().toLowerCase(Locale.ROOT);
    }

    private record FeatureTable(long generation, IrisImportedFeatureControl control,
                                List<Holder<Biome>> biomes, Set<Holder<Biome>> biomeSet,
                                Map<String, Holder<Biome>> byKey,
                                List<FeatureSorter.StepFeatureData> steps,
                                Map<String, Holder<Biome>> derivatives, boolean filtered) {
    }
}
