package art.arcane.iris.platform.bukkit.nms;

import art.arcane.iris.generation.runtime.Engine;
import art.arcane.iris.generation.terrain.IrisDimension;
import art.arcane.iris.generation.terrain.IrisDimensionRuntimeContract;
import art.arcane.iris.world.IrisEnvironment;
import art.arcane.volmlib.nativelib.terrain.NativeGenerationRegistry;
import art.arcane.volmlib.nativelib.terrain.NativeTerrainAccess;
import art.arcane.volmlib.nativelib.terrain.NativeTerrainSnapshots;
import art.arcane.volmlib.nativelib.terrain.NativeWorldGeneration;
import art.arcane.volmlib.nativelib.terrain.NativeWorldLifecycleFactory;
import org.bukkit.World;
import org.junit.Test;

import java.util.Locale;

import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
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
        when(fixture.lifecycle.deferPluginClassLoaderClose()).thenReturn(true);
        assertTrue(fixture.binding.deferPluginClassLoaderClose());
        fixture.binding.releasePluginClassLoaderClose();
        verify(fixture.lifecycle).deferPluginClassLoaderClose();
        verify(fixture.lifecycle).releasePluginClassLoaderClose();
    }

    @Test
    public void injectAttachesVanillaSpawnersOnlyToOverworldEnvironments() throws NoSuchFieldException, IllegalAccessException {
        Fixture fixture = new Fixture();
        World overworld = injectedWorld(fixture, IrisEnvironment.NORMAL);
        World nether = injectedWorld(fixture, IrisEnvironment.NETHER);
        verify(fixture.generation).attachOverworldSpawners(overworld);
        verify(fixture.generation, never()).attachOverworldSpawners(nether);
    }

    private static World injectedWorld(Fixture fixture, IrisEnvironment environment) throws NoSuchFieldException, IllegalAccessException {
        IrisDimension dimension = new IrisDimension();
        dimension.setLoadKey(environment.name().toLowerCase(Locale.ROOT));
        dimension.setEnvironment(environment);
        IrisDimensionRuntimeContract contract = IrisDimensionRuntimeContract.expected(dimension, "iris");
        Engine engine = mock(Engine.class);
        when(engine.getDimension()).thenReturn(dimension);
        World world = mock(World.class);
        when(world.getMinHeight()).thenReturn(contract.minHeight());
        when(world.getMaxHeight()).thenReturn(contract.minHeight() + contract.height());
        when(fixture.generation.dimension(world)).thenReturn(new NativeWorldGeneration.Dimension(contract.typeKey(),
                contract.minHeight(), contract.height(), contract.logicalHeight(), contract.minHeight(), contract.height()));
        fixture.binding.inject(1L, engine, world);
        return world;
    }

    private static final class Fixture {
        private final NativeTerrainAccess terrain = mock(NativeTerrainAccess.class);
        private final NativeWorldGeneration generation = mock(NativeWorldGeneration.class);
        private final NativeWorldLifecycleFactory.Controller lifecycle = mock(NativeWorldLifecycleFactory.Controller.class);
        private final BukkitBinding binding;

        private Fixture() {
            when(generation.lifecycle(BukkitWorldLifecyclePolicy.INSTANCE)).thenReturn(lifecycle);
            binding = new BukkitBinding(new BukkitBinding.Capabilities(terrain, generation,
                    mock(NativeTerrainSnapshots.class), mock(NativeGenerationRegistry.class)));
        }
    }
}
