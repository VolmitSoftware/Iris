package art.arcane.iris.world.tree;

import art.arcane.iris.api.tree.TreeFellerRunHooks;
import art.arcane.iris.world.tree.TreeFellerModel.TreeCandidate;
import art.arcane.iris.world.tree.TreeFellerModel.TreeClaim;
import art.arcane.iris.world.tree.TreeFellerModel.TreeMember;
import org.bukkit.Location;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

final class FellingRun {
    final TreeClaim claim;
    final TreeCandidate candidate;
    final int preservationChance;
    final TreeFellerRunHooks runHooks;
    final int heldSlot;
    final TreeFellerPresentation presentation;
    final AtomicBoolean finished = new AtomicBoolean();
    final AtomicInteger cursor = new AtomicInteger();
    final AtomicInteger processed = new AtomicInteger();
    volatile int blocksPerPulse = 1;
    volatile int effectStride = 1;
    volatile ItemStack expectedTool;
    volatile List<TreeMember> work = List.of();
    volatile String abortReason;

    FellingRun(
            TreeClaim claim,
            TreeCandidate candidate,
            int preservationChance,
            TreeFellerRunHooks runHooks,
            int heldSlot,
            Location fallbackLocation
    ) {
        this.claim = claim;
        this.candidate = candidate;
        this.preservationChance = preservationChance;
        this.runHooks = runHooks;
        this.heldSlot = heldSlot;
        this.presentation = new TreeFellerPresentation(candidate.player(), candidate.world(), fallbackLocation);
        this.expectedTool = candidate.tool().clone();
    }
}
