package art.arcane.iris.engine.object.placement;

import art.arcane.iris.builtin.objects.ObjectPlacement;
import art.arcane.iris.core.loader.IrisData;
import art.arcane.iris.core.modules.IrisPackResources;
import art.arcane.iris.engine.framework.Engine;
import art.arcane.iris.engine.framework.SeedManager;
import art.arcane.iris.util.project.noise.CNG;
import art.arcane.volmlib.util.math.RNG;
import com.volmit.noise.NoiseStyle;
import org.junit.Test;

import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class IrisPlacementFormSurfaceWarpTest {
    @Test
    public void surfaceWarpSeparatesEngineSeeds() {
        IrisData data = data();
        IrisPlacementForm form = IrisPlacementForm.of(placement(), data);

        Engine firstEngine = engine(100L);
        CNG firstWarp = form.surfaceWarp(new RNG(1L), data, firstEngine);
        assertSame(firstWarp, form.surfaceWarp(new RNG(999L), data, firstEngine));

        assertNotSame(firstWarp, form.surfaceWarp(new RNG(1L), data, engine(200L)));
    }

    @Test
    public void standaloneSurfaceWarpUsesSuppliedSeed() {
        IrisData data = data();
        IrisPlacementForm form = IrisPlacementForm.of(placement(), data);

        CNG first = form.surfaceWarp(new RNG(1L), data, null);

        assertSame(first, form.surfaceWarp(new RNG(1L), data, null));
        assertNotSame(first, form.surfaceWarp(new RNG(2L), data, null));
    }

    @Test
    public void theFormIsMadeOncePerPlacement() {
        IrisData data = data();
        ObjectPlacement placement = placement();

        assertSame(IrisPlacementForm.of(placement, data), IrisPlacementForm.of(placement, data));
    }

    private static ObjectPlacement placement() {
        return ObjectPlacement.builder().warp(NoiseStyle.of("SIMPLEX")).build();
    }

    private static IrisData data() {
        IrisData data = mock(IrisData.class);
        when(data.getPackResources()).thenReturn(mock(IrisPackResources.class));
        return data;
    }

    private static Engine engine(long componentSeed) {
        SeedManager seedManager = mock(SeedManager.class);
        when(seedManager.getComponent()).thenReturn(componentSeed);
        Engine engine = mock(Engine.class);
        when(engine.getSeedManager()).thenReturn(seedManager);
        return engine;
    }
}
