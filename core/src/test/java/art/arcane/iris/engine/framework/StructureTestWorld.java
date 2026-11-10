package art.arcane.iris.engine.framework;

import art.arcane.iris.builtin.structure.StructurePlacement;
import art.arcane.iris.builtin.structure.StructurePlacementSettings;
import art.arcane.iris.engine.framework.structure.assembly.StructureAssembly;
import com.volmit.IrisFeature;
import com.volmit.ModuleLog;
import com.volmit.block.BlockRegistry;
import com.volmit.block.BlockState;
import com.volmit.config.FeatureSettings;
import com.volmit.config.FeatureSettings.Entry;
import com.volmit.config.Level;
import com.volmit.config.PackSettings;
import com.volmit.config.SettingsStack;
import com.volmit.data.SavedKey;
import com.volmit.geometry.BlockBox;
import com.volmit.geometry.BlockPosition;
import com.volmit.host.ModuleLogs;
import com.volmit.item.ItemRegistry;
import com.volmit.mantle.CarvedVoid;
import com.volmit.noise.NoiseFactory;
import com.volmit.object.PackObjects;
import com.volmit.placement.PlacementRecord;
import com.volmit.placement.Placements;
import com.volmit.resource.PackResources;
import com.volmit.resource.ResourceId;
import com.volmit.resource.ResourceKind;
import com.volmit.world.BiomeKind;
import com.volmit.world.BiomeSample;
import com.volmit.world.PackWorld;
import com.volmit.world.RunningWorld;
import com.volmit.world.TerrainSample;
import com.volmit.world.TerrainSampler;
import com.volmit.world.UpperDimension;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.function.IntBinaryOperator;

/**
 * A world as structure placement sees it, for tests: the declarations of the dimension, regions and biomes, the biome of
 * each column, its height and the voids carved into it. Everything is set before the first read.
 */
public final class StructureTestWorld implements PackWorld {
    public static final ResourceId DIMENSION = ResourceId.parse("test:dimension/overworld");
    public static final ResourceId REGION = ResourceId.parse("test:region/plains");
    public static final ResourceId SURFACE_BIOME = ResourceId.parse("test:biome/plains");
    private static final int WORLD_LIMIT_BLOCKS = 30_000_000;

    private PackResources resources = new FakePack();
    private final List<Entry<StructurePlacementSettings>> declarations = new ArrayList<>();
    private final List<StructureAssembly> assemblies = new ArrayList<>();
    private final Map<Class<?>, Object> states = new HashMap<>();
    private final AtomicInteger columnReads = new AtomicInteger();
    private final AtomicInteger placementScans = new AtomicInteger();
    private int minY = -64;
    private int height = 384;
    private int fluidHeight = 63;
    private long placementSeed = 0L;
    private Long worldSeed;
    private PackWorld host;
    private IntBinaryOperator highest = (x, z) -> 64;
    private BiomeColumns biomeColumns = (x, y, z) -> new BiomeSample(REGION, SURFACE_BIOME, Optional.empty());
    private CarvedColumns carved = (x, y, z) -> Optional.empty();

    /** The world that gives this one its pack, its services and the placement stages it runs. */
    public StructureTestWorld hostedBy(PackWorld value) {
        host = value;
        return this;
    }

    /** The world's own seed, when it differs from the one placement starts from. */
    public StructureTestWorld worldSeed(long value) {
        worldSeed = value;
        return this;
    }

    /** The pack the world generates, in place of the empty one. */
    public StructureTestWorld resources(PackResources value) {
        resources = value;
        return this;
    }

    public StructureTestWorld minY(int value) {
        minY = value;
        return this;
    }

    public StructureTestWorld height(int value) {
        height = value;
        return this;
    }

    public StructureTestWorld fluidHeight(int value) {
        fluidHeight = value;
        return this;
    }

    public StructureTestWorld placementSeed(long value) {
        placementSeed = value;
        return this;
    }

    public StructureTestWorld highest(IntBinaryOperator value) {
        highest = value;
        return this;
    }

    public StructureTestWorld biomes(BiomeColumns value) {
        biomeColumns = value;
        return this;
    }

    public StructureTestWorld carved(CarvedColumns value) {
        carved = value;
        return this;
    }

    public StructureTestWorld assembling(StructureAssembly assembly) {
        assemblies.add(assembly);
        return this;
    }

    public StructureTestWorld dimension(StructurePlacement... placements) {
        return declare(Level.DIMENSION, DIMENSION, placements);
    }

    public StructureTestWorld region(ResourceId region, StructurePlacement... placements) {
        return declare(Level.REGION, region, placements);
    }

    public StructureTestWorld biome(ResourceId biome, StructurePlacement... placements) {
        return declare(Level.BIOME, biome, placements);
    }

    private StructureTestWorld declare(Level level, ResourceId source, StructurePlacement[] placements) {
        declarations.add(new Entry<>(level, source, StructurePlacementFixtures.placementSettings(List.of(placements))));
        return this;
    }

    /** The placements the dimension declares. */
    public List<StructurePlacement> dimensionPlacements() {
        return declarations.stream().filter(entry -> entry.level() == Level.DIMENSION)
                .flatMap(entry -> entry.value().placements().stream()).toList();
    }

    /** How many times the placements of a chunk were worked out. */
    public int placementScans() {
        return placementScans.get();
    }

    /** How many columns placement asked the world's layout about. */
    public int columnReads() {
        return columnReads.get();
    }

    @Override
    public long seed() {
        return worldSeed == null ? placementSeed : worldSeed;
    }

    @Override
    public long seed(ResourceId purpose) {
        return placementSeed;
    }

    @Override
    public PackResources resources() {
        return host == null ? resources : host.resources();
    }

    @Override
    public PackSettings settings() {
        return new PackSettings() {
            @Override
            @SuppressWarnings("unchecked")
            public <F extends IrisFeature> SettingsStack<F> declared(Class<F> featureType) {
                return featureType == StructurePlacementSettings.class
                        ? (SettingsStack<F>) SettingsStack.of(declarations) : SettingsStack.empty();
            }

            @Override
            public PackResources resources() {
                return StructureTestWorld.this.resources();
            }
        };
    }

    @Override
    public int minWorldY() {
        return minY;
    }

    @Override
    public Optional<RunningWorld> running() {
        return Optional.of(new Running());
    }

    @Override
    public <T> T state(Class<T> type, Function<? super PackWorld, ? extends T> create) {
        if (type == StructurePlacementScope.ScopeIndex.class) {
            placementScans.incrementAndGet();
        }
        Object known = states.get(type);
        if (known == null) {
            known = create.apply(this);
            states.put(type, known);
        }
        return type.cast(known);
    }

    @Override
    public Placements placements() {
        if (host == null) {
            throw new UnsupportedOperationException("No placements here");
        }
        return host.placements();
    }

    @Override
    public ModuleLog log() {
        return ModuleLogs.discarding();
    }

    @Override
    @SuppressWarnings("unchecked")
    public <S> List<S> services(Class<S> service) {
        if (host != null) {
            return host.services(service);
        }
        return service == StructureAssembly.class ? (List<S>) List.copyOf(assemblies) : List.of();
    }

    /** The biome of a column at a height. */
    @FunctionalInterface
    public interface BiomeColumns {
        BiomeSample at(int worldX, int y, int worldZ);
    }

    /** The void carved into a block, if any. */
    @FunctionalInterface
    public interface CarvedColumns {
        Optional<CarvedVoid> at(int worldX, int y, int worldZ);
    }

    private final class Running implements RunningWorld {
        private final TerrainSampler layout = new Layout();

        @Override
        public TerrainSampler layout() {
            return layout;
        }

        @Override
        public FeatureSettings settings() {
            return new FeatureSettings() {
                @Override
                @SuppressWarnings("unchecked")
                public <F extends IrisFeature> SettingsStack<F> at(Class<F> featureType, int worldX, int worldZ) {
                    if (featureType != StructurePlacementSettings.class) {
                        return SettingsStack.empty();
                    }
                    columnReads.incrementAndGet();
                    BiomeSample column = biomeColumns.at(worldX, minY, worldZ);
                    List<Entry<StructurePlacementSettings>> here = new ArrayList<>();
                    for (Entry<StructurePlacementSettings> declaration : declarations) {
                        if (declaration.level() == Level.DIMENSION
                                || declaration.source().equals(column.region())
                                || declaration.source().equals(column.surfaceBiome())) {
                            here.add(declaration);
                        }
                    }
                    return (SettingsStack<F>) SettingsStack.of(here);
                }

                @Override
                @SuppressWarnings("unchecked")
                public <F extends IrisFeature> Optional<F> declaredBy(ResourceId biome, Class<F> featureType) {
                    if (featureType != StructurePlacementSettings.class) {
                        return Optional.empty();
                    }
                    return declarations.stream().filter(entry -> entry.level() == Level.BIOME && entry.source().equals(biome))
                            .map(entry -> (F) entry.value()).findFirst();
                }
            };
        }

        @Override
        public int highest(int worldX, int worldZ, boolean ignoreFluid) {
            return highest.applyAsInt(worldX, worldZ);
        }

        @Override
        public List<BlockPosition> caveFloors(int chunkX, int chunkZ) {
            return List.of();
        }

        @Override
        public Optional<PlacementRecord> placementAt(int worldX, int y, int worldZ) {
            return Optional.empty();
        }

        @Override
        public Optional<CarvedVoid> carvedAt(int worldX, int y, int worldZ) {
            return carved.at(worldX, y, worldZ);
        }

        @Override
        public <T> T saved(SavedKey<T> key) {
            throw new UnsupportedOperationException("No saved state here");
        }

        @Override
        public Optional<Sky> sky() {
            return Optional.empty();
        }

        @Override
        public Set<ResourceId> platformLootTables() {
            return Set.of();
        }

        @Override
        public boolean debug() {
            return false;
        }
    }

    private final class Layout implements TerrainSampler {
        @Override
        public BlockBox bounds() {
            return new BlockBox(-WORLD_LIMIT_BLOCKS, 0, -WORLD_LIMIT_BLOCKS, WORLD_LIMIT_BLOCKS, height, WORLD_LIMIT_BLOCKS);
        }

        @Override
        public TerrainSample column(int worldX, int worldZ) {
            throw new UnsupportedOperationException("No terrain columns here");
        }

        @Override
        public double surfaceY(int worldX, int worldZ) {
            return highest.applyAsInt(worldX, worldZ);
        }

        @Override
        public BiomeSample biome(int worldX, int y, int worldZ) {
            return biomeColumns.at(worldX, y, worldZ);
        }

        @Override
        public int fluidHeight() {
            return fluidHeight;
        }

        @Override
        public OptionalInt upperDimensionY(int worldX, int worldZ) {
            return OptionalInt.empty();
        }

        @Override
        public BiomeKind surfaceKind(int worldX, int worldZ) {
            throw new UnsupportedOperationException("No biome kinds here");
        }

        @Override
        public Optional<BlockState> surfaceBlock(int worldX, int worldZ) {
            return Optional.empty();
        }

        @Override
        public double biomeHeight(ResourceId biome, int worldX, int worldZ, long seed) {
            throw new UnsupportedOperationException("No biome heights here");
        }

        @Override
        public Optional<ResourceId> skyBiome(ResourceId biome, int worldX, int worldZ) {
            return Optional.empty();
        }

        @Override
        public int minWorldY() {
            return minY;
        }

        @Override
        public Optional<UpperDimension> upperDimension() {
            return Optional.empty();
        }
    }

    /** A pack with no resources at all: placement only reads its id. */
    private static final class FakePack implements PackResources {
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
            throw new UnsupportedOperationException("No blocks here");
        }

        @Override
        public ItemRegistry items() {
            throw new UnsupportedOperationException("No items here");
        }

        @Override
        public NoiseFactory noise() {
            throw new UnsupportedOperationException("No noise here");
        }

        @Override
        public PackObjects objects() {
            throw new UnsupportedOperationException("No objects here");
        }

        @Override
        public <T> Optional<T> find(ResourceKind<T> kind, ResourceId id) {
            return Optional.empty();
        }

        @Override
        public List<ResourceId> ids(ResourceKind<?> kind) {
            return List.of();
        }
    }
}
