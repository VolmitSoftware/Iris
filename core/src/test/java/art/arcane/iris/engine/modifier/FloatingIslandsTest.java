package art.arcane.iris.engine.modifier;

import art.arcane.iris.builtin.floating.FloatingHost;
import art.arcane.iris.builtin.objects.ObjectHost;
import art.arcane.iris.engine.decorator.BiomeDecorators;
import com.volmit.block.BlockState;
import com.volmit.config.FeatureSettings;
import com.volmit.config.PackSettings;
import com.volmit.config.SettingsStack;
import com.volmit.host.ModuleLogs;
import com.volmit.hunk.Hunk;
import com.volmit.resource.PackResources;
import com.volmit.resource.ResourceId;
import com.volmit.stage.ChunkStageContext;
import com.volmit.world.PackWorld;
import com.volmit.world.RunningWorld;
import com.volmit.world.TerrainSampler;
import org.junit.Test;

import java.util.Optional;
import java.util.function.Function;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** A column whose biome declares no floating child biomes gets no island: nothing is written or decorated. */
public class FloatingIslandsTest {
    private static final int CHUNK_ORIGIN = 32;
    private static final ResourceId PLAIN_BIOME = ResourceId.parse("test:plain");

    @Test
    @SuppressWarnings("unchecked")
    public void columnsWithoutFloatingChildBiomesAreLeftAlone() {
        TerrainSampler layout = mock(TerrainSampler.class);
        when(layout.surfaceBiome(anyInt(), anyInt())).thenReturn(PLAIN_BIOME);
        PackWorld world = world(layout);
        Hunk<BlockState> output = mock(Hunk.class);
        when(output.height()).thenReturn(64);
        ChunkStageContext context = mock(ChunkStageContext.class, RETURNS_DEEP_STUBS);
        when(context.originX()).thenReturn(CHUNK_ORIGIN);
        when(context.originZ()).thenReturn(CHUNK_ORIGIN);
        when(context.world()).thenReturn(world);
        when(context.blocks()).thenReturn(output);
        FeatureSettings nothingConfigured = mock(FeatureSettings.class);
        when(nothingConfigured.at(any(), anyInt(), anyInt())).thenAnswer(invocation -> SettingsStack.empty());
        when(context.settings()).thenReturn(nothingConfigured);
        when(context.mantle()).thenReturn(null);
        FloatingIslands islands = FloatingIslands.of(world, ModuleLogs.discarding());

        islands.fillSolids(context);
        islands.decorateColumns(context);

        verify(layout, atLeastOnce()).surfaceBiome(CHUNK_ORIGIN + 15, CHUNK_ORIGIN + 15);
        verify(output, never()).set(anyInt(), anyInt(), anyInt(), any());
    }

    @SuppressWarnings("unchecked")
    private static PackWorld world(TerrainSampler layout) {
        RunningWorld running = mock(RunningWorld.class);
        when(running.layout()).thenReturn(layout);
        FeatureSettings nothingDeclaredByBiomes = mock(FeatureSettings.class);
        when(nothingDeclaredByBiomes.declaredBy(any(), any())).thenReturn(Optional.empty());
        when(running.settings()).thenReturn(nothingDeclaredByBiomes);
        PackWorld world = mock(PackWorld.class);
        when(world.running()).thenReturn(Optional.of(running));
        when(world.extension(FloatingHost.class)).thenReturn(Optional.of(mock(FloatingHost.class)));
        when(world.extension(ObjectHost.class)).thenReturn(Optional.of(mock(ObjectHost.class)));
        when(world.resources()).thenReturn(mock(PackResources.class));
        PackSettings nothingDeclared = mock(PackSettings.class);
        when(nothingDeclared.declared(any())).thenAnswer(invocation -> SettingsStack.empty());
        when(world.settings()).thenReturn(nothingDeclared);
        when(world.state(any(), any())).thenAnswer(invocation -> {
            Class<?> type = invocation.getArgument(0);
            if (type == BiomeDecorators.class) {
                return mock(BiomeDecorators.class);
            }
            return invocation.getArgument(1, Function.class).apply(world);
        });
        return world;
    }
}
