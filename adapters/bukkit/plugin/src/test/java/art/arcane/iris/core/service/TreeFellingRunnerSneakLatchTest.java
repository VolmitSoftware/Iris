package art.arcane.iris.core.service;

import art.arcane.iris.api.tree.TreeFellerRunHooks;
import art.arcane.iris.core.service.TreeFellerModel.TreeCandidate;
import art.arcane.iris.core.service.TreeFellerModel.TreeClaim;
import art.arcane.iris.core.service.tree.TreeMarkerTraversal;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.junit.Test;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class TreeFellingRunnerSneakLatchTest {
    private static final TreeFellerRunHooks LATCHED_HOOKS = new TreeFellerRunHooks() {
        @Override
        public boolean requiresSneaking() {
            return false;
        }

        @Override
        public void onActivationAccepted() {
        }

        @Override
        public boolean reserveLogCost() {
            return true;
        }

        @Override
        public void commitLogCost() {
        }

        @Override
        public void refundLogCost() {
        }
    };

    @Test
    public void runHooksRequireSneakingByDefault() {
        assertTrue(TreeFellerRunHooks.NONE.requiresSneaking());
    }

    @Test
    public void releasingSneakFinishesOnlyRunsThatRequireIt() {
        TreeFellerSVC service = new TreeFellerSVC();
        TreeFellingRunner runner = new TreeFellingRunner(service, null);
        UUID playerId = UUID.randomUUID();
        FellingRun sneakRun = registerRun(service, playerId, TreeFellerRunHooks.NONE, "sneak");
        FellingRun latchedRun = registerRun(service, playerId, LATCHED_HOOKS, "latched");

        runner.finishSneakRuns(playerId);

        assertTrue(sneakRun.finished.get());
        assertEquals("halted", sneakRun.abortReason);
        assertFalse(latchedRun.finished.get());
        assertTrue(service.activeRuns.get(playerId).contains(latchedRun));
    }

    @Test
    public void finishingAllRunsStillEndsLatchedRuns() {
        TreeFellerSVC service = new TreeFellerSVC();
        TreeFellingRunner runner = new TreeFellingRunner(service, null);
        UUID playerId = UUID.randomUUID();
        FellingRun latchedRun = registerRun(service, playerId, LATCHED_HOOKS, "latched");

        runner.finishRuns(playerId);

        assertTrue(latchedRun.finished.get());
        assertEquals("halted", latchedRun.abortReason);
    }

    private FellingRun registerRun(TreeFellerSVC service, UUID playerId, TreeFellerRunHooks hooks, String marker) {
        Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(playerId);
        World world = mock(World.class);
        TreeCandidate candidate = new TreeCandidate(
                null,
                world,
                player,
                mock(ItemStack.class),
                null,
                new TreeMarkerTraversal.Position(0, 64, 0)
        );
        TreeClaim claim = new TreeClaim(UUID.randomUUID(), marker);
        FellingRun run = new FellingRun(claim, candidate, 0, hooks, 0, new Location(world, 0D, 64D, 0D));
        service.activeClaims.add(claim);
        service.activeRuns.computeIfAbsent(playerId, ignored -> ConcurrentHashMap.newKeySet()).add(run);
        return run;
    }
}
