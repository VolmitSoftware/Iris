package art.arcane.iris.modded;

import art.arcane.iris.structure.placement.IrisStructureTerrain;
import art.arcane.iris.structure.placement.IrisStructureTerrainMode;
import art.arcane.iris.nativegen.NativeStructurePostProcessor;
import art.arcane.iris.nativegen.NativeStructureTerrainIntegrator;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.PoolElementStructurePiece;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.TerrainAdjustment;
import net.minecraft.world.level.levelgen.structure.pieces.PiecesContainer;
import net.minecraft.world.level.levelgen.structure.pools.StructurePoolElement;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool;
import net.minecraft.world.level.levelgen.structure.structures.DesertPyramidStructure;
import net.minecraft.world.level.levelgen.structure.templatesystem.GravityProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.LiquidSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.ticks.LevelTickAccess;
import net.minecraft.world.ticks.ScheduledTick;
import org.junit.BeforeClass;
import org.junit.Test;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class ModdedNativeStructureWorldgenAccessTest {
    private static final int GENERATION_CENTER_X = 60;
    private static final int GENERATION_CENTER_Z = 15;
    private static final int SURFACE_FIRST_FREE_Y = 80;
    private static final int FLOOR_FIRST_FREE_Y = 70;

    @BeforeClass
    public static void bootstrapMinecraftRegistries() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    public void ordinaryWorldFlattenPreparationHandlesReferencedVillagePiecesBeyondEightChunks() {
        int farX = (GENERATION_CENTER_X - 9) << 4;
        int farZ = (GENERATION_CENTER_Z + 4) << 4;
        BoundingBox bounds = new BoundingBox(farX, 64, farZ, farX + 4, 90, farZ + 4);
        PoolElementStructurePiece piece = new PoolElementStructurePiece(
                null, StructurePoolElement.single("minecraft:empty").apply(StructureTemplatePool.Projection.RIGID),
                new BlockPos(farX, 64, farZ), 1, Rotation.NONE, bounds, LiquidSettings.APPLY_WATERLOGGING);
        Structure structure = new DesertPyramidStructure(new Structure.StructureSettings(
                HolderSet.empty(), Map.of(), GenerationStep.Decoration.SURFACE_STRUCTURES, TerrainAdjustment.BEARD_BOX));
        StructureStart start = new StructureStart(structure, generationCenter(), 0, new PiecesContainer(List.of(piece)));
        List<NativeStructureTerrainIntegrator.TerrainTarget> targets = List.of(
                new NativeStructureTerrainIntegrator.TerrainTarget("minecraft:village_plains", start,
                        new IrisStructureTerrain().setMode(IrisStructureTerrainMode.FLATTEN)));
        BoundingBox area = new BoundingBox(generationCenter().getMinBlockX(), -64, generationCenter().getMinBlockZ(),
                generationCenter().getMaxBlockX(), 319, generationCenter().getMaxBlockZ());
        RecordingDelegate unbounded = new RecordingDelegate();
        unbounded.rejectDistantTerrainReads = true;

        assertThrows(IllegalStateException.class, () -> NativeStructurePostProcessor.prepareTerrain(
                unbounded.world(), area, targets, (palette, rng, x, y, z) -> Blocks.STONE.defaultBlockState()));

        RecordingDelegate recording = new RecordingDelegate();
        recording.rejectDistantTerrainReads = true;
        ModdedNativeStructureWorldgenAccess access = access(recording);
        NativeStructurePostProcessor.prepareTerrain(
                access, area, targets, (palette, rng, x, y, z) -> Blocks.STONE.defaultBlockState());

        assertEquals(0, recording.heightReads);
        assertEquals(0, recording.chunkReads);
        assertEquals(0, recording.terrainReads);
        assertEquals(0, recording.mutations);
        assertEquals(91, access.getHeight(Heightmap.Types.WORLD_SURFACE_WG,
                generationCenter().getMinBlockX(), generationCenter().getMinBlockZ()));
        assertEquals(1, recording.heightReads);
    }

    @Test
    public void projectedTemplatePrefiltersUseRemoteTerrainAndRetainLocalHeightmaps() {
        RecordingDelegate recording = new RecordingDelegate();
        recording.rejectDistantTerrainReads = true;
        ModdedNativeStructureWorldgenAccess access = access(recording);
        GravityProcessor gravity = new GravityProcessor(Heightmap.Types.WORLD_SURFACE_WG, -1);
        BlockPos remote = new BlockPos((GENERATION_CENTER_X - 9) << 4, 64, (GENERATION_CENTER_Z + 4) << 4);
        StructureTemplate.StructureBlockInfo raw = new StructureTemplate.StructureBlockInfo(
                new BlockPos(0, 4, 0), Blocks.DIRT_PATH.defaultBlockState(), null);
        StructurePlaceSettings settings = new StructurePlaceSettings().addProcessor(gravity);

        StructureTemplate.StructureBlockInfo projected = StructureTemplate.processBlockInfos(
                access, remote, BlockPos.ZERO, settings, List.of(raw)).getFirst();

        assertEquals(remote.atY(SURFACE_FIRST_FREE_Y + 3), projected.pos());
        assertEquals(0, recording.heightReads);
        BlockPos local = generationCenter().getMiddleBlockPosition(64);
        StructureTemplate.StructureBlockInfo localBlock = new StructureTemplate.StructureBlockInfo(
                BlockPos.ZERO, Blocks.DIRT_PATH.defaultBlockState(), null);
        StructureTemplate.StructureBlockInfo localProjection = StructureTemplate.processBlockInfos(
                access, local, BlockPos.ZERO, settings, List.of(localBlock)).getFirst();
        assertEquals(local.atY(90), localProjection.pos());
        assertEquals(1, recording.heightReads);
        assertEquals(0, recording.chunkReads);
    }

    @Test
    public void distanceTwoTerrainReadsNeverReachTheDelegate() {
        RecordingDelegate recording = new RecordingDelegate();
        ModdedNativeStructureWorldgenAccess access = access(recording);
        ChunkPos generationCenter = generationCenter();
        int x = generationCenter.getMiddleBlockX();
        int z = (generationCenter.z() + 2) << 4;

        assertSame(Blocks.STONE, access.getBlockState(new BlockPos(x, FLOOR_FIRST_FREE_Y - 1, z)).getBlock());
        assertSame(Blocks.WATER, access.getBlockState(new BlockPos(x, FLOOR_FIRST_FREE_Y, z)).getBlock());
        assertSame(Blocks.AIR, access.getBlockState(new BlockPos(x, SURFACE_FIRST_FREE_Y, z)).getBlock());
        assertSame(Blocks.WATER.defaultBlockState().getFluidState(),
                access.getFluidState(new BlockPos(x, FLOOR_FIRST_FREE_Y, z)));
        assertEquals(SURFACE_FIRST_FREE_Y, access.getHeight(Heightmap.Types.WORLD_SURFACE_WG, x, z));
        assertEquals(FLOOR_FIRST_FREE_Y, access.getHeight(Heightmap.Types.OCEAN_FLOOR_WG, x, z));
        assertNull(access.getChunk(generationCenter.x(), generationCenter.z() + 2, ChunkStatus.FEATURES, false));
        List<BlockState> loadedOnly = access.getBlockStatesIfLoaded(new AABB(
                x, FLOOR_FIRST_FREE_Y, z, x, FLOOR_FIRST_FREE_Y, z)).toList();
        List<BlockState> streamed = access.getBlockStates(new AABB(
                x, FLOOR_FIRST_FREE_Y, z, x, FLOOR_FIRST_FREE_Y, z)).toList();

        assertTrue(loadedOnly.isEmpty());
        assertEquals(1, streamed.size());
        assertSame(Blocks.WATER, streamed.getFirst().getBlock());
        assertEquals(0, recording.terrainReads);
        assertEquals(0, recording.heightReads);
        assertEquals(0, recording.chunkReads);
    }

    @Test
    public void distanceTwoMutationsAndSideEffectsNeverReachTheDelegate() {
        RecordingDelegate recording = new RecordingDelegate();
        ModdedNativeStructureWorldgenAccess access = access(recording);
        ChunkPos generationCenter = generationCenter();
        BlockPos position = new BlockPos(
                generationCenter.getMiddleBlockX(),
                FLOOR_FIRST_FREE_Y,
                (generationCenter.z() + 2) << 4);

        assertFalse(access.ensureCanWrite(position));
        assertFalse(access.setBlock(position, Blocks.DIRT.defaultBlockState(), 2));
        assertFalse(access.removeBlock(position, false));
        assertFalse(access.destroyBlock(position, false));
        access.updateNeighborsAt(position, Blocks.DIRT);
        access.neighborShapeChanged(
                Direction.NORTH, position, position.north(),
                Blocks.DIRT.defaultBlockState(), 2, 512);
        access.levelEvent(null, 2001, position, 0);
        access.gameEvent(null, Vec3.atCenterOf(position), null);
        access.addParticle(null, position.getX(), position.getY(), position.getZ(), 0, 0, 0);
        access.getBlockTicks().schedule(new ScheduledTick<>(
                Blocks.DIRT, position, 1L, 0L));

        assertEquals(0, recording.mutations);
        assertEquals(0, recording.events);
        assertEquals(0, recording.blockTicks.count());
    }

    @Test
    public void radiusOneReadsWritesAndTicksRemainUnchanged() {
        RecordingDelegate recording = new RecordingDelegate();
        ModdedNativeStructureWorldgenAccess access = access(recording);
        ChunkPos generationCenter = generationCenter();
        BlockPos position = new BlockPos(
                (generationCenter.x() + 1) << 4,
                FLOOR_FIRST_FREE_Y,
                (generationCenter.z() - 1) << 4);

        assertTrue(access.isInsideGenerationRegion(
                generationCenter.x() + 1, generationCenter.z() - 1));
        assertSame(Blocks.DIRT, access.getBlockState(position).getBlock());
        assertEquals(91, access.getHeight(
                Heightmap.Types.WORLD_SURFACE_WG, position.getX(), position.getZ()));
        assertTrue(access.ensureCanWrite(position));
        assertTrue(access.setBlock(position, Blocks.STONE.defaultBlockState(), 2));
        assertTrue(access.removeBlock(position, false));
        assertTrue(access.destroyBlock(position, false));
        access.getBlockTicks().schedule(new ScheduledTick<>(
                Blocks.DIRT, position, 1L, 0L));

        assertEquals(1, recording.terrainReads);
        assertEquals(1, recording.heightReads);
        assertEquals(4, recording.mutations);
        assertEquals(1, recording.blockTicks.count());
    }

    @Test
    public void virtualizedHeightsHideInRegionTerrainFromTheDelegate() {
        RecordingDelegate recording = new RecordingDelegate();
        ChunkPos generationCenter = generationCenter();
        ModdedNativeStructureWorldgenAccess access = ModdedNativeStructureWorldgenAccess.create(
                recording.world(), generationCenter,
                (x, z) -> SURFACE_FIRST_FREE_Y,
                (x, z) -> FLOOR_FIRST_FREE_Y,
                true,
                position -> false);
        int x = generationCenter.getMiddleBlockX();
        int z = generationCenter.getMiddleBlockZ();

        assertEquals(SURFACE_FIRST_FREE_Y, access.getHeight(Heightmap.Types.WORLD_SURFACE_WG, x, z));
        assertEquals(FLOOR_FIRST_FREE_Y, access.getHeight(Heightmap.Types.OCEAN_FLOOR_WG, x, z));
        assertEquals(0, recording.heightReads);
    }

    @Test
    public void protectedCellsRemainReadableButRejectWritesTilesAndTicks() {
        RecordingDelegate recording = new RecordingDelegate();
        BlockPos position = generationCenter().getMiddleBlockPosition(70);
        ModdedNativeStructureWorldgenAccess access = ModdedNativeStructureWorldgenAccess.create(
                recording.world(), generationCenter(),
                (x, z) -> SURFACE_FIRST_FREE_Y,
                (x, z) -> FLOOR_FIRST_FREE_Y,
                false,
                position::equals);

        assertSame(Blocks.DIRT, access.getBlockState(position).getBlock());
        assertFalse(access.ensureCanWrite(position));
        assertFalse(access.setBlock(position, Blocks.STONE.defaultBlockState(), 2));
        assertFalse(access.removeBlock(position, false));
        assertFalse(access.destroyBlock(position, false));
        assertNull(access.getBlockEntity(position));
        assertTrue(access.getBlockEntity(position, null).isEmpty());
        access.getBlockTicks().schedule(new ScheduledTick<>(Blocks.DIRT, position, 1L, 0L));
        access.updateNeighborsAt(position.east(), Blocks.DIRT);
        access.neighborShapeChanged(
                Direction.EAST, position, position.east(), Blocks.DIRT.defaultBlockState(), 2, 512);

        assertEquals(1, recording.terrainReads);
        assertEquals(0, recording.mutations);
        assertEquals(0, recording.blockTicks.count());
        assertTrue(access.setBlock(position.above(), Blocks.STONE.defaultBlockState(), 2));
        assertEquals(1, recording.mutations);
    }

    @Test
    public void statuslessChunkReadsPreserveWorldgenDelegateSemantics() {
        RecordingDelegate recording = new RecordingDelegate();
        ModdedNativeStructureWorldgenAccess access = access(recording);
        ChunkPos generationCenter = generationCenter();

        assertNull(access.getChunk(generationCenter.getWorldPosition()));

        assertEquals(1, recording.statuslessChunkReads);
        assertEquals(0, recording.statusChunkReads);
    }

    @Test
    public void entityQueriesRejectDisjointAreasAndClampOverlaps() {
        RecordingDelegate recording = new RecordingDelegate();
        ModdedNativeStructureWorldgenAccess access = access(recording);
        ChunkPos generationCenter = generationCenter();
        int safeMaxX = generationCenter.getMaxBlockX() + 17;
        int safeMinZ = generationCenter.getMinBlockZ() - 16;
        int safeMaxZ = generationCenter.getMaxBlockZ() + 17;
        AABB disjoint = new AABB(
                safeMaxX, -64, safeMinZ,
                safeMaxX + 16, 320, safeMaxZ);
        AABB overlap = new AABB(
                safeMaxX - 8, -128, safeMinZ - 8,
                safeMaxX + 16, 400, safeMaxZ + 8);
        EntityTypeTest<Entity, Entity> type = EntityTypeTest.forClass(Entity.class);

        assertTrue(access.getEntities((Entity) null, disjoint, entity -> true).isEmpty());
        assertTrue(access.getEntities(type, disjoint, entity -> true).isEmpty());
        access.getEntities((Entity) null, overlap, entity -> true);
        access.getEntities(type, overlap, entity -> true);

        assertEquals(2, recording.entityAreas.size());
        for (AABB delegatedArea : recording.entityAreas) {
            assertEquals(safeMaxX - 8, delegatedArea.minX, 0D);
            assertEquals(-64, delegatedArea.minY, 0D);
            assertEquals(safeMinZ, delegatedArea.minZ, 0D);
            assertEquals(safeMaxX, delegatedArea.maxX, 0D);
            assertEquals(320, delegatedArea.maxY, 0D);
            assertEquals(safeMaxZ, delegatedArea.maxZ, 0D);
        }
    }

    private static ModdedNativeStructureWorldgenAccess access(RecordingDelegate recording) {
        return ModdedNativeStructureWorldgenAccess.create(
                recording.world(), generationCenter(),
                (x, z) -> SURFACE_FIRST_FREE_Y,
                (x, z) -> FLOOR_FIRST_FREE_Y,
                false,
                position -> false);
    }

    private static ChunkPos generationCenter() {
        return new ChunkPos(GENERATION_CENTER_X, GENERATION_CENTER_Z);
    }

    private static final class RecordingDelegate implements InvocationHandler {
        private final Holder<Biome> biome;
        private final BiomeManager biomeManager;
        private final RecordingTicks<Block> blockTicks;
        private final RecordingTicks<Fluid> fluidTicks;
        private final List<AABB> entityAreas;
        private int terrainReads;
        private int heightReads;
        private int chunkReads;
        private int statuslessChunkReads;
        private int statusChunkReads;
        private int mutations;
        private int events;
        private boolean rejectDistantTerrainReads;

        private RecordingDelegate() {
            this.biome = Holder.direct((Biome) null);
            this.biomeManager = new BiomeManager((x, y, z) -> biome, 13L);
            this.blockTicks = new RecordingTicks<>();
            this.fluidTicks = new RecordingTicks<>();
            this.entityAreas = new ArrayList<>();
        }

        private WorldGenLevel world() {
            return (WorldGenLevel) Proxy.newProxyInstance(
                    WorldGenLevel.class.getClassLoader(),
                    new Class<?>[]{WorldGenLevel.class}, this);
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] arguments) {
            String name = method.getName();
            if (name.equals("getSeaLevel")) {
                return 63;
            }
            if (name.equals("getBiome")) {
                return biome;
            }
            if (name.equals("getBiomeManager")) {
                return biomeManager;
            }
            if (name.equals("getBlockTicks")) {
                return blockTicks;
            }
            if (name.equals("getFluidTicks")) {
                return fluidTicks;
            }
            if (name.equals("getMinY")) {
                return -64;
            }
            if (name.equals("getHeight") && arguments == null) {
                return 384;
            }
            if (name.equals("getHeight")) {
                if (rejectDistantTerrainReads
                        && (Math.abs(((int) arguments[1] >> 4) - GENERATION_CENTER_X) > 1
                        || Math.abs(((int) arguments[2] >> 4) - GENERATION_CENTER_Z) > 1)) {
                    throw new IllegalStateException("Requested chunk unavailable during world generation");
                }
                heightReads++;
                return 91;
            }
            if (name.equals("getBlockState")) {
                terrainReads++;
                return Blocks.DIRT.defaultBlockState();
            }
            if (name.equals("getFluidState")) {
                terrainReads++;
                return Blocks.WATER.defaultBlockState().getFluidState();
            }
            if (name.equals("getChunk")) {
                chunkReads++;
                if (arguments.length == 2) {
                    statuslessChunkReads++;
                } else {
                    statusChunkReads++;
                }
                return null;
            }
            if (name.equals("getChunkIfLoadedImmediately")) {
                chunkReads++;
                return null;
            }
            if (name.equals("getEntities")) {
                entityAreas.add((AABB) arguments[1]);
                return List.of();
            }
            if (name.equals("ensureCanWrite") || name.equals("setBlock")
                    || name.equals("removeBlock") || name.equals("destroyBlock")) {
                mutations++;
                return true;
            }
            if (name.equals("updateNeighborsAt") || name.equals("neighborShapeChanged")) {
                mutations++;
                return null;
            }
            if (name.equals("levelEvent") || name.equals("gameEvent") || name.equals("addParticle")) {
                events++;
                return null;
            }
            if (name.equals("hashCode")) {
                return System.identityHashCode(proxy);
            }
            if (name.equals("equals")) {
                return proxy == arguments[0];
            }
            if (name.equals("toString")) {
                return "native-structure-worldgen-test-delegate";
            }
            throw new UnsupportedOperationException(method.toString());
        }
    }

    private static final class RecordingTicks<T> implements LevelTickAccess<T> {
        private int count;

        @Override
        public void schedule(ScheduledTick<T> tick) {
            count++;
        }

        @Override
        public boolean hasScheduledTick(BlockPos position, T type) {
            return false;
        }

        @Override
        public int count() {
            return count;
        }

        @Override
        public boolean willTickThisTick(BlockPos position, T type) {
            return false;
        }
    }
}
