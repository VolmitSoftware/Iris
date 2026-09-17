package art.arcane.iris.engine.object.prefab;

import art.arcane.iris.engine.object.prefab.IrisObject;
import art.arcane.iris.engine.object.prefab.IrisObjectIO;
import art.arcane.iris.spi.PlatformBlockState;
import art.arcane.iris.util.common.math.IrisBlockVector;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Proxy;
import java.nio.file.Files;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class IrisObjectIoWriteLimitsTest {
    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    private static PlatformBlockState state(String key) {
        return (PlatformBlockState) Proxy.newProxyInstance(
                IrisObjectIoWriteLimitsTest.class.getClassLoader(),
                new Class<?>[]{PlatformBlockState.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "key" -> key;
                    case "toString" -> key;
                    case "hashCode" -> key.hashCode();
                    case "equals" -> proxy == args[0];
                    default -> switch (method.getReturnType().getName()) {
                        case "boolean" -> false;
                        case "int" -> 0;
                        case "double" -> 0D;
                        default -> null;
                    };
                });
    }

    private static IrisObject objectWithDistinctStates(int paletteSize) {
        IrisObject object = new IrisObject(64, 64, 64);
        object.setLoadKey("limits-test");
        int placed = 0;
        outer:
        for (int x = 0; x < 64; x++) {
            for (int y = 0; y < 64; y++) {
                for (int z = 0; z < 64; z++) {
                    if (placed >= paletteSize) {
                        break outer;
                    }
                    object.blocks.put(new IrisBlockVector(x, y, z), state("iris:test_" + placed));
                    placed++;
                }
            }
        }
        return object;
    }

    @Test
    public void rejectsPaletteOverflowInsteadOfWrappingTheShort() {
        IrisObject object = objectWithDistinctStates(32_768);

        try {
            IrisObjectIO.write(object, new ByteArrayOutputStream());
            fail("expected the oversized palette to be rejected");
        } catch (IOException e) {
            assertTrue(e.getMessage(), e.getMessage().contains("limits-test"));
            assertTrue(e.getMessage(), e.getMessage().contains("32768"));
        }
    }

    @Test
    public void acceptsPaletteAtExactlyTheCap() throws IOException {
        IrisObject object = objectWithDistinctStates(32_767);
        File file = folder.newFile("cap.iob");

        IrisObjectIO.write(object, file);

        assertEquals(32_767, IrisObjectIO.readPaletteKeys(file).size());
    }

    @Test
    public void rejectsCoordinateBeyondShortRange() {
        IrisObject object = new IrisObject(64, 64, 64);
        object.setLoadKey("limits-test");
        object.blocks.put(new IrisBlockVector(40_000, 0, 0), state("iris:test"));

        try {
            IrisObjectIO.write(object, new ByteArrayOutputStream());
            fail("expected the out-of-range coordinate to be rejected");
        } catch (IOException e) {
            assertTrue(e.getMessage(), e.getMessage().contains("40000"));
            assertTrue(e.getMessage(), e.getMessage().contains("x"));
        }
    }

    @Test
    public void failedWriteLeavesExistingFileIntact() throws IOException {
        File file = folder.newFile("existing.iob");
        IrisObject valid = objectWithDistinctStates(3);
        IrisObjectIO.write(valid, file);
        byte[] before = Files.readAllBytes(file.toPath());

        IrisObject oversized = objectWithDistinctStates(32_768);
        try {
            IrisObjectIO.write(oversized, file);
            fail("expected the oversized write to be rejected");
        } catch (IOException expected) {
        }

        assertArrayEquals("a rejected write must not truncate the previous object", before,
                Files.readAllBytes(file.toPath()));
    }
}
