package com.volmit.iris.core.nms.v1_20_R4;

import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.MapCodec;
import com.volmit.iris.Iris;
import com.volmit.iris.engine.framework.Engine;
import com.volmit.iris.engine.framework.ResultLocator;
import com.volmit.iris.engine.framework.WrongEngineBroException;
import com.volmit.iris.engine.object.IrisJigsawStructure;
import com.volmit.iris.engine.object.IrisJigsawStructurePlacement;
import com.volmit.iris.util.collection.KList;
import com.volmit.iris.util.collection.KMap;
import com.volmit.iris.util.collection.KSet;
import com.volmit.iris.util.mantle.MantleFlag;
import com.volmit.iris.util.math.Position2;
import com.volmit.iris.util.reflect.WrappedField;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.tags.TagKey;
import net.minecraft.util.random.WeightedRandomList;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.NoiseColumn;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.MobSpawnSettings;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.blending.Blender;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import org.bukkit.World;
import org.bukkit.craftbukkit.v1_20_R4.CraftWorld;
import org.bukkit.craftbukkit.v1_20_R4.generator.CustomChunkGenerator;

import javax.annotation.Nullable;
import java.lang.reflect.Field;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.*;

public class IrisChunkGenerator extends CustomChunkGenerator {
    private static final WrappedField<ChunkGenerator, BiomeSource> BIOME_SOURCE;
    private final ChunkGenerator delegate;
    private final Engine engine;
    private final KMap<ResourceKey<Structure>, KSet<String>> structures = new KMap<>();

    public IrisChunkGenerator(ChunkGenerator delegate, long seed, Engine engine, World world) {
        super(((CraftWorld) world).getHandle(), edit(delegate, new CustomBiomeSource(seed, engine, world)), null);
        this.delegate = delegate;
        this.engine = engine;
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

        var registry = ((CraftWorld) world).getHandle().registryAccess().registry(Registries.STRUCTURE).orElseThrow();
        for (var s : placements) {
            try {
                String raw = s.getStructureKey();
                if (raw == null) continue;
                boolean tag = raw.startsWith("#");
                if (tag) raw = raw.substring(1);

                var location = new ResourceLocation(raw);
                if (!tag) {
                    structures.computeIfAbsent(ResourceKey.create(Registries.STRUCTURE, location), k -> new KSet<>()).add(s.getLoadKey());
                    continue;
                }

                var key = TagKey.create(Registries.STRUCTURE, location);
                var set = registry.getTag(key).orElse(null);
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
    public void createStructures(RegistryAccess iregistrycustom, ChunkGeneratorStructureState chunkgeneratorstructurestate, StructureManager structuremanager, ChunkAccess ichunkaccess, StructureTemplateManager structuretemplatemanager) {
        delegate.createStructures(iregistrycustom, chunkgeneratorstructurestate, structuremanager, ichunkaccess, structuretemplatemanager);
    }

    @Override
    public void buildSurface(WorldGenRegion regionlimitedworldaccess, StructureManager structuremanager, RandomState randomstate, ChunkAccess ichunkaccess) {
        delegate.buildSurface(regionlimitedworldaccess, structuremanager, randomstate, ichunkaccess);
    }

    @Override
    public void applyCarvers(WorldGenRegion regionlimitedworldaccess, long seed, RandomState randomstate, BiomeManager biomemanager, StructureManager structuremanager, ChunkAccess ichunkaccess, GenerationStep.Carving worldgenstage_features) {
        delegate.applyCarvers(regionlimitedworldaccess, seed, randomstate, biomemanager, structuremanager, ichunkaccess, worldgenstage_features);
    }

    @Override
    public CompletableFuture<ChunkAccess> fillFromNoise(Executor executor, Blender blender, RandomState randomstate, StructureManager structuremanager, ChunkAccess ichunkaccess) {
        return delegate.fillFromNoise(executor, blender, randomstate, structuremanager, ichunkaccess);
    }

    @Override
    public int getBaseHeight(int i, int j, Heightmap.Types heightmap_type, LevelHeightAccessor levelheightaccessor, RandomState randomstate) {
        return delegate.getBaseHeight(i, j, heightmap_type, levelheightaccessor, randomstate);
    }

    @Override
    public WeightedRandomList<MobSpawnSettings.SpawnerData> getMobsAt(Holder<Biome> holder, StructureManager structuremanager, MobCategory enumcreaturetype, BlockPos blockposition) {
        return delegate.getMobsAt(holder, structuremanager, enumcreaturetype, blockposition);
    }

    @Override
    public void applyBiomeDecoration(WorldGenLevel generatoraccessseed, ChunkAccess ichunkaccess, StructureManager structuremanager) {
        delegate.applyBiomeDecoration(generatoraccessseed, ichunkaccess, structuremanager);
    }

    @Override
    public void addDebugScreenInfo(List<String> list, RandomState randomstate, BlockPos blockposition) {
        delegate.addDebugScreenInfo(list, randomstate, blockposition);
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
    public NoiseColumn getBaseColumn(int i, int j, LevelHeightAccessor levelheightaccessor, RandomState randomstate) {
        return delegate.getBaseColumn(i, j, levelheightaccessor, randomstate);
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
        BIOME_SOURCE = new WrappedField<>(ChunkGenerator.class, biomeSource.getName());
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
