package art.arcane.iris.generation.stage;

import art.arcane.iris.generation.runtime.Engine;
import art.arcane.iris.generation.runtime.EngineAssignedComponent;
import art.arcane.iris.generation.runtime.EngineStage;
import art.arcane.iris.world.history.BoundaryColumnGeometry;
import art.arcane.iris.world.history.BoundaryGeometryInfluence;
import art.arcane.iris.world.history.GenerationBlend;
import art.arcane.iris.world.history.SavedTerrainChunk;
import art.arcane.iris.world.history.TransitionGenerationPlan;
import art.arcane.iris.world.history.TransitionGeometryBlender;
import art.arcane.iris.spi.IrisPlatforms;
import art.arcane.iris.spi.PlatformBiome;
import art.arcane.iris.spi.PlatformBlockState;
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
    public void generate(int x, int z, Hunk<PlatformBlockState> blocks, Hunk<PlatformBiome> biomes,
                         boolean multicore, ChunkContext context) {
        TransitionGenerationPlan plan = context.getComplex().getTransitionGenerationPlan();
        if (plan == null || !plan.hasTransitionAtChunk(x >> 4, z >> 4)) {
            return;
        }
        GeometrySource source = new GeometrySource(blocks, biomes, getEngine().getMinHeight(), context);
        blendGeometry(x, z, blocks, context, source, plan);
    }

    public static SavedTerrainChunk capture(int x, int z, Hunk<PlatformBlockState> blocks,
                                             Hunk<PlatformBiome> biomes, int minimumY,
                                             ChunkContext context, boolean boundaryOnly) throws IOException {
        GeometrySource source = new GeometrySource(blocks, biomes, minimumY, context);
        return boundaryOnly
                ? SavedTerrainChunk.captureBoundary(x >> 4, z >> 4, minimumY, blocks.getHeight(), "minecraft:noise", source)
                : SavedTerrainChunk.capture(x >> 4, z >> 4, minimumY, blocks.getHeight(), "minecraft:noise", source);
    }

    private void blendGeometry(int x, int z, Hunk<PlatformBlockState> blocks, ChunkContext context,
                               GeometrySource source, TransitionGenerationPlan plan) {
        PlatformRegistries registries = IrisPlatforms.get().registries();
        Map<String, PlatformBlockState> resolved = new HashMap<>();
        for (int localX = 0; localX < blocks.getWidth(); localX++) {
            for (int localZ = 0; localZ < blocks.getDepth(); localZ++) {
                BoundaryGeometryInfluence influence = plan.geometryAt(x + localX, z + localZ);
                if (influence.newTerrainWeight() == 1D || influence.contributions().isEmpty()) {
                    continue;
                }
                BoundaryColumnGeometry current = source.column(localX, localZ);
                BoundaryColumnGeometry blended = TransitionGeometryBlender.blendColumn(
                        influence, x + localX, z + localZ, current);
                if (blended == current) {
                    continue;
                }
                double expectedFloor = GenerationBlend.interpolate(
                        plan.terrainSampleAt(x + localX, z + localZ).historicalOceanFloorHeight(),
                        context.getRoundedHeight(localX, localZ), influence.newTerrainWeight());
                int floor = blended.surfaceOffsetNear(expectedFloor);
                List<BoundaryColumnGeometry.Voxel> voxels = blended.voxels();
                for (int offset = 0; offset < voxels.size(); offset++) {
                    BoundaryColumnGeometry.Voxel voxel = voxels.get(offset);
                    PlatformBlockState existing = blocks.getRaw(localX, offset, localZ);
                    if (existing != null && existing.key().equals(voxel.stateKey())) {
                        continue;
                    }
                    PlatformBlockState replacement = resolved.computeIfAbsent(voxel.stateKey(), registries::blockOrNull);
                    if (replacement == null) {
                        throw new IllegalStateException("Terrain boundary requires unavailable block state " + voxel.stateKey());
                    }
                    blocks.setRaw(localX, offset, localZ, replacement);
                }
                context.setTerrainHeight(localX, localZ, floor);
            }
        }
    }

    private static final class GeometrySource implements SavedTerrainChunk.VoxelSource {
        private final Hunk<PlatformBlockState> blocks;
        private final Hunk<PlatformBiome> biomes;
        private final int minimumY;
        private final ChunkContext context;
        private final Map<PlatformBlockState, BoundaryColumnGeometry.Voxel> voxels = new IdentityHashMap<>();

        private GeometrySource(Hunk<PlatformBlockState> blocks, Hunk<PlatformBiome> biomes, int minimumY, ChunkContext context) {
            this.blocks = blocks;
            this.biomes = biomes;
            this.minimumY = minimumY;
            this.context = context;
        }

        @Override
        public BoundaryColumnGeometry.Voxel voxel(int localX, int worldY, int localZ) {
            PlatformBlockState state = blocks.getRaw(localX, worldY - minimumY, localZ);
            if (state == null) {
                state = IrisPlatforms.get().registries().air();
            }
            return voxels.computeIfAbsent(state, GeometrySource::encode);
        }

        @Override
        public OptionalInt groundSurface(int localX, int localZ) {
            return OptionalInt.of(Math.max(0, Math.min(blocks.getHeight() - 1, context.getRoundedHeight(localX, localZ))));
        }

        @Override
        public String biome(int localX, int worldY, int localZ) throws IOException {
            PlatformBiome biome = biomes.getRaw(localX, worldY - minimumY, localZ);
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

        private static BoundaryColumnGeometry.Voxel encode(PlatformBlockState state) {
            String stateKey = state.key();
            boolean custom = state.isCustom();
            PlatformBlockState nativeState = state.placementBaseState();
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
