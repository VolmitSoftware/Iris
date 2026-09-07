package art.arcane.iris.engine.object;

import art.arcane.iris.spi.PlatformBlockState;
import art.arcane.iris.util.common.math.IrisBlockVector;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class IrisObjectIoWriteLimitsTest {
    private static IrisObject oversizedPaletteObject;

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    @BeforeClass
    public static void buildSharedObjects() {
        oversizedPaletteObject = objectWithDistinctStates(32_768);
    }

    private static PlatformBlockState state(String key) {
        return new KeyedBlockState(key);
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
        IrisObject object = oversizedPaletteObject;

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

        IrisObject oversized = oversizedPaletteObject;
        try {
            IrisObjectIO.write(oversized, file);
            fail("expected the oversized write to be rejected");
        } catch (IOException expected) {
        }

        assertArrayEquals("a rejected write must not truncate the previous object", before,
                Files.readAllBytes(file.toPath()));
    }

    private static final class KeyedBlockState implements PlatformBlockState {
        private final String key;

        private KeyedBlockState(String key) {
            this.key = key;
        }

        @Override
        public String key() {
            return key;
        }

        @Override
        public String namespace() {
            return null;
        }

        @Override
        public String materialKey() {
            return null;
        }

        @Override
        public boolean isAir() {
            return false;
        }

        @Override
        public boolean isSolid() {
            return false;
        }

        @Override
        public boolean isOccluding() {
            return false;
        }

        @Override
        public boolean isCustom() {
            return false;
        }

        @Override
        public String deferredPlacementKey() {
            return null;
        }

        @Override
        public PlatformBlockState placementBaseState() {
            return null;
        }

        @Override
        public boolean isFluid() {
            return false;
        }

        @Override
        public boolean isWater() {
            return false;
        }

        @Override
        public boolean isWaterLogged() {
            return false;
        }

        @Override
        public boolean isLit() {
            return false;
        }

        @Override
        public boolean isUpdatable() {
            return false;
        }

        @Override
        public boolean isFoliage() {
            return false;
        }

        @Override
        public boolean isTreeBlock() {
            return false;
        }

        @Override
        public boolean isFoliagePlantable() {
            return false;
        }

        @Override
        public boolean isDecorant() {
            return false;
        }

        @Override
        public boolean isStorage() {
            return false;
        }

        @Override
        public boolean isStorageChest() {
            return false;
        }

        @Override
        public boolean isOre() {
            return false;
        }

        @Override
        public boolean isDeepSlate() {
            return false;
        }

        @Override
        public boolean isVineBlock() {
            return false;
        }

        @Override
        public boolean canPlaceOnto(PlatformBlockState onto) {
            return false;
        }

        @Override
        public boolean matches(PlatformBlockState state) {
            return false;
        }

        @Override
        public boolean isAirOrFluid() {
            return false;
        }

        @Override
        public boolean hasTileEntity() {
            return false;
        }

        @Override
        public PlatformBlockState withProperty(String name, String value) {
            return null;
        }

        @Override
        public Object nativeHandle() {
            return null;
        }

        @Override
        public int hashCode() {
            return key.hashCode();
        }

        @Override
        public boolean equals(Object other) {
            return this == other;
        }

        @Override
        public String toString() {
            return key;
        }
    }
}
