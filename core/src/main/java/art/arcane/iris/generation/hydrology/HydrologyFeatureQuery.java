package art.arcane.iris.generation.hydrology;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

public record HydrologyFeatureQuery(Set<HydrologyFeatureType> types, String profileKey) {
    private static final Set<HydrologyFeatureType> SURFACE = Set.of(
            HydrologyFeatureType.SURFACE_POOL,
            HydrologyFeatureType.RIFFLE,
            HydrologyFeatureType.CASCADE,
            HydrologyFeatureType.WATERFALL,
            HydrologyFeatureType.MOUTH
    );
    private static final Set<HydrologyFeatureType> UNDERGROUND = Set.of(
            HydrologyFeatureType.UNDERGROUND_POOL,
            HydrologyFeatureType.UNDERGROUND_DROP,
            HydrologyFeatureType.SINKHOLE
    );
    private static final Set<HydrologyFeatureType> GROTTOS = Set.of(
            HydrologyFeatureType.COASTAL_GROTTO,
            HydrologyFeatureType.INLAND_GROTTO
    );
    private static final Set<HydrologyFeatureType> DEEP = Set.of(
            HydrologyFeatureType.DEEP_POOL,
            HydrologyFeatureType.DEEP_CHANNEL
    );
    private static final Set<HydrologyFeatureType> PROFILED = Set.of(
            HydrologyFeatureType.DEEP_POOL,
            HydrologyFeatureType.DEEP_CHANNEL,
            HydrologyFeatureType.STANDING_POOL
    );
    private static final Map<String, Set<HydrologyFeatureType>> BUILT_INS = builtIns();

    public HydrologyFeatureQuery {
        types = Set.copyOf(types);
        if (types.isEmpty()) {
            throw new IllegalArgumentException("Hydrology feature query requires at least one feature type.");
        }
        profileKey = profileKey == null || profileKey.isBlank() ? null : profileKey.trim();
    }

    public static HydrologyFeatureQuery parse(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Hydrology feature type must not be blank.");
        }
        String normalized = value.trim();
        if (normalized.regionMatches(true, 0, "type=", 0, 5)) {
            normalized = normalized.substring(5).trim();
        }
        String key = normalizeKeyword(normalized);
        Set<HydrologyFeatureType> builtInTypes = BUILT_INS.get(key);
        return builtInTypes == null
                ? new HydrologyFeatureQuery(PROFILED, normalized)
                : new HydrologyFeatureQuery(builtInTypes, null);
    }

    public static List<String> suggestions(Collection<String> deepFluidIds) {
        LinkedHashSet<String> suggestions = new LinkedHashSet<>(BUILT_INS.keySet());
        TreeSet<String> orderedIds = new TreeSet<>();
        if (deepFluidIds != null) {
            for (String id : deepFluidIds) {
                if (id != null && !id.isBlank() && !isReservedKeyword(id)) {
                    orderedIds.add(id.trim());
                }
            }
        }
        suggestions.addAll(orderedIds);
        return List.copyOf(suggestions);
    }

    public static boolean isReservedKeyword(String value) {
        return value != null && !value.isBlank() && BUILT_INS.containsKey(normalizeKeyword(value.trim()));
    }

    private static String normalizeKeyword(String value) {
        return value.toLowerCase(Locale.ROOT).replace('-', '_');
    }

    private static Map<String, Set<HydrologyFeatureType>> builtIns() {
        LinkedHashMap<String, Set<HydrologyFeatureType>> builtIns = new LinkedHashMap<>();
        builtIns.put("surface", SURFACE);
        builtIns.put("waterfall", Set.of(HydrologyFeatureType.WATERFALL));
        builtIns.put("sinkhole", Set.of(HydrologyFeatureType.SINKHOLE));
        builtIns.put("underground", UNDERGROUND);
        builtIns.put("grotto", GROTTOS);
        builtIns.put("coastal_grotto", Set.of(HydrologyFeatureType.COASTAL_GROTTO));
        builtIns.put("inland_grotto", Set.of(HydrologyFeatureType.INLAND_GROTTO));
        builtIns.put("mouth", Set.of(HydrologyFeatureType.MOUTH));
        builtIns.put("deep", DEEP);
        builtIns.put("pool", Set.of(HydrologyFeatureType.STANDING_POOL));
        return Collections.unmodifiableMap(builtIns);
    }
}
