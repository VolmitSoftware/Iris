package art.arcane.iris.world.tree;

import art.arcane.iris.world.tree.TreeFellerModel.ChunkPosition;
import art.arcane.iris.world.tree.TreeFellerModel.DamageReservation;
import art.arcane.iris.world.tree.TreeFellerModel.TreeContext;
import art.arcane.iris.world.tree.TreeFellerModel.TreeMember;
import art.arcane.iris.generation.decoration.tree.TreeBlockMaterial;
import art.arcane.iris.spi.IrisLogging;
import art.arcane.iris.world.task.J;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

final class TreeFellingRunner {
    private final TreeFellerSVC service;
    private final TreeProvenance provenance;

    TreeFellingRunner(TreeFellerSVC service, TreeProvenance provenance) {
        this.service = service;
        this.provenance = provenance;
    }

    void discover(FellingRun run) {
        J.a(() -> {
            if (run.candidate.context().engine().isClosed()) {
                finish(run);
                return;
            }
            try {
                TreeMarkerTraversal.Discovery discovery = TreeMarkerTraversal.discover(
                        run.candidate.trigger(),
                        run.candidate.context().marker(),
                        run.candidate.context().minimumY(),
                        run.candidate.context().maximumY(),
                        (x, y, z) -> provenance.markerAt(
                                run.candidate.context().engine(),
                                run.candidate.context().minimumY(),
                                x,
                                y,
                                z
                        )
                );
                List<TreeMarkerTraversal.Position> positions = positionsForFelling(discovery, run.candidate.trigger());
                if (!discovery.complete()) {
                    run.abortReason = "discovery-incomplete";
                }
                preflight(run, positions, discovery.complete());
            } catch (Throwable error) {
                IrisLogging.reportError("Failed to discover an Iris tree for felling.", error);
                preflight(run, List.of(run.candidate.trigger()), false);
            }
        });
    }

    private void preflight(FellingRun run, List<TreeMarkerTraversal.Position> positions, boolean allowFallback) {
        Map<ChunkPosition, List<TreeMarkerTraversal.Position>> grouped = groupByChunk(positions);
        if (grouped.isEmpty()) {
            finish(run);
            return;
        }
        Map<TreeMarkerTraversal.Position, Integer> erosionOrder = new HashMap<>(positions.size());
        for (int index = 0; index < positions.size(); index++) {
            erosionOrder.put(positions.get(index), index);
        }

        List<TreeMember> members = Collections.synchronizedList(new ArrayList<>());
        AtomicBoolean failed = new AtomicBoolean();
        AtomicInteger remaining = new AtomicInteger(grouped.size());
        AtomicBoolean completed = new AtomicBoolean();

        for (Map.Entry<ChunkPosition, List<TreeMarkerTraversal.Position>> entry : grouped.entrySet()) {
            ChunkPosition chunk = entry.getKey();
            Runnable task = () -> {
                try {
                    if (!run.candidate.world().isChunkLoaded(chunk.x(), chunk.z())) {
                        run.abortReason = "chunk-unloaded";
                        failed.set(true);
                        return;
                    }
                    for (TreeMarkerTraversal.Position position : entry.getValue()) {
                        TreeMember member = inspectMember(run, position, erosionOrder.getOrDefault(position, Integer.MAX_VALUE));
                        if (member != null) {
                            members.add(member);
                        }
                    }
                } catch (Throwable error) {
                    failed.set(true);
                    IrisLogging.reportError(
                            "Failed to preflight an Iris tree-feller chunk at " + chunk.x() + "," + chunk.z() + ".",
                            error
                    );
                } finally {
                    completePreflightGroup(run, members, failed, remaining, completed, allowFallback);
                }
            };
            if (!J.runRegion(run.candidate.world(), chunk.x(), chunk.z(), task)) {
                failed.set(true);
                completePreflightGroup(run, members, failed, remaining, completed, allowFallback);
            }
        }
    }

    static List<TreeMarkerTraversal.Position> positionsForFelling(
            TreeMarkerTraversal.Discovery discovery,
            TreeMarkerTraversal.Position trigger
    ) {
        return discovery.complete() ? discovery.members() : List.of(trigger);
    }

    private void completePreflightGroup(
            FellingRun run,
            List<TreeMember> members,
            AtomicBoolean failed,
            AtomicInteger remaining,
            AtomicBoolean completed,
            boolean allowFallback
    ) {
        if (remaining.decrementAndGet() != 0 || !completed.compareAndSet(false, true)) {
            return;
        }
        if (failed.get() && allowFallback) {
            preflight(run, List.of(run.candidate.trigger()), false);
            return;
        }
        if (failed.get()) {
            finish(run);
            return;
        }

        List<TreeMember> ordered = orderMembers(run.candidate.trigger(), members);
        if (ordered.isEmpty() || !ordered.getFirst().position().equals(run.candidate.trigger())) {
            run.abortReason = "trigger-drift";
            finish(run);
            return;
        }
        run.work = ordered;
        run.blocksPerPulse = TreeFellerPresentation.blocksPerPulse(ordered.size());
        run.effectStride = TreeFellerPresentation.effectStride(run.blocksPerPulse);
        processNext(run);
    }

    private TreeMember inspectMember(FellingRun run, TreeMarkerTraversal.Position position, int erosionOrder) {
        World world = run.candidate.world();
        Block block = world.getBlockAt(position.x(), position.y(), position.z());
        TreeContext context = run.candidate.context();
        if (!context.marker().equals(provenance.markerAt(context.engine(), context.minimumY(), position))) {
            return null;
        }
        TreeBlockMaterial expected = provenance.materialAt(context.engine(), context.minimumY(), position);
        if (expected != null && !provenance.matchesExpectedMaterial(block, expected)) {
            provenance.clearProvenance(context.engine(), context.minimumY(), position);
            return null;
        }
        if (block.getType().isAir()) {
            provenance.clearProvenance(context.engine(), context.minimumY(), position);
            return null;
        }
        return new TreeMember(position, Tag.LOGS.isTagged(block.getType()), expected, erosionOrder);
    }

    private List<TreeMember> orderMembers(
            TreeMarkerTraversal.Position trigger,
            Collection<TreeMember> discovered
    ) {
        Comparator<TreeMember> erosionOrder = Comparator
                .comparingInt(TreeMember::erosionOrder)
                .thenComparingInt(member -> member.position().y())
                .thenComparingInt(member -> member.position().x())
                .thenComparingInt(member -> member.position().z());
        List<TreeMember> ordered = discovered.stream().sorted(erosionOrder).toList();
        if (ordered.isEmpty() || !ordered.getFirst().position().equals(trigger)) {
            return List.of();
        }
        return ordered;
    }

    private void processNext(FellingRun run) {
        if (run.finished.get()) {
            return;
        }
        int index = run.cursor.getAndIncrement();
        if (index >= run.work.size()) {
            finish(run);
            return;
        }

        TreeMember member = run.work.get(index);
        ChunkPosition chunk = new ChunkPosition(member.position().x() >> 4, member.position().z() >> 4);
        Runnable task = () -> runTask(
                run,
                "Failed to prepare an Iris tree-feller block.",
                () -> prepareBreak(run, member)
        );
        if (!runMemberRegion(run, chunk.x(), chunk.z(), task)) {
            if (member.log()) {
                run.abortReason = "schedule-refused";
                finish(run);
            } else {
                continueRun(run);
            }
        }
    }

    // On Paper, J.runRegion/J.runEntity always defer a full tick even when already on the
    // main thread, which collapsed the pulse pacing to one block per tick and forced players
    // to hold sneak for 10+ seconds on large trees. Folia keeps the scheduler hop.
    static boolean shouldRunInline(boolean folia, boolean primaryThread) {
        return !folia && primaryThread;
    }

    private boolean runMemberRegion(FellingRun run, int chunkX, int chunkZ, Runnable task) {
        if (shouldRunInline(J.isFolia(), Bukkit.isPrimaryThread())) {
            task.run();
            return true;
        }
        return J.runRegion(run.candidate.world(), chunkX, chunkZ, task);
    }

    private boolean runMemberEntity(FellingRun run, Runnable task) {
        if (shouldRunInline(J.isFolia(), Bukkit.isPrimaryThread())) {
            task.run();
            return true;
        }
        return J.runEntity(run.candidate.player(), task);
    }

    private void prepareBreak(FellingRun run, TreeMember member) {
        Block block = liveMemberBlock(run, member);
        if (block == null) {
            if (member.log()) {
                run.abortReason = "member-drift";
                finish(run);
            } else {
                continueRun(run);
            }
            return;
        }

        if (!member.log()) {
            runMutationTask(
                    run,
                    member,
                    new DamageReservation(run.expectedTool.clone(), false, false, false),
                    new AtomicBoolean()
            );
            return;
        }

        Runnable task = () -> runTask(
                run,
                "Failed to reserve Iris tree-feller tool durability.",
                () -> reserveDamage(run, member)
        );
        if (!runMemberEntity(run, task)) {
            run.abortReason = "schedule-refused";
            finish(run);
        }
    }

    private void reserveDamage(FellingRun run, TreeMember member) {
        Player player = run.candidate.player();
        if (!isRunControlActive(run, player)) {
            run.abortReason = "control-released";
            finish(run);
            return;
        }
        PlayerInventory inventory = player.getInventory();
        ItemStack current = inventory.getItem(run.heldSlot);
        if (current == null
                || inventory.getHeldItemSlot() != run.heldSlot
                || !current.isSimilar(run.expectedTool)
                || !TreeFellerSVC.isAxe(current)) {
            run.abortReason = "tool-changed";
            finish(run);
            return;
        }

        if (!reserveLogCost(run)) {
            run.abortReason = "log-cost-refused";
            finish(run);
            return;
        }

        ItemStack before = current.clone();
        ItemMeta meta = current.getItemMeta();
        if (meta.isUnbreakable() || ThreadLocalRandom.current().nextInt(100) < run.preservationChance) {
            scheduleMutation(run, member, new DamageReservation(before, false, false, true));
            return;
        }
        if (!(meta instanceof Damageable damageable) || current.getType().getMaxDurability() <= 0) {
            refundAndFinish(run, new DamageReservation(before, false, false, true));
            return;
        }

        int nextDamage = damageable.getDamage() + 1;
        boolean broke = nextDamage >= current.getType().getMaxDurability();
        if (broke) {
            inventory.setItem(run.heldSlot, new ItemStack(Material.AIR));
            run.expectedTool = new ItemStack(Material.AIR);
        } else {
            damageable.setDamage(nextDamage);
            current.setItemMeta(meta);
            inventory.setItem(run.heldSlot, current);
            run.expectedTool = current.clone();
        }
        scheduleMutation(run, member, new DamageReservation(before, true, broke, true));
    }

    private void scheduleMutation(FellingRun run, TreeMember member, DamageReservation reservation) {
        TreeMarkerTraversal.Position position = member.position();
        AtomicBoolean mutationSucceeded = new AtomicBoolean();
        Runnable task = () -> runMutationTask(run, member, reservation, mutationSucceeded);
        boolean scheduled;
        try {
            scheduled = runMemberRegion(
                    run,
                    position.x() >> 4,
                    position.z() >> 4,
                    task
            );
        } catch (Throwable error) {
            IrisLogging.reportError("Failed to schedule an Iris tree-feller block removal.", error);
            refundAndFinish(run, reservation);
            return;
        }
        if (!scheduled) {
            refundAndFinish(run, reservation);
        }
    }

    private void runMutationTask(
            FellingRun run,
            TreeMember member,
            DamageReservation reservation,
            AtomicBoolean mutationSucceeded
    ) {
        if (run.finished.get()) {
            refundAndFinish(run, reservation);
            return;
        }
        try {
            probeAndMutate(run, member, reservation, mutationSucceeded);
        } catch (Throwable error) {
            IrisLogging.reportError("Failed to remove an Iris tree-feller block.", error);
            if (mutationSucceeded.get()) {
                finish(run);
            } else {
                refundAndFinish(run, reservation);
            }
        }
    }

    private void probeAndMutate(
            FellingRun run,
            TreeMember member,
            DamageReservation reservation,
            AtomicBoolean mutationSucceeded
    ) {
        Block block = liveMemberBlock(run, member);
        if (block == null) {
            refundAndFinish(run, reservation);
            return;
        }

        BlockBreakEvent probe = new RoutedBlockBreakEvent(block, run.candidate.player(), run, service);
        service.managedEvents.add(probe);
        try {
            Bukkit.getPluginManager().callEvent(probe);
        } catch (Throwable error) {
            probe.setCancelled(true);
            IrisLogging.reportError("Failed to dispatch an Iris tree-feller block probe.", error);
        } finally {
            service.managedEvents.remove(probe);
        }

        try {
            if (probe.isCancelled()) {
                if (reservation.charged() || reservation.logCostReserved()) {
                    run.abortReason = "probe-cancelled";
                    refundAndFinish(run, reservation);
                } else if (member.log()) {
                    run.abortReason = "probe-cancelled";
                    finish(run);
                } else {
                    continueRun(run);
                }
                return;
            }

            block = liveMemberBlock(run, member);
            if (run.finished.get() || block == null) {
                if (block == null) {
                    run.abortReason = "member-drift";
                }
                probe.setCancelled(true);
                refundAndFinish(run, reservation);
                return;
            }

            Location source = block.getLocation().clone().add(0.5D, 0.5D, 0.5D);
            BlockData visualData = block.getBlockData().clone();
            List<ItemStack> vanillaDrops = probe.isDropItems()
                    ? block.getDrops(reservation.toolForDrops()).stream()
                    .map(ItemStack::clone)
                    .toList()
                    : List.of();
            block.setType(Material.AIR, false);
            if (!block.getType().isAir()) {
                probe.setCancelled(true);
                refundAndFinish(run, reservation);
                return;
            }
            mutationSucceeded.set(true);
            run.presentation.erode(
                    source,
                    visualData,
                    member.erosionOrder(),
                    run.processed.get(),
                    run.blocksPerPulse,
                    run.effectStride,
                    run.work.size()
            );

            provenance.clearProvenance(
                    run.candidate.context().engine(),
                    run.candidate.context().minimumY(),
                    member.position()
            );
            service.routeDrops(run, vanillaDrops, source);
            if (!run.presentation.routeExperience(probe.getExpToDrop())) {
                service.dropExperience(source, probe.getExpToDrop());
            }
            if (reservation.logCostReserved()) {
                completeLogCost(run, reservation);
                return;
            }
            completeSuccessfulMutation(run, reservation);
        } catch (RuntimeException | Error error) {
            if (!mutationSucceeded.get()) {
                probe.setCancelled(true);
            }
            throw error;
        }
    }

    private Block liveMemberBlock(FellingRun run, TreeMember member) {
        World world = run.candidate.world();
        TreeMarkerTraversal.Position position = member.position();
        if (!world.isChunkLoaded(position.x() >> 4, position.z() >> 4)) {
            return null;
        }
        Block block = world.getBlockAt(position.x(), position.y(), position.z());
        TreeContext context = run.candidate.context();
        if (!context.marker().equals(provenance.markerAt(context.engine(), context.minimumY(), position))) {
            return null;
        }
        if (block.getType().isAir() || member.log() != Tag.LOGS.isTagged(block.getType())) {
            provenance.clearProvenance(context.engine(), context.minimumY(), position);
            return null;
        }
        TreeBlockMaterial expected = provenance.materialAt(context.engine(), context.minimumY(), position);
        if (member.expectedMaterial() != null && !member.expectedMaterial().equals(expected)) {
            provenance.clearProvenance(context.engine(), context.minimumY(), position);
            return null;
        }
        if (expected != null && !provenance.matchesExpectedMaterial(block, expected)) {
            provenance.clearProvenance(context.engine(), context.minimumY(), position);
            return null;
        }
        return block;
    }

    private void refundAndFinish(FellingRun run, DamageReservation reservation) {
        if (!reservation.charged() && !reservation.logCostReserved()) {
            finish(run);
            return;
        }
        if (!J.runEntity(run.candidate.player(), () -> {
            if (reservation.charged()) {
                PlayerInventory inventory = run.candidate.player().getInventory();
                ItemStack current = inventory.getItem(run.heldSlot);
                boolean expectedAir = run.expectedTool.getType() == Material.AIR;
                boolean currentMatches = expectedAir
                        ? current == null || current.getType() == Material.AIR
                        : current != null && current.isSimilar(run.expectedTool);
                if (currentMatches) {
                    inventory.setItem(run.heldSlot, reservation.toolForDrops().clone());
                    run.expectedTool = reservation.toolForDrops().clone();
                }
            }
            if (reservation.logCostReserved()) {
                refundLogCost(run);
            }
            finish(run);
        })) {
            finish(run);
        }
    }

    private boolean isRunControlActive(FellingRun run, Player player) {
        return player.isOnline()
                && player.getGameMode() == GameMode.SURVIVAL
                && player.isSneaking()
                && player.getWorld().equals(run.candidate.world());
    }

    private boolean reserveLogCost(FellingRun run) {
        try {
            return run.runHooks.reserveLogCost();
        } catch (Throwable error) {
            IrisLogging.reportError("An Iris tree-feller integration log-cost reservation failed.", error);
            return false;
        }
    }

    private void completeLogCost(FellingRun run, DamageReservation reservation) {
        if (!J.runEntity(run.candidate.player(), () -> {
            try {
                run.runHooks.commitLogCost();
            } catch (Throwable error) {
                IrisLogging.reportError("An Iris tree-feller integration log-cost commit failed.", error);
                finish(run);
                return;
            }
            completeSuccessfulMutation(run, reservation);
        })) {
            finish(run);
        }
    }

    private void refundLogCost(FellingRun run) {
        try {
            run.runHooks.refundLogCost();
        } catch (Throwable error) {
            IrisLogging.reportError("An Iris tree-feller integration log-cost refund failed.", error);
        }
    }

    private void completeSuccessfulMutation(FellingRun run, DamageReservation reservation) {
        if (reservation.broke()) {
            run.abortReason = "tool-broke";
            finish(run);
            return;
        }
        continueRun(run);
    }

    private void continueRun(FellingRun run) {
        int processed = run.processed.incrementAndGet();
        if (processed % run.blocksPerPulse == 0) {
            J.s(() -> runTask(run, "Failed to continue an Iris tree-feller run.", () -> processNext(run)), 1);
        } else {
            processNext(run);
        }
    }

    private void runTask(FellingRun run, String context, Runnable task) {
        if (run.finished.get() || !service.isServiceEnabled()) {
            finish(run);
            return;
        }
        try {
            task.run();
        } catch (Throwable error) {
            IrisLogging.reportError(context, error);
            finish(run);
        }
    }

    void finish(FellingRun run) {
        if (run.finished.compareAndSet(false, true)) {
            service.activeClaims.remove(run.claim);
            Set<FellingRun> runs = service.activeRuns.get(run.candidate.player().getUniqueId());
            if (runs != null) {
                runs.remove(run);
                if (runs.isEmpty()) {
                    service.activeRuns.remove(run.candidate.player().getUniqueId(), runs);
                }
            }
            run.presentation.finish();
            IrisLogging.debug("Tree feller run ended: members=" + run.work.size()
                    + " processed=" + run.processed.get()
                    + " reason=" + (run.abortReason == null ? "complete" : run.abortReason));
        }
    }

    void finishRuns(UUID playerId) {
        Set<FellingRun> runs = service.activeRuns.get(playerId);
        if (runs == null) {
            return;
        }
        for (FellingRun run : List.copyOf(runs)) {
            if (run.abortReason == null) {
                run.abortReason = "halted";
            }
            finish(run);
        }
    }

    private Map<ChunkPosition, List<TreeMarkerTraversal.Position>> groupByChunk(
            List<TreeMarkerTraversal.Position> positions
    ) {
        Map<ChunkPosition, List<TreeMarkerTraversal.Position>> grouped = new LinkedHashMap<>();
        for (TreeMarkerTraversal.Position position : positions) {
            ChunkPosition chunk = new ChunkPosition(position.x() >> 4, position.z() >> 4);
            grouped.computeIfAbsent(chunk, ignored -> new ArrayList<>()).add(position);
        }
        return grouped;
    }
}
