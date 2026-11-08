package art.arcane.iris.core.service;

import art.arcane.iris.engine.framework.PlacedStructurePiece;
import art.arcane.iris.engine.object.structure.jigsaw.IrisJigsawConnector;
import art.arcane.iris.engine.object.prefab.ObjectTurn;
import art.arcane.volmlib.util.math.IrisBlockVector;
import com.volmit.ModuleLog;
import com.volmit.block.BlockRegistry;
import com.volmit.block.BlockState;
import com.volmit.object.ObjectBlock;
import com.volmit.tooling.world.ToolWorld;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

public final class JigsawStudioPreviewRenderer {
    private static final int MAX_BLOCKS = 250_000;
    private static final String AIR = "minecraft:air";
    private static final String STRUCTURE_VOID = "minecraft:structure_void";
    private static final BlockState AIR_STATE = BlockState.parse(AIR);
    private static final int CHUNK_SHIFT = 4;

    private final Map<UUID, RenderState> requests = new HashMap<>();
    private final ModuleLog log;

    public JigsawStudioPreviewRenderer(ModuleLog log) {
        this.log = Objects.requireNonNull(log, "Jigsaw Studio preview log");
    }

    public static PreviewPlan plan(List<PlacedStructurePiece> pieces, BlockRegistry registry) throws IOException {
        List<PlacedStructurePiece> source = List.copyOf(Objects.requireNonNull(
                pieces,
                "Jigsaw Studio preview pieces"));
        if (source.isEmpty()) {
            return PreviewPlan.empty();
        }
        Map<BlockPosition, String> blocks = new LinkedHashMap<>();
        int minimumX = Integer.MAX_VALUE;
        int minimumY = Integer.MAX_VALUE;
        int minimumZ = Integer.MAX_VALUE;
        int maximumX = Integer.MIN_VALUE;
        int maximumY = Integer.MIN_VALUE;
        int maximumZ = Integer.MIN_VALUE;
        for (PlacedStructurePiece piece : source) {
            if (piece == null || piece.getObject() == null || piece.getPiece() == null
                    || piece.getRotation() == null) {
                throw new IOException("Jigsaw Studio preview contains an incomplete placed piece");
            }
            minimumX = Math.min(minimumX, piece.getMinX());
            minimumY = Math.min(minimumY, piece.getMinY());
            minimumZ = Math.min(minimumZ, piece.getMinZ());
            maximumX = Math.max(maximumX, piece.getMaxX());
            maximumY = Math.max(maximumY, piece.getMaxY());
            maximumZ = Math.max(maximumZ, piece.getMaxZ());
            appendObject(blocks, piece, registry);
            appendFinalStates(blocks, piece, registry);
            if (blocks.size() > MAX_BLOCKS) {
                throw new IOException("The seed-1337 preview exceeds the Studio render limit of "
                        + MAX_BLOCKS + " explicit blocks");
            }
        }
        return new PreviewPlan(
                Map.copyOf(blocks),
                new PreviewBounds(minimumX, minimumY, minimumZ, maximumX, maximumY, maximumZ));
    }

    public void render(
            ToolWorld world,
            UUID requestId,
            long generation,
            PreviewPlan plan,
            Consumer<RenderResult> completion
    ) {
        ToolWorld activeWorld = Objects.requireNonNull(world, "Jigsaw Studio preview world");
        UUID activeRequestId = Objects.requireNonNull(requestId, "Jigsaw Studio preview request ID");
        PreviewPlan activePlan = Objects.requireNonNull(plan, "Jigsaw Studio preview plan");
        Consumer<RenderResult> callback = Objects.requireNonNull(completion, "Jigsaw Studio preview callback");
        Map<BlockPosition, String> previous;
        Set<BlockPosition> uncertain;
        Map<Long, List<BlockUpdate>> updates;
        synchronized (this) {
            RenderState old = requests.get(activeRequestId);
            previous = old == null || old.world() != activeWorld
                    ? Map.of()
                    : old.blocks();
            uncertain = old == null || old.world() != activeWorld
                    ? Set.of()
                    : Set.copyOf(old.pending());
            updates = updates(previous, activePlan.blocks(), uncertain);
            Set<BlockPosition> pending = new HashSet<>();
            for (List<BlockUpdate> chunkUpdates : updates.values()) {
                for (BlockUpdate update : chunkUpdates) {
                    pending.add(update.position());
                }
            }
            requests.put(activeRequestId, new RenderState(
                    activeWorld, generation, activePlan.blocks(), activePlan.bounds(), pending));
        }
        if (updates.isEmpty()) {
            callback.accept(new RenderResult(true, 0, ""));
            return;
        }
        AtomicInteger remaining = new AtomicInteger(updates.size());
        AtomicBoolean failed = new AtomicBoolean();
        for (Map.Entry<Long, List<BlockUpdate>> chunk : updates.entrySet()) {
            int chunkX = chunkX(chunk.getKey());
            int chunkZ = chunkZ(chunk.getKey());
            JigsawStudioThreads.onRegion(
                    log,
                    activeWorld,
                    chunkX,
                    chunkZ,
                    () -> applyChunk(
                            activeWorld,
                            activeRequestId,
                            generation,
                            chunk.getValue(),
                            remaining,
                            failed,
                            callback),
                    () -> {
                        failed.set(true);
                        if (remaining.decrementAndGet() == 0) {
                            callback.accept(new RenderResult(
                                    false,
                                    activePlan.blocks().size(),
                                    "One or more preview chunks could not be scheduled"));
                        }
                    });
        }
    }

    public synchronized PreviewBounds bounds(UUID requestId) {
        RenderState state = requests.get(requestId);
        return state == null ? null : state.bounds();
    }

    public synchronized boolean contains(UUID requestId, int x, int y, int z) {
        RenderState state = requests.get(requestId);
        return state != null && state.bounds().contains(x, y, z);
    }

    public void removeRequest(UUID requestId) {
        if (requestId == null) {
            return;
        }
        RenderState removed;
        synchronized (this) {
            removed = requests.remove(requestId);
        }
        if (removed != null) {
            clear(removed.world(), removalPositions(removed));
        }
    }

    void forgetRequest(UUID requestId) {
        if (requestId == null) {
            return;
        }
        synchronized (this) {
            requests.remove(requestId);
        }
    }

    public void removeAll() {
        Map<UUID, RenderState> removed;
        synchronized (this) {
            removed = Map.copyOf(requests);
            requests.clear();
        }
        for (RenderState state : removed.values()) {
            clear(state.world(), removalPositions(state));
        }
    }

    private static void appendObject(
            Map<BlockPosition, String> blocks,
            PlacedStructurePiece piece,
            BlockRegistry registry
    ) throws IOException {
        ObjectTurn rotation = piece.getRotation();
        for (ObjectBlock block : piece.getObject().geometry().blocks()) {
            IrisBlockVector rotated = rotation.rotate(new IrisBlockVector(
                    block.position().x(), block.position().y(), block.position().z()));
            putState(
                    blocks,
                    new BlockPosition(
                            piece.getX() + rotated.getBlockX(),
                            piece.getY() + rotated.getBlockY(),
                            piece.getZ() + rotated.getBlockZ()),
                    JigsawStudioBlocks.rotated(registry, block.state(), rotation),
                    "object block");
        }
    }

    private static void appendFinalStates(
            Map<BlockPosition, String> blocks,
            PlacedStructurePiece piece,
            BlockRegistry registry
    ) throws IOException {
        ObjectTurn rotation = piece.getRotation();
        if (piece.getPiece().getConnectors() == null) {
            return;
        }
        for (IrisJigsawConnector connector : piece.getPiece().getConnectors()) {
            if (connector == null || connector.getPosition() == null) {
                throw new IOException("Jigsaw Studio preview contains an incomplete connector");
            }
            IrisBlockVector signed = new IrisBlockVector(
                    connector.getPosition().getX() - piece.getObject().center().x(),
                    connector.getPosition().getY() - piece.getObject().center().y(),
                    connector.getPosition().getZ() - piece.getObject().center().z());
            IrisBlockVector rotated = rotation.rotate(signed);
            BlockState source = registry.find(connector.getFinalState()).orElse(null);
            putState(
                    blocks,
                    new BlockPosition(
                            piece.getX() + rotated.getBlockX(),
                            piece.getY() + rotated.getBlockY(),
                            piece.getZ() + rotated.getBlockZ()),
                    source == null ? null : JigsawStudioBlocks.rotated(registry, source, rotation),
                    "connector final state");
        }
    }

    private static void putState(
            Map<BlockPosition, String> blocks,
            BlockPosition position,
            BlockState state,
            String source
    ) throws IOException {
        if (state == null || state.key() == null || state.key().isBlank()) {
            throw new IOException("Jigsaw Studio preview could not resolve a " + source);
        }
        String key = state.key();
        if (AIR.equals(key) || STRUCTURE_VOID.equals(key)) {
            blocks.remove(position);
            return;
        }
        blocks.put(position, key);
    }

    private void applyChunk(
            ToolWorld world,
            UUID requestId,
            long generation,
            List<BlockUpdate> updates,
            AtomicInteger remaining,
            AtomicBoolean failed,
            Consumer<RenderResult> completion
    ) {
        int changed = 0;
        List<BlockPosition> applied = new ArrayList<>(updates.size());
        try {
            if (!isCurrent(world, requestId, generation)) {
                return;
            }
            for (BlockUpdate update : updates) {
                world.setBlock(
                        new com.volmit.geometry.BlockPosition(update.position().x(), update.position().y(), update.position().z()),
                        BlockState.parse(update.stateKey()),
                        Optional.empty());
                applied.add(update.position());
                changed++;
            }
        } catch (RuntimeException exception) {
            failed.set(true);
            log.error("The Jigsaw Studio preview could not be rendered.", exception);
        } finally {
            synchronized (this) {
                RenderState active = requests.get(requestId);
                if (active != null
                        && active.world() == world
                        && active.generation() == generation) {
                    active.pending().removeAll(applied);
                }
            }
            if (remaining.decrementAndGet() == 0) {
                RenderState state;
                boolean pendingEmpty;
                synchronized (this) {
                    state = requests.get(requestId);
                    pendingEmpty = state != null && state.pending().isEmpty();
                }
                boolean current = state != null
                        && state.world() == world
                        && state.generation() == generation;
                completion.accept(new RenderResult(
                        current && pendingEmpty && !failed.get(),
                        current ? state.blocks().size() : changed,
                        failed.get() || current && !pendingEmpty
                                ? "One or more preview blocks could not be rendered"
                                : ""));
            }
        }
    }

    private synchronized boolean isCurrent(ToolWorld world, UUID requestId, long generation) {
        RenderState state = requests.get(requestId);
        return state != null && state.world() == world && state.generation() == generation;
    }

    private static Map<Long, List<BlockUpdate>> updates(
            Map<BlockPosition, String> previous,
            Map<BlockPosition, String> next,
            Set<BlockPosition> uncertain
    ) {
        Set<BlockPosition> positions = new HashSet<>(previous.keySet());
        positions.addAll(next.keySet());
        positions.addAll(uncertain);
        Map<Long, List<BlockUpdate>> updates = new HashMap<>();
        for (BlockPosition position : positions) {
            String nextState = next.getOrDefault(position, AIR);
            if (!requiresUpdate(position, previous, next, uncertain)) {
                continue;
            }
            updates.computeIfAbsent(
                    chunkKey(position.x() >> 4, position.z() >> 4),
                    ignored -> new ArrayList<>()).add(new BlockUpdate(position, nextState));
        }
        return updates;
    }

    static boolean requiresUpdate(
            BlockPosition position,
            Map<BlockPosition, String> previous,
            Map<BlockPosition, String> next,
            Set<BlockPosition> uncertain
    ) {
        return uncertain.contains(position)
                || !next.getOrDefault(position, AIR).equals(previous.get(position));
    }

    private void clear(ToolWorld world, Set<BlockPosition> positions) {
        Map<Long, List<BlockUpdate>> updates = new HashMap<>();
        for (BlockPosition position : positions) {
            updates.computeIfAbsent(
                    chunkKey(position.x() >> CHUNK_SHIFT, position.z() >> CHUNK_SHIFT),
                    ignored -> new ArrayList<>()).add(new BlockUpdate(position, AIR));
        }
        for (Map.Entry<Long, List<BlockUpdate>> chunk : updates.entrySet()) {
            JigsawStudioThreads.onRegion(log, world, chunkX(chunk.getKey()), chunkZ(chunk.getKey()), () -> {
                for (BlockUpdate update : chunk.getValue()) {
                    world.setBlock(
                            new com.volmit.geometry.BlockPosition(
                                    update.position().x(), update.position().y(), update.position().z()),
                            AIR_STATE,
                            Optional.empty());
                }
            }, () -> { });
        }
    }

    private static Set<BlockPosition> removalPositions(RenderState state) {
        Set<BlockPosition> positions = new HashSet<>(state.blocks().keySet());
        positions.addAll(state.pending());
        return Set.copyOf(positions);
    }

    private static long chunkKey(int chunkX, int chunkZ) {
        return ((long) chunkX << 32) ^ (chunkZ & 0xffffffffL);
    }

    private static int chunkX(long chunkKey) {
        return (int) (chunkKey >> 32);
    }

    private static int chunkZ(long chunkKey) {
        return (int) chunkKey;
    }

    public record PreviewPlan(Map<BlockPosition, String> blocks, PreviewBounds bounds) {
        public PreviewPlan {
            blocks = Map.copyOf(Objects.requireNonNull(blocks, "Jigsaw Studio preview blocks"));
            bounds = Objects.requireNonNull(bounds, "Jigsaw Studio preview bounds");
        }

        static PreviewPlan empty() {
            return new PreviewPlan(Map.of(), PreviewBounds.empty());
        }
    }

    public record PreviewBounds(
            int minimumX,
            int minimumY,
            int minimumZ,
            int maximumX,
            int maximumY,
            int maximumZ
    ) {
        static PreviewBounds empty() {
            return new PreviewBounds(0, 0, 0, -1, -1, -1);
        }

        public boolean isEmpty() {
            return maximumX < minimumX || maximumY < minimumY || maximumZ < minimumZ;
        }

        public int centerX() {
            return isEmpty() ? 0 : minimumX + (maximumX - minimumX) / 2;
        }

        public int centerZ() {
            return isEmpty() ? 0 : minimumZ + (maximumZ - minimumZ) / 2;
        }

        public boolean contains(int x, int y, int z) {
            return !isEmpty()
                    && x >= minimumX && x <= maximumX
                    && y >= minimumY && y <= maximumY
                    && z >= minimumZ && z <= maximumZ;
        }
    }

    public record BlockPosition(int x, int y, int z) {
    }

    public record RenderResult(boolean successful, int blockCount, String failure) {
        public RenderResult {
            failure = failure == null ? "" : failure;
        }
    }

    private record RenderState(
            ToolWorld world,
            long generation,
            Map<BlockPosition, String> blocks,
            PreviewBounds bounds,
            Set<BlockPosition> pending
    ) {
        private RenderState {
            Objects.requireNonNull(world, "Jigsaw Studio preview world");
            blocks = Map.copyOf(Objects.requireNonNull(blocks, "Jigsaw Studio preview state blocks"));
            bounds = Objects.requireNonNull(bounds, "Jigsaw Studio preview state bounds");
            pending = Objects.requireNonNull(pending, "Jigsaw Studio preview pending blocks");
        }
    }

    private record BlockUpdate(BlockPosition position, String stateKey) {
    }
}
