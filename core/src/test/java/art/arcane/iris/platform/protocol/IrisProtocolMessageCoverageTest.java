/*
 * Iris is a World Generator for Minecraft Bukkit Servers
 * Copyright (c) 2026 Arcane Arts (Volmit Software)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package art.arcane.iris.platform.protocol;

import art.arcane.iris.spi.protocol.IrisMessage;
import art.arcane.iris.spi.protocol.IrisMessageCodec;
import art.arcane.iris.spi.protocol.IrisProtocol;
import art.arcane.iris.spi.protocol.ProtocolException;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Exhaustiveness gate for the wire protocol. Three ways a type can rot without any compiler complaint:
 * a TYPE_* constant is declared but no record claims it, a permitted record exists but no sample proves it
 * survives the codec, and a server-to-client type is defined but nothing on the server ever constructs it.
 *
 * <p>Producers are checked structurally, from the constant pool: a {@code Methodref} naming
 * {@code IrisMessage$X.<init>}. A plain class reference is not enough evidence - the dispatch switch in
 * IrisProtocolServer names every inbound record it consumes, so "mentions the class" and "produces the class"
 * are different facts.
 */
public class IrisProtocolMessageCoverageTest {
    private static final String MESSAGE_OWNER_PREFIX = "art/arcane/iris/spi/protocol/IrisMessage$";

    /** One sample per permitted record. Hand written on purpose: the field values are the wire contract. */
    private static final List<IrisMessage> SAMPLES = List.of(
            new IrisMessage.ClientHello(IrisProtocol.PROTOCOL_VERSION, IrisProtocol.CAPABILITY_VISION),
            new IrisMessage.ServerHello(IrisProtocol.PROTOCOL_VERSION, IrisProtocol.CAPABILITY_VISION, "Fabric", true),
            new IrisMessage.PregenProgress(1L, 2L, 3L, 4.5D, 6L, IrisMessage.PregenProgress.STATE_RUNNING),
            new IrisMessage.PregenEnd(1L, true),
            new IrisMessage.DimensionStatus("minecraft:overworld", "overworld", 1337L, -64, 320, true),
            new IrisMessage.CursorInfoRequest(16, -32),
            new IrisMessage.CursorInfo(16, -32, "iris:plains", "iris:temperate", "", 72, "overworld"),
            new IrisMessage.VisionTileRequest(1, 2, 3),
            new IrisMessage.VisionTile(1, 2, 3, 4, 0, 1, new byte[]{7, 8}),
            new IrisMessage.VisionMarkers(1, 2, 3, List.of(new IrisMessage.VisionMarkers.Marker(4, 5, 6, "spawn"))),
            new IrisMessage.PregenRegionDelta(1L, 2, 3, IrisMessage.PregenRegionDelta.STATE_DONE),
            new IrisMessage.StudioHotload("overworld", 3, false, ""),
            new IrisMessage.Toast(IrisMessage.Toast.KIND_INFO, "Title", "Body"));

    /** Server-to-client types the server must be able to construct, and where that construction lives. */
    private static final List<Class<?>> SERVER_PRODUCERS = List.of(
            IrisProtocolServer.class,
            IrisCursorResolver.class,
            IrisTileEncoder.class);
    private static final Set<Integer> CLIENT_PRODUCED_TYPES = Set.of(
            IrisProtocol.TYPE_CLIENT_HELLO,
            IrisProtocol.TYPE_CURSOR_INFO_REQUEST,
            IrisProtocol.TYPE_VISION_TILE_REQUEST);
    /**
     * TYPE_VISION_MARKERS has no producer by decision, not by omission: the codec and the client-side overlay
     * exist, the server-side marker source is deferred past 1.0. Keeping the type wired both ways means adding
     * the producer later is not a wire change. If a producer ever lands, delete this entry.
     */
    private static final Set<Integer> PRODUCER_DEFERRED_TYPES = Set.of(IrisProtocol.TYPE_VISION_MARKERS);

    @Test
    public void everyTypeConstantIsClaimedByExactlyOneRecord() {
        Map<Integer, String> declared = declaredTypeConstants();
        Map<Integer, String> claimed = new TreeMap<>();
        for (IrisMessage sample : SAMPLES) {
            String previous = claimed.put(sample.messageTypeId(), sample.getClass().getSimpleName());
            assertNull("type id " + sample.messageTypeId() + " claimed twice", previous);
        }
        assertEquals("TYPE_* constants without a message record, or records without a constant",
                declared.keySet(), claimed.keySet());
    }

    @Test
    public void everyPermittedRecordHasASample() {
        Set<String> permitted = new LinkedHashSet<>();
        for (Class<?> subtype : IrisMessage.class.getPermittedSubclasses()) {
            permitted.add(subtype.getSimpleName());
        }
        Set<String> sampled = new LinkedHashSet<>();
        for (IrisMessage sample : SAMPLES) {
            sampled.add(sample.getClass().getSimpleName());
        }
        assertEquals("a permitted IrisMessage record has no coverage sample", permitted, sampled);
    }

    @Test
    public void everySampleRoundTripsThroughTheCodec() throws ProtocolException {
        for (IrisMessage sample : SAMPLES) {
            byte[] frame = IrisMessageCodec.encode(sample);
            assertTrue(sample.getClass().getSimpleName() + " frame exceeds the cap",
                    frame.length <= IrisProtocol.MAX_FRAME_BYTES);
            IrisMessage decoded = IrisMessageCodec.decode(frame);
            assertNotNull(sample.getClass().getSimpleName() + " has an encoder but no decoder arm", decoded);
            assertEquals(sample.getClass(), decoded.getClass());
            assertEquals(sample.messageTypeId(), decoded.messageTypeId());
        }
    }

    @Test
    public void everyServerBoundTypeHasAProducerOrIsDeferred() throws IOException {
        Set<String> constructed = new LinkedHashSet<>();
        for (Class<?> producer : SERVER_PRODUCERS) {
            constructed.addAll(constructedMessageRecords(producer));
        }
        List<String> missing = new ArrayList<>();
        List<String> unexpected = new ArrayList<>();
        for (IrisMessage sample : SAMPLES) {
            String name = sample.getClass().getSimpleName();
            int typeId = sample.messageTypeId();
            boolean produced = constructed.contains(name);
            if (CLIENT_PRODUCED_TYPES.contains(typeId)) {
                if (produced) {
                    unexpected.add(name + " is client-to-server but the server constructs it");
                }
                continue;
            }
            if (PRODUCER_DEFERRED_TYPES.contains(typeId)) {
                if (produced) {
                    unexpected.add(name + " now has a server producer; drop it from PRODUCER_DEFERRED_TYPES");
                }
                continue;
            }
            if (!produced) {
                missing.add(name);
            }
        }
        assertEquals("server-to-client types with no producer in " + SERVER_PRODUCERS, List.of(), missing);
        assertEquals("producer direction no longer matches the declared roles", List.of(), unexpected);
    }

    private static Map<Integer, String> declaredTypeConstants() {
        Map<Integer, String> constants = new TreeMap<>();
        for (Field field : IrisProtocol.class.getDeclaredFields()) {
            if (!field.getName().startsWith("TYPE_")
                    || !Modifier.isStatic(field.getModifiers())
                    || field.getType() != int.class) {
                continue;
            }
            try {
                constants.put(field.getInt(null), field.getName());
            } catch (IllegalAccessException unreachable) {
                throw new AssertionError("TYPE_* constant is not readable: " + field.getName(), unreachable);
            }
        }
        assertTrue("no TYPE_* constants found on IrisProtocol", constants.size() >= 13);
        return constants;
    }

    /**
     * Reads {@code owner} out of every {@code CONSTANT_Methodref} whose name is {@code <init>} and whose owner
     * is an IrisMessage record. Bytes only, no class loading, same reason as the core purity gate.
     */
    private static Set<String> constructedMessageRecords(Class<?> type) throws IOException {
        byte[] bytes;
        try (InputStream stream = type.getResourceAsStream(type.getSimpleName() + ".class")) {
            assertNotNull("class file missing for " + type.getName(), stream);
            bytes = stream.readAllBytes();
        }
        ConstantPool pool = ConstantPool.read(bytes);
        Set<String> constructed = new LinkedHashSet<>();
        for (int[] methodref : pool.methodrefs()) {
            String owner = pool.className(methodref[0]);
            if (owner == null || !owner.startsWith(MESSAGE_OWNER_PREFIX)) {
                continue;
            }
            if (!"<init>".equals(pool.memberName(methodref[1]))) {
                continue;
            }
            constructed.add(owner.substring(MESSAGE_OWNER_PREFIX.length()));
        }
        return constructed;
    }

    /**
     * The slice of the class-file format this gate needs: UTF8 entries, Class entries, NameAndType entries and
     * the Methodref entries that point at them.
     */
    private record ConstantPool(String[] utf8, int[] classNameIndex, int[] nameAndTypeNameIndex,
                                List<int[]> methodrefs) {
        private static final int CONSTANT_UTF8 = 1;
        private static final int CONSTANT_INTEGER = 3;
        private static final int CONSTANT_FLOAT = 4;
        private static final int CONSTANT_LONG = 5;
        private static final int CONSTANT_DOUBLE = 6;
        private static final int CONSTANT_CLASS = 7;
        private static final int CONSTANT_STRING = 8;
        private static final int CONSTANT_FIELDREF = 9;
        private static final int CONSTANT_METHODREF = 10;
        private static final int CONSTANT_INTERFACE_METHODREF = 11;
        private static final int CONSTANT_NAME_AND_TYPE = 12;
        private static final int CONSTANT_METHOD_HANDLE = 15;
        private static final int CONSTANT_METHOD_TYPE = 16;
        private static final int CONSTANT_DYNAMIC = 17;
        private static final int CONSTANT_INVOKE_DYNAMIC = 18;
        private static final int CONSTANT_MODULE = 19;
        private static final int CONSTANT_PACKAGE = 20;

        String className(int index) {
            int nameIndex = classNameIndex[index];
            return nameIndex == 0 ? null : utf8[nameIndex];
        }

        String memberName(int nameAndTypeIndex) {
            int nameIndex = nameAndTypeNameIndex[nameAndTypeIndex];
            return nameIndex == 0 ? null : utf8[nameIndex];
        }

        static ConstantPool read(byte[] bytes) throws IOException {
            DataInputStream in = new DataInputStream(new ByteArrayInputStream(bytes));
            if (in.readInt() != 0xCAFEBABE) {
                throw new IOException("not a class file");
            }
            in.readUnsignedShort();
            in.readUnsignedShort();
            int count = in.readUnsignedShort();
            String[] utf8 = new String[count];
            int[] classNameIndex = new int[count];
            int[] nameAndTypeNameIndex = new int[count];
            Map<Integer, int[]> refs = new LinkedHashMap<>();
            for (int index = 1; index < count; index++) {
                int tag = in.readUnsignedByte();
                switch (tag) {
                    case CONSTANT_UTF8 -> utf8[index] = in.readUTF();
                    case CONSTANT_CLASS -> classNameIndex[index] = in.readUnsignedShort();
                    case CONSTANT_NAME_AND_TYPE -> {
                        nameAndTypeNameIndex[index] = in.readUnsignedShort();
                        in.readUnsignedShort();
                    }
                    case CONSTANT_METHODREF, CONSTANT_INTERFACE_METHODREF ->
                            refs.put(index, new int[]{in.readUnsignedShort(), in.readUnsignedShort()});
                    case CONSTANT_INTEGER, CONSTANT_FLOAT, CONSTANT_FIELDREF, CONSTANT_DYNAMIC,
                         CONSTANT_INVOKE_DYNAMIC -> in.readInt();
                    case CONSTANT_LONG, CONSTANT_DOUBLE -> {
                        in.readLong();
                        index++;
                    }
                    case CONSTANT_STRING, CONSTANT_METHOD_TYPE, CONSTANT_MODULE, CONSTANT_PACKAGE ->
                            in.readUnsignedShort();
                    case CONSTANT_METHOD_HANDLE -> {
                        in.readUnsignedByte();
                        in.readUnsignedShort();
                    }
                    default -> throw new IOException("unknown constant pool tag " + tag + " at " + index);
                }
            }
            return new ConstantPool(utf8, classNameIndex, nameAndTypeNameIndex, List.copyOf(refs.values()));
        }
    }
}
