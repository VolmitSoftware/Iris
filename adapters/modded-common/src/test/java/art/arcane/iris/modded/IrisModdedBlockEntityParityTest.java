package art.arcane.iris.modded;

import art.arcane.volmlib.nativelib.minecraft26_2.modded.NativeModdedChunkGenerator;

import art.arcane.volmlib.nativelib.minecraft26_2.modded.NativeContainerLoot;

import art.arcane.volmlib.util.math.RNG;
import com.mojang.serialization.Codec;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.IdMapper;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.server.Bootstrap;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.Container;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeGenerationSettings;
import net.minecraft.world.level.biome.BiomeSpecialEffects;
import net.minecraft.world.level.biome.MobSpawnSettings;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BannerBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.entity.SpawnerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.PalettedContainerFactory;
import net.minecraft.world.level.chunk.PalettedContainer;
import net.minecraft.world.level.chunk.PalettedContainerRO;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.chunk.Strategy;
import net.minecraft.world.level.chunk.UpgradeData;
import net.minecraft.world.level.storage.TagValueInput;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class IrisModdedBlockEntityParityTest {
    private static RegistryAccess registries;

    @BeforeClass
    public static void bootstrapMinecraftRegistries() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        registries = RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);
        if (!Items.DIAMOND.builtInRegistryHolder().areComponentsBound()) {
            Items.DIAMOND.builtInRegistryHolder().bindComponents(DataComponentMap.EMPTY);
        }
    }

    @Test
    public void generatedContainerGetsBlockEntityAndPersistsFilledLoot() {
        ProtoChunk chunk = newChunk();
        BlockPos position = new BlockPos(3, 70, 5);
        chunk.setBlockState(position, Blocks.CHEST.defaultBlockState(), 0);

        NativeModdedChunkGenerator.createDefaultBlockEntity(chunk, position, Blocks.CHEST.defaultBlockState());

        BlockEntity blockEntity = chunk.getBlockEntity(position);
        assertNotNull(blockEntity);
        assertTrue(blockEntity instanceof Container);
        Container container = (Container) blockEntity;
        NativeContainerLoot.fillContainer(container, List.of(new ItemStack(Items.DIAMOND)), new RNG(17L), message -> {});
        assertEquals(1, countItem(container, Items.DIAMOND));
        CompoundTag saved = blockEntity.saveWithoutMetadata(registries);
        ChestBlockEntity restored = new ChestBlockEntity(position, Blocks.CHEST.defaultBlockState());
        restored.loadWithComponents(TagValueInput.create(ProblemReporter.DISCARDING, registries, saved));
        assertEquals(1, countItem(restored, Items.DIAMOND));
    }

    @Test
    public void generatedSignSpawnerAndBannerGetNativeBlockEntities() {
        ProtoChunk chunk = newChunk();
        BlockState signState = Blocks.OAK_SIGN.defaultBlockState();
        BlockState spawnerState = Blocks.SPAWNER.defaultBlockState();
        BlockState bannerState = BuiltInRegistries.BLOCK.getValue(
                Identifier.parse("minecraft:white_banner")).defaultBlockState();
        BlockPos signPosition = new BlockPos(1, 70, 1);
        BlockPos spawnerPosition = new BlockPos(2, 70, 1);
        BlockPos bannerPosition = new BlockPos(3, 70, 1);

        chunk.setBlockState(signPosition, signState, 0);
        chunk.setBlockState(spawnerPosition, spawnerState, 0);
        chunk.setBlockState(bannerPosition, bannerState, 0);
        NativeModdedChunkGenerator.createDefaultBlockEntity(chunk, signPosition, signState);
        NativeModdedChunkGenerator.createDefaultBlockEntity(chunk, spawnerPosition, spawnerState);
        NativeModdedChunkGenerator.createDefaultBlockEntity(chunk, bannerPosition, bannerState);

        assertTrue(chunk.getBlockEntity(signPosition) instanceof SignBlockEntity);
        assertTrue(chunk.getBlockEntity(spawnerPosition) instanceof SpawnerBlockEntity);
        assertTrue(chunk.getBlockEntity(bannerPosition) instanceof BannerBlockEntity);
    }

    private static ProtoChunk newChunk() {
        return new ProtoChunk(
                new ChunkPos(0, 0),
                UpgradeData.EMPTY,
                LevelHeightAccessor.create(-64, 384),
                palettedContainerFactory(),
                null);
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
        return new PalettedContainerFactory(
                blockStrategy,
                Blocks.AIR.defaultBlockState(),
                blockCodec,
                biomeStrategy,
                biomeHolder,
                biomeCodec);
    }

    private static int countItem(Container container, Item item) {
        int count = 0;
        for (int slot = 0; slot < container.getContainerSize(); slot++) {
            ItemStack stack = container.getItem(slot);
            if (stack.getItem() == item) {
                count += stack.getCount();
            }
        }
        return count;
    }
}
