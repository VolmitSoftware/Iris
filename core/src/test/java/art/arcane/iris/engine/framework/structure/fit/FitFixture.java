package art.arcane.iris.engine.framework.structure.fit;

import art.arcane.iris.engine.framework.structure.NativeStructureFit;
import com.google.gson.Gson;
import com.volmit.block.BlockFace;
import com.volmit.block.BlockState;
import com.volmit.block.BlockTrait;
import com.volmit.mantle.BlockBox;
import com.volmit.resource.ResourceId;
import com.volmit.vanilla.PieceKind;
import com.volmit.vanilla.StartMemo;
import com.volmit.vanilla.StructureFit;
import com.volmit.vanilla.StructureStep;
import com.volmit.vanilla.TemplateCells;
import com.volmit.vanilla.TerrainAdaptation;
import com.volmit.world.BlockAccess;
import com.volmit.world.BlockPosition;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Structure fit components outside a platform: an in-memory world in world heights whose block answers follow the
 * platform's for the blocks the tests use, and pieces whose template cells the test states the way the platform would
 * process them.
 */
final class FitFixture {
    static final int BOTTOM_Y = -64;
    static final int TOP_Y = 320;
    static final BlockState AIR = state("air");
    static final BlockState STONE = state("stone");
    static final BlockState DIRT = state("dirt");
    static final BlockState GRASS = state("grass_block");
    static final BlockState COBBLESTONE = state("cobblestone");
    static final BlockState WATER = state("water");
    static final BlockState OAK_LOG = state("oak_log");
    static final BlockState OAK_LEAVES = state("oak_leaves");
    private static final Gson JSON = new Gson();
    private static final Set<String> NOT_SOLID = Set.of("air", "cave_air", "water", "lava", "short_grass", "dandelion",
            "structure_void", "vine", "sculk_vein");
    private static final Map<String, Set<String>> TAGS = Map.of(
            "minecraft:logs", Set.of("oak_log", "spruce_log", "oak_wood"),
            "minecraft:leaves", Set.of("oak_leaves", "spruce_leaves"),
            "minecraft:base_stone_overworld", Set.of("stone", "deepslate", "tuff", "granite", "diorite", "andesite"),
            "minecraft:dirt", Set.of("dirt", "grass_block", "podzol", "coarse_dirt"),
            "minecraft:sand", Set.of("sand", "red_sand"));

    private FitFixture() {
    }

    static BlockState state(String path) {
        return new BlockState("minecraft:" + path);
    }

    /** A world of blocks keyed by world position, air where nothing is set. */
    static final class World implements BlockAccess {
        private final Map<BlockPosition, BlockState> blocks = new HashMap<>();
        private final Box bounds;

        World(Box columns) {
            this.bounds = new Box(columns.minX(), BOTTOM_Y, columns.minZ(), columns.maxX(), TOP_Y - 1, columns.maxZ());
        }

        void put(int x, int y, int z, BlockState state) {
            blocks.put(new BlockPosition(x, y, z), state);
        }

        BlockState at(int x, int y, int z) {
            return blocks.getOrDefault(new BlockPosition(x, y, z), AIR);
        }

        Map<BlockPosition, BlockState> snapshot() {
            return Map.copyOf(blocks);
        }

        /** Grass at {@code surfaceY} over a dirt layer, in every column of {@code area}. */
        World flat(Box area, int surfaceY) {
            for (int x = area.minX(); x <= area.maxX(); x++) {
                for (int z = area.minZ(); z <= area.maxZ(); z++) {
                    put(x, surfaceY - 1, z, DIRT);
                    put(x, surfaceY, z, GRASS);
                }
            }
            return this;
        }

        FitBlocks fitBlocks() {
            return new FitBlocks(this, BOTTOM_Y);
        }

        @Override
        public BlockBox bounds() {
            return bounds.toApi(BOTTOM_Y);
        }

        @Override
        public BlockState get(int x, int y, int z) {
            return at(x, y + BOTTOM_Y, z);
        }

        @Override
        public void column(int x, int z, int fromY, BlockState[] into) {
            for (int index = 0; index < into.length; index++) {
                into[index] = get(x, fromY + index, z);
            }
        }

        @Override
        public void set(int x, int y, int z, BlockState state) {
            put(x, y + BOTTOM_Y, z, state);
        }

        @Override
        public boolean is(BlockState state, BlockTrait trait) {
            String path = state.material().path();
            return switch (trait) {
                case AIR -> path.endsWith("air");
                case SOLID -> !NOT_SOLID.contains(path);
                case FLUID -> path.equals("water") || path.equals("lava")
                        || "true".equals(state.properties().get("waterlogged"));
                case LOG -> path.endsWith("_log") || path.endsWith("_wood") || path.endsWith("_stem");
                case LEAVES -> path.endsWith("_leaves");
                default -> throw new UnsupportedOperationException(trait.name());
            };
        }

        @Override
        public boolean inTag(BlockState state, ResourceId tag) {
            return TAGS.getOrDefault(tag.toString(), Set.of()).contains(state.material().path());
        }

        @Override
        public boolean sturdy(int x, int y, int z, BlockFace face) {
            BlockState state = get(x, y, z);
            return is(state, BlockTrait.SOLID) && !is(state, BlockTrait.LEAVES);
        }

        @Override
        public BlockState fluid(BlockState state) {
            return is(state, BlockTrait.FLUID) ? WATER : AIR;
        }
    }

    /** A piece; its cells are the processed template blocks the test states (null for a block a processor removed). */
    static final class Piece implements StructureFit.Piece {
        private final Box box;
        private final PieceKind kind;
        private final int groundLevelDelta;
        private final List<BlockPosition> junctions = new ArrayList<>();
        private final List<Cell> cells = new ArrayList<>();
        private final List<BlockPosition> legacyAir = new ArrayList<>();
        private boolean template;

        Piece(Box box, PieceKind kind, int groundLevelDelta) {
            this.box = box;
            this.kind = kind;
            this.groundLevelDelta = groundLevelDelta;
        }

        static Piece rigid(Box box, int groundLevelDelta) {
            return new Piece(box, PieceKind.RIGID, groundLevelDelta);
        }

        static Piece fixed(Box box) {
            return new Piece(box, PieceKind.FIXED, 0);
        }

        Piece cell(int x, int y, int z, BlockState state) {
            template = true;
            cells.add(new Cell(x, y, z, state));
            return this;
        }

        Piece junction(int x, int groundY, int z) {
            junctions.add(new BlockPosition(x, groundY - BOTTOM_Y, z));
            return this;
        }

        Piece legacyAir(int x, int y, int z) {
            legacyAir.add(new BlockPosition(x, y - BOTTOM_Y, z));
            return this;
        }

        @Override
        public BlockBox box() {
            return box.toApi(BOTTOM_Y);
        }

        @Override
        public PieceKind kind() {
            return kind;
        }

        @Override
        public int groundLevelDelta() {
            return groundLevelDelta;
        }

        @Override
        public List<BlockPosition> junctions() {
            return List.copyOf(junctions);
        }

        @Override
        public TemplateCells cells(BlockBox region) {
            if (!template) {
                return TemplateCells.UNRESOLVED;
            }
            Box worldRegion = Box.of(region, BOTTOM_Y);
            List<Cell> inside = cells.stream().filter(cell -> worldRegion.contains(cell.x(), cell.y(), cell.z())).toList();
            int[] xs = inside.stream().mapToInt(Cell::x).toArray();
            int[] ys = inside.stream().mapToInt(cell -> cell.y() - BOTTOM_Y).toArray();
            int[] zs = inside.stream().mapToInt(Cell::z).toArray();
            BlockState[] states = inside.stream().map(Cell::state).toArray(BlockState[]::new);
            return TemplateCells.of(xs, ys, zs, states, cells.size() * 2);
        }

        @Override
        public void columns(StructureFit.ColumnVisitor visitor) {
            for (int z = box.minZ(); z <= box.maxZ(); z++) {
                for (int x = box.minX(); x <= box.maxX(); x++) {
                    visitor.column(x, z, box.minY() - BOTTOM_Y, box.maxY() - BOTTOM_Y);
                }
            }
        }

        @Override
        public List<BlockPosition> legacyAir(BlockBox clip) {
            Box worldClip = Box.of(clip, BOTTOM_Y);
            return legacyAir.stream()
                    .filter(air -> worldClip.contains(air.x(), air.y() + BOTTOM_Y, air.z()))
                    .toList();
        }
    }

    private record Cell(int x, int y, int z, BlockState state) {
    }

    /** A start of the structure fit, with the terrain fit JSON a memo would carry and no stilts. */
    static FitStart start(String terrainJson, TerrainAdaptation adaptation, StructureStep step, Piece... pieces) {
        return start(terrainJson, "null", adaptation, step, pieces);
    }

    static FitStart start(String terrainJson, String stiltsJson, TerrainAdaptation adaptation, StructureStep step,
                          Piece... pieces) {
        StartMemo memo = new StartMemo(terrainJson, stiltsJson);
        StructureFit.Start start = new StructureFit.Start() {
            private final Object key = new Object();

            @Override
            public ResourceId structure() {
                return ResourceId.parse("test:structure");
            }

            @Override
            public int chunkX() {
                return 0;
            }

            @Override
            public int chunkZ() {
                return 0;
            }

            @Override
            public StructureStep step() {
                return step;
            }

            @Override
            public TerrainAdaptation adaptation() {
                return adaptation;
            }

            @Override
            public Optional<StartMemo> memo() {
                return Optional.of(memo);
            }

            @Override
            public List<StructureFit.Piece> pieces() {
                return List.of(pieces);
            }

            @Override
            public Object key() {
                return key;
            }
        };
        return new FitStart(start, NativeStructureFit.of(memo), BOTTOM_Y);
    }

    static String json(Object value) {
        return JSON.toJson(value);
    }
}
