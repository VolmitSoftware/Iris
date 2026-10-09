package art.arcane.iris.engine.framework;

import art.arcane.iris.engine.object.structure.IrisStructureStiltSettings;
import art.arcane.iris.engine.object.structure.IrisStructureTerrain;
import art.arcane.iris.engine.object.structure.jigsaw.IrisJigsawConfiguration;
import art.arcane.iris.engine.object.structure.vanilla.IrisNativeStructureDecision;
import art.arcane.iris.engine.object.structure.vanilla.NativeStructureGenerationStatus;

import java.util.Objects;

/**
 * One start of a platform structure a module places, as the platform code builds it: the structure key, the start
 * chunk, the world base Y (the structure's lowest block when underground, the planned surface otherwise), whether it
 * takes the chunk from a platform start of the same structure, the identity of what placed it, and Iris's jigsaw,
 * stilt and terrain classes for the module's jigsaw overrides and fit. {@code jigsaw} and {@code stilt} may be null;
 * {@code terrain} is never null ({@code SOURCE} when the module states none).
 */
public record NativeStructureStartPlan(
        String structureKey,
        int chunkX,
        int chunkZ,
        int baseY,
        boolean underground,
        boolean replacesPlatformStart,
        long placementIdentity,
        IrisJigsawConfiguration jigsaw,
        IrisStructureStiltSettings stilt,
        IrisStructureTerrain terrain
) {
    public NativeStructureStartPlan {
        Objects.requireNonNull(structureKey, "Native structure start plan requires a structure key");
        Objects.requireNonNull(terrain, "Native structure start plan requires a terrain fit");
    }

    /** The platform decision this start is generated under: generated natively with the start's own fit. */
    public IrisNativeStructureDecision decision() {
        return new IrisNativeStructureDecision(NativeStructureGenerationStatus.GENERATE_NATIVE, 0, null, false, stilt, terrain);
    }
}
