package art.arcane.iris.probe;

import ca.spottedleaf.moonrise.patches.starlight.chunk.StarlightChunk;
import ca.spottedleaf.moonrise.patches.starlight.light.SWMRNibbleArray;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.DataLayer;
import net.minecraft.world.level.chunk.LightChunk;
import net.minecraft.world.level.chunk.LightChunkGetter;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.lighting.BlockLightEngine;
import net.minecraft.world.level.lighting.ChunkSkyLightSources;
import net.minecraft.world.level.lighting.LayerLightEventListener;
import net.minecraft.world.level.lighting.SkyLightEngine;
import net.minecraft.world.level.material.FluidState;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.BiConsumer;

final class HeadlessChunkLighting {
    private static final int MAXIMUM_WINDOW_CHUNKS = 256;
    private static final int HALO = 2;

    private HeadlessChunkLighting() {
    }

    static void light(Request request) {
        if (Thread.currentThread().isInterrupted()) {
            throw new IllegalStateException("Offline lighting interrupted before initialization");
        }
        Window window = new Window(request);
        BlockLightEngine block = new BlockLightEngine(window);
        SkyLightEngine sky = request.skyLight() ? new SkyLightEngine(window) : null;
        for (LightingChunk chunk : window.chunks.values()) {
            ProtoChunk source = chunk.source;
            for (int section = source.getMinSectionY(); section <= source.getMaxSectionY(); section++) {
                SectionPos position = SectionPos.of(source.getPos(), section);
                boolean empty = source.getSection(source.getSectionIndexFromSectionY(section)).hasOnlyAir();
                block.updateSectionStatus(position, empty);
                if (sky != null) {
                    sky.updateSectionStatus(position, empty);
                }
            }
        }
        for (LightingChunk chunk : window.chunks.values()) {
            block.propagateLightSources(chunk.source.getPos());
            if (sky != null) {
                sky.propagateLightSources(chunk.source.getPos());
            }
        }
        drain(block);
        if (sky != null) {
            drain(sky);
        }
        for (ChunkPos position : request.targets()) {
            ProtoChunk chunk = window.chunks.get(position.pack()).source;
            StarlightChunk destination = (StarlightChunk) chunk;
            destination.starlight$setBlockNibbles(copyLight(block, chunk));
            destination.starlight$setSkyNibbles(copyLight(sky, chunk));
            boolean[] emptySections = new boolean[chunk.getSectionsCount()];
            for (int index = 0; index < emptySections.length; index++) {
                emptySections[index] = chunk.getSection(index).hasOnlyAir();
            }
            destination.starlight$setBlockEmptinessMap(emptySections.clone());
            destination.starlight$setSkyEmptinessMap(emptySections);
            chunk.setPersistedStatus(ChunkStatus.LIGHT);
            chunk.setLightCorrect(true);
        }
    }

    private static void drain(LayerLightEventListener engine) {
        while (engine.hasLightWork()) {
            if (Thread.currentThread().isInterrupted()) {
                throw new IllegalStateException("Offline lighting interrupted before completion");
            }
            engine.runLightUpdates();
        }
    }

    private static SWMRNibbleArray[] copyLight(LayerLightEventListener engine, ProtoChunk chunk) {
        SWMRNibbleArray[] result = new SWMRNibbleArray[chunk.getSectionsCount() + 2];
        BlockPos.MutableBlockPos position = new BlockPos.MutableBlockPos();
        for (int index = 0; index < result.length; index++) {
            int sectionY = chunk.getMinSectionY() + index - 1;
            DataLayer layer = engine == null ? new DataLayer() : engine.getDataLayerData(SectionPos.of(chunk.getPos(), sectionY));
            if (layer == null) {
                layer = new DataLayer();
                for (int y = 0; y < 16; y++) {
                    for (int z = 0; z < 16; z++) {
                        for (int x = 0; x < 16; x++) {
                            position.set(chunk.getPos().getMinBlockX() + x, (sectionY << 4) + y,
                                    chunk.getPos().getMinBlockZ() + z);
                            layer.set(x, y, z, engine.getLightValue(position));
                        }
                    }
                }
            }
            result[index] = SWMRNibbleArray.fromVanilla(layer);
        }
        return result;
    }

    record Request(List<ProtoChunk> chunks, Set<ChunkPos> targets, boolean skyLight) {
        Request {
            chunks = List.copyOf(chunks);
            targets = Set.copyOf(targets);
            if (chunks.isEmpty() || chunks.size() > MAXIMUM_WINDOW_CHUNKS || targets.isEmpty()) {
                throw new IllegalArgumentException("Offline lighting requires targets inside a bounded chunk window");
            }
        }
    }

    private static final class Window implements LightChunkGetter, BlockGetter {
        private final Map<Long, LightingChunk> chunks;
        private final int minimumY;
        private final int height;

        private Window(Request request) {
            minimumY = request.chunks().getFirst().getMinY();
            height = request.chunks().getFirst().getHeight();
            chunks = new HashMap<>(request.chunks().size());
            for (ProtoChunk chunk : request.chunks()) {
                if (chunk.getMinY() != minimumY || chunk.getHeight() != height
                        || chunk.getPersistedStatus() != ChunkStatus.FEATURES) {
                    throw new IllegalArgumentException("Offline lighting requires matching heights and completed FEATURES chunks");
                }
                if (chunks.putIfAbsent(chunk.getPos().pack(), new LightingChunk(chunk)) != null) {
                    throw new IllegalArgumentException("Duplicate lighting chunk: " + chunk.getPos());
                }
            }
            for (ChunkPos target : request.targets()) {
                for (int dz = -HALO; dz <= HALO; dz++) {
                    for (int dx = -HALO; dx <= HALO; dx++) {
                        if (!chunks.containsKey(ChunkPos.pack(Math.addExact(target.x(), dx), Math.addExact(target.z(), dz)))) {
                            throw new IllegalArgumentException("Offline lighting target lacks its completed neighbor halo: " + target);
                        }
                    }
                }
            }
        }

        @Override
        public LightChunk getChunkForLighting(int x, int z) {
            return chunks.get(ChunkPos.pack(x, z));
        }

        @Override
        public BlockGetter getLevel() {
            return this;
        }

        @Override
        public int getMinY() {
            return minimumY;
        }

        @Override
        public int getHeight() {
            return height;
        }

        @Override
        public BlockEntity getBlockEntity(BlockPos position) {
            return requireChunk(position).getBlockEntity(position);
        }

        @Override
        public BlockState getBlockState(BlockPos position) {
            return requireChunk(position).getBlockState(position);
        }

        @Override
        public BlockState getBlockStateIfLoaded(BlockPos position) {
            LightingChunk chunk = chunks.get(ChunkPos.pack(position.getX() >> 4, position.getZ() >> 4));
            return chunk == null ? null : chunk.getBlockState(position);
        }

        @Override
        public FluidState getFluidState(BlockPos position) {
            return getBlockState(position).getFluidState();
        }

        @Override
        public FluidState getFluidIfLoaded(BlockPos position) {
            BlockState state = getBlockStateIfLoaded(position);
            return state == null ? null : state.getFluidState();
        }

        private LightingChunk requireChunk(BlockPos position) {
            return Objects.requireNonNull(chunks.get(ChunkPos.pack(position.getX() >> 4, position.getZ() >> 4)),
                    "Lighting query outside the supplied chunk window");
        }
    }

    private static final class LightingChunk implements LightChunk {
        private final ProtoChunk source;
        private final ChunkSkyLightSources skySources;

        private LightingChunk(ProtoChunk source) {
            this.source = source;
            skySources = new ChunkSkyLightSources(source);
            skySources.fillFrom(source);
        }

        @Override
        public void findBlockLightSources(BiConsumer<BlockPos, BlockState> consumer) {
            source.findBlockLightSources(consumer);
        }

        @Override
        public ChunkSkyLightSources getSkyLightSources() {
            return skySources;
        }

        @Override
        public BlockEntity getBlockEntity(BlockPos position) {
            return source.getBlockEntity(position);
        }

        @Override
        public BlockState getBlockState(BlockPos position) {
            return source.getBlockState(position);
        }

        @Override
        public BlockState getBlockStateIfLoaded(BlockPos position) {
            return source.getBlockStateIfLoaded(position);
        }

        @Override
        public FluidState getFluidState(BlockPos position) {
            return source.getFluidState(position);
        }

        @Override
        public FluidState getFluidIfLoaded(BlockPos position) {
            return source.getFluidIfLoaded(position);
        }

        @Override
        public int getMinY() {
            return source.getMinY();
        }

        @Override
        public int getHeight() {
            return source.getHeight();
        }
    }
}
