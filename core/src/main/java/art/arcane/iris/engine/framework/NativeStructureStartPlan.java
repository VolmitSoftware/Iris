package art.arcane.iris.engine.framework;

import art.arcane.iris.engine.object.structure.vanilla.IrisNativeStructure;
import art.arcane.iris.engine.object.structure.IrisStructurePlacement;

public record NativeStructureStartPlan(
        IrisStructurePlacement placement,
        IrisNativeStructure source,
        int chunkX,
        int chunkZ,
        int baseY
) {
}
