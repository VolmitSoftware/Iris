package art.arcane.iris.engine.framework.nativefit;

import art.arcane.iris.engine.framework.NativeStartMemo;

import java.util.List;

/** A start the platform places in the chunk it decorates, as the platform code reads it. */
public interface NativeFitStart<S> {
    String structureKey();

    int chunkX();

    int chunkZ();

    /** The name of the platform's decoration step, as the API's {@code StructureStep} names it. */
    String step();

    /** The name of the structure definition's terrain adaptation, as the API's {@code TerrainAdaptation} names it. */
    String adaptation();

    /** The memo the module that placed the start gave it; null for the platform's own starts. */
    NativeStartMemo memo();

    List<NativeFitPiece<S>> pieces();

    /** The same object for this start every chunk, for as long as the platform keeps the start. */
    Object key();
}
