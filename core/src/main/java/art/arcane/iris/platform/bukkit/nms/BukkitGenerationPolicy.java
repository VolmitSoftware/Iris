package art.arcane.iris.platform.bukkit.nms;

import art.arcane.iris.generation.context.IrisContext;
import art.arcane.iris.generation.runtime.Engine;
import art.arcane.iris.generation.runtime.GenerationSessionException;
import art.arcane.iris.generation.runtime.GenerationSessionLease;
import art.arcane.iris.generation.runtime.IrisEngine;
import art.arcane.iris.platform.generation.BukkitChunkGenerator;
import art.arcane.iris.world.history.GenerationHistoryRuntimeRouter;
import art.arcane.iris.world.history.NativeTerrainReceipt;
import art.arcane.iris.world.history.SavedTerrainChunk;
import art.arcane.volmlib.nativelib.terrain.NativeGenerationLease;
import art.arcane.volmlib.nativelib.terrain.NativeTerrainPipelinePolicy;
import org.bukkit.NamespacedKey;

import java.io.IOException;

public class BukkitGenerationPolicy
        implements NativeTerrainPipelinePolicy<GenerationHistoryRuntimeRouter.RuntimeRoute> {
    private static final NamespacedKey NATURAL_TERRAIN = new NamespacedKey("iris", "natural_terrain");

    private final Engine engine;
    private final BukkitChunkGenerator generator;

    public BukkitGenerationPolicy(Engine engine, BukkitChunkGenerator generator) {
        this.engine = engine;
        this.generator = generator;
    }

    @Override
    public BukkitChunkGenerator.GenerationStagePermit acquireStage(String operation) {
        return generator == null
                ? BukkitChunkGenerator.GenerationStagePermit.noop()
                : generator.acquireGenerationStage(operation);
    }

    @Override
    public GenerationSessionLease acquireLease(String operation) {
        try {
            return engine.acquireGenerationLease(operation);
        } catch (GenerationSessionException exception) {
            throw new IllegalStateException("Iris " + operation + " could not acquire its engine runtime.", exception);
        }
    }

    @Override
    public GenerationHistoryRuntimeRouter.RuntimeRoute openRoute(int chunkX, int chunkZ, String operation) {
        if (!(engine instanceof IrisEngine irisEngine)) {
            return null;
        }
        GenerationHistoryRuntimeRouter router = irisEngine.getGenerationHistoryRuntimeRouter().orElse(null);
        if (router == null) {
            if (generator != null && generator.getGenerationHistory() != null) {
                throw new IllegalStateException("Iris " + operation + " has no generation-history runtime router.");
            }
            return null;
        }
        try {
            return router.openRoute(chunkX, chunkZ);
        } catch (IOException failure) {
            throw new IllegalStateException("Iris " + operation
                    + " could not route generation history for chunk " + chunkX + "," + chunkZ + ".", failure);
        }
    }

    @Override
    public IrisContext.Scope openContext(NativeGenerationLease lease) {
        return IrisContext.open(engine, lease.sessionId(), null);
    }

    @Override
    public boolean allowsChunkWrite(int chunkX, int chunkZ) {
        return engine.getComplex().allowsMantleChunkWrite(chunkX, chunkZ);
    }

    @Override
    public boolean usesFlatTerrain() {
        return generator != null && generator.usesFlatStudioTerrain();
    }

    @Override
    public int minimumY() {
        return engine.getMinHeight();
    }

    @Override
    public int terrainHeight(int blockX, int blockZ, boolean floor) {
        return engine.getHeight(blockX, blockZ, floor);
    }

    @Override
    public NamespacedKey naturalTerrainKey() {
        return NATURAL_TERRAIN;
    }

    @Override
    public byte[] naturalTerrainReceipt(GenerationHistoryRuntimeRouter.RuntimeRoute route) throws IOException {
        if (route.naturalTerrain().isEmpty()) {
            return null;
        }
        SavedTerrainChunk terrain = route.naturalTerrain().orElseThrow();
        return NativeTerrainReceipt.encode(terrain, route.activation().activationId(), route.epoch().epochId());
    }
}
