package art.arcane.iris.core.modules;

import art.arcane.iris.builtin.IrisSeedPurposes;
import art.arcane.iris.core.loader.IrisData;
import art.arcane.iris.engine.framework.Engine;
import art.arcane.iris.engine.framework.SeedManager;
import art.arcane.iris.engine.object.world.dimension.IrisDimension;
import com.volmit.random.WorldRandom;
import com.volmit.resource.ResourceId;
import com.volmit.world.PackWorld;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** An Iris world seeds every purpose by the API's rule, Iris's own purposes included. */
public class IrisPackWorldSeedTest {
    private static final long WORLD_SEED = 42L;

    @Test
    public void irisPurposesFollowTheApisRule() {
        PackWorld world = new IrisPackWorld(engine());

        assertEquals(WorldRandom.purposeSeed(WORLD_SEED, IrisSeedPurposes.TERRAIN), world.seed(IrisSeedPurposes.TERRAIN));
        assertEquals(WorldRandom.purposeSeed(WORLD_SEED, IrisSeedPurposes.OBJECT_WARP), world.seed(IrisSeedPurposes.OBJECT_WARP));
    }

    @Test
    public void otherPurposesFollowTheApisRule() {
        ResourceId patches = new ResourceId("example", "moss/patches");

        assertEquals(WorldRandom.purposeSeed(WORLD_SEED, patches), new IrisPackWorld(engine()).seed(patches));
    }

    private static Engine engine() {
        SeedManager seeds = mock(SeedManager.class);
        when(seeds.getSeed()).thenReturn(WORLD_SEED);
        Engine engine = mock(Engine.class);
        when(engine.getData()).thenReturn(mock(IrisData.class));
        when(engine.getDimension()).thenReturn(new IrisDimension());
        when(engine.getSeedManager()).thenReturn(seeds);
        return engine;
    }
}
