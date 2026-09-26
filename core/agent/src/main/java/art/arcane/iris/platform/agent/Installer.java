package art.arcane.iris.platform.agent;

import java.lang.instrument.Instrumentation;
import java.io.Closeable;
import java.io.IOException;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

public class Installer {
    private static volatile Instrumentation instrumentation;
    private static final Map<ClassLoader, Boolean> retainedClassLoaders = new IdentityHashMap<>();
    private static final Map<Object, AtomicBoolean> serverStorage = new IdentityHashMap<>();

    public static synchronized AtomicBoolean trackServerStorage(Object storage) {
        return serverStorage.computeIfAbsent(Objects.requireNonNull(storage, "Server storage"),
                ignored -> new AtomicBoolean());
    }

    public static synchronized void serverStorageClosed(Object storage) {
        AtomicBoolean closed = serverStorage.get(storage);
        if (closed != null) {
            closed.set(true);
        }
    }

    public static synchronized void releaseServerStorage(Object storage) {
        serverStorage.remove(storage);
    }

    public static synchronized void retainClassLoader(ClassLoader loader) {
        ClassLoader requiredLoader = Objects.requireNonNull(loader, "Plugin class loader");
        if (!(requiredLoader instanceof Closeable)) {
            throw new IllegalArgumentException("Plugin class loader is not closeable");
        }
        retainedClassLoaders.putIfAbsent(requiredLoader, false);
    }

    public static synchronized boolean deferClassLoaderClose(ClassLoader loader) {
        if (!retainedClassLoaders.containsKey(loader)) {
            return false;
        }
        retainedClassLoaders.put(loader, true);
        return true;
    }

    public static void releaseClassLoader(ClassLoader loader) throws IOException {
        boolean closeRequested;
        synchronized (Installer.class) {
            closeRequested = Boolean.TRUE.equals(retainedClassLoaders.remove(loader));
        }
        if (closeRequested) {
            ((Closeable) loader).close();
        }
    }

    public static Instrumentation getInstrumentation() {
        Instrumentation instrumentation = Installer.instrumentation;
        if (instrumentation == null) {
            throw new IllegalStateException("The agent is not loaded or this method is not called via the system class loader");
        }
        return instrumentation;
    }

    public static void premain(String arguments, Instrumentation instrumentation) {
        doMain(instrumentation);
    }

    public static void agentmain(String arguments, Instrumentation instrumentation) {
        doMain(instrumentation);
    }

    private static synchronized void doMain(Instrumentation instrumentation) {
        if (Installer.instrumentation != null)
            return;
        Installer.instrumentation = instrumentation;
    }
}
