package art.arcane.iris.probe;

import net.minecraft.SharedConstants;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.server.Bootstrap;
import net.minecraft.core.registries.Registries;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.chunk.UpgradeData;
import net.minecraft.world.level.chunk.PalettedContainerFactory;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.storage.RegionFile;
import net.minecraft.world.level.chunk.storage.RegionStorageInfo;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.DataInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public final class NativeRegionTerrainWriterTest {
    @BeforeClass
    public static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    public void nativeRoundTripPreservesNegativeCoordinatesAndPartialStatus() throws Exception {
        NativeRegionTerrainWriter codec = new NativeRegionTerrainWriter(HeadlessNativeTestRegistries.get());
        CompoundTag tag = codec.encode(new NativeRegionTerrainWriter.TerrainInput(-33, -1, -64, 32,
                (x, y, z) -> y < 17 ? Blocks.STONE.defaultBlockState() : Blocks.AIR.defaultBlockState(),
                (x, y, z) -> "minecraft:plains", List.of()));
        assertEquals("minecraft:empty", tag.getStringOr("Status", ""));
        assertEquals(SharedConstants.getCurrentVersion().dataVersion().version(), tag.getIntOr("DataVersion", -1));
        assertFalse(tag.contains("Level"));
        assertFalse(tag.getBooleanOr("isLightOn", false));
        assertEquals("iris:terrain_checkpoint", tag.getStringOr("iris:checkpoint", ""));
        assertEquals(Blocks.STONE.defaultBlockState(), codec.readBlock(tag, 0, -48, 0));
        assertEquals(Blocks.AIR.defaultBlockState(), codec.readBlock(tag, 0, -47, 0));
        Path directory = Files.createTempDirectory("iris-native-region-test-");
        Path file = directory.resolve("r.-2.-1.mca");
        try {
            try (RegionFile region = new RegionFile(new RegionStorageInfo("checkpoint", Level.OVERWORLD, "chunk"), file, directory, false)) {
                NativeRegionTerrainWriter.write(region, tag);
            }
            try (RegionFile region = new RegionFile(new RegionStorageInfo("checkpoint", Level.OVERWORLD, "chunk"), file, directory, false);
                 DataInputStream input = region.getChunkDataInputStream(new ChunkPos(-33, -1))) {
                assertTrue(input != null);
                assertEquals(tag, NbtIo.read(input));
            }
        } finally {
            Files.deleteIfExists(file);
            Files.delete(directory);
        }
    }

    @Test
    public void preservesDistinctAirStatesInInitiallyEmptyContainers() throws Exception {
        NativeRegionTerrainWriter codec = new NativeRegionTerrainWriter(HeadlessNativeTestRegistries.get());
        assertEquals(Blocks.AIR.defaultBlockState(),
                PalettedContainerFactory.create(codec.registries()).createForBlockStates().get(0, 0, 0));
        CompoundTag tag = codec.encode(new NativeRegionTerrainWriter.TerrainInput(0, 0, 0, 16,
                (x, y, z) -> x == 1 ? Blocks.CAVE_AIR.defaultBlockState()
                        : x == 2 ? Blocks.VOID_AIR.defaultBlockState() : Blocks.AIR.defaultBlockState(),
                (x, y, z) -> "minecraft:plains", List.of()));
        assertEquals(Blocks.AIR.defaultBlockState(), codec.readBlock(tag, 0, 0, 0));
        assertEquals(Blocks.CAVE_AIR.defaultBlockState(), codec.readBlock(tag, 1, 0, 0));
        assertEquals(Blocks.VOID_AIR.defaultBlockState(), codec.readBlock(tag, 2, 0, 0));
        assertTrue(tag.getListOrEmpty("block_entities").isEmpty());
    }

    @Test
    public void serializesNativeBlockEntityDefaultsAndCustomPayload() throws Exception {
        NativeRegionTerrainWriter codec = new NativeRegionTerrainWriter(HeadlessNativeTestRegistries.get());
        CompoundTag payload = new CompoundTag();
        payload.putString("LootTable", "minecraft:chests/simple_dungeon");
        payload.putLong("LootTableSeed", 91L);
        NativeRegionTerrainWriter.TileInput tile = new NativeRegionTerrainWriter.TileInput(3, 5, 7,
                "minecraft:chest[facing=north,type=single,waterlogged=false]", payload);
        CompoundTag tag = codec.encode(new NativeRegionTerrainWriter.TerrainInput(-2, 3, -64, 16,
                (x, y, z) -> x == 3 && y == 5 && z == 7 || x == 1 && y == 2 && z == 4
                        ? Blocks.CHEST.defaultBlockState() : Blocks.AIR.defaultBlockState(),
                (x, y, z) -> "minecraft:plains", List.of(tile)));
        List<CompoundTag> entities = tag.getListOrEmpty("block_entities").compoundStream().toList();
        assertEquals(2, entities.size());
        CompoundTag custom = entities.stream().filter(entity -> entity.getIntOr("x", 0) == -29)
                .findFirst().orElseThrow();
        assertEquals("minecraft:chest", custom.getStringOr("id", ""));
        assertEquals(-59, custom.getIntOr("y", 0));
        assertEquals(55, custom.getIntOr("z", 0));
        assertEquals("minecraft:chests/simple_dungeon", custom.getStringOr("LootTable", ""));
        assertEquals(91L, custom.getLongOr("LootTableSeed", 0));
        assertEquals("minecraft:empty", tag.getStringOr("Status", ""));
    }

    @Test
    public void fillsPlannedChunkWithoutDiscardingStatusOrBlockEntities() throws Exception {
        NativeRegionTerrainWriter codec = new NativeRegionTerrainWriter(HeadlessNativeTestRegistries.get());
        ProtoChunk chunk = new ProtoChunk(new ChunkPos(-2, 3), UpgradeData.EMPTY,
                LevelHeightAccessor.create(-64, 32), PalettedContainerFactory.create(codec.registries()), null);
        chunk.setPersistedStatus(ChunkStatus.BIOMES);
        chunk.fillBiomesFromNoise((x, y, z) -> codec.registries().lookupOrThrow(Registries.BIOME)
                .getOrThrow(Biomes.SWAMP));
        BlockState chest = Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, Direction.EAST);
        NativeRegionTerrainWriter.TerrainInput input = new NativeRegionTerrainWriter.TerrainInput(-2, 3, -64, 32,
                (x, y, z) -> x == 3 && y == 5 && z == 7 ? chest : Blocks.STONE.defaultBlockState(),
                (x, y, z) -> "minecraft:plains", List.of());
        codec.fill(chunk, input);
        assertEquals(ChunkStatus.BIOMES, chunk.getPersistedStatus());
        assertEquals(Biomes.SWAMP,
                chunk.getNoiseBiome(-8, -16, 12).unwrapKey().orElseThrow());
        assertEquals(Blocks.STONE.defaultBlockState(), chunk.getBlockState(new BlockPos(-32, -33, 48)));
        assertEquals(chest, chunk.getBlockState(new BlockPos(-29, -59, 55)));
        assertEquals("minecraft:chest", chunk.getBlockEntityNbt(new BlockPos(-29, -59, 55))
                .getStringOr("id", ""));
        assertThrows(IllegalArgumentException.class, () -> codec.fill(chunk,
                new NativeRegionTerrainWriter.TerrainInput(0, 0, -64, 32,
                        input.blocks(), input.biomes(), List.of())));
    }

    @Test
    public void serializesEveryNativeDefaultBlockEntityWithoutWorld() throws Exception {
        NativeRegionTerrainWriter codec = new NativeRegionTerrainWriter(HeadlessNativeTestRegistries.get());
        int tested = 0;
        for (Block block : BuiltInRegistries.BLOCK) {
            if (!block.defaultBlockState().hasBlockEntity()) {
                continue;
            }
            String key = BuiltInRegistries.BLOCK.getKey(block).toString();
            CompoundTag tag = codec.encode(new NativeRegionTerrainWriter.TerrainInput(0, 0, 0, 16,
                    (x, y, z) -> x == 0 && y == 0 && z == 0 ? block.defaultBlockState() : Blocks.AIR.defaultBlockState(),
                    (x, y, z) -> "minecraft:plains", List.of()));
            int expected = ((EntityBlock) block).newBlockEntity(BlockPos.ZERO, block.defaultBlockState()) == null ? 0 : 1;
            assertEquals(key, expected, tag.getListOrEmpty("block_entities").size());
            tested++;
        }
        assertTrue(tested > 50);
    }

    @Test
    public void rejectsMissingNativeStatesAndUnregisteredBiomes() throws Exception {
        NativeRegionTerrainWriter codec = new NativeRegionTerrainWriter(HeadlessNativeTestRegistries.get());
        assertThrows(NullPointerException.class, () -> codec.encode(new NativeRegionTerrainWriter.TerrainInput(
                0, 0, 0, 16, (x, y, z) -> null, (x, y, z) -> "minecraft:plains", List.of())));
        assertThrows(IllegalArgumentException.class, () -> codec.encode(new NativeRegionTerrainWriter.TerrainInput(
                0, 0, 0, 16, (x, y, z) -> Blocks.STONE.defaultBlockState(), (x, y, z) -> "iris:custom_biome", List.of())));
    }

    @Test
    public void validatesSectionBounds() throws Exception {
        assertThrows(IllegalArgumentException.class, () -> new NativeRegionTerrainWriter.TerrainInput(
                0, 0, -63, 16, (x, y, z) -> Blocks.AIR.defaultBlockState(), (x, y, z) -> "minecraft:plains", List.of()));
    }

    @Test
    public void preservesDeclaredCustomBiomeKeys() throws Exception {
        NativeRegionTerrainWriter codec = new NativeRegionTerrainWriter(HeadlessNativeTestRegistries.get());
        CompoundTag tag = codec.encode(new NativeRegionTerrainWriter.TerrainInput(0, 0, 0, 16,
                (x, y, z) -> Blocks.STONE.defaultBlockState(), (x, y, z) -> "iris:biomes/test", List.of()));
        assertEquals("iris:biomes/test", tag.getListOrEmpty("sections").getCompoundOrEmpty(0)
                .getCompoundOrEmpty("biomes").getListOrEmpty("palette").getStringOr(0, ""));
    }
}
