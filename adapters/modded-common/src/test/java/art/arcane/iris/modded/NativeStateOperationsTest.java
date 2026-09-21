package art.arcane.iris.modded;

import art.arcane.volmlib.nativelib.minecraft26_2.modded.ModdedBlockState;
import art.arcane.volmlib.nativelib.minecraft26_2.modded.NativeBlockResolver;
import art.arcane.volmlib.nativelib.minecraft26_2.modded.NativeRegistryAccess;
import art.arcane.iris.spi.PlatformGenerationRegistry;
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
import static org.junit.Assert.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

public class NativeStateOperationsTest {
    @BeforeClass
    public static void bootstrapMinecraftRegistries() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    public void persistedLeafPropertiesBypassGenerationDefaults() {
        NativeBlockResolver.Policy policy = mock(NativeBlockResolver.Policy.class);
        NativeBlockResolver resolver = new NativeBlockResolver(policy);
        ModdedRegistries registries = new ModdedRegistries(mock(NativeRegistryAccess.class),
                () -> mock(PlatformGenerationRegistry.class));
        for (String key : new String[]{
                "minecraft:oak_leaves[distance=2,persistent=false,waterlogged=true]",
                "minecraft:oak_leaves[distance=6,persistent=true,waterlogged=false]",
                "minecraft:oak_stairs[facing=west,half=top,shape=inner_left,waterlogged=true]"}) {
            assertEquals(NativeBlockResolver.strictParse(key).key(), resolver.decode(key).key());
            assertEquals(NativeBlockResolver.strictParse(key).key(), registries.decodeBlockState(key).key());
        }
        verifyNoInteractions(policy);
    }

    @Test
    public void persistedCustomStateRetainsDeferredPlacementIdentity() {
        String key = "test:persisted_stairs[facing=west]";
        ModdedBlockState nativeState = NativeBlockResolver.strictParse("minecraft:oak_stairs[facing=west]");
        ModdedBlockState custom = nativeState.withDeferredPlacement(key);
        NativeBlockResolver.Policy policy = mock(NativeBlockResolver.Policy.class);
        when(policy.resolveCustomBlock(key)).thenReturn(custom);

        assertSame(custom, new NativeBlockResolver(policy).decode(key));
        assertEquals(key, custom.deferredPlacementKey());
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
