package art.arcane.iris.platform.bukkit.nms.v26_2_R1;

import ca.spottedleaf.moonrise.common.util.TickThread;
import com.mojang.serialization.Codec;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.IdMapper;
import net.minecraft.core.SectionPos;
import net.minecraft.server.Bootstrap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiRecord;
import net.minecraft.world.entity.ai.village.poi.PoiSection;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ImposterProtoChunk;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.PalettedContainer;
import net.minecraft.world.level.chunk.PalettedContainerFactory;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.chunk.Strategy;
import net.minecraft.world.level.chunk.UpgradeData;
import org.bukkit.NamespacedKey;
import org.bukkit.craftbukkit.persistence.CraftPersistentDataTypeRegistry;
import org.bukkit.craftbukkit.persistence.DirtyCraftPersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class NativeStructurePoiUpdatesTest {
    private static final NamespacedKey DIRTY_SECTIONS = new NamespacedKey("iris", "native_poi_sections");

    @BeforeClass
    public static void bootstrapMinecraftRegistries() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    public void generationRecordsIntentBeforeSuppressingNativePoiCallbacks() {
        Fixture fixture = new Fixture();
        BlockPos position = new BlockPos(-31, 5, 49);
        doAnswer(call -> {
            assertNotNull(fixture.proto.persistentDataContainer.get(DIRTY_SECTIONS, PersistentDataType.LONG_ARRAY));
            assertEquals(Block.UPDATE_CLIENTS | 4096, (int) call.getArgument(2));
            fixture.proto.setBlockState(position, call.getArgument(1), 0);
            return true;
        }).when(fixture.world).setBlock(any(), any(), anyInt(), anyInt());

        assertTrue(fixture.updates.setBlock(fixture.world, position,
                Blocks.COMPOSTER.defaultBlockState(), Block.UPDATE_CLIENTS, 17));
        assertArrayEquals(new long[]{16L}, fixture.marker());
    }

    @Test
    public void denseWritesStoreOneSectionBitAndSurvivePersistentDataRoundTrip() throws Exception {
        Fixture fixture = new Fixture();
        for (int z = 0; z < 16; z++) {
            for (int x = 0; x < 16; x++) {
                fixture.write(new BlockPos(-32 + x, 5, 48 + z), Blocks.COMPOSTER.defaultBlockState());
            }
        }
        assertArrayEquals(new long[]{16L}, fixture.marker());
        DirtyCraftPersistentDataContainer restored = new DirtyCraftPersistentDataContainer(new CraftPersistentDataTypeRegistry());
        restored.putAll(fixture.proto.persistentDataContainer.toTagCompound());
        fixture.loaded.persistentDataContainer = restored;

        onOwner(() -> NativeStructurePoiUpdates.reconcile(fixture.loaded));

        assertEquals(256L, fixture.records().getRecords(type -> true, PoiManager.Occupancy.ANY).count());
        assertFalse(restored.has(DIRTY_SECTIONS));
    }

    @Test
    public void validSectionsReconcileFinalStatesAndKeepMatchingClaimedTickets() throws Exception {
        Fixture fixture = new Fixture();
        BlockPos retained = new BlockPos(-31, 5, 49);
        BlockPos replaced = new BlockPos(-30, 5, 49);
        BlockPos removed = new BlockPos(-29, 5, 49);
        Holder<PoiType> farmer = PoiTypes.forState(Blocks.COMPOSTER.defaultBlockState()).orElseThrow();
        PoiSection existing = new PoiSection.Packed(true, List.of(
                new PoiRecord.Packed(retained, farmer, 0),
                new PoiRecord.Packed(replaced, farmer, 1),
                new PoiRecord.Packed(removed, farmer, 1))).unpack(() -> {});
        fixture.pois.put(SectionPos.of(retained).asLong(), existing);
        PoiRecord retainedRecord = existing.getRecords(type -> true, PoiManager.Occupancy.ANY)
                .filter(record -> record.getPos().equals(retained)).findFirst().orElseThrow();
        for (BlockPos position : List.of(retained, replaced, removed)) {
            fixture.proto.setBlockState(position, Blocks.COMPOSTER.defaultBlockState(), 0);
        }
        fixture.write(replaced, Blocks.BARREL.defaultBlockState());
        fixture.write(removed, Blocks.AIR.defaultBlockState());

        onOwner(() -> NativeStructurePoiUpdates.reconcile(fixture.loaded));

        assertEquals(PoiTypes.forState(Blocks.BARREL.defaultBlockState()), existing.getType(replaced));
        assertTrue(existing.getType(removed).isEmpty());
        assertSame(retainedRecord, existing.getRecords(type -> true, PoiManager.Occupancy.ANY)
                .filter(record -> record.getPos().equals(retained)).findFirst().orElseThrow());
        assertEquals(0, existing.getFreeTickets(retained));
        onOwner(() -> NativeStructurePoiUpdates.reconcile(fixture.loaded));
        assertEquals(0, existing.getFreeTickets(retained));
    }

    @Test
    public void failedReconciliationRetainsIntentForRetry() throws Exception {
        Fixture fixture = new Fixture();
        fixture.write(new BlockPos(-31, 5, 49), Blocks.COMPOSTER.defaultBlockState());
        IllegalStateException failure = new IllegalStateException("POI storage failure");
        doThrow(failure).when(fixture.manager).add(any(), any());

        onOwner(() -> assertSame(failure, assertThrows(IllegalStateException.class,
                () -> NativeStructurePoiUpdates.reconcile(fixture.loaded))));

        assertArrayEquals(new long[]{16L}, fixture.marker());
        fixture.allowPoiAdds();
        onOwner(() -> NativeStructurePoiUpdates.reconcile(fixture.loaded));
        assertFalse(fixture.proto.persistentDataContainer.has(DIRTY_SECTIONS));
    }

    @Test
    public void explicitPoiSuppressionAndReadOnlyFullChunksDoNotCreateIntent() {
        Fixture fixture = new Fixture();
        BlockPos position = new BlockPos(-31, 5, 49);
        fixture.updates.setBlock(fixture.world, position, Blocks.COMPOSTER.defaultBlockState(),
                4096, 17);
        assertFalse(fixture.proto.persistentDataContainer.has(DIRTY_SECTIONS));
        ImposterProtoChunk full = mock(ImposterProtoChunk.class);
        when(fixture.world.getChunk(any(BlockPos.class))).thenReturn(full);
        fixture.updates.setBlock(fixture.world, position, Blocks.BARREL.defaultBlockState(), 2, 17);
        assertFalse(fixture.proto.persistentDataContainer.has(DIRTY_SECTIONS));
    }

    @Test
    public void invalidSectionBitsRetainTheStoredRecord() throws Exception {
        Fixture fixture = new Fixture();
        fixture.proto.persistentDataContainer.set(DIRTY_SECTIONS, PersistentDataType.LONG_ARRAY, new long[]{1L << 24});
        onOwner(() -> assertThrows(IllegalArgumentException.class,
                () -> NativeStructurePoiUpdates.reconcile(fixture.loaded)));
        assertArrayEquals(new long[]{1L << 24}, fixture.marker());
    }

    private static void onOwner(Runnable action) throws Exception {
        FutureTask<Void> task = new FutureTask<>(action, null);
        new TickThread(task, "Native POI test").start();
        task.get(5L, TimeUnit.SECONDS);
    }

    private static PalettedContainerFactory containerFactory() {
        Strategy<BlockState> blocks = Strategy.createForBlockStates(Block.BLOCK_STATE_REGISTRY);
        BlockState air = Blocks.AIR.defaultBlockState();
        IdMapper<Holder<Biome>> biomeIds = new IdMapper<>();
        Holder<Biome> biome = Holder.direct((Biome) null);
        biomeIds.add(biome);
        Strategy<Holder<Biome>> biomes = Strategy.createForBiomes(biomeIds);
        Codec<Holder<Biome>> biomeCodec = Codec.STRING.xmap(name -> biome, holder -> "iris-test-biome");
        return new PalettedContainerFactory(blocks, air, PalettedContainer.codecRW(BlockState.CODEC, blocks, air),
                biomes, biome, PalettedContainer.codecRO(biomeCodec, biomes, biome),
                PalettedContainer.codecRW(biomeCodec, biomes, biome));
    }

    private static final class Fixture {
        private final NativeStructurePoiUpdates updates = new NativeStructurePoiUpdates(4096);
        private final ProtoChunk proto = new ProtoChunk(new ChunkPos(-2, 3), UpgradeData.EMPTY,
                LevelHeightAccessor.create(-64, 384), containerFactory(), null);
        private final LevelChunk loaded = mock(LevelChunk.class);
        private final WorldGenLevel world = mock(WorldGenLevel.class);
        private final PoiManager manager = mock(PoiManager.class);
        private final Map<Long, PoiSection> pois = new HashMap<>();

        private Fixture() {
            ServerLevel level = mock(ServerLevel.class);
            when(level.getPoiManager()).thenReturn(manager);
            loaded.persistentDataContainer = proto.persistentDataContainer;
            when(loaded.getLevel()).thenReturn(level);
            when(loaded.getPos()).thenReturn(proto.getPos());
            when(loaded.getSectionsCount()).thenReturn(proto.getSectionsCount());
            when(loaded.getSectionYFromSectionIndex(anyInt())).thenAnswer(call -> proto.getSectionYFromSectionIndex(call.getArgument(0)));
            when(loaded.getSection(anyInt())).thenAnswer(call -> proto.getSection(call.getArgument(0)));
            when(loaded.getBlockState(any())).thenAnswer(call -> proto.getBlockState(call.getArgument(0)));
            when(world.getChunk(any(BlockPos.class))).thenReturn(proto);
            when(world.setBlock(any(), any(), anyInt(), anyInt())).thenAnswer(call -> {
                proto.setBlockState(call.getArgument(0), call.getArgument(1), call.getArgument(2));
                return true;
            });
            when(manager.getOrLoad(anyLong())).thenAnswer(call -> Optional.ofNullable(pois.get((long) call.getArgument(0))));
            when(manager.getType(any())).thenAnswer(call -> {
                BlockPos position = call.getArgument(0);
                PoiSection section = pois.get(SectionPos.of(position).asLong());
                return section == null ? Optional.empty() : section.getType(position);
            });
            doAnswer(call -> {
                BlockPos position = call.getArgument(0);
                pois.get(SectionPos.of(position).asLong()).remove(position);
                return null;
            }).when(manager).remove(any());
            allowPoiAdds();
        }

        private void allowPoiAdds() {
            doAnswer(call -> {
                BlockPos position = call.getArgument(0);
                return pois.computeIfAbsent(SectionPos.of(position).asLong(), ignored -> new PoiSection(() -> {}))
                        .add(position, call.getArgument(1));
            }).when(manager).add(any(), any());
        }

        private void write(BlockPos position, BlockState state) {
            updates.setBlock(world, position, state, Block.UPDATE_CLIENTS, Block.UPDATE_LIMIT);
        }

        private long[] marker() {
            return proto.persistentDataContainer.get(DIRTY_SECTIONS, PersistentDataType.LONG_ARRAY);
        }

        private PoiSection records() {
            return pois.get(SectionPos.of(new BlockPos(-31, 5, 49)).asLong());
        }
    }
}
