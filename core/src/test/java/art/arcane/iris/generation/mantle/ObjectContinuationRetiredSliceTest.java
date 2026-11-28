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
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class ObjectContinuationRetiredSliceTest {
    @Test
    public void retiredUnregisteredSliceIsSkippedAndRestDecodes() throws Exception {
        IrisMatterSupport.ensureRegistered();
        byte[] canopy = sliceBytes(TreeCanopy.class, TreeCanopy.INSTANCE);
        byte[] number = sliceBytes(Integer.class, 42);
        byte[] payload = payload(List.of(canopy, number));

        Object registered = unregister(RetiredMatterSlices.TREE_CANOPY);
        try {
            assertEquals(null, IrisMatter.getSliceType(RetiredMatterSlices.TREE_CANOPY));
            Matter decoded = fragment(payload).decode();
            assertFalse(decoded.hasSlice(TreeCanopy.class));
            assertEquals(Integer.valueOf(42), decoded.getSlice(Integer.class).get(1, 2, 3));
        } finally {
            restore(registered);
        }
    }

    @Test
    public void duplicateRetiredUnregisteredSliceFails() throws Exception {
        IrisMatterSupport.ensureRegistered();
        byte[] canopy = sliceBytes(TreeCanopy.class, TreeCanopy.INSTANCE);
        byte[] payload = payload(List.of(canopy, canopy));

        Object registered = unregister(RetiredMatterSlices.TREE_CANOPY);
        try {
            IOException failure = assertThrows(IOException.class, () -> fragment(payload).decode());
            assertTrue(failure.getMessage(), failure.getMessage().startsWith("Duplicate object continuation slice type"));
        } finally {
            restore(registered);
        }
    }

    @Test
    public void unknownSliceStillFails() throws Exception {
        IrisMatterSupport.ensureRegistered();
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream output = new DataOutputStream(bytes);
        output.writeUTF("art.arcane.iris.test.NeverRegistered");
        output.writeInt(7);
        byte[] payload = payload(List.of(bytes.toByteArray(), sliceBytes(Integer.class, 42)));

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

    @Test
    public void registeredRetiredSliceStillDecodes() throws Exception {
        IrisMatterSupport.ensureRegistered();
        byte[] payload = payload(List.of(sliceBytes(TreeCanopy.class, TreeCanopy.INSTANCE)));

        Matter decoded = fragment(payload).decode();
        assertEquals(TreeCanopy.INSTANCE, decoded.getSlice(TreeCanopy.class).get(1, 2, 3));
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

    private static Field registryField() throws ReflectiveOperationException {
        Field field = IrisMatter.class.getDeclaredField("sliceRegistry");
        field.setAccessible(true);
        return field;
    }

    @SuppressWarnings("unchecked")
    private static Object unregister(String id) throws ReflectiveOperationException {
        Field field = registryField();
        Object registry = field.get(null);
        Class<?> registryType = registry.getClass();
        Method byTypeAccessor = registryType.getDeclaredMethod("byType");
        Method byIdAccessor = registryType.getDeclaredMethod("byId");
        byTypeAccessor.setAccessible(true);
        byIdAccessor.setAccessible(true);
        Map<Class<?>, Object> byType = new HashMap<>((Map<Class<?>, Object>) byTypeAccessor.invoke(registry));
        Map<String, Object> byId = new HashMap<>((Map<String, Object>) byIdAccessor.invoke(registry));
        Class<?> type = IrisMatter.getSliceType(id);
        byType.remove(type);
        byId.remove(id);
        Constructor<?> constructor = registryType.getDeclaredConstructor(Map.class, Map.class);
        constructor.setAccessible(true);
        field.set(null, constructor.newInstance(Map.copyOf(byType), Map.copyOf(byId)));
        return registry;
    }

    private static void restore(Object registry) throws ReflectiveOperationException {
        registryField().set(null, registry);
    }
}
