package art.arcane.iris.world.entity;

import art.arcane.iris.world.task.J;
import org.bukkit.HeightMap;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.junit.Test;
import org.mockito.MockedStatic;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class IrisEffectSurfaceTest {
    @Test
    public void samplesLiveHeightOnTargetRegionThenEmitsOnPlayerThread() {
        World world = mock(World.class);
        Player player = mock(Player.class);
        Location location = new Location(world, -17, 100, 32);
        when(player.getWorld()).thenReturn(world);
        when(world.isChunkLoaded(-2, 2)).thenReturn(true);
        when(world.getHighestBlockYAt(-17, 32, HeightMap.OCEAN_FLOOR)).thenReturn(23);
        AtomicReference<Runnable> regionTask = new AtomicReference<>();
        AtomicReference<Runnable> playerTask = new AtomicReference<>();

        try (MockedStatic<J> scheduler = mockStatic(J.class)) {
            scheduler.when(() -> J.runAt(eq(location), any(Runnable.class))).thenAnswer(call -> {
                regionTask.set(call.getArgument(1));
                return true;
            });
            scheduler.when(() -> J.runEntity(eq(player), any(Runnable.class))).thenAnswer(call -> {
                playerTask.set(call.getArgument(1));
                return true;
            });

            new IrisEffect().applyParticles(player, Particle.FLAME, location);
            assertNotNull(regionTask.get());
            assertNull(playerTask.get());
            verify(world, never()).getHighestBlockYAt(anyInt(), anyInt(), any(HeightMap.class));
            regionTask.get().run();
            assertEquals(24D, location.getY(), 0D);
            assertNotNull(playerTask.get());
            playerTask.get().run();
            verify(player).spawnParticle(eq(Particle.FLAME), anyDouble(), eq(24D), anyDouble(),
                    anyInt(), anyDouble(), anyDouble(), anyDouble());
        }
    }

    @Test
    public void unloadedParticleColumnsDoNotLoadTerrainOrEmit() {
        World world = mock(World.class);
        Player player = mock(Player.class);
        Location location = new Location(world, 0, 100, 0);
        try (MockedStatic<J> scheduler = mockStatic(J.class)) {
            scheduler.when(() -> J.isOwnedByCurrentRegion(world, 0, 0)).thenReturn(true);
            new IrisEffect().applyParticles(player, Particle.FLAME, location);
            verify(world, never()).getHighestBlockYAt(anyInt(), anyInt(), any(HeightMap.class));
            scheduler.verify(() -> J.runEntity(eq(player), any(Runnable.class)), never());
        }
    }
}
