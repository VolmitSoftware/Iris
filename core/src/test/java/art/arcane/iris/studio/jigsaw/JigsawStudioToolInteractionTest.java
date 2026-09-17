package art.arcane.iris.studio.jigsaw;

import java.lang.reflect.Field;
import java.util.Optional;
import java.util.UUID;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.RegisteredListener;
import org.junit.Test;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

public class JigsawStudioToolInteractionTest {
    @Test
    public void airClickReachesTheToolDespiteBukkitsDefaultCancellation() throws Exception {
        Fixture fixture = fixture();
        PlayerInteractEvent event = event(fixture.player(), Action.RIGHT_CLICK_AIR, null);
        assertTrue(event.isCancelled());

        dispatch(fixture.listener(), event);

        verify(fixture.toolbelt()).useTool(fixture.player(), fixture.payload(), null, false);
        verify(fixture.watcher()).finalizeJigsawTileWatchesForPlayer(fixture.player().getUniqueId());
    }

    @Test
    public void deniedBlockAndItemInteractionsDoNotUseTheTool() throws Exception {
        Fixture fixture = fixture();
        PlayerInteractEvent block = event(fixture.player(), Action.RIGHT_CLICK_BLOCK, mock(Block.class));
        block.setUseInteractedBlock(Event.Result.DENY);
        dispatch(fixture.listener(), block);
        PlayerInteractEvent air = event(fixture.player(), Action.RIGHT_CLICK_AIR, null);
        air.setUseItemInHand(Event.Result.DENY);
        dispatch(fixture.listener(), air);
        PlayerInteractEvent item = event(fixture.player(), Action.RIGHT_CLICK_BLOCK, mock(Block.class));
        item.setUseItemInHand(Event.Result.DENY);
        dispatch(fixture.listener(), item);

        verifyNoInteractions(fixture.codec(), fixture.toolbelt(), fixture.watcher());
    }

    private static Fixture fixture() throws Exception {
        JigsawStudioService service = new JigsawStudioService();
        JigsawStudioToolbelt toolbelt = mock(JigsawStudioToolbelt.class);
        JigsawStudioToolCodec codec = mock(JigsawStudioToolCodec.class);
        JigsawStudioTileWatcher watcher = mock(JigsawStudioTileWatcher.class);
        JigsawStudioToolPayload payload = mock(JigsawStudioToolPayload.class);
        Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        when(codec.decode((ItemStack) null)).thenReturn(Optional.of(payload));
        setField(service, "toolbelt", toolbelt);
        setField(service, "tileWatcher", watcher);
        setField(toolbelt, "toolCodec", codec);
        return new Fixture(service.protectionListener, toolbelt, codec, watcher, payload, player);
    }

    private static PlayerInteractEvent event(Player player, Action action, Block block) {
        return new PlayerInteractEvent(player, action, null, block, BlockFace.UP, EquipmentSlot.HAND);
    }

    private static void dispatch(JigsawStudioProtectionListener listener, PlayerInteractEvent event) throws Exception {
        EventHandler annotation = JigsawStudioProtectionListener.class
            .getMethod("onUseTool", PlayerInteractEvent.class).getAnnotation(EventHandler.class);
        RegisteredListener registered = new RegisteredListener(listener,
            (ignored, dispatched) -> listener.onUseTool((PlayerInteractEvent) dispatched),
            EventPriority.LOWEST, mock(Plugin.class), annotation.ignoreCancelled());
        registered.callEvent(event);
    }

    private static void setField(Object target, String name, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

    private record Fixture(JigsawStudioProtectionListener listener, JigsawStudioToolbelt toolbelt,
                           JigsawStudioToolCodec codec, JigsawStudioTileWatcher watcher,
                           JigsawStudioToolPayload payload, Player player) {
    }
}
