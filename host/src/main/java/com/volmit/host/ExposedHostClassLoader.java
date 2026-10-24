package com.volmit.host;

import java.io.IOException;
import java.net.URL;
import java.util.Enumeration;
import java.util.Objects;

/** Parent of every pack class loader. It shows a pack the exposed host packages and the JDK and
 * nothing else: classes in an {@link ExposedPackages exposed package}, always including the Iris
 * API, come from the host's loader, every other class from the platform class loader. Iris
 * internals and the libraries Iris bundles stay hidden even though they sit in the host's loader,
 * so a module can use only the exposed packages, the JDK and the classes of its own pack's jars.
 *
 * <p>Resources follow the same rule, by path: {@code com/volmit/...} and the paths of other
 * exposed packages from the host's loader, everything else from the platform loader. A class and
 * its class file therefore always come from the same place, and a pack never sees Iris's own
 * resources, such as its service files.
 *
 * <p>This loader defines no classes itself.
 */
final class ExposedHostClassLoader extends ClassLoader {
    private static final String CLASS_FILE_SUFFIX = ".class";
    private static final String NAME = "iris-exposed-host-and-jdk";

    static {
        registerAsParallelCapable();
    }

    private final ClassLoader hostLoader;
    private final ExposedPackages exposed;
    private final HostClassesLeftOut leftOut;

    /** {@code hostLoader} is the loader that defines the API; in production the Iris plugin's. */
    ExposedHostClassLoader(ClassLoader hostLoader, ExposedPackages exposed) {
        this(hostLoader, exposed, HostClassesLeftOut.none());
    }

    /** For a build that checks a jar against more classes than the real host has: {@code leftOut} names the
     * classes the real host's loader lacks, which {@link #hasClass} then denies and {@link #leavesOut} reports.
     */
    ExposedHostClassLoader(ClassLoader hostLoader, ExposedPackages exposed, HostClassesLeftOut leftOut) {
        super(NAME, ClassLoader.getPlatformClassLoader());
        this.hostLoader = Objects.requireNonNull(hostLoader, "hostLoader");
        this.exposed = Objects.requireNonNull(exposed, "exposed");
        this.leftOut = Objects.requireNonNull(leftOut, "leftOut");
    }

    ExposedPackages exposed() {
        return exposed;
    }

    /** Whether this loader can load the class, judged by its class file so that nothing is loaded. */
    boolean hasClass(String binaryName) {
        return !leavesOut(binaryName) && getResource(classFile(binaryName)) != null;
    }

    /** Whether the real host lacks the class although the loader a build checks against has it. */
    boolean leavesOut(String binaryName) {
        return leftOut.leavesOut(binaryName);
    }

    /** Whether the host's loader has the class, whether or not this loader shows it to packs. In
     * production that loader also holds Iris internals and the libraries Iris bundles.
     */
    boolean hostHasClass(String binaryName) {
        return hostLoader.getResource(classFile(binaryName)) != null;
    }

    @Override
    protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
        if (exposed.exposes(name)) return hostLoader.loadClass(name);
        return super.loadClass(name, resolve);
    }

    @Override
    public URL getResource(String name) {
        if (exposed.exposesResource(name)) return hostLoader.getResource(name);
        return super.getResource(name);
    }

    @Override
    public Enumeration<URL> getResources(String name) throws IOException {
        if (exposed.exposesResource(name)) return hostLoader.getResources(name);
        return super.getResources(name);
    }

    private static String classFile(String binaryName) {
        return binaryName.replace('.', '/') + CLASS_FILE_SUFFIX;
    }
}
