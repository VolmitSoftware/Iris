package art.arcane.iris.platform.agent;

import org.junit.Test;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.util.Locale;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeFalse;

public class AttachSignalTest {
    private static final int SIGQUIT = 3;
    private static final int SIGTERM = 15;
    private static final long ACTION_BYTES = 256;
    private static final MethodHandle SIGACTION = Linker.nativeLinker().downcallHandle(
            Linker.nativeLinker().defaultLookup().find("sigaction").orElseThrow(),
            FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS));

    @Test
    public void aConsoleThatResetsQuitGetsTheJvmHandlerBack() throws Throwable {
        assumeFalse(System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("win"));
        long jvmHandler = handler(SIGTERM);
        assertNotEquals(0L, jvmHandler);
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment original = arena.allocate(ACTION_BYTES);
            assertEquals(0, (int) SIGACTION.invokeExact(SIGQUIT, MemorySegment.NULL, original));
            try {
                MemorySegment reset = arena.allocate(ACTION_BYTES);
                assertEquals(0, (int) SIGACTION.invokeExact(SIGQUIT, reset, MemorySegment.NULL));
                assertEquals(0L, handler(SIGQUIT));

                assertTrue(AttachSignal.restoreJvmHandler());

                assertEquals(jvmHandler, handler(SIGQUIT));
                assertFalse(AttachSignal.restoreJvmHandler());
            } finally {
                assertEquals(0, (int) SIGACTION.invokeExact(SIGQUIT, original, MemorySegment.NULL));
            }
        }
    }

    private static long handler(int signal) throws Throwable {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment action = arena.allocate(ACTION_BYTES);
            assertEquals(0, (int) SIGACTION.invokeExact(signal, MemorySegment.NULL, action));
            return action.get(ValueLayout.JAVA_LONG, 0);
        }
    }
}
