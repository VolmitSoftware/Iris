package art.arcane.iris.world.history;

import art.arcane.iris.generation.mantle.ObjectContinuationBundle;
import art.arcane.iris.generation.runtime.PackTransitions;
import art.arcane.volmlib.util.matter.Matter;

import java.io.IOException;
import java.util.List;

final class VersionedPackTransitions implements PackTransitions {
    private final AttachedRouter router;

    VersionedPackTransitions(AttachedRouter router) {
        this.router = router;
    }

    @Override
    public List<Matter> objectContinuations(TransitionGenerationPlan plan, int chunkX, int chunkZ) throws IOException {
        GenerationHistoryRuntimeRouter attached = router.attachedOrNull();
        if (attached == null) {
            return List.of();
        }
        List<GenerationHistoryRuntimeRouter.ObjectContinuation> fragments =
                attached.objectContinuations(plan, chunkX, chunkZ);
        return fragments.isEmpty() ? List.of() : attached.decodeObjectContinuations(fragments);
    }

    @Override
    public boolean allowsObjectFootprint(
            TransitionGenerationPlan plan, int minimumX, int minimumZ, int maximumX, int maximumZ
    ) {
        GenerationHistoryRuntimeRouter attached = router.attachedOrNull();
        return attached == null || attached.allowsObjectFootprint(plan,
                new ObjectContinuationBundle.Bounds(minimumX, minimumZ, maximumX, maximumZ));
    }

    @Override
    public void recordNaturalTerrain(SavedTerrainChunk terrain, FloatingBiomeOverlay floatingBiomes) {
        GenerationHistoryRuntimeRouter attached = router.attachedOrNull();
        if (attached == null) {
            return;
        }
        attached.recordNaturalTerrain(terrain);
        attached.recordFloatingBiomes(terrain.chunkX(), terrain.chunkZ(), floatingBiomes);
    }
}
