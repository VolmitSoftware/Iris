package art.arcane.iris.structure.object;

import art.arcane.iris.generation.runtime.PackRuntime;
import art.arcane.iris.testsupport.MockPackData;
import art.arcane.iris.generation.noise.IrisGeneratorStyle;
import art.arcane.iris.generation.noise.NoiseStyle;

import art.arcane.iris.pack.loading.IrisData;
import art.arcane.iris.generation.runtime.Engine;
import art.arcane.iris.generation.runtime.SeedManager;
import art.arcane.volmlib.util.noise.CNG;
import art.arcane.volmlib.util.math.RNG;
import org.junit.Test;

import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class IrisObjectPlacementSurfaceWarpCacheTest {
    @Test
    public void surfaceWarpSeparatesEngineSeeds() {
        IrisData data = MockPackData.create();
        PackRuntime first = PackRuntime.forEngine(engine(100L), data);
        PackRuntime second = PackRuntime.forEngine(engine(200L), data);
        IrisObjectPlacement placement = placement();

        CNG firstWarp = placement.getSurfaceWarp(new RNG(1L), first);
        assertSame(firstWarp, placement.getSurfaceWarp(new RNG(999L), first));

        assertNotSame(firstWarp, placement.getSurfaceWarp(new RNG(1L), second));
    }

    @Test
    public void standaloneSurfaceWarpUsesSuppliedSeed() {
        PackRuntime runtime = PackRuntime.standalone(MockPackData.create());
        IrisObjectPlacement placement = placement();

        CNG first = placement.getSurfaceWarp(new RNG(1L), runtime);

        assertSame(first, placement.getSurfaceWarp(new RNG(1L), runtime));
        assertNotSame(first, placement.getSurfaceWarp(new RNG(2L), runtime));
    }

    private IrisObjectPlacement placement() {
        return new IrisObjectPlacement().setWarp(new IrisGeneratorStyle(NoiseStyle.SIMPLEX));
    }

    private Engine engine(long componentSeed) {
        SeedManager seedManager = mock(SeedManager.class);
        when(seedManager.getComponent()).thenReturn(componentSeed);
        Engine engine = mock(Engine.class);
        when(engine.getSeedManager()).thenReturn(seedManager);
        return engine;
    }
}
