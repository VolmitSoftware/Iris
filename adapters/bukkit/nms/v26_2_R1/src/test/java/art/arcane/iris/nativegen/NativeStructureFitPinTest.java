package art.arcane.iris.nativegen;

import art.arcane.iris.engine.framework.NativeStartMemo;
import art.arcane.iris.engine.framework.nativefit.NativeStructureFitting;
import com.mojang.datafixers.util.Either;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.Bootstrap;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.PoolElementStructurePiece;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.TerrainAdjustment;
import net.minecraft.world.level.levelgen.structure.pieces.PiecesContainer;
import net.minecraft.world.level.levelgen.structure.pools.JigsawJunction;
import net.minecraft.world.level.levelgen.structure.pools.LegacySinglePoolElement;
import net.minecraft.world.level.levelgen.structure.pools.SinglePoolElement;
import net.minecraft.world.level.levelgen.structure.pools.StructurePoolElement;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool;
import net.minecraft.world.level.levelgen.structure.structures.DesertPyramidPiece;
import net.minecraft.world.level.levelgen.structure.structures.DesertPyramidStructure;
import net.minecraft.world.level.levelgen.structure.templatesystem.LiquidSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessorList;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import org.junit.BeforeClass;
import org.junit.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.IntBinaryOperator;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Pins the blocks the native structure fit leaves in one decorated chunk for each terrain mode, so the fit can move
 * without changing what a world generates. Every scenario builds an uneven terrain with water, caves, ores and trees,
 * starts with real pieces and inline templates, and fit records written the way ownership records persist them. It
 * then runs the chunk's fit in the generator's order (terrain fitting for every start, then each start's pieces and
 * stilts, then the foundation repair) with a fixed stand-in for the platform's piece placement, and hashes every block
 * that differs from the untouched terrain. The hashes were taken from the platform-side fit before it moved into the
 * structure module; they now run through the platform bridge and the built-in modules.
 */
public class NativeStructureFitPinTest {
    private static final long WORLD_SEED = 8675309L;
    private static final int SEA_LEVEL = 61;
    private static final int WORLD_MIN_Y = -64;
    private static final int WORLD_HEIGHT = 384;
    private static final BoundingBox CHUNK_AREA = new BoundingBox(0, WORLD_MIN_Y + 1, 0, 15, WORLD_MIN_Y + WORLD_HEIGHT - 2, 15);
    private static final String NO_STILTS = "null";
    private static final String SOURCE_TERRAIN = "{}";

    private static final Map<String, String> PINNED = Map.ofEntries(
            Map.entry("encase", "1371:5448f4c7083265eaa032022d"),
            Map.entry("source-bury", "1662:a826097850d8e5e4ebe324d7"),
            Map.entry("source-encapsulate", "4907:206093fc3b11fe5e4678a04d"),
            Map.entry("source-beard", "721:bc95c2ac5138738846f342fd"),
            Map.entry("vacuum", "667:302305ab38330cc32cd42123"),
            Map.entry("carve", "11573:7c238685fe13c6a7c115863c"),
            Map.entry("stilts", "903:9c949d85eb9882f87f478478")
    );

    @BeforeClass
    public static void bootstrapMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    public void everyScenarioLeavesThePinnedBlocks() throws Exception {
        Map<String, String> actual = new LinkedHashMap<>();
        actual.put("encase", fit(encase()));
        actual.put("source-bury", fit(sourceBury()));
        actual.put("source-encapsulate", fit(sourceEncapsulate()));
        actual.put("source-beard", fit(sourceBeard()));
        actual.put("vacuum", fit(vacuum()));
        actual.put("carve", fit(carve()));
        actual.put("stilts", fit(stilts()));
        StringBuilder report = new StringBuilder();
        actual.forEach((name, hash) -> report.append("            Map.entry(\"").append(name)
                .append("\", \"").append(hash).append("\"),\n"));
        assertEquals("Pinned fit hashes changed:\n" + report, PINNED, actual);
    }

    @Test
    public void everyScenarioChangesTheTerrain() throws Exception {
        for (Scenario scenario : List.of(encase(), sourceBury(), sourceEncapsulate(), sourceBeard(), vacuum(), carve(), stilts())) {
            String hash = fit(scenario);
            assertTrue(scenario.name() + " left the terrain untouched", !hash.startsWith("0:"));
        }
    }

    private static String fit(Scenario scenario) throws Exception {
        TerrainWorld terrain = new TerrainWorld();
        WorldGenLevel world = terrain.level();
        fitChunk(world, scenario);
        return terrain.changedBlocksHash(CHUNK_AREA);
    }

    /**
     * The chunk's fit in the order the generators run it, through the platform bridge and the built-in modules: the
     * fit before any piece, then each start's pieces ({@link #placePieces}) and its fit after them.
     */
    private static void fitChunk(WorldGenLevel world, Scenario scenario) {
        List<NativeStructureFitBridge.FitTarget> targets = new ArrayList<>();
        for (FitTarget target : scenario.targets()) {
            targets.add(new NativeStructureFitBridge.FitTarget(target.structureId(), target.start(),
                    new NativeStartMemo(target.terrainJson(), target.stiltJson())));
        }
        NativeStructureFitting.Session fit = BuiltInFitting.FITTING.beforePieces(
                NativeStructureFitBridge.chunk(world, CHUNK_AREA, targets, TerrainWorld::surfaceY));
        for (int index = 0; index < targets.size(); index++) {
            placePieces(world, scenario.targets().get(index));
            fit.afterPieces(index);
        }
    }

    /** The built-in modules load once for every scenario. */
    private static final class BuiltInFitting {
        private static final NativeStructureFitting FITTING = NativeStructureFitting.builtInModules();
    }

    /**
     * A fixed stand-in for the platform placing a start's pieces in the chunk: template pieces write their template
     * blocks turned with the piece (jigsaws as air, structure voids not at all), other pieces a sandstone floor and
     * walls. Only blocks inside the chunk are written, as the platform clips them.
     */
    private static void placePieces(WorldGenLevel world, FitTarget target) {
        BlockPos.MutableBlockPos position = new BlockPos.MutableBlockPos();
        for (StructurePiece piece : target.start().getPieces()) {
            List<StructureTemplate.StructureBlockInfo> blocks = target.templates().get(piece);
            if (blocks == null) {
                placeShell(world, piece.getBoundingBox(), position);
                continue;
            }
            PoolElementStructurePiece poolPiece = (PoolElementStructurePiece) piece;
            StructurePlaceSettings settings = new StructurePlaceSettings().setRotation(poolPiece.getRotation());
            for (StructureTemplate.StructureBlockInfo block : blocks) {
                if (block.state().is(Blocks.STRUCTURE_VOID)) {
                    continue;
                }
                BlockPos at = poolPiece.getPosition().offset(StructureTemplate.calculateRelativePosition(settings, block.pos()));
                if (!CHUNK_AREA.isInside(at)) {
                    continue;
                }
                BlockState state = block.state().is(Blocks.JIGSAW)
                        ? Blocks.AIR.defaultBlockState() : block.state().rotate(poolPiece.getRotation());
                world.setBlock(position.set(at), state, 2);
            }
        }
    }

    private static void placeShell(WorldGenLevel world, BoundingBox box, BlockPos.MutableBlockPos position) {
        for (int x = box.minX(); x <= box.maxX(); x++) {
            for (int z = box.minZ(); z <= box.maxZ(); z++) {
                for (int y = box.minY(); y <= Math.min(box.maxY(), box.minY() + 3); y++) {
                    boolean wall = x == box.minX() || x == box.maxX() || z == box.minZ() || z == box.maxZ();
                    if ((y == box.minY() || wall) && CHUNK_AREA.isInside(position.set(x, y, z))) {
                        world.setBlock(position, Blocks.SANDSTONE.defaultBlockState(), 2);
                    }
                }
            }
        }
    }

    private static Scenario encase() {
        return new Scenario("encase", List.of(
                pyramid("minecraft:stronghold", GenerationStep.Decoration.STRONGHOLDS, TerrainAdjustment.BURY, 2, 2, 14,
                        NO_STILTS, "{\"mode\":\"ENCASE\",\"horizontalPadding\":2,\"ceilingPadding\":2,\"floorPadding\":1}"),
                pyramid("minecraft:trial_chambers", GenerationStep.Decoration.UNDERGROUND_STRUCTURES, TerrainAdjustment.ENCAPSULATE,
                        4, -12, -8, NO_STILTS, "{\"mode\":\"ENCASE\"}")));
    }

    private static Scenario sourceBury() {
        TemplateStart start = new TemplateStart("minecraft:ancient_city", GenerationStep.Decoration.UNDERGROUND_STRUCTURES,
                TerrainAdjustment.BURY);
        PoolElementStructurePiece room = start.single(room(7, 5, 7), new BoundingBox(2, 30, 2, 8, 34, 8), 1, Rotation.NONE);
        room.addJunction(new JigsawJunction(5, 31, 9, 0, StructureTemplatePool.Projection.RIGID));
        start.single(path(3, 5), new BoundingBox(6, 31, 9, 8, 31, 13), 0, Rotation.NONE,
                StructureTemplatePool.Projection.TERRAIN_MATCHING);
        start.legacy(airPocket(4, 4, 4), new BoundingBox(10, 28, 10, 13, 31, 13), 1, Rotation.NONE);
        start.single(room(5, 6, 4), new BoundingBox(1, 58, 11, 5, 63, 14), 1, Rotation.NONE);
        return new Scenario("source-bury", List.of(start.target(NO_STILTS, SOURCE_TERRAIN)));
    }

    private static Scenario sourceEncapsulate() {
        return new Scenario("source-encapsulate", List.of(
                pyramid("minecraft:ocean_ruin_cold", GenerationStep.Decoration.SURFACE_STRUCTURES, TerrainAdjustment.ENCAPSULATE,
                        -3, 1, 55, NO_STILTS, SOURCE_TERRAIN)));
    }

    private static Scenario sourceBeard() {
        TemplateStart village = new TemplateStart("minecraft:village_plains", GenerationStep.Decoration.SURFACE_STRUCTURES,
                TerrainAdjustment.BEARD_THIN);
        PoolElementStructurePiece house = village.single(room(6, 6, 6), new BoundingBox(1, 64, 1, 6, 69, 6), 1, Rotation.NONE);
        house.addJunction(new JigsawJunction(7, 63, 3, 0, StructureTemplatePool.Projection.TERRAIN_MATCHING));
        PoolElementStructurePiece road = village.single(path(3, 8), new BoundingBox(7, 63, 2, 9, 63, 9), 0, Rotation.NONE,
                StructureTemplatePool.Projection.TERRAIN_MATCHING);
        road.addJunction(new JigsawJunction(8, 63, 10, 0, StructureTemplatePool.Projection.RIGID));
        village.single(room(5, 5, 5), new BoundingBox(9, 60, 9, 13, 64, 13), 1, Rotation.NONE);
        village.single(floor(3, 3), new BoundingBox(10, 62, 10, 12, 66, 12), 1, Rotation.NONE);
        village.legacy(airPocket(3, 3, 3), new BoundingBox(2, 65, 11, 4, 67, 13), 1, Rotation.NONE);
        return new Scenario("source-beard", List.of(
                village.target(NO_STILTS, SOURCE_TERRAIN),
                pyramid("minecraft:desert_pyramid", GenerationStep.Decoration.SURFACE_STRUCTURES, TerrainAdjustment.BEARD_BOX,
                        -14, 9, 61, NO_STILTS, SOURCE_TERRAIN)));
    }

    private static Scenario vacuum() {
        TemplateStart village = new TemplateStart("minecraft:village_taiga", GenerationStep.Decoration.SURFACE_STRUCTURES,
                TerrainAdjustment.NONE);
        PoolElementStructurePiece houseA = village.single(room(5, 5, 5), new BoundingBox(2, 68, 2, 6, 72, 6), 1, Rotation.NONE);
        houseA.addJunction(new JigsawJunction(7, 66, 6, 0, StructureTemplatePool.Projection.RIGID));
        village.single(voidedRoom(4, 4, 4), new BoundingBox(9, 65, 8, 12, 68, 11), 1, Rotation.CLOCKWISE_90);
        village.single(path(2, 6), new BoundingBox(7, 64, 1, 8, 64, 6), 0, Rotation.NONE,
                StructureTemplatePool.Projection.TERRAIN_MATCHING);
        village.single(room(4, 3, 4), new BoundingBox(13, 70, 12, 16, 72, 15), 1, Rotation.NONE);
        return new Scenario("vacuum", List.of(
                village.target(NO_STILTS, "{\"mode\":\"VACUUM\"}"),
                pyramid("minecraft:igloo", GenerationStep.Decoration.SURFACE_STRUCTURES, TerrainAdjustment.BEARD_THIN,
                        8, -10, 64, NO_STILTS, "{\"mode\":\"PRESERVE\"}")));
    }

    private static Scenario carve() {
        TemplateStart eroded = new TemplateStart("minecraft:mineshaft", GenerationStep.Decoration.UNDERGROUND_STRUCTURES,
                TerrainAdjustment.NONE);
        eroded.single(room(6, 5, 6), new BoundingBox(3, 2, 3, 8, 6, 8), 1, Rotation.NONE);
        eroded.single(voidedRoom(4, 4, 4), new BoundingBox(9, 4, 2, 12, 7, 5), 1, Rotation.NONE);
        return new Scenario("carve", List.of(
                eroded.target(NO_STILTS, "{\"mode\":\"FORCE_CARVE\",\"shape\":\"ERODED\",\"horizontalPadding\":6,"
                        + "\"ceilingPadding\":4,\"floorPadding\":1,\"erosionStrength\":0.9,\"erosionFrequency\":0.08,\"lobeStrength\":0.7}"),
                pyramid("minecraft:buried_treasure", GenerationStep.Decoration.UNDERGROUND_STRUCTURES, TerrainAdjustment.NONE,
                        -6, 4, 30, NO_STILTS, "{\"mode\":\"BORE\",\"horizontalPadding\":2,\"ceilingPadding\":3}"),
                pyramid("minecraft:trail_ruins", GenerationStep.Decoration.UNDERGROUND_STRUCTURES, TerrainAdjustment.NONE,
                        2, -9, -40, NO_STILTS, "{\"mode\":\"FORCE_CARVE\",\"shape\":\"ROUNDED\",\"horizontalPadding\":3,"
                                + "\"ceilingPadding\":2,\"floorPadding\":2}"),
                pyramid("minecraft:swamp_hut", GenerationStep.Decoration.UNDERGROUND_STRUCTURES, TerrainAdjustment.NONE,
                        -10, -2, 44, NO_STILTS, "{\"mode\":\"FORCE_CARVE\",\"horizontalPadding\":1,\"floorPadding\":1}")));
    }

    private static Scenario stilts() {
        TemplateStart hut = new TemplateStart("minecraft:pillager_outpost", GenerationStep.Decoration.SURFACE_STRUCTURES,
                TerrainAdjustment.NONE);
        hut.single(room(5, 4, 5), new BoundingBox(9, 71, 1, 13, 74, 5), 1, Rotation.NONE);
        hut.single(floor(4, 4), new BoundingBox(1, 69, 10, 4, 72, 13), 1, Rotation.COUNTERCLOCKWISE_90);
        return new Scenario("stilts", List.of(
                pyramid("minecraft:jungle_pyramid", GenerationStep.Decoration.SURFACE_STRUCTURES, TerrainAdjustment.NONE,
                        -12, -14, 70, "{\"maxDepth\":16,\"spacing\":2,\"palette\":null}", "{\"mode\":\"PRESERVE\"}"),
                pyramid("minecraft:stronghold", GenerationStep.Decoration.STRONGHOLDS, TerrainAdjustment.NONE,
                        -2, -3, 24, "{\"maxDepth\":6,\"spacing\":1,\"palette\":null}", "{\"mode\":\"PRESERVE\"}"),
                hut.target("{\"maxDepth\":64,\"spacing\":1,\"palette\":null}", "{\"mode\":\"VACUUM\"}")));
    }

    private static FitTarget pyramid(String structureId, GenerationStep.Decoration step, TerrainAdjustment adjustment,
                                     int x, int z, int minY, String stiltJson, String terrainJson) {
        StructurePiece piece = new DesertPyramidPiece(RandomSource.create(7L), x, z);
        piece.move(0, minY - piece.getBoundingBox().minY(), 0);
        StructureStart start = new StructureStart(structure(step, adjustment), new ChunkPos(0, 0), 0,
                new PiecesContainer(List.of(piece)));
        return new FitTarget(structureId, start, Map.of(), stiltJson, terrainJson);
    }

    private static Structure structure(GenerationStep.Decoration step, TerrainAdjustment adjustment) {
        return new DesertPyramidStructure(new Structure.StructureSettings(HolderSet.empty(), Map.of(), step, adjustment));
    }

    /** A room: a stone floor, plank walls with a doorway and a glass window, air inside, a slab roof. */
    private static List<StructureTemplate.StructureBlockInfo> room(int width, int height, int depth) {
        List<StructureTemplate.StructureBlockInfo> blocks = new ArrayList<>();
        for (int x = 0; x < width; x++) {
            for (int z = 0; z < depth; z++) {
                for (int y = 0; y < height; y++) {
                    boolean wall = x == 0 || z == 0 || x == width - 1 || z == depth - 1;
                    BlockState state;
                    if (y == 0) {
                        state = Blocks.COBBLESTONE.defaultBlockState();
                    } else if (y == height - 1) {
                        state = Blocks.OAK_SLAB.defaultBlockState();
                    } else if (wall && x == width / 2 && z == 0 && y < 3) {
                        state = Blocks.AIR.defaultBlockState();
                    } else if (wall) {
                        state = y == 2 && z == depth / 2 ? Blocks.GLASS.defaultBlockState() : Blocks.OAK_PLANKS.defaultBlockState();
                    } else {
                        state = Blocks.AIR.defaultBlockState();
                    }
                    blocks.add(block(x, y, z, state));
                }
            }
        }
        return blocks;
    }

    /** A room whose middle columns are structure void and whose corner holds a jigsaw. */
    private static List<StructureTemplate.StructureBlockInfo> voidedRoom(int width, int height, int depth) {
        List<StructureTemplate.StructureBlockInfo> blocks = new ArrayList<>();
        for (StructureTemplate.StructureBlockInfo block : room(width, height, depth)) {
            BlockPos pos = block.pos();
            boolean middle = pos.getX() > 0 && pos.getX() < width - 1 && pos.getZ() > 0 && pos.getZ() < depth - 1;
            if (middle && pos.getY() > 0) {
                blocks.add(block(pos.getX(), pos.getY(), pos.getZ(), Blocks.STRUCTURE_VOID.defaultBlockState()));
            } else if (pos.getX() == 0 && pos.getZ() == 0 && pos.getY() == 0) {
                blocks.add(block(0, 0, 0, Blocks.JIGSAW.defaultBlockState()));
            } else {
                blocks.add(block);
            }
        }
        return blocks;
    }

    private static List<StructureTemplate.StructureBlockInfo> path(int width, int depth) {
        List<StructureTemplate.StructureBlockInfo> blocks = new ArrayList<>();
        for (int x = 0; x < width; x++) {
            for (int z = 0; z < depth; z++) {
                blocks.add(block(x, 0, z, Blocks.DIRT_PATH.defaultBlockState()));
            }
        }
        return blocks;
    }

    private static List<StructureTemplate.StructureBlockInfo> floor(int width, int depth) {
        List<StructureTemplate.StructureBlockInfo> blocks = new ArrayList<>();
        for (int x = 0; x < width; x++) {
            for (int z = 0; z < depth; z++) {
                blocks.add(block(x, 0, z, (x + z) % 2 == 0 ? Blocks.STONE_BRICKS.defaultBlockState() : Blocks.MOSSY_COBBLESTONE.defaultBlockState()));
            }
        }
        return blocks;
    }

    private static List<StructureTemplate.StructureBlockInfo> airPocket(int width, int height, int depth) {
        List<StructureTemplate.StructureBlockInfo> blocks = new ArrayList<>();
        for (int x = 0; x < width; x++) {
            for (int z = 0; z < depth; z++) {
                for (int y = 0; y < height; y++) {
                    blocks.add(block(x, y, z, y == 0 && x == 0 ? Blocks.BRICKS.defaultBlockState() : Blocks.AIR.defaultBlockState()));
                }
            }
        }
        return blocks;
    }

    private static StructureTemplate.StructureBlockInfo block(int x, int y, int z, BlockState state) {
        return new StructureTemplate.StructureBlockInfo(new BlockPos(x, y, z), state, null);
    }

    private static StructureTemplate template(List<StructureTemplate.StructureBlockInfo> blocks) {
        try {
            Constructor<StructureTemplate.Palette> constructor =
                    StructureTemplate.Palette.class.getDeclaredConstructor(List.class);
            if (!constructor.trySetAccessible()) {
                throw new IllegalStateException("StructureTemplate.Palette is not accessible");
            }
            StructureTemplate template = new StructureTemplate();
            template.palettes.add(constructor.newInstance(blocks));
            return template;
        } catch (ReflectiveOperationException error) {
            throw new IllegalStateException(error);
        }
    }

    private record Scenario(String name, List<FitTarget> targets) {
    }

    /** A start in the chunk with its fit as an ownership record persists it, and the template blocks of its pieces. */
    private record FitTarget(String structureId, StructureStart start,
                             Map<StructurePiece, List<StructureTemplate.StructureBlockInfo>> templates,
                             String stiltJson, String terrainJson) {
    }

    /** Builds a jigsaw start from template pieces, remembering each piece's template blocks for the placement stand-in. */
    private static final class TemplateStart {
        private final String structureId;
        private final Structure structure;
        private final List<StructurePiece> pieces = new ArrayList<>();
        private final Map<StructurePiece, List<StructureTemplate.StructureBlockInfo>> templates = new IdentityHashMap<>();

        private TemplateStart(String structureId, GenerationStep.Decoration step, TerrainAdjustment adjustment) {
            this.structureId = structureId;
            this.structure = structure(step, adjustment);
        }

        PoolElementStructurePiece single(List<StructureTemplate.StructureBlockInfo> blocks, BoundingBox bounds,
                                         int groundLevelDelta, Rotation rotation) {
            return single(blocks, bounds, groundLevelDelta, rotation, StructureTemplatePool.Projection.RIGID);
        }

        PoolElementStructurePiece single(List<StructureTemplate.StructureBlockInfo> blocks, BoundingBox bounds,
                                         int groundLevelDelta, Rotation rotation,
                                         StructureTemplatePool.Projection projection) {
            return add(new InlineSinglePoolElement(template(blocks), projection), blocks, bounds, groundLevelDelta, rotation);
        }

        PoolElementStructurePiece legacy(List<StructureTemplate.StructureBlockInfo> blocks, BoundingBox bounds,
                                         int groundLevelDelta, Rotation rotation) {
            return add(new InlineLegacyPoolElement(template(blocks)), blocks, bounds, groundLevelDelta, rotation);
        }

        private PoolElementStructurePiece add(StructurePoolElement element, List<StructureTemplate.StructureBlockInfo> blocks,
                                              BoundingBox bounds, int groundLevelDelta, Rotation rotation) {
            PoolElementStructurePiece piece = new PoolElementStructurePiece(null, element,
                    new BlockPos(bounds.minX(), bounds.minY(), bounds.minZ()), groundLevelDelta, rotation, bounds,
                    LiquidSettings.APPLY_WATERLOGGING);
            pieces.add(piece);
            templates.put(piece, blocks);
            return piece;
        }

        FitTarget target(String stiltJson, String terrainJson) {
            StructureStart start = new StructureStart(structure, new ChunkPos(0, 0), 0, new PiecesContainer(List.copyOf(pieces)));
            return new FitTarget(structureId, start, templates, stiltJson, terrainJson);
        }
    }

    private static final class InlineSinglePoolElement extends SinglePoolElement {
        private InlineSinglePoolElement(StructureTemplate template, StructureTemplatePool.Projection projection) {
            super(Either.right(template), Holder.direct(new StructureProcessorList(List.of())), projection,
                    Optional.<LiquidSettings>empty());
        }
    }

    private static final class InlineLegacyPoolElement extends LegacySinglePoolElement {
        private InlineLegacyPoolElement(StructureTemplate template) {
            super(Either.right(template), Holder.direct(new StructureProcessorList(List.of())),
                    StructureTemplatePool.Projection.RIGID, Optional.<LiquidSettings>empty());
        }
    }

    /**
     * An uneven terrain computed per block, with the fit's writes kept on top: grass over dirt over stone (deepslate
     * below 0), a sea at {@link #SEA_LEVEL}, a flooded cave and a dry cave, scattered iron ore and two trees.
     */
    private static final class TerrainWorld {
        private final Map<BlockPos, BlockState> written = new HashMap<>();

        static int surfaceY(int x, int z) {
            return 62 + (int) Math.round(3D * Math.sin(x * 0.35D) + 2D * Math.cos(z * 0.27D));
        }

        static BlockState natural(int x, int y, int z) {
            int surface = surfaceY(x, z);
            if (insideSphere(x, y, z, 8, 20, 8, 6)) {
                return y <= 17 ? Blocks.WATER.defaultBlockState() : Blocks.AIR.defaultBlockState();
            }
            if (insideSphere(x, y, z, 4, -6, 11, 4)) {
                return Blocks.AIR.defaultBlockState();
            }
            BlockState tree = tree(x, y, z);
            if (tree != null) {
                return tree;
            }
            if (y > surface) {
                return y <= SEA_LEVEL ? Blocks.WATER.defaultBlockState() : Blocks.AIR.defaultBlockState();
            }
            if (y == surface) {
                return surface < SEA_LEVEL ? Blocks.SAND.defaultBlockState() : Blocks.GRASS_BLOCK.defaultBlockState();
            }
            if (y >= surface - 3) {
                return Blocks.DIRT.defaultBlockState();
            }
            if (Math.floorMod(x + z, 11) == 0 && Math.floorMod(y, 7) == 0) {
                return Blocks.IRON_ORE.defaultBlockState();
            }
            return y < 0 ? Blocks.DEEPSLATE.defaultBlockState() : Blocks.STONE.defaultBlockState();
        }

        private static BlockState tree(int x, int y, int z) {
            for (int[] trunk : new int[][]{{12, 3}, {5, 13}}) {
                int base = surfaceY(trunk[0], trunk[1]);
                if (base < SEA_LEVEL) {
                    continue;
                }
                if (x == trunk[0] && z == trunk[1] && y > base && y <= base + 4) {
                    return Blocks.OAK_LOG.defaultBlockState();
                }
                if (Math.abs(x - trunk[0]) <= 1 && Math.abs(z - trunk[1]) <= 1 && y >= base + 4 && y <= base + 5
                        && !(x == trunk[0] && z == trunk[1] && y == base + 4)) {
                    return Blocks.OAK_LEAVES.defaultBlockState();
                }
            }
            return null;
        }

        private static boolean insideSphere(int x, int y, int z, int cx, int cy, int cz, int radius) {
            int dx = x - cx;
            int dy = y - cy;
            int dz = z - cz;
            return dx * dx + dy * dy + dz * dz <= radius * radius;
        }

        BlockState get(int x, int y, int z) {
            BlockState state = written.get(new BlockPos(x, y, z));
            return state == null ? natural(x, y, z) : state;
        }

        /** The heightmap the platform's processors read: one above the highest block that is not air. */
        int heightmapY(int x, int z) {
            for (int y = CHUNK_AREA.maxY(); y >= CHUNK_AREA.minY() - 1; y--) {
                if (!get(x, y, z).isAir()) {
                    return y + 1;
                }
            }
            return CHUNK_AREA.minY() - 1;
        }

        WorldGenLevel level() {
            InvocationHandler handler = (proxy, method, arguments) -> switch (method.getName()) {
                case "getBlockState" -> {
                    BlockPos position = (BlockPos) arguments[0];
                    yield get(position.getX(), position.getY(), position.getZ());
                }
                case "setBlock" -> {
                    written.put(((BlockPos) arguments[0]).immutable(), (BlockState) arguments[1]);
                    yield true;
                }
                case "getSeed" -> WORLD_SEED;
                case "getMinY" -> WORLD_MIN_Y;
                case "getHeight" -> arguments == null ? WORLD_HEIGHT : heightmapY((int) arguments[1], (int) arguments[2]);
                case "getLevel" -> null;
                case "holderLookup" -> BuiltInRegistries.BLOCK;
                case "hashCode" -> System.identityHashCode(proxy);
                case "equals" -> proxy == arguments[0];
                case "toString" -> "fit-pin-world";
                default -> throw new UnsupportedOperationException(method.toString());
            };
            return (WorldGenLevel) Proxy.newProxyInstance(
                    WorldGenLevel.class.getClassLoader(), new Class<?>[]{WorldGenLevel.class}, handler);
        }

        String changedBlocksHash(BoundingBox area) throws Exception {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            int changed = 0;
            for (int x = area.minX(); x <= area.maxX(); x++) {
                for (int z = area.minZ(); z <= area.maxZ(); z++) {
                    for (int y = area.minY(); y <= area.maxY(); y++) {
                        BlockState state = get(x, y, z);
                        if (state == natural(x, y, z)) {
                            continue;
                        }
                        changed++;
                        digest.update((x + "," + y + "," + z + "=" + state + "\n").getBytes(StandardCharsets.UTF_8));
                    }
                }
            }
            return changed + ":" + HexFormat.of().formatHex(digest.digest()).substring(0, 24);
        }
    }
}
