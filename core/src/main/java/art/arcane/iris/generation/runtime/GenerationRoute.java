package art.arcane.iris.generation.runtime;

import art.arcane.volmlib.nativelib.terrain.NativeGenerationRoute;

import java.io.IOException;

/**
 * A native chunk generation pass routed to the activation that owns the chunk.
 */
public interface GenerationRoute extends NativeGenerationRoute {
    long activationId();

    /**
     * @return the receipt for the natural terrain this pass captured, or null when it captured none
     */
    byte[] naturalTerrainReceipt() throws IOException;
}
