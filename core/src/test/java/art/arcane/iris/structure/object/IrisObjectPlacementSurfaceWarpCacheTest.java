package art.arcane.iris.structure.object;

import art.arcane.iris.generation.noise.IrisGeneratorStyle;
import art.arcane.iris.generation.noise.NoiseStyle;

import art.arcane.iris.pack.loading.IrisData;
import art.arcane.iris.generation.runtime.Engine;
import art.arcane.iris.generation.runtime.SeedManager;
import art.arcane.volmlib.util.noise.CNG;
import art.arcane.volmlib.util.math.RNG;
import org.junit.Test;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class IrisObjectPlacementSurfaceWarpCacheTest {
    @Test
    public void surfaceWarpSeparatesEngineSeeds() {
        Engine first = engine(100L);
        Engine second = engine(200L);
        AtomicReference<Engine> active = new AtomicReference<>(first);
        IrisData data = mock(IrisData.class);
        when(data.getEngine()).thenAnswer(ignored -> active.get());
        IrisObjectPlacement placement = placement();

        CNG firstWarp = placement.getSurfaceWarp(new RNG(1L), data);
        assertSame(firstWarp, placement.getSurfaceWarp(new RNG(999L), data));
        active.set(second);

        assertNotSame(firstWarp, placement.getSurfaceWarp(new RNG(1L), data));
    }

    @Test
    public void standaloneSurfaceWarpUsesSuppliedSeed() {
        IrisData data = mock(IrisData.class);
        IrisObjectPlacement placement = placement();

        CNG first = placement.getSurfaceWarp(new RNG(1L), data);

        assertSame(first, placement.getSurfaceWarp(new RNG(1L), data));
        assertNotSame(first, placement.getSurfaceWarp(new RNG(2L), data));
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
