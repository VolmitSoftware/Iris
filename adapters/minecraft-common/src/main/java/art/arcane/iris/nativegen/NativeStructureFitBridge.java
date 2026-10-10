package art.arcane.iris.nativegen;

import art.arcane.iris.engine.framework.NativeStartMemo;
import art.arcane.iris.engine.framework.nativefit.NativeFitBlocks;
import art.arcane.iris.engine.framework.nativefit.NativeFitBox;
import art.arcane.iris.engine.framework.nativefit.NativeFitChunk;
import art.arcane.iris.engine.framework.nativefit.NativeFitPiece;
import art.arcane.iris.engine.framework.nativefit.NativeFitStart;
import art.arcane.iris.engine.framework.nativefit.NativeFitTrait;
import art.arcane.iris.engine.framework.nativefit.NativeTemplateCells;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.arguments.blocks.BlockStateParser;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.SupportType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.dimension.BuiltinDimensionTypes;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.PoolElementStructurePiece;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.pools.JigsawJunction;
import net.minecraft.world.level.levelgen.structure.pools.LegacySinglePoolElement;
import net.minecraft.world.level.levelgen.structure.pools.ListPoolElement;
import net.minecraft.world.level.levelgen.structure.pools.SinglePoolElement;
import net.minecraft.world.level.levelgen.structure.pools.StructurePoolElement;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.IntBinaryOperator;
import java.util.function.Supplier;

/**
 * The platform's side of the modules' structure fit: a decorated chunk, its starts and their pieces, read from
 * Minecraft's own structures and handed to the modules as host values ({@link NativeFitChunk}). It knows how
 * Minecraft builds pieces (templates, processors, jigsaw junctions, legacy template air) but nothing of how a module
 * fits them.
 */
public final class NativeStructureFitBridge {
    private static final int MAX_TEMPLATE_COLUMN_CELLS = 4_194_304;
    private static final List<Block> TEMPLATE_VOID_BLOCKS = List.of(Blocks.AIR, Blocks.STRUCTURE_VOID);
    private static final Map<String, TagKey<Block>> TAGS = new ConcurrentHashMap<>();

    private NativeStructureFitBridge() {
    }

    /** One start the platform places in the chunk, with the memo its ownership record kept (null for none). */
    public record FitTarget(String structureId, StructureStart start, NativeStartMemo memo) {
    }

    /**
     * The chunk {@code world} decorates in {@code area}, with its starts in the order the platform places them;
     * {@code surfaceHeight} gives the terrain's surface per column in world heights.
     */
    public static NativeFitChunk<BlockState> chunk(WorldGenLevel world, BoundingBox area, List<FitTarget> targets,
                                                   IntBinaryOperator surfaceHeight) {
        Supplier<StructureTemplateManager> templates = () -> world.getLevel().getStructureManager();
        List<NativeFitStart<BlockState>> starts = new ArrayList<>(targets.size());
        for (FitTarget target : targets) {
            starts.add(new MinecraftStart(world, target, templates));
        }
        return new NativeFitChunk<>(new MinecraftBlocks(world), box(area), world.getMinY(),
                world.getMinY() + world.getHeight(), world.getSeed(), surfaceHeight, y -> naturalBlock(world, y), starts);
    }

    /** The block the dimension's natural terrain is made of at world height {@code y}. */
    static BlockState naturalBlock(WorldGenLevel world, int y) {
        ServerLevel level = world == null ? null : world.getLevel();
        if (level == null) {
            return overworldBlock(y);
        }
        Holder<DimensionType> dimensionType = level.dimensionTypeRegistration();
        return naturalBlock(level.dimension(), dimensionType.unwrapKey().orElse(null), dimensionType.value(), y);
    }

    static BlockState naturalBlock(ResourceKey<Level> dimension, ResourceKey<DimensionType> dimensionTypeKey,
                                   DimensionType dimensionType, int y) {
        if (Level.NETHER.equals(dimension)) {
            return Blocks.NETHERRACK.defaultBlockState();
        }
        if (Level.END.equals(dimension)) {
            return Blocks.END_STONE.defaultBlockState();
        }
        if (BuiltinDimensionTypes.NETHER.equals(dimensionTypeKey)
                || dimensionType != null && dimensionType.hasCeiling() && !dimensionType.hasSkyLight()) {
            return Blocks.NETHERRACK.defaultBlockState();
        }
        if (BuiltinDimensionTypes.END.equals(dimensionTypeKey)
                || dimensionType != null && (dimensionType.hasEnderDragonFight() || dimensionType.hasEndFlashes())) {
            return Blocks.END_STONE.defaultBlockState();
        }
        return overworldBlock(y);
    }

    private static BlockState overworldBlock(int y) {
        return y < 0 ? Blocks.DEEPSLATE.defaultBlockState() : Blocks.STONE.defaultBlockState();
    }

    static NativeFitBox box(BoundingBox box) {
        return new NativeFitBox(box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ());
    }

    static BoundingBox boundingBox(NativeFitBox box) {
        return new BoundingBox(box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ());
    }

    /** Logs, wood, stems, hyphae and leaves, by tag or by the block's name. */
    static boolean isTreeBlock(BlockState state) {
        return isLog(state) || isLeaves(state);
    }

    private static boolean isLog(BlockState state) {
        if (state.is(BlockTags.LOGS)) {
            return true;
        }
        String path = blockPath(state);
        return path.endsWith("_log") || path.endsWith("_wood") || path.endsWith("_stem") || path.endsWith("_hyphae");
    }

    private static boolean isLeaves(BlockState state) {
        return state.is(BlockTags.LEAVES) || blockPath(state).endsWith("_leaves");
    }

    /** Field reads instead of a registry lookup: this runs per block probe on the hottest fit scans. */
    private static String blockPath(BlockState state) {
        return state.getBlock().builtInRegistryHolder().key().identifier().getPath();
    }

    private static final class MinecraftBlocks implements NativeFitBlocks<BlockState> {
        private final WorldGenLevel world;
        private final BlockPos.MutableBlockPos position = new BlockPos.MutableBlockPos();

        private MinecraftBlocks(WorldGenLevel world) {
            this.world = world;
        }

        @Override
        public BlockState get(int x, int y, int z) {
            return world.getBlockState(position.set(x, y, z));
        }

        @Override
        public void set(int x, int y, int z, BlockState state) {
            world.setBlock(position.set(x, y, z), state, 2);
        }

        @Override
        public String key(BlockState state) {
            return BlockStateParser.serialize(state);
        }

        @Override
        public BlockState state(String key) {
            try {
                return BlockStateParser.parseForBlock(BuiltInRegistries.BLOCK, key, false).blockState();
            } catch (CommandSyntaxException invalid) {
                throw new IllegalArgumentException("Block state " + key + " is not one of the platform's: "
                        + invalid.getMessage(), invalid);
            }
        }

        @Override
        public boolean is(BlockState state, NativeFitTrait trait) {
            return switch (trait) {
                case AIR -> state.isAir();
                case SOLID -> state.isSolid();
                case FLUID -> !state.getFluidState().isEmpty();
                case LOG -> isLog(state);
                case LEAVES -> isLeaves(state);
            };
        }

        @Override
        public boolean inTag(BlockState state, String tag) {
            return state.is(TAGS.computeIfAbsent(tag, id -> TagKey.create(Registries.BLOCK, Identifier.parse(id))));
        }

        @Override
        public boolean sturdy(int x, int y, int z, String face) {
            BlockState state = world.getBlockState(position.set(x, y, z));
            return state.isFaceSturdy(world, position, Direction.valueOf(face), SupportType.FULL);
        }

        @Override
        public BlockState fluid(BlockState state) {
            return state.getFluidState().createLegacyBlock();
        }
    }

    private static final class MinecraftStart implements NativeFitStart<BlockState> {
        private final FitTarget target;
        private final List<NativeFitPiece<BlockState>> pieces;

        private MinecraftStart(WorldGenLevel world, FitTarget target, Supplier<StructureTemplateManager> templates) {
            this.target = target;
            StructureStart start = target.start();
            BlockPos referencePosition = referencePosition(start);
            List<NativeFitPiece<BlockState>> converted = new ArrayList<>(start.getPieces().size());
            for (StructurePiece piece : start.getPieces()) {
                converted.add(new MinecraftPiece(world, piece, referencePosition, templates));
            }
            this.pieces = List.copyOf(converted);
        }

        /** Where template processors measure from: the first piece's center, at its lowest block. */
        private static BlockPos referencePosition(StructureStart start) {
            BoundingBox bounds = start.getPieces().getFirst().getBoundingBox();
            BlockPos center = bounds.getCenter();
            return new BlockPos(center.getX(), bounds.minY(), center.getZ());
        }

        @Override
        public String structureKey() {
            return target.structureId();
        }

        @Override
        public int chunkX() {
            return target.start().getChunkPos().x();
        }

        @Override
        public int chunkZ() {
            return target.start().getChunkPos().z();
        }

        @Override
        public String step() {
            return target.start().getStructure().step().name();
        }

        @Override
        public String adaptation() {
            return target.start().getStructure().terrainAdaptation().name();
        }

        @Override
        public NativeStartMemo memo() {
            return target.memo();
        }

        @Override
        public List<NativeFitPiece<BlockState>> pieces() {
            return pieces;
        }

        @Override
        public Object key() {
            return target.start();
        }
    }

    private static final class MinecraftPiece implements NativeFitPiece<BlockState> {
        private final WorldGenLevel world;
        private final StructurePiece piece;
        private final BlockPos referencePosition;
        private final Supplier<StructureTemplateManager> templates;

        private MinecraftPiece(WorldGenLevel world, StructurePiece piece, BlockPos referencePosition,
                               Supplier<StructureTemplateManager> templates) {
            this.world = world;
            this.piece = piece;
            this.referencePosition = referencePosition;
            this.templates = templates;
        }

        @Override
        public NativeFitBox box() {
            return NativeStructureFitBridge.box(piece.getBoundingBox());
        }

        @Override
        public String kind() {
            if (!(piece instanceof PoolElementStructurePiece poolPiece)) {
                return "FIXED";
            }
            return poolPiece.getElement().getProjection() == StructureTemplatePool.Projection.RIGID
                    ? "RIGID" : "TERRAIN_MATCHING";
        }

        @Override
        public int groundLevelDelta() {
            return piece instanceof PoolElementStructurePiece poolPiece ? poolPiece.getGroundLevelDelta() : 0;
        }

        @Override
        public List<int[]> junctions() {
            if (!(piece instanceof PoolElementStructurePiece poolPiece)) {
                return List.of();
            }
            List<int[]> junctions = new ArrayList<>(poolPiece.getJunctions().size());
            for (JigsawJunction junction : poolPiece.getJunctions()) {
                junctions.add(new int[]{junction.getSourceX(), junction.getSourceGroundY(), junction.getSourceZ()});
            }
            return junctions;
        }

        @Override
        public NativeTemplateCells<BlockState> cells(NativeFitBox region) {
            if (!(piece instanceof PoolElementStructurePiece poolPiece)) {
                return new NativeTemplateCells<>(false, new int[0], new int[0], new int[0], new BlockState[0], 0);
            }
            BoundingBox processingArea = boundingBox(region);
            return NativeStructureTemplateOccupancy.cells(world, poolPiece, referencePosition, processingArea, templates);
        }

        @Override
        public void columns(ColumnVisitor visitor) {
            BoundingBox bounds = piece.getBoundingBox();
            if (piece instanceof PoolElementStructurePiece poolPiece
                    && emitTemplateColumns(pieceTemplates(poolPiece), poolPiece.getPosition(), poolPiece.getRotation(),
                    bounds, visitor)) {
                return;
            }
            for (int z = bounds.minZ(); z <= bounds.maxZ(); z++) {
                for (int x = bounds.minX(); x <= bounds.maxX(); x++) {
                    visitor.column(x, z, bounds.minY(), bounds.maxY());
                }
            }
        }

        @Override
        public List<int[]> legacyAir(NativeFitBox clip) {
            if (!(piece instanceof PoolElementStructurePiece poolPiece)) {
                return List.of();
            }
            StructurePlaceSettings settings = new StructurePlaceSettings()
                    .setRotation(poolPiece.getRotation())
                    .setBoundingBox(boundingBox(clip));
            List<int[]> air = new ArrayList<>();
            collectLegacyAir(poolPiece.getElement(), poolPiece.getPosition(), settings, air);
            return air;
        }

        private void collectLegacyAir(StructurePoolElement element, BlockPos position, StructurePlaceSettings settings,
                                      List<int[]> air) {
            if (element instanceof ListPoolElement listElement) {
                for (StructurePoolElement child : listElement.getElements()) {
                    collectLegacyAir(child, position, settings, air);
                }
                return;
            }
            if (!(element instanceof LegacySinglePoolElement legacyElement)) {
                return;
            }
            StructureTemplate template = NativeStructureReflection.resolveTemplate(legacyElement, templates);
            for (StructureTemplate.StructureBlockInfo block : template.filterBlocks(position, settings, Blocks.AIR)) {
                BlockPos at = block.pos();
                air.add(new int[]{at.getX(), at.getY(), at.getZ()});
            }
        }

        private List<StructureTemplate> pieceTemplates(PoolElementStructurePiece poolPiece) {
            List<StructureTemplate> resolved = new ArrayList<>(1);
            collectElementTemplates(poolPiece.getElement(), resolved);
            return resolved;
        }

        private void collectElementTemplates(StructurePoolElement element, List<StructureTemplate> resolved) {
            if (element instanceof ListPoolElement listElement) {
                for (StructurePoolElement child : listElement.getElements()) {
                    collectElementTemplates(child, resolved);
                }
                return;
            }
            if (element instanceof SinglePoolElement singleElement) {
                resolved.add(NativeStructureReflection.resolveTemplate(singleElement, templates));
            }
        }

        /**
         * Per-column vertical extents from the complement of the templates' air and structure-void cells, so a carve
         * tracks the actual silhouette instead of the piece's rectangular box. False when there is no template or it
         * is too large to read, and the box stands in.
         */
        private static boolean emitTemplateColumns(List<StructureTemplate> templates, BlockPos position,
                                                   Rotation rotation, BoundingBox bounds,
                                                   ColumnVisitor visitor) {
            if (templates.isEmpty()) {
                return false;
            }
            int width = bounds.getXSpan();
            int depth = bounds.getZSpan();
            long cells = (long) width * depth * bounds.getYSpan();
            if (cells < 1L || cells > MAX_TEMPLATE_COLUMN_CELLS) {
                return false;
            }
            StructurePlaceSettings settings = new StructurePlaceSettings().setRotation(rotation);
            boolean[] voidCells = null;
            for (StructureTemplate template : templates) {
                boolean[] templateVoid = new boolean[(int) cells];
                for (Block ignored : TEMPLATE_VOID_BLOCKS) {
                    for (StructureTemplate.StructureBlockInfo info : template.filterBlocks(position, settings, ignored)) {
                        BlockPos voidPosition = info.pos();
                        if (bounds.isInside(voidPosition)) {
                            templateVoid[cellIndex(bounds, width, depth,
                                    voidPosition.getX(), voidPosition.getY(), voidPosition.getZ())] = true;
                        }
                    }
                }
                if (voidCells == null) {
                    voidCells = templateVoid;
                    continue;
                }
                for (int cell = 0; cell < voidCells.length; cell++) {
                    voidCells[cell] &= templateVoid[cell];
                }
            }
            for (int z = bounds.minZ(); z <= bounds.maxZ(); z++) {
                for (int x = bounds.minX(); x <= bounds.maxX(); x++) {
                    int minY = Integer.MAX_VALUE;
                    int maxY = Integer.MIN_VALUE;
                    for (int y = bounds.minY(); y <= bounds.maxY(); y++) {
                        if (voidCells[cellIndex(bounds, width, depth, x, y, z)]) {
                            continue;
                        }
                        if (minY == Integer.MAX_VALUE) {
                            minY = y;
                        }
                        maxY = y;
                    }
                    if (minY != Integer.MAX_VALUE) {
                        visitor.column(x, z, minY, maxY);
                    }
                }
            }
            return true;
        }

        private static int cellIndex(BoundingBox bounds, int width, int depth, int x, int y, int z) {
            return ((y - bounds.minY()) * depth + z - bounds.minZ()) * width + x - bounds.minX();
        }
    }
}
