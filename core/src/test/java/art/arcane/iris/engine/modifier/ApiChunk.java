/*
 * Iris is a World Generator for Minecraft Servers
 * Copyright (c) 2026 Arcane Arts (Volmit Software)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package art.arcane.iris.engine.modifier;

import art.arcane.iris.builtin.IrisStages;
import art.arcane.iris.builtin.IrisSeed;
import art.arcane.iris.builtin.post.PostProcessingSlabsSettings;
import art.arcane.iris.builtin.post.PostProcessingWallsSettings;
import art.arcane.iris.builtin.prepare.SlopeClip;
import art.arcane.iris.builtin.terrain.HideOresSettings;
import art.arcane.iris.builtin.terrain.PaletteLayer;
import art.arcane.iris.builtin.terrain.SlabSettings;
import art.arcane.iris.builtin.terrain.WallSettings;
import art.arcane.iris.engine.object.TestSettings;
import com.volmit.FeatureAttachment.Target;
import com.volmit.IrisFeature;
import com.volmit.IrisModule;
import com.volmit.ModuleInfo;
import com.volmit.ModuleLog;
import com.volmit.block.BlockFace;
import com.volmit.block.BlockRegistry;
import com.volmit.block.BlockSpec;
import com.volmit.block.BlockState;
import com.volmit.block.BlockTrait;
import com.volmit.block.PaletteSampler;
import com.volmit.block.PaletteSpec;
import com.volmit.config.FeatureSettings;
import com.volmit.config.PackSettings;
import com.volmit.generation.GenerationContext;
import com.volmit.host.ArrayHunk;
import com.volmit.host.ChunkRunner;
import com.volmit.host.DiscoveredModule;
import com.volmit.host.ModuleLogs;
import com.volmit.host.ResolvedGraph;
import com.volmit.host.StageGraphBuilder;
import com.volmit.hunk.Hunk;
import com.volmit.item.ItemRegistry;
import com.volmit.mantle.BlockBox;
import com.volmit.mantle.Mantle;
import com.volmit.mantle.MantleEdit;
import com.volmit.mantle.MantleKey;
import com.volmit.mantle.MantleLayer;
import com.volmit.mantle.MantleVisitor;
import com.volmit.noise.NoiseFactory;
import com.volmit.noise.NoiseSampler;
import com.volmit.object.BlockEntityData;
import com.volmit.resource.PackResources;
import com.volmit.resource.ResourceId;
import com.volmit.config.FeatureSettings.Entry;
import com.volmit.resource.ResourceKey;
import com.volmit.stage.Pipeline;
import com.volmit.stage.StageGraph;
import com.volmit.world.BiomeKind;
import com.volmit.world.BiomeSample;
import com.volmit.world.BlockPosition;
import com.volmit.world.PackWorld;
import com.volmit.world.TerrainSample;
import com.volmit.world.TerrainSampler;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.IntBinaryOperator;

/**
 * One chunk as IrisApi shows it to a chunk stage, held in memory, so the ported built-ins are tested through the surface
 * alone, without an Iris engine: the chunk's blocks, the blocks placements planned in its mantle, a terrain of given
 * heights, a block registry that knows a few plants, settings the dimension and every biome of the chunk declare, and a
 * world with Iris's seeds.
 */
public final class ApiChunk implements GenerationContext {
    public static final int WIDTH = 16;
    public static final BlockState AIR = new BlockState("minecraft:air");
    private static final long SEED = 1337L;
    /** What every noise of the fixture's pack samples, everywhere. */
    private static final double HALF = 0.5;
    /** A key that the fixture's pack has no block for. */
    public static final String UNKNOWN_BLOCK = "unknown_block";
    private static final ResourceId DIMENSION = ResourceId.parse("test:dimension/overworld");
    private static final ResourceId REGION = ResourceId.parse("test:region/plains");
    private static final ResourceId BIOME = ResourceId.parse("test:biome/plains");
    private static final int TERRAIN_MARGIN = 16;
    private static final Set<String> AIRS = Set.of("minecraft:air", "minecraft:cave_air", "minecraft:void_air");
    private static final Set<String> FLUIDS = Set.of("minecraft:water", "minecraft:lava");
    private static final Set<String> DECORATIONS = Set.of("minecraft:short_grass", "minecraft:poppy", "minecraft:tall_grass",
            "minecraft:dead_bush");
    private static final Set<String> FOLIAGE = Set.of("minecraft:short_grass", "minecraft:poppy", "minecraft:tall_grass",
            "minecraft:fern");
    private static final Set<String> SOILS = Set.of("minecraft:grass_block", "minecraft:dirt", "minecraft:sand");
    private static final Set<String> VINES = Set.of("minecraft:vine");
    private static final Comparator<BlockPosition> VISIT_ORDER = Comparator.comparingInt(BlockPosition::x)
            .thenComparingInt(BlockPosition::y).thenComparingInt(BlockPosition::z);

    private final int originX;
    private final int originZ;
    private final ArrayHunk<BlockState> blocks;
    private final Map<BlockPosition, BlockState> planned = new HashMap<>();
    private final World world = new World();
    private final Set<BlockPosition> carved = new HashSet<>();
    private final List<IrisFeature> dimensionSettings = new ArrayList<>();
    private final List<IrisFeature> regionSettings = new ArrayList<>();
    private final List<IrisFeature> biomeSettings = new ArrayList<>();
    private BiomeSample biomeSample;
    private BiomeKind surfaceKind = BiomeKind.LAND;
    private int minWorldY;
    private IntBinaryOperator surface = (x, z) -> 0;
    private IntBinaryOperator upperDimension;
    private double slope;
    private int fluidHeight;

    public ApiChunk(int originX, int originZ, int height) {
        this.originX = originX;
        this.originZ = originZ;
        this.blocks = new ArrayHunk<>(BlockState.class, WIDTH, height, WIDTH, AIR);
    }

    public static BlockState block(String key) {
        return BlockState.parse(key);
    }

    /** The terrain's surface height of every column, the chunk's and its neighbours'. */
    public ApiChunk surface(IntBinaryOperator heights) {
        this.surface = heights;
        return this;
    }

    public ApiChunk slope(double slope) {
        this.slope = slope;
        return this;
    }

    public ApiChunk fluidHeight(int height) {
        this.fluidHeight = height;
        return this;
    }

    /** Where the dimension above begins, per column. */
    public ApiChunk upperDimension(IntBinaryOperator heights) {
        this.upperDimension = heights;
        return this;
    }

    /** The biomes every column and cave position of the chunk has. */
    public ApiChunk biomes(ResourceId surfaceBiome, ResourceId caveBiome) {
        this.biomeSample = new BiomeSample(ResourceId.parse("test:region/plains"), surfaceBiome, Optional.ofNullable(caveBiome));
        return this;
    }

    /** What the surface biome of every column is generated as. */
    public ApiChunk surfaceKind(BiomeKind kind) {
        this.surfaceKind = kind;
        return this;
    }

    /** The Y in the world of the dimension's bottom. */
    public ApiChunk minWorldY(int minWorldY) {
        this.minWorldY = minWorldY;
        return this;
    }

    /** A void the mantle carved, in world coordinates. */
    public ApiChunk carve(int x, int y, int z) {
        carved.add(new BlockPosition(x, y, z));
        return this;
    }

    /** Settings the dimension declares. */
    public ApiChunk dimensionSettings(IrisFeature... settings) {
        dimensionSettings.addAll(List.of(settings));
        return this;
    }

    /** Settings every region of the chunk declares. */
    public ApiChunk regionSettings(IrisFeature... settings) {
        regionSettings.addAll(List.of(settings));
        return this;
    }

    /** Settings every surface biome of the chunk declares. */
    public ApiChunk biomeSettings(IrisFeature... settings) {
        biomeSettings.addAll(List.of(settings));
        return this;
    }

    /** The blocks the fixture's pack knows, by the rule in {@link Blocks}. */
    public static BlockRegistry blocks() {
        return new Blocks();
    }

    /** A block a placement planned in the mantle, in world coordinates. */
    public ApiChunk plan(int x, int y, int z, BlockState block) {
        planned.put(new BlockPosition(x, y, z), block);
        return this;
    }

    /** Sets a block of the chunk, in the chunk's own coordinates. */
    public ApiChunk set(int x, int y, int z, BlockState block) {
        blocks.set(x, y, z, block);
        return this;
    }

    public BlockState get(int x, int y, int z) {
        return blocks.get(x, y, z);
    }

    /** The dimension's {@code hideOresForHiddenOre}: true. */
    public ApiChunk hidingOres() {
        dimensionSettings.add(new HideOresSettings(true));
        return this;
    }

    /** The dimension's {@code postProcessingWalls} and {@code postProcessingSlabs}. */
    public ApiChunk postProcessing(boolean walls, boolean slabs) {
        dimensionSettings.add(TestSettings.with(new PostProcessingWallsSettings(), "enabled", walls));
        dimensionSettings.add(TestSettings.with(new PostProcessingSlabsSettings(), "enabled", slabs));
        return this;
    }

    /** Every biome's {@code wall}: one block. */
    public ApiChunk wall(BlockState block) {
        biomeSettings.add(TestSettings.with(new WallSettings(), "layer", layerOf(block, new SlopeClip())));
        return this;
    }

    /** Every biome's {@code slab}: one block, placed where the slope is at most {@code maxSlope}. */
    public ApiChunk slab(BlockState block, double maxSlope) {
        SlopeClip slope = TestSettings.with(new SlopeClip(), "maximumSlope", maxSlope);
        biomeSettings.add(TestSettings.with(new SlabSettings(), "layer", layerOf(block, slope)));
        return this;
    }

    private static PaletteLayer layerOf(BlockState block, SlopeClip slope) {
        return TestSettings.with(new PaletteLayer(), "palette", List.of(BlockSpec.of(block.key())), "slopeCondition", slope);
    }

    /** Empty stand-ins for the built-in stages the ported modules order after: terrain, carving and floating islands. */
    public static List<IrisModule> standIns() {
        return List.of(new TerrainStandIn(), new CarveStandIn(), new FloatingStandIn());
    }

    /** Runs {@code modules} as a pack's chunk graph, with the {@link #standIns()}. */
    public ApiChunk generate(IrisModule... modules) {
        StageGraphBuilder builder = new StageGraphBuilder();
        for (IrisModule module : standIns()) {
            builder.install(DiscoveredModule.builtIn(module));
        }
        for (IrisModule module : modules) {
            builder.install(DiscoveredModule.builtIn(module));
        }
        return generate(builder.resolve(Pipeline.CHUNK));
    }

    /** Runs a resolved chunk graph on this chunk, such as the one of a loaded pack. */
    public ApiChunk generate(ResolvedGraph graph) {
        new ChunkRunner(graph).generate(this, SEED);
        return this;
    }

    @Override
    public int originX() {
        return originX;
    }

    @Override
    public int originZ() {
        return originZ;
    }

    @Override
    public TerrainSampler terrain() {
        return new Terrain();
    }

    @Override
    public FeatureSettings settings() {
        return new FeatureSettings() {
            @Override
            public <F extends IrisFeature> List<Entry<F>> at(Class<F> featureType, int worldX, int worldZ) {
                List<Entry<F>> entries = new ArrayList<>();
                addEntries(entries, featureType, Target.DIMENSION, DIMENSION, dimensionSettings);
                addEntries(entries, featureType, Target.REGION, REGION, regionSettings);
                addEntries(entries, featureType, Target.BIOME, BIOME, biomeSettings);
                return entries;
            }
        };
    }

    @Override
    public Mantle mantle() {
        return new ChunkMantle();
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> Hunk<T> hunk(Class<T> type) {
        if (type != BlockState.class) {
            throw new IllegalArgumentException("Only block hunks");
        }
        return (Hunk<T>) blocks;
    }

    @Override
    public long worldSeed() {
        return SEED;
    }

    @Override
    public PackResources resources() {
        return world.resources;
    }

    @Override
    public PackWorld world() {
        return world;
    }

    private static <F extends IrisFeature> void addEntries(List<Entry<F>> entries, Class<F> type, Target level, ResourceId source,
                                                           List<IrisFeature> declared) {
        declared.stream().filter(type::isInstance).map(type::cast).forEach(value -> entries.add(new Entry<>(level, source, value)));
    }

    private BlockBox chunkColumns() {
        return new BlockBox(originX, 0, originZ, originX + WIDTH, blocks.getHeight(), originZ + WIDTH);
    }

    private final class Terrain implements TerrainSampler {
        @Override
        public BlockBox bounds() {
            return new BlockBox(originX - TERRAIN_MARGIN, 0, originZ - TERRAIN_MARGIN, originX + WIDTH + TERRAIN_MARGIN,
                    blocks.getHeight(), originZ + WIDTH + TERRAIN_MARGIN);
        }

        @Override
        public TerrainSample column(int worldX, int worldZ) {
            requireInside(worldX, worldZ);
            int height = surface.applyAsInt(worldX, worldZ);
            return new TerrainSample(height, height < fluidHeight ? OptionalInt.of(fluidHeight) : OptionalInt.empty(), slope);
        }

        @Override
        public BiomeSample biome(int worldX, int y, int worldZ) {
            if (biomeSample == null) {
                throw new UnsupportedOperationException("The ported built-ins read biomes through their settings");
            }
            return biomeSample;
        }

        @Override
        public BiomeKind surfaceKind(int worldX, int worldZ) {
            requireInside(worldX, worldZ);
            return surfaceKind;
        }

        @Override
        public int minWorldY() {
            return minWorldY;
        }

        @Override
        public int fluidHeight() {
            return fluidHeight;
        }

        @Override
        public OptionalInt upperDimensionY(int worldX, int worldZ) {
            requireInside(worldX, worldZ);
            return upperDimension == null ? OptionalInt.empty() : OptionalInt.of(upperDimension.applyAsInt(worldX, worldZ));
        }

        private void requireInside(int worldX, int worldZ) {
            if (!bounds().contains(worldX, 0, worldZ)) {
                throw new IndexOutOfBoundsException(worldX + ", " + worldZ + " is outside " + bounds());
            }
        }
    }

    private final class ChunkMantle implements Mantle {
        @Override
        public BlockBox bounds() {
            return chunkColumns();
        }

        @Override
        public <T> MantleLayer<T> layer(MantleKey<T> key) {
            throw new IllegalArgumentException("No module layers here");
        }

        @Override
        public MantleLayer<BlockState> blocks() {
            return new PlannedLayer();
        }

        @Override
        public MantleLayer<Boolean> caverns() {
            return new CarvedLayer();
        }

        @Override
        public MantleEdit edit() {
            throw new UnsupportedOperationException("Chunk stages do not edit the mantle");
        }
    }

    private final class PlannedLayer implements MantleLayer<BlockState> {
        @Override
        public BlockState get(int x, int y, int z) {
            return planned.get(new BlockPosition(x, y, z));
        }

        @Override
        public void set(int x, int y, int z, BlockState value) {
            throw new UnsupportedOperationException("Planned blocks are read-only");
        }

        @Override
        public void remove(int x, int y, int z) {
            throw new UnsupportedOperationException("Planned blocks are read-only");
        }

        @Override
        public void visit(BlockBox area, MantleVisitor<BlockState> visitor) {
            if (!chunkColumns().contains(area)) {
                throw new IndexOutOfBoundsException(area + " is outside " + chunkColumns());
            }
            List<BlockPosition> positions = new ArrayList<>(planned.keySet());
            positions.sort(VISIT_ORDER);
            for (BlockPosition position : positions) {
                if (area.contains(position.x(), position.y(), position.z())) {
                    visitor.accept(position.x(), position.y(), position.z(), planned.get(position));
                }
            }
        }

        @Override
        public void clear(BlockBox area) {
            throw new UnsupportedOperationException("Planned blocks are read-only");
        }
    }

    private final class CarvedLayer implements MantleLayer<Boolean> {
        @Override
        public Boolean get(int x, int y, int z) {
            return carved.contains(new BlockPosition(x, y, z)) ? Boolean.TRUE : null;
        }

        @Override
        public void set(int x, int y, int z, Boolean value) {
            throw new UnsupportedOperationException("Caverns are read-only");
        }

        @Override
        public void remove(int x, int y, int z) {
            throw new UnsupportedOperationException("Caverns are read-only");
        }

        @Override
        public void visit(BlockBox area, MantleVisitor<Boolean> visitor) {
            List<BlockPosition> positions = new ArrayList<>(carved);
            positions.sort(VISIT_ORDER);
            for (BlockPosition position : positions) {
                if (area.contains(position.x(), position.y(), position.z())) {
                    visitor.accept(position.x(), position.y(), position.z(), Boolean.TRUE);
                }
            }
        }

        @Override
        public void clear(BlockBox area) {
            throw new UnsupportedOperationException("Caverns are read-only");
        }
    }

    /** A world of the chunk: its pack's blocks, a state store and Iris's seeds, each the world seed plus its place. */
    private final class World implements PackWorld, IrisSeed.Source {
        private final Resources resources = new Resources();
        private final Map<Class<?>, Object> states = new HashMap<>();

        @Override
        public long seed() {
            return SEED;
        }

        @Override
        public PackResources resources() {
            return resources;
        }

        @Override
        public PackSettings settings() {
            return new PackSettings() {
                @Override
                public <F extends IrisFeature> List<Entry<F>> declared(Class<F> featureType) {
                    List<Entry<F>> entries = new ArrayList<>();
                    addEntries(entries, featureType, Target.DIMENSION, DIMENSION, dimensionSettings);
                    addEntries(entries, featureType, Target.REGION, REGION, regionSettings);
                    addEntries(entries, featureType, Target.BIOME, BIOME, biomeSettings);
                    return entries;
                }
            };
        }

        @Override
        public <T> T state(Class<T> type, Function<? super PackWorld, ? extends T> create) {
            Object known = states.get(type);
            if (known == null) {
                known = create.apply(this);
                states.put(type, known);
            }
            return type.cast(known);
        }

        @Override
        public ModuleLog log() {
            return ModuleLogs.discarding();
        }

        @Override
        public PackWorld withLog(ModuleLog log) {
            return this;
        }

        @Override
        public long seed(IrisSeed seed) {
            return SEED + seed.ordinal();
        }
    }

    private static final class Resources implements PackResources {
        private final UUID snapshot = UUID.randomUUID();

        @Override
        public ResourceId packId() {
            return ResourceId.parse("test:pack");
        }

        @Override
        public UUID snapshotId() {
            return snapshot;
        }

        @Override
        public BlockRegistry blocks() {
            return new Blocks();
        }

        @Override
        public ItemRegistry items() {
            throw new UnsupportedOperationException("No items here");
        }

        @Override
        public NoiseFactory noise() {
            return (style, seed) -> new NoiseSampler() {
                @Override
                public double sample(double worldX, double worldZ) {
                    return HALF;
                }

                @Override
                public double sample(double worldX, double y, double worldZ) {
                    return HALF;
                }
            };
        }

        @Override
        public <T> Optional<T> find(ResourceKey<T> key) {
            return Optional.empty();
        }

        @Override
        public List<ResourceId> ids(Class<?> type) {
            return List.of();
        }
    }

    /**
     * Blocks by name: air, water and lava, a few decorations and foliage standing on soil (a fern is foliage only, a dead
     * bush a decoration only), vines; everything else is a solid cube.
     */
    private static final class Blocks implements BlockRegistry {
        @Override
        public BlockState resolve(String key) {
            if (key.contains(UNKNOWN_BLOCK)) {
                throw new IllegalArgumentException("Unknown block: " + key);
            }
            return BlockState.parse(key.contains(":") ? key : "minecraft:" + key);
        }

        @Override
        public BlockState resolve(BlockSpec spec) {
            BlockState block = BlockState.parse(spec.block().contains(":") ? spec.block() : "minecraft:" + spec.block());
            for (Map.Entry<String, String> property : spec.data().entrySet()) {
                block = block.withProperty(property.getKey(), property.getValue());
            }
            return block;
        }

        @Override
        public Optional<BlockEntityData> blockEntity(BlockSpec spec) {
            return Optional.empty();
        }

        @Override
        public PaletteSampler palette(PaletteSpec spec, long seed) {
            throw new UnsupportedOperationException("No palettes here");
        }

        @Override
        public boolean hasTrait(BlockState state, BlockTrait trait) {
            String material = state.material().toString();
            boolean air = AIRS.contains(material);
            boolean fluid = FLUIDS.contains(material);
            boolean plant = DECORATIONS.contains(material) || FOLIAGE.contains(material);
            boolean vine = VINES.contains(material);
            return switch (trait) {
                case AIR -> air;
                case FLUID -> fluid;
                case WATER -> material.equals("minecraft:water");
                case SOLID, OCCLUDING -> !air && !fluid && !plant && !vine;
                case FOLIAGE -> FOLIAGE.contains(material);
                case DECORATION -> DECORATIONS.contains(material);
                case VINE -> vine;
                case ORE -> material.endsWith("_ore");
                default -> false;
            };
        }

        @Override
        public boolean inTag(BlockState state, ResourceId tag) {
            return false;
        }

        @Override
        public boolean canAttach(BlockState state, BlockState support, BlockFace face) {
            return hasTrait(support, BlockTrait.SOLID);
        }

        @Override
        public boolean canPlaceOn(BlockState state, BlockState below) {
            if (hasTrait(below, BlockTrait.AIR)) {
                return false;
            }
            String material = state.material().toString();
            boolean plant = DECORATIONS.contains(material) || FOLIAGE.contains(material);
            return !plant || SOILS.contains(below.material().toString());
        }
    }

    private static final class TerrainStandIn implements IrisModule {
        @Override
        public ModuleInfo info() {
            return ModuleInfo.of("iris:terrain", "Terrain stand-in", "test");
        }

        @Override
        public void install(StageGraph graph) {
            graph.define(IrisStages.Terrain.SHAPE).run(context -> {
            });
        }
    }

    private static final class CarveStandIn implements IrisModule {
        @Override
        public ModuleInfo info() {
            return ModuleInfo.of("iris:carve", "Carving stand-in", "test");
        }

        @Override
        public void install(StageGraph graph) {
            graph.define(IrisStages.Carve.APPLY).run(context -> {
            });
        }
    }

    private static final class FloatingStandIn implements IrisModule {
        @Override
        public ModuleInfo info() {
            return ModuleInfo.of("iris:floating", "Floating stand-in", "test");
        }

        @Override
        public void install(StageGraph graph) {
            graph.define(IrisStages.Floating.SOLIDS).run(context -> {
            });
            graph.define(IrisStages.Floating.DECORATE).run(context -> {
            });
        }
    }
}
