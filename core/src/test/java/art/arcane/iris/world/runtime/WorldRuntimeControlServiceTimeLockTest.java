package art.arcane.iris.world.runtime;

import art.arcane.iris.world.lifecycle.CapabilitySnapshot;
import art.arcane.volmlib.nativelib.terrain.NativeWorldRuntime;
import art.arcane.volmlib.nativelib.terrain.NativeWorldClock;
import art.arcane.iris.world.lifecycle.ServerFamily;
import art.arcane.iris.spi.IrisPlatform;
import art.arcane.iris.spi.IrisPlatforms;
import art.arcane.iris.testsupport.BukkitTestServer;
import org.mockito.ArgumentCaptor;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.junit.Before;
import org.junit.Test;

import java.lang.reflect.Constructor;
import java.util.OptionalLong;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class WorldRuntimeControlServiceTimeLockTest {
    private NativeWorldClock clock;
    @Before
    public void ensureBukkitServer() {
        BukkitTestServer.install();
    }

    @Test
    public void skipsTimeLockWhenWorldDoesNotExposeMutableClock() throws Exception {
        AtomicBoolean setTimeCalled = new AtomicBoolean(false);
        AtomicLong dayTime = new AtomicLong(0L);
        World world = createWorldProxy("fixed", true, setTimeCalled, dayTime, false);

        boolean applied = createService().applyNoonTimeLock(world);

        assertFalse(applied);
        assertFalse(setTimeCalled.get());
    }

    @Test
    public void skipsTimeLockWhenRuntimeSetterRejectsClockMutation() throws Exception {
        AtomicBoolean setTimeCalled = new AtomicBoolean(false);
        AtomicLong dayTime = new AtomicLong(0L);
        World world = createWorldProxy("no-clock", false, setTimeCalled, dayTime, true);

        boolean applied = createService().applyNoonTimeLock(world);

        assertFalse(applied);
        assertTrue(setTimeCalled.get());
    }

    /**
     * A runtime that refuses the clock write used to leave a single debug line with no trace, so a world
     * stuck at night looked like a pack setting rather than a rejected reflective call.
     */
    @Test
    public void reportsTheFailureWhenTheRuntimeClockSetterThrows() throws Exception {
        IrisPlatform previousPlatform = IrisPlatforms.isBound() ? IrisPlatforms.get() : null;
        IrisPlatforms.unbind();
        IrisPlatform capturingPlatform = mock(IrisPlatform.class);
        IrisPlatforms.bind(capturingPlatform);
        try {
            World world = createWorldProxy("rejecting-clock", false, new AtomicBoolean(false), new AtomicLong(0L), true);

            assertFalse(createService().applyNoonTimeLock(world));

            ArgumentCaptor<Throwable> reported = ArgumentCaptor.forClass(Throwable.class);
            verify(capturingPlatform).reportError(contains("rejecting-clock"), reported.capture());
            assertNotNull(reported.getValue());
        } finally {
            IrisPlatforms.unbind();
            if (previousPlatform != null) {
                IrisPlatforms.bind(previousPlatform);
            }
        }
    }

    @Test
    public void appliesTimeLockWhenWorldHasMutableClock() throws Exception {
        AtomicBoolean setTimeCalled = new AtomicBoolean(false);
        AtomicLong dayTime = new AtomicLong(0L);
        World world = createWorldProxy("mutable", false, setTimeCalled, dayTime, false);

        boolean applied = createService().applyNoonTimeLock(world);

        assertTrue(applied);
        assertTrue(setTimeCalled.get());
        assertTrue(dayTime.get() == 6000L);
    }

    private WorldRuntimeControlService createService() throws Exception {
        Constructor<CapabilitySnapshot> snapshotConstructor = CapabilitySnapshot.class.getDeclaredConstructor(
                ServerFamily.class, boolean.class, Object.class, Class.class, Class.class, String.class,
                NativeWorldRuntime.class, java.lang.reflect.Method.class);
        snapshotConstructor.setAccessible(true);
        NativeWorldRuntime runtime = mock(NativeWorldRuntime.class);
        when(runtime.clock()).thenReturn(clock);
        CapabilitySnapshot snapshot = snapshotConstructor.newInstance(
                ServerFamily.PAPER, false, null, null, null, "test", runtime, null);

        Constructor<WorldRuntimeControlService> serviceConstructor = WorldRuntimeControlService.class.getDeclaredConstructor(CapabilitySnapshot.class);
        serviceConstructor.setAccessible(true);
        return serviceConstructor.newInstance(snapshot);
    }

    private World createWorldProxy(String name, boolean fixedTime, AtomicBoolean setTimeCalled,
                                   AtomicLong dayTime, boolean throwOnSetTime) throws Exception {
        World world = mock(World.class);
        when(world.getName()).thenReturn(name);
        when(world.getFullTime()).thenAnswer(invocation -> dayTime.get());
        clock = mock(NativeWorldClock.class);
        when(clock.hasMutableClock(world)).thenReturn(!fixedTime);
        when(clock.readDayTime(world)).thenAnswer(invocation -> OptionalLong.of(dayTime.get()));
        when(clock.writeDayTime(world, 6000L)).thenAnswer(invocation -> {
            setTimeCalled.set(true);
            if (throwOnSetTime) {
                throw new IllegalArgumentException("Cannot set time in world without world clock");
            }
            dayTime.set(6000L);
            return true;
        });
        return world;
    }
}
