package art.arcane.iris.engine.framework.nativefit;

import art.arcane.iris.core.modules.ModuleStructureFitting;
import art.arcane.iris.engine.framework.Engine;
import com.volmit.IrisModule;

/**
 * The pack's modules fitting a chunk's structure starts into the terrain, around the platform placing their pieces:
 * {@link #beforePieces} once before any piece of the chunk is placed, then {@link Session#afterPieces} after each
 * start's pieces, in the order of the chunk's starts.
 */
public interface NativeStructureFitting {
    <S> Session beforePieces(NativeFitChunk<S> chunk);

    /** The modules of {@code engine}'s pack, fitting in its world. */
    static NativeStructureFitting of(Engine engine) {
        return ModuleStructureFitting.of(engine);
    }

    /**
     * Iris's own modules and {@code modules} outside any world, for starts whose fit their memo records and whose fit uses no palette;
     * a fit that needs the world or the pack's resources fails. For tools and tests that drive the platform side alone.
     */
    static NativeStructureFitting ofModules(IrisModule... modules) {
        return ModuleStructureFitting.ofModules(modules);
    }

    /** One chunk's fit, between its starts' placements. */
    interface Session {
        /** Called after the platform placed the pieces of the chunk's start number {@code startIndex}. */
        void afterPieces(int startIndex);
    }
}
