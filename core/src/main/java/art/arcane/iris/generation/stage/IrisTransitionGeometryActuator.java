package art.arcane.iris.generation.stage;

import art.arcane.iris.generation.chunk.ColumnReadHunk;
import art.arcane.iris.generation.runtime.Engine;
import art.arcane.iris.generation.runtime.EngineAssignedComponent;
import art.arcane.iris.generation.runtime.EngineStage;
import art.arcane.iris.generation.runtime.IrisComplex;
import art.arcane.iris.generation.terrain.Terrain3DColumn;
import art.arcane.iris.world.history.BoundaryColumnGeometry;
import art.arcane.iris.world.history.BoundaryGeometryInfluence;
import art.arcane.iris.world.history.SavedTerrainChunk;
import art.arcane.iris.world.history.TransitionGenerationPlan;
import art.arcane.iris.world.history.TransitionGeometryBlender;
import art.arcane.iris.spi.IrisPlatforms;
import art.arcane.volmlib.nativelib.terrain.NativeBiome;
import art.arcane.volmlib.nativelib.terrain.NativeBlockState;
import art.arcane.iris.spi.PlatformRegistries;
import art.arcane.iris.generation.context.ChunkContext;
import art.arcane.volmlib.util.hunk.Hunk;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;

public final class IrisTransitionGeometryActuator extends EngineAssignedComponent implements EngineStage {
    public IrisTransitionGeometryActuator(Engine engine) {
        super(engine, "Terrain Transition");
    }

    @Override
    public void close() {
        super.close();
    }

    @Override
    public void generate(int x, int z, Hunk<NativeBlockState> blocks, Hunk<NativeBiome> biomes,
                         boolean multicore, ChunkContext context) {
        TransitionGenerationPlan plan = context.getComplex().getTransitionGenerationPlan();
        if (plan == null || !plan.hasTransitionAtChunk(x >> 4, z >> 4)) {
            return;
        }
        GeometrySource source = new GeometrySource(x, z, blocks, biomes, getEngine().getMinHeight(), context);
        blendGeometry(x, z, blocks, context, source, plan);
    }

    public static SavedTerrainChunk capture(int x, int z, Hunk<NativeBlockState> blocks,
                                             Hunk<NativeBiome> biomes, int minimumY,
                                             ChunkContext context, boolean boundaryOnly) throws IOException {
        GeometrySource source = new GeometrySource(x, z, blocks, biomes, minimumY, context);
        return boundaryOnly
                ? SavedTerrainChunk.captureBoundary(x >> 4, z >> 4, minimumY, blocks.getHeight(), "minecraft:noise", source)
                : SavedTerrainChunk.capture(x >> 4, z >> 4, minimumY, blocks.getHeight(), "minecraft:noise", source);
    }

    private void blendGeometry(int x, int z, Hunk<NativeBlockState> blocks, ChunkContext context,
                               GeometrySource source, TransitionGenerationPlan plan) {
        PlatformRegistries registries = IrisPlatforms.get().registries();
        Map<String, NativeBlockState> resolved = new HashMap<>();
        IrisComplex complex = context.getComplex();
        for (int localX = 0; localX < blocks.getWidth(); localX++) {
            for (int localZ = 0; localZ < blocks.getDepth(); localZ++) {
                BoundaryGeometryInfluence influence = plan.geometryAt(x + localX, z + localZ);
                if (influence.newTerrainWeight() == 1D || influence.contributions().isEmpty()
                        || complex.hydrologyOwnsTerrain(x + localX, z + localZ)) {
                    continue;
                }
                BoundaryColumnGeometry current = source.column(localX, localZ);
                BoundaryColumnGeometry blended = TransitionGeometryBlender.blendColumn(
                        influence, x + localX, z + localZ, current);
                if (blended == current) {
                    continue;
                }
                int ground = context.getRoundedHeight(localX, localZ);
                BoundaryColumnGeometry.WaterCap cap = paintedCap(current, blended, ground,
                        waterHead(complex, x + localX, z + localZ, current.height()));
                int floor = blended.surfaceOffsetNear(ground, cap);
                List<BoundaryColumnGeometry.Voxel> voxels = blended.voxels();
                for (int offset = 0; offset < voxels.size(); offset++) {
                    BoundaryColumnGeometry.Voxel voxel = voxels.get(offset);
                    NativeBlockState existing = blocks.getRaw(localX, offset, localZ);
                    if (existing != null && existing.key().equals(voxel.stateKey())) {
                        continue;
                    }
                    NativeBlockState replacement = resolved.computeIfAbsent(voxel.stateKey(), registries::blockOrNull);
                    if (replacement == null) {
                        throw new IllegalStateException("Terrain boundary requires unavailable block state " + voxel.stateKey());
                    }
                    blocks.setRaw(localX, offset, localZ, replacement);
                }
                context.setTerrainHeight(localX, localZ, floor);
                int fluidHeight = floor;
                while (fluidHeight + 1 < voxels.size()
                        && voxels.get(fluidHeight + 1).phase() == BoundaryColumnGeometry.Phase.FLUID) {
                    fluidHeight++;
                }
                if (blended.capsWater(cap, floor)) {
                    fluidHeight = cap.top();
                }
                if (fluidHeight == floor) {
                    fluidHeight = -1;
                }
                context.setTransitionTerrain(localX, localZ, Terrain3DColumn.fromOccupancy(
                        floor, voxels.size(), offset -> voxels.get(offset).phase() == BoundaryColumnGeometry.Phase.SOLID),
                        fluidHeight);
            }
        }
    }

    private static BoundaryColumnGeometry.WaterCap paintedCap(BoundaryColumnGeometry painted, BoundaryColumnGeometry blended,
                                                              int ground, int waterHead) {
        BoundaryColumnGeometry.WaterCap cap = painted.waterCap(ground, waterHead);
        if (!cap.present()) {
            return cap;
        }
        int top = Math.min(cap.top() + 1, painted.height() - 1);
        for (int offset = cap.bottom(); offset <= top; offset++) {
            int y = painted.minimumY() + offset;
            if (!painted.voxelAt(y).equals(blended.voxelAt(y))) {
                return BoundaryColumnGeometry.WaterCap.none();
            }
        }
        return cap;
    }

    private static int waterHead(IrisComplex complex, int blockX, int blockZ, int height) {
        return Math.clamp(Math.round(complex.getWaterHeadStream().getDouble(blockX, blockZ)), -1, height - 1);
    }

    private static final class GeometrySource implements SavedTerrainChunk.VoxelSource {
        private final int originX;
        private final int originZ;
        private final Hunk<NativeBlockState> blocks;
        private final Hunk<NativeBiome> biomes;
        private final int minimumY;
        private final ChunkContext context;
        private final Map<NativeBlockState, BoundaryColumnGeometry.Voxel> voxels = new IdentityHashMap<>();
        private NativeBlockState air;
        private NativeBlockState[] column = new NativeBlockState[0];
        private NativeBlockState lastState;
        private BoundaryColumnGeometry.Voxel lastVoxel;

        private GeometrySource(int originX, int originZ, Hunk<NativeBlockState> blocks, Hunk<NativeBiome> biomes,
                               int minimumY, ChunkContext context) {
            this.originX = originX;
            this.originZ = originZ;
            this.blocks = blocks;
            this.biomes = biomes;
            this.minimumY = minimumY;
            this.context = context;
        }

        @Override
        public BoundaryColumnGeometry.Voxel voxel(int localX, int worldY, int localZ) {
            return voxelOf(stateAt(localX, worldY - minimumY, localZ));
        }

        @Override
        public void scanColumn(int localX, int localZ, int minimumY, int height, SavedTerrainChunk.VoxelRuns runs) {
            if (height <= 0) {
                return;
            }
            int base = minimumY - this.minimumY;
            if (column.length < height) {
                column = new NativeBlockState[height];
            }
            ColumnReadHunk.readColumn(blocks, localX, localZ, base, base + height - 1, column, 0);
            NativeBlockState runState = orAir(column[0]);
            int start = 0;
            for (int offset = 1; offset < height; offset++) {
                NativeBlockState state = orAir(column[offset]);
                if (state != runState) {
                    runs.add(start, offset, voxelOf(runState));
                    start = offset;
                    runState = state;
                }
            }
            runs.add(start, height, voxelOf(runState));
        }

        private NativeBlockState stateAt(int localX, int offset, int localZ) {
            return orAir(blocks.getRaw(localX, offset, localZ));
        }

        private NativeBlockState orAir(NativeBlockState state) {
            if (state != null) {
                return state;
            }
            if (air == null) {
                air = IrisPlatforms.get().registries().air();
            }
            return air;
        }

        private BoundaryColumnGeometry.Voxel voxelOf(NativeBlockState state) {
            if (state != lastState) {
                lastVoxel = voxels.computeIfAbsent(state, GeometrySource::encode);
                lastState = state;
            }
            return lastVoxel;
        }

        @Override
        public OptionalInt groundSurface(int localX, int localZ) {
            return OptionalInt.of(Math.max(0, Math.min(blocks.getHeight() - 1, context.getRoundedHeight(localX, localZ))));
        }

        @Override
        public OptionalInt waterSurface(int localX, int localZ) {
            return OptionalInt.of(waterHead(context.getComplex(), originX + localX, originZ + localZ, blocks.getHeight()));
        }

        @Override
        public String biome(int localX, int worldY, int localZ) throws IOException {
            NativeBiome biome = biomes.getRaw(localX, worldY - minimumY, localZ);
            if (biome == null) {
                throw new IOException("Natural terrain has no physical biome at " + localX + "," + worldY + "," + localZ);
            }
            return biome.key();
        }

        private BoundaryColumnGeometry column(int localX, int localZ) {
            ArrayList<BoundaryColumnGeometry.Voxel> values = new ArrayList<>(blocks.getHeight());
            for (int offset = 0; offset < blocks.getHeight(); offset++) {
                values.add(voxel(localX, minimumY + offset, localZ));
            }
            return BoundaryColumnGeometry.fromVoxels(minimumY, values);
        }

        private static BoundaryColumnGeometry.Voxel encode(NativeBlockState state) {
            String stateKey = state.key();
            boolean custom = state.isCustom();
            NativeBlockState nativeState = state.placementBaseState();
            if (nativeState != null) {
                state = nativeState;
            }
            BoundaryColumnGeometry.Phase phase = state.isAir() ? BoundaryColumnGeometry.Phase.AIR
                    : state.isFluid() ? BoundaryColumnGeometry.Phase.FLUID : BoundaryColumnGeometry.Phase.SOLID;
            String fluidKey = state.isFluid() ? state.key()
                    : state.isWaterLogged() ? "minecraft:water[level=0]" : "";
            boolean protectedContent = custom || state.isDecorant() || state.isFoliage() || state.isTreeBlock()
                    || state.hasTileEntity();
            return new BoundaryColumnGeometry.Voxel(stateKey, phase, fluidKey, protectedContent);
        }
    }
}
