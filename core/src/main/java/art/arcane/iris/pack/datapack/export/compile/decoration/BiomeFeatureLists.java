package art.arcane.iris.pack.datapack.export.compile.decoration;

import art.arcane.iris.pack.datapack.export.VanillaResourceIdentifier;
import art.arcane.iris.pack.datapack.export.ir.BiomeDef;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public final class BiomeFeatureLists {
    private static final GenerationStep[] STEPS = GenerationStep.values();

    private final Map<GenerationStep, Set<String>> order = new EnumMap<>(GenerationStep.class);
    private final Map<String, Map<GenerationStep, Set<String>>> members = new HashMap<>();

    public void add(String biomeId, GenerationStep step, String placedFeatureId) {
        VanillaResourceIdentifier.requireIdentifier(biomeId, "feature list biome id");
        Objects.requireNonNull(step);
        VanillaResourceIdentifier.requireIdentifier(placedFeatureId, "placed feature id");
        order.computeIfAbsent(step, ignored -> new LinkedHashSet<>()).add(placedFeatureId);
        members.computeIfAbsent(biomeId, ignored -> new EnumMap<>(GenerationStep.class))
                .computeIfAbsent(step, ignored -> new HashSet<>())
                .add(placedFeatureId);
    }

    public List<BiomeDef> apply(List<BiomeDef> biomes, String namespace) {
        VanillaResourceIdentifier.requireNamespace(namespace, "feature list namespace");
        Set<String> defined = new HashSet<>(biomes.size());
        List<BiomeDef> result = new ArrayList<>(biomes.size());
        for (BiomeDef biome : biomes) {
            String id = namespace + ":" + biome.id();
            defined.add(id);
            JsonObject json = biome.json().deepCopy();
            json.add("features", features(members.getOrDefault(id, Map.of())));
            result.add(new BiomeDef(biome.id(), json));
        }
        for (String biomeId : members.keySet()) {
            if (!defined.contains(biomeId)) {
                throw new IllegalArgumentException("Feature lists reference undefined biome '" + biomeId + "'");
            }
        }
        return result;
    }

    private JsonArray features(Map<GenerationStep, Set<String>> biomeSteps) {
        JsonArray steps = new JsonArray(STEPS.length);
        for (GenerationStep step : STEPS) {
            JsonArray features = new JsonArray();
            Set<String> biomeFeatures = biomeSteps.get(step);
            if (biomeFeatures != null) {
                for (String feature : order.get(step)) {
                    if (biomeFeatures.contains(feature)) {
                        features.add(feature);
                    }
                }
            }
            steps.add(features);
        }
        return steps;
    }
}
