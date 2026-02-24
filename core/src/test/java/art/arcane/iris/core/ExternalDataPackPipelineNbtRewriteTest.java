package art.arcane.iris.core;

import art.arcane.volmlib.util.nbt.io.NBTDeserializer;
import art.arcane.volmlib.util.nbt.io.NBTSerializer;
import art.arcane.volmlib.util.nbt.io.NamedTag;
import art.arcane.volmlib.util.nbt.tag.CompoundTag;
import art.arcane.volmlib.util.nbt.tag.IntTag;
import art.arcane.volmlib.util.nbt.tag.ListTag;
import art.arcane.volmlib.util.nbt.tag.Tag;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

public class ExternalDataPackPipelineNbtRewriteTest {
    @Test
    public void rewritesOnlyJigsawPoolReferencesForCompressedAndUncompressedNbt() throws Exception {
        for (boolean compressed : new boolean[]{false, true}) {
            byte[] source = encodeStructureNbt(compressed, true);
            Map<String, String> remapped = new HashMap<>();
            remapped.put("minecraft:witch_hut/foundation", "iris_external_1:witch_hut/foundation");

            byte[] rewritten = invokeRewrite(source, remapped);
            CompoundTag root = decodeRoot(rewritten, compressed);
            ListTag<?> blocks = root.getListTag("blocks");

            CompoundTag jigsawBlock = (CompoundTag) blocks.get(0);
            CompoundTag nonJigsawBlock = (CompoundTag) blocks.get(1);
            assertEquals("iris_external_1:witch_hut/foundation", jigsawBlock.getCompoundTag("nbt").getString("pool"));
            assertEquals("minecraft:witch_hut/foundation", nonJigsawBlock.getCompoundTag("nbt").getString("pool"));
        }
    }

    @Test
    public void nonJigsawPayloadIsLeftUnchanged() throws Exception {
        byte[] source = encodeStructureNbt(false, false);
        Map<String, String> remapped = new HashMap<>();
        remapped.put("minecraft:witch_hut/foundation", "iris_external_1:witch_hut/foundation");

        byte[] rewritten = invokeRewrite(source, remapped);
        assertArrayEquals(source, rewritten);
    }

    private byte[] invokeRewrite(byte[] input, Map<String, String> remappedKeys) {
        return StructureNbtJigsawPoolRewriter.rewrite(input, remappedKeys);
    }

    private byte[] encodeStructureNbt(boolean compressed, boolean includeJigsaw) throws Exception {
        CompoundTag root = new CompoundTag();
        ListTag<CompoundTag> palette = new ListTag<>(CompoundTag.class);

        CompoundTag firstPalette = new CompoundTag();
        firstPalette.putString("Name", includeJigsaw ? "minecraft:jigsaw" : "minecraft:stone");
        palette.add(firstPalette);

        CompoundTag secondPalette = new CompoundTag();
        secondPalette.putString("Name", "minecraft:stone");
        palette.add(secondPalette);
        root.put("palette", palette);

        ListTag<CompoundTag> blocks = new ListTag<>(CompoundTag.class);
        blocks.add(blockTag(0, "minecraft:witch_hut/foundation"));
        blocks.add(blockTag(1, "minecraft:witch_hut/foundation"));
        root.put("blocks", blocks);

        NamedTag named = new NamedTag("test", root);
        return new NBTSerializer(compressed).toBytes(named);
    }

    private CompoundTag blockTag(int state, String pool) {
        CompoundTag block = new CompoundTag();
        block.putInt("state", state);
        CompoundTag nbt = new CompoundTag();
        nbt.putString("pool", pool);
        block.put("nbt", nbt);
        ListTag<IntTag> pos = new ListTag<>(IntTag.class);
        pos.add(new IntTag(0));
        pos.add(new IntTag(0));
        pos.add(new IntTag(0));
        block.put("pos", pos);
        return block;
    }

    private CompoundTag decodeRoot(byte[] bytes, boolean compressed) throws Exception {
        NamedTag namedTag = new NBTDeserializer(compressed).fromStream(new ByteArrayInputStream(bytes));
        Tag<?> rootTag = namedTag.getTag();
        return (CompoundTag) rootTag;
    }
}
