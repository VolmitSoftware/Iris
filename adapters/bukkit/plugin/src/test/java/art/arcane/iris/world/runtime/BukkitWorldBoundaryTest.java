package art.arcane.iris.world.runtime;

import art.arcane.iris.generation.runtime.Engine;
import art.arcane.iris.generation.terrain.IrisDimension;
import art.arcane.iris.world.IrisWorldBoundary;
import art.arcane.iris.world.IrisWorldBoundaryCenter;
import org.bukkit.WorldBorder;
import org.junit.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class BukkitWorldBoundaryTest {
    @Test
    public void omittedBoundaryDoesNotTouchTheNativeWorld() {
        Engine engine = mock(Engine.class);
        when(engine.getDimension()).thenReturn(new IrisDimension());

        new BukkitEnginePlatformHooks().applyWorldBoundary(engine);

        verify(engine, never()).getWorld();
    }

    @Test
    public void mapsEveryBoundaryValueToBukkit() {
        WorldBorder worldBorder = mock(WorldBorder.class);
        IrisWorldBoundary boundary = new IrisWorldBoundary()
                .setCenter(new IrisWorldBoundaryCenter(12.5D, -7.25D))
                .setSize(4096D)
                .setWarningDistance(24)
                .setDamageBuffer(6.5D)
                .setDamageAmount(0.4D);

        BukkitEnginePlatformHooks.applyWorldBoundary(worldBorder, boundary);

        verify(worldBorder).setCenter(12.5D, -7.25D);
        verify(worldBorder).setSize(4096D);
        verify(worldBorder).setWarningDistance(24);
        verify(worldBorder).setDamageBuffer(6.5D);
        verify(worldBorder).setDamageAmount(0.4D);
    }
}
