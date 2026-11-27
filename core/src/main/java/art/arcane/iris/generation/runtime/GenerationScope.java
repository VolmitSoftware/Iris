package art.arcane.iris.generation.runtime;

import art.arcane.volmlib.nativelib.terrain.NativeGenerationScope;

import java.io.IOException;

/**
 * Binds the calling thread to the pack that owns one column until closed.
 */
public interface GenerationScope extends NativeGenerationScope {
    /**
     * Records what the scoped chunk generated, when its pack keeps a record.
     *
     * @return true when this call recorded the chunk
     */
    boolean claimGeneratedSemantics() throws IOException;
}
