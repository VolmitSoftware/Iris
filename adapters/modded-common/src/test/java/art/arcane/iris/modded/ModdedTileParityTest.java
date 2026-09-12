package art.arcane.iris.modded;

import art.arcane.iris.generation.block.TileData;
import art.arcane.volmlib.util.collection.KMap;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.server.Bootstrap;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.RandomizableContainer;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.entity.SpawnerBlockEntity;
import net.minecraft.world.level.storage.TagValueInput;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class ModdedTileParityTest {
    private static RegistryAccess registries;

    @BeforeClass
    public static void bootstrapMinecraftRegistries() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        registries = RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);
        TileData.bindPlatformReader(new ModdedTileReader(() -> null));
        TileData.bindPlatformFactory(ModdedTileData::fromProperties);
    }

    @Test
    public void legacySignPayloadLoadsIntoNativeSignBlockEntity() throws Exception {
        ModdedTileData tile = legacyTile(out -> {
            out.writeShort(0);
            out.writeUTF("Iris");
            out.writeUTF("modded");
            out.writeUTF("tile");
            out.writeUTF("parity");
            out.writeByte(DyeColor.BLUE.getId());
        });
        SignBlockEntity sign = new SignBlockEntity(BlockPos.ZERO, Blocks.OAK_SIGN.defaultBlockState());

        sign.loadWithComponents(TagValueInput.create(ProblemReporter.DISCARDING, registries, tile.payload()));

        assertEquals("Iris", sign.getFrontText().getMessage(0, false).getString());
        assertEquals("parity", sign.getBackText().getMessage(3, false).getString());
        assertEquals(DyeColor.BLUE, sign.getFrontText().getColor());
    }

    @Test
    public void legacySpawnerPayloadLoadsNamespacedEntity() throws Exception {
        ModdedTileData tile = legacyTile(out -> {
            out.writeShort(1);
            out.writeUTF("minecraft:zombie");
        });
        SpawnerBlockEntity spawner = new SpawnerBlockEntity(BlockPos.ZERO, Blocks.SPAWNER.defaultBlockState());

        spawner.loadWithComponents(TagValueInput.create(ProblemReporter.DISCARDING, registries, tile.payload()));
        CompoundTag saved = spawner.saveWithoutMetadata(registries);

        assertTrue(saved.toString().contains("minecraft:zombie"));
    }

    @Test
    public void legacySpawnerOrdinalUsesPaperEntityTypeOrdering() throws Exception {
        ModdedTileData tile = legacyTile(out -> {
            out.writeShort(1);
            out.writeShort(28);
        });
        SpawnerBlockEntity spawner = new SpawnerBlockEntity(BlockPos.ZERO, Blocks.SPAWNER.defaultBlockState());

        spawner.loadWithComponents(TagValueInput.create(ProblemReporter.DISCARDING, registries, tile.payload()));
        CompoundTag saved = spawner.saveWithoutMetadata(registries);

        assertTrue(saved.toString().contains("minecraft:command_block_minecart"));
    }

    @Test
    public void invalidLegacySpawnerOrdinalFallsBackToPig() throws Exception {
        ModdedTileData tile = legacyTile(out -> {
            out.writeShort(1);
            out.writeShort(-1);
        });
        SpawnerBlockEntity spawner = new SpawnerBlockEntity(BlockPos.ZERO, Blocks.SPAWNER.defaultBlockState());

        spawner.loadWithComponents(TagValueInput.create(ProblemReporter.DISCARDING, registries, tile.payload()));
        CompoundTag saved = spawner.saveWithoutMetadata(registries);

        assertTrue(saved.toString().contains("minecraft:pig"));
    }

    @Test
    public void legacyLootablePayloadLoadsTableAndSeed() throws Exception {
        ModdedTileData tile = legacyTile(out -> {
            out.writeShort(3);
            out.writeUTF("minecraft:chest");
            out.writeUTF("minecraft:chests/simple_dungeon");
            out.writeLong(4123L);
        });
        ChestBlockEntity chest = new ChestBlockEntity(BlockPos.ZERO, Blocks.CHEST.defaultBlockState());

        chest.loadWithComponents(TagValueInput.create(ProblemReporter.DISCARDING, registries, tile.payload()));
        RandomizableContainer lootable = chest;

        assertNotNull(lootable.getLootTable());
        assertEquals("minecraft:chests/simple_dungeon", lootable.getLootTable().identifier().toString());
        assertEquals(4123L, lootable.getLootTableSeed());
    }

    @Test
    public void legacyBannerBaseColorChangesTheGeneratedBlockState() throws Exception {
        ModdedTileData tile = legacyTile(out -> {
            out.writeShort(2);
            out.writeByte(DyeColor.RED.getId());
            out.writeByte(0);
        });

        assertEquals(
                BuiltInRegistries.BLOCK.getValue(Identifier.parse("minecraft:red_banner")),
                tile.adjustBlockState(BuiltInRegistries.BLOCK.getValue(Identifier.parse("minecraft:white_banner")).defaultBlockState()).getBlock());
        assertEquals(Blocks.BARREL, tile.adjustBlockState(Blocks.BARREL.defaultBlockState()).getBlock());
    }

    @Test
    public void paper26_2LegacyBannerFirstOrdinalIsSmallStripes() {
        assertEquals(
                Identifier.parse("minecraft:small_stripes"),
                ModdedTileReader.legacyBannerPatternKey(0));
    }

    @Test
    public void paper26_2LegacyBannerMiddleOrdinalIsStripeLeft() {
        assertEquals(
                Identifier.parse("minecraft:stripe_left"),
                ModdedTileReader.legacyBannerPatternKey(21));
    }

    @Test
    public void paper26_2LegacyBannerLastOrdinalIsHalfVerticalRight() {
        assertEquals(
                Identifier.parse("minecraft:half_vertical_right"),
                ModdedTileReader.legacyBannerPatternKey(42));
    }

    @Test
    public void invalidLegacyBannerOrdinalFallsBackToBase() {
        assertEquals(Identifier.parse("minecraft:base"), ModdedTileReader.legacyBannerPatternKey(-1));
        assertEquals(Identifier.parse("minecraft:base"), ModdedTileReader.legacyBannerPatternKey(43));
    }

    @Test
    public void modernPackTilePropertiesUseTheModdedFactoryAndLoadNatively() throws Exception {
        KMap<String, Object> properties = new KMap<>();
        properties.put("LootTable", "minecraft:chests/abandoned_mineshaft");
        properties.put("LootTableSeed", 9124L);
        TileData tile = TileData.of(ModdedBlockState.of(Blocks.CHEST.defaultBlockState(), null), properties);

        assertTrue(tile instanceof ModdedTileData);
        ChestBlockEntity chest = new ChestBlockEntity(BlockPos.ZERO, Blocks.CHEST.defaultBlockState());
        chest.loadWithComponents(TagValueInput.create(
                ProblemReporter.DISCARDING, registries, ((ModdedTileData) tile).payload()));

        assertNotNull(chest.getLootTable());
        assertEquals("minecraft:chests/abandoned_mineshaft", chest.getLootTable().identifier().toString());
        assertEquals(9124L, chest.getLootTableSeed());
        assertTrue(((ModdedTileData) tile).isApplicable(Blocks.CHEST.defaultBlockState(), chest));
        assertFalse(((ModdedTileData) tile).isApplicable(
                Blocks.BARREL.defaultBlockState(),
                new BarrelBlockEntity(BlockPos.ZERO, Blocks.BARREL.defaultBlockState())));
    }

    @Test
    public void legacyTileFamiliesRejectUnrelatedBlockEntities() throws Exception {
        ModdedTileData tile = legacyTile(out -> {
            out.writeShort(0);
            out.writeUTF("one");
            out.writeUTF("two");
            out.writeUTF("three");
            out.writeUTF("four");
            out.writeByte(DyeColor.BLACK.getId());
        });
        SignBlockEntity sign = new SignBlockEntity(BlockPos.ZERO, Blocks.OAK_SIGN.defaultBlockState());
        ChestBlockEntity chest = new ChestBlockEntity(BlockPos.ZERO, Blocks.CHEST.defaultBlockState());

        assertTrue(tile.isApplicable(Blocks.OAK_SIGN.defaultBlockState(), sign));
        assertFalse(tile.isApplicable(Blocks.CHEST.defaultBlockState(), chest));
    }

    private static ModdedTileData legacyTile(TileWriter writer) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(bytes)) {
            writer.write(out);
        }
        TileData tile = TileData.read(new DataInputStream(new ByteArrayInputStream(bytes.toByteArray())));
        assertTrue(tile instanceof ModdedTileData);
        return (ModdedTileData) tile;
    }

    @FunctionalInterface
    private interface TileWriter {
        void write(DataOutputStream out) throws Exception;
    }
}
