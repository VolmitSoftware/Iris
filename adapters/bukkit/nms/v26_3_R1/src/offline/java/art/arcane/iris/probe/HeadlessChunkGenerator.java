package art.arcane.iris.probe;

import art.arcane.iris.generation.runtime.Engine;
import art.arcane.iris.generation.runtime.IrisEngine;
import art.arcane.iris.platform.bukkit.nms.BukkitBiomePolicy;
import art.arcane.iris.platform.bukkit.nms.BukkitTerrainColumnPolicy;
import art.arcane.volmlib.nativelib.v26_3_R1.terrain.NativeBiomeRegistryImpl;
import art.arcane.volmlib.nativelib.v26_3_R1.terrain.NativeBiomeSourceImpl;
import art.arcane.volmlib.nativelib.v26_3_R1.terrain.NativeTerrainColumns;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.NoiseColumn;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.blending.Blender;
import net.minecraft.world.level.levelgen.densityfunction.SamplerContext;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

final class HeadlessChunkGenerator extends ChunkGenerator {
    private final NativeTerrainColumns columns;
    private final int minimumY;
    private final int height;
    private final int seaLevel;

    HeadlessChunkGenerator(Options options) {
        super(biomes(options));
        Engine engine = options.engine();
        columns = new NativeTerrainColumns(new BukkitTerrainColumnPolicy(engine, null));
        minimumY = engine.getMinHeight();
        height = engine.getHeight();
        seaLevel = minimumY + engine.getDimension().getFluidHeight();
        if (engine instanceof IrisEngine irisEngine) {
            irisEngine.addGenerationRuntimeRetirementListener(columns::evictRuntime);
        }
    }

    @Override
    protected MapCodec<? extends ChunkGenerator> codec() {
        return MapCodec.unit(this);
    }

    @Override
    public int getGenDepth() {
        return height;
    }

    @Override
    public int getSeaLevel() {
        return seaLevel;
    }

    @Override
    public int getMinY() {
        return minimumY;
    }

    @Override
    public int getBaseHeight(int x, int z, Heightmap.Types type, LevelHeightAccessor accessor, RandomState random) {
        return columns.baseHeight(x, z, type, accessor);
    }

    @Override
    public NoiseColumn getBaseColumn(int x, int z, LevelHeightAccessor accessor, RandomState random) {
        return columns.baseColumn(x, z, accessor);
    }

    @Override
    public CompletableFuture<ChunkAccess> buildTerrain(ChunkAccess chunk, Blender blender, RandomState random,
            StructureManager structures, BiomeManager biomes, WorldGenRegion region, Set<Holder<Biome>> possibleBiomes) {
        throw new UnsupportedOperationException("Headless terrain must be generated through the Iris region pipeline");
    }

    @Override
    public void spawnOriginalMobs(WorldGenRegion region) {
        throw new UnsupportedOperationException("Headless native population is not installed");
    }

    @Override
    public void addDebugScreenInfo(List<String> result, RandomState random, BlockPos position, SamplerContext context) {
        result.add("Iris native structure terrain");
    }

    private static NativeBiomeSourceImpl biomes(Options options) {
        NativeBiomeRegistryImpl registry = new NativeBiomeRegistryImpl(options.registries().lookupOrThrow(Registries.BIOME));
        BukkitBiomePolicy<Holder<Biome>, Biome> policy = new BukkitBiomePolicy<>(
                new BukkitBiomePolicy.RuntimeOptions<>(options.engine().getSeedManager().getSeed(), options.engine(),
                        null, () -> registry), registry);
        return new NativeBiomeSourceImpl(policy);
    }

    record Options(Engine engine, RegistryAccess registries) {
        Options {
            Objects.requireNonNull(engine, "engine");
            Objects.requireNonNull(registries, "registries");
        }
    }
}
