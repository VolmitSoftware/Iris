package art.arcane.iris.platform.bukkit.nms;

import art.arcane.volmlib.nativelib.terrain.NativeGenerationRegistry;
import art.arcane.volmlib.nativelib.terrain.NativeTerrainAccess;
import art.arcane.volmlib.nativelib.terrain.NativeTerrainSnapshots;
import art.arcane.volmlib.nativelib.terrain.NativeWorldGeneration;
import art.arcane.volmlib.nativelib.terrain.NativeWorldLifecycleFactory;
import org.bukkit.World;
import org.junit.Test;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class BukkitBindingTest {
    @Test
    public void chunkCapabilitiesOverrideLimitedBindingDefaults() {
        Fixture fixture = new Fixture();
        World world = mock(World.class);
        when(fixture.terrain.pollChunkTask(world)).thenReturn(true);
        when(fixture.terrain.forceEvictChunk(world, 2, 3)).thenReturn(true);
        assertTrue(fixture.binding.pollChunkTask(world));
        assertTrue(fixture.binding.forceEvictChunk(world, 2, 3));
        fixture.binding.flushChunkIO(world);
        verify(fixture.terrain).flushChunkIO(world);
    }

    @Test
    public void lifecycleCapabilitiesOverrideLimitedBindingDefaults() {
        Fixture fixture = new Fixture();
        when(fixture.lifecycle.isServerStopping()).thenReturn(true);
        assertTrue(fixture.binding.isServerStopping());
        fixture.binding.deferPluginClassLoaderClose();
        fixture.binding.releasePluginClassLoaderClose();
        verify(fixture.lifecycle).deferPluginClassLoaderClose();
        verify(fixture.lifecycle).releasePluginClassLoaderClose();
    }

    private static final class Fixture {
        private final NativeTerrainAccess terrain = mock(NativeTerrainAccess.class);
        private final NativeWorldLifecycleFactory.Controller lifecycle = mock(NativeWorldLifecycleFactory.Controller.class);
        private final BukkitBinding binding;

        private Fixture() {
            NativeWorldGeneration generation = mock(NativeWorldGeneration.class);
            when(generation.lifecycle(BukkitWorldLifecyclePolicy.INSTANCE)).thenReturn(lifecycle);
            binding = new BukkitBinding(new BukkitBinding.Capabilities(terrain, generation,
                    mock(NativeTerrainSnapshots.class), mock(NativeGenerationRegistry.class)));
        }
    }
}
