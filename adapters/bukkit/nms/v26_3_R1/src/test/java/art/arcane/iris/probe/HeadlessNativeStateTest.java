package art.arcane.iris.probe;

import art.arcane.volmlib.nativelib.minecraft26_2.modded.ModdedBlockState;
import art.arcane.volmlib.nativelib.minecraft26_2.modded.NativeBlockResolver;
import art.arcane.volmlib.nativelib.terrain.NativeBlockState;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mockito.MockedStatic;

import java.io.File;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;

public final class HeadlessNativeStateTest {
    @BeforeClass
    public static void bootstrap() {
        HeadlessNativeBootstrap.initialize();
    }

    @Test
    public void platformAirResolvesOnceAndRetainsCanonicalNativeState() {
        BlockState air = Blocks.AIR.defaultBlockState();
        try (MockedStatic<ModdedBlockState> states = mockStatic(ModdedBlockState.class, CALLS_REAL_METHODS)) {
            HeadlessNativePlatform platform = new HeadlessNativePlatform(new File("unused-native-platform"),
                    HeadlessNativeTestRegistries::get);
            NativeBlockState wrapped = platform.air();
            for (int iteration = 0; iteration < 1024; iteration++) {
                assertSame(wrapped, platform.air());
                assertSame(air, ((ModdedBlockState) platform.air()).handle());
            }
            states.verify(() -> ModdedBlockState.of(air, null), times(1));
        }
    }

    @Test
    public void nativeMaterialsDistinguishDecorationsFluidsGlassAndBlockEntities() {
        assertFalse(NativeBlockResolver.strictParse("minecraft:short_grass").isSolid());
        assertTrue(NativeBlockResolver.strictParse("minecraft:glass").isSolid());
        assertFalse(NativeBlockResolver.strictParse("minecraft:glass").isOccluding());
        assertTrue(NativeBlockResolver.strictParse("minecraft:stone").isOccluding());
        assertTrue(NativeBlockResolver.strictParse("minecraft:oak_log").isTreeBlock());
        assertTrue(NativeBlockResolver.strictParse("minecraft:oak_leaves").isTreeBlock());
        assertTrue(NativeBlockResolver.strictParse("minecraft:chest").hasTileEntity());
        NativeBlockState slab = NativeBlockResolver.strictParse("minecraft:oak_slab[waterlogged=true]");
        assertTrue(slab.isWaterLogged());
        assertFalse(slab.isFluid());
        assertTrue(NativeBlockResolver.strictParse("minecraft:water").isFluid());
    }

    @Test
    public void canonicalStateKeysRoundTripEveryNativeDefaultState() {
        for (Block block : BuiltInRegistries.BLOCK) {
            BlockState state = block.defaultBlockState();
            ModdedBlockState decoded = NativeBlockResolver.strictParse(ModdedBlockState.serialize(state));
            assertEquals(state, decoded.handle());
        }
        assertThrows(IllegalArgumentException.class,
                () -> NativeBlockResolver.strictParse("minecraft:oak_log[axis=invalid]"));
    }
}
