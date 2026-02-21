package art.arcane.iris.core.nms.v1_21_R7;

import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.MapCodec;
import art.arcane.iris.Iris;
import art.arcane.iris.core.ExternalDataPackPipeline;
import art.arcane.iris.core.IrisSettings;
import art.arcane.iris.engine.framework.Engine;
import art.arcane.iris.util.common.reflect.WrappedField;
import art.arcane.iris.util.common.reflect.WrappedReturningMethod;
import net.minecraft.CrashReport;
import net.minecraft.CrashReportCategory;
import net.minecraft.ReportedException;
import net.minecraft.core.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.util.random.WeightedList;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.*;
import net.minecraft.world.level.biome.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.*;
import net.minecraft.world.level.levelgen.blending.Blender;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.craftbukkit.v1_21_R7.CraftWorld;
import org.bukkit.craftbukkit.v1_21_R7.generator.CustomChunkGenerator;
import org.spigotmc.SpigotWorldConfig;

import javax.annotation.Nullable;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

public class IrisChunkGenerator extends CustomChunkGenerator {
    private static final WrappedField<ChunkGenerator, BiomeSource> BIOME_SOURCE;
    private static final WrappedReturningMethod<Heightmap, Object> SET_HEIGHT;
    private static final int EXTERNAL_FOUNDATION_MAX_DEPTH = 96;
    private final ChunkGenerator delegate;
    private final Engine engine;
    private volatile Registry<Structure> cachedStructureRegistry;
    private volatile Map<Structure, Integer> cachedStructureOrder;

    public IrisChunkGenerator(ChunkGenerator delegate, long seed, Engine engine, World world) {
        super(((CraftWorld) world).getHandle(), edit(delegate, new CustomBiomeSource(seed, engine, world)), null);
        this.delegate = delegate;
        this.engine = engine;
    }

    @Override
    public @Nullable Pair<BlockPos, Holder<Structure>> findNearestMapStructure(ServerLevel level, HolderSet<Structure> holders, BlockPos pos, int radius, boolean findUnexplored) {
        if (holders.size() == 0) return null;
        if (engine.getDimension().isDisableExplorerMaps())
            return null;
        return delegate.findNearestMapStructure(level, holders, pos, radius, findUnexplored);
    }

    @Override
    protected MapCodec<? extends ChunkGenerator> codec() {
        return MapCodec.unit(null);
    }

    @Override
    public ChunkGenerator getDelegate() {
        if (delegate instanceof CustomChunkGenerator chunkGenerator)
            return chunkGenerator.getDelegate();
        return delegate;
    }

    @Override
    public int getMinY() {
        return delegate.getMinY();
    }

    @Override
    public int getSeaLevel() {
        return delegate.getSeaLevel();
    }

    @Override
    public void createStructures(RegistryAccess registryAccess, ChunkGeneratorStructureState structureState, StructureManager structureManager, ChunkAccess access, StructureTemplateManager templateManager, ResourceKey<Level> levelKey) {
        if (!structureManager.shouldGenerateStructures())
            return;
        if (!IrisSettings.get().getGeneral().isAutoGenerateIntrinsicStructures()) {
            return;
        }
        delegate.createStructures(registryAccess, structureState, structureManager, access, templateManager, levelKey);
    }

    @Override
    public ChunkGeneratorStructureState createState(HolderLookup<StructureSet> holderlookup, RandomState randomstate, long i, SpigotWorldConfig conf) {
        return delegate.createState(holderlookup, randomstate, i, conf);
    }

    @Override
    public void createReferences(WorldGenLevel generatoraccessseed, StructureManager structuremanager, ChunkAccess ichunkaccess) {
        delegate.createReferences(generatoraccessseed, structuremanager, ichunkaccess);
    }

    @Override
    public CompletableFuture<ChunkAccess> createBiomes(RandomState randomstate, Blender blender, StructureManager structuremanager, ChunkAccess ichunkaccess) {
        return delegate.createBiomes(randomstate, blender, structuremanager, ichunkaccess);
    }

    @Override
    public void buildSurface(WorldGenRegion regionlimitedworldaccess, StructureManager structuremanager, RandomState randomstate, ChunkAccess ichunkaccess) {
        delegate.buildSurface(regionlimitedworldaccess, structuremanager, randomstate, ichunkaccess);
    }

    @Override
    public void applyCarvers(WorldGenRegion regionlimitedworldaccess, long seed, RandomState randomstate, BiomeManager biomemanager, StructureManager structuremanager, ChunkAccess ichunkaccess) {
        delegate.applyCarvers(regionlimitedworldaccess, seed, randomstate, biomemanager, structuremanager, ichunkaccess);
    }

    @Override
    public CompletableFuture<ChunkAccess> fillFromNoise(Blender blender, RandomState randomstate, StructureManager structuremanager, ChunkAccess ichunkaccess) {
        return delegate.fillFromNoise(blender, randomstate, structuremanager, ichunkaccess);
    }

    @Override
    public WeightedList<MobSpawnSettings.SpawnerData> getMobsAt(Holder<Biome> holder, StructureManager structuremanager, MobCategory enumcreaturetype, BlockPos blockposition) {
        return delegate.getMobsAt(holder, structuremanager, enumcreaturetype, blockposition);
    }

    @Override
    public void applyBiomeDecoration(WorldGenLevel generatoraccessseed, ChunkAccess ichunkaccess, StructureManager structuremanager) {
        applyBiomeDecoration(generatoraccessseed, ichunkaccess, structuremanager, true);
    }

    @Override
    public void addDebugScreenInfo(List<String> list, RandomState randomstate, BlockPos blockposition) {
        delegate.addDebugScreenInfo(list, randomstate, blockposition);
    }

    @Override
    public void applyBiomeDecoration(WorldGenLevel generatoraccessseed, ChunkAccess ichunkaccess, StructureManager structuremanager, boolean vanilla) {
        addVanillaDecorations(generatoraccessseed, ichunkaccess, structuremanager);
        delegate.applyBiomeDecoration(generatoraccessseed, ichunkaccess, structuremanager, false);
    }

    @Override
    public void addVanillaDecorations(WorldGenLevel level, ChunkAccess chunkAccess, StructureManager structureManager) {
        if (!structureManager.shouldGenerateStructures())
            return;
        if (!IrisSettings.get().getGeneral().isAutoGenerateIntrinsicStructures()) {
            return;
        }

        SectionPos sectionPos = SectionPos.of(chunkAccess.getPos(), level.getMinSectionY());
        BlockPos blockPos = sectionPos.origin();
        WorldgenRandom random = new WorldgenRandom(new XoroshiroRandomSource(RandomSupport.generateUniqueSeed()));
        long i = random.setDecorationSeed(level.getSeed(), blockPos.getX(), blockPos.getZ());
        Registry<Structure> structureRegistry = level.registryAccess().lookupOrThrow(Registries.STRUCTURE);
        Map<Structure, Integer> structureOrder = getStructureOrder(structureRegistry);

        Heightmap surface = chunkAccess.getOrCreateHeightmapUnprimed(Heightmap.Types.WORLD_SURFACE_WG);
        Heightmap ocean = chunkAccess.getOrCreateHeightmapUnprimed(Heightmap.Types.OCEAN_FLOOR_WG);
        Heightmap motion = chunkAccess.getOrCreateHeightmapUnprimed(Heightmap.Types.MOTION_BLOCKING);
        Heightmap motionNoLeaves = chunkAccess.getOrCreateHeightmapUnprimed(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES);

        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                int wX = x + blockPos.getX();
                int wZ = z + blockPos.getZ();

                int noAir = engine.getHeight(wX, wZ, false) + engine.getMinHeight() + 1;
                int noFluid = engine.getHeight(wX, wZ, true) + engine.getMinHeight() + 1;
                int oceanHeight = ocean.getFirstAvailable(x, z);
                int surfaceHeight = surface.getFirstAvailable(x, z);
                int motionHeight = motion.getFirstAvailable(x, z);
                int motionNoLeavesHeight = motionNoLeaves.getFirstAvailable(x, z);
                if (noFluid > oceanHeight) {
                    SET_HEIGHT.invoke(ocean, x, z, noFluid);
                }
                if (noAir > surfaceHeight) {
                    SET_HEIGHT.invoke(surface, x, z, noAir);
                }
                if (noAir > motionHeight) {
                    SET_HEIGHT.invoke(motion, x, z, noAir);
                }
                if (noAir > motionNoLeavesHeight) {
                    SET_HEIGHT.invoke(motionNoLeaves, x, z, noAir);
                }
            }
        }

        List<StructureStart> starts = new ArrayList<>(structureManager.startsForStructure(chunkAccess.getPos(), structure -> true));
        starts.sort(Comparator.comparingInt(start -> structureOrder.getOrDefault(start.getStructure(), Integer.MAX_VALUE)));
        Set<String> externalLocateStructures = ExternalDataPackPipeline.snapshotLocateStructureKeys();

        int seededStructureIndex = Integer.MIN_VALUE;
        for (int j = 0; j < starts.size(); j++) {
            StructureStart start = starts.get(j);
            Structure structure = start.getStructure();
            int structureIndex = structureOrder.getOrDefault(structure, j);
            if (structureIndex != seededStructureIndex) {
                random.setFeatureSeed(i, structureIndex, structure.step().ordinal());
                seededStructureIndex = structureIndex;
            }
            Supplier<String> supplier = () -> structureRegistry.getResourceKey(structure).map(Object::toString).orElseGet(structure::toString);
            String structureKey = supplier.get().toLowerCase(Locale.ROOT);
            boolean isExternalLocateStructure = externalLocateStructures.contains(structureKey);
            BitSet[] beforeSolidColumns = null;
            if (isExternalLocateStructure) {
                beforeSolidColumns = snapshotChunkSolidColumns(level, chunkAccess);
            }

            try {
                level.setCurrentlyGenerating(supplier);
                start.placeInChunk(level, structureManager, this, random, getWritableArea(chunkAccess), chunkAccess.getPos());
                if (isExternalLocateStructure && beforeSolidColumns != null) {
                    applyExternalStructureFoundations(level, chunkAccess, beforeSolidColumns, EXTERNAL_FOUNDATION_MAX_DEPTH);
                }
            } catch (Exception exception) {
                CrashReport crashReport = CrashReport.forThrowable(exception, "Feature placement");
                CrashReportCategory category = crashReport.addCategory("Feature");
                category.setDetail("Description", supplier::get);
                throw new ReportedException(crashReport);
            }
        }

        Heightmap.primeHeightmaps(chunkAccess, ChunkStatus.FINAL_HEIGHTMAPS);
    }

    private static BoundingBox getWritableArea(ChunkAccess ichunkaccess) {
        ChunkPos chunkPos = ichunkaccess.getPos();
        int minX = chunkPos.getMinBlockX();
        int minZ = chunkPos.getMinBlockZ();
        LevelHeightAccessor heightAccessor = ichunkaccess.getHeightAccessorForGeneration();
        int minY = heightAccessor.getMinY() + 1;
        int maxY = heightAccessor.getMaxY();
        return new BoundingBox(minX, minY, minZ, minX + 15, maxY, minZ + 15);
    }

    private static BitSet[] snapshotChunkSolidColumns(WorldGenLevel level, ChunkAccess chunkAccess) {
        int minY = level.getMinY();
        int maxY = level.getMaxY();
        int ySpan = maxY - minY;
        if (ySpan <= 0) {
            return new BitSet[0];
        }

        ChunkPos chunkPos = chunkAccess.getPos();
        int minX = chunkPos.getMinBlockX();
        int minZ = chunkPos.getMinBlockZ();
        BitSet[] columns = new BitSet[16 * 16];
        BlockPos.MutableBlockPos mutablePos = new BlockPos.MutableBlockPos();
        for (int localX = 0; localX < 16; localX++) {
            for (int localZ = 0; localZ < 16; localZ++) {
                int index = (localX << 4) | localZ;
                BitSet solids = new BitSet(ySpan);
                int worldX = minX + localX;
                int worldZ = minZ + localZ;
                for (int y = minY; y < maxY; y++) {
                    mutablePos.set(worldX, y, worldZ);
                    if (isFoundationSolid(level.getBlockState(mutablePos))) {
                        solids.set(y - minY);
                    }
                }
                columns[index] = solids;
            }
        }

        return columns;
    }

    private static void applyExternalStructureFoundations(
            WorldGenLevel level,
            ChunkAccess chunkAccess,
            BitSet[] beforeSolidColumns,
            int maxDepth
    ) {
        if (beforeSolidColumns == null || beforeSolidColumns.length == 0 || maxDepth <= 0) {
            return;
        }

        int minY = level.getMinY();
        int maxY = level.getMaxY();
        int ySpan = maxY - minY;
        if (ySpan <= 0) {
            return;
        }

        ChunkPos chunkPos = chunkAccess.getPos();
        int minX = chunkPos.getMinBlockX();
        int minZ = chunkPos.getMinBlockZ();
        BlockPos.MutableBlockPos mutablePos = new BlockPos.MutableBlockPos();
        for (int localX = 0; localX < 16; localX++) {
            for (int localZ = 0; localZ < 16; localZ++) {
                int index = (localX << 4) | localZ;
                BitSet before = beforeSolidColumns[index];
                if (before == null) {
                    continue;
                }

                int worldX = minX + localX;
                int worldZ = minZ + localZ;
                int lowestNewSolidY = Integer.MIN_VALUE;
                for (int y = minY; y < maxY; y++) {
                    mutablePos.set(worldX, y, worldZ);
                    BlockState state = level.getBlockState(mutablePos);
                    if (!isFoundationSolid(state)) {
                        continue;
                    }

                    if (before.get(y - minY)) {
                        continue;
                    }

                    lowestNewSolidY = y;
                    break;
                }

                if (lowestNewSolidY == Integer.MIN_VALUE) {
                    continue;
                }

                mutablePos.set(worldX, lowestNewSolidY, worldZ);
                BlockState foundationState = level.getBlockState(mutablePos);
                if (!isFoundationSolid(foundationState)) {
                    continue;
                }

                int depth = 0;
                for (int y = lowestNewSolidY - 1; y >= minY && depth < maxDepth; y--) {
                    mutablePos.set(worldX, y, worldZ);
                    BlockState state = level.getBlockState(mutablePos);
                    if (isFoundationSolid(state)) {
                        break;
                    }

                    level.setBlock(mutablePos, foundationState, 2);
                    depth++;
                }
            }
        }
    }

    private static boolean isFoundationSolid(BlockState state) {
        if (state == null || state.isAir()) {
            return false;
        }
        if (!state.getFluidState().isEmpty()) {
            return false;
        }
        return Heightmap.Types.MOTION_BLOCKING_NO_LEAVES.isOpaque().test(state);
    }

    private Map<Structure, Integer> getStructureOrder(Registry<Structure> structureRegistry) {
        Map<Structure, Integer> localOrder = cachedStructureOrder;
        Registry<Structure> localRegistry = cachedStructureRegistry;
        if (localRegistry == structureRegistry && localOrder != null) {
            return localOrder;
        }

        synchronized (this) {
            Map<Structure, Integer> synchronizedOrder = cachedStructureOrder;
            Registry<Structure> synchronizedRegistry = cachedStructureRegistry;
            if (synchronizedRegistry == structureRegistry && synchronizedOrder != null) {
                return synchronizedOrder;
            }

            List<Structure> sortedStructures = structureRegistry.stream()
                    .sorted(Comparator.comparingInt(structure -> structure.step().ordinal()))
                    .toList();
            Map<Structure, Integer> builtOrder = new IdentityHashMap<>(sortedStructures.size());
            for (int index = 0; index < sortedStructures.size(); index++) {
                Structure structure = sortedStructures.get(index);
                builtOrder.put(structure, index);
            }

            cachedStructureRegistry = structureRegistry;
            cachedStructureOrder = builtOrder;
            return builtOrder;
        }
    }

    @Override
    public void spawnOriginalMobs(WorldGenRegion regionlimitedworldaccess) {
        delegate.spawnOriginalMobs(regionlimitedworldaccess);
    }

    @Override
    public int getSpawnHeight(LevelHeightAccessor levelheightaccessor) {
        return delegate.getSpawnHeight(levelheightaccessor);
    }

    @Override
    public int getGenDepth() {
        return delegate.getGenDepth();
    }

    @Override
    public int getBaseHeight(int i, int j, Heightmap.Types heightmap_type, LevelHeightAccessor levelheightaccessor, RandomState randomstate) {
        return levelheightaccessor.getMinY() + engine.getHeight(i, j, !heightmap_type.isOpaque().test(Blocks.WATER.defaultBlockState())) + 1;
    }

    @Override
    public NoiseColumn getBaseColumn(int i, int j, LevelHeightAccessor levelheightaccessor, RandomState randomstate) {
        int block = engine.getHeight(i, j, true);
        int water = engine.getHeight(i, j, false);
        BlockState[] column = new BlockState[levelheightaccessor.getHeight()];
        for (int k = 0; k < column.length; k++) {
            if (k <= block) column[k] = Blocks.STONE.defaultBlockState();
            else if (k <= water) column[k] = Blocks.WATER.defaultBlockState();
            else column[k] = Blocks.AIR.defaultBlockState();
        }
        return new NoiseColumn(levelheightaccessor.getMinY(), column);
    }

    @Override
    public Optional<ResourceKey<MapCodec<? extends ChunkGenerator>>> getTypeNameForDataFixer() {
        return delegate.getTypeNameForDataFixer();
    }

    @Override
    public void validate() {
        delegate.validate();
    }

    static {
        Field biomeSource = null;
        for (Field field : ChunkGenerator.class.getDeclaredFields()) {
            if (!field.getType().equals(BiomeSource.class))
                continue;
            biomeSource = field;
            break;
        }
        if (biomeSource == null)
            throw new RuntimeException("Could not find biomeSource field in ChunkGenerator!");

        Method setHeight = null;
        for (Method method : Heightmap.class.getDeclaredMethods()) {
            var types = method.getParameterTypes();
            if (types.length != 3 || !Arrays.equals(types, new Class<?>[]{int.class, int.class, int.class})
                    || !method.getReturnType().equals(void.class))
                continue;
            setHeight = method;
            break;
        }
        if (setHeight == null)
            throw new RuntimeException("Could not find setHeight method in Heightmap!");

        BIOME_SOURCE = new WrappedField<>(ChunkGenerator.class, biomeSource.getName());
        SET_HEIGHT = new WrappedReturningMethod<>(Heightmap.class, setHeight.getName(), setHeight.getParameterTypes());
    }

    private static ChunkGenerator edit(ChunkGenerator generator, BiomeSource source) {
        try {
            BIOME_SOURCE.set(generator, source);
            if (generator instanceof CustomChunkGenerator custom)
                BIOME_SOURCE.set(custom.getDelegate(), source);

            return generator;
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }
}
