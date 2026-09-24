package art.arcane.iris.probe;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.chunk.PalettedContainerFactory;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.chunk.UpgradeData;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.structures.StrongholdPieces;
import net.minecraft.world.level.levelgen.structure.structures.SwampHutPiece;
import net.minecraft.world.level.material.Fluids;
import org.bukkit.Bukkit;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public final class OfflineNativeFeatureFeasibilityTest {
    private static HeadlessNativeRegistries loaded;
    private static RegistryAccess registries;
    private static DimensionType dimension;

    @BeforeClass
    public static void loadImportedFeatures() throws Exception {
        assertNull(Bukkit.getServer());
        loaded = HeadlessNativeRegistries.load(Map.of(
                "data/iris/worldgen/placed_feature/offline_spring.json",
                "{\"feature\":\"minecraft:spring_water\",\"placement\":[]}",
                "data/iris/worldgen/placed_feature/offline_chest.json",
                "{\"feature\":{\"type\":\"minecraft:simple_block\",\"to_place\":{\"id\":\"minecraft:chest\"}},\"placement\":[]}"
        ));
        registries = loaded.registries();
        dimension = registries.lookupOrThrow(Registries.DIMENSION_TYPE).getOrThrow(ResourceKey.create(
                Registries.DIMENSION_TYPE, Identifier.parse("minecraft:overworld"))).value();
    }

    @AfterClass
    public static void closeRegistries() throws Exception {
        if (loaded != null) {
            loaded.close();
        }
        assertNull(Bukkit.getServer());
    }

    @Test
    public void registryConfiguredSpringCapturesNativeFluidTickAcrossChunkBoundary() {
        Fixture fixture = fixture();
        BlockPos spring = new BlockPos(-1, 20, 0);
        for (Direction direction : Direction.values()) {
            if (direction != Direction.EAST) {
                fixture.world().setBlock(spring.relative(direction), Blocks.STONE.defaultBlockState(), 2);
            }
        }
        assertTrue(feature("offline_spring").placement().isEmpty());
        assertTrue(feature("offline_spring").feature().value().place(fixture.world(), null, RandomSource.create(42L), spring));
        assertTrue(fixture.world().getFluidState(spring).is(Fluids.WATER));
        assertTrue(fixture.world().getFluidTicks().hasScheduledTick(spring, Fluids.WATER));
        assertEquals(1, fixture.world().getFluidTicks().count());
        ProtoChunk source = fixture.chunk(-1, 0);
        assertEquals(1, source.getTicksForSerialization(0L).fluids().size());
        assertEquals(spring, source.getTicksForSerialization(0L).fluids().getFirst().pos());
        assertFalse(source.isLightCorrect());
        assertEquals(ChunkStatus.TERRAIN, source.getPersistedStatus());
        assertThrows(UnsupportedOperationException.class,
                () -> fixture.world().getFluidTicks().willTickThisTick(spring, Fluids.WATER));
        assertNull(Bukkit.getServer());
    }

    @Test
    public void registryConfiguredSimpleBlockCreatesRealNativeBlockEntity() {
        Fixture fixture = fixture();
        BlockPos chest = new BlockPos(1, 20, 1);
        assertTrue(feature("offline_chest").placement().isEmpty());
        assertTrue(feature("offline_chest").feature().value().place(fixture.world(), null, RandomSource.create(3L), chest));
        assertTrue(fixture.world().getBlockState(chest).is(Blocks.CHEST));
        assertTrue(fixture.world().getBlockEntity(chest) instanceof ChestBlockEntity);
        CompoundTag saved = fixture.chunk(0, 0).getBlockEntityNbtForSaving(chest, registries);
        assertNotNull(saved);
        assertEquals("minecraft:chest", saved.getStringOr("id", ""));
        assertEquals(1, saved.getIntOr("x", -1));
        assertEquals(20, saved.getIntOr("y", -1));
        assertEquals(ChunkStatus.TERRAIN, fixture.chunk(0, 0).getPersistedStatus());
        assertNull(Bukkit.getServer());
    }

    @Test
    public void importedPlacedFeatureWrapperRequiresServerLevelEvenWithoutModifiers() {
        Fixture fixture = fixture();
        BlockPos position = new BlockPos(1, 20, 1);
        for (String key : List.of("offline_spring", "offline_chest")) {
            PlacedFeature placed = feature(key);
            assertTrue(placed.placement().isEmpty());
            UnsupportedOperationException failure = assertThrows(UnsupportedOperationException.class,
                    () -> placed.place(fixture.world(), null, RandomSource.create(42L), position));
            assertTrue(failure.getMessage(), failure.getMessage().contains("ServerLevel"));
            assertTrue(hasFrame(failure, "net.minecraft.world.level.levelgen.placement.PlacementContext"));
        }
        assertTrue(fixture.world().getBlockState(position).isAir());
        assertEquals(0, fixture.world().getFluidTicks().count());
        assertEquals(ChunkStatus.TERRAIN, fixture.chunk(0, 0).getPersistedStatus());
    }

    @Test
    public void realStrongholdPieceWritesAcrossPreparedChunksWithoutServer() {
        Fixture fixture = fixture();
        BoundingBox box = new BoundingBox(-2, 20, -2, 2, 24, 4);
        for (BlockPos position : BlockPos.betweenClosed(-2, 20, -2, 2, 24, 4)) {
            fixture.world().setBlock(position, Blocks.STONE.defaultBlockState(), 2);
        }
        StrongholdPieces.Straight piece = new StrongholdPieces.Straight(0, RandomSource.create(4L), box, Direction.SOUTH);
        piece.postProcess(fixture.world(), null, null, RandomSource.create(5L),
                box, new ChunkPos(0, 0), BlockPos.ZERO);
        assertTrue(fixture.world().getBlockState(new BlockPos(0, 22, 0)).isAir());
        assertFalse(fixture.world().getBlockState(new BlockPos(-2, 20, -2)).is(Blocks.STONE));
        assertFalse(fixture.world().getBlockState(new BlockPos(2, 20, 4)).is(Blocks.STONE));
        for (ProtoChunk chunk : fixture.chunks()) {
            assertEquals(ChunkStatus.TERRAIN, chunk.getPersistedStatus());
        }
        assertNull(Bukkit.getServer());
    }

    @Test
    public void nativeLootChestRequiresCraftBukkitWorldServices() {
        Fixture fixture = fixture();
        BoundingBox box = new BoundingBox(0, 20, 0, 4, 24, 6);
        StrongholdPieces.ChestCorridor piece = new StrongholdPieces.ChestCorridor(
                0, RandomSource.create(4L), box, Direction.SOUTH);
        RuntimeException failure = assertThrows(RuntimeException.class,
                () -> piece.postProcess(fixture.world(), null, null, RandomSource.create(5L),
                        box, new ChunkPos(0, 0), BlockPos.ZERO));
        assertEquals("Failed to read non-placed BlockState", failure.getMessage());
        assertTrue(failure.getCause() instanceof NullPointerException);
        assertTrue(hasFrame(failure.getCause(), "net.minecraft.world.level.storage.TagValueOutput"));
        assertTrue(hasFrame(failure, "org.bukkit.craftbukkit.block.CraftBlockStates"));
        assertEquals(ChunkStatus.TERRAIN, fixture.chunk(0, 0).getPersistedStatus());
        assertNull(Bukkit.getServer());
    }

    @Test
    public void nativeHutPopulationRequiresServerLevelAndLeavesChunkUnpromoted() {
        Fixture fixture = fixture();
        for (BlockPos position : BlockPos.betweenClosed(-16, 63, -16, 31, 63, 31)) {
            fixture.world().setBlock(position, Blocks.STONE.defaultBlockState(), 2);
        }
        SwampHutPiece piece = new SwampHutPiece(RandomSource.create(9L), 0, 0);
        UnsupportedOperationException failure = assertThrows(UnsupportedOperationException.class,
                () -> piece.postProcess(fixture.world(), null, null, RandomSource.create(6L),
                        new BoundingBox(-16, dimension.minY(), -16, 31, dimension.minY() + dimension.height() - 1, 31),
                        new ChunkPos(0, 0), BlockPos.ZERO));
        assertTrue(failure.getMessage(), failure.getMessage().contains("ServerLevel"));
        assertTrue(hasFrame(failure, SwampHutPiece.class.getName()));
        for (ProtoChunk chunk : fixture.chunks()) {
            assertEquals(ChunkStatus.TERRAIN, chunk.getPersistedStatus());
            assertTrue(chunk.getEntities().isEmpty());
        }
    }

    @Test
    public void finiteFixtureRejectsEscapedWritesAndUnavailableStatus() {
        Fixture fixture = fixture();
        assertThrows(IllegalStateException.class,
                () -> fixture.world().setBlock(new BlockPos(32, 20, 0), Blocks.STONE.defaultBlockState(), 2));
        assertThrows(UnsupportedOperationException.class,
                () -> fixture.world().getChunk(0, 0, ChunkStatus.FULL, true));
        assertNull(fixture.world().getChunk(3, 3, ChunkStatus.TERRAIN, false));
    }

    private static PlacedFeature feature(String path) {
        return registries.lookupOrThrow(Registries.PLACED_FEATURE).getOrThrow(ResourceKey.create(
                Registries.PLACED_FEATURE, Identifier.fromNamespaceAndPath("iris", path))).value();
    }

    private static boolean hasFrame(Throwable failure, String className) {
        for (StackTraceElement frame : failure.getStackTrace()) {
            if (frame.getClassName().equals(className)) {
                return true;
            }
        }
        return false;
    }

    private static Fixture fixture() {
        List<ProtoChunk> chunks = new ArrayList<>();
        for (int z = -1; z <= 1; z++) {
            for (int x = -1; x <= 1; x++) {
                ProtoChunk chunk = new ProtoChunk(new ChunkPos(x, z), UpgradeData.EMPTY,
                        LevelHeightAccessor.create(dimension.minY(), dimension.height()),
                        PalettedContainerFactory.create(registries), null);
                chunk.fillBiomesFromNoise((qx, qy, qz) -> registries.lookupOrThrow(Registries.BIOME).getOrThrow(Biomes.PLAINS));
                chunk.setPersistedStatus(ChunkStatus.TERRAIN);
                chunks.add(chunk);
            }
        }
        return new Fixture(new OfflineFeatureWorld(new OfflineFeatureWorld.Options(
                registries, dimension, 69420L, 63), chunks), List.copyOf(chunks));
    }

    private record Fixture(OfflineFeatureWorld world, List<ProtoChunk> chunks) {
        ProtoChunk chunk(int x, int z) {
            return (ProtoChunk) world.getChunk(x, z);
        }
    }
}
