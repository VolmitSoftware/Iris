package art.arcane.iris.pack.datapack;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;

public class DataVersionTest {
    @Test
    public void runtimeSelectionPreservesEverySupportedVersion() {
        assertSame(DataVersion.V26_1_2, DataVersion.forMinecraftVersion("26.1.2"));
        assertSame(DataVersion.V26_2, DataVersion.forMinecraftVersion("26.2"));
        assertSame(DataVersion.V26_2, DataVersion.forMinecraftVersion("26.2.0"));
        assertSame(DataVersion.V26_3, DataVersion.forMinecraftVersion("26.3"));
        assertSame(DataVersion.V26_3, DataVersion.forMinecraftVersion("26.3.0"));
        assertSame(DataVersion.UNSUPPORTED, DataVersion.forMinecraftVersion("26.4"));
    }

    @Test
    public void latestRemainsNewestSupportedRelease() {
        assertSame(DataVersion.V26_3, DataVersion.getLatest());
    }

    @Test
    public void packFormatsMatchServerVersionJson() {
        assertEquals(101, DataVersion.V26_1_2.getPackFormat());
        assertEquals(107, DataVersion.V26_2.getPackFormat());
        assertEquals(121, DataVersion.V26_3.getPackFormat());
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
