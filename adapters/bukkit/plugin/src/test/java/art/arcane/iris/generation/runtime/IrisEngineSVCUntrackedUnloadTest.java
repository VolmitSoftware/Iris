package art.arcane.iris.generation.runtime;

import art.arcane.iris.platform.generation.BukkitChunkGenerator;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.event.world.WorldUnloadEvent;
import org.junit.Test;

import java.util.concurrent.CompletableFuture;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Multiverse unloads a world through {@code Bukkit.unloadWorld}, which carries no Iris unload boundary.
 * {@code IrisEngineSVC} closes the engine of every world it registered, but registration is skipped while
 * a previous generator for the same identity is still closing, and an unload landing in that window used
 * to leave a live engine bound to a dead world.
 */
public class IrisEngineSVCUntrackedUnloadTest {
    @Test
    public void externalUnloadClosesAnIrisGeneratorTheServiceNeverRegistered() {
        BukkitChunkGenerator generator = mock(BukkitChunkGenerator.class);
        when(generator.closeAsync()).thenReturn(CompletableFuture.completedFuture(null));

        new IrisEngineSVC().onWorldUnload(new WorldUnloadEvent(irisWorld("mvtest", generator)));

        verify(generator).closeAsync();
    }

    @Test
    public void externalUnloadOfANonIrisWorldClosesNothing() {
        World world = mock(World.class);
        when(world.getName()).thenReturn("plots");
        when(world.getKey()).thenReturn(new NamespacedKey("minecraft", "plots"));

        new IrisEngineSVC().onWorldUnload(new WorldUnloadEvent(world));
    }

    @Test
    public void externalUnloadDoesNotReenterAGeneratorThatIsAlreadyClosing() {
        BukkitChunkGenerator generator = mock(BukkitChunkGenerator.class);
        when(generator.isClosing()).thenReturn(true);

        new IrisEngineSVC().onWorldUnload(new WorldUnloadEvent(irisWorld("closing", generator)));

        verify(generator, never()).closeAsync();
    }

    private static World irisWorld(String key, BukkitChunkGenerator generator) {
        World world = mock(World.class);
        when(world.getName()).thenReturn("world_iris_" + key);
        when(world.getKey()).thenReturn(new NamespacedKey("iris", key));
        when(world.getGenerator()).thenReturn(generator);
        return world;
    }
}
