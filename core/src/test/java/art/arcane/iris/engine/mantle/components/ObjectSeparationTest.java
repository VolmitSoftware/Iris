package art.arcane.iris.engine.mantle.components;

import art.arcane.iris.builtin.objects.ObjectPlacement;
import art.arcane.iris.builtin.objects.ObjectSettings;
import art.arcane.iris.modules.objects.ObjectSeparation;
import com.google.gson.Gson;
import com.volmit.config.FeatureSettings;
import com.volmit.config.Level;
import com.volmit.config.SettingsStack;
import com.volmit.resource.ResourceId;
import com.volmit.world.PackWorld;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class ObjectSeparationTest {
    private static final int SEPARATION_BLOCKS = 40;
    private static final int CHUNK_RANGE = 24;
    private static final String SEPARATED = "{\"place\": [\"trees/oak\"], \"chance\": 1, \"separation\": " + SEPARATION_BLOCKS + "}";

    @Test
    public void keptCandidatesNeverStandCloserThanTheSeparation() {
        ObjectPlacement placement = new Gson().fromJson(SEPARATED, ObjectPlacement.class);
        ObjectSeparation separation = ObjectSeparation.of(world(1337L));
        FeatureSettings settings = everywhere(placement);

        List<ObjectSeparation.Candidate> kept = new ArrayList<>();
        for (int chunkX = 0; chunkX < CHUNK_RANGE; chunkX++) {
            for (int chunkZ = 0; chunkZ < CHUNK_RANGE; chunkZ++) {
                separation.keptCandidate(chunkX, chunkZ, placement, settings).ifPresent(kept::add);
            }
        }

        for (int i = 0; i < kept.size(); i++) {
            for (int j = i + 1; j < kept.size(); j++) {
                long dx = kept.get(i).x() - kept.get(j).x();
                long dz = kept.get(i).z() - kept.get(j).z();
                assertTrue(kept.get(i) + " and " + kept.get(j), dx * dx + dz * dz >= (long) SEPARATION_BLOCKS * SEPARATION_BLOCKS);
            }
        }
        int areaBlocks = CHUNK_RANGE * 16 * CHUNK_RANGE * 16;
        int tightestPacking = areaBlocks / (SEPARATION_BLOCKS * SEPARATION_BLOCKS);
        assertTrue("only " + kept.size() + " of at most " + tightestPacking + " objects place", kept.size() * 3 >= tightestPacking);
    }

    @Test
    public void aChunkAgreesWithItselfWhicheverChunkAsksFirst() {
        ObjectPlacement placement = new Gson().fromJson(SEPARATED, ObjectPlacement.class);
        FeatureSettings settings = everywhere(placement);
        ObjectSeparation forward = ObjectSeparation.of(world(99L));
        ObjectSeparation backward = ObjectSeparation.of(world(99L));

        List<Boolean> forwardKept = new ArrayList<>();
        for (int chunk = 0; chunk < CHUNK_RANGE; chunk++) {
            forwardKept.add(forward.keptCandidate(chunk, 3, placement, settings).isPresent());
        }
        List<Boolean> backwardKept = new ArrayList<>();
        for (int chunk = CHUNK_RANGE - 1; chunk >= 0; chunk--) {
            backwardKept.addFirst(backward.keptCandidate(chunk, 3, placement, settings).isPresent());
        }

        assertEquals(forwardKept, backwardKept);
    }

    private static PackWorld world(long seed) {
        PackWorld world = mock(PackWorld.class);
        when(world.seed(any(ResourceId.class))).thenReturn(seed);
        when(world.state(eq(ObjectSeparation.class), any())).thenAnswer(invocation -> {
            Function<PackWorld, ?> create = invocation.getArgument(1);
            return create.apply(world);
        });
        return world;
    }

    @SuppressWarnings("unchecked")
    private static FeatureSettings everywhere(ObjectPlacement placement) {
        ObjectSettings objects = new Gson().fromJson("{\"objects\": [" + SEPARATED + "]}", ObjectSettings.class);
        FeatureSettings settings = mock(FeatureSettings.class);
        SettingsStack<ObjectSettings> stack = SettingsStack.of(List.of(new FeatureSettings.Entry<>(Level.BIOME, new ResourceId("test", "plains"), objects)));
        when(settings.at(eq(ObjectSettings.class), anyInt(), anyInt())).thenReturn(stack);
        return settings;
    }
}
