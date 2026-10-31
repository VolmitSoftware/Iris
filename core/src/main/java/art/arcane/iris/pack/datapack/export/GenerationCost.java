package art.arcane.iris.pack.datapack.export;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;

public record GenerationCost(Map<GenerationCost.Root, GenerationCost.RootCost> roots, long densityFunctionBytes) {
    public static final GenerationCost NONE = new GenerationCost(Map.of(), 0L);
    private static final String DATA_PREFIX = "data/";
    private static final String DENSITY_FUNCTION_FOLDER = "/worldgen/density_function/";
    private static final String JSON_SUFFIX = ".json";

    public GenerationCost {
        EnumMap<Root, RootCost> copy = new EnumMap<>(Root.class);
        copy.putAll(roots);
        roots = Collections.unmodifiableMap(copy);
        if (densityFunctionBytes < 0L) {
            throw new IllegalArgumentException("Density function JSON size cannot be negative, got " + densityFunctionBytes);
        }
    }

    public static long densityFunctionBytes(Map<String, byte[]> files) {
        long bytes = 0L;
        for (Map.Entry<String, byte[]> file : files.entrySet()) {
            String path = file.getKey();
            if (path.startsWith(DATA_PREFIX) && path.contains(DENSITY_FUNCTION_FOLDER) && path.endsWith(JSON_SUFFIX)) {
                bytes += file.getValue().length;
            }
        }
        return bytes;
    }

    public Optional<RootCost> root(Root root) {
        return Optional.ofNullable(roots.get(root));
    }

    public GenerationCost withDensityFunctionBytes(long bytes) {
        return new GenerationCost(roots, bytes);
    }

    public enum Root {
        TEMPERATURE("temperature", true),
        VEGETATION("vegetation", true),
        CONTINENTS("continents", true),
        EROSION("erosion", true),
        DEPTH("depth", true),
        RIDGES("ridges", true),
        CHUNK_SURFACE_LEVEL("chunk_surface_level", false),
        FINAL_DENSITY("final_density", false),
        AQUIFER_BARRIER("aquifer.barrier", false),
        AQUIFER_FLOODEDNESS("aquifer.fluid_level_floodedness", false),
        AQUIFER_SPREAD("aquifer.fluid_level_spread", false),
        AQUIFER_LAVA("aquifer.lava", false),
        AQUIFER_EXCLUSION("aquifer.exclusion", false),
        AQUIFER_SURFACE_LEVEL("aquifer.surface_level", false);

        private final String id;
        private final boolean climate;

        Root(String id, boolean climate) {
            this.id = id;
            this.climate = climate;
        }

        public String id() {
            return id;
        }

        public boolean climate() {
            return climate;
        }
    }

    public record RootCost(double worstCaseNodes, double expandedNodes, long distinctNodes) {
        public RootCost {
            if (!(worstCaseNodes >= 0.0) || !(expandedNodes >= 0.0) || distinctNodes < 0L) {
                throw new IllegalArgumentException("Generation cost counts cannot be negative, got " + worstCaseNodes + " worst case, "
                        + expandedNodes + " expanded and " + distinctNodes + " distinct");
            }
        }
    }
}
