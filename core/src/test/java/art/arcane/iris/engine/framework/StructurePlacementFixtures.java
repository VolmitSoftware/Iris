package art.arcane.iris.engine.framework;

import art.arcane.iris.builtin.structure.NativeStructureSource;
import art.arcane.iris.builtin.structure.StructurePlacement;
import art.arcane.iris.builtin.structure.StructurePlacementSettings;
import art.arcane.iris.core.loader.IrisRegistrant;
import art.arcane.iris.core.modules.BuiltInPack;
import art.arcane.iris.core.modules.ModuleSettings;
import art.arcane.iris.engine.object.TestSettings;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Structure placements as packs write them, for tests: {@code placement("structures", List.of("test:city"))}. */
public final class StructurePlacementFixtures {
    private StructurePlacementFixtures() {
    }

    /** A placement with the options {@code namesAndValues} set, the others at their defaults. */
    public static StructurePlacement placement(Object... namesAndValues) {
        return TestSettings.with(new StructurePlacement(), namesAndValues);
    }

    /** A placement that assembles the pack's structures {@code keys}. */
    public static StructurePlacement assembling(String... keys) {
        return placement("structures", new ArrayList<>(Arrays.asList(keys)));
    }

    /** A placement that starts the platform's structures {@code sources} natively. */
    public static StructurePlacement starting(NativeStructureSource... sources) {
        return placement("nativeStructures", new ArrayList<>(Arrays.asList(sources)));
    }

    public static NativeStructureSource nativeSource(String structure) {
        return TestSettings.with(new NativeStructureSource(), "structure", structure);
    }

    public static NativeStructureSource nativeSource(String structure, int weight) {
        return TestSettings.with(new NativeStructureSource(), "structure", structure, "weight", weight);
    }

    /** {@code placement} with {@code value} appended to its list option {@code name}, as a pack listing one more would have it. */
    public static StructurePlacement withAdded(StructurePlacement placement, String name, Object value) {
        List<Object> values = new ArrayList<>(currentList(placement, name));
        values.add(value);
        return TestSettings.with(placement, name, values);
    }

    private static List<?> currentList(StructurePlacement placement, String name) {
        try {
            java.lang.reflect.Field field = StructurePlacement.class.getDeclaredField(name);
            field.setAccessible(true);
            Object value = field.get(placement);
            return value == null ? List.of() : (List<?>) value;
        } catch (ReflectiveOperationException failure) {
            throw new IllegalArgumentException("No list option " + name + " in a structure placement", failure);
        }
    }

    /** The {@code "structures"} settings listing {@code placements}. */
    public static StructurePlacementSettings placementSettings(List<StructurePlacement> placements) {
        return TestSettings.with(new StructurePlacementSettings(), "placements", new ArrayList<>(placements));
    }

    /** The module settings of a dimension, region or biome whose {@code "structures"} are {@code placements}. */
    public static ModuleSettings declaring(List<StructurePlacement> placements) {
        return BuiltInPack.settings(placementSettings(placements));
    }

    public static ModuleSettings declaring(StructurePlacement... placements) {
        return declaring(Arrays.asList(placements));
    }

    /** {@code owner}, now declaring {@code placements} in its {@code "structures"}. */
    public static <T extends IrisRegistrant> T declare(T owner, StructurePlacement... placements) {
        owner.setModuleSettings(declaring(placements));
        return owner;
    }
}
