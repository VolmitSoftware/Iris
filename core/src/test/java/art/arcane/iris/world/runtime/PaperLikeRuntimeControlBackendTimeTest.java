package art.arcane.iris.world.runtime;

import art.arcane.iris.spi.IrisLogging;
import art.arcane.iris.testsupport.BukkitTestServer;
import art.arcane.iris.world.lifecycle.CapabilitySnapshot;
import org.bukkit.World;
import org.junit.Before;
import org.junit.Test;
import org.mockito.MockedStatic;

import java.util.OptionalLong;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

public class PaperLikeRuntimeControlBackendTimeTest {
    @Before
    public void installServer() {
        BukkitTestServer.install();
    }

    @Test
    public void missingWorldHandleSkipsClockWithoutReportingFailure() throws Exception {
        assertUnsupportedClock(mock(World.class));
    }

    @Test
    public void missingClockMethodsSkipClockWithoutReportingFailure() throws Exception {
        assertUnsupportedClock(worldWithHandle(new Object()));
    }

    @Test
    public void supportedClockStillReadsAndWrites() throws Exception {
        ClockHandle handle = new ClockHandle();
        World world = worldWithHandle(handle);
        PaperLikeRuntimeControlBackend backend = new PaperLikeRuntimeControlBackend(mock(CapabilitySnapshot.class));

        assertEquals(OptionalLong.of(12000L), backend.readDayTime(world));
        assertTrue(backend.writeDayTime(world, 6000L));
        assertEquals(OptionalLong.of(6000L), backend.readDayTime(world));
    }

    private void assertUnsupportedClock(World world) throws Exception {
        PaperLikeRuntimeControlBackend backend = new PaperLikeRuntimeControlBackend(mock(CapabilitySnapshot.class));
        try (MockedStatic<IrisLogging> logging = mockStatic(IrisLogging.class)) {
            assertEquals(OptionalLong.empty(), backend.readDayTime(world));
            assertEquals(OptionalLong.empty(), backend.readDayTime(world));
            assertFalse(backend.writeDayTime(world, 6000L));
            logging.verify(() -> IrisLogging.reportError(anyString(), any(Throwable.class)), never());
        }
    }

    private World worldWithHandle(Object handle) {
        World world = mock(World.class, withSettings().extraInterfaces(HandleWorld.class));
        when(((HandleWorld) world).getHandle()).thenReturn(handle);
        return world;
    }

    public interface HandleWorld {
        Object getHandle();
    }

    public static class ClockHandle {
        private long dayTime = 12000L;

        public long getDayTime() {
            return dayTime;
        }

        public void setDayTime(long dayTime) {
            this.dayTime = dayTime;
        }
    }
}
