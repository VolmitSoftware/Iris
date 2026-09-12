package art.arcane.iris.studio.wand;

import art.arcane.iris.platform.bukkit.registry.RegistryUtil;
import art.arcane.volmlib.util.data.Cuboid;
import art.arcane.volmlib.util.math.M;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class WandSelectionTest {
    @Test
    public void capsEmittedDustSizeAtLongDistances() {
        for (double distance : new double[]{64D, 128D, 255.9D}) {
            Player player = drawAt(distance);
            ArgumentCaptor<Particle.DustOptions> options = ArgumentCaptor.forClass(Particle.DustOptions.class);

            verify(player, atLeastOnce()).spawnParticle(eq(Particle.DUST), anyDouble(), anyDouble(), anyDouble(),
                    eq(0), eq(0D), eq(0D), eq(0D), eq(1D), options.capture());

            for (Particle.DustOptions dust : options.getAllValues()) {
                assertEquals("Dust size at distance " + distance, 4f, dust.getSize(), 0f);
            }
        }
    }

    @Test
    public void retainsNearbyDustSize() {
        Player player = drawAt(0D);
        ArgumentCaptor<Particle.DustOptions> options = ArgumentCaptor.forClass(Particle.DustOptions.class);

        verify(player, atLeastOnce()).spawnParticle(eq(Particle.DUST), eq(0D), eq(0D), eq(0D),
                eq(0), eq(0D), eq(0D), eq(0D), eq(1D), options.capture());

        for (Particle.DustOptions dust : options.getAllValues()) {
            assertEquals(0.375f, dust.getSize(), 0f);
        }
    }

    private Player drawAt(double distance) {
        Player player = mock(Player.class);
        World world = mock(World.class);
        Cuboid cuboid = mock(Cuboid.class);
        when(player.getLocation()).thenReturn(new Location(world, distance, 0D, 0D));
        when(cuboid.getWorld()).thenReturn(world);

        try (MockedStatic<M> math = mockStatic(M.class, CALLS_REAL_METHODS);
             MockedStatic<RegistryUtil> registry = mockStatic(RegistryUtil.class)) {
            math.when(() -> M.r(anyDouble())).thenReturn(false);
            registry.when(() -> RegistryUtil.find(Particle.class, "crit_magic", "crit")).thenReturn(Particle.CRIT);
            registry.when(() -> RegistryUtil.find(Particle.class, "redstone", "dust")).thenReturn(Particle.DUST);
            registry.when(() -> RegistryUtil.find(Particle.class, "item_crack", "item")).thenReturn(Particle.ITEM);

            new WandSelection(cuboid, player).draw();
        }

        return player;
    }
}
