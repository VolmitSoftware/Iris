package art.arcane.iris.platform.bukkit.nms;

import art.arcane.iris.generation.context.IrisContext;
import art.arcane.iris.generation.runtime.Engine;
import art.arcane.iris.generation.runtime.GenerationSessionException;
import art.arcane.iris.generation.runtime.GenerationSessionLease;
import art.arcane.iris.generation.runtime.IrisEngine;
import art.arcane.iris.platform.generation.BukkitChunkGenerator;
import art.arcane.iris.studio.generation.JigsawStudioGenerator;
import art.arcane.iris.world.history.GenerationHistoryRuntimeRouter;
import art.arcane.iris.world.history.TerrainBoundarySignature;
import art.arcane.iris.world.history.TerrainNativeBlockKeys;
import art.arcane.volmlib.nativelib.terrain.NativeBlockColumn;
import art.arcane.volmlib.nativelib.terrain.NativeTerrainColumnPolicy;

import java.io.IOException;
import java.util.Optional;

public final class BukkitTerrainColumnPolicy implements NativeTerrainColumnPolicy {
    private final Engine engine;
    private final BukkitChunkGenerator generator;

    public BukkitTerrainColumnPolicy(Engine engine, BukkitChunkGenerator generator) {
        this.engine = engine;
        this.generator = generator;
    }

    @Override
    public boolean usesFlatTerrain() {
        return generator != null && generator.usesFlatStudioTerrain();
    }

    @Override
    public int flatBaseHeight() {
        return generator.getAuthoringBaseHeight();
    }

    @Override
    public FlatColumn flatColumn(int blockX, int blockZ) {
        String block = generator.isJigsawStudioActive() && JigsawStudioGenerator.isLightFloor(blockX, blockZ)
                ? "minecraft:smooth_stone" : "minecraft:polished_deepslate";
        return new FlatColumn(generator.getAuthoringFloorY(), block);
    }

    @Override
    public ColumnSession openQuery(Query query) {
        return new Session(query);
    }

    @Override
    public String placementKey(String stateKey) {
        return TerrainNativeBlockKeys.placementKey(stateKey);
    }

    private GenerationHistoryRuntimeRouter.CoordinateScope openHistory(Query query) {
        if (!(engine instanceof IrisEngine irisEngine)) {
            return null;
        }
        try {
            return irisEngine.openGenerationHistoryCoordinateScope(query.blockX(), query.blockZ());
        } catch (IOException failure) {
            throw new IllegalStateException("Iris " + query.operation() + " could not route generation history at "
                    + query.blockX() + "," + query.blockZ() + ".", failure);
        }
    }

    private final class Session implements ColumnSession {
        private final Query query;
        private final GenerationHistoryRuntimeRouter.CoordinateScope history;
        private final GenerationSessionLease lease;
        private final IrisContext.Scope context;

        private Session(Query query) {
            this.query = query;
            history = openHistory(query);
            GenerationSessionLease acquired = null;
            try {
                acquired = engine.acquireGenerationLease(query.operation());
                context = IrisContext.open(engine, acquired.sessionId(), null);
                lease = acquired;
            } catch (GenerationSessionException | RuntimeException | Error failure) {
                try (history; GenerationSessionLease cleanup = acquired) {
                    if (failure instanceof GenerationSessionException) {
                        throw new IllegalStateException("Iris " + query.operation()
                                + " could not acquire its engine runtime.", failure);
                    }
                    if (failure instanceof RuntimeException runtime) {
                        throw runtime;
                    }
                    throw (Error) failure;
                }
            }
        }

        @Override
        public int runtimeId() {
            return engine.getCacheID();
        }

        @Override
        public Optional<? extends NativeBlockColumn> resolvedColumn() {
            return engine.getComplex().resolvedTerrainColumn(query.blockX(), query.blockZ())
                    .map(TerrainBoundarySignature::geometry);
        }

        @Override
        public int height(boolean ignoreFluid) {
            return engine.getDimensionStackContext() == null
                    ? engine.getHeight(query.blockX(), query.blockZ(), ignoreFluid)
                    : Engine.hostHeight(engine, query.blockX(), query.blockZ(), ignoreFluid);
        }

        @Override
        public void close() {
            try (history; lease; context) {
            }
        }
    }
}
