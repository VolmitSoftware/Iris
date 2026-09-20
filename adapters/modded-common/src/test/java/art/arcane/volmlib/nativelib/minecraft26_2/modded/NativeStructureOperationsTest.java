package art.arcane.volmlib.nativelib.minecraft26_2.modded;

import net.minecraft.server.level.ServerLevel;

import art.arcane.volmlib.nativelib.minecraft26_2.modded.ModdedPlatformWorld;

import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import org.junit.BeforeClass;
import org.junit.Test;
import java.util.function.Supplier;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import static org.mockito.Mockito.mock;

import static org.mockito.Mockito.when;

public class NativeStructureOperationsTest {
    @BeforeClass
    public static void bootstrapMinecraftRegistries() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    public void objectFeaturesMatchPaperGroups() {
        assertEquals("trees", NativeStructureOperations.classifyFeature(Feature.TREE));
        assertEquals("fallen_trees", NativeStructureOperations.classifyFeature(Feature.FALLEN_TREE));
        assertEquals("mushrooms", NativeStructureOperations.classifyFeature(Feature.HUGE_BROWN_MUSHROOM));
        assertEquals("mushrooms", NativeStructureOperations.classifyFeature(Feature.HUGE_RED_MUSHROOM));
        assertNull(NativeStructureOperations.classifyFeature(Feature.ORE));
    }

    @Test
    public void structureSpanGuardUsesInclusiveBoundingBoxDimensions() {
        BoundingBox box = new BoundingBox(-16, -64, 32, 15, 63, 47);

        assertTrue(NativeStructureOperations.isWithinSpan(box, 128));
        assertFalse(NativeStructureOperations.isWithinSpan(box, 127));
        assertTrue(NativeStructureOperations.isWithinSpan(box, 0));
        assertTrue(NativeStructureOperations.isWithinSpan(box, -1));
    }

    @Test
    public void chunkGridSizeCountsEveryChunkPlacementWouldLoad() {
        assertEquals(1, NativeStructureOperations.chunkGridSize(new BoundingBox(0, 0, 0, 15, 15, 15)));
        assertEquals(4, NativeStructureOperations.chunkGridSize(new BoundingBox(0, 0, 0, 16, 15, 16)));
        assertEquals(9, NativeStructureOperations.chunkGridSize(new BoundingBox(-16, 0, -16, 16, 15, 16)));
    }

    @Test
    public void chunkGridSizeSaturatesInsteadOfOverflowing() {
        BoundingBox box = new BoundingBox(
                Integer.MIN_VALUE / 2, 0, Integer.MIN_VALUE / 2,
                Integer.MAX_VALUE / 2, 15, Integer.MAX_VALUE / 2);

        assertEquals(Integer.MAX_VALUE, NativeStructureOperations.chunkGridSize(box));
        assertTrue(NativeStructureOperations.chunkGridSize(box) > 1024);
    }

    @Test
    public void structurePlacementReturnsPaperOrderedBounds() {
        BoundingBox box = new BoundingBox(-16, -64, 32, 15, 63, 47);

        assertArrayEquals(new int[]{-16, -64, 32, 15, 63, 47}, NativeStructureOperations.bounds(box));
    }

    @Test
    public void platformWorldMaximumHeightIsExclusive() {
        ServerLevel level = mock(ServerLevel.class);
        when(level.getMinY()).thenReturn(-64);
        when(level.getHeight()).thenReturn(384);
        ModdedPlatformWorld world = new ModdedPlatformWorld(level);
        assertEquals(320, world.maxHeight());
        when(level.getMinY()).thenReturn(0);
        assertEquals(384, world.maxHeight());
    }

    @Test
    public void structureRegistryHooksRejectUnavailableServer() {
        NativeStructureOperations hooks = hooks(() -> null);

        IllegalStateException structureKeys = assertThrows(
                IllegalStateException.class, hooks::structureKeys);
        IllegalStateException jigsawStructureKeys = assertThrows(
                IllegalStateException.class, hooks::jigsawStructureKeys);
        IllegalStateException templatePoolKeys = assertThrows(
                IllegalStateException.class, hooks::templatePoolKeys);
        IllegalStateException jigsawMetadata = assertThrows(
                IllegalStateException.class, () -> hooks.jigsawSourceMetadata("minecraft:village"));
        IllegalStateException templatePoolSpan = assertThrows(
                IllegalStateException.class,
                () -> hooks.templatePoolHorizontalSpan("minecraft:village/plains/town_centers"));
        IllegalStateException structureSetKeys = assertThrows(
                IllegalStateException.class, hooks::structureSetKeys);
        IllegalStateException structureBiomeKeys = assertThrows(
                IllegalStateException.class, () -> hooks.structureBiomeKeys("minecraft:village"));

        assertTrue(structureKeys.getMessage().contains("before the Minecraft server is available"));
        assertTrue(jigsawStructureKeys.getMessage().contains("before the Minecraft server is available"));
        assertTrue(templatePoolKeys.getMessage().contains("before the Minecraft server is available"));
        assertTrue(jigsawMetadata.getMessage().contains("before the Minecraft server is available"));
        assertTrue(templatePoolSpan.getMessage().contains("before the Minecraft server is available"));
        assertTrue(structureSetKeys.getMessage().contains("before the Minecraft server is available"));
        assertTrue(structureBiomeKeys.getMessage().contains("before the Minecraft server is available"));
    }

    @Test
    public void structureReachabilityHooksRejectUnavailableLevel() {
        NativeStructureOperations hooks = hooks(() -> null);

        IllegalStateException reachable = assertThrows(
                IllegalStateException.class, () -> hooks.reachableStructureKeys(null));
        IllegalStateException possibleBiomes = assertThrows(
                IllegalStateException.class, () -> hooks.possibleBiomeKeys(null));

        assertTrue(reachable.getMessage().contains("without a bound modded ServerLevel"));
        assertTrue(possibleBiomes.getMessage().contains("without a bound modded ServerLevel"));
    }

    @Test
    public void structureRegistryHooksPreserveServerSupplierFailure() {
        IllegalStateException cause = new IllegalStateException("server supplier failed");
        NativeStructureOperations hooks = hooks(() -> {
            throw cause;
        });

        IllegalStateException failure = assertThrows(
                IllegalStateException.class, hooks::structureKeys);

        assertTrue(failure.getMessage().contains("access the Minecraft server"));
        assertEquals(cause, failure.getCause());
    }
    private static NativeStructureOperations hooks(Supplier<NativeModdedServer> server) {
        return new NativeStructureOperations(new NativeStructureOperations.Options(server, 1024,
                failure -> { throw new AssertionError(failure); }, warning -> { }));
    }

}
