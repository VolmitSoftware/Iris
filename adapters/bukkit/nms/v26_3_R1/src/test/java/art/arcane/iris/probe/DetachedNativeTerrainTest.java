package art.arcane.iris.probe;

import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.status.ChunkPyramid;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.chunk.status.ChunkStep;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public final class DetachedNativeTerrainTest {
    @BeforeClass
    public static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    public void fullRegionPlanSatisfiesEveryNativeStageDependency() {
        List<NativeRegionPlan.PlannedChunk> plan = NativeRegionPlan.plan(-32, 64, 32);
        Map<Long, ChunkStatus> statuses = new HashMap<>();
        int terrain = 0;
        for (NativeRegionPlan.PlannedChunk chunk : plan) {
            statuses.put(ChunkPos.pack(chunk.x(), chunk.z()), chunk.status());
            if (chunk.status() == ChunkStatus.TERRAIN) {
                terrain++;
            }
        }
        assertEquals(1296, terrain);
        assertEquals(2916, plan.size());
        assertTrue(plan.size() < 4096);
        for (NativeRegionPlan.PlannedChunk chunk : plan) {
            for (ChunkStatus status : ChunkStatus.getStatusList()) {
                if (status == ChunkStatus.EMPTY || status.isAfter(chunk.status())) {
                    continue;
                }
                ChunkStep step = ChunkPyramid.GENERATION_PYRAMID.getStepTo(status);
                int radius = step.directDependencies().size() - 1;
                for (int z = -radius; z <= radius; z++) {
                    for (int x = -radius; x <= radius; x++) {
                        ChunkStatus required = step.directDependencies().get(Math.max(Math.abs(x), Math.abs(z)));
                        ChunkStatus available = statuses.get(ChunkPos.pack(chunk.x() + x, chunk.z() + z));
                        assertTrue("Missing native stage dependency", available != null && available.isOrAfter(required));
                    }
                }
            }
        }
    }
}
