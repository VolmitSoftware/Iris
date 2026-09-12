package art.arcane.iris.structure.nativegen;

import art.arcane.iris.structure.placement.IrisStructurePlacement;

public record NativeStructureStartPlan(
        IrisStructurePlacement placement,
        IrisNativeStructure source,
        int chunkX,
        int chunkZ,
        int baseY
) {
}
