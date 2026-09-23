package art.arcane.iris.probe;

import art.arcane.volmlib.nativelib.minecraft26_2.modded.ModdedBlockState;
import art.arcane.volmlib.nativelib.minecraft26_2.modded.NativeBlockProperties;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mockito.MockedStatic;

import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;

public final class HeadlessNativeBlockPredicateTest {
    @BeforeClass
    public static void bootstrap() {
        HeadlessNativeBootstrap.initialize();
    }

    @Test
    public void memoizedPredicatesMatchEveryNativeBlockStateIncludingStateProperties() {
        for (Block block : BuiltInRegistries.BLOCK) {
            for (BlockState state : block.getStateDefinition().getPossibleStates()) {
                ModdedBlockState wrapped = ModdedBlockState.deferred(state, Map.of(), "iris:test");
                for (int iteration = 0; iteration < 2; iteration++) {
                    assertEquals(NativeBlockProperties.isStorage(state), wrapped.isStorage());
                    assertEquals(NativeBlockProperties.isUpdatable(state), wrapped.isUpdatable());
                }
            }
        }
    }

    @Test
    public void repeatedWritesClassifyImmutableStateOnlyOnce() {
        BlockState state = Blocks.CHEST.defaultBlockState();
        ModdedBlockState wrapped = ModdedBlockState.deferred(state, Map.of(), "iris:chest");
        boolean storage = NativeBlockProperties.isStorage(state);
        boolean updatable = NativeBlockProperties.isUpdatable(state);
        try (MockedStatic<NativeBlockProperties> properties = mockStatic(NativeBlockProperties.class, CALLS_REAL_METHODS)) {
            for (int iteration = 0; iteration < 512; iteration++) {
                assertEquals(storage, wrapped.isStorage());
                assertEquals(updatable, wrapped.isUpdatable());
            }
            properties.verify(() -> NativeBlockProperties.isUpdatable(state), times(1));
            properties.verify(() -> NativeBlockProperties.isStorage(state), times(2));
        }
    }
}
