package art.arcane.iris.world.runtime;

import art.arcane.iris.world.lifecycle.CapabilitySnapshot;
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
import java.lang.reflect.Proxy;
import java.util.OptionalLong;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

public class WorldRuntimeControlServiceTimeLockTest {
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
                ServerFamily.class,
                boolean.class,
                Object.class,
                Class.class,
                Class.class,
                String.class,
                Object.class,
                Object.class,
                java.lang.reflect.Method.class,
                CapabilitySnapshot.PaperLikeFlavor.class,
                Class.class,
                java.lang.reflect.Method.class,
                java.lang.reflect.Constructor.class,
                java.lang.reflect.Constructor.class,
                java.lang.reflect.Method.class,
                java.lang.reflect.Method.class,
                java.lang.reflect.Field.class,
                java.lang.reflect.Method.class,
                java.lang.reflect.Field.class,
                java.lang.reflect.Field.class,
                java.lang.reflect.Method.class,
                java.lang.reflect.Method.class,
                java.lang.reflect.Method.class,
                java.lang.reflect.Method.class,
                String.class
        );
        snapshotConstructor.setAccessible(true);
        CapabilitySnapshot snapshot = snapshotConstructor.newInstance(
                ServerFamily.PAPER,
                false,
                null,
                null,
                null,
                "test",
                Bukkit.getServer(),
                null,
                null,
                CapabilitySnapshot.PaperLikeFlavor.UNSUPPORTED,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                "test"
        );

        Constructor<WorldRuntimeControlService> serviceConstructor = WorldRuntimeControlService.class.getDeclaredConstructor(CapabilitySnapshot.class);
        serviceConstructor.setAccessible(true);
        return serviceConstructor.newInstance(snapshot);
    }

    private World createWorldProxy(String name, boolean fixedTime, AtomicBoolean setTimeCalled, AtomicLong dayTime, boolean throwOnSetTime) {
        Object dimensionType = Proxy.newProxyInstance(
                World.class.getClassLoader(),
                new Class[]{DimensionTypeProbe.class},
                (proxy, method, args) -> {
                    if ("hasFixedTime".equals(method.getName())) {
                        return fixedTime;
                    }
                    if ("fixedTime".equals(method.getName())) {
                        return fixedTime ? OptionalLong.of(6000L) : OptionalLong.empty();
                    }
                    return null;
                }
        );
        Object holder = Proxy.newProxyInstance(
                World.class.getClassLoader(),
                new Class[]{HolderProbe.class},
                (proxy, method, args) -> {
                    if ("value".equals(method.getName())) {
                        return dimensionType;
                    }
                    return null;
                }
        );
        Object handle = Proxy.newProxyInstance(
                World.class.getClassLoader(),
                new Class[]{HandleProbe.class},
                (proxy, method, args) -> {
                    if ("dimensionTypeRegistration".equals(method.getName())) {
                        return holder;
                    }
                    if ("getDayTime".equals(method.getName())) {
                        return dayTime.get();
                    }
                    if ("setDayTime".equals(method.getName())) {
                        setTimeCalled.set(true);
                        if (throwOnSetTime) {
                            throw new IllegalArgumentException("Cannot set time in world without world clock");
                        }
                        dayTime.set(((Long) args[0]).longValue());
                        return null;
                    }
                    return null;
                }
        );
        return (World) Proxy.newProxyInstance(
                World.class.getClassLoader(),
                new Class[]{World.class, WorldHandleProbe.class},
                (proxy, method, args) -> {
                    if ("getName".equals(method.getName())) {
                        return name;
                    }
                    if ("getHandle".equals(method.getName())) {
                        return handle;
                    }
                    if ("getFullTime".equals(method.getName())) {
                        return dayTime.get();
                    }

                    Class<?> returnType = method.getReturnType();
                    if (boolean.class.equals(returnType)) {
                        return false;
                    }
                    if (int.class.equals(returnType)) {
                        return 0;
                    }
                    if (long.class.equals(returnType)) {
                        return 0L;
                    }
                    if (float.class.equals(returnType)) {
                        return 0F;
                    }
                    if (double.class.equals(returnType)) {
                        return 0D;
                    }

                    return null;
                }
        );
    }

    private interface WorldHandleProbe {
        Object getHandle();
    }

    private interface HandleProbe {
        Object dimensionTypeRegistration();

        long getDayTime();

        void setDayTime(long time);
    }

    private interface HolderProbe {
        Object value();
    }

    private interface DimensionTypeProbe {
        boolean hasFixedTime();

        OptionalLong fixedTime();
    }
}
