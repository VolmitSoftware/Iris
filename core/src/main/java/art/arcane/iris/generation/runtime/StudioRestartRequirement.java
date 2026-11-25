package art.arcane.iris.generation.runtime;

import java.util.List;

/**
 * Custom biomes a Studio world generates without their latest definition until the server restarts: edited biomes
 * keep the definition the server loaded, new biomes generate as their vanilla derivative.
 */
public record StudioRestartRequirement(List<String> biomes) {
    private static final StudioRestartRequirement NONE = new StudioRestartRequirement(List.of());
    private static final int LISTED_BIOME_LIMIT = 3;

    public StudioRestartRequirement {
        biomes = List.copyOf(biomes);
    }

    public static StudioRestartRequirement none() {
        return NONE;
    }

    public static StudioRestartRequirement pending(List<String> biomes) {
        return biomes.isEmpty() ? NONE : new StudioRestartRequirement(biomes);
    }

    public boolean isPending() {
        return !biomes.isEmpty();
    }

    public String biomeSummary() {
        List<String> listed = biomes.stream().limit(LISTED_BIOME_LIMIT).toList();
        int hidden = biomes.size() - listed.size();
        String summary = String.join(", ", listed);
        return hidden > 0 ? summary + " +" + hidden : summary;
    }
}
