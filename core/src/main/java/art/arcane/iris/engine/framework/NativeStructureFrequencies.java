package art.arcane.iris.engine.framework;

import art.arcane.iris.core.loader.IrisData;
import art.arcane.iris.core.modules.ModuleVanillaStructures;
import art.arcane.iris.engine.object.world.dimension.IrisDimension;

import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * How often the structures of each platform structure set start in a dimension: the multipliers the pack's modules
 * set up through their vanilla structure hooks, by normalized structure set key. Sets without one start as their
 * definition says (multiplier 1).
 */
public final class NativeStructureFrequencies {
    public static final NativeStructureFrequencies NONE = new NativeStructureFrequencies(Map.of());

    private static final double UNCHANGED = 1D;

    private final Map<String, Double> multipliers;

    private NativeStructureFrequencies(Map<String, Double> multipliers) {
        this.multipliers = Map.copyOf(multipliers);
    }

    /** The frequencies the modules of {@code data}'s pack set up for {@code dimension}. */
    public static NativeStructureFrequencies of(IrisData data, IrisDimension dimension) {
        return new NativeStructureFrequencies(ModuleVanillaStructures.setupFor(data, dimension).frequencies().entrySet().stream()
                .collect(Collectors.toMap(entry -> entry.getKey().toString(), Map.Entry::getValue)));
    }

    /** Test and fixture form: multipliers by structure set key. */
    public static NativeStructureFrequencies of(Map<String, Double> multipliers) {
        return new NativeStructureFrequencies(multipliers.entrySet().stream()
                .collect(Collectors.toMap(entry -> normalize(entry.getKey()), Map.Entry::getValue)));
    }

    public double multiplier(String structureSetKey) {
        return multipliers.getOrDefault(normalize(structureSetKey), UNCHANGED);
    }

    public boolean hasOverrides() {
        return !multipliers.isEmpty();
    }

    private static String normalize(String key) {
        return key == null ? "" : key.trim().toLowerCase(Locale.ROOT);
    }
}
