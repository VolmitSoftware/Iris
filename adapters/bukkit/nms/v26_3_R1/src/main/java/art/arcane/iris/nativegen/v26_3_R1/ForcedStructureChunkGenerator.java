package art.arcane.iris.nativegen.v26_3_R1;

import net.minecraft.world.level.levelgen.densityfunction.SamplerContext;
import java.util.Set;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.NoiseColumn;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.biome.FixedBiomeSource;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.blending.Blender;

import java.util.List;
import java.util.concurrent.CompletableFuture;

final class ForcedStructureChunkGenerator extends ChunkGenerator {
    private final ChunkGenerator delegate;

    ForcedStructureChunkGenerator(ChunkGenerator delegate, Holder<Biome> sourceBiome) {
        super(new FixedBiomeSource(sourceBiome));
        this.delegate = delegate;
    }

    @Override
    protected MapCodec<? extends ChunkGenerator> codec() {
        return MapCodec.unit(this);
    }

    @Override
    public void spawnOriginalMobs(WorldGenRegion worldGenRegion) {
        delegate.spawnOriginalMobs(worldGenRegion);
    }

    @Override
    public int getGenDepth() {
        return delegate.getGenDepth();
    }

    @Override
    public CompletableFuture<ChunkAccess> buildTerrain(ChunkAccess centerChunk, Blender blender, RandomState randomState,
                                                         StructureManager structureManager, BiomeManager biomeManager,
                                                         WorldGenRegion carverBiomeRegion, Set<Holder<Biome>> possibleBiomes) {
        return delegate.buildTerrain(
                centerChunk, blender, randomState, structureManager, biomeManager, carverBiomeRegion, possibleBiomes);
    }

    @Override
    public int getSeaLevel() {
        return delegate.getSeaLevel();
    }

    @Override
    public int getMinY() {
        return delegate.getMinY();
    }

    @Override
    public int getBaseHeight(int x, int z, Heightmap.Types type,
                             LevelHeightAccessor heightAccessor, RandomState randomState) {
        return delegate.getBaseHeight(x, z, type, heightAccessor, randomState);
    }

    @Override
    public NoiseColumn getBaseColumn(int x, int z, LevelHeightAccessor heightAccessor,
                                     RandomState randomState) {
        return delegate.getBaseColumn(x, z, heightAccessor, randomState);
    }

    @Override
    public void addDebugScreenInfo(List<String> result, RandomState randomState,
                                   BlockPos feetPos, SamplerContext samplerContext) {
        delegate.addDebugScreenInfo(result, randomState, feetPos, samplerContext);
    }

}
