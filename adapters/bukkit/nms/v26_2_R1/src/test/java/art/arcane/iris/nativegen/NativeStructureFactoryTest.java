package art.arcane.iris.nativegen;

import art.arcane.iris.engine.object.IrisJigsawConfiguration;
import art.arcane.iris.engine.object.IrisStructureTerrain;
import com.mojang.datafixers.util.Either;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.MapCodec;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderOwner;
import net.minecraft.core.HolderSet;
import net.minecraft.core.Vec3i;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.Bootstrap;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.NoiseColumn;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.biome.FixedBiomeSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.blending.Blender;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.TerrainAdjustment;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.pieces.PiecesContainer;
import net.minecraft.world.level.levelgen.structure.pools.EmptyPoolElement;
import net.minecraft.world.level.levelgen.structure.pools.ListPoolElement;
import net.minecraft.world.level.levelgen.structure.pools.SinglePoolElement;
import net.minecraft.world.level.levelgen.structure.pools.StructurePoolElement;
import net.minecraft.world.level.levelgen.structure.pools.StructurePoolElementType;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool;
import net.minecraft.world.level.levelgen.structure.pools.alias.PoolAliasBinding;
import net.minecraft.world.level.levelgen.structure.structures.SwampHutPiece;
import net.minecraft.world.level.levelgen.structure.structures.SwampHutStructure;
import net.minecraft.world.level.levelgen.structure.templatesystem.LiquidSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessorList;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class NativeStructureFactoryTest {
    @BeforeClass
    public static void bootstrapMinecraftRegistries() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    public void genericSourceDoesNotRequireJigsawType() {
        Structure source = new SwampHutStructure(
                new Structure.StructureSettings(HolderSet.empty()));

        Structure configured = NativeStructureFactory.configure(
                null, source, null, false, 80);

        assertSame(source, configured);
    }

    @Test
    public void jigsawOptionsFailClosedForNonJigsawSource() {
        Structure source = new SwampHutStructure(
                new Structure.StructureSettings(HolderSet.empty()));

        assertThrows(IllegalStateException.class, () -> NativeStructureFactory.configure(
                null, source, new IrisJigsawConfiguration(), false, 80));
    }

    @Test
    public void sourceMetadataReadsScalarAndAxisSpecificJigsawRanges() {
        CompoundTag scalar = new CompoundTag();
        scalar.putInt("max_distance_from_center", 80);
        CompoundTag axisSpecific = new CompoundTag();
        CompoundTag distance = new CompoundTag();
        distance.putInt("horizontal", 96);
        distance.putInt("vertical", 64);
        axisSpecific.put("max_distance_from_center", distance);

        assertEquals(80, NativeStructureFactory.sourceDistance(scalar, "horizontal"));
        assertEquals(80, NativeStructureFactory.sourceDistance(scalar, "vertical"));
        assertEquals(96, NativeStructureFactory.sourceDistance(axisSpecific, "horizontal"));
        assertEquals(64, NativeStructureFactory.sourceDistance(axisSpecific, "vertical"));
    }

    @Test
    public void sourceMetadataIncludesAdjustBoundingBoxExpansion() {
        Structure none = new SwampHutStructure(new Structure.StructureSettings(HolderSet.empty()));
        Structure beardBox = new SwampHutStructure(new Structure.StructureSettings.Builder(HolderSet.empty())
                .terrainAdapation(TerrainAdjustment.BEARD_BOX)
                .build());

        assertEquals(0, NativeStructureFactory.horizontalReferenceExpansion(none));
        assertEquals(12, NativeStructureFactory.horizontalReferenceExpansion(beardBox));
    }

    @Test
    public void templatePoolSpanIncludesWeightedListsFallbacksAndRotations() {
        StructurePoolElement narrow = inlineElement(30, 8, 70);
        StructurePoolElement wide = inlineElement(96, 8, 12);
        ListPoolElement list = new ListPoolElement(
                List.of(narrow, wide, EmptyPoolElement.INSTANCE),
                StructureTemplatePool.Projection.RIGID);
        BoundPoolHolder fallbackHolder = new BoundPoolHolder("test:fallback");
        StructureTemplatePool fallback = new StructureTemplatePool(
                fallbackHolder,
                List.of(Pair.of(inlineElement(110, 8, 20), 1)));
        fallbackHolder.bind(fallback);
        BoundPoolHolder startHolder = new BoundPoolHolder("test:start");
        StructureTemplatePool start = new StructureTemplatePool(
                fallbackHolder,
                List.of(Pair.of(list, 5), Pair.of(EmptyPoolElement.INSTANCE, 1)));
        startHolder.bind(start);

        assertEquals(110, NativeStructureTemplatePoolBounds.horizontalSpan(start, null));
    }

    @Test
    public void oversizedInlineStartTemplateReportsItsFullSpan() {
        BoundPoolHolder holder = new BoundPoolHolder("test:oversized");
        StructureTemplatePool pool = new StructureTemplatePool(
                holder,
                List.of(Pair.of(inlineElement(160, 8, 9), 1)));
        holder.bind(pool);

        assertEquals(160, NativeStructureTemplatePoolBounds.horizontalSpan(pool, null));
    }

    @Test
    public void sourceSpanIncludesOnlyAliasesThatCanReplaceTheStartPool() {
        BoundPoolHolder emptyHolder = new BoundPoolHolder("test:empty");
        StructureTemplatePool empty = new StructureTemplatePool(emptyHolder, List.of());
        emptyHolder.bind(empty);
        BoundPoolHolder startHolder = new BoundPoolHolder("test:start");
        StructureTemplatePool start = new StructureTemplatePool(
                emptyHolder, List.of(Pair.of(inlineElement(20, 8, 12), 1)));
        startHolder.bind(start);
        BoundPoolHolder replacementHolder = new BoundPoolHolder("test:replacement");
        StructureTemplatePool replacement = new StructureTemplatePool(
                emptyHolder, List.of(Pair.of(inlineElement(30, 8, 12), 1)));
        replacementHolder.bind(replacement);
        BoundPoolHolder unrelatedHolder = new BoundPoolHolder("test:unrelated");
        StructureTemplatePool unrelated = new StructureTemplatePool(
                emptyHolder, List.of(Pair.of(inlineElement(200, 8, 12), 1)));
        unrelatedHolder.bind(unrelated);
        ResourceKey<StructureTemplatePool> startKey = startHolder.unwrapKey().orElseThrow();
        ResourceKey<StructureTemplatePool> replacementKey = replacementHolder.unwrapKey().orElseThrow();
        ResourceKey<StructureTemplatePool> unrelatedKey = unrelatedHolder.unwrapKey().orElseThrow();
        ResourceKey<StructureTemplatePool> childAlias = ResourceKey.create(
                Registries.TEMPLATE_POOL, Identifier.parse("test:child_alias"));
        List<PoolAliasBinding> aliases = List.of(
                PoolAliasBinding.direct(startKey, replacementKey),
                PoolAliasBinding.direct(childAlias, unrelatedKey));
        Map<ResourceKey<StructureTemplatePool>, StructureTemplatePool> pools = Map.of(
                replacementKey, replacement,
                unrelatedKey, unrelated);

        assertEquals(30, NativeStructureTemplatePoolBounds.sourceHorizontalSpan(
                null, start, startKey, aliases, pools::get));
    }

    @Test
    public void sourceStartSpanDoesNotIncludeItsFallbackPool() {
        BoundPoolHolder fallbackHolder = new BoundPoolHolder("test:large_fallback");
        StructureTemplatePool fallback = new StructureTemplatePool(
                fallbackHolder, List.of(Pair.of(inlineElement(180, 8, 10), 1)));
        fallbackHolder.bind(fallback);
        BoundPoolHolder startHolder = new BoundPoolHolder("test:small_start");
        StructureTemplatePool start = new StructureTemplatePool(
                fallbackHolder, List.of(Pair.of(inlineElement(24, 8, 10), 1)));
        startHolder.bind(start);

        assertEquals(180, NativeStructureTemplatePoolBounds.horizontalSpan(start, null));
        assertEquals(24, NativeStructureTemplatePoolBounds.sourceHorizontalSpan(
                null, start, startHolder.unwrapKey().orElseThrow(), List.of(), ignored -> null));
    }

    @Test
    public void customPoolElementUsesItsBoundingBoxCapability() {
        BoundPoolHolder fallbackHolder = new BoundPoolHolder("test:custom_fallback");
        StructureTemplatePool fallback = new StructureTemplatePool(fallbackHolder, List.of());
        fallbackHolder.bind(fallback);
        BoundPoolHolder poolHolder = new BoundPoolHolder("test:custom");
        StructureTemplatePool pool = new StructureTemplatePool(
                fallbackHolder, List.of(Pair.of(new BoundingPoolElement(37, 11), 1)));
        poolHolder.bind(pool);

        assertEquals(37, NativeStructureTemplatePoolBounds.horizontalSpan(pool, null));
    }

    @Test
    public void forcedGeneratorBypassesBiomeEligibilityWithoutFabricatingTerrain() {
        ChunkGenerator delegate = new TestChunkGenerator();
        Holder<Biome> biome = Holder.direct((Biome) null);
        LevelHeightAccessor heightAccessor = LevelHeightAccessor.create(-64, 384);
        ForcedStructureChunkGenerator generator = new ForcedStructureChunkGenerator(
                delegate, biome);

        assertEquals(63, generator.getSeaLevel());
        assertEquals(64, generator.getBaseHeight(
                0, 0, Heightmap.Types.WORLD_SURFACE_WG, heightAccessor, null));
        NoiseColumn column = generator.getBaseColumn(
                0, 0, heightAccessor, null);
        assertSame(Blocks.STONE, column.getBlock(80).getBlock());
        assertSame(Blocks.STONE, column.getBlock(81).getBlock());
        assertNotSame(delegate.getBiomeSource(), generator.getBiomeSource());
    }

    @Test
    public void undergroundRelocationMovesGenericPiecesAndReturnsFreshStart() {
        Structure source = new SwampHutStructure(
                new Structure.StructureSettings(HolderSet.empty()));
        SwampHutPiece piece = new SwampHutPiece(RandomSource.create(17L), 0, 0);
        StructureStart start = new StructureStart(
                source,
                new ChunkPos(0, 0),
                0,
                new PiecesContainer(List.of(piece))
        );

        StructureStart relocated = NativeStructureVerticalPlacer.relocateToMinY(
                start, source, -20, LevelHeightAccessor.create(-64, 384));

        assertNotSame(start, relocated);
        assertEquals(-20, relocated.getPieces().getFirst().getBoundingBox().minY());
        assertEquals(-20, relocated.getBoundingBox().minY());
    }

    @Test
    public void invalidGeneratedStartIsANormalSkippedCandidate() {
        Structure source = new SwampHutStructure(
                new Structure.StructureSettings(HolderSet.empty()));
        StructureStart valid = new StructureStart(
                source,
                new ChunkPos(0, 0),
                0,
                new PiecesContainer(List.of(
                        new SwampHutPiece(RandomSource.create(23L), 0, 0)))
        );

        assertFalse(NativeStructureStartInjector.isUsableGeneratedStart(
                StructureStart.INVALID_START));
        assertFalse(NativeStructureStartInjector.isUsableGeneratedStart(null));
        assertFalse(NativeStructureStartInjector.isUsableGeneratedStart(
                new StructureStart(source, new ChunkPos(0, 0), 0,
                        new PiecesContainer(List.of()))));
        assertTrue(NativeStructureStartInjector.isUsableGeneratedStart(valid));
    }

    @Test
    public void unrepresentableGeneratedContentBecomesAnUnpublishedInvalidStart() {
        Structure source = new SwampHutStructure(
                new Structure.StructureSettings(HolderSet.empty()));
        SwampHutPiece boundaryPiece = new SwampHutPiece(RandomSource.create(29L), 0, 0);
        boundaryPiece.move(143 - boundaryPiece.getBoundingBox().maxX(), 0, 0);
        StructureStart boundary = new StructureStart(
                source,
                new ChunkPos(0, 0),
                0,
                new PiecesContainer(List.of(boundaryPiece))
        );
        SwampHutPiece oversizedPiece = new SwampHutPiece(RandomSource.create(31L), 0, 0);
        oversizedPiece.move(144 - oversizedPiece.getBoundingBox().maxX(), 0, 0);
        StructureStart oversized = new StructureStart(
                source,
                new ChunkPos(0, 0),
                0,
                new PiecesContainer(List.of(oversizedPiece))
        );

        StructureStart boundaryResult = NativeStructureReferenceEnvelope.wrapForPublication(
                boundary, source, 0, new IrisStructureTerrain(), "test:boundary_content");

        StructureStart result = NativeStructureReferenceEnvelope.wrapForPublication(
                oversized, source, 0,
                new IrisStructureTerrain(),
                "test:oversized_content");

        assertTrue(boundaryResult.isValid());
        assertSame(StructureStart.INVALID_START, result);
        assertFalse(NativeStructureStartInjector.isUsableGeneratedStart(result));
        assertEquals(1, oversized.getPieces().size());
    }

    private static final class TestChunkGenerator extends ChunkGenerator {
        private TestChunkGenerator() {
            super(new FixedBiomeSource(Holder.direct((Biome) null)));
        }

        @Override
        protected MapCodec<? extends ChunkGenerator> codec() {
            return MapCodec.unit(this);
        }

        @Override
        public void applyCarvers(WorldGenRegion region, long seed, RandomState randomState,
                                 BiomeManager biomeManager, StructureManager structureManager,
                                 ChunkAccess chunk) {
        }

        @Override
        public void buildSurface(WorldGenRegion level, StructureManager structureManager,
                                 RandomState randomState, ChunkAccess protoChunk) {
        }

        @Override
        public void spawnOriginalMobs(WorldGenRegion worldGenRegion) {
        }

        @Override
        public int getGenDepth() {
            return 384;
        }

        @Override
        public CompletableFuture<ChunkAccess> fillFromNoise(
                Blender blender, RandomState randomState,
                StructureManager structureManager, ChunkAccess centerChunk) {
            return CompletableFuture.completedFuture(centerChunk);
        }

        @Override
        public int getSeaLevel() {
            return 63;
        }

        @Override
        public int getMinY() {
            return -64;
        }

        @Override
        public int getBaseHeight(int x, int z, Heightmap.Types type,
                                 LevelHeightAccessor heightAccessor, RandomState randomState) {
            return 64;
        }

        @Override
        public NoiseColumn getBaseColumn(int x, int z, LevelHeightAccessor heightAccessor,
                                         RandomState randomState) {
            BlockState[] states = new BlockState[heightAccessor.getHeight()];
            for (int index = 0; index < states.length; index++) {
                states[index] = Blocks.STONE.defaultBlockState();
            }
            return new NoiseColumn(heightAccessor.getMinY(), states);
        }

        @Override
        public void addDebugScreenInfo(List<String> result, RandomState randomState,
                                       BlockPos feetPos) {
        }
    }

    private static StructurePoolElement inlineElement(int sizeX, int sizeY, int sizeZ) {
        StructureTemplate template = new StructureTemplate();
        CompoundTag tag = new CompoundTag();
        ListTag size = new ListTag();
        size.add(IntTag.valueOf(sizeX));
        size.add(IntTag.valueOf(sizeY));
        size.add(IntTag.valueOf(sizeZ));
        tag.put("size", size);
        template.load(BuiltInRegistries.BLOCK, tag);
        return new InlinePoolElement(template);
    }

    private static final class InlinePoolElement extends SinglePoolElement {
        private final StructureTemplate inlineTemplate;

        private InlinePoolElement(StructureTemplate template) {
            super(Either.right(template), Holder.direct(new StructureProcessorList(List.of())),
                    StructureTemplatePool.Projection.RIGID, Optional.<LiquidSettings>empty());
            inlineTemplate = template;
        }

        @Override
        public BoundingBox getBoundingBox(StructureTemplateManager templateManager,
                                          BlockPos position, Rotation rotation) {
            return inlineTemplate.getBoundingBox(
                    new StructurePlaceSettings().setRotation(rotation), position);
        }
    }

    private static final class BoundingPoolElement extends StructurePoolElement {
        private final int width;
        private final int depth;

        private BoundingPoolElement(int width, int depth) {
            super(StructureTemplatePool.Projection.RIGID);
            this.width = width;
            this.depth = depth;
        }

        @Override
        public Vec3i getSize(StructureTemplateManager templateManager, Rotation rotation) {
            return new Vec3i(width, 1, depth);
        }

        @Override
        public List<StructureTemplate.JigsawBlockInfo> getShuffledJigsawBlocks(
                StructureTemplateManager templateManager, BlockPos position,
                Rotation rotation, RandomSource random) {
            return List.of();
        }

        @Override
        public BoundingBox getBoundingBox(StructureTemplateManager templateManager,
                                          BlockPos position, Rotation rotation) {
            return new BoundingBox(
                    position.getX(), position.getY(), position.getZ(),
                    position.getX() + width - 1, position.getY(),
                    position.getZ() + depth - 1);
        }

        @Override
        public boolean place(StructureTemplateManager templateManager,
                             WorldGenLevel world,
                             StructureManager structureManager, ChunkGenerator chunkGenerator,
                             BlockPos position, BlockPos pivot, Rotation rotation,
                             BoundingBox area, RandomSource random,
                             LiquidSettings liquidSettings, boolean keepJigsaws) {
            return true;
        }

        @Override
        public StructurePoolElementType<?> getType() {
            return StructurePoolElementType.EMPTY;
        }
    }

    private static final class BoundPoolHolder extends Holder.Reference<StructureTemplatePool> {
        private BoundPoolHolder(String key) {
            super(Type.STAND_ALONE, new HolderOwner<>() {
            }, ResourceKey.create(Registries.TEMPLATE_POOL, Identifier.parse(key)), null);
        }

        private void bind(StructureTemplatePool pool) {
            bindValue(pool);
        }
    }
}
