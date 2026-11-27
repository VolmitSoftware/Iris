package art.arcane.iris.generation.runtime;

import art.arcane.iris.world.history.FloatingBiomeOverlay;
import art.arcane.iris.world.history.SavedTerrainChunk;
import art.arcane.iris.world.history.TransitionGenerationPlan;
import art.arcane.volmlib.util.matter.Matter;

import java.io.IOException;
import java.util.List;

/**
 * Answers what generation reads and writes at the border between pack versions.
 */
public interface PackTransitions {
    /** Object fragments that earlier packs placed across the transition band into this chunk. */
    List<Matter> objectContinuations(TransitionGenerationPlan plan, int chunkX, int chunkZ) throws IOException;

    boolean allowsObjectFootprint(TransitionGenerationPlan plan, int minimumX, int minimumZ, int maximumX, int maximumZ);

    void recordNaturalTerrain(SavedTerrainChunk terrain, FloatingBiomeOverlay floatingBiomes);
}
