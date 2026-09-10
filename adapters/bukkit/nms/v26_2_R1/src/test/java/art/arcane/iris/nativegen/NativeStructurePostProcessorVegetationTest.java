package art.arcane.iris.nativegen;

import com.mojang.serialization.Codec;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.IdMapper;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.Bootstrap;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.PalettedContainer;
import net.minecraft.world.level.chunk.PalettedContainerFactory;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.chunk.Strategy;
import net.minecraft.world.level.chunk.UpgradeData;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.TerrainAdjustment;
import net.minecraft.world.level.levelgen.structure.pieces.PiecesContainer;
import net.minecraft.world.level.levelgen.structure.structures.DesertPyramidPiece;
import net.minecraft.world.level.levelgen.structure.structures.DesertPyramidStructure;
import org.junit.BeforeClass;
import org.junit.Test;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class NativeStructurePostProcessorVegetationTest {
    private static final long TEST_SEED = 1234L;
    private static final int MIN_Y = -64;
    private static final int MAX_Y = 319;
    private static final int GROUND_Y = 64;
    private static final BoundingBox AREA = new BoundingBox(0, MIN_Y, 0, 15, MAX_Y, 15);

    @BeforeClass
    public static void bootstrapMinecraftRegistries() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    public void canopyAboveTheStructureRoofSurvivesVegetationClearing() {
        ProtoChunk chunk = chunk();
        StructureStart start = desertStart(0, 0, GROUND_Y + 15);
        BoundingBox bounds = start.getBoundingBox();
        for (int y = bounds.maxY() - 1; y <= GROUND_Y + 40; y++) {
            fillLayer(chunk, y, leaves());
        }
        write(chunk, 4, bounds.minY() - 1, 4, log());

        clearVegetation(chunk, start);

        for (int z = 0; z < 16; z++) {
            for (int x = 0; x < 16; x++) {
                assertEquals(leaves(), chunk.getBlockState(new BlockPos(x, GROUND_Y + 40, z)));
                assertEquals(leaves(), chunk.getBlockState(new BlockPos(x, bounds.maxY() + 1, z)));
                assertEquals(air(), chunk.getBlockState(new BlockPos(x, bounds.maxY(), z)));
                assertEquals(air(), chunk.getBlockState(new BlockPos(x, bounds.maxY() - 1, z)));
            }
        }
        assertEquals(air(), chunk.getBlockState(new BlockPos(4, bounds.minY() - 1, 4)));
    }

    @Test
    public void buriedStructuresPreserveTheForestAboveThem() {
        ProtoChunk chunk = chunk();
        StructureStart start = desertStart(0, 0, -20);
        write(chunk, 8, GROUND_Y + 1, 8, log());
        write(chunk, 8, GROUND_Y + 2, 8, log());
        write(chunk, 8, GROUND_Y + 3, 8, log());
        write(chunk, 8, GROUND_Y + 4, 8, leaves());

        clearVegetation(chunk, start);

        assertEquals(log(), chunk.getBlockState(new BlockPos(8, GROUND_Y + 1, 8)));
        assertEquals(log(), chunk.getBlockState(new BlockPos(8, GROUND_Y + 2, 8)));
        assertEquals(log(), chunk.getBlockState(new BlockPos(8, GROUND_Y + 3, 8)));
        assertEquals(leaves(), chunk.getBlockState(new BlockPos(8, GROUND_Y + 4, 8)));
    }

    @Test
    public void columnsOutsideThePieceFootprintKeepEveryTreeBlock() {
        ProtoChunk chunk = chunk();
        StructureStart start = desertStart(8, 8, GROUND_Y + 15);
        BoundingBox bounds = start.getBoundingBox();
        int inside = bounds.minY() + 4;
        write(chunk, 0, inside, 0, log());
        write(chunk, 0, GROUND_Y + 40, 0, leaves());
        write(chunk, 12, inside, 12, log());
        write(chunk, 12, GROUND_Y + 40, 12, leaves());

        clearVegetation(chunk, start);

        assertEquals(log(), chunk.getBlockState(new BlockPos(0, inside, 0)));
        assertEquals(leaves(), chunk.getBlockState(new BlockPos(0, GROUND_Y + 40, 0)));
        assertEquals(air(), chunk.getBlockState(new BlockPos(12, inside, 12)));
        assertEquals(leaves(), chunk.getBlockState(new BlockPos(12, GROUND_Y + 40, 12)));
    }

    @Test
    public void clearingStopsAtThePieceEnvelopeBoundaries() {
        ProtoChunk chunk = chunk();
        StructureStart start = desertStart(0, 0, GROUND_Y + 15);
        BoundingBox bounds = start.getBoundingBox();
        write(chunk, 4, bounds.maxY() + 1, 4, log());
        write(chunk, 4, bounds.maxY(), 4, log());
        write(chunk, 4, bounds.minY() - 1, 4, log());
        write(chunk, 4, bounds.minY() - 2, 4, log());

        clearVegetation(chunk, start);

        assertEquals(log(), chunk.getBlockState(new BlockPos(4, bounds.maxY() + 1, 4)));
        assertEquals(air(), chunk.getBlockState(new BlockPos(4, bounds.maxY(), 4)));
        assertEquals(air(), chunk.getBlockState(new BlockPos(4, bounds.minY() - 1, 4)));
        assertEquals(log(), chunk.getBlockState(new BlockPos(4, bounds.minY() - 2, 4)));
    }

    @Test
    public void overlappingPieceEnvelopesMergeIntoOneClearedRange() {
        ProtoChunk chunk = chunk();
        StructureStart low = desertStart(0, 0, GROUND_Y);
        StructureStart high = desertStart(0, 0, GROUND_Y + 40);
        int between = GROUND_Y + 20;
        write(chunk, 4, between, 4, log());
        write(chunk, 4, high.getBoundingBox().maxY() + 1, 4, log());
        write(chunk, 4, low.getBoundingBox().minY() - 2, 4, log());

        NativeStructureVegetationClearer.clearIntersectingVegetation(
                world(chunk), chunk, AREA, List.of(low, high));

        assertEquals(air(), chunk.getBlockState(new BlockPos(4, between, 4)));
        assertEquals(log(), chunk.getBlockState(
                new BlockPos(4, high.getBoundingBox().maxY() + 1, 4)));
        assertEquals(log(), chunk.getBlockState(
                new BlockPos(4, low.getBoundingBox().minY() - 2, 4)));
    }

    @Test
    public void validStartsReachIntersectionProcessing() {
        assertTrue(NativeStructureVegetationClearer.shouldProcessStart(
                desertStart(0, 0, GROUND_Y + 15)));
        assertFalse(NativeStructureVegetationClearer.shouldProcessStart(
                StructureStart.INVALID_START));
    }

    @Test
    public void allUndergroundGenerationStepsShareOneClassification() {
        assertTrue(NativeStructureVegetationClearer.isUndergroundStep(
                GenerationStep.Decoration.UNDERGROUND_STRUCTURES));
        assertTrue(NativeStructureVegetationClearer.isUndergroundStep(
                GenerationStep.Decoration.UNDERGROUND_DECORATION));
        assertTrue(NativeStructureVegetationClearer.isUndergroundStep(
                GenerationStep.Decoration.STRONGHOLDS));
        assertFalse(NativeStructureVegetationClearer.isUndergroundStep(
                GenerationStep.Decoration.SURFACE_STRUCTURES));
    }

    @Test
    public void undergroundStructuresUseTheLowestTerrainColumn() {
        BoundingBox bounds = new BoundingBox(0, 60, 0, 1, 80, 0);
        int offset = NativeStructureVerticalPlacer.resolveBuriedOffset(
                bounds, 0, -64, 320, (x, z) -> x == 1 ? 76 : 100);
        assertEquals(-5, offset);
    }

    @Test
    public void undergroundBurialClampsToTheWorldFloorInsteadOfFailing() {
        BoundingBox bounds = new BoundingBox(0, 60, 0, 1, 80, 0);
        assertEquals(-2, NativeStructureVerticalPlacer.resolveBuriedOffset(
                bounds, 0, 58, 320, (x, z) -> x == 1 ? 76 : 100));
    }

    @Test
    public void loweringSurfaceRemovesCactusStacksAndTheirFlowers() {
        ProtoChunk chunk = chunk();
        for (int y = 80; y <= 102; y++) {
            write(chunk, 4, y, 4, Blocks.SAND.defaultBlockState());
        }
        for (int y = 103; y <= 105; y++) {
            write(chunk, 4, y, 4, Blocks.CACTUS.defaultBlockState());
        }
        write(chunk, 4, 106, 4, Blocks.CACTUS_FLOWER.defaultBlockState());

        NativeStructureSurfaceFitter.applySurfaceColumn(
                world(chunk), new BlockPos.MutableBlockPos(), 4, 4, 102, 93, MIN_Y, MAX_Y);

        assertEquals(Blocks.SAND.defaultBlockState(), chunk.getBlockState(new BlockPos(4, 93, 4)));
        for (int y = 94; y <= 106; y++) {
            assertTrue("Unsupported decoration at " + y, chunk.getBlockState(new BlockPos(4, y, 4)).isAir());
        }
    }

    @Test
    public void loweringSurfacePreservesCactusOnUnchangedColumns() {
        ProtoChunk chunk = chunk();
        for (int x : List.of(4, 5)) {
            for (int y = 60; y <= 64; y++) {
                write(chunk, x, y, 4, Blocks.SAND.defaultBlockState());
            }
            write(chunk, x, 65, 4, Blocks.CACTUS.defaultBlockState());
            write(chunk, x, 66, 4, Blocks.CACTUS_FLOWER.defaultBlockState());
        }
        WorldGenLevel world = world(chunk);

        NativeStructureSurfaceFitter.applySurfaceColumn(
                world, new BlockPos.MutableBlockPos(), 4, 4, 64, 60, MIN_Y, MAX_Y);
        NativeStructureSurfaceFitter.applySurfaceColumn(
                world, new BlockPos.MutableBlockPos(), 5, 4, 64, 64, MIN_Y, MAX_Y);

        assertTrue(chunk.getBlockState(new BlockPos(4, 65, 4)).isAir());
        assertTrue(chunk.getBlockState(new BlockPos(4, 66, 4)).isAir());
        assertEquals(Blocks.SAND.defaultBlockState(), chunk.getBlockState(new BlockPos(5, 64, 4)));
        assertEquals(Blocks.CACTUS.defaultBlockState(), chunk.getBlockState(new BlockPos(5, 65, 4)));
        assertEquals(Blocks.CACTUS_FLOWER.defaultBlockState(), chunk.getBlockState(new BlockPos(5, 66, 4)));
    }

    @Test
    public void loweringSurfaceKeepsTreeAndSolidObjectBoundaries() {
        for (BlockState boundary : List.of(Blocks.OAK_LOG.defaultBlockState(), Blocks.OAK_PLANKS.defaultBlockState())) {
            ProtoChunk chunk = chunk();
            write(chunk, 4, 63, 4, Blocks.SAND.defaultBlockState());
            write(chunk, 4, 64, 4, Blocks.SAND.defaultBlockState());
            write(chunk, 4, 65, 4, boundary);
            write(chunk, 4, 66, 4, Blocks.CACTUS.defaultBlockState());
            write(chunk, 4, 67, 4, Blocks.CACTUS_FLOWER.defaultBlockState());

            NativeStructureSurfaceFitter.applySurfaceColumn(
                    world(chunk), new BlockPos.MutableBlockPos(), 4, 4, 64, 60, MIN_Y, MAX_Y);

            assertEquals(boundary, chunk.getBlockState(new BlockPos(4, 65, 4)));
            assertEquals(Blocks.CACTUS.defaultBlockState(), chunk.getBlockState(new BlockPos(4, 66, 4)));
            assertEquals(Blocks.CACTUS_FLOWER.defaultBlockState(), chunk.getBlockState(new BlockPos(4, 67, 4)));
        }
    }

    private static void clearVegetation(ProtoChunk chunk, StructureStart start) {
        NativeStructureVegetationClearer.clearIntersectingVegetation(
                world(chunk), chunk, AREA, List.of(start));
    }

    private static StructureStart desertStart(int originX, int originZ, int topY) {
        Structure structure = new DesertPyramidStructure(
                new Structure.StructureSettings(
                        HolderSet.empty(), Map.of(),
                        GenerationStep.Decoration.SURFACE_STRUCTURES, TerrainAdjustment.NONE));
        DesertPyramidPiece piece = new DesertPyramidPiece(RandomSource.create(7L), originX, originZ);
        piece.move(0, topY - piece.getBoundingBox().maxY(), 0);
        return new StructureStart(
                structure, new ChunkPos(0, 0), 0, new PiecesContainer(List.of(piece)));
    }

    private static ProtoChunk chunk() {
        return new ProtoChunk(
                new ChunkPos(0, 0), UpgradeData.EMPTY,
                LevelHeightAccessor.create(MIN_Y, MAX_Y - MIN_Y + 1), containerFactory(), null);
    }

    private static void fillLayer(ProtoChunk chunk, int y, BlockState state) {
        for (int z = 0; z < 16; z++) {
            for (int x = 0; x < 16; x++) {
                write(chunk, x, y, z, state);
            }
        }
    }

    private static void write(ProtoChunk chunk, int x, int y, int z, BlockState state) {
        LevelChunkSection section = chunk.getSection(chunk.getSectionIndex(y));
        section.setBlockState(x, y & 15, z, state, false);
    }

    private static BlockState log() {
        return Blocks.OAK_LOG.defaultBlockState();
    }

    private static BlockState leaves() {
        return Blocks.OAK_LEAVES.defaultBlockState();
    }

    private static BlockState air() {
        return Blocks.AIR.defaultBlockState();
    }

    private static WorldGenLevel world(ChunkAccess chunk) {
        InvocationHandler handler = (proxy, method, arguments) -> {
            String methodName = method.getName();
            if (methodName.equals("getBlockState")) {
                return chunk.getBlockState((BlockPos) arguments[0]);
            }
            if (methodName.equals("setBlock")) {
                return chunk.setBlockState(
                        ((BlockPos) arguments[0]).immutable(),
                        (BlockState) arguments[1], (int) arguments[2]) != null;
            }
            if (methodName.equals("getSeed")) {
                return TEST_SEED;
            }
            if (methodName.equals("getLevel")) {
                return null;
            }
            if (methodName.equals("holderLookup")) {
                return BuiltInRegistries.BLOCK;
            }
            if (methodName.equals("hashCode")) {
                return System.identityHashCode(proxy);
            }
            if (methodName.equals("equals")) {
                return proxy == arguments[0];
            }
            if (methodName.equals("toString")) {
                return "vegetation-test-world";
            }
            throw new UnsupportedOperationException(method.toString());
        };
        return (WorldGenLevel) Proxy.newProxyInstance(
                WorldGenLevel.class.getClassLoader(), new Class<?>[]{WorldGenLevel.class}, handler);
    }

    private static PalettedContainerFactory containerFactory() {
        Strategy<BlockState> blockStates = Strategy.createForBlockStates(Block.BLOCK_STATE_REGISTRY);
        BlockState air = Blocks.AIR.defaultBlockState();
        IdMapper<Holder<Biome>> biomeIds = new IdMapper<>();
        Holder<Biome> defaultBiome = Holder.direct((Biome) null);
        biomeIds.add(defaultBiome);
        Strategy<Holder<Biome>> biomes = Strategy.createForBiomes(biomeIds);
        Codec<Holder<Biome>> biomeCodec = Codec.STRING.xmap(
                name -> defaultBiome, holder -> "iris-test-biome");
        return new PalettedContainerFactory(
                blockStates,
                air,
                PalettedContainer.codecRW(BlockState.CODEC, blockStates, air),
                biomes,
                defaultBiome,
                PalettedContainer.codecRO(biomeCodec, biomes, defaultBiome),
                PalettedContainer.codecRW(biomeCodec, biomes, defaultBiome));
    }
}
