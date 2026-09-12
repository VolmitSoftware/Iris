package art.arcane.iris.world.history;

import art.arcane.iris.pack.loading.IrisRegistrant;
import art.arcane.iris.generation.runtime.IrisEngine;
import art.arcane.iris.generation.runtime.Engine;
import art.arcane.iris.generation.biome.IrisBiome;
import art.arcane.iris.generation.terrain.IrisRegion;
import art.arcane.volmlib.util.collection.KList;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

public final class GenerationFindCatalog {
    private static final Map<GenerationHistory, RetainedCatalog> RETAINED = new WeakHashMap<>();
    private static final RetainedCatalog EMPTY = new RetainedCatalog(0L);

    private GenerationFindCatalog() {
    }

    public static KList<IrisBiome> biomes(Engine engine) {
        return merge(engine.getAllBiomes(), retained(engine).biomes);
    }

    public static IrisBiome biome(Engine engine, String key) {
        return find(biomes(engine), key);
    }

    public static KList<IrisRegion> regions(Engine engine) {
        return merge(engine.getDimension().getAllRegions(engine), retained(engine).regions);
    }

    public static IrisRegion region(Engine engine, String key) {
        return find(regions(engine), key);
    }

    public static KList<String> objectKeys(Engine engine) {
        Set<String> keys = new LinkedHashSet<>();
        for (String key : engine.getData().getObjectLoader().getPossibleKeys()) {
            keys.add(key);
        }
        keys.addAll(retained(engine).objects);
        return new KList<>(keys);
    }

    public static boolean hasObjectPlacement(Engine engine, String key) {
        return engine.hasObjectPlacement(key) || containsKey(retained(engine).objects, normalizeObjectKey(key));
    }

    public static KList<String> retainedStructureKeys(Engine engine) {
        return new KList<>(retained(engine).structures);
    }

    public static boolean hasRetainedStructurePlacement(Engine engine, String key) {
        return containsKey(retained(engine).structures, normalizeKey(key));
    }

    private static RetainedCatalog retained(Engine engine) {
        if (!(engine instanceof IrisEngine irisEngine)) {
            return EMPTY;
        }
        return irisEngine.getGenerationHistoryRuntimeRouter()
                .map(router -> retained(router.history()))
                .orElse(EMPTY);
    }

    private static RetainedCatalog retained(GenerationHistory history) {
        GenerationManifest manifest = history.manifest();
        synchronized (RETAINED) {
            RetainedCatalog catalog = RETAINED.get(history);
            if (catalog != null && catalog.activeActivationId == manifest.activeActivation().activationId()) {
                return catalog;
            }
            catalog = loadRetained(history, manifest);
            RETAINED.put(history, catalog);
            return catalog;
        }
    }

    private static RetainedCatalog loadRetained(GenerationHistory history, GenerationManifest manifest) {
        RetainedCatalog catalog = new RetainedCatalog(manifest.activeActivation().activationId());
        try {
            history.forEachRecordedSemantic(semantics -> addRecorded(catalog, semantics));
        } catch (IOException error) {
            throw new UncheckedIOException("Unable to read recorded Iris find catalog", error);
        }
        return catalog;
    }

    private static void addRecorded(RetainedCatalog catalog, ChunkGenerationSemantics semantics) {
        if (!semantics.sealed() || semantics.activationId() == catalog.activeActivationId) {
            return;
        }
        for (String key : semantics.surfaceBiomeKeys()) {
            addBiome(catalog, key);
        }
        for (String key : semantics.caveBiomeKeys()) {
            addBiome(catalog, key);
        }
        for (String key : semantics.regionKeys()) {
            String normalized = normalizeKey(key);
            if (!catalog.regions.containsKey(normalized)) {
                IrisRegion region = new IrisRegion().setName(key);
                region.setLoadKey(key);
                catalog.regions.put(normalized, region);
            }
        }
        for (String key : semantics.objectKeys()) {
            catalog.objects.add(normalizeObjectKey(key));
        }
        for (ChunkGenerationSemantics.StructureOccurrence occurrence : semantics.structures()) {
            catalog.structures.add(normalizeKey(occurrence.key()));
        }
    }

    private static void addBiome(RetainedCatalog catalog, String key) {
        String normalized = normalizeKey(key);
        if (catalog.biomes.containsKey(normalized)) {
            return;
        }
        IrisBiome biome = new IrisBiome().setName(key);
        biome.setLoadKey(key);
        catalog.biomes.put(normalized, biome);
    }

    private static <T extends IrisRegistrant> KList<T> merge(Iterable<T> active, Map<String, T> historical) {
        Map<String, T> merged = new LinkedHashMap<>();
        for (T registrant : active) {
            merged.putIfAbsent(normalizeKey(registrant.getLoadKey()), registrant);
        }
        historical.forEach(merged::putIfAbsent);
        return new KList<>(merged.values());
    }

    private static <T extends IrisRegistrant> T find(Iterable<T> entries, String key) {
        String normalized = normalizeKey(key);
        T caseInsensitiveMatch = null;
        for (T entry : entries) {
            String entryKey = normalizeKey(entry.getLoadKey());
            if (entryKey.equals(normalized)) {
                return entry;
            }
            if (caseInsensitiveMatch == null && entryKey.equalsIgnoreCase(normalized)) {
                caseInsensitiveMatch = entry;
            }
        }
        return caseInsensitiveMatch;
    }

    private static boolean containsKey(Set<String> keys, String key) {
        if (keys.contains(key)) {
            return true;
        }
        for (String candidate : keys) {
            if (candidate.equalsIgnoreCase(key)) {
                return true;
            }
        }
        return false;
    }

    private static String normalizeKey(String key) {
        return key == null ? "" : key.trim();
    }

    private static String normalizeObjectKey(String key) {
        String normalized = normalizeKey(key).replace('\\', '/');
        while (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized.endsWith(".iob") ? normalized.substring(0, normalized.length() - 4) : normalized;
    }

    private static final class RetainedCatalog {
        private final long activeActivationId;
        private final Map<String, IrisBiome> biomes = new LinkedHashMap<>();
        private final Map<String, IrisRegion> regions = new LinkedHashMap<>();
        private final Set<String> objects = new LinkedHashSet<>();
        private final Set<String> structures = new LinkedHashSet<>();

        private RetainedCatalog(long activeActivationId) {
            this.activeActivationId = activeActivationId;
        }
    }
}
