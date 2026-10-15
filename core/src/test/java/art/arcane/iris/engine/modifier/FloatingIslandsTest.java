package art.arcane.iris.engine.modifier;

import art.arcane.iris.builtin.IrisSeed;
import art.arcane.iris.builtin.floating.FloatingHost;
import art.arcane.iris.builtin.objects.ObjectHost;
import art.arcane.iris.engine.decorator.BiomeDecorators;
import com.volmit.block.BlockState;
import com.volmit.config.FeatureSettings;
import com.volmit.config.PackSettings;
import com.volmit.host.ModuleLogs;
import com.volmit.hunk.Hunk;
import com.volmit.resource.PackResources;
import com.volmit.resource.ResourceId;
import com.volmit.stage.ChunkStageContext;
import com.volmit.world.PackWorld;
import org.junit.Test;

import java.util.function.Function;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

/** A column whose biome declares no floating child biomes gets no island: nothing is written or decorated. */
public class FloatingIslandsTest {
    private static final int CHUNK_ORIGIN = 32;
    private static final ResourceId PLAIN_BIOME = ResourceId.parse("test:plain");

    @Test
    @SuppressWarnings("unchecked")
    public void columnsWithoutFloatingChildBiomesAreLeftAlone() {
        FloatingHost host = mock(FloatingHost.class);
        when(host.surfaceBiome(anyInt(), anyInt())).thenReturn(PLAIN_BIOME);
        PackWorld world = world(host);
        Hunk<BlockState> output = mock(Hunk.class);
        when(output.getHeight()).thenReturn(64);
        ChunkStageContext context = mock(ChunkStageContext.class, RETURNS_DEEP_STUBS);
        when(context.originX()).thenReturn(CHUNK_ORIGIN);
        when(context.originZ()).thenReturn(CHUNK_ORIGIN);
        when(context.world()).thenReturn(world);
        when(context.hunk(BlockState.class)).thenReturn(output);
        when(context.settings()).thenReturn(mock(FeatureSettings.class));
        when(context.mantle()).thenReturn(null);
        FloatingIslands islands = FloatingIslands.of(world, ModuleLogs.discarding());

        islands.fillSolids(context);
        islands.decorateColumns(context);

        verify(host, org.mockito.Mockito.atLeastOnce()).surfaceBiome(CHUNK_ORIGIN + 15, CHUNK_ORIGIN + 15);
        verify(output, never()).set(anyInt(), anyInt(), anyInt(), any());
    }

    @SuppressWarnings("unchecked")
    private static PackWorld world(FloatingHost host) {
        PackWorld world = mock(PackWorld.class, withSettings().extraInterfaces(FloatingHost.Source.class, ObjectHost.Source.class, IrisSeed.Source.class));
        when(((FloatingHost.Source) world).floating()).thenReturn(host);
        when(((ObjectHost.Source) world).objects()).thenReturn(mock(ObjectHost.class));
        when(world.resources()).thenReturn(mock(PackResources.class));
        when(world.settings()).thenReturn(mock(PackSettings.class));
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
