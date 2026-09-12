package art.arcane.iris.platform.generation;

import art.arcane.iris.generation.runtime.Engine;
import art.arcane.iris.generation.biome.IrisBiome;
import art.arcane.iris.world.IrisWorld;
import org.bukkit.Location;
import org.junit.Test;

import static org.junit.Assert.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class EngineBukkitOpsBiomeTest {
    @Test
    public void mantleBiomeLookupConvertsWorldYToInternalY() {
        Engine engine = mock(Engine.class);
        IrisWorld world = IrisWorld.builder().minHeight(-64).maxHeight(320).build();
        IrisBiome biome = mock(IrisBiome.class);
        Location location = mock(Location.class);
        when(engine.getWorld()).thenReturn(world);
        when(location.getBlockX()).thenReturn(12);
        when(location.getBlockY()).thenReturn(-25);
        when(location.getBlockZ()).thenReturn(-7);
        when(engine.getBiomeOrMantle(12, 39, -7)).thenReturn(biome);

        assertSame(biome, EngineBukkitOps.getBiomeOrMantle(engine, location));
        verify(engine).getBiomeOrMantle(12, 39, -7);
    }
}
