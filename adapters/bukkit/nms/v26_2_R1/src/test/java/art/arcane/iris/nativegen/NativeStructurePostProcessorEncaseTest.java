package art.arcane.iris.nativegen;

import art.arcane.iris.engine.object.IrisMaterialPalette;
import art.arcane.iris.engine.object.IrisStructureTerrain;
import art.arcane.iris.engine.object.IrisStructureTerrainMode;
import art.arcane.iris.engine.object.IrisStructureYBand;
import art.arcane.volmlib.util.math.RNG;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.Bootstrap;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.dimension.BuiltinDimensionTypes;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.PoolElementStructurePiece;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.TerrainAdjustment;
import net.minecraft.world.level.levelgen.structure.pieces.PiecesContainer;
import net.minecraft.world.level.levelgen.structure.pools.StructurePoolElement;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool;
import net.minecraft.world.level.levelgen.structure.structures.DesertPyramidPiece;
import net.minecraft.world.level.levelgen.structure.structures.DesertPyramidStructure;
import net.minecraft.world.level.levelgen.structure.templatesystem.BlockIgnoreProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.LiquidSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessorList;
import org.junit.BeforeClass;
import org.junit.Test;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.BitSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class NativeStructurePostProcessorEncaseTest {
    private static final long TEST_SEED = 1234L;

    @BeforeClass
    public static void bootstrapMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    public void encaseFillsAirAndLiquidWithoutOverwritingExistingTerrain() {
        StructureStart start = start(TerrainAdjustment.BURY, 64);
        BoundingBox bounds = start.getPieces().getFirst().getBoundingBox();
        BoundingBox area = new BoundingBox(
                bounds.minX() - 1, bounds.minY() - 1, bounds.minZ() - 1,
                bounds.maxX() + 1, bounds.maxY() + 1, bounds.maxZ() + 1);
        Map<BlockPos, BlockState> blocks = new HashMap<>();
        put(blocks, bounds.minX(), bounds.minY(), bounds.minZ(), Blocks.DIRT.defaultBlockState());
        put(blocks, bounds.maxX(), bounds.minY(), bounds.maxZ(), Blocks.WATER.defaultBlockState());

        NativeStructureTerrainIntegrator.integrateTerrain(
                world(blocks), area, "minecraft:stronghold", start,
                new IrisStructureTerrain()
                        .setMode(IrisStructureTerrainMode.ENCASE)
                        .setHorizontalPadding(1)
                        .setCeilingPadding(1)
                        .setFloorPadding(1),
                null);

        assertEquals(Blocks.DIRT.defaultBlockState(),
                state(blocks, bounds.minX(), bounds.minY(), bounds.minZ()));
        assertEquals(Blocks.STONE.defaultBlockState(),
                state(blocks, bounds.maxX(), bounds.minY(), bounds.maxZ()));
        assertEquals(Blocks.STONE.defaultBlockState(),
                state(blocks, bounds.minX() - 1, bounds.minY() - 1, bounds.minZ() - 1));
        assertEquals(Blocks.STONE.defaultBlockState(),
                state(blocks, bounds.maxX() + 1, bounds.maxY() + 1, bounds.maxZ() + 1));
    }

    @Test
    public void defaultEncasePaletteSplitsStoneAboveZeroAndDeepslateBelow() {
        StructureStart start = start(TerrainAdjustment.BURY, -1);
        BoundingBox bounds = start.getPieces().getFirst().getBoundingBox();
        Map<BlockPos, BlockState> blocks = new HashMap<>();

        NativeStructureTerrainIntegrator.integrateTerrain(
                world(blocks), bounds, "minecraft:stronghold", start,
                new IrisStructureTerrain().setMode(IrisStructureTerrainMode.ENCASE),
                null);

        assertEquals(-1, bounds.minY());
        assertEquals(Blocks.DEEPSLATE.defaultBlockState(),
                state(blocks, bounds.minX(), -1, bounds.minZ()));
        assertEquals(Blocks.STONE.defaultBlockState(),
                state(blocks, bounds.minX(), 0, bounds.minZ()));
    }

    @Test
    public void defaultEncaseMaterialFollowsVanillaDimensionTerrain() {
        assertEquals(Blocks.NETHERRACK.defaultBlockState(),
                NativeStructureTerrainIntegrator.defaultEncaseBlock(Level.NETHER, 64));
        assertEquals(Blocks.END_STONE.defaultBlockState(),
                NativeStructureTerrainIntegrator.defaultEncaseBlock(Level.END, 64));
        assertEquals(Blocks.STONE.defaultBlockState(),
                NativeStructureTerrainIntegrator.defaultEncaseBlock(Level.OVERWORLD, 64));
    }

    @Test
    public void sourceEncaseMaterialFollowsNearbyIrisTerrain() {
        Map<BlockPos, BlockState> blocks = new HashMap<>();
        BlockPos fill = new BlockPos(4, 64, 8);
        put(blocks, fill.getX() + 3, fill.getY(), fill.getZ(), Blocks.TUFF.defaultBlockState());

        assertEquals(Blocks.TUFF.defaultBlockState(),
                NativeStructureTerrainIntegrator.sourceEncaseBlock(world(blocks), fill));
    }

    @Test
    public void sourceMaterialSnapshotIgnoresPreviouslyDecoratedAdjacentChunks() {
        Map<BlockPos, BlockState> blocks = new HashMap<>();
        BlockPos fill = new BlockPos(0, 64, 8);
        put(blocks, -1, 64, 8, Blocks.OAK_PLANKS.defaultBlockState());
        put(blocks, 3, 64, 8, Blocks.TUFF.defaultBlockState());
        WorldGenLevel world = world(blocks);
        NativeStructureTerrainIntegrator.SourceTerrainSnapshot sourceTerrain =
                sourceSnapshot(world, new BoundingBox(0, 64, 0, 15, 64, 15));

        assertEquals(Blocks.TUFF.defaultBlockState(),
                NativeStructureTerrainIntegrator.sourceEncaseBlock(
                        world, fill, sourceTerrain));
    }

    @Test
    public void sourceMaterialSnapshotIsStableAcrossEarlierFillsInTheSameChunk() {
        Map<BlockPos, BlockState> blocks = new HashMap<>();
        BlockPos fill = new BlockPos(0, 64, 8);
        put(blocks, 3, 64, 8, Blocks.TUFF.defaultBlockState());
        WorldGenLevel world = world(blocks);
        NativeStructureTerrainIntegrator.SourceTerrainSnapshot sourceTerrain =
                sourceSnapshot(world, new BoundingBox(0, 64, 0, 15, 64, 15));
        put(blocks, 1, 64, 8, Blocks.OAK_PLANKS.defaultBlockState());

        assertEquals(Blocks.TUFF.defaultBlockState(),
                NativeStructureTerrainIntegrator.sourceEncaseBlock(
                        world, fill, sourceTerrain));
    }

    @Test
    public void sourceMaterialRejectsStructureBlocksAndOresBeforeDimensionFallback() {
        Map<BlockPos, BlockState> blocks = new HashMap<>();
        BlockPos fill = new BlockPos(4, -20, 8);
        put(blocks, 3, -20, 8, Blocks.OAK_PLANKS.defaultBlockState());
        put(blocks, 5, -20, 8, Blocks.STONE_BRICKS.defaultBlockState());
        put(blocks, 4, -20, 7, Blocks.IRON_ORE.defaultBlockState());

        assertEquals(Blocks.DEEPSLATE.defaultBlockState(),
                NativeStructureTerrainIntegrator.sourceEncaseBlock(world(blocks), fill));
    }

    @Test
    public void sourceMaterialSnapshotOnlyAllocatesActualFillLayers() {
        StructureStart start = start(TerrainAdjustment.BURY, 64);
        BoundingBox area = new BoundingBox(0, -2032, 0, 15, 2030, 15);
        NativeStructureTerrainIntegrator.SourceTerrainSnapshot sourceTerrain =
                NativeStructureTerrainIntegrator.captureSourceTerrain(
                        world(new HashMap<>()), area,
                        List.of(new NativeStructureTerrainIntegrator.TerrainTarget(
                                "test:bounded-snapshot", start,
                                new IrisStructureTerrain().setMode(IrisStructureTerrainMode.SOURCE))));

        assertNotNull(sourceTerrain);
        assertEquals(23 * 16 * 16, sourceTerrain.sampledCells());
    }

    @Test
    public void customDimensionKeyUsesRegisteredVanillaDimensionType() {
        ResourceKey<Level> customDimension = ResourceKey.create(
                Registries.DIMENSION, Identifier.parse("iris:custom_nether"));

        assertEquals(Blocks.NETHERRACK.defaultBlockState(),
                NativeStructureTerrainIntegrator.defaultEncaseBlock(
                        customDimension, BuiltinDimensionTypes.NETHER, null, 64));
        assertEquals(Blocks.END_STONE.defaultBlockState(),
                NativeStructureTerrainIntegrator.defaultEncaseBlock(
                        customDimension, BuiltinDimensionTypes.END, null, 64));
    }

    @Test
    public void configuredEncasePaletteReplacesTheDefaultMaterial() {
        StructureStart start = start(TerrainAdjustment.BURY, 64);
        BoundingBox bounds = start.getPieces().getFirst().getBoundingBox();
        Map<BlockPos, BlockState> blocks = new HashMap<>();
        IrisMaterialPalette palette = new IrisMaterialPalette().qclear().qadd("minecraft:tuff");

        NativeStructureTerrainIntegrator.integrateTerrain(
                world(blocks), bounds, "minecraft:stronghold", start,
                new IrisStructureTerrain()
                        .setMode(IrisStructureTerrainMode.ENCASE)
                        .setEncasePalette(palette),
                NativeStructurePostProcessorEncaseTest::tuffBlock);

        assertEquals(Blocks.TUFF.defaultBlockState(),
                state(blocks, bounds.minX(), bounds.minY(), bounds.minZ()));
    }

    @Test
    public void omittedTerrainConfigurationRetainsSourceSemantics() {
        for (TerrainAdjustment adjustment : TerrainAdjustment.values()) {
            IrisStructureTerrain resolved = NativeStructureTerrainIntegrator.resolveNativeTerrain(
                    start(adjustment, 64), null);

            assertEquals(IrisStructureTerrainMode.SOURCE, resolved.resolvedMode());
        }
    }

    @Test
    public void sourceBuryUsesTheVanillaSixByTwelveInfluenceEnvelope() {
        StructureStart start = start(TerrainAdjustment.BURY, 64);
        BoundingBox bounds = start.getPieces().getFirst().getBoundingBox();
        int groundY = bounds.minY();
        BoundingBox area = new BoundingBox(
                bounds.minX() - 7, groundY - 13, bounds.minZ(),
                bounds.maxX(), groundY + 13, bounds.maxZ());
        Map<BlockPos, BlockState> blocks = new HashMap<>();

        NativeStructureTerrainIntegrator.integrateTerrain(
                world(blocks), area, "test:bury", start,
                new IrisStructureTerrain().setMode(IrisStructureTerrainMode.SOURCE), null);

        assertEquals(Blocks.STONE.defaultBlockState(),
                state(blocks, bounds.minX() - 5, groundY, bounds.minZ()));
        assertEquals(Blocks.STONE.defaultBlockState(),
                state(blocks, bounds.minX(), groundY + 11, bounds.minZ()));
        assertEquals(Blocks.AIR.defaultBlockState(),
                state(blocks, bounds.minX() - 6, groundY, bounds.minZ()));
        assertEquals(Blocks.AIR.defaultBlockState(),
                state(blocks, bounds.minX(), groundY + 12, bounds.minZ()));
        assertEquals(Blocks.AIR.defaultBlockState(),
                state(blocks, bounds.minX() - 7, groundY, bounds.minZ()));
        assertEquals(Blocks.AIR.defaultBlockState(),
                state(blocks, bounds.minX(), groundY + 13, bounds.minZ()));
    }

    @Test
    public void sourceEncapsulateWrapsTheFullPieceBounds() {
        StructureStart start = start(TerrainAdjustment.ENCAPSULATE, 64);
        BoundingBox bounds = start.getPieces().getFirst().getBoundingBox();
        BoundingBox area = new BoundingBox(
                bounds.minX() - 13, bounds.minY(), bounds.minZ(),
                bounds.maxX(), bounds.maxY() + 13, bounds.maxZ());
        Map<BlockPos, BlockState> blocks = new HashMap<>();

        NativeStructureTerrainIntegrator.integrateTerrain(
                world(blocks), area, "test:encapsulate", start,
                new IrisStructureTerrain().setMode(IrisStructureTerrainMode.SOURCE), null);

        assertEquals(Blocks.STONE.defaultBlockState(),
                state(blocks, bounds.minX() - 11, bounds.minY(), bounds.minZ()));
        assertEquals(Blocks.STONE.defaultBlockState(),
                state(blocks, bounds.minX(), bounds.maxY() + 11, bounds.minZ()));
        assertEquals(Blocks.AIR.defaultBlockState(),
                state(blocks, bounds.minX() - 12, bounds.minY(), bounds.minZ()));
        assertEquals(Blocks.AIR.defaultBlockState(),
                state(blocks, bounds.minX(), bounds.maxY() + 12, bounds.minZ()));
        assertEquals(Blocks.AIR.defaultBlockState(),
                state(blocks, bounds.minX() - 13, bounds.minY(), bounds.minZ()));
        assertEquals(Blocks.AIR.defaultBlockState(),
                state(blocks, bounds.minX(), bounds.maxY() + 13, bounds.minZ()));
    }

    @Test
    public void sourceTerrainDensityOnlyUsesRigidPoolPieces() {
        PoolElementStructurePiece terrainMatching = poolPiece(StructureTemplatePool.Projection.TERRAIN_MATCHING);
        PoolElementStructurePiece rigid = poolPiece(StructureTemplatePool.Projection.RIGID);

        assertFalse(NativeStructureTerrainIntegrator.isSourceRigidPiece(terrainMatching));
        assertTrue(NativeStructureTerrainIntegrator.isSourceRigidPiece(rigid));
    }

    @Test
    public void sourceJunctionTerrainUsesTheStrictLowerTwelveBlockEnvelope() {
        assertTrue(NativeStructureTerrainIntegrator.insideSourceJunctionEnvelope(0, -1, 0));
        assertTrue(NativeStructureTerrainIntegrator.insideSourceJunctionEnvelope(0, -11, 0));
        assertFalse(NativeStructureTerrainIntegrator.insideSourceJunctionEnvelope(0, 0, 0));
        assertFalse(NativeStructureTerrainIntegrator.insideSourceJunctionEnvelope(0, -12, 0));
        assertFalse(NativeStructureTerrainIntegrator.insideSourceJunctionEnvelope(12, -1, 0));
    }

    @Test
    public void explicitTerrainConfigurationWinsOverAutoEncase() {
        IrisStructureTerrain configured = new IrisStructureTerrain()
                .setMode(IrisStructureTerrainMode.PRESERVE);

        assertSame(configured, NativeStructureTerrainIntegrator.resolveNativeTerrain(
                start(TerrainAdjustment.BURY, 64), configured));
    }

    @Test
    public void legacyTemplateAirIsClearedAfterEveryTerrainFillPath() {
        IrisStructureTerrain source = new IrisStructureTerrain()
                .setMode(IrisStructureTerrainMode.SOURCE);
        IrisStructureTerrain preserve = new IrisStructureTerrain()
                .setMode(IrisStructureTerrainMode.PRESERVE);
        IrisStructureTerrain encase = new IrisStructureTerrain()
                .setMode(IrisStructureTerrainMode.ENCASE);

        assertTrue(NativeStructureTerrainIntegrator.clearsLegacyTemplateAir(
                start(TerrainAdjustment.BURY, 64), source));
        assertTrue(NativeStructureTerrainIntegrator.clearsLegacyTemplateAir(
                start(TerrainAdjustment.BEARD_BOX, 64), source));
        assertTrue(NativeStructureTerrainIntegrator.clearsLegacyTemplateAir(
                start(TerrainAdjustment.NONE, 64), encase));
        assertFalse(NativeStructureTerrainIntegrator.clearsLegacyTemplateAir(
                start(TerrainAdjustment.NONE, 64), source));
        assertFalse(NativeStructureTerrainIntegrator.clearsLegacyTemplateAir(
                start(TerrainAdjustment.BURY, 64), preserve));
    }

    @Test
    public void encaseReservesTheNeighborChunkEnvelope() {
        StructureStart generated = start(TerrainAdjustment.BURY, 64);
        BoundingBox sourceBounds = generated.getBoundingBox();
        BoundingBox content = NativeStructureReferenceEnvelope.contentBounds(generated);
        IrisStructureTerrain terrain = new IrisStructureTerrain()
                .setMode(IrisStructureTerrainMode.ENCASE)
                .setHorizontalPadding(4);

        StructureStart wrapped = NativeStructureReferenceEnvelope.wrap(
                generated,
                generated.getStructure(),
                0,
                terrain);
        BoundingBox referenceBounds = NativeStructureReferenceEnvelope.referenceBounds(
                wrapped, wrapped.getStructure(), terrain);

        assertEquals(sourceBounds, wrapped.getBoundingBox());
        assertEquals(content.minX() - 4, referenceBounds.minX());
        assertEquals(content.maxX() + 4, referenceBounds.maxX());
        assertEquals(content.minZ() - 4, referenceBounds.minZ());
        assertEquals(content.maxZ() + 4, referenceBounds.maxZ());
        assertEquals(generated.getPieces().size(), wrapped.getPieces().size());
    }

    @Test
    public void referenceEnvelopeRejectsChunkCoordinateOverflow() {
        StructureStart generated = start(TerrainAdjustment.NONE, 64);
        StructureStart extreme = new StructureStart(
                generated.getStructure(), new ChunkPos(Integer.MAX_VALUE, 0), 0,
                new PiecesContainer(generated.getPieces()));

        assertThrows(IllegalStateException.class, () ->
                NativeStructureReferenceEnvelope.referenceBounds(
                        extreme, extreme.getStructure(), new IrisStructureTerrain()));
    }

    @Test
    public void explicitTerrainEnvelopeDoesNotInheritSourceAdjustment() {
        StructureStart generated = start(TerrainAdjustment.BURY, 64);
        BoundingBox content = NativeStructureReferenceEnvelope.contentBounds(generated);
        IrisStructureTerrain terrain = new IrisStructureTerrain()
                .setMode(IrisStructureTerrainMode.ENCASE)
                .setHorizontalPadding(112);

        StructureStart wrapped = NativeStructureReferenceEnvelope.wrap(
                generated, generated.getStructure(), 0, terrain);
        BoundingBox referenceBounds = NativeStructureReferenceEnvelope.referenceBounds(
                wrapped, wrapped.getStructure(), terrain);

        assertEquals(content.minX() - 112, referenceBounds.minX());
        assertEquals(content.maxX() + 112, referenceBounds.maxX());
    }

    @Test
    public void yBandRelocatesTheStartMidpointDeterministically() {
        IrisStructureYBand band = new IrisStructureYBand().setMin(-120).setMax(-20);
        StructureStart first = start(TerrainAdjustment.BURY, 64);
        StructureStart second = start(TerrainAdjustment.BURY, 64);

        NativeStructureVerticalPlacer.applyVerticalShift(
                first, -64, -256, 320, true, false, band, (x, z) -> 40);
        NativeStructureVerticalPlacer.applyVerticalShift(
                second, -64, -256, 320, true, false, band, (x, z) -> 40);

        BoundingBox bounds = first.getBoundingBox();
        assertEquals(bounds.minY(), second.getBoundingBox().minY());
        assertTrue(bounds.minY() >= -120);
        assertTrue(bounds.maxY() <= -20);

        int repeated = NativeStructureVerticalPlacer.applyVerticalShift(
                first, -64, -256, 320, true, false, band, (x, z) -> 40);
        assertEquals(0, repeated);
    }

    @Test
    public void yBandClampsWhenTheBandCannotContainTheStructure() {
        IrisStructureYBand band = new IrisStructureYBand().setMin(-50).setMax(-45);
        StructureStart start = start(TerrainAdjustment.BURY, 64);

        NativeStructureVerticalPlacer.applyVerticalShift(
                start, 0, -256, 320, true, false, band, (x, z) -> 40);

        BoundingBox bounds = start.getBoundingBox();
        int midpoint = bounds.minY() + (bounds.maxY() - bounds.minY()) / 2;
        assertTrue(midpoint >= -50);
        assertTrue(midpoint <= -45);
    }

    @Test
    public void yBandReplacesBothTheBlindShiftAndBurial() {
        IrisStructureYBand band = new IrisStructureYBand().setMin(-64).setMax(-64);
        StructureStart start = start(TerrainAdjustment.BURY, 64);
        BoundingBox bounds = start.getBoundingBox();
        int height = bounds.maxY() - bounds.minY();

        NativeStructureVerticalPlacer.applyVerticalShift(
                start, -200, -256, 320, true, false, band, (x, z) -> 40);

        assertEquals(-64 - height / 2, start.getBoundingBox().minY());
    }

    @Test
    public void preserveSourceYWinsOverTheConfiguredBand() {
        IrisStructureYBand band = new IrisStructureYBand().setMin(-120).setMax(-20);
        StructureStart start = start(TerrainAdjustment.BURY, 64);
        int minY = start.getBoundingBox().minY();

        int offset = NativeStructureVerticalPlacer.applyVerticalShift(
                start, 0, -256, 320, true, true, band, (x, z) -> 40);

        assertEquals(0, offset);
        assertEquals(minY, start.getBoundingBox().minY());
    }

    private static BlockState tuffBlock(IrisMaterialPalette palette, RNG rng, int x, int y, int z) {
        assertNotNull(palette);
        assertNotNull(rng);
        return Blocks.TUFF.defaultBlockState();
    }

    private static StructureStart start(TerrainAdjustment adjustment, int minY) {
        Structure structure = new DesertPyramidStructure(new Structure.StructureSettings(
                HolderSet.empty(), Map.of(), GenerationStep.Decoration.STRONGHOLDS, adjustment));
        StructurePiece piece = new DesertPyramidPiece(RandomSource.create(7L), 0, 0);
        piece.move(0, minY - piece.getBoundingBox().minY(), 0);
        return new StructureStart(
                structure, new ChunkPos(0, 0), 0, new PiecesContainer(List.of(piece)));
    }

    private static PoolElementStructurePiece poolPiece(StructureTemplatePool.Projection projection) {
        BlockPos position = new BlockPos(0, 64, 0);
        StructurePoolElement element = StructurePoolElement.single(
                "minecraft:empty",
                Holder.direct(new StructureProcessorList(List.of(
                        BlockIgnoreProcessor.STRUCTURE_AND_AIR))),
                LiquidSettings.APPLY_WATERLOGGING
        ).apply(projection);
        return new PoolElementStructurePiece(
                null,
                element,
                position,
                0,
                Rotation.NONE,
                new BoundingBox(position),
                LiquidSettings.APPLY_WATERLOGGING);
    }

    private static WorldGenLevel world(Map<BlockPos, BlockState> blocks) {
        InvocationHandler handler = (proxy, method, arguments) -> {
            String methodName = method.getName();
            if (methodName.equals("getBlockState")) {
                BlockPos position = (BlockPos) arguments[0];
                return state(blocks, position.getX(), position.getY(), position.getZ());
            }
            if (methodName.equals("setBlock")) {
                BlockPos position = (BlockPos) arguments[0];
                BlockState blockState = (BlockState) arguments[1];
                put(blocks, position.getX(), position.getY(), position.getZ(), blockState);
                return true;
            }
            if (methodName.equals("getSeed")) {
                return TEST_SEED;
            }
            if (methodName.equals("getLevel")) {
                return null;
            }
            if (methodName.equals("hashCode")) {
                return System.identityHashCode(proxy);
            }
            if (methodName.equals("equals")) {
                return proxy == arguments[0];
            }
            if (methodName.equals("toString")) {
                return "encase-test-world";
            }
            throw new UnsupportedOperationException(method.toString());
        };
        return (WorldGenLevel) Proxy.newProxyInstance(
                WorldGenLevel.class.getClassLoader(), new Class<?>[]{WorldGenLevel.class}, handler);
    }

    private static NativeStructureTerrainIntegrator.SourceTerrainSnapshot sourceSnapshot(
            WorldGenLevel world, BoundingBox area) {
        BitSet requiredLayers = new BitSet(area.getYSpan());
        requiredLayers.set(0, area.getYSpan());
        return NativeStructureTerrainIntegrator.SourceTerrainSnapshot.capture(
                world, area, requiredLayers);
    }

    private static void put(Map<BlockPos, BlockState> blocks,
                           int x, int y, int z, BlockState state) {
        blocks.put(new BlockPos(x, y, z), state);
    }

    private static BlockState state(Map<BlockPos, BlockState> blocks, int x, int y, int z) {
        return blocks.getOrDefault(new BlockPos(x, y, z), Blocks.AIR.defaultBlockState());
    }
}
