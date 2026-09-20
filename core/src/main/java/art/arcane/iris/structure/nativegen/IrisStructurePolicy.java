package art.arcane.iris.structure.nativegen;

import art.arcane.iris.generation.runtime.Engine;
import art.arcane.iris.spi.IrisLogging;
import art.arcane.volmlib.nativelib.terrain.structure.StructureFingerprint;
import art.arcane.volmlib.nativelib.terrain.structure.StructureInjectionPolicy;
import art.arcane.volmlib.nativelib.terrain.structure.StructureReferencePolicy;
import art.arcane.iris.generation.runtime.IrisEngine;
import art.arcane.iris.world.history.GenerationHistoryRuntimeRouter;
import java.io.IOException;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import java.util.List;
import java.util.Objects;

public record IrisStructurePolicy(Engine engine)
        implements StructureInjectionPolicy<NativeStructureStartPlan>,
        StructureReferencePolicy<NativeStructureStartPlan, NativeStructureOwnershipRecord> {
    private static final Set<String> WARNED_POLICY_INVALIDATIONS = ConcurrentHashMap.newKeySet();

    public IrisStructurePolicy {
        Objects.requireNonNull(engine, "Native structure injection requires an engine");
    }

    @Override
    public List<NativeStructureStartPlan> plansAt(int chunkX, int chunkZ) {
        return NativeStructurePlacementPlanner.plansAt(engine, chunkX, chunkZ);
    }

    @Override
    public boolean sourceReplaced(String structureKey, boolean underground) {
        return NativeStructureGenerationPolicy.resolve(engine, structureKey, underground).status()
                == NativeStructureGenerationStatus.REPLACED_BY_IRIS;
    }

    @Override
    public int surfaceHeight(int blockX, int blockZ) {
        return Engine.hostHeight(engine, blockX, blockZ, true) + engine.getMinHeight();
    }

    @Override
    public void record(NativeStructureStartPlan plan, StructureFingerprint fingerprint) {
        NativeStructureOwnershipStore.record(engine, NativeStructureOwnershipRecord.capture(fingerprint, plan));
    }

    @Override
    public void discard(String structureKey, int chunkX, int chunkZ) {
        NativeStructureOwnershipStore.discard(engine, structureKey, chunkX, chunkZ);
    }

    @Override
    public void duplicate(String structureKey) {
        IrisLogging.warn("Ignoring duplicate native structure placements for '" + structureKey
                + "'; the first deterministic candidate owns each chunk start");
    }
    @Override
    public NativeStructureStartPlan matchingPlan(String structureKey, int chunkX, int chunkZ) {
        return NativeStructurePlacementPlanner.matchingPlan(engine, structureKey, chunkX, chunkZ);
    }

    @Override
    public NativeStructureOwnershipRecord findPersisted(String structureKey, int chunkX, int chunkZ) {
        return NativeStructureOwnershipStore.findPersisted(engine, structureKey, chunkX, chunkZ);
    }

    @Override
    public NativeStructureOwnershipRecord capture(StructureFingerprint fingerprint, NativeStructureStartPlan plan) {
        return NativeStructureOwnershipRecord.capture(fingerprint, plan);
    }

    @Override
    public void record(NativeStructureOwnershipRecord ownership) {
        NativeStructureOwnershipStore.record(engine, ownership);
    }

    @Override
    public GenerationHistoryRuntimeRouter.CoordinateScope openOriginScope(int chunkX, int chunkZ) {
        if (!(engine instanceof IrisEngine irisEngine)) {
            return null;
        }
        try {
            return irisEngine.openGenerationHistoryCoordinateScope(chunkX << 4, chunkZ << 4);
        } catch (IOException failure) {
            throw new IllegalStateException("Unable to resolve native structure references for origin "
                    + chunkX + "," + chunkZ + " through generation history.", failure);
        }
    }

    @Override
    public IrisNativeStructureDecision resolve(String structureKey, boolean underground) {
        return NativeStructureGenerationPolicy.resolve(engine, structureKey, underground);
    }

    @Override
    public void invalidated(String structureKey) {
        if (WARNED_POLICY_INVALIDATIONS.add(structureKey)) {
            IrisLogging.warn("Invalidating persisted natural structure start '"
                    + structureKey + "' because the current Iris dimension policy disables it");
        }
    }

}
