package art.arcane.iris.modded;

import art.arcane.volmlib.nativelib.minecraft26_2.modded.ModdedBlockState;
import art.arcane.volmlib.nativelib.minecraft26_2.modded.NativeBlockResolver;
import art.arcane.volmlib.nativelib.minecraft26_2.modded.NativeStateMerger;
import art.arcane.volmlib.nativelib.minecraft26_2.modded.NativeStateRotator;
import art.arcane.volmlib.nativelib.terrain.NativeBlockState;
import net.minecraft.SharedConstants;
import net.minecraft.core.Direction;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.block.StairBlock;
import org.junit.BeforeClass;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class NativeStateOperationsTest {
    @BeforeClass
    public static void bootstrapMinecraftRegistries() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    public void rotationPreservesDeferredPlacementMetadata() {
        ModdedBlockState parsed = NativeBlockResolver.strictParse("minecraft:oak_stairs[facing=north]");
        ModdedBlockState deferred = ModdedBlockState.deferred(parsed.handle(), parsed.parsedProperties(), "custom:stairs");

        NativeBlockState result = NativeStateRotator.rotate(deferred,
                (x, y, z) -> new NativeStateRotator.Vector(-z, y, x), failure -> {
                    throw new AssertionError(failure);
                });

        assertEquals(Direction.EAST, ((ModdedBlockState) result).handle().getValue(StairBlock.FACING));
        assertEquals("custom:stairs", result.deferredPlacementKey());
    }

    @Test
    public void mergeAppliesOnlySpecifiedPropertiesAndRetainsDeferredPlacement() {
        ModdedBlockState parsed = NativeBlockResolver.strictParse("minecraft:oak_stairs[facing=west]");
        ModdedBlockState deferred = ModdedBlockState.deferred(parsed.handle(), parsed.parsedProperties(), "custom:stairs");
        ModdedBlockState update = NativeBlockResolver.strictParse("minecraft:oak_stairs[waterlogged=true]");

        NativeBlockState result = new NativeStateMerger(ModdedBlockResolution.BLOCKS).merge(deferred, update);

        assertEquals(Direction.WEST, ((ModdedBlockState) result).handle().getValue(StairBlock.FACING));
        assertTrue(((ModdedBlockState) result).handle().getValue(StairBlock.WATERLOGGED));
        assertEquals("custom:stairs", result.deferredPlacementKey());
    }
}
