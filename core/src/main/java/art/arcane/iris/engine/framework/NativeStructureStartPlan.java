package art.arcane.iris.engine.framework;

import java.util.Objects;

/**
 * One start of a platform structure a module places, as the platform code builds it: the structure key, the start
 * chunk, the world base Y (the structure's lowest block when underground, the planned surface otherwise), whether it
 * takes the chunk from a platform start of the same structure, the identity of what placed it, the changes to the
 * jigsaw definition it is assembled with (null for none), and the memo the module gave it (null for none), which the
 * ownership record keeps for every later chunk of the start.
 */
public record NativeStructureStartPlan(
        String structureKey,
        int chunkX,
        int chunkZ,
        int baseY,
        boolean underground,
        boolean replacesPlatformStart,
        long placementIdentity,
        NativeJigsawOverrides jigsaw,
        NativeStartMemo memo
) {
    public NativeStructureStartPlan {
        Objects.requireNonNull(structureKey, "Native structure start plan requires a structure key");
    }
}
