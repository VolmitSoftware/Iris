package art.arcane.iris.pack.datapack;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class DatapackStructureScopeIndex {
    private final Map<String, Set<String>> sourcesByStructure;
    private final Map<String, Set<String>> sourcesByStructureSet;

    private DatapackStructureScopeIndex(
            Map<String, Set<String>> sourcesByStructure,
            Map<String, Set<String>> sourcesByStructureSet
    ) {
        this.sourcesByStructure = sourcesByStructure;
        this.sourcesByStructureSet = sourcesByStructureSet;
    }

    public static DatapackStructureScopeIndex create(
            List<DatapackIngestService.StructureScopeResources> resources
    ) {
        Map<String, Set<String>> mutableSourcesByStructure = new HashMap<>();
        Map<String, Set<String>> mutableSourcesBySet = new HashMap<>();
        if (resources != null) {
            for (DatapackIngestService.StructureScopeResources resource : resources) {
                if (resource == null) {
                    continue;
                }
                String source = normalizeSource(resource.source());
                if (source.isEmpty()) {
                    continue;
                }
                addOwnership(mutableSourcesByStructure, resource.structureKeys(), source);
                addOwnership(mutableSourcesBySet, resource.structureSetKeys(), source);
            }
        }

        return new DatapackStructureScopeIndex(
                immutableOwnership(mutableSourcesByStructure),
                immutableOwnership(mutableSourcesBySet));
    }

    public Set<String> declaredSources(Iterable<String> datapackImports) {
        if (datapackImports == null) {
            return Set.of();
        }
        Set<String> declared = new HashSet<>();
        for (String source : datapackImports) {
            String normalized = normalizeSource(source);
            if (!normalized.isEmpty()) {
                declared.add(normalized);
            }
        }
        return declared.isEmpty() ? Set.of() : Set.copyOf(declared);
    }

    public boolean allowsStructureSet(String structureSetKey, Set<String> declaredSources) {
        return allows(sourcesByStructureSet, structureSetKey, declaredSources);
    }

    public boolean allowsStructure(String structureKey, Set<String> declaredSources) {
        return allows(sourcesByStructure, structureKey, declaredSources);
    }

    public boolean isManagedStructureSet(String structureSetKey) {
        return sourcesByStructureSet.containsKey(normalizeKey(structureSetKey));
    }

    public boolean isManagedStructure(String structureKey) {
        return sourcesByStructure.containsKey(normalizeKey(structureKey));
    }

    public int managedStructureCount() {
        return sourcesByStructure.size();
    }

    public int managedStructureSetCount() {
        return sourcesByStructureSet.size();
    }

    public boolean isEmpty() {
        return sourcesByStructure.isEmpty() && sourcesByStructureSet.isEmpty();
    }

    private static void addOwnership(
            Map<String, Set<String>> ownership,
            List<String> keys,
            String source
    ) {
        if (keys == null) {
            return;
        }
        for (String key : keys) {
            String normalizedKey = normalizeKey(key);
            if (!normalizedKey.isEmpty()) {
                ownership.computeIfAbsent(normalizedKey, ignored -> new HashSet<>()).add(source);
            }
        }
    }

    private static Map<String, Set<String>> immutableOwnership(Map<String, Set<String>> ownership) {
        Map<String, Set<String>> immutable = new HashMap<>(ownership.size());
        for (Map.Entry<String, Set<String>> entry : ownership.entrySet()) {
            immutable.put(entry.getKey(), Set.copyOf(entry.getValue()));
        }
        return Map.copyOf(immutable);
    }

    private static boolean allows(
            Map<String, Set<String>> ownership,
            String key,
            Set<String> declaredSources
    ) {
        Set<String> owners = ownership.get(normalizeKey(key));
        if (owners == null) {
            return true;
        }
        return declaredSources != null && declaredSources.containsAll(owners);
    }

    private static String normalizeSource(String source) {
        return source == null ? "" : source.trim();
    }

    private static String normalizeKey(String key) {
        return key == null ? "" : key.trim().toLowerCase(Locale.ROOT);
    }
}
