package art.arcane.iris.world.tree;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.junit.Test;

import java.util.Collection;
import java.util.List;

import static org.junit.Assert.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

public class RoutedBlockBreakEventTest {
    @Test
    public void nativeLootUsesTheReservedToolCloneWithoutReadingPlayerInventory() {
        Block block = mock(Block.class);
        Player player = mock(Player.class);
        ItemStack reservedTool = mock(ItemStack.class);
        ItemStack capturedTool = mock(ItemStack.class);
        ItemStack drop = mock(ItemStack.class);
        Collection<ItemStack> expected = List.of(drop);
        when(reservedTool.clone()).thenReturn(capturedTool);
        when(block.getDrops(capturedTool)).thenReturn(expected);
        RoutedBlockBreakEvent event = new RoutedBlockBreakEvent(new RoutedBlockBreakEvent.Options(
                block, player, mock(FellingRun.class), mock(TreeFellerSVC.class), reservedTool));

        reservedTool.setType(Material.AIR);

        assertSame(expected, event.captureNativeDrops());
        verify(reservedTool).clone();
        verify(block).getDrops(capturedTool);
        verify(block, never()).getDrops(reservedTool);
        verifyNoInteractions(player);
    }
}
