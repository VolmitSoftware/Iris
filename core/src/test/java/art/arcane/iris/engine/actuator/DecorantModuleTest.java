package art.arcane.iris.engine.actuator;

import art.arcane.iris.engine.framework.Engine;
import art.arcane.iris.engine.framework.EngineMetrics;
import art.arcane.iris.engine.framework.SeedManager;
import art.arcane.iris.engine.object.world.biome.IrisBiome;
import art.arcane.iris.engine.object.world.dimension.IrisDimension;
import art.arcane.iris.spi.PlatformBlockState;
import art.arcane.iris.util.project.context.ChunkContext;
import art.arcane.iris.util.project.context.ChunkedDataCache;
import art.arcane.iris.util.project.hunk.Hunk;
import art.arcane.volmlib.util.collection.KList;
import org.junit.Test;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** Decorating reads nothing of the chunk when the dimension does not decorate, and skips biomes without decorators. */
public class DecorantModuleTest {
    @Test
    public void aDimensionThatDoesNotDecorateIsLeftAlone() {
        DecorantModule.Decorating decorating = new DecorantModule.Decorating(engine(false));
        ChunkContext context = mock(ChunkContext.class);

        decorating.decorate(0, 0, Hunk.newArrayHunk(16, 16, 16), context);

        verifyNoInteractions(context);
    }

    @Test
    @SuppressWarnings("unchecked")
    public void aBiomeWithoutDecoratorsGetsNothingPlaced() {
        DecorantModule.Decorating decorating = new DecorantModule.Decorating(engine(true));
        IrisBiome biome = mock(IrisBiome.class);
        when(biome.getDecorators()).thenReturn(new KList<>());
        ChunkedDataCache<IrisBiome> biomes = mock(ChunkedDataCache.class);
        when(biomes.get(anyInt(), anyInt())).thenReturn(biome);
        ChunkContext context = mock(ChunkContext.class);
        when(context.getBiome()).thenReturn(biomes);
        Hunk<PlatformBlockState> output = mock(Hunk.class);
        when(output.getWidth()).thenReturn(16);
        when(output.getDepth()).thenReturn(16);

        decorating.decorate(0, 0, output, context);

        verify(output, never()).set(anyInt(), anyInt(), anyInt(), org.mockito.ArgumentMatchers.any());
        verify(biomes, org.mockito.Mockito.times(16 * 16)).get(anyInt(), anyInt());
    }

    private Engine engine(boolean decorate) {
        IrisDimension dimension = mock(IrisDimension.class);
        when(dimension.isDecorate()).thenReturn(decorate);
        SeedManager seeds = mock(SeedManager.class);
        Engine engine = mock(Engine.class);
        when(engine.getDimension()).thenReturn(dimension);
        when(engine.getSeedManager()).thenReturn(seeds);
        when(engine.getMetrics()).thenReturn(new EngineMetrics(16));
        return engine;
    }
}
