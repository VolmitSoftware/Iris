package art.arcane.iris.engine.framework;

import art.arcane.iris.core.modules.ModuleVanillaStructures;

import java.util.List;

/**
 * Where the platform's own starts sit and how far around their pieces the pack's modules change the terrain, as the
 * platform code asks before it publishes a start: the shift every module gives a start, added up, and every module's
 * reach.
 */
public final class NativeStructureLayouts {
    private NativeStructureLayouts() {
    }

    /** How far the pack's modules move {@code start} up (negative: down), before the platform keeps it inside the world. */
    public static int shift(Engine engine, NativeStartPlacement start) {
        return ModuleVanillaStructures.shiftOf(engine, start);
    }

    /**
     * How far every module of the pack changes the terrain around a start of {@code structureKey}: a start a module
     * placed, with the {@code memo} it gave it, or one of the platform's own (null memo).
     */
    public static List<NativeStructureReach> reaches(Engine engine, String structureKey, boolean underground,
                                                     NativeStartMemo memo) {
        return ModuleVanillaStructures.reachesOf(engine, structureKey, underground, memo);
    }
}
