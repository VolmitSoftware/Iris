package art.arcane.iris.engine.modifier;

import art.arcane.iris.core.modules.ModuleSettings;
import art.arcane.iris.engine.decorator.DecoratorTestEngines;
import art.arcane.iris.engine.framework.Engine;
import art.arcane.iris.engine.framework.EngineMetrics;
import art.arcane.iris.engine.framework.SeedManager;
import art.arcane.iris.engine.object.terrain.floating.FloatingIslandBoundarySampler;
import art.arcane.iris.engine.object.world.biome.IrisBiome;
import art.arcane.iris.spi.PlatformBlockState;
import art.arcane.iris.util.project.context.ChunkContext;
import art.arcane.iris.util.project.hunk.Hunk;
import com.volmit.host.ModuleLogs;
import org.junit.Test;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** A column whose biome declares no floating child biomes gets no island: nothing is written or decorated. */
public class FloatingIslandsTest {
    private static final int CHUNK_ORIGIN = 32;

    @Test
    @SuppressWarnings("unchecked")
    public void columnsWithoutFloatingChildBiomesAreLeftAlone() {
        FloatingIslands islands = new FloatingIslands(engine(), ModuleLogs.discarding());
        IrisBiome biome = mock(IrisBiome.class);
        when(biome.getModuleSettings()).thenReturn(ModuleSettings.NONE);
        FloatingIslandBoundarySampler sampler = mock(FloatingIslandBoundarySampler.class);
        when(sampler.parent(anyInt(), anyInt())).thenReturn(biome);
        ChunkContext context = mock(ChunkContext.class);
        when(context.getFloatingIslandBoundarySampler()).thenReturn(sampler);
        Hunk<PlatformBlockState> output = mock(Hunk.class);
        when(output.getHeight()).thenReturn(64);

        islands.fillSolids(CHUNK_ORIGIN, CHUNK_ORIGIN, output, false, context);
        islands.decorateColumns(CHUNK_ORIGIN, CHUNK_ORIGIN, output, false, context, null);

        verify(sampler, times(2)).parent(CHUNK_ORIGIN + 15, CHUNK_ORIGIN + 15);
        verify(output, never()).set(anyInt(), anyInt(), anyInt(), any());
        verify(output, never()).setRaw(anyInt(), anyInt(), anyInt(), any());
    }

    private Engine engine() {
        SeedManager seeds = mock(SeedManager.class);
        Engine engine = mock(Engine.class);
        when(engine.getSeedManager()).thenReturn(seeds);
        when(engine.getMetrics()).thenReturn(new EngineMetrics(16));
        DecoratorTestEngines.withGeneration(engine);
        return engine;
    }
}
