package art.arcane.iris.engine.object.prefab;

import art.arcane.iris.engine.object.prefab.IrisObjectTransforms;
import art.arcane.iris.spi.PlatformBlockState;
import art.arcane.iris.util.common.data.B;
import art.arcane.volmlib.util.data.VectorMap;
import art.arcane.volmlib.util.math.IrisBlockVector;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;

import static org.junit.Assert.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.withSettings;
import static org.mockito.Mockito.when;

public class IrisObjectTransformsNearestBlockIndexTest {
    @Test
    public void spatialLookupMatchesLinearIterationIncludingDistanceTies() {
        VectorMap<PlatformBlockState> blocks = new VectorMap<>();
        Random random = new Random(812734L);
        for (int i = 0; i < 120; i++) {
            PlatformBlockState state = mock(PlatformBlockState.class, withSettings().stubOnly());
            blocks.put(new IrisBlockVector(
                    random.nextInt(25) - 12,
                    random.nextInt(25) - 12,
                    random.nextInt(25) - 12
            ), state);
        }

        PlatformBlockState explicitAir = mock(PlatformBlockState.class, withSettings().stubOnly());
        when(explicitAir.isAir()).thenReturn(true);
        blocks.put(new IrisBlockVector(0, 0, 0), explicitAir);
        IrisObjectTransforms.NearestBlockIndex index = IrisObjectTransforms.NearestBlockIndex.create(blocks);
        List<Map.Entry<IrisBlockVector, PlatformBlockState>> candidates = new ArrayList<>();
        for (Map.Entry<IrisBlockVector, PlatformBlockState> entry : blocks) {
            candidates.add(entry);
        }

        for (int x = -10; x <= 10; x++) {
            for (int y = -10; y <= 10; y++) {
                for (int z = -10; z <= 10; z++) {
                    IrisBlockVector query = new IrisBlockVector(x, y, z);
                    PlatformBlockState direct = blocks.get(query);
                    PlatformBlockState actual = B.isAir(direct) ? index.nearest(x, y, z, direct) : direct;
                    assertSame("Mismatch at " + x + "," + y + "," + z,
                            nearestByLinearIteration(blocks, candidates, query), actual);
                }
            }
        }
    }

    @Test
    public void emptyIndexRetainsMissingAndExplicitAirFallbacks() {
        VectorMap<PlatformBlockState> blocks = new VectorMap<>();
        PlatformBlockState explicitAir = mock(PlatformBlockState.class, withSettings().stubOnly());
        when(explicitAir.isAir()).thenReturn(true);
        blocks.put(new IrisBlockVector(1, 2, 3), explicitAir);
        IrisObjectTransforms.NearestBlockIndex index = IrisObjectTransforms.NearestBlockIndex.create(blocks);

        assertSame(explicitAir, index.nearest(1, 2, 3, explicitAir));
        assertSame(null, index.nearest(4, 5, 6, null));
    }

    private static PlatformBlockState nearestByLinearIteration(
            VectorMap<PlatformBlockState> blocks,
            List<Map.Entry<IrisBlockVector, PlatformBlockState>> candidates,
            IrisBlockVector query
    ) {
        PlatformBlockState result = blocks.get(query);
        if (!B.isAir(result)) {
            return result;
        }

        double nearestDistance = Double.MAX_VALUE;
        for (Map.Entry<IrisBlockVector, PlatformBlockState> entry : candidates) {
            PlatformBlockState state = entry.getValue();
            if (B.isAir(state)) {
                continue;
            }

            double distance = entry.getKey().distanceSquared(query);
            if (distance < nearestDistance) {
                nearestDistance = distance;
                result = state;
            }
        }
        return result;
    }
}
