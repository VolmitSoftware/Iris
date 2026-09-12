package art.arcane.iris.studio.jigsaw;

import art.arcane.iris.structure.placement.PlacedStructurePiece;
import art.arcane.iris.structure.jigsaw.IrisJigsawConnector;
import art.arcane.iris.structure.object.IrisObject;
import art.arcane.iris.structure.object.IrisObjectRotation;
import art.arcane.iris.spi.IrisLogging;
import art.arcane.iris.spi.PlatformBlockState;
import art.arcane.iris.generation.block.B;
import art.arcane.iris.generation.geometry.IrisBlockVector;
import art.arcane.iris.world.task.J;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

public final class JigsawStudioPreviewRenderer {
    private static final int MAX_BLOCKS = 250_000;
    private static final String AIR = "minecraft:air";
    private static final String STRUCTURE_VOID = "minecraft:structure_void";

    private final Map<UUID, RenderState> requests = new HashMap<>();

    public static PreviewPlan plan(List<PlacedStructurePiece> pieces) throws IOException {
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
            appendObject(blocks, piece);
            appendFinalStates(blocks, piece);
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
            World world,
            UUID requestId,
            long generation,
            PreviewPlan plan,
            Consumer<RenderResult> completion
    ) {
        World activeWorld = Objects.requireNonNull(world, "Jigsaw Studio preview world");
        UUID activeRequestId = Objects.requireNonNull(requestId, "Jigsaw Studio preview request ID");
        PreviewPlan activePlan = Objects.requireNonNull(plan, "Jigsaw Studio preview plan");
        Consumer<RenderResult> callback = Objects.requireNonNull(completion, "Jigsaw Studio preview callback");
        Map<BlockPosition, String> previous;
        Set<BlockPosition> uncertain;
        Map<Long, List<BlockUpdate>> updates;
        synchronized (this) {
            RenderState old = requests.get(activeRequestId);
            previous = old == null || !old.worldId().equals(activeWorld.getUID())
                    ? Map.of()
                    : old.blocks();
            uncertain = old == null || !old.worldId().equals(activeWorld.getUID())
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
                    activeWorld.getUID(), generation, activePlan.blocks(), activePlan.bounds(), pending));
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
            boolean scheduled = J.runRegion(
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
                            callback));
            if (!scheduled) {
                failed.set(true);
                if (remaining.decrementAndGet() == 0) {
                    callback.accept(new RenderResult(
                            false,
                            activePlan.blocks().size(),
                            "One or more preview chunks could not be scheduled"));
                }
            }
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
            World world = Bukkit.getWorld(removed.worldId());
            if (world != null) {
                clear(world, removalPositions(removed));
            }
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
            World world = Bukkit.getWorld(state.worldId());
            if (world != null) {
                clear(world, removalPositions(state));
            }
        }
    }

    private static void appendObject(
            Map<BlockPosition, String> blocks,
            PlacedStructurePiece piece
    ) throws IOException {
        IrisObject object = piece.getObject();
        IrisObjectRotation rotation = piece.getRotation();
        for (Map.Entry<IrisBlockVector, PlatformBlockState> entry : object.getBlocks()) {
            IrisBlockVector rotated = rotation.rotate(entry.getKey());
            PlatformBlockState state = rotation.rotate(entry.getValue(), 0, 0, 0);
            putState(
                    blocks,
                    new BlockPosition(
                            piece.getX() + rotated.getBlockX(),
                            piece.getY() + rotated.getBlockY(),
                            piece.getZ() + rotated.getBlockZ()),
                    state,
                    "object block");
        }
    }

    private static void appendFinalStates(
            Map<BlockPosition, String> blocks,
            PlacedStructurePiece piece
    ) throws IOException {
        IrisObject object = piece.getObject();
        IrisObjectRotation rotation = piece.getRotation();
        if (piece.getPiece().getConnectors() == null) {
            return;
        }
        for (IrisJigsawConnector connector : piece.getPiece().getConnectors()) {
            if (connector == null || connector.getPosition() == null) {
                throw new IOException("Jigsaw Studio preview contains an incomplete connector");
            }
            IrisBlockVector signed = new IrisBlockVector(
                    connector.getPosition().getX() - object.getCenter().getBlockX(),
                    connector.getPosition().getY() - object.getCenter().getBlockY(),
                    connector.getPosition().getZ() - object.getCenter().getBlockZ());
            IrisBlockVector rotated = rotation.rotate(signed);
            PlatformBlockState source = B.getStateOrNull(connector.getFinalState(), false);
            PlatformBlockState state = source == null ? null : rotation.rotate(source, 0, 0, 0);
            putState(
                    blocks,
                    new BlockPosition(
                            piece.getX() + rotated.getBlockX(),
                            piece.getY() + rotated.getBlockY(),
                            piece.getZ() + rotated.getBlockZ()),
                    state,
                    "connector final state");
        }
    }

    private static void putState(
            Map<BlockPosition, String> blocks,
            BlockPosition position,
            PlatformBlockState state,
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
            World world,
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
            if (!isCurrent(world.getUID(), requestId, generation)) {
                return;
            }
            for (BlockUpdate update : updates) {
                Block block = world.getBlockAt(update.position().x(), update.position().y(), update.position().z());
                BlockData data = Bukkit.createBlockData(update.stateKey());
                block.setBlockData(data, false);
                applied.add(update.position());
                changed++;
            }
        } catch (RuntimeException exception) {
            failed.set(true);
            IrisLogging.reportError(exception);
        } finally {
            synchronized (this) {
                RenderState active = requests.get(requestId);
                if (active != null
                        && active.worldId().equals(world.getUID())
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
                        && state.worldId().equals(world.getUID())
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

    private synchronized boolean isCurrent(UUID worldId, UUID requestId, long generation) {
        RenderState state = requests.get(requestId);
        return state != null && state.worldId().equals(worldId) && state.generation() == generation;
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

    private static void clear(World world, Set<BlockPosition> positions) {
        Map<Long, List<BlockUpdate>> updates = new HashMap<>();
        for (BlockPosition position : positions) {
            updates.computeIfAbsent(
                    chunkKey(position.x() >> 4, position.z() >> 4),
                    ignored -> new ArrayList<>()).add(new BlockUpdate(position, AIR));
        }
        for (Map.Entry<Long, List<BlockUpdate>> chunk : updates.entrySet()) {
            J.runRegion(world, chunkX(chunk.getKey()), chunkZ(chunk.getKey()), () -> {
                for (BlockUpdate update : chunk.getValue()) {
                    world.getBlockAt(
                                    update.position().x(),
                                    update.position().y(),
                                    update.position().z())
                            .setType(Material.AIR, false);
                }
            });
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
            UUID worldId,
            long generation,
            Map<BlockPosition, String> blocks,
            PreviewBounds bounds,
            Set<BlockPosition> pending
    ) {
        private RenderState {
            Objects.requireNonNull(worldId, "Jigsaw Studio preview world ID");
            blocks = Map.copyOf(Objects.requireNonNull(blocks, "Jigsaw Studio preview state blocks"));
            bounds = Objects.requireNonNull(bounds, "Jigsaw Studio preview state bounds");
            pending = Objects.requireNonNull(pending, "Jigsaw Studio preview pending blocks");
        }
    }

    private record BlockUpdate(BlockPosition position, String stateKey) {
    }
}
