package art.arcane.iris.studio.jigsaw;

import art.arcane.volmlib.util.event.ProtectionProbe;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.PlayerInventory;
import org.junit.Test;
import org.mockito.MockedStatic;

import java.lang.reflect.Field;
import java.util.UUID;

import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class JigsawStudioProtectionProbeTest {
    @Test
    public void previewDoesNotMarkStudioDirty() throws Exception {
        JigsawStudioService service = new JigsawStudioService();
        JigsawStudioDirtyTracker tracker = mock(JigsawStudioDirtyTracker.class);
        JigsawStudioTileWatcher watcher = mock(JigsawStudioTileWatcher.class);
        setField(service, "dirtyTracker", tracker);
        setField(service, "tileWatcher", watcher);
        PlayerInteractEvent event = probe();

        service.protectionListener.onPlayerInteract(event);

        verify(tracker, never()).markDirty(event.getClickedBlock());
        verify(watcher, never()).finalizeJigsawTileWatchesForPlayer(event.getPlayer().getUniqueId());
    }

    @Test
    public void controlChestProbeRemainsDeniedWithoutOpeningItsMenu() throws Exception {
        JigsawStudioService service = new JigsawStudioService();
        JigsawStudioProtection protection = mock(JigsawStudioProtection.class);
        JigsawStudioToolbelt toolbelt = mock(JigsawStudioToolbelt.class);
        setField(service, "protection", protection);
        setField(service, "toolbelt", toolbelt);
        PlayerInteractEvent event = probe();
        when(protection.isControlChest(event.getClickedBlock())).thenReturn(true);
        try (MockedStatic<JigsawStudioProtection> authorization = mockStatic(JigsawStudioProtection.class)) {
            authorization.when(() -> JigsawStudioProtection.authorizeOwner(event.getPlayer(), null)).thenReturn(true);

            service.protectionListener.onControlChest(event);

            assertTrue(event.isCancelled());
            verify(toolbelt, never()).openControlMenu(any(Player.class));
        }
    }

    private static PlayerInteractEvent probe() {
        Player player = mock(Player.class);
        World world = mock(World.class);
        when(world.getUID()).thenReturn(UUID.randomUUID());
        when(player.getWorld()).thenReturn(world);
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        when(player.getInventory()).thenReturn(mock(PlayerInventory.class));
        return ProtectionProbe.blockInteract(player, mock(Block.class), EquipmentSlot.HAND);
    }

    private static void setField(Object target, String name, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }
}
