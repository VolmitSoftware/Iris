package art.arcane.iris.pack.datapack.export;

import art.arcane.iris.generation.block.TileData;
import art.arcane.iris.pack.datapack.DataVersion;
import art.arcane.iris.pack.datapack.export.ir.ExportDiagnostic;
import art.arcane.iris.pack.datapack.export.ir.ExportLimit;
import art.arcane.iris.spi.IrisPlatform;
import art.arcane.iris.spi.IrisPlatforms;
import art.arcane.iris.spi.PlatformRegistries;
import art.arcane.iris.structure.object.IrisObject;
import art.arcane.iris.testsupport.PlatformLeakGuard;
import art.arcane.volmlib.nativelib.terrain.NativeBlockState;
import art.arcane.volmlib.util.collection.KMap;
import art.arcane.volmlib.util.nbt.io.NBTUtil;
import art.arcane.volmlib.util.nbt.mca.MCABlockStateCodecSupport;
import art.arcane.volmlib.util.nbt.tag.ByteTag;
import art.arcane.volmlib.util.nbt.tag.CompoundTag;
import art.arcane.volmlib.util.nbt.tag.IntTag;
import art.arcane.volmlib.util.nbt.tag.ListTag;
import art.arcane.volmlib.util.nbt.tag.LongTag;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.GZIPInputStream;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class StructureTemplateEncoderTest {
    @ClassRule
    public static final PlatformLeakGuard PLATFORM_GUARD = PlatformLeakGuard.clean();

    private static final String STAIRS = "minecraft:oak_stairs[facing=north]";

    @BeforeClass
    public static void bindPlatform() {
        IrisPlatforms.unbind();
        NativeBlockState stone = blockState("minecraft:stone");
        PlatformRegistries registries = mock(PlatformRegistries.class);
        when(registries.block(anyString())).thenReturn(stone);
        IrisPlatform platform = mock(IrisPlatform.class);
        when(platform.registries()).thenReturn(registries);
        when(platform.minecraftVersion()).thenReturn("1.21.1");
        IrisPlatforms.bind(platform);
    }

    @AfterClass
    public static void unbindPlatform() {
        IrisPlatforms.unbind();
    }

    @Test
    public void dataVersionFollowsTarget() throws IOException {
        IrisObject object = singleBlock("minecraft:stone");

        assertEquals(5023, encode(object, List.of(), DataVersion.V26_3).getInt("DataVersion"));
        assertEquals(4903, encode(object, List.of(), DataVersion.V26_2).getInt("DataVersion"));
    }

    @Test
    public void templatesAreCompleteGzipStreams() throws IOException {
        IrisObject object = singleBlock(STAIRS);

        for (DataVersion target : List.of(DataVersion.V26_3, DataVersion.V26_2)) {
            byte[] bytes = new StructureTemplateEncoder().encode(object, List.of(), target);
            byte[] inflated;
            try (InputStream input = new GZIPInputStream(new ByteArrayInputStream(bytes))) {
                inflated = input.readAllBytes();
                assertEquals(-1, input.read());
            }
            CompoundTag root = (CompoundTag) NBTUtil.read(new ByteArrayInputStream(inflated), false).getTag();
            assertEquals(target.getWorldDataVersion(), root.getInt("DataVersion"));
        }
    }

    @Test
    public void paletteFormatFollowsTarget() throws IOException {
        IrisObject object = singleBlock(STAIRS);
        VanillaBlockState stairs = VanillaBlockState.parse(STAIRS);

        assertEquals(stairs.toNbt(MCABlockStateCodecSupport.Format.LOWERCASE),
                encode(object, List.of(), DataVersion.V26_3).getListTag("palette").get(0));
        assertEquals(stairs.toNbt(MCABlockStateCodecSupport.Format.CAPITALIZED),
                encode(object, List.of(), DataVersion.V26_2).getListTag("palette").get(0));
    }

    @Test
    public void extraBlockOverridesObjectBlockAtSamePosition() throws IOException {
        IrisObject object = new IrisObject(2, 1, 1);
        object.setUnsigned(0, 0, 0, blockState("minecraft:stone"));
        object.setUnsigned(1, 0, 0, blockState("minecraft:stone"));
        CompoundTag nbt = new CompoundTag();
        nbt.putString("id", "minecraft:jigsaw");
        TemplateBlock jigsaw = new TemplateBlock(1, 0, 0,
                VanillaBlockState.parse("minecraft:jigsaw[orientation=north_up]"), nbt);

        CompoundTag template = encode(object, List.of(jigsaw), DataVersion.V26_3);

        ListTag<?> blocks = template.getListTag("blocks");
        assertEquals(2, blocks.size());
        CompoundTag first = (CompoundTag) blocks.get(0);
        CompoundTag second = (CompoundTag) blocks.get(1);
        assertEquals(List.of(0, 0, 0), position(first));
        assertNull(first.get("nbt"));
        assertEquals(List.of(1, 0, 0), position(second));
        assertEquals(nbt, second.getCompoundTag("nbt"));
        CompoundTag paletteEntry = (CompoundTag) template.getListTag("palette").get(second.getInt("state"));
        assertEquals("minecraft:jigsaw", paletteEntry.getString("id"));
        assertFalse(first.getInt("state") == second.getInt("state"));
    }

    @Test
    public void tileEntitiesRoundTrip() throws IOException {
        IrisObject object = new IrisObject(2, 1, 1);
        object.setUnsigned(0, 0, 0, blockState("minecraft:chest[facing=north]"));
        object.setUnsigned(1, 0, 0, blockState("minecraft:oak_sign[rotation=4]"));
        KMap<String, Object> item = new KMap<>();
        item.put("Slot", 3L);
        item.put("id", "minecraft:diamond");
        item.put("count", 5L);
        KMap<String, Object> chest = new KMap<>();
        chest.put("Items", new ArrayList<>(List.of(item)));
        chest.put("LootTableSeed", 42L);
        chest.put("x", 10L);
        object.setUnsignedTile(0, 0, 0, new TileData("minecraft:chest", chest));
        KMap<String, Object> sign = new KMap<>();
        sign.put("nbt", "{front_text:{color:\"black\",has_glowing_text:1b,messages:[\"\\\"a\\\"\",\"\\\"\\\"\",\"\\\"\\\"\",\"\\\"\\\"\"]},is_waxed:0b}");
        object.setUnsignedTile(1, 0, 0, new TileData("minecraft:oak_sign", sign));
        List<ExportDiagnostic> diagnostics = new ArrayList<>();

        TemplateVolume volume = StructureTemplateEncoder.volume(object, "objects/test", diagnostics);
        CompoundTag template = decode(new StructureTemplateEncoder().encode(volume, DataVersion.V26_3));

        assertTrue(diagnostics.isEmpty());
        ListTag<?> blocks = template.getListTag("blocks");
        CompoundTag chestNbt = ((CompoundTag) blocks.get(0)).getCompoundTag("nbt");
        assertEquals("minecraft:chest", chestNbt.getString("id"));
        assertEquals(42L, ((LongTag) chestNbt.get("LootTableSeed")).asLong());
        assertFalse(chestNbt.containsKey("x"));
        CompoundTag stack = (CompoundTag) chestNbt.getListTag("Items").get(0);
        assertEquals(3, ((ByteTag) stack.get("Slot")).asByte());
        assertEquals(5, ((IntTag) stack.get("count")).asInt());
        assertEquals("minecraft:diamond", stack.getString("id"));
        CompoundTag signNbt = ((CompoundTag) blocks.get(1)).getCompoundTag("nbt");
        assertEquals("minecraft:sign", signNbt.getString("id"));
        assertEquals(1, ((ByteTag) signNbt.getCompoundTag("front_text").get("has_glowing_text")).asByte());
        assertEquals(4, signNbt.getCompoundTag("front_text").getListTag("messages").size());
        assertEquals(volume.blocks().get(0).nbt(), chestNbt);
    }

    @Test
    public void unknownTileEntityBecomesExportLimit() {
        IrisObject object = new IrisObject(1, 1, 1);
        object.setUnsigned(0, 0, 0, blockState("minecraft:stone"));
        object.setUnsignedTile(0, 0, 0, new TileData("minecraft:stone", new KMap<>()));
        List<ExportDiagnostic> diagnostics = new ArrayList<>();

        TemplateVolume volume = StructureTemplateEncoder.volume(object, "objects/rock", diagnostics);

        assertNull(volume.blocks().getFirst().nbt());
        assertEquals(1, diagnostics.size());
        assertSame(ExportLimit.TILE_ENTITY, diagnostics.getFirst().limit());
        assertEquals("objects/rock", diagnostics.getFirst().resource());
    }

    @Test
    public void customBlocksAreLeftOutWithExportLimit() {
        IrisObject object = new IrisObject(2, 1, 1);
        object.setUnsigned(0, 0, 0, blockState("minecraft:stone"));
        object.setUnsigned(1, 0, 0, blockState("mymod:crystal"));
        List<ExportDiagnostic> diagnostics = new ArrayList<>();

        TemplateVolume volume = StructureTemplateEncoder.volume(object, "objects/crystal", diagnostics);

        assertEquals(1, volume.blocks().size());
        assertEquals(1, diagnostics.size());
        assertSame(ExportLimit.CUSTOM_BLOCK, diagnostics.getFirst().limit());
    }

    @Test
    public void blocksOutsideTheObjectSizeAreLeftOutWithExportLimit() {
        IrisObject object = new IrisObject(2, 2, 1);
        object.setUnsigned(0, 0, 0, blockState("minecraft:stone"));
        object.setUnsigned(1, -1, 0, blockState("minecraft:dirt"));
        object.setUnsigned(-1, 1, 0, blockState("minecraft:dirt"));
        List<ExportDiagnostic> diagnostics = new ArrayList<>();

        TemplateVolume volume = StructureTemplateEncoder.volume(object, "objects/overhang", diagnostics);

        assertEquals(1, volume.blocks().size());
        assertEquals(1, diagnostics.size());
        assertSame(ExportLimit.OBJECT_BOUNDS, diagnostics.getFirst().limit());
        assertEquals("objects/overhang", diagnostics.getFirst().resource());
    }

    @Test
    public void tileEntityMapsKeepTypedArrays() {
        KMap<String, Object> profile = new KMap<>();
        profile.put("id", new ArrayList<>(List.of(1L, 2L, 3L, 4L)));
        profile.put("name", "Notch");
        KMap<String, Object> head = new KMap<>();
        head.put("profile", profile);

        CompoundTag nbt = TileEntityNbt.toNbt("minecraft:player_head", head).orElseThrow();

        assertEquals("minecraft:skull", nbt.getString("id"));
        assertArrayEquals(new int[]{1, 2, 3, 4}, nbt.getCompoundTag("profile").getIntArray("id"));
        assertTrue(TileEntityNbt.toNbt("minecraft:stone", head).isEmpty());
    }

    private static CompoundTag decode(byte[] bytes) throws IOException {
        return (CompoundTag) NBTUtil.read(new ByteArrayInputStream(bytes), true).getTag();
    }

    private static CompoundTag encode(IrisObject object, List<TemplateBlock> extraBlocks, DataVersion target)
            throws IOException {
        byte[] bytes = new StructureTemplateEncoder().encode(object, extraBlocks, target);
        return (CompoundTag) NBTUtil.read(new ByteArrayInputStream(bytes), true).getTag();
    }

    private static IrisObject singleBlock(String key) {
        IrisObject object = new IrisObject(1, 1, 1);
        object.setUnsigned(0, 0, 0, blockState(key));
        return object;
    }

    private static List<Integer> position(CompoundTag block) {
        ListTag<?> pos = block.getListTag("pos");
        return List.of(((IntTag) pos.get(0)).asInt(), ((IntTag) pos.get(1)).asInt(), ((IntTag) pos.get(2)).asInt());
    }

    private static NativeBlockState blockState(String key) {
        NativeBlockState state = mock(NativeBlockState.class);
        when(state.key()).thenReturn(key);
        when(state.namespace()).thenReturn(key.substring(0, key.indexOf(':')));
        return state;
    }
}
