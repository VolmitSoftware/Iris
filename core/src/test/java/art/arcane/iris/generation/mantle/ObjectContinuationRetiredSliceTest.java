package art.arcane.iris.generation.mantle;

import art.arcane.iris.world.storage.matter.IrisMatterSupport;
import art.arcane.iris.world.storage.matter.RetiredMatterSlices;
import art.arcane.volmlib.util.matter.IrisMatter;
import art.arcane.volmlib.util.matter.Matter;
import art.arcane.volmlib.util.matter.MatterHeader;
import art.arcane.volmlib.util.matter.MatterSlice;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class ObjectContinuationRetiredSliceTest {
    @Test
    public void retiredSliceIsSkippedAndRestDecodes() throws Exception {
        IrisMatterSupport.ensureRegistered();
        byte[] payload = payload(List.of(retiredCanopySliceBytes(), sliceBytes(Integer.class, 42)));

        assertNull(IrisMatter.getSliceType(RetiredMatterSlices.TREE_CANOPY));
        Matter decoded = fragment(payload).decode();
        assertEquals(Integer.valueOf(42), decoded.getSlice(Integer.class).get(1, 2, 3));
    }

    @Test
    public void duplicateRetiredSliceFails() throws Exception {
        IrisMatterSupport.ensureRegistered();
        byte[] canopy = retiredCanopySliceBytes();
        byte[] payload = payload(List.of(canopy, canopy));

        IOException failure = assertThrows(IOException.class, () -> fragment(payload).decode());
        assertTrue(failure.getMessage(), failure.getMessage().startsWith("Duplicate object continuation slice type"));
    }

    @Test
    public void unknownSliceStillFails() throws Exception {
        IrisMatterSupport.ensureRegistered();
        byte[] payload = payload(List.of(rawSliceBytes("art.arcane.iris.test.NeverRegistered"), sliceBytes(Integer.class, 42)));

        IOException failure = assertThrows(IOException.class, () -> fragment(payload).decode());
        assertEquals("Unknown object continuation slice type: art.arcane.iris.test.NeverRegistered", failure.getMessage());
    }

    @Test
    public void duplicateRegisteredSliceFails() throws Exception {
        IrisMatterSupport.ensureRegistered();
        byte[] number = sliceBytes(Integer.class, 42);
        byte[] payload = payload(List.of(number, number));

        IOException failure = assertThrows(IOException.class, () -> fragment(payload).decode());
        assertEquals("Duplicate object continuation slice type: " + IrisMatter.getSliceId(Integer.class), failure.getMessage());
    }

    private static byte[] retiredCanopySliceBytes() throws IOException {
        return rawSliceBytes(RetiredMatterSlices.TREE_CANOPY);
    }

    private static byte[] rawSliceBytes(String identifier) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream output = new DataOutputStream(bytes);
        output.writeUTF(identifier);
        output.writeInt(7);
        return bytes.toByteArray();
    }

    private static <T> byte[] sliceBytes(Class<T> type, T value) throws IOException {
        Matter matter = new IrisMatter(16, 16, 16);
        MatterSlice<T> slice = matter.slice(type);
        slice.set(1, 2, 3, value);
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        slice.write(new DataOutputStream(bytes));
        return bytes.toByteArray();
    }

    private static byte[] payload(List<byte[]> slices) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream output = new DataOutputStream(bytes);
        output.writeInt(16);
        output.writeInt(16);
        output.writeInt(16);
        output.writeByte(slices.size());
        new MatterHeader().write(output);
        for (byte[] slice : slices) {
            output.writeInt(slice.length);
            output.write(slice);
        }
        return bytes.toByteArray();
    }

    private static ObjectContinuationBundle.Fragment fragment(byte[] payload) {
        return new ObjectContinuationBundle.Fragment(
                new ObjectContinuationBundle.PlacementKey(ObjectContinuationBundle.Kind.STATIC, 0, 0, 0),
                new ObjectContinuationBundle.Bounds(0, 0, 16, 0),
                List.of(new ObjectContinuationBundle.ChunkPosition(0, 0), new ObjectContinuationBundle.ChunkPosition(1, 0)),
                payload);
    }
}
