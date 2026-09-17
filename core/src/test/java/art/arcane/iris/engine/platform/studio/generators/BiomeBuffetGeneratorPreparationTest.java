package art.arcane.iris.engine.platform.studio.generators;

import art.arcane.iris.engine.framework.Engine;
import art.arcane.iris.engine.object.world.biome.IrisBiome;
import art.arcane.iris.engine.object.world.dimension.IrisDimension;
import art.arcane.iris.spi.PlatformBlockState;
import art.arcane.iris.util.common.data.B;
import art.arcane.volmlib.util.collection.KList;
import org.junit.Test;
import org.mockito.MockedStatic;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class BiomeBuffetGeneratorPreparationTest {
    @Test
    public void focusHotloadPreparationIsOptedInAndIdempotent() {
        Engine engine = mock(Engine.class);
        IrisDimension dimension = mock(IrisDimension.class);
        IrisBiome biome = mock(IrisBiome.class);
        PlatformBlockState barrier = mock(PlatformBlockState.class);
        AtomicReference<String> focus = new AtomicReference<>("");
        when(engine.getDimension()).thenReturn(dimension);
        when(dimension.getAllBiomes(engine)).thenReturn(new KList<>(biome));
        when(dimension.getFocus()).thenAnswer(invocation -> focus.get());
        when(dimension.setFocus(anyString())).thenAnswer(invocation -> {
            focus.set(invocation.getArgument(0, String.class));
            return dimension;
        });
        when(biome.getLoadKey()).thenReturn("prepared_biome");

        try (MockedStatic<B> blocks = mockStatic(B.class)) {
            blocks.when(() -> B.getState("BARRIER")).thenReturn(barrier);
            BiomeBuffetGenerator generator = new BiomeBuffetGenerator(engine, 1);

            assertTrue(generator.requiresPreSessionPreparation());
            generator.prepareChunkBeforeSession(engine, 0, 0);
            generator.prepareChunkBeforeSession(engine, 0, 0);
            generator.prepareChunkBeforeSession(engine, 2, 0);
        }

        verify(dimension, times(1)).setFocus("prepared_biome");
        verify(engine, times(1)).hotloadComplex();
    }
}
