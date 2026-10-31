package art.arcane.volmlib.nativelib.minecraft26_2.modded;

import art.arcane.volmlib.nativelib.terrain.NativeBlockState;
import com.mojang.serialization.Codec;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.IdMapper;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeGenerationSettings;
import net.minecraft.world.level.biome.BiomeSpecialEffects;
import net.minecraft.world.level.biome.MobSpawnSettings;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.PalettedContainer;
import net.minecraft.world.level.chunk.PalettedContainerFactory;
import net.minecraft.world.level.chunk.PalettedContainerRO;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.chunk.Strategy;
import net.minecraft.world.level.chunk.UpgradeData;
import org.junit.Assume;
import org.junit.BeforeClass;
import org.junit.Test;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class NativeModdedChunkGeneratorTerrainWriteTest {
    private static final int MIN_Y = -64;
    private static final int HEIGHT = 384;
    private static final int SURFACE = 10;

    @BeforeClass
    public static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    public void freshSectionsInstallWithoutSelfLockingTheirContainers() throws Exception {
        BlockState stone = Blocks.STONE.defaultBlockState();
        Assume.assumeTrue("block-state ids are not baked on this classpath",
                Block.BLOCK_STATE_REGISTRY.getId(stone) >= 0);
        NativeBlockState nativeStone = mock(NativeBlockState.class);
        when(nativeStone.nativeHandle()).thenReturn(stone);
        NativeModdedGeneratorPolicy.BlockBuffer blocks = (x, y, z) -> y + MIN_Y < SURFACE ? nativeStone : null;
        ProtoChunk chunk = newChunk();
        Method writeBlocks = NativeModdedChunkGenerator.class.getDeclaredMethod("writeBlocks", ChunkAccess.class,
                NativeModdedGeneratorPolicy.BlockBuffer.class, int.class, int.class);
        writeBlocks.setAccessible(true);

        CompletableFuture.runAsync(() -> invoke(writeBlocks, chunk, blocks)).get(30, TimeUnit.SECONDS);

        assertSame(stone, chunk.getBlockState(new BlockPos(3, SURFACE - 1, 5)));
        assertSame(stone, chunk.getBlockState(new BlockPos(0, MIN_Y, 0)));
        assertSame(Blocks.AIR.defaultBlockState(), chunk.getBlockState(new BlockPos(3, SURFACE, 5)));
    }

    private static void invoke(Method writeBlocks, ChunkAccess chunk, NativeModdedGeneratorPolicy.BlockBuffer blocks) {
        try {
            writeBlocks.invoke(null, chunk, blocks, MIN_Y, HEIGHT);
        } catch (IllegalAccessException | InvocationTargetException failure) {
            throw new IllegalStateException(failure);
        }
    }

    private static ProtoChunk newChunk() {
        return new ProtoChunk(new ChunkPos(0, 0), UpgradeData.EMPTY, LevelHeightAccessor.create(MIN_Y, HEIGHT),
                palettedContainerFactory(), null);
    }

    private static PalettedContainerFactory palettedContainerFactory() {
        Strategy<BlockState> blockStrategy = Strategy.createForBlockStates(Block.BLOCK_STATE_REGISTRY);
        Codec<PalettedContainer<BlockState>> blockCodec = PalettedContainer.codecRW(
                BlockState.CODEC, blockStrategy, Blocks.AIR.defaultBlockState());
        Biome biome = new Biome.BiomeBuilder()
                .hasPrecipitation(false)
                .temperature(0.8F)
                .downfall(0.4F)
                .specialEffects(new BiomeSpecialEffects.Builder().waterColor(0x3F76E4).build())
                .mobSpawnSettings(MobSpawnSettings.EMPTY)
                .generationSettings(BiomeGenerationSettings.EMPTY)
                .build();
        Holder<Biome> biomeHolder = Holder.direct(biome);
        IdMapper<Holder<Biome>> biomeIds = new IdMapper<>(1);
        biomeIds.add(biomeHolder);
        Strategy<Holder<Biome>> biomeStrategy = Strategy.createForBiomes(biomeIds);
        Codec<PalettedContainerRO<Holder<Biome>>> biomeCodec = PalettedContainer.codecRO(
                Biome.CODEC, biomeStrategy, biomeHolder);
        return new PalettedContainerFactory(blockStrategy, Blocks.AIR.defaultBlockState(), blockCodec,
                biomeStrategy, biomeHolder, biomeCodec);
    }
}
