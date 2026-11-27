package art.arcane.iris.generation.runtime;

import art.arcane.iris.world.history.RegistryRestartRequiredException;

import java.util.LinkedHashSet;
import java.util.List;

/**
 * What a Studio world still needs a server restart for. Deferred biomes hotloaded with the definition the server
 * loaded; blocking biomes have no loaded definition, so their update waits for the restart.
 */
public record StudioRestartRequirement(List<String> deferredBiomes, List<String> blockingBiomes) {
    private static final StudioRestartRequirement NONE = new StudioRestartRequirement(List.of(), List.of());
    private static final int LISTED_BIOME_LIMIT = 3;

    public StudioRestartRequirement {
        deferredBiomes = List.copyOf(deferredBiomes);
        blockingBiomes = List.copyOf(blockingBiomes);
    }

    public static StudioRestartRequirement none() {
        return NONE;
    }

    public static StudioRestartRequirement deferring(List<String> deferredBiomes) {
        return deferredBiomes.isEmpty() ? NONE : new StudioRestartRequirement(deferredBiomes, List.of());
    }

    public static StudioRestartRequirement blockedBy(RegistryRestartRequiredException restart) {
        return new StudioRestartRequirement(restart.deferredBiomes(), restart.blockingBiomes());
    }

    public boolean isPending() {
        return !deferredBiomes.isEmpty() || !blockingBiomes.isEmpty();
    }

    public boolean blocksHotload() {
        return !blockingBiomes.isEmpty();
    }

    public String biomeSummary() {
        LinkedHashSet<String> biomes = new LinkedHashSet<>(blockingBiomes);
        biomes.addAll(deferredBiomes);
        List<String> listed = biomes.stream().limit(LISTED_BIOME_LIMIT).toList();
        int hidden = biomes.size() - listed.size();
        String summary = String.join(", ", listed);
        return hidden > 0 ? summary + " +" + hidden : summary;
    }
}
