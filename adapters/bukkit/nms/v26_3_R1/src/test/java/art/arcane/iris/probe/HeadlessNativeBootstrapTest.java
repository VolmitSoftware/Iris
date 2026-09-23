package art.arcane.iris.probe;

import net.minecraft.tags.BlockTags;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.Map;
import net.minecraft.world.level.block.Blocks;
import org.junit.Test;
import org.bukkit.Bukkit;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public final class HeadlessNativeBootstrapTest {
    @Test
    public void bindsNativeItemComponentsWithoutStartingServer() throws Exception {
        HeadlessNativeBootstrap.bindComponents(HeadlessNativeTestRegistries.get());
        for (Item item : BuiltInRegistries.ITEM) {
            ItemStack stack = new ItemStack(item);
            if (item != Items.AIR) {
                assertTrue(stack.getMaxStackSize() > 0);
            }
        }
        assertNotNull(new ItemStack(Items.APPLE).get(DataComponents.FOOD));
        assertEquals(64, new ItemStack(Items.TRIAL_KEY).getMaxStackSize());
        assertTrue(Bukkit.getServer() == null);
    }

    @Test
    public void loadsActualNativeTagsWithoutStartingServer() throws Exception {
        HeadlessNativeBootstrap.initialize();
        assertTrue(Blocks.OAK_LOG.defaultBlockState().is(BlockTags.LOGS));
        assertTrue(Blocks.OAK_LEAVES.defaultBlockState().is(BlockTags.LEAVES));
        assertTrue(Blocks.STONE.defaultBlockState().is(BlockTags.BLOCKS_MOTION));
        assertFalse(Blocks.WATER.defaultBlockState().is(BlockTags.BLOCKS_MOTION));
        assertFalse(Blocks.SHORT_GRASS.defaultBlockState().is(BlockTags.BLOCKS_MOTION));
        assertTrue(Bukkit.getServer() == null);
    }
}
