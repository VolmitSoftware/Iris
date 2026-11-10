/*
 * Iris is a World Generator for Minecraft Servers
 * Copyright (c) 2026 Arcane Arts (Volmit Software)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package art.arcane.iris.core.modules;

import art.arcane.iris.engine.framework.Engine;
import art.arcane.iris.engine.framework.NativeStartMemo;
import art.arcane.iris.engine.framework.nativefit.NativeFitBlocks;
import art.arcane.iris.engine.framework.nativefit.NativeFitBox;
import art.arcane.iris.engine.framework.nativefit.NativeFitChunk;
import art.arcane.iris.engine.framework.nativefit.NativeFitPiece;
import art.arcane.iris.engine.framework.nativefit.NativeFitStart;
import art.arcane.iris.engine.framework.nativefit.NativeFitTrait;
import art.arcane.iris.engine.framework.nativefit.NativeStructureFitting;
import art.arcane.iris.engine.framework.nativefit.NativeTemplateCells;
import com.volmit.block.BlockState;
import com.volmit.block.BlockTrait;
import com.volmit.host.DiscoveredModule;
import com.volmit.host.ModuleRegistry;
import com.volmit.host.ModuleSetups;
import com.volmit.host.ServiceRegistration;
import com.volmit.host.StageGraphBuilder;
import com.volmit.geometry.BlockBox;
import com.volmit.geometry.BlockFace;
import com.volmit.geometry.BlockPosition;
import com.volmit.resource.ResourceId;
import com.volmit.service.VanillaStructures;
import com.volmit.vanilla.DecorationStep;
import com.volmit.vanilla.PieceKind;
import com.volmit.vanilla.StartKey;
import com.volmit.vanilla.StartMemo;
import com.volmit.vanilla.StructureFit;
import com.volmit.vanilla.TemplateCells;
import com.volmit.vanilla.TerrainAdaptation;
import com.volmit.ModuleLog;
import com.volmit.world.BlockAccess;
import com.volmit.world.PackWorld;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * The pack's {@link VanillaStructures} providers fitting the platform's starts into a chunk: the platform code's view
 * of the chunk ({@link NativeFitChunk}) as an API {@link StructureFit}, its native block states translated to API
 * states and back through caches, so a provider can read and write blocks without allocating per block.
 */
public final class ModuleStructureFitting implements NativeStructureFitting {
    /** One key per platform start for as long as the platform keeps the start, so a start fitted in many chunks is known again. */
    private static final Map<Object, StartKey> START_KEYS = Collections.synchronizedMap(new WeakHashMap<>());
    private static final Map<Object, BlockState> API_STATES = new ConcurrentHashMap<>();
    private static final Map<BlockState, Object> NATIVE_STATES = new ConcurrentHashMap<>();
    private static final Map<Object, Integer> NATIVE_TRAITS = new ConcurrentHashMap<>();
    private static final Map<BlockTrait, NativeFitTrait> TRAITS = new EnumMap<>(Map.of(
            BlockTrait.AIR, NativeFitTrait.AIR, BlockTrait.SOLID, NativeFitTrait.SOLID,
            BlockTrait.FLUID, NativeFitTrait.FLUID, BlockTrait.LOG, NativeFitTrait.LOG,
            BlockTrait.LEAVES, NativeFitTrait.LEAVES));

    private final List<ServiceRegistration> providers;
    private final Supplier<PackWorld> world;

    private ModuleStructureFitting(List<ServiceRegistration> providers, Supplier<PackWorld> world) {
        this.providers = providers;
        this.world = world;
    }

    public static NativeStructureFitting of(Engine engine) {
        return new ModuleStructureFitting(ModuleVanillaStructures.providersOf(engine.getData()),
                () -> engine.getGeneration().world());
    }

    /** The built-in modules' registrations only: their stage graph needs the modules of the built-ins jar too. */
    public static NativeStructureFitting builtInModules() {
        ModuleRegistry registry = new ModuleRegistry();
        IrisModuleHost.builtInModules().forEach(module -> ModuleSetups.run(DiscoveredModule.builtIn(module), new StageGraphBuilder(), registry));
        FitlessWorld fitless = new FitlessWorld();
        return new ModuleStructureFitting(registry.freeze().registrations(VanillaStructures.class), () -> fitless);
    }

    @Override
    public <S> Session beforePieces(NativeFitChunk<S> chunk) {
        List<ChunkFit<S>> fits = providers.stream()
                .<ChunkFit<S>>map(registration -> new ChunkFit<>(chunk, () -> registration.scope(world.get()), registration.module().log()))
                .toList();
        for (int index = 0; index < providers.size(); index++) {
            ChunkFit<S> fit = fits.get(index);
            ModuleVanillaStructures.ask(providers.get(index), "fitting structures into chunk " + fit.chunkName(), provider -> {
                provider.beforePieces(fit);
                return Boolean.TRUE;
            });
        }
        return startIndex -> {
            for (int index = 0; index < providers.size(); index++) {
                ChunkFit<S> fit = fits.get(index);
                StructureFit.Start placed = fit.starts().get(startIndex);
                ModuleVanillaStructures.ask(providers.get(index), "fitting " + placed.structure() + " into chunk " + fit.chunkName(),
                        provider -> {
                            provider.afterPieces(fit, placed);
                            return Boolean.TRUE;
                        });
            }
        };
    }

    @SuppressWarnings("unchecked")
    private static <S> S nativeState(NativeFitBlocks<S> blocks, BlockState state) {
        return (S) NATIVE_STATES.computeIfAbsent(state, key -> blocks.state(key.key()));
    }

    private static <S> BlockState apiState(NativeFitBlocks<S> blocks, S state) {
        BlockState known = API_STATES.get(state);
        if (known != null) {
            return known;
        }
        return API_STATES.computeIfAbsent(state, key -> BlockState.parse(PlatformBlockStates.namespaced(blocks.key(state))));
    }

    private static <S> boolean nativeTrait(NativeFitBlocks<S> blocks, S state, NativeFitTrait trait) {
        int traits = NATIVE_TRAITS.computeIfAbsent(state, key -> {
            int bits = 0;
            for (NativeFitTrait each : NativeFitTrait.values()) {
                if (blocks.is(state, each)) {
                    bits |= 1 << each.ordinal();
                }
            }
            return bits;
        });
        return (traits & 1 << trait.ordinal()) != 0;
    }

    /** One chunk's fit in the API's terms: heights above the dimension's bottom, maxima exclusive. */
    private static final class ChunkFit<S> implements StructureFit {
        private final NativeFitChunk<S> chunk;
        private final Supplier<PackWorld> world;
        private final ModuleLog log;
        private final Access<S> blocks;
        private final List<StructureFit.Start> starts;
        private final Map<Class<?>, Object> states = new HashMap<>();

        private ChunkFit(NativeFitChunk<S> chunk, Supplier<PackWorld> world, ModuleLog log) {
            this.chunk = chunk;
            this.world = world;
            this.log = log;
            this.blocks = new Access<>(chunk);
            List<StructureFit.Start> converted = new ArrayList<>(chunk.starts().size());
            for (NativeFitStart<S> start : chunk.starts()) {
                converted.add(new HostStart<>(start, chunk));
            }
            this.starts = List.copyOf(converted);
        }

        String chunkName() {
            return (chunk.area().minX() >> 4) + "," + (chunk.area().minZ() >> 4);
        }

        @Override
        public PackWorld world() {
            return world.get();
        }

        @Override
        public ModuleLog log() {
            return log;
        }

        @Override
        public long seed() {
            return chunk.seed();
        }

        @Override
        public BlockBox area() {
            return apiBox(chunk.area(), chunk.bottomY());
        }

        @Override
        public int minWorldY() {
            return chunk.bottomY();
        }

        @Override
        public BlockAccess blocks() {
            return blocks;
        }

        @Override
        public int surfaceY(int x, int z) {
            return chunk.surfaceY().applyAsInt(x, z) - chunk.bottomY();
        }

        @Override
        public BlockState naturalBlock(int y) {
            return apiState(chunk.blocks(), chunk.naturalBlock().apply(y + chunk.bottomY()));
        }

        @Override
        public List<StructureFit.Start> starts() {
            return starts;
        }

        @Override
        public <T> T state(Class<T> type, Supplier<? extends T> create) {
            Object known = states.get(type);
            if (known == null) {
                known = create.get();
                states.put(type, known);
            }
            return type.cast(known);
        }
    }

    /** The chunk column's blocks: the area's columns from the dimension's bottom to its top. */
    private static final class Access<S> implements BlockAccess {
        private final NativeFitBlocks<S> blocks;
        private final int bottomY;
        private final BlockBox bounds;
        private final Map<BlockState, S> written = new IdentityHashMap<>();

        private Access(NativeFitChunk<S> chunk) {
            this.blocks = chunk.blocks();
            this.bottomY = chunk.bottomY();
            NativeFitBox area = chunk.area();
            this.bounds = new BlockBox(area.minX(), 0, area.minZ(), area.maxX() + 1, chunk.topY() - bottomY, area.maxZ() + 1);
        }

        @Override
        public BlockBox bounds() {
            return bounds;
        }

        @Override
        public BlockState get(int x, int y, int z) {
            requireInside(x, y, z);
            return apiState(blocks, blocks.get(x, y + bottomY, z));
        }

        @Override
        public void column(int x, int z, int fromY, BlockState[] into) {
            for (int index = 0; index < into.length; index++) {
                into[index] = get(x, fromY + index, z);
            }
        }

        @Override
        public void set(int x, int y, int z, BlockState state) {
            requireInside(x, y, z);
            blocks.set(x, y + bottomY, z, nativeOf(state));
        }

        @Override
        public boolean is(BlockState state, BlockTrait trait) {
            NativeFitTrait nativeTrait = TRAITS.get(trait);
            if (nativeTrait == null) {
                throw new UnsupportedOperationException("A structure fit answers only " + TRAITS.keySet() + ", not " + trait);
            }
            return nativeTrait(blocks, nativeOf(state), nativeTrait);
        }

        @Override
        public boolean inTag(BlockState state, ResourceId tag) {
            return blocks.inTag(nativeOf(state), tag.toString());
        }

        @Override
        public boolean sturdy(int x, int y, int z, BlockFace face) {
            requireInside(x, y, z);
            return blocks.sturdy(x, y + bottomY, z, face.name());
        }

        @Override
        public BlockState fluid(BlockState state) {
            return apiState(blocks, blocks.fluid(nativeOf(state)));
        }

        private S nativeOf(BlockState state) {
            S known = written.get(state);
            if (known == null) {
                known = nativeState(blocks, state);
                written.put(state, known);
            }
            return known;
        }

        private void requireInside(int x, int y, int z) {
            if (!bounds.contains(x, y, z)) {
                throw new IndexOutOfBoundsException("Block " + x + ", " + y + ", " + z + " is outside the fitted chunk " + bounds);
            }
        }
    }

    private static final class HostStart<S> implements StructureFit.Start {
        private final NativeFitStart<S> start;
        private final ResourceId structure;
        private final List<StructureFit.Piece> pieces;

        private HostStart(NativeFitStart<S> start, NativeFitChunk<S> chunk) {
            this.start = start;
            this.structure = ResourceId.parse(start.structureKey());
            List<StructureFit.Piece> converted = new ArrayList<>(start.pieces().size());
            for (NativeFitPiece<S> piece : start.pieces()) {
                converted.add(new HostPiece<>(piece, chunk));
            }
            this.pieces = List.copyOf(converted);
        }

        @Override
        public ResourceId structure() {
            return structure;
        }

        @Override
        public int chunkX() {
            return start.chunkX();
        }

        @Override
        public int chunkZ() {
            return start.chunkZ();
        }

        @Override
        public DecorationStep step() {
            return DecorationStep.valueOf(start.step());
        }

        @Override
        public TerrainAdaptation adaptation() {
            return TerrainAdaptation.valueOf(start.adaptation());
        }

        @Override
        public Optional<StartMemo> memo() {
            NativeStartMemo memo = start.memo();
            return memo == null ? Optional.empty() : Optional.of(new StartMemo(memo.text()));
        }

        @Override
        public List<StructureFit.Piece> pieces() {
            return pieces;
        }

        @Override
        public StartKey key() {
            return START_KEYS.computeIfAbsent(start.key(), ignored -> StartKey.create());
        }
    }

    private static final class HostPiece<S> implements StructureFit.Piece {
        private final NativeFitPiece<S> piece;
        private final NativeFitChunk<S> chunk;
        private final int bottomY;

        private HostPiece(NativeFitPiece<S> piece, NativeFitChunk<S> chunk) {
            this.piece = piece;
            this.chunk = chunk;
            this.bottomY = chunk.bottomY();
        }

        @Override
        public BlockBox box() {
            return apiBox(piece.box(), bottomY);
        }

        @Override
        public PieceKind kind() {
            return PieceKind.valueOf(piece.kind());
        }

        @Override
        public int groundLevelDelta() {
            return piece.groundLevelDelta();
        }

        @Override
        public List<BlockPosition> junctions() {
            List<BlockPosition> junctions = new ArrayList<>(piece.junctions().size());
            for (int[] junction : piece.junctions()) {
                junctions.add(new BlockPosition(junction[0], junction[1] - bottomY, junction[2]));
            }
            return junctions;
        }

        @Override
        public TemplateCells cells(BlockBox region) {
            NativeTemplateCells<S> cells = piece.cells(nativeBox(region, bottomY));
            if (!cells.resolved()) {
                return TemplateCells.UNRESOLVED;
            }
            int size = cells.states().length;
            int[] ys = new int[size];
            BlockState[] states = new BlockState[size];
            for (int cell = 0; cell < size; cell++) {
                ys[cell] = cells.ys()[cell] - bottomY;
                S state = cells.states()[cell];
                states[cell] = state == null ? null : apiState(chunk.blocks(), state);
            }
            return TemplateCells.of(cells.xs(), ys, cells.zs(), states, cells.readBlocks());
        }

        @Override
        public void columns(StructureFit.ColumnVisitor visitor) {
            piece.columns((x, z, minY, maxY) -> visitor.column(x, z, minY - bottomY, maxY - bottomY));
        }

        @Override
        public List<BlockPosition> legacyAir(BlockBox clip) {
            List<int[]> air = piece.legacyAir(nativeBox(clip, bottomY));
            List<BlockPosition> positions = new ArrayList<>(air.size());
            for (int[] position : air) {
                positions.add(new BlockPosition(position[0], position[1] - bottomY, position[2]));
            }
            return positions;
        }
    }

    private static BlockBox apiBox(NativeFitBox box, int bottomY) {
        return new BlockBox(box.minX(), box.minY() - bottomY, box.minZ(), box.maxX() + 1, box.maxY() + 1 - bottomY,
                box.maxZ() + 1);
    }

    private static NativeFitBox nativeBox(BlockBox box, int bottomY) {
        return new NativeFitBox(box.minX(), box.minY() + bottomY, box.minZ(), box.maxX() - 1, box.maxY() - 1 + bottomY,
                box.maxZ() - 1);
    }
}
