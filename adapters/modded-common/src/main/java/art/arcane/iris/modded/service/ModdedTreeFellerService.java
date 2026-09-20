package art.arcane.iris.modded.service;

import art.arcane.volmlib.nativelib.terrain.NativeWorld;
import art.arcane.volmlib.nativelib.terrain.NativeBlockPoint;
import art.arcane.volmlib.nativelib.terrain.NativeBlockState;
import art.arcane.volmlib.nativelib.minecraft26_2.modded.NativeProtocolPlayer;
import art.arcane.volmlib.nativelib.minecraft26_2.modded.NativeHarvestSession;
import art.arcane.volmlib.nativelib.minecraft26_2.modded.NativeItemStack;

import art.arcane.iris.modded.ModdedIrisLog;
import art.arcane.iris.configuration.IrisSettings;
import art.arcane.iris.world.tree.TreeDefinitionIndex;
import art.arcane.iris.world.tree.TreeMarkerTraversal;
import art.arcane.iris.generation.runtime.Engine;
import art.arcane.iris.structure.placement.StructurePlacementMarker;
import art.arcane.iris.generation.decoration.tree.TreeBlockMaterial;
import art.arcane.iris.modded.ModdedBlockBreakHandler;
import art.arcane.volmlib.nativelib.minecraft26_2.modded.ModdedBlockState;
import art.arcane.iris.modded.ModdedEngineBootstrap;
import art.arcane.iris.modded.ModdedScheduler;
import art.arcane.volmlib.nativelib.minecraft26_2.modded.NativeModdedServer;

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
    public void onServerTick(NativeModdedServer server) {
        for (FellingRun run : List.copyOf(activeRuns)) {
            if (!isRunControlActive(run)) {
                finish(run);
            }
        }
    }

    public PreparedOrigin prepare(
            NativeWorld level,
            NativeProtocolPlayer player,
            NativeBlockPoint position,
            NativeBlockState state
    ) {
        IrisSettings.IrisSettingsTreeFeller settings = IrisSettings.get().getTreeFeller();
        if (!enabled.get()
                || settings == null
                || !settings.isEnabled()
                || !NativeHarvestSession.survival(player)
                || !NativeHarvestSession.sneaking(player)
                || !NativeHarvestSession.log(state)
                || !NativeHarvestSession.holdingAxe(player)
                || !NativeHarvestSession.hasPermission(ModdedEngineBootstrap.loader(), player)) {
            return null;
        }
        Engine engine = ModdedBlockBreakHandler.engineFor(level);
        if (engine == null || engine.isClosed()) {
            return null;
        }
        int minimumY = level.minHeight();
        String marker = markerAt(engine, minimumY, position);
        StructurePlacementMarker.Decoded decoded = StructurePlacementMarker.decode(marker);
        if (decoded == null || decoded.structureAware()) {
            return null;
        }
        TreeBlockMaterial expectedMaterial = materialAt(engine, minimumY, position);
        if (expectedMaterial != null && !expectedMaterial.matches(state.key())) {
            return null;
        }
        if (expectedMaterial == null
                && !decoded.objectKey().startsWith("trees/")
                && !definitionIndex(engine).isTreeMarker(marker)) {
            return null;
        }
        NativeHarvestSession harvest = new NativeHarvestSession(level, player);
        return new PreparedOrigin(
                this,
                level,
                player,
                position,
                state,
                engine,
                marker,
                minimumY,
                level.maxHeight() - 1,
                harvest,
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
        if (prepared.harvest().toolBroken()) {
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
        return run.prepared.harvest().normalizeOriginTool(
                ThreadLocalRandom.current().nextInt(100) < run.prepared.preservationChance());
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
        NativeBlockPoint blockPosition = blockPosition(position);
        NativeWorld level = prepared.level();
        if (!prepared.harvest().loaded(blockPosition)) {
            return MemberResult.STOP;
        }
        NativeBlockState state = level.getBlock(blockPosition.x(), blockPosition.y(), blockPosition.z());
        if (NativeHarvestSession.air(state)) {
            ModdedBlockBreakHandler.completeManagedBreak(level, blockPosition);
            return MemberResult.CONTINUE;
        }
        if (!prepared.marker().equals(markerAt(prepared.engine(), prepared.minimumY(), blockPosition))) {
            return MemberResult.STOP;
        }
        TreeBlockMaterial expectedMaterial = materialAt(prepared.engine(), prepared.minimumY(), blockPosition);
        if (expectedMaterial != null && !expectedMaterial.matches(state.key())) {
            ModdedBlockBreakHandler.completeManagedBreak(level, blockPosition);
            return NativeHarvestSession.log(state) ? MemberResult.STOP : MemberResult.CONTINUE;
        }
        boolean log = NativeHarvestSession.log(state);
        NativeProtocolPlayer player = prepared.player();
        if (!prepared.harvest().mayDestroy(blockPosition, state)) {
            return log ? MemberResult.STOP : MemberResult.CONTINUE;
        }
        if (!prepared.harvest().canBreak(ModdedEngineBootstrap.loader(), blockPosition, state)) {
            return log ? MemberResult.STOP : MemberResult.CONTINUE;
        }
        if (!state.equals(level.getBlock(blockPosition.x(), blockPosition.y(), blockPosition.z()))
                || !prepared.marker().equals(markerAt(prepared.engine(), prepared.minimumY(), blockPosition))) {
            return MemberResult.STOP;
        }

        List<NativeItemStack> vanillaDrops = prepared.harvest().drops(blockPosition, state);
        ModdedBlockBreakHandler.Result customDrops = ModdedBlockBreakHandler.evaluateManagedDrops(
                level,
                blockPosition,
                state
        );
        NativeHarvestSession.Reservation reservation = log ? prepared.harvest().reserveToolDamage(
                ThreadLocalRandom.current().nextInt(100) < prepared.preservationChance()) : null;
        if (log && reservation == null) {
            return MemberResult.STOP;
        }
        if (!prepared.harvest().destroy(blockPosition)) {
            if (reservation != null) {
                prepared.harvest().refundToolDamage(reservation);
            }
            return MemberResult.STOP;
        }
        ModdedBlockBreakHandler.completeManagedBreak(level, blockPosition);
        int processed = run.processed++;
        float progress = run.work.size() <= 1 ? 1F : (float) processed / (float) (run.work.size() - 1);
        float pitch = Math.min(1.95F, 0.65F + (progress * 1.25F));
        run.presentation.erode(blockPosition, state, processed, run.effectStride, pitch);
        run.presentation.route(customDrops.combinedDrops(vanillaDrops));
        return reservation != null && reservation.broke() ? MemberResult.STOP : MemberResult.CONTINUE;
    }

    private boolean isRunControlActive(FellingRun run) {
        PreparedOrigin prepared = run.prepared;
        NativeHarvestSession harvest = prepared.harvest();
        IrisSettings.IrisSettingsTreeFeller settings = IrisSettings.get().getTreeFeller();
        return enabled.get() && settings != null && settings.isEnabled() && !run.finished.get()
                && harvest.active() && harvest.survival() && harvest.sneaking() && harvest.holdingAxe();
    }

    private void finish(FellingRun run) {
        if (run.finished.compareAndSet(false, true)) {
            activeRuns.remove(run);
            activeClaims.remove(run.claim);
            ModdedBlockBreakHandler.completeManagedBreak(run.prepared.level(), run.prepared.position());
            run.presentation.finish();
        }
    }

    private String markerAt(Engine engine, int minimumY, NativeBlockPoint position) {
        return markerAt(engine, minimumY, position.x(), position.y(), position.z());
    }

    private String markerAt(Engine engine, int minimumY, int x, int y, int z) {
        return engine.getMantle().getMantle().get(x, y - minimumY, z, String.class);
    }

    private TreeBlockMaterial materialAt(Engine engine, int minimumY, NativeBlockPoint position) {
        return engine.getMantle().getMantle().get(
                position.x(),
                position.y() - minimumY,
                position.z(),
                TreeBlockMaterial.class
        );
    }

    private TreeMarkerTraversal.Position positionOf(NativeBlockPoint position) {
        return new TreeMarkerTraversal.Position(position.x(), position.y(), position.z());
    }

    private NativeBlockPoint blockPosition(TreeMarkerTraversal.Position position) {
        return new NativeBlockPoint(position.x(), position.y(), position.z());
    }

    public record PreparedOrigin(
            ModdedTreeFellerService owner,
            NativeWorld level,
            NativeProtocolPlayer player,
            NativeBlockPoint position,
            NativeBlockState state,
            Engine engine,
            String marker,
            int minimumY,
            int maximumY,
            NativeHarvestSession harvest,
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
            Objects.requireNonNull(harvest, "harvest");
        }
    }

    @FunctionalInterface
    public interface OriginDropRoute {
        boolean route(Iterable<NativeItemStack> drops);
    }

    private enum MemberResult {
        CONTINUE,
        STOP
    }

    private record TreeClaim(NativeWorld level, String marker) {
    }

    private static final class FellingRun {
        private final TreeClaim claim;
        private final PreparedOrigin prepared;
        private final ModdedTreeFellerPresentation presentation;
        private final AtomicBoolean finished = new AtomicBoolean();
        private List<TreeMarkerTraversal.Position> work = List.of();
        private int cursor;
        private int processed;
        private int blocksPerPulse = 1;
        private int effectStride = 1;

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
