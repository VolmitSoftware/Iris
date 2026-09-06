package art.arcane.iris.engine;

import art.arcane.iris.core.loader.IrisData;
import art.arcane.iris.engine.framework.BiomeEnvironment;
import art.arcane.iris.engine.framework.GenerationSessionLease;
import art.arcane.iris.engine.history.GenerationHistoryRuntimeRouter;
import art.arcane.iris.engine.history.SavedBiomeRuntime;
import art.arcane.iris.engine.object.IrisBiome;
import art.arcane.iris.engine.object.IrisDimension;
import art.arcane.iris.engine.object.IrisRegion;
import org.junit.Test;

import java.awt.Color;
import java.lang.reflect.Field;
import java.util.Optional;
import java.util.function.Function;

import static org.junit.Assert.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class IrisEnginePreviewTest {
    @Test
    public void previewRendersThePreparedEnvironmentWhileItIsRetained() throws Exception {
        IrisEngine engine = mock(IrisEngine.class);
        GenerationHistoryRuntimeRouter router = mock(GenerationHistoryRuntimeRouter.class);
        SavedBiomeRuntime biomes = mock(SavedBiomeRuntime.class);
        GenerationSessionLease lease = mock(GenerationSessionLease.class);
        BiomeEnvironment environment = new BiomeEnvironment(1L, mock(IrisBiome.class), mock(IrisRegion.class),
                mock(IrisDimension.class), mock(IrisData.class));
        Field field = IrisEngine.class.getDeclaredField("generationHistoryRuntimeRouter");
        field.setAccessible(true);
        field.set(engine, router);
        when(engine.acquireGenerationLease("pregen_preview")).thenReturn(lease);
        when(router.biomes()).thenReturn(biomes);
        when(biomes.readSurfaceBiome(eq(-24), eq(40), any())).thenAnswer(invocation -> {
            Function<Optional<BiomeEnvironment>, Color> reader = invocation.getArgument(2);
            Color color = reader.apply(Optional.of(environment));
            verify(engine).drawBiomeEnvironment(-24, 40, environment);
            return color;
        });
        when(engine.drawBiomeEnvironment(-24, 40, environment)).thenReturn(Color.RED);
        doCallRealMethod().when(engine).drawForPreview(-24, 40);

        assertSame(Color.RED, engine.drawForPreview(-24, 40));

        verify(lease).close();
        verify(engine, never()).getSurfaceBiomeEnvironment(anyInt(), anyInt());
        verify(engine, never()).getRegion(anyInt(), anyInt());
    }
}
