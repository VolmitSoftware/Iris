package art.arcane.iris.structure.nativegen;

import art.arcane.iris.generation.runtime.Engine;
import art.arcane.iris.spi.IrisLogging;
import art.arcane.volmlib.nativelib.terrain.structure.StructureVolumePolicy;

import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public enum IrisStructureVolumePolicy implements StructureVolumePolicy<Engine, NativeStructureStartPlan> {
    INSTANCE;

    private static final Set<String> WARNED_RESOLUTION_FAILURES = ConcurrentHashMap.newKeySet();

    @Override
    public List<NativeStructureStartPlan> plansAt(Engine engine, int chunkX, int chunkZ) {
        return NativeStructurePlacementPlanner.plansAt(engine, chunkX, chunkZ);
    }

    @Override
    public IrisNativeStructureDecision decisionFor(Engine engine, NativeStructureStartPlan plan) {
        return NativeStructurePlacementPlanner.decisionFor(plan);
    }

    @Override
    public IrisNativeStructureDecision resolve(Engine engine, String structureKey, boolean underground) {
        return NativeStructureGenerationPolicy.resolve(engine, structureKey, underground);
    }

    @Override
    public int surfaceHeight(Engine engine, int blockX, int blockZ) {
        return Engine.hostHeight(engine, blockX, blockZ, true) + engine.getMinHeight();
    }

    @Override
    public void warn(String structureKey, Throwable error) {
        if (WARNED_RESOLUTION_FAILURES.add(structureKey)) {
            IrisLogging.reportError("Native structure volume resolution failed for '" + structureKey
                    + "'; objects will not be vetoed against it.", error);
        }
    }
}
