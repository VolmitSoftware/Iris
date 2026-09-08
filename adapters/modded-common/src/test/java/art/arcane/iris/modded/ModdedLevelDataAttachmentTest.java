package art.arcane.iris.modded;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class ModdedLevelDataAttachmentTest {
    @Test
    public void detectsAndInvokesHostLevelDataAttachment() {
        ModdedServerLevels.HostLevelDataAttachment attachment =
                ModdedServerLevels.HostLevelDataAttachment.detect(
                        AttachedLevelData.class, RootData.class, DimensionKey.class);
        AttachedLevelData levelData = new AttachedLevelData();
        RootData rootData = new RootData();
        DimensionKey dimensionKey = new DimensionKey();

        assertTrue(attachment.supported());
        attachment.attach(levelData, rootData, dimensionKey);
        assertSame(rootData, levelData.rootData);
        assertSame(dimensionKey, levelData.dimensionKey);
    }

    @Test
    public void ignoresLevelDataWithoutAttachmentCapability() {
        ModdedServerLevels.HostLevelDataAttachment attachment =
                ModdedServerLevels.HostLevelDataAttachment.detect(
                        StandardLevelData.class, RootData.class, DimensionKey.class);

        assertFalse(attachment.supported());
    }

    @Test
    public void propagatesHostAttachmentFailureWithoutReflectionWrapper() {
        ModdedServerLevels.HostLevelDataAttachment attachment =
                ModdedServerLevels.HostLevelDataAttachment.detect(
                        FailingLevelData.class, RootData.class, DimensionKey.class);
        IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> attachment.attach(new FailingLevelData(), new RootData(), new DimensionKey()));

        assertNull(failure.getCause());
    }

    public static final class AttachedLevelData {
        private RootData rootData;
        private DimensionKey dimensionKey;

        public void attach(RootData rootData, DimensionKey dimensionKey) {
            this.rootData = rootData;
            this.dimensionKey = dimensionKey;
        }
    }

    public static final class FailingLevelData {
        public void attach(RootData rootData, DimensionKey dimensionKey) {
            throw new IllegalStateException("attachment failed");
        }
    }

    public static final class StandardLevelData {
    }

    public static final class RootData {
    }

    public static final class DimensionKey {
    }
}
