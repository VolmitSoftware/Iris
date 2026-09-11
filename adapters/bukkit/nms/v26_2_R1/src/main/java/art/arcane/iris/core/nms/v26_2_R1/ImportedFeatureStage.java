package art.arcane.iris.core.nms.v26_2_R1;

import art.arcane.iris.engine.DimensionStackContext;
import art.arcane.iris.engine.DimensionStackLayout;
import art.arcane.iris.nativegen.NativeGenerationWriteGuard;
import art.arcane.iris.engine.framework.Engine;
import art.arcane.iris.engine.framework.NativeFeatureGenerationPolicy;
import art.arcane.iris.engine.object.IrisBiome;
import art.arcane.iris.engine.object.IrisBiomeCustom;
import art.arcane.iris.engine.object.IrisDecorationStep;
import art.arcane.iris.engine.object.IrisDimension;
import art.arcane.iris.engine.object.IrisImportedFeatureControl;
import art.arcane.iris.engine.object.IrisStaticObjectLayer;
import art.arcane.iris.spi.IrisLogging;
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
import net.minecraft.world.level.chunk.ChunkGenerator;
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
import java.util.function.IntBinaryOperator;

/**
 * Bukkit twin of the modded imported-feature stage. Same control, same semantics, same seeds: with
 * {@code importedFeatures.enabled} the vanilla placed-feature pass runs over Iris terrain, and with the
 * control off nothing here allocates or runs.
 *
 * <p>Iris does its own native structure pass and calls the delegate with {@code addVanillaDecorations=false},
 * so the vanilla feature half never runs on its own. This reproduces that half only - structures are never
 * placed twice.
 *
 * <p>Threading: called from {@code applyBiomeDecoration} on the worldgen thread that owns the chunk. The
 * FEATURES chunk step is not parallel-safe.
 */
final class ImportedFeatureStage {
    private static final String CYCLE_MARKER = "Feature order cycle found";

    private final Engine engine;
    private final ConcurrentHashMap<Integer, RuntimeFeatureState> runtimeStates = new ConcurrentHashMap<>();

    ImportedFeatureStage(Engine engine) {
        this.engine = engine;
    }

    /**
     * Generation settings for one biome holder, mapping Iris custom biomes onto their vanilla derivative.
     * Pass-through while the control is off, which is what keeps {@code BiomeFilter} behaving exactly as it
     * does today.
     */
    BiomeGenerationSettings generationSettings(Holder<Biome> biome) {
        RuntimeFeatureState state = currentState();
        FeatureTable table = state == null ? null : state.featureTable();
        if (table == null) {
            return null;
        }
        Holder<Biome> mapped = table.derivatives().get(holderKey(biome));
        return mapped == null ? null : mapped.value().getGenerationSettings();
    }

    /**
     * Builds the table on the first decorated chunk, or leaves the stage inert. A feature-order cycle is
     * reported once here and degrades to features-off; it never reaches chunk generation as a crash.
     */
    void prepare(WorldGenLevel level) {
        IrisDimension dimension = engine.getDimension();
        int runtimeId = engine.getCacheID();
        if (settled(runtimeStates.get(runtimeId), dimension)) {
            return;
        }
        synchronized (this) {
            if (settled(runtimeStates.get(runtimeId), dimension)) {
                return;
            }
            runtimeStates.put(runtimeId, build(level, dimension));
        }
    }

    void evictRuntime(int runtimeId) {
        runtimeStates.remove(runtimeId);
    }

    private RuntimeFeatureState currentState() {
        IrisDimension dimension = engine.getDimension();
        RuntimeFeatureState state = runtimeStates.get(engine.getCacheID());
        return settled(state, dimension) ? state : null;
    }

    private static boolean settled(RuntimeFeatureState state, IrisDimension dimension) {
        return state != null && state.dimension() == dimension;
    }

    private RuntimeFeatureState build(WorldGenLevel level, IrisDimension dimension) {
        IrisImportedFeatureControl control;
        try {
            control = NativeFeatureGenerationPolicy.control(engine);
        } catch (RuntimeException error) {
            IrisLogging.error("Iris could not read importedFeatures for this dimension; features off: "
                    + error);
            return new RuntimeFeatureState(dimension, null);
        }
        if (!control.shouldGenerateFeatures()) {
            return new RuntimeFeatureState(dimension, null);
        }
        FeatureTable built;
        try {
            built = buildTable(level, control, dimension);
        } catch (Throwable error) {
            IrisLogging.error("Iris importedFeatures is off for " + dimensionKey()
                    + ": feature table construction failed: " + error);
            return new RuntimeFeatureState(dimension, null);
        }
        if (built == null) {
            return new RuntimeFeatureState(dimension, null);
        }
        IrisLogging.info("Iris importedFeatures on for " + dimensionKey() + ": " + built.biomes().size()
                + " biomes, " + built.steps().size() + " steps, " + built.derivatives().size()
                + " custom-biome derivative maps");
        return new RuntimeFeatureState(dimension, built);
    }

    private FeatureTable buildTable(WorldGenLevel level, IrisImportedFeatureControl control,
                                    IrisDimension dimension) {
        Registry<Biome> registry = level.registryAccess().lookupOrThrow(Registries.BIOME);
        Set<String> visibleKeys = visibleBiomeKeys();
        // Registry-ordered walk, never a hash-ordered set: FeatureSorter's cycle detection walks this list and
        // an unordered walk makes detection depend on JVM hash order.
        List<Holder<Biome>> biomes = new ArrayList<>();
        Map<String, Holder<Biome>> byKey = new HashMap<>();
        registry.listElements().forEach((Holder.Reference<Biome> reference) -> {
            String key = holderKey(reference);
            if (key != null && visibleKeys.contains(key)) {
                biomes.add(reference);
                byKey.put(key, reference);
            }
        });
        if (biomes.isEmpty()) {
            IrisLogging.error("Iris importedFeatures is on but " + dimensionKey()
                    + " exposes no registered biomes; features off");
            return null;
        }
        Map<String, Holder<Biome>> derivatives = customBiomeDerivatives(registry, byKey);
        List<FeatureSorter.StepFeatureData> steps;
        try {
            steps = FeatureSorter.buildFeaturesPerStep(biomes,
                    (Holder<Biome> biome) -> settingsFor(biome, derivatives).features(), true);
        } catch (IllegalStateException error) {
            String message = error.getMessage();
            if (message == null || !message.contains(CYCLE_MARKER)) {
                throw error;
            }
            IrisLogging.error("Iris importedFeatures is off for " + dimensionKey()
                    + ": the registered placed features cannot be ordered. " + message
                    + ". Remove or reorder the conflicting content, or leave importedFeatures.enabled false.");
            return null;
        }
        boolean filtered = control.getDisabled() != null && !control.getDisabled().isEmpty();
        return new FeatureTable(dimension, control, List.copyOf(biomes), Set.copyOf(biomes),
                Map.copyOf(byKey), steps, Map.copyOf(derivatives), filtered);
    }

    /**
     * Every biome key Iris can write into a chunk section: the structure derivative, the raw derivative, every
     * scatter entry, and every generated custom biome.
     */
    private Set<String> visibleBiomeKeys() {
        Set<String> keys = new LinkedHashSet<>();
        for (IrisBiome irisBiome : engine.getDimension().getReachableBiomes(engine)) {
            addKey(keys, irisBiome.getStructureDerivativeKey());
            addKey(keys, irisBiome.getDerivativeKey());
            addKey(keys, irisBiome.getVanillaDerivativeKey());
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
                addKey(keys, engine.getData().customBiomeResourceKey(engine.getDimension(), customBiome));
            }
        }
        return keys;
    }

    /**
     * Maps every generated Iris custom biome key onto the registry holder of its Iris biome's vanilla
     * derivative. The custom biome's own datapack JSON carries no features by design.
     */
    private Map<String, Holder<Biome>> customBiomeDerivatives(Registry<Biome> registry,
                                                              Map<String, Holder<Biome>> byKey) {
        Map<String, Holder<Biome>> derivatives = new HashMap<>();
        for (IrisBiome irisBiome : engine.getDimension().getReachableBiomes(engine)) {
            if (!irisBiome.isCustom()) {
                continue;
            }
            String derivativeKey = normalizeKey(irisBiome.getVanillaDerivativeKey());
            // Resolve from the registry, not only from the visible biome set: a sea or shore biome's structure
            // derivative is rewritten away from its vanilla derivative, so the derivative whose features we
            // want is not always a biome Iris can emit.
            Holder<Biome> derivative = byKey.get(derivativeKey);
            if (derivative == null) {
                derivative = resolveHolder(registry, derivativeKey);
            }
            if (derivative == null) {
                IrisLogging.warn("Iris importedFeatures: vanilla derivative " + derivativeKey + " of biome "
                        + irisBiome.getLoadKey()
                        + " is not registered; its custom biomes generate no imported features");
                continue;
            }
            for (IrisBiomeCustom customBiome : irisBiome.getCustomDerivitives()) {
                derivatives.put(
                        engine.getData().customBiomeResourceKey(engine.getDimension(), customBiome),
                        derivative
                );
            }
        }
        return derivatives;
    }

    private static Holder<Biome> resolveHolder(Registry<Biome> registry, String key) {
        if (registry == null || key == null || key.isBlank()) {
            return null;
        }
        Identifier identifier = Identifier.tryParse(key);
        if (identifier == null) {
            return null;
        }
        return registry.get(identifier).<Holder<Biome>>map((Holder.Reference<Biome> reference) -> reference)
                .orElse(null);
    }

    private static BiomeGenerationSettings settingsFor(Holder<Biome> biome,
                                                       Map<String, Holder<Biome>> derivatives) {
        Holder<Biome> mapped = derivatives.get(holderKey(biome));
        return mapped == null
                ? biome.value().getGenerationSettings()
                : mapped.value().getGenerationSettings();
    }

    /**
     * Runs the vanilla placed-feature pass for one chunk on the calling worldgen thread. A no-op while the
     * control is disabled or the table degraded.
     */
    void run(WorldGenLevel level, ChunkAccess chunk, ChunkGenerator owner) {
        RuntimeFeatureState state = currentState();
        FeatureTable table = state == null ? null : state.featureTable();
        if (table == null) {
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
                : NativeStructureWorldgenAccess.create(
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
            throw new IllegalStateException("Iris imported feature placement failed for chunk "
                    + centerPos.x() + "," + centerPos.z(), error);
        } finally {
            level.setCurrentlyGenerating(null);
        }
    }

    private void placeStep(WorldGenLevel level, FeatureTable table, FeatureSorter.StepFeatureData stepData,
                           Registry<PlacedFeature> featureRegistry, Set<Holder<Biome>> chunkBiomes,
                           ChunkGenerator owner, WorldgenRandom random, long decorationSeed,
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

    private static void addKey(Set<String> keys, String key) {
        String normalized = normalizeKey(key);
        if (normalized != null) {
            keys.add(normalized);
        }
    }

    private String dimensionKey() {
        IrisDimension dimension = engine.getDimension();
        return dimension == null ? "<unbound>" : dimension.getLoadKey();
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

    private record FeatureTable(IrisDimension dimension, IrisImportedFeatureControl control,
                                List<Holder<Biome>> biomes, Set<Holder<Biome>> biomeSet,
                                Map<String, Holder<Biome>> byKey,
                                List<FeatureSorter.StepFeatureData> steps,
                                Map<String, Holder<Biome>> derivatives, boolean filtered) {
    }

    private record RuntimeFeatureState(IrisDimension dimension, FeatureTable featureTable) {
    }
}
