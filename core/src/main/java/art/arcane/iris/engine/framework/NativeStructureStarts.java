package art.arcane.iris.engine.framework;

import art.arcane.iris.core.modules.ModuleVanillaStructures;

import java.util.List;
import java.util.Locale;

/**
 * The platform structure starts the pack's modules place in a chunk, on every platform the same: what the vanilla
 * structure hooks of the engine's pack return, in module id order, as the plans the platform code generates.
 */
public final class NativeStructureStarts {
    private NativeStructureStarts() {
    }

    public static List<NativeStructureStartPlan> plansAt(Engine engine, int chunkX, int chunkZ) {
        return ModuleVanillaStructures.startsAt(engine, chunkX, chunkZ);
    }

    /** The first start of {@code structureKey} (ignoring case) the modules place in the chunk, or null when none does. */
    public static NativeStructureStartPlan matchingPlan(Engine engine, String structureKey, int chunkX, int chunkZ) {
        if (structureKey == null || structureKey.isBlank()) {
            return null;
        }
        String normalizedKey = normalize(structureKey);
        for (NativeStructureStartPlan plan : plansAt(engine, chunkX, chunkZ)) {
            if (normalizedKey.equals(normalize(plan.structureKey()))) {
                return plan;
            }
        }
        return null;
    }

    private static String normalize(String structureKey) {
        return structureKey.toLowerCase(Locale.ROOT);
    }
}
