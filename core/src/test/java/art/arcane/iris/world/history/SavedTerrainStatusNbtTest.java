package art.arcane.iris.world.history;

import art.arcane.volmlib.util.nbt.io.NBTUtil;
import art.arcane.volmlib.util.nbt.tag.CompoundTag;
import art.arcane.volmlib.util.nbt.tag.NumberTag;
import art.arcane.volmlib.util.nbt.tag.StringTag;
import art.arcane.volmlib.util.nbt.tag.Tag;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Arrays;

import static org.junit.Assert.assertTrue;

public final class SavedTerrainStatusNbtTest {

    @Test
    public void validatesEveryTagAndListType() throws Exception {
        for (int type = 1; type <= 12; type++) {
            int tagType = type;
            check("tag-" + type, root(out -> field(out, tagType, "extra", child -> payload(child, tagType))), 7, -9);
        }
        for (int type = 0; type <= 12; type++) {
            int tagType = type;
            check("list-" + type, root(out -> field(out, 9, "extra", child -> {
                child.writeByte(tagType);
                child.writeInt(2);
                payload(child, tagType);
                payload(child, tagType);
            })), 7, -9);
        }
    }

    @Test
    public void preservesNumericCoordinateConversions() throws Exception {
        for (int type = 1; type <= 6; type++) {
            int tagType = type;
            check("coordinates-" + type, root(out -> {
                field(out, tagType, "xPos", child -> numeric(child, tagType, 7));
                field(out, tagType, "zPos", child -> numeric(child, tagType, -9));
            }), 7, -9);
        }
        double[] special = {Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY, -9.75};
        for (double value : special) {
            check("double-coordinate-" + value, root(out -> field(out, 6, "xPos", child -> child.writeDouble(value))), (int) value, -9);
        }
        check("long-overflow-coordinate", root(out -> field(out, 4, "xPos", child -> child.writeLong(0x100000007L))), 7, -9);
    }

    @Test
    public void validatesModifiedUtfNamesAndValues() throws Exception {
        String[] strings = {"minecraft:full", "nul\u0000tail", "\u00e9\u4e00", "\ud83d\ude00", "\ud800", "\udc00", "\t \n", ""};
        for (int index = 0; index < strings.length; index++) {
            String value = strings[index];
            check("status-utf-" + index, root(out -> field(out, 8, "Status", child -> child.writeUTF(value))), 7, -9);
        }
        byte[][] malformed = {{(byte) 0x80}, {(byte) 0xc2}, {(byte) 0xe1, (byte) 0x80}, {(byte) 0xf0, (byte) 0x90, (byte) 0x80, (byte) 0x80}, {(byte) 0xc2, 0x41}};
        for (int index = 0; index < malformed.length; index++) {
            byte[] value = malformed[index];
            check("malformed-tail-utf-" + index, root(out -> field(out, 8, "unused", child -> rawUtf(child, value))), 7, -9);
        }
        check("overlong-status-name-2", namedStatus(2), 7, -9);
        check("overlong-status-name-3", namedStatus(3), 7, -9);
        check("overlong-x-name", root(out -> {
            out.writeByte(3);
            rawUtf(out, new byte[]{(byte) 0xc1, (byte) 0xb8, 'P', 'o', 's'});
            out.writeInt(11);
        }), 11, -9);
    }

    @Test
    public void preservesDuplicateAndNestedFieldSemantics() throws Exception {
        check("duplicate-status-last-wins", root(out -> field(out, 8, "Status", child -> child.writeUTF("minecraft:noise"))), 7, -9);
        check("duplicate-status-wrong-type", root(out -> field(out, 3, "Status", child -> child.writeInt(4))), 7, -9);
        check("duplicate-x-last-wins", root(out -> field(out, 3, "xPos", child -> child.writeInt(11))), 11, -9);
        check("duplicate-x-wrong-type", root(out -> field(out, 8, "xPos", child -> child.writeUTF("7"))), 7, -9);
        check("duplicate-z-last-wins", root(out -> field(out, 3, "zPos", child -> child.writeInt(11))), 7, 11);
        check("nested-fields-ignored", root(out -> field(out, 10, "extra", child -> {
            field(child, 3, "xPos", value -> value.writeInt(33));
            field(child, 8, "Status", value -> value.writeUTF("minecraft:empty"));
            child.writeByte(0);
        })), 7, -9);
    }

    @Test
    public void validatesNestingAndArrayLengths() throws Exception {
        for (int depth : new int[]{510, 511, 512, 513}) {
            check("compound-depth-" + depth, root(out -> nested(out, depth, false)), 7, -9);
            check("list-depth-" + depth, root(out -> nested(out, depth, true)), 7, -9);
        }
        for (int type : new int[]{7, 11, 12}) {
            check("negative-array-" + type, root(out -> field(out, type, "extra", child -> child.writeInt(-1))), 7, -9);
            check("truncated-array-" + type, root(out -> field(out, type, "extra", child -> child.writeInt(65536))), 7, -9);
        }
        for (int length : new int[]{-1, 0, 1}) {
            check("invalid-list-type-length-" + length, root(out -> field(out, 9, "extra", child -> {
                child.writeByte(99);
                child.writeInt(length);
            })), 7, -9);
        }
        check("negative-list-length", root(out -> field(out, 9, "extra", child -> {
            child.writeByte(3);
            child.writeInt(-1);
        })), 7, -9);
    }

    @Test
    public void validatesTruncatedAndMalformedPayloads() throws Exception {
        byte[] valid = root(out -> field(out, 8, "tail", child -> child.writeUTF("tailvalue")));
        for (int cut : new int[]{0, 1, 2, 3, valid.length - 1, valid.length - 2, valid.length - 8}) {
            check("truncated-at-" + cut, Arrays.copyOf(valid, cut), 7, -9);
        }
        check("invalid-tail-tag", root(out -> { out.writeByte(99); out.writeUTF("tail"); }), 7, -9);
        check("trailing-garbage", Arrays.copyOf(valid, valid.length + 3), 7, -9);
        check("coordinates-mismatch", valid, 8, -9);
        check("raw-zero-string", root(out -> field(out, 8, "Status", child -> rawUtf(child, new byte[]{0}))), 7, -9);
        check("overlong-value", root(out -> field(out, 8, "Status", child -> rawUtf(child, new byte[]{(byte) 0xc1, (byte) 0x93}))), 7, -9);
        check("non-ascii-root-key", root(out -> field(out, 8, "xP\u00f6s", child -> child.writeUTF("ignored"))), 7, -9);
        check("maximum-utf-name", root(out -> field(out, 1, "k".repeat(65535), child -> child.writeByte(3))), 7, -9);
        check("root-not-compound", new byte[]{3, 0, 0, 0, 0, 0, 1}, 7, -9);
        check("empty-compound-missing-fields", new byte[]{10, 0, 0, 0}, 7, -9);
        check("root-malformed-utf", new byte[]{10, 0, 1, (byte) 0x80, 0}, 7, -9);
        check("maximum-empty-list-type", root(out -> field(out, 9, "extra", child -> {
            child.writeByte(255);
            child.writeInt(0);
        })), 7, -9);
        check("empty-array", root(out -> field(out, 12, "extra", child -> child.writeInt(0))), 7, -9);
        check("truncated-primitive-tail", root(out -> field(out, 6, "extra", child -> child.writeInt(4))), 7, -9);
        check("malformed-utf-name-tail", root(out -> {
            out.writeByte(1);
            rawUtf(out, new byte[]{(byte) 0xc2});
            out.writeByte(0);
        }), 7, -9);
        for (int depth : new int[]{511, 512}) {
            check("nonempty-depth-" + depth, root(out -> {
                for (int index = 0; index < depth; index++) {
                    out.writeByte(10);
                    out.writeUTF("nested");
                }
                field(out, 1, "leaf", child -> child.writeByte(1));
                for (int index = 0; index < depth; index++) {
                    out.writeByte(0);
                }
            }), 7, -9);
        }
        check("float-nan-coordinate", root(out -> field(out, 5, "xPos", child -> child.writeFloat(Float.NaN))), 0, -9);

    }

    private static byte[] namedStatus(int width) throws IOException {
        return root(out -> {
            out.writeByte(8);
            if (width == 2) {
                rawUtf(out, new byte[]{(byte) 0xc1, (byte) 0x93, 't', 'a', 't', 'u', 's'});
            } else {
                rawUtf(out, new byte[]{(byte) 0xe0, (byte) 0x81, (byte) 0x93, 't', 'a', 't', 'u', 's'});
            }
            out.writeUTF("minecraft:noise");
        });
    }

    private static void check(String label, byte[] data, int x, int z) {
        Outcome tree = tree(data, x, z);
        Outcome cursor;
        try {
            cursor = new Outcome(true, SavedTerrainStatusNbt.read(data, 0, data.length, x, z), "");
        } catch (IOException | RuntimeException failure) {
            cursor = new Outcome(false, null, failure.getClass().getSimpleName());
        }
        boolean same = tree.accepted == cursor.accepted && (!tree.accepted || tree.status.equals(cursor.status));
        assertTrue(label + " tree=" + tree + " cursor=" + cursor, same);
    }

    private static Outcome tree(byte[] bytes, int x, int z) {
        try {
            Tag<?> parsed = NBTUtil.read(new ByteArrayInputStream(bytes), false).getTag();
            if (!(parsed instanceof CompoundTag root)
                    || !(root.get("xPos") instanceof NumberTag<?> savedX) || savedX.asInt() != x
                    || !(root.get("zPos") instanceof NumberTag<?> savedZ) || savedZ.asInt() != z
                    || !(root.get("Status") instanceof StringTag status) || status.getValue().isBlank()) {
                throw new IOException("Invalid chunk fields");
            }
            return new Outcome(true, status.getValue(), "");
        } catch (IOException | RuntimeException failure) {
            return new Outcome(false, null, failure.getClass().getSimpleName());
        }
    }

    private static byte[] root(Writer extra) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(bytes);
        out.writeByte(10);
        out.writeUTF("\u0000\ud800root");
        field(out, 3, "xPos", child -> child.writeInt(7));
        field(out, 3, "zPos", child -> child.writeInt(-9));
        field(out, 8, "Status", child -> child.writeUTF("minecraft:full"));
        extra.write(out);
        out.writeByte(0);
        return bytes.toByteArray();
    }

    private static void field(DataOutputStream out, int type, String key, Writer value) throws IOException {
        out.writeByte(type);
        out.writeUTF(key);
        value.write(out);
    }

    private static void rawUtf(DataOutputStream out, byte[] bytes) throws IOException {
        out.writeShort(bytes.length);
        out.write(bytes);
    }

    private static void nested(DataOutputStream out, int depth, boolean lists) throws IOException {
        out.writeByte(lists ? 9 : 10);
        out.writeUTF("nested");
        for (int index = 1; index < depth; index++) {
            out.writeByte(lists ? 9 : 10);
            if (lists) {
                out.writeInt(1);
            } else {
                out.writeUTF("nested");
            }
        }
        if (lists) {
            out.writeByte(0);
            out.writeInt(0);
        } else {
            for (int index = 0; index < depth; index++) {
                out.writeByte(0);
            }
        }
    }

    private static void payload(DataOutputStream out, int type) throws IOException {
        switch (type) {
            case 0 -> { }
            case 1, 2, 3, 4, 5, 6 -> numeric(out, type, 7);
            case 7 -> { out.writeInt(3); out.write(new byte[]{1, 2, 3}); }
            case 8 -> out.writeUTF("\u0000\ud83d\ude00\u00e9\u4e00");
            case 9 -> { out.writeByte(3); out.writeInt(1); out.writeInt(42); }
            case 10 -> { field(out, 3, "nested", child -> child.writeInt(42)); out.writeByte(0); }
            case 11 -> { out.writeInt(2); out.writeInt(42); out.writeInt(-42); }
            case 12 -> { out.writeInt(2); out.writeLong(42); out.writeLong(-42); }
            default -> throw new IOException("Unknown type");
        }
    }

    private static void numeric(DataOutputStream out, int type, int value) throws IOException {
        switch (type) {
            case 1 -> out.writeByte(value);
            case 2 -> out.writeShort(value);
            case 3 -> out.writeInt(value);
            case 4 -> out.writeLong(value);
            case 5 -> out.writeFloat(value);
            case 6 -> out.writeDouble(value);
            default -> throw new IOException("Unknown numeric type");
        }
    }

    private record Outcome(boolean accepted, String status, String failure) {
    }

    private interface Writer {
        void write(DataOutputStream out) throws IOException;
    }
}
