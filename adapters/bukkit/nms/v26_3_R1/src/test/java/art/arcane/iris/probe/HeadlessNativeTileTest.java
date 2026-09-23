package art.arcane.iris.probe;

import art.arcane.volmlib.nativelib.minecraft26_2.modded.NativeTileData;
import art.arcane.volmlib.nativelib.minecraft26_2.modded.ModdedBlockState;
import art.arcane.iris.modded.ModdedTileData;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import java.util.Map;
import java.util.List;
import art.arcane.volmlib.nativelib.minecraft26_2.modded.NativeTileReader;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.data.registries.VanillaRegistries;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

public final class HeadlessNativeTileTest {
    @BeforeClass
    public static void bootstrap() {
        HeadlessNativeBootstrap.initialize();
    }

    @Test
    public void nativeTileBinaryRetainsTypedPayloadWithoutWorld() throws Exception {
        NativeTileData captured = NativeTileData.capture("minecraft:player_head",
                "{id:\"minecraft:skull\",profile:{id:[I;1,2,3,4]},note:[B;5b,6b]}",
                message -> { throw new AssertionError(message); });
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream output = new DataOutputStream(bytes)) {
            captured.toBinary(output);
        }
        NativeTileReader reader = new NativeTileReader(VanillaRegistries::createWorldLookup);
        NativeTileData decoded;
        try (DataInputStream input = new DataInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
            decoded = reader.read(input);
        }
        CompoundTag payload = decoded.payload();
        assertEquals(captured.payload(), payload);
        assertArrayEquals(new int[]{1, 2, 3, 4}, payload.getCompoundOrEmpty("profile").getIntArray("id").orElseThrow());
        assertArrayEquals(new byte[]{5, 6}, payload.getByteArray("note").orElseThrow());
        assertEquals(captured, decoded);
    }

    @Test
    public void writesNativeTilePayloadIntoChunkAndRejectsMismatchedBlocks() throws Exception {
        ModdedTileData tile = ModdedTileData.capture("minecraft:chest",
                "{LootTable:'minecraft:chests/simple_dungeon',LootTableSeed:17L}");
        HeadlessNativeTiles.Placement placement = new HeadlessNativeTiles.Placement(2, 4, 6,
                new BlockPos(-32, -64, 48), ModdedBlockState.of(Blocks.CHEST.defaultBlockState(), Map.of()), tile);
        NativeRegionTerrainWriter.TileInput input = HeadlessNativeTiles.prepare(placement);
        NativeRegionTerrainWriter writer = new NativeRegionTerrainWriter(HeadlessNativeTestRegistries.get());
        CompoundTag chunk = writer.encode(new NativeRegionTerrainWriter.TerrainInput(-2, 3, -64, 16,
                (x, y, z) -> x == 2 && y == 4 && z == 6 ? Blocks.CHEST.defaultBlockState() : Blocks.AIR.defaultBlockState(),
                (x, y, z) -> "minecraft:plains", List.of(input)));
        CompoundTag entity = chunk.getListOrEmpty("block_entities").getCompoundOrEmpty(0);
        assertEquals("minecraft:chest", entity.getStringOr("id", ""));
        assertEquals(17L, entity.getLongOr("LootTableSeed", 0));
        assertEquals(-60, entity.getIntOr("y", 0));
        assertThrows(IllegalStateException.class, () -> HeadlessNativeTiles.prepare(new HeadlessNativeTiles.Placement(
                2, 4, 6, placement.origin(), ModdedBlockState.of(Blocks.BARREL.defaultBlockState(), Map.of()), tile)));
    }

}
