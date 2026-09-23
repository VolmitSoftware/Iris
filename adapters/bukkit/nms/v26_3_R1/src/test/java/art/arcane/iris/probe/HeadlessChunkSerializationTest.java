package art.arcane.iris.probe;

import ca.spottedleaf.moonrise.patches.starlight.chunk.StarlightChunk;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.shorts.ShortArrayList;
import net.minecraft.core.BlockPos;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.repository.ServerPacksSource;
import net.minecraft.server.packs.resources.MultiPackResourceManager;
import net.minecraft.util.RandomSource;
import net.minecraft.util.datafix.DataFixers;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.PalettedContainerFactory;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.chunk.UpgradeData;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.chunk.storage.SerializableChunkData;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.pieces.PiecesContainer;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import net.minecraft.world.level.levelgen.structure.structures.SwampHutPiece;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.storage.LevelStorageSource;
import net.minecraft.world.ticks.ScheduledTick;
import org.bukkit.NamespacedKey;
import org.bukkit.persistence.PersistentDataType;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.util.EnumSet;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class HeadlessChunkSerializationTest {
    @Rule
    public TemporaryFolder temporary = new TemporaryFolder();

    @Test
    public void snapshotMatchesNativeCopyOfIncludingStructuresLightAndReceipts() throws Exception {
        RegistryAccess registries = HeadlessNativeTestRegistries.get();
        PalettedContainerFactory containers = PalettedContainerFactory.create(registries);
        LevelHeightAccessor height = LevelHeightAccessor.create(-64, 384);
        ProtoChunk chunk = new ProtoChunk(new ChunkPos(-33, 17), UpgradeData.EMPTY, height, containers, null);
        BlockPos block = new BlockPos(-528, -40, 272);
        chunk.setBlockState(block, Blocks.CHEST.defaultBlockState(), 0);
        CompoundTag chest = new CompoundTag();
        chest.putString("id", "minecraft:chest");
        chest.putInt("x", block.getX());
        chest.putInt("y", block.getY());
        chest.putInt("z", block.getZ());
        chest.putString("LootTable", "minecraft:chests/simple_dungeon");
        chunk.setBlockEntityNbt(chest);
        CompoundTag entity = new CompoundTag();
        entity.putString("id", "minecraft:pig");
        chunk.addEntity(entity);
        chunk.getBlockTicks().schedule(new ScheduledTick<>(Blocks.CHEST, block, 37L, 2L));
        chunk.getFluidTicks().schedule(new ScheduledTick<>(Fluids.WATER, block.above(), 41L, 3L));
        chunk.addPackedPostProcess(new ShortArrayList(new short[]{17, 2049}), 1);
        chunk.setInhabitedTime(192L);
        Heightmap.primeHeightmaps(chunk, EnumSet.allOf(Heightmap.Types.class));
        chunk.setPersistedStatus(ChunkStatus.TERRAIN);
        chunk.persistentDataContainer.set(new NamespacedKey("iris", "natural_terrain"),
                PersistentDataType.BYTE_ARRAY, new byte[]{1, -8, 92, 0});
        StarlightChunk light = (StarlightChunk) chunk;
        light.starlight$getBlockNibbles()[1].setFull();
        light.starlight$getBlockNibbles()[1].updateVisible();
        light.starlight$getSkyNibbles()[0].setFull();
        light.starlight$getSkyNibbles()[0].updateVisible();
        chunk.setLightCorrect(true);
        Structure hut = registries.lookupOrThrow(Registries.STRUCTURE).getOrThrow(
                ResourceKey.create(Registries.STRUCTURE, Identifier.parse("minecraft:swamp_hut"))).value();
        StructureStart start = new StructureStart(hut, chunk.getPos(), 2,
                new PiecesContainer(List.of(new SwampHutPiece(RandomSource.create(69420L), block.getX(), block.getZ()))));
        start.persistentDataContainer.set(new NamespacedKey("iris", "structure_activation"), PersistentDataType.LONG, 73L);
        chunk.setAllStarts(Map.of(hut, start));
        chunk.setAllReferences(Map.of(hut, new LongOpenHashSet(new long[]{ChunkPos.pack(-32, 17)})));
        try (MultiPackResourceManager resources = new MultiPackResourceManager(PackType.SERVER_DATA,
                List.of(ServerPacksSource.createVanillaPackSource().fullResources()));
             LevelStorageSource.LevelStorageAccess storage = LevelStorageSource.createDefault(
                     temporary.newFolder().toPath()).createAccess("serialization")) {
            StructureTemplateManager templates = new StructureTemplateManager(resources, storage,
                    DataFixers.getDataFixer(), BuiltInRegistries.BLOCK);
            StructurePieceSerializationContext structures = new StructurePieceSerializationContext(resources, registries, templates);
            HeadlessChunkSerialization.Context context = new HeadlessChunkSerialization.Context(structures, containers, 29L);
            MinecraftServer server = mock(MinecraftServer.class);
            when(server.getResourceManager()).thenReturn(resources);
            when(server.registryAccess()).thenReturn(registries.freeze());
            when(server.getStructureTemplateManager()).thenReturn(templates);
            ServerLevel level = mock(ServerLevel.class);
            when(level.getServer()).thenReturn(server);
            when(level.registryAccess()).thenReturn(registries);
            when(level.palettedContainerFactory()).thenReturn(containers);
            when(level.getGameTime()).thenReturn(29L);
            when(level.getMinSectionY()).thenReturn(height.getMinSectionY());
            when(level.getMaxSectionY()).thenReturn(height.getMaxSectionY());
            CompoundTag nativeTag = SerializableChunkData.copyOf(level, chunk).write();
            SerializableChunkData snapshot = HeadlessChunkSerialization.copyOf(chunk, context);
            CompoundTag headless = snapshot.write();
            assertEquals(nativeTag, headless);
            assertEquals("minecraft:terrain", headless.getStringOr("Status", ""));
            assertEquals(29L, headless.getLongOr("LastUpdate", -1L));
            assertEquals(192L, headless.getLongOr("InhabitedTime", -1L));
            assertArrayEquals(new byte[]{1, -8, 92, 0}, headless.getCompoundOrEmpty("ChunkBukkitValues")
                    .getByteArray("iris:natural_terrain").orElseThrow());
            assertFalse(headless.getListOrEmpty("block_entities").isEmpty());
            assertFalse(headless.getListOrEmpty("block_ticks").isEmpty());
            assertFalse(headless.getListOrEmpty("fluid_ticks").isEmpty());
            assertTrue(headless.getCompoundOrEmpty("structures").getCompoundOrEmpty("starts").contains("minecraft:swamp_hut"));
            CompoundTag serializedStart = headless.getCompoundOrEmpty("structures").getCompoundOrEmpty("starts")
                    .getCompoundOrEmpty("minecraft:swamp_hut");
            StructureStart restored = StructureStart.loadStaticStart(structures, serializedStart, 69420L);
            assertTrue(restored.isValid());
            assertEquals(start.getBoundingBox(), restored.getBoundingBox());
            assertEquals(73L, serializedStart.getCompoundOrEmpty("StructureBukkitValues")
                    .getLongOr("iris:structure_activation", -1L));
            chunk.getSection(1).setBlockState(0, 8, 0, Blocks.AIR.defaultBlockState());
            chunk.getPostProcessing()[1].clear();
            chunk.getOrCreateHeightmapUnprimed(Heightmap.Types.WORLD_SURFACE).getRawData()[0] = 0L;
            assertEquals(headless, snapshot.write());
            for (ChunkStatus status : List.of(ChunkStatus.EMPTY, ChunkStatus.STRUCTURE_STARTS,
                    ChunkStatus.STRUCTURE_REFERENCES, ChunkStatus.BIOMES, ChunkStatus.TERRAIN)) {
                ProtoChunk fresh = new ProtoChunk(new ChunkPos(0, -1), UpgradeData.EMPTY, height, containers, null);
                fresh.setPersistedStatus(status);
                CompoundTag freshTag = HeadlessChunkSerialization.copyOf(fresh, context).write();
                assertEquals(SerializableChunkData.copyOf(level, fresh).write(), freshTag);
                assertEquals(status, SerializableChunkData.getChunkStatusFromTag(freshTag));
                assertFalse(freshTag.contains("ChunkBukkitValues"));
                assertTrue(freshTag.getListOrEmpty("entities").isEmpty());
                assertTrue(freshTag.getListOrEmpty("block_entities").isEmpty());
            }
            chunk.setPersistedStatus(ChunkStatus.FULL);
            assertThrows(IllegalArgumentException.class, () -> HeadlessChunkSerialization.copyOf(chunk, context));
        }
    }
}
