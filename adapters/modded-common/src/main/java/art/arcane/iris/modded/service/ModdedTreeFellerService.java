package art.arcane.iris.modded.service;

import art.arcane.iris.modded.ModdedIrisLog;
import art.arcane.iris.configuration.IrisSettings;
import art.arcane.iris.world.tree.TreeDefinitionIndex;
import art.arcane.iris.world.tree.TreeMarkerTraversal;
import art.arcane.iris.generation.runtime.Engine;
import art.arcane.iris.structure.placement.StructurePlacementMarker;
import art.arcane.iris.generation.decoration.tree.TreeBlockMaterial;
import art.arcane.iris.modded.ModdedBlockBreakHandler;
import art.arcane.iris.modded.ModdedBlockState;
import art.arcane.iris.modded.ModdedEngineBootstrap;
import art.arcane.iris.modded.ModdedScheduler;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;

public final class ModdedTreeFellerService implements ModdedTickableService {
    private static final ThreadLocal<Integer> BREAK_PROBE_DEPTH = ThreadLocal.withInitial(() -> 0);

    private final AtomicBoolean enabled = new AtomicBoolean();
    private final Set<TreeClaim> activeClaims = ConcurrentHashMap.newKeySet();
    private final Set<FellingRun> activeRuns = ConcurrentHashMap.newKeySet();
    private final Map<Engine, TreeDefinitionIndex> definitions = Collections.synchronizedMap(new WeakHashMap<>());

    public static boolean isBreakProbe() {
        return BREAK_PROBE_DEPTH.get() > 0;
    }

    public static boolean runBreakProbe(BooleanSupplier probe) {
        int depth = BREAK_PROBE_DEPTH.get();
        BREAK_PROBE_DEPTH.set(depth + 1);
        try {
            return probe.getAsBoolean();
        } finally {
            if (depth == 0) {
                BREAK_PROBE_DEPTH.remove();
            } else {
                BREAK_PROBE_DEPTH.set(depth);
            }
        }
    }

    @Override
    public void onEnable() {
        enabled.set(true);
    }

    @Override
    public void onDisable() {
        enabled.set(false);
        for (FellingRun run : List.copyOf(activeRuns)) {
            finish(run);
        }
        activeRuns.clear();
        activeClaims.clear();
        definitions.clear();
    }

    @Override
    public void onServerTick(MinecraftServer server) {
        for (FellingRun run : List.copyOf(activeRuns)) {
            if (!isRunControlActive(run)) {
                finish(run);
            }
        }
    }

    public PreparedOrigin prepare(
            ServerLevel level,
            ServerPlayer player,
            BlockPos position,
            BlockState state
    ) {
        IrisSettings.IrisSettingsTreeFeller settings = IrisSettings.get().getTreeFeller();
        if (!enabled.get()
                || settings == null
                || !settings.isEnabled()
                || !player.gameMode().isSurvival()
                || !player.isShiftKeyDown()
                || !state.is(BlockTags.LOGS)
                || !player.getInventory().getSelectedItem().is(ItemTags.AXES)
                || !ModdedEngineBootstrap.loader().hasTreeFellerPermission(player)) {
            return null;
        }
        Engine engine = ModdedBlockBreakHandler.engineFor(level);
        if (engine == null || engine.isClosed()) {
            return null;
        }
        int minimumY = level.getMinY();
        String marker = markerAt(engine, minimumY, position);
        StructurePlacementMarker.Decoded decoded = StructurePlacementMarker.decode(marker);
        if (decoded == null || decoded.structureAware()) {
            return null;
        }
        TreeBlockMaterial expectedMaterial = materialAt(engine, minimumY, position);
        if (expectedMaterial != null && !expectedMaterial.matches(ModdedBlockState.serialize(state))) {
            return null;
        }
        if (expectedMaterial == null
                && !decoded.objectKey().startsWith("trees/")
                && !definitionIndex(engine).isTreeMarker(marker)) {
            return null;
        }
        ItemStack tool = player.getInventory().getSelectedItem();
        return new PreparedOrigin(
                this,
                level,
                player,
                position.immutable(),
                state,
                engine,
                marker,
                minimumY,
                level.getMaxY(),
                player.getInventory().getSelectedSlot(),
                tool.copy(),
                settings.getDurabilityPreservationChance()
        );
    }

    public OriginDropRoute completeOrigin(PreparedOrigin prepared) {
        if (prepared == null
                || prepared.owner() != this
                || !enabled.get()
                || prepared.engine().isClosed()) {
            return null;
        }
        TreeClaim claim = new TreeClaim(prepared.level(), prepared.marker());
        if (!activeClaims.add(claim)) {
            return null;
        }
        ModdedTreeFellerPresentation presentation = new ModdedTreeFellerPresentation(
                prepared.player(),
                prepared.level()
        );
        FellingRun run = new FellingRun(claim, prepared, presentation);
        if (!normalizeOriginTool(run)) {
            activeClaims.remove(claim);
            return null;
        }
        activeRuns.add(run);
        presentation.activate(prepared.position(), prepared.state());
        OriginDropRoute route = presentation::route;
        if (run.toolBroken) {
            ModdedBlockBreakHandler.completeManagedBreak(prepared.level(), prepared.position());
            finish(run);
            return route;
        }
        discover(run);
        return route;
    }

    private TreeDefinitionIndex definitionIndex(Engine engine) {
        synchronized (definitions) {
            return definitions.computeIfAbsent(engine, TreeDefinitionIndex::build);
        }
    }

    private boolean normalizeOriginTool(FellingRun run) {
        PreparedOrigin prepared = run.prepared;
        ItemStack before = prepared.toolBefore();
        ItemStack current = prepared.player().getInventory().getItem(prepared.heldSlot());
        boolean currentMatches = !current.isEmpty()
                && ItemStack.matchesIgnoringComponents(
                before,
                current,
                (componentType) -> componentType == DataComponents.DAMAGE
        );
        if (!before.isDamageableItem()) {
            if (!currentMatches || !ItemStack.isSameItemSameComponents(before, current)) {
                return false;
            }
            run.expectedTool = current.copy();
            return true;
        }

        boolean preserve = ThreadLocalRandom.current().nextInt(100) < prepared.preservationChance();
        int desiredDamage = before.getDamageValue() + (preserve ? 0 : 1);
        if (desiredDamage >= before.getMaxDamage()) {
            if (!current.isEmpty() && !currentMatches) {
                return false;
            }
            Item brokenItem = before.getItem();
            prepared.player().getInventory().setItem(prepared.heldSlot(), ItemStack.EMPTY);
            if (!current.isEmpty()) {
                prepared.player().onEquippedItemBroken(brokenItem, EquipmentSlot.MAINHAND);
            }
            prepared.player().inventoryMenu.sendAllDataToRemote();
            run.expectedTool = ItemStack.EMPTY;
            run.toolBroken = true;
            return true;
        }
        if (!current.isEmpty() && !currentMatches) {
            return false;
        }
        ItemStack normalized = before.copy();
        normalized.setDamageValue(desiredDamage);
        prepared.player().getInventory().setItem(prepared.heldSlot(), normalized);
        prepared.player().inventoryMenu.sendAllDataToRemote();
        run.expectedTool = normalized.copy();
        return true;
    }

    private void discover(FellingRun run) {
        ModdedScheduler scheduler = ModdedEngineBootstrap.schedulerOrNull();
        if (scheduler == null) {
            ModdedBlockBreakHandler.completeManagedBreak(run.prepared.level(), run.prepared.position());
            finish(run);
            return;
        }
        scheduler.async(() -> {
            TreeMarkerTraversal.Discovery discovery;
            try {
                PreparedOrigin prepared = run.prepared;
                TreeMarkerTraversal.Position trigger = positionOf(prepared.position());
                discovery = TreeMarkerTraversal.discover(
                        trigger,
                        prepared.marker(),
                        prepared.minimumY(),
                        prepared.maximumY(),
                        (x, y, z) -> markerAt(prepared.engine(), prepared.minimumY(), x, y, z)
                );
            } catch (Throwable error) {
                ModdedIrisLog.error("Iris modded tree-feller discovery failed", error);
                discovery = new TreeMarkerTraversal.Discovery(List.of(), false);
            }
            TreeMarkerTraversal.Discovery resolved = discovery;
            scheduler.global(() -> beginErosion(run, resolved));
        });
    }

    private void beginErosion(FellingRun run, TreeMarkerTraversal.Discovery discovery) {
        ModdedBlockBreakHandler.completeManagedBreak(run.prepared.level(), run.prepared.position());
        if (run.finished.get() || !discovery.complete()) {
            finish(run);
            return;
        }
        TreeMarkerTraversal.Position trigger = positionOf(run.prepared.position());
        run.work = discovery.members().stream()
                .filter((position) -> !position.equals(trigger))
                .toList();
        if (run.work.isEmpty()) {
            finish(run);
            return;
        }
        run.blocksPerPulse = ModdedTreeFellerPresentation.blocksPerPulse(run.work.size());
        run.effectStride = ModdedTreeFellerPresentation.effectStride(run.blocksPerPulse);
        scheduleNextPulse(run);
    }

    private void scheduleNextPulse(FellingRun run) {
        ModdedScheduler scheduler = ModdedEngineBootstrap.schedulerOrNull();
        if (scheduler == null) {
            finish(run);
            return;
        }
        scheduler.laterGlobal(() -> processPulse(run), 1);
    }

    private void processPulse(FellingRun run) {
        if (run.finished.get() || !isRunControlActive(run)) {
            finish(run);
            return;
        }
        int processedThisPulse = 0;
        while (processedThisPulse < run.blocksPerPulse && run.cursor < run.work.size()) {
            if (!isRunControlActive(run)) {
                finish(run);
                return;
            }
            TreeMarkerTraversal.Position position = run.work.get(run.cursor++);
            MemberResult result = processMember(run, position);
            if (result == MemberResult.STOP) {
                finish(run);
                return;
            }
            processedThisPulse++;
        }
        run.presentation.flush();
        if (run.cursor >= run.work.size()) {
            finish(run);
            return;
        }
        scheduleNextPulse(run);
    }

    private MemberResult processMember(FellingRun run, TreeMarkerTraversal.Position position) {
        PreparedOrigin prepared = run.prepared;
        BlockPos blockPosition = blockPosition(position);
        ServerLevel level = prepared.level();
        if (!level.isLoaded(blockPosition)) {
            return MemberResult.STOP;
        }
        BlockState state = level.getBlockState(blockPosition);
        if (state.isAir()) {
            ModdedBlockBreakHandler.completeManagedBreak(level, blockPosition);
            return MemberResult.CONTINUE;
        }
        if (!prepared.marker().equals(markerAt(prepared.engine(), prepared.minimumY(), blockPosition))) {
            return MemberResult.STOP;
        }
        TreeBlockMaterial expectedMaterial = materialAt(prepared.engine(), prepared.minimumY(), blockPosition);
        if (expectedMaterial != null && !expectedMaterial.matches(ModdedBlockState.serialize(state))) {
            ModdedBlockBreakHandler.completeManagedBreak(level, blockPosition);
            return state.is(BlockTags.LOGS) ? MemberResult.STOP : MemberResult.CONTINUE;
        }
        boolean log = state.is(BlockTags.LOGS);
        ServerPlayer player = prepared.player();
        if (!level.mayInteract(player, blockPosition)
                || player.blockActionRestricted(level, blockPosition, player.gameMode())
                || !player.getInventory().getSelectedItem().canDestroyBlock(
                state,
                level,
                blockPosition,
                player
        )) {
            return log ? MemberResult.STOP : MemberResult.CONTINUE;
        }
        if (!ModdedEngineBootstrap.loader().canTreeFellerBreak(
                level,
                player,
                blockPosition,
                state
        )) {
            return log ? MemberResult.STOP : MemberResult.CONTINUE;
        }
        if (!state.equals(level.getBlockState(blockPosition))
                || !prepared.marker().equals(markerAt(prepared.engine(), prepared.minimumY(), blockPosition))) {
            return MemberResult.STOP;
        }

        ItemStack toolForDrops = prepared.player().getInventory().getSelectedItem().copy();
        BlockEntity blockEntity = state.hasBlockEntity() ? level.getBlockEntity(blockPosition) : null;
        List<ItemStack> vanillaDrops = Block.getDrops(
                state,
                level,
                blockPosition,
                blockEntity,
                player,
                toolForDrops
        );
        ModdedBlockBreakHandler.Result customDrops = ModdedBlockBreakHandler.evaluateManagedDrops(
                level,
                blockPosition,
                state
        );
        ToolReservation reservation = log ? reserveToolDamage(run) : ToolReservation.free(toolForDrops);
        if (reservation == null) {
            return MemberResult.STOP;
        }
        if (!level.destroyBlock(blockPosition, false, player, 512)) {
            refundToolDamage(run, reservation);
            return MemberResult.STOP;
        }
        ModdedBlockBreakHandler.completeManagedBreak(level, blockPosition);
        int processed = run.processed++;
        float progress = run.work.size() <= 1 ? 1F : (float) processed / (float) (run.work.size() - 1);
        float pitch = Math.min(1.95F, 0.65F + (progress * 1.25F));
        run.presentation.erode(blockPosition, state, processed, run.effectStride, pitch);
        run.presentation.route(customDrops.combinedDrops(vanillaDrops));
        return reservation.broke() ? MemberResult.STOP : MemberResult.CONTINUE;
    }

    private ToolReservation reserveToolDamage(FellingRun run) {
        PreparedOrigin prepared = run.prepared;
        ItemStack current = prepared.player().getInventory().getSelectedItem();
        if (!ItemStack.isSameItemSameComponents(current, run.expectedTool) || !current.is(ItemTags.AXES)) {
            return null;
        }
        ItemStack before = current.copy();
        if (!current.isDamageableItem()
                || ThreadLocalRandom.current().nextInt(100) < prepared.preservationChance()) {
            return ToolReservation.free(before);
        }
        int nextDamage = current.getDamageValue() + 1;
        if (nextDamage >= current.getMaxDamage()) {
            Item brokenItem = current.getItem();
            prepared.player().getInventory().setSelectedItem(ItemStack.EMPTY);
            prepared.player().onEquippedItemBroken(brokenItem, EquipmentSlot.MAINHAND);
            prepared.player().inventoryMenu.sendAllDataToRemote();
            run.expectedTool = ItemStack.EMPTY;
            return new ToolReservation(before, true, true);
        }
        current.setDamageValue(nextDamage);
        prepared.player().getInventory().setSelectedItem(current);
        prepared.player().inventoryMenu.sendAllDataToRemote();
        run.expectedTool = current.copy();
        return new ToolReservation(before, true, false);
    }

    private void refundToolDamage(FellingRun run, ToolReservation reservation) {
        if (!reservation.charged()) {
            return;
        }
        PreparedOrigin prepared = run.prepared;
        ItemStack current = prepared.player().getInventory().getSelectedItem();
        boolean expectedEmpty = run.expectedTool.isEmpty();
        if ((expectedEmpty && current.isEmpty())
                || (!expectedEmpty && ItemStack.isSameItemSameComponents(current, run.expectedTool))) {
            ItemStack restored = reservation.before().copy();
            prepared.player().getInventory().setSelectedItem(restored);
            prepared.player().inventoryMenu.sendAllDataToRemote();
            run.expectedTool = restored.copy();
        }
    }

    private boolean isRunControlActive(FellingRun run) {
        PreparedOrigin prepared = run.prepared;
        ServerPlayer player = prepared.player();
        IrisSettings.IrisSettingsTreeFeller settings = IrisSettings.get().getTreeFeller();
        if (!enabled.get()
                || settings == null
                || !settings.isEnabled()
                || run.finished.get()
                || player.isRemoved()
                || player.hasDisconnected()
                || player.level() != prepared.level()
                || !player.gameMode().isSurvival()
                || !player.isShiftKeyDown()
                || player.getInventory().getSelectedSlot() != prepared.heldSlot()
                || run.expectedTool.isEmpty()) {
            return false;
        }
        ItemStack current = player.getInventory().getSelectedItem();
        return current.is(ItemTags.AXES) && ItemStack.isSameItemSameComponents(current, run.expectedTool);
    }

    private void finish(FellingRun run) {
        if (run.finished.compareAndSet(false, true)) {
            activeRuns.remove(run);
            activeClaims.remove(run.claim);
            ModdedBlockBreakHandler.completeManagedBreak(run.prepared.level(), run.prepared.position());
            run.presentation.finish();
        }
    }

    private String markerAt(Engine engine, int minimumY, BlockPos position) {
        return markerAt(engine, minimumY, position.getX(), position.getY(), position.getZ());
    }

    private String markerAt(Engine engine, int minimumY, int x, int y, int z) {
        return engine.getMantle().getMantle().get(x, y - minimumY, z, String.class);
    }

    private TreeBlockMaterial materialAt(Engine engine, int minimumY, BlockPos position) {
        return engine.getMantle().getMantle().get(
                position.getX(),
                position.getY() - minimumY,
                position.getZ(),
                TreeBlockMaterial.class
        );
    }

    private TreeMarkerTraversal.Position positionOf(BlockPos position) {
        return new TreeMarkerTraversal.Position(position.getX(), position.getY(), position.getZ());
    }

    private BlockPos blockPosition(TreeMarkerTraversal.Position position) {
        return new BlockPos(position.x(), position.y(), position.z());
    }

    public record PreparedOrigin(
            ModdedTreeFellerService owner,
            ServerLevel level,
            ServerPlayer player,
            BlockPos position,
            BlockState state,
            Engine engine,
            String marker,
            int minimumY,
            int maximumY,
            int heldSlot,
            ItemStack toolBefore,
            int preservationChance
    ) {
        public PreparedOrigin {
            Objects.requireNonNull(owner, "owner");
            Objects.requireNonNull(level, "level");
            Objects.requireNonNull(player, "player");
            Objects.requireNonNull(position, "position");
            Objects.requireNonNull(state, "state");
            Objects.requireNonNull(engine, "engine");
            Objects.requireNonNull(marker, "marker");
            Objects.requireNonNull(toolBefore, "toolBefore");
        }
    }

    @FunctionalInterface
    public interface OriginDropRoute {
        boolean route(Iterable<ItemStack> drops);
    }

    private enum MemberResult {
        CONTINUE,
        STOP
    }

    private record TreeClaim(ServerLevel level, String marker) {
    }

    private record ToolReservation(ItemStack before, boolean charged, boolean broke) {
        private static ToolReservation free(ItemStack before) {
            return new ToolReservation(before, false, false);
        }
    }

    private static final class FellingRun {
        private final TreeClaim claim;
        private final PreparedOrigin prepared;
        private final ModdedTreeFellerPresentation presentation;
        private final AtomicBoolean finished = new AtomicBoolean();
        private ItemStack expectedTool = ItemStack.EMPTY;
        private List<TreeMarkerTraversal.Position> work = List.of();
        private int cursor;
        private int processed;
        private int blocksPerPulse = 1;
        private int effectStride = 1;
        private boolean toolBroken;

        private FellingRun(
                TreeClaim claim,
                PreparedOrigin prepared,
                ModdedTreeFellerPresentation presentation
        ) {
            this.claim = claim;
            this.prepared = prepared;
            this.presentation = presentation;
        }
    }
}
