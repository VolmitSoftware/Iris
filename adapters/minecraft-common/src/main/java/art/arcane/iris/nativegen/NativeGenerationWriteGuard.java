package art.arcane.iris.nativegen;

import art.arcane.iris.generation.runtime.DimensionStackContext;
import art.arcane.iris.generation.runtime.DimensionStackLayout;
import art.arcane.iris.generation.runtime.Engine;
import art.arcane.iris.generation.runtime.IrisEngine;
import art.arcane.iris.world.history.GenerationHistoryRuntimeRouter;
import art.arcane.iris.structure.object.IrisStaticObjectLayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.status.ChunkPyramid;
import net.minecraft.world.level.chunk.status.ChunkStatus;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;

public final class NativeGenerationWriteGuard {
    private NativeGenerationWriteGuard() {
    }

    public static boolean allowsDecoration(Engine engine, WorldGenLevel region, ChunkPos center) {
        int radius = ChunkPyramid.GENERATION_PYRAMID.getStepTo(ChunkStatus.FEATURES).blockStateWriteRadius();
        for (int offsetX = -radius; offsetX <= radius; offsetX++) {
            for (int offsetZ = -radius; offsetZ <= radius; offsetZ++) {
                int chunkX = Math.addExact(center.x(), offsetX);
                int chunkZ = Math.addExact(center.z(), offsetZ);
                ChunkAccess target = region.getChunk(chunkX, chunkZ);
                if (target.getPersistedStatus().isOrAfter(ChunkStatus.FULL)
                        && !engine.getComplex().allowsMantleChunkWrite(chunkX, chunkZ)) {
                    return false;
                }
            }
        }
        return true;
    }

    public static boolean isHistoricalStructure(Engine engine, long activation) {
        if (activation <= 0 || !(engine instanceof IrisEngine irisEngine)) {
            return false;
        }
        GenerationHistoryRuntimeRouter router = irisEngine.getGenerationHistoryRuntimeRouter().orElse(null);
        return router != null && activation < router.history().activeActivation().activationId();
    }

    public static boolean allowsPendingStage(Engine engine, ChunkAccess chunk, ChunkStatus stage) {
        return stage.isOrAfter(ChunkStatus.FEATURES)
                && chunk.getPersistedStatus().isOrAfter(ChunkStatus.NOISE)
                && !chunk.getPersistedStatus().isOrAfter(stage)
                && !engine.getComplex().allowsMantleChunkWrite(chunk.getPos().x(), chunk.getPos().z());
    }

    public static Predicate<BlockPos> protectedPositions(
            IrisStaticObjectLayer staticObjects,
            DimensionStackContext stackContext,
            int minimumY
    ) {
        Map<Long, DimensionStackLayout> layouts = new ConcurrentHashMap<>();
        return position -> {
            if (!staticObjects.isEmpty() && staticObjects.contains(
                    position.getX(), position.getY() - minimumY, position.getZ())) {
                return true;
            }
            if (stackContext == null) {
                return false;
            }
            long columnKey = ((long) position.getX() << 32)
                    ^ (position.getZ() & 0xFFFFFFFFL);
            DimensionStackLayout layout = layouts.computeIfAbsent(
                    columnKey,
                    ignored -> stackContext.sample(position.getX(), position.getZ())
            );
            return layout.isHostFeatureProtectedY(position.getY() - minimumY);
        };
    }
}
