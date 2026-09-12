package art.arcane.iris.modded.service;

import art.arcane.iris.world.IrisWorldBoundary;
import art.arcane.iris.world.IrisWorldBoundaryCenter;
import net.minecraft.world.level.border.WorldBorder;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class ModdedWorldBoundaryTest {
    @Test
    public void omittedBoundaryDoesNotTouchTheNativeLevel() {
        WorldBorder worldBorder = new WorldBorder();
        worldBorder.setCenter(3.5D, -4.5D);
        worldBorder.setSize(1024D);
        worldBorder.setWarningBlocks(9);
        worldBorder.setSafeZone(2.5D);
        worldBorder.setDamagePerBlock(0.75D);

        ModdedStudioHotloadService.applyWorldBoundary(worldBorder, null);

        assertEquals(3.5D, worldBorder.getCenterX(), 0D);
        assertEquals(-4.5D, worldBorder.getCenterZ(), 0D);
        assertEquals(1024D, worldBorder.getSize(), 0D);
        assertEquals(9, worldBorder.getWarningBlocks());
        assertEquals(2.5D, worldBorder.getSafeZone(), 0D);
        assertEquals(0.75D, worldBorder.getDamagePerBlock(), 0D);
    }

    @Test
    public void mapsEveryBoundaryValueToMinecraft() {
        WorldBorder worldBorder = new WorldBorder();
        IrisWorldBoundary boundary = new IrisWorldBoundary()
                .setCenter(new IrisWorldBoundaryCenter(12.5D, -7.25D))
                .setSize(4096D)
                .setWarningDistance(24)
                .setDamageBuffer(6.5D)
                .setDamageAmount(0.4D);

        ModdedStudioHotloadService.applyWorldBoundary(worldBorder, boundary);

        assertEquals(12.5D, worldBorder.getCenterX(), 0D);
        assertEquals(-7.25D, worldBorder.getCenterZ(), 0D);
        assertEquals(4096D, worldBorder.getSize(), 0D);
        assertEquals(24, worldBorder.getWarningBlocks());
        assertEquals(6.5D, worldBorder.getSafeZone(), 0D);
        assertEquals(0.4D, worldBorder.getDamagePerBlock(), 0D);
    }
}
