package art.arcane.iris.generation.block;

import art.arcane.iris.generation.block.BlockDataMergeSupport;

import org.bukkit.Material;
import org.bukkit.block.data.BlockData;
import org.junit.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

import static org.junit.Assert.assertSame;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;

public class IrisObjectBlockDataMergeTest {
    @Test
    public void reparsesBlockDataBeforeRetryingMerge() {
        BlockData base = mock(BlockData.class);
        BlockData update = mock(BlockData.class);
        BlockData parsedBase = mock(BlockData.class);
        BlockData parsedUpdate = mock(BlockData.class);
        BlockData merged = mock(BlockData.class);
        Function<String, BlockData> resolver = createResolver(
                "minecraft:oak_log[axis=x]", parsedBase,
                "minecraft:oak_log[axis=y]", parsedUpdate
        );

        doThrow(new IllegalArgumentException("Block data not created via string parsing")).when(base).merge(update);
        doReturn("minecraft:oak_log[axis=x]").when(base).getAsString(false);
        doReturn("minecraft:oak_log[axis=y]").when(update).getAsString(false);
        doReturn("minecraft:oak_log[axis=x]").when(base).getAsString();
        doReturn("minecraft:oak_log[axis=y]").when(update).getAsString();
        doReturn("minecraft:oak_log[axis=x]").when(parsedBase).getAsString();
        doReturn("minecraft:oak_log[axis=y]").when(parsedUpdate).getAsString();
        doReturn("minecraft:oak_log[axis=y]").when(merged).getAsString();
        doReturn(merged).when(parsedBase).merge(parsedUpdate);

        BlockData result = BlockDataMergeSupport.merge(base, update, resolver);

        assertSame(merged, result);
    }

    @Test
    public void fallsBackToNormalizedUpdateWhenRetryMergeStillFails() {
        BlockData base = mock(BlockData.class);
        BlockData update = mock(BlockData.class);
        BlockData parsedBase = mock(BlockData.class);
        BlockData parsedUpdate = mock(BlockData.class);
        Function<String, BlockData> resolver = createResolver(
                "minecraft:stone", parsedBase,
                "minecraft:stone[waterlogged=true]", parsedUpdate
        );

        doThrow(new IllegalArgumentException("Block data not created via string parsing")).when(base).merge(update);
        doReturn("minecraft:stone").when(base).getAsString(false);
        doReturn("minecraft:stone[waterlogged=true]").when(update).getAsString(false);
        doReturn("minecraft:stone").when(base).getAsString();
        doReturn("minecraft:stone[waterlogged=true]").when(update).getAsString();
        doReturn("minecraft:stone").when(parsedBase).getAsString();
        doReturn("minecraft:stone[waterlogged=true]").when(parsedUpdate).getAsString();
        doThrow(new IllegalArgumentException("normalized merge failed")).when(parsedBase).merge(parsedUpdate);

        BlockData result = BlockDataMergeSupport.merge(base, update, resolver);

        assertSame(parsedUpdate, result);
    }

    @Test
    public void keepsDirectMergeWhenBukkitAcceptsIt() {
        BlockData base = mock(BlockData.class);
        BlockData update = mock(BlockData.class);
        BlockData merged = mock(BlockData.class);

        doReturn(Material.STONE).when(base).getMaterial();
        doReturn(Material.STONE).when(update).getMaterial();
        doReturn("minecraft:stone").when(base).getAsString();
        doReturn("minecraft:stone[waterlogged=true]").when(update).getAsString();
        doReturn("minecraft:stone[waterlogged=true]").when(merged).getAsString();
        doReturn(merged).when(base).merge(update);

        BlockData result = BlockDataMergeSupport.merge(base, update, key -> null);

        assertSame(merged, result);
    }

    private Function<String, BlockData> createResolver(String firstKey, BlockData firstValue, String secondKey, BlockData secondValue) {
        Map<String, BlockData> resolved = new HashMap<>();
        resolved.put(firstKey, firstValue);
        resolved.put(secondKey, secondValue);
        return resolved::get;
    }
}
