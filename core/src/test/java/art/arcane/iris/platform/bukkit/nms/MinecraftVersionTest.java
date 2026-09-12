package art.arcane.iris.platform.bukkit.nms;

import org.bukkit.Server;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

public class MinecraftVersionTest {
    private interface PaperLikeServer extends Server {
        String getMinecraftVersion();
    }

    @Test
    public void detectsMinecraftVersionFromPurpurDecoratedVersion() {
        Server server = mock(Server.class);
        doReturn("git-Purpur-2570 (MC: 26.2)").when(server).getVersion();
        doReturn("26.2.build.2570-experimental").when(server).getBukkitVersion();

        MinecraftVersion version = MinecraftVersion.detect(server);
        assertEquals("26.2", version.value());
        assertEquals(26, version.major());
        assertEquals(2, version.minor());
        assertEquals(0, version.patch());
    }

    @Test
    public void prefersRuntimeMinecraftVersionMethodWhenPresent() {
        PaperLikeServer server = mock(PaperLikeServer.class);
        doReturn("26.2").when(server).getMinecraftVersion();
        doReturn("26.2-2570-e64b1b2 (MC: 26.2)").when(server).getVersion();
        doReturn("26.2.build.2570-experimental").when(server).getBukkitVersion();

        MinecraftVersion version = MinecraftVersion.detect(server);
        assertEquals("26.2", version.value());
        assertEquals(26, version.major());
        assertEquals(2, version.minor());
        assertEquals(0, version.patch());
    }

    @Test
    public void rejectsPurpurApiBuildNumbersAsMinecraftVersion() {
        MinecraftVersion version = MinecraftVersion.fromBukkitVersion("26.2.build.2570-experimental");
        assertNull(version);
    }

    @Test
    public void parsesStandardBukkitSnapshotVersion() {
        MinecraftVersion version = MinecraftVersion.fromBukkitVersion("26.2-R0.1-SNAPSHOT");
        assertEquals("26.2", version.value());
        assertEquals(26, version.major());
        assertEquals(2, version.minor());
        assertEquals(0, version.patch());
    }

    @Test
    public void parsesThreePartNewSchemeVersion() {
        MinecraftVersion version = MinecraftVersion.fromBukkitVersion("26.1.2-R0.1-SNAPSHOT");
        assertEquals("26.1.2", version.value());
        assertEquals(26, version.major());
        assertEquals(1, version.minor());
        assertEquals(2, version.patch());
        assertTrue(version.isSameRelease(26, 1, 2));
        assertFalse(version.isSameRelease(26, 2, 0));
    }

    @Test
    public void detectsThreePartVersionFromDecoratedVersion() {
        Server server = mock(Server.class);
        doReturn("git-Paper-74 (MC: 26.1.2)").when(server).getVersion();
        doReturn("26.1.2-R0.1-SNAPSHOT").when(server).getBukkitVersion();

        MinecraftVersion version = MinecraftVersion.detect(server);
        assertEquals("26.1.2", version.value());
        assertEquals(26, version.major());
        assertEquals(1, version.minor());
        assertEquals(2, version.patch());
    }

    @Test
    public void comparesMajorBeforeMinor() {
        MinecraftVersion version = MinecraftVersion.fromBukkitVersion("26.2-R0.1-SNAPSHOT");
        assertFalse(version.isAtLeast(26, 2, 1));
        assertTrue(version.isAtLeast(26, 2, 0));
        assertTrue(version.isSameRelease(26, 2, 0));
    }
}
