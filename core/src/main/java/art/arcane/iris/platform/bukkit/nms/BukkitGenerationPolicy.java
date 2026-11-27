package art.arcane.iris.platform.bukkit.nms;

import art.arcane.iris.generation.context.IrisContext;
import art.arcane.iris.generation.runtime.Engine;
import art.arcane.iris.generation.runtime.GenerationSessionException;
import art.arcane.iris.generation.runtime.GenerationSessionLease;
import art.arcane.iris.generation.runtime.GenerationRoute;
import art.arcane.iris.generation.runtime.GenerationSource;
import art.arcane.iris.generation.runtime.IrisEngine;
import art.arcane.iris.platform.generation.BukkitChunkGenerator;
import art.arcane.volmlib.nativelib.terrain.NativeGenerationLease;
import art.arcane.volmlib.nativelib.terrain.NativeTerrainPipelinePolicy;
import org.bukkit.NamespacedKey;

import java.io.IOException;

public class BukkitGenerationPolicy
        implements NativeTerrainPipelinePolicy<GenerationRoute> {
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
    public GenerationRoute openRoute(int chunkX, int chunkZ, String operation) {
        if (!(engine instanceof IrisEngine irisEngine)) {
            return null;
        }
        GenerationSource source = irisEngine.getGenerationSource();
        if (!source.keepsPackVersions() && generator != null && generator.getGenerationHistory() != null) {
            throw new IllegalStateException("Iris " + operation + " has no generation-history runtime router.");
        }
        try {
            return source.openRoute(chunkX, chunkZ);
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
    public byte[] naturalTerrainReceipt(GenerationRoute route) throws IOException {
        return route.naturalTerrainReceipt();
    }
}
