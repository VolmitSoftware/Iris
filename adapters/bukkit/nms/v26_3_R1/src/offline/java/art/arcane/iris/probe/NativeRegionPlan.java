package art.arcane.iris.probe;

import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.status.ChunkPyramid;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.chunk.status.ChunkStep;

import java.util.ArrayList;
import java.util.List;

final class NativeRegionPlan {
    private NativeRegionPlan() {
    }

    static List<PlannedChunk> plan(int chunkX, int chunkZ, int width) {
        if (width < 1 || width > 32) {
            throw new IllegalArgumentException("Native terrain tile width must be 1..32 chunks");
        }
        ChunkStep target = ChunkPyramid.GENERATION_PYRAMID.getStepTo(ChunkStatus.FULL);
        int halo = target.accumulatedDependencies().size() - 1;
        int maximumX = Math.addExact(chunkX, width - 1);
        int maximumZ = Math.addExact(chunkZ, width - 1);
        if (!new ChunkPos(Math.subtractExact(chunkX, halo), Math.subtractExact(chunkZ, halo)).isValid()
                || !new ChunkPos(Math.addExact(maximumX, halo), Math.addExact(maximumZ, halo)).isValid()) {
            throw new IllegalArgumentException("Native terrain tile exceeds supported chunk bounds");
        }
        List<PlannedChunk> result = new ArrayList<>((width + halo * 2) * (width + halo * 2));
        for (int z = Math.subtractExact(chunkZ, halo); z <= Math.addExact(maximumZ, halo); z++) {
            for (int x = Math.subtractExact(chunkX, halo); x <= Math.addExact(maximumX, halo); x++) {
                int distance = Math.max(distance(x, chunkX, maximumX), distance(z, chunkZ, maximumZ));
                ChunkStatus required = distance == 0 ? ChunkStatus.FULL : target.accumulatedDependencies().get(distance);
                ChunkStatus status = required.isAfter(ChunkStatus.TERRAIN) ? ChunkStatus.TERRAIN : required;
                result.add(new PlannedChunk(x, z, status, distance == 0));
            }
        }
        return List.copyOf(result);
    }

    static int terrainHalo() {
        ChunkStep target = ChunkPyramid.GENERATION_PYRAMID.getStepTo(ChunkStatus.FULL);
        int radius = 0;
        for (int distance = 1; distance < target.accumulatedDependencies().size(); distance++) {
            if (target.accumulatedDependencies().get(distance).isOrAfter(ChunkStatus.TERRAIN)) {
                radius = distance;
            }
        }
        return radius;
    }

    private static int distance(int value, int minimum, int maximum) {
        return Math.max(0, Math.max(minimum - value, value - maximum));
    }

    record PlannedChunk(int x, int z, ChunkStatus status, boolean target) {
    }
}
