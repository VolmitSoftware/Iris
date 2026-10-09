package com.volmit.vanilla;

import com.volmit.resource.ResourceId;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/** What a module changes about how the platform sets up its structures for a dimension, before any world generates:
 * how often the structures of a structure set start ({@link #frequency}), and whether data packs the host installs may
 * replace the platform's own structure definitions.
 */
public final class VanillaStructureSetup {
    /** The platform's structures as their definitions set them up. */
    public static final VanillaStructureSetup UNCHANGED = new VanillaStructureSetup(Map.of(), false);

    private static final double UNCHANGED_FREQUENCY = 1D;

    private final Map<ResourceId, Double> frequencies;
    private final boolean protectsVanillaDefinitions;

    private VanillaStructureSetup(Map<ResourceId, Double> frequencies, boolean protectsVanillaDefinitions) {
        this.frequencies = frequencies;
        this.protectsVanillaDefinitions = protectsVanillaDefinitions;
    }

    /** This setup, starting the structures of {@code structureSet}, such as {@code minecraft:villages}, {@code multiplier}
     * times as often (0.01 to 16). A set given twice keeps the later multiplier.
     */
    public VanillaStructureSetup withFrequency(ResourceId structureSet, double multiplier) {
        Map<ResourceId, Double> changed = new HashMap<>(frequencies);
        changed.put(Objects.requireNonNull(structureSet, "structureSet"), multiplier);
        return new VanillaStructureSetup(Collections.unmodifiableMap(changed), protectsVanillaDefinitions);
    }

    /** This setup, keeping the platform's own ({@code minecraft}) structure definitions out of the data packs the host
     * installs, so their structures, sets, pools and templates stay as the platform defines them.
     */
    public VanillaStructureSetup protectingVanillaDefinitions() {
        return new VanillaStructureSetup(frequencies, true);
    }

    /** How many times as often the structures of {@code structureSet} start; 1 for a set the setup leaves alone. */
    public double frequency(ResourceId structureSet) {
        return frequencies.getOrDefault(structureSet, UNCHANGED_FREQUENCY);
    }

    /** Every structure set whose frequency the setup changes, with its multiplier. */
    public Map<ResourceId, Double> frequencies() {
        return frequencies;
    }

    public boolean protectsVanillaDefinitions() {
        return protectsVanillaDefinitions;
    }

    /** What two modules' setups mean together: frequencies multiply, and the definitions are protected when either
     * protects them. The order does not matter.
     */
    public VanillaStructureSetup and(VanillaStructureSetup other) {
        Map<ResourceId, Double> combined = new HashMap<>(frequencies);
        other.frequencies.forEach((set, multiplier) -> combined.merge(set, multiplier, (first, second) -> first * second));
        return new VanillaStructureSetup(Collections.unmodifiableMap(combined),
                protectsVanillaDefinitions || other.protectsVanillaDefinitions);
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof VanillaStructureSetup that && frequencies.equals(that.frequencies)
                && protectsVanillaDefinitions == that.protectsVanillaDefinitions;
    }

    @Override
    public int hashCode() {
        return Objects.hash(frequencies, protectsVanillaDefinitions);
    }

    @Override
    public String toString() {
        return "VanillaStructureSetup[" + frequencies + (protectsVanillaDefinitions ? ", vanilla definitions protected" : "") + "]";
    }
}
