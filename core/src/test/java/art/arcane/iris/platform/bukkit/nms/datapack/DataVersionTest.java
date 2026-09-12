package art.arcane.iris.platform.bukkit.nms.datapack;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;

public class DataVersionTest {
    @Test
    public void latestRemainsNewestSupportedRelease() {
        assertSame(DataVersion.V26_2, DataVersion.getLatest());
    }

    @Test
    public void packFormatsMatchServerVersionJson() {
        assertEquals(101, DataVersion.V26_1_2.getPackFormat());
        assertEquals(107, DataVersion.V26_2.getPackFormat());
    }

    @Test
    public void minSupportedPackFormatIsTheOldestSupportedRuntime() {
        assertEquals(101, DataVersion.minSupportedPackFormat());
    }

    @Test
    public void bothSupportedReleasesShareAFixer() {
        assertNotNull(DataVersion.V26_1_2.get());
        assertNotNull(DataVersion.V26_2.get());
        assertSame(DataVersion.V26_1_2.get().getClass(), DataVersion.V26_2.get().getClass());
    }
}
