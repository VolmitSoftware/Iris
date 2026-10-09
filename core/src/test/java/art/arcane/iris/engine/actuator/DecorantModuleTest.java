package art.arcane.iris.engine.actuator;

import art.arcane.iris.core.loader.IrisData;
import art.arcane.iris.core.modules.ModuleSettings;
import art.arcane.iris.engine.framework.Engine;
import art.arcane.iris.engine.framework.EngineGeneration;
import art.arcane.iris.engine.framework.EngineMetrics;
import art.arcane.iris.engine.framework.SeedManager;
import art.arcane.iris.engine.object.world.biome.IrisBiome;
import art.arcane.iris.engine.object.world.dimension.IrisDimension;
import art.arcane.iris.spi.PlatformBlockState;
import art.arcane.iris.util.project.context.ChunkContext;
import art.arcane.iris.util.project.context.ChunkedDataCache;
import art.arcane.iris.util.project.hunk.Hunk;
import com.volmit.world.PackWorld;
import org.junit.Test;

import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Decorating reads nothing of the chunk when the dimension does not decorate, skips biomes without decorators, and is
 * kept per world in the world's state store.
 */
public class DecorantModuleTest {
    @Test
    public void eachWorldKeepsOneDecoratingInItsStateStore() {
        Engine engine = engine(true);
        when(engine.getData()).thenReturn(mock(IrisData.class));
        EngineGeneration generation = new EngineGeneration(engine);
        when(engine.getGeneration()).thenReturn(generation);
        PackWorld world = generation.world();

        DecorantModule.Decorating decorating = DecorantModule.decorating(world);

        assertSame(decorating, DecorantModule.decorating(world));
        assertSame("built-in stages share the store", decorating,
                generation.state(DecorantModule.Decorating.class, DecorantModule.Decorating::new));
        EngineGeneration otherGeneration = new EngineGeneration(engine);
        when(engine.getGeneration()).thenReturn(otherGeneration);
        assertNotSame(decorating, DecorantModule.decorating(otherGeneration.world()));
    }

    @Test
    public void aDimensionThatDoesNotDecorateIsLeftAlone() {
        DecorantModule.Decorating decorating = new DecorantModule.Decorating(engineWithGeneration(false));
        ChunkContext context = mock(ChunkContext.class);

        decorating.decorate(0, 0, Hunk.newArrayHunk(16, 16, 16), context);

        verifyNoInteractions(context);
    }

    @Test
    @SuppressWarnings("unchecked")
    public void aBiomeWithoutDecoratorsGetsNothingPlaced() {
        DecorantModule.Decorating decorating = new DecorantModule.Decorating(engineWithGeneration(true));
        IrisBiome biome = mock(IrisBiome.class);
        when(biome.getModuleSettings()).thenReturn(ModuleSettings.NONE);
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

    private Engine engineWithGeneration(boolean decorate) {
        Engine engine = engine(decorate);
        when(engine.getData()).thenReturn(mock(IrisData.class));
        when(engine.getGeneration()).thenReturn(new EngineGeneration(engine));
        return engine;
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
