package art.arcane.iris.platform.agent;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.util.Locale;

public final class AttachSignal {
    private static final int SIGQUIT = 3;
    private static final int SIGTERM = 15;
    private static final long ACTION_BYTES = 256;
    private static final long SIG_IGN = 1L;

    private AttachSignal() {
    }

    public static boolean restoreJvmHandler() {
        if (System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win")) {
            return false;
        }
        Linker linker = Linker.nativeLinker();
        MethodHandle sigaction = linker.downcallHandle(
                linker.defaultLookup().find("sigaction")
                        .orElseThrow(() -> new IllegalStateException("sigaction is unavailable")),
                FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS));
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment jvm = arena.allocate(ACTION_BYTES);
            MemorySegment quit = arena.allocate(ACTION_BYTES);
            if ((int) sigaction.invokeExact(SIGTERM, MemorySegment.NULL, jvm) != 0
                    || (int) sigaction.invokeExact(SIGQUIT, MemorySegment.NULL, quit) != 0) {
                throw new IllegalStateException("sigaction could not read the SIGTERM and SIGQUIT handlers");
            }
            long handler = jvm.get(ValueLayout.JAVA_LONG, 0);
            if (handler == 0L || handler == SIG_IGN || quit.get(ValueLayout.JAVA_LONG, 0) == handler) {
                return false;
            }
            if ((int) sigaction.invokeExact(SIGQUIT, jvm, MemorySegment.NULL) != 0) {
                throw new IllegalStateException("sigaction could not restore the JVM SIGQUIT handler");
            }
            return true;
        } catch (RuntimeException | Error failure) {
            throw failure;
        } catch (Throwable failure) {
            throw new IllegalStateException("Unable to restore the JVM SIGQUIT handler", failure);
        }
    }
}
