package art.arcane.iris.generation.runtime;

import art.arcane.iris.pack.loading.IrisData;
import art.arcane.iris.world.history.SavedBiomeUnavailableException;
import art.arcane.iris.generation.biome.IrisBiome;
import art.arcane.iris.generation.terrain.IrisDimension;
import art.arcane.iris.generation.terrain.IrisRegion;
import art.arcane.iris.world.IrisWorld;
import art.arcane.iris.platform.bukkit.BukkitWorldBinding;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.junit.Test;
import org.mockito.MockedStatic;

import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class EnginePlayerTest {
    @Test
    public void samplesImmediatelyThenWaitsForTimeAndMovement() {
        assertTrue(EnginePlayer.needsSample(false, 0L, 0D));
        assertFalse(EnginePlayer.needsSample(true, 56L, 81D));
        assertFalse(EnginePlayer.needsSample(true, 55L, 82D));
        assertTrue(EnginePlayer.needsSample(true, 56L, 82D));
    }

    @Test
    public void loadingSavedBiomesRetryWithoutMovementAndKeepOneDefinitionContext() {
        Engine engine = mock(Engine.class);
        IrisWorld irisWorld = mock(IrisWorld.class);
        World world = mock(World.class);
        Player player = mock(Player.class);
        when(engine.getWorld()).thenReturn(irisWorld);
        when(irisWorld.minHeight()).thenReturn(-64);
        when(player.getLocation()).thenReturn(new Location(world, 19, 80, -3));
        BiomeEnvironment environment = new BiomeEnvironment(4L, mock(IrisBiome.class),
                mock(IrisRegion.class), mock(IrisDimension.class), mock(IrisData.class));
        when(engine.getBiomeEnvironment(19, 144, -3))
                .thenThrow(new SavedBiomeUnavailableException("Loading", true))
                .thenReturn(environment);

        try (MockedStatic<BukkitWorldBinding> binding = mockStatic(BukkitWorldBinding.class)) {
            binding.when(() -> BukkitWorldBinding.world(irisWorld)).thenReturn(world);
            EnginePlayer sampled = new EnginePlayer(engine, player);
            assertTrue(sampled.sample());
            assertNull(sampled.getLastLocation());
            assertNull(sampled.getEnvironment());
            assertFalse(sampled.sample());
            assertSame(environment, sampled.getEnvironment());
            assertSame(environment.biome(), sampled.getBiome());
            assertSame(environment.region(), sampled.getRegion());
            assertFalse(sampled.sample());
            verify(engine, times(2)).getBiomeEnvironment(19, 144, -3);
        }
    }

}
