package com.volmit.iris.core.nms.v1_21_R4;

import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.MapCodec;
import com.volmit.iris.Iris;
import com.volmit.iris.engine.framework.Engine;
import com.volmit.iris.engine.framework.ResultLocator;
import com.volmit.iris.engine.framework.WrongEngineBroException;
import com.volmit.iris.engine.object.IrisJigsawStructure;
import com.volmit.iris.engine.object.IrisJigsawStructurePlacement;
import com.volmit.iris.engine.object.IrisStructurePopulator;
import com.volmit.iris.util.collection.KList;
import com.volmit.iris.util.collection.KMap;
import com.volmit.iris.util.collection.KSet;
import com.volmit.iris.util.mantle.flag.MantleFlag;
import com.volmit.iris.util.math.Position2;
import com.volmit.iris.util.reflect.WrappedField;
import com.volmit.iris.util.reflect.WrappedReturningMethod;
import net.minecraft.CrashReport;
import net.minecraft.CrashReportCategory;
import net.minecraft.ReportedException;
import net.minecraft.core.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.tags.StructureTags;
import net.minecraft.tags.TagKey;
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
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.craftbukkit.v1_21_R4.CraftWorld;
import org.bukkit.craftbukkit.v1_21_R4.generator.CustomChunkGenerator;
import org.bukkit.craftbukkit.v1_21_R4.generator.structure.CraftStructure;
import org.bukkit.event.world.AsyncStructureSpawnEvent;
import org.spigotmc.SpigotWorldConfig;

import javax.annotation.Nullable;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.function.Supplier;

public class IrisChunkGenerator extends CustomChunkGenerator {
    private static final WrappedField<ChunkGenerator, BiomeSource> BIOME_SOURCE;
    private static final WrappedReturningMethod<Heightmap, Object> SET_HEIGHT;
    private final ChunkGenerator delegate;
    private final Engine engine;
    private final KMap<ResourceKey<Structure>, KSet<String>> structures = new KMap<>();
    private final IrisStructurePopulator populator;

    public IrisChunkGenerator(ChunkGenerator delegate, long seed, Engine engine, World world) {
        super(((CraftWorld) world).getHandle(), edit(delegate, new CustomBiomeSource(seed, engine, world)), null);
        this.delegate = delegate;
        this.engine = engine;
        this.populator = new IrisStructurePopulator(engine);
        var dimension = engine.getDimension();

        KSet<IrisJigsawStructure> placements = new KSet<>();
        addAll(dimension.getJigsawStructures(), placements);
        for (var region : dimension.getAllRegions(engine)) {
            addAll(region.getJigsawStructures(), placements);
            for (var biome : region.getAllBiomes(engine))
                addAll(biome.getJigsawStructures(), placements);
        }
        var stronghold = dimension.getStronghold();
        if (stronghold != null)
            placements.add(engine.getData().getJigsawStructureLoader().load(stronghold));
        placements.removeIf(Objects::isNull);

        var registry = ((CraftWorld) world).getHandle().registryAccess().lookup(Registries.STRUCTURE).orElseThrow();
        for (var s : placements) {
            try {
                String raw = s.getStructureKey();
                if (raw == null) continue;
                boolean tag = raw.startsWith("#");
                if (tag) raw = raw.substring(1);

                var location = ResourceLocation.parse(raw);
                if (!tag) {
                    structures.computeIfAbsent(ResourceKey.create(Registries.STRUCTURE, location), k -> new KSet<>()).add(s.getLoadKey());
                    continue;
                }

                var key = TagKey.create(Registries.STRUCTURE, location);
                var set = registry.get(key).orElse(null);
                if (set == null) {
                    Iris.error("Could not find structure tag: " + raw);
                    continue;
                }
                for (var holder : set) {
                    var resourceKey = holder.unwrapKey().orElse(null);
                    if (resourceKey == null) continue;
                    structures.computeIfAbsent(resourceKey, k -> new KSet<>()).add(s.getLoadKey());
                }
            } catch (Throwable e) {
                Iris.error("Failed to load structure: " + s.getLoadKey());
                e.printStackTrace();
            }
        }
    }

    private void addAll(KList<IrisJigsawStructurePlacement> placements, KSet<IrisJigsawStructure> structures) {
        if (placements == null) return;
        placements.stream()
                .map(IrisJigsawStructurePlacement::getStructure)
                .map(engine.getData().getJigsawStructureLoader()::load)
                .filter(Objects::nonNull)
                .forEach(structures::add);
    }

    @Override
    public @Nullable Pair<BlockPos, Holder<Structure>> findNearestMapStructure(ServerLevel level, HolderSet<Structure> holders, BlockPos pos, int radius, boolean findUnexplored) {
        if (holders.size() == 0) return null;
        if (holders.unwrapKey().orElse(null) == StructureTags.EYE_OF_ENDER_LOCATED) {
            var next = engine.getNearestStronghold(new Position2(pos.getX(), pos.getZ()));
            return next == null ? null : new Pair<>(new BlockPos(next.getX(), 0, next.getZ()), holders.get(0));
        }
        if (engine.getDimension().isDisableExplorerMaps())
            return null;

        KMap<String, Holder<Structure>> structures = new KMap<>();
        for (var holder : holders) {
            if (holder == null) continue;
            var key = holder.unwrapKey().orElse(null);
            var set = this.structures.get(key);
            if (set == null) continue;
            for (var structure : set) {
                structures.put(structure, holder);
            }
        }
        if (structures.isEmpty())
            return null;

        var locator = ResultLocator.locateStructure(structures.keySet())
                .then((e, p , s) -> structures.get(s.getLoadKey()));
        if (findUnexplored)
            locator = locator.then((e, p, s) -> e.getMantle().getMantle().getChunk(p.getX(), p.getZ()).isFlagged(MantleFlag.DISCOVERED) ? null : s);

        try {
            var result = locator.find(engine, new Position2(pos.getX() >> 4, pos.getZ() >> 4), radius * 10L, i -> {}, false).get();
            if (result == null) return null;
            var blockPos = new BlockPos(result.getBlockX(), 0, result.getBlockZ());
            return Pair.of(blockPos, result.obj());
        } catch (WrongEngineBroException | ExecutionException | InterruptedException e) {
            return null;
        }
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
        var chunkPos = access.getPos();
        var sectionPos = SectionPos.bottomOf(access);
        var registry = registryAccess.lookupOrThrow(Registries.STRUCTURE);
        populator.populateStructures(chunkPos.x, chunkPos.z, (key, ignoreBiomes) -> {
            var loc = ResourceLocation.tryParse(key);
            if (loc == null) return false;
            var holder = registry.get(loc).orElse(null);
            if (holder == null) return false;
            var structure = holder.value();
            var biomes = structure.biomes();

            var start = structure.generate(
                    holder,
                    levelKey,
                    registryAccess,
                    this,
                    biomeSource,
                    structureState.randomState(),
                    templateManager,
                    structureState.getLevelSeed(),
                    chunkPos,
                    fetchReferences(structureManager, access, sectionPos, structure),
                    access,
                    biome -> ignoreBiomes || biomes.contains(biome)
            );

            if (!start.isValid())
                return false;

            BoundingBox box = start.getBoundingBox();
            AsyncStructureSpawnEvent event = new AsyncStructureSpawnEvent(
                    structureManager.level.getMinecraftWorld().getWorld(),
                    CraftStructure.minecraftToBukkit(structure),
                    new org.bukkit.util.BoundingBox(
                            box.minX(),
                            box.minY(),
                            box.minZ(),
                            box.maxX(),
                            box.maxY(),
                            box.maxZ()
                    ), chunkPos.x, chunkPos.z);
            Bukkit.getPluginManager().callEvent(event);
            if (!event.isCancelled()) {
                structureManager.setStartForStructure(sectionPos, structure, start, access);
            }
            return true;
        });
    }

    private static int fetchReferences(StructureManager structureManager, ChunkAccess access, SectionPos sectionPos, Structure structure) {
        StructureStart structurestart = structureManager.getStartForStructure(sectionPos, structure, access);
        return structurestart != null ? structurestart.getReferences() : 0;
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

        SectionPos sectionPos = SectionPos.of(chunkAccess.getPos(), level.getMinSectionY());
        BlockPos blockPos = sectionPos.origin();
        WorldgenRandom random = new WorldgenRandom(new XoroshiroRandomSource(RandomSupport.generateUniqueSeed()));
        long i = random.setDecorationSeed(level.getSeed(), blockPos.getX(), blockPos.getZ());
        var structures = level.registryAccess().lookupOrThrow(Registries.STRUCTURE);
        var list = structures.stream()
                .sorted(Comparator.comparingInt(s -> s.step().ordinal()))
                .toList();

        var surface = chunkAccess.getOrCreateHeightmapUnprimed(Heightmap.Types.WORLD_SURFACE_WG);
        var ocean = chunkAccess.getOrCreateHeightmapUnprimed(Heightmap.Types.OCEAN_FLOOR_WG);
        var motion = chunkAccess.getOrCreateHeightmapUnprimed(Heightmap.Types.MOTION_BLOCKING);
        var motionNoLeaves = chunkAccess.getOrCreateHeightmapUnprimed(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES);

        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                int wX = x + blockPos.getX();
                int wZ = z + blockPos.getZ();

                int noAir = engine.getHeight(wX, wZ, false) + engine.getMinHeight() + 1;
                int noFluid = engine.getHeight(wX, wZ, true) + engine.getMinHeight() + 1;
                SET_HEIGHT.invoke(ocean, x, z, Math.min(noFluid, ocean.getFirstAvailable(x, z)));
                SET_HEIGHT.invoke(surface, x, z, Math.min(noAir, surface.getFirstAvailable(x, z)));
                SET_HEIGHT.invoke(motion, x, z, Math.min(noAir, motion.getFirstAvailable(x, z)));
                SET_HEIGHT.invoke(motionNoLeaves, x, z, Math.min(noAir, motionNoLeaves.getFirstAvailable(x, z)));
            }
        }

        for (int j = 0; j < list.size(); j++) {
            Structure structure = list.get(j);
            random.setFeatureSeed(i, j, structure.step().ordinal());
            Supplier<String> supplier = () -> structures.getResourceKey(structure).map(Object::toString).orElseGet(structure::toString);

            try {
                level.setCurrentlyGenerating(supplier);
                structureManager.startsForStructure(sectionPos, structure).forEach((start) -> start.placeInChunk(level, structureManager, this, random, getWritableArea(chunkAccess), chunkAccess.getPos()));
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

    @Override
    @SuppressWarnings("deprecation")
    public BiomeGenerationSettings getBiomeGenerationSettings(Holder<Biome> holder) {
        return delegate.getBiomeGenerationSettings(holder);
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
