package art.arcane.iris.platform.bukkit.nms;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class NmsBindingSelectorTest {
    @Test
    public void selectsOnlyTheExactSupportedRevision() {
        MinecraftVersion version = MinecraftVersion.fromBukkitVersion("26.2-R0.1-SNAPSHOT");

        assertEquals("v26_2_R1", NmsBindingSelector.select(version));
    }

    @Test
    public void selectsSharedRevisionFor2612() {
        MinecraftVersion version = MinecraftVersion.fromBukkitVersion("26.1.2-R0.1-SNAPSHOT");

        assertEquals("v26_2_R1", NmsBindingSelector.select(version));
    }

    @Test
    public void rejectsUnsupportedVersionsWithoutProbingAnotherRevision() {
        MinecraftVersion version = MinecraftVersion.fromBukkitVersion("26.3-R0.1-SNAPSHOT");

        IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> NmsBindingSelector.select(version));

        assertTrue(failure.getMessage().contains("26.3"));
    }

    @Test
    public void rejectsBasePatchOfSupportedMinorLine() {
        MinecraftVersion version = MinecraftVersion.fromBukkitVersion("26.1-R0.1-SNAPSHOT");

        IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> NmsBindingSelector.select(version));

        assertTrue(failure.getMessage().contains("26.1"));
    }

    @Test
    public void rejectsMissingVersionDetection() {
        assertThrows(IllegalStateException.class, () -> NmsBindingSelector.select(null));
    }
}
