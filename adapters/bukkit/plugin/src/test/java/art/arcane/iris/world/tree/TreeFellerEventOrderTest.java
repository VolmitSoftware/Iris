package art.arcane.iris.world.tree;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.junit.Test;

import java.lang.reflect.Method;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class TreeFellerEventOrderTest {
    @Test
    public void standaloneIntentPrecedesMonitorFinalization() throws Exception {
        Method request = TreeFellerSVC.class.getMethod("requestStandalone", org.bukkit.event.block.BlockBreakEvent.class);
        Method finalize = TreeFellerSVC.class.getMethod("finalizeBreak", org.bukkit.event.block.BlockBreakEvent.class);
        EventHandler requestHandler = request.getAnnotation(EventHandler.class);
        EventHandler finalizeHandler = finalize.getAnnotation(EventHandler.class);

        assertEquals(EventPriority.HIGHEST, requestHandler.priority());
        assertTrue(requestHandler.ignoreCancelled());
        assertEquals(EventPriority.MONITOR, finalizeHandler.priority());
        assertFalse(finalizeHandler.ignoreCancelled());
    }

    @Test
    public void incompleteDiscoveryFallsOnlyTheTrigger() {
        TreeMarkerTraversal.Position trigger = new TreeMarkerTraversal.Position(4, 80, -2);
        TreeMarkerTraversal.Discovery incomplete = new TreeMarkerTraversal.Discovery(
                List.of(trigger, new TreeMarkerTraversal.Position(4, 81, -2)),
                false
        );

        assertEquals(List.of(trigger), TreeFellingRunner.positionsForFelling(incomplete, trigger));
    }

    @Test
    public void playerControlChangesHaltRunsAtMonitor() throws Exception {
        Method sneak = TreeFellerSVC.class.getMethod("haltWhenSneakingStops", PlayerToggleSneakEvent.class);
        Method held = TreeFellerSVC.class.getMethod("haltWhenHeldSlotChanges", PlayerItemHeldEvent.class);
        Method hands = TreeFellerSVC.class.getMethod("haltWhenHandsSwap", PlayerSwapHandItemsEvent.class);

        assertMonitorCancellationHandler(sneak);
        assertMonitorCancellationHandler(held);
        assertMonitorCancellationHandler(hands);
    }

    private void assertMonitorCancellationHandler(Method method) {
        EventHandler handler = method.getAnnotation(EventHandler.class);

        assertEquals(EventPriority.MONITOR, handler.priority());
        assertTrue(handler.ignoreCancelled());
    }
}
