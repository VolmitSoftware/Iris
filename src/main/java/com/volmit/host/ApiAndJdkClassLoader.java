package com.volmit.host;

import java.io.IOException;
import java.net.URL;
import java.util.Enumeration;
import java.util.Objects;

/** Parent of every pack class loader. It shows a pack the Iris API and the JDK and nothing else:
 * API classes come from the loader that defines the API, every other class from the platform class
 * loader. Iris internals and the libraries Iris bundles stay hidden even when they sit in the API's
 * loader, so a module can use only the API, the JDK and the classes of its own pack's jars.
 *
 * <p>Resources follow the same rule, by path: {@code com/volmit/...} from the API's loader,
 * everything else from the platform loader. A class and its class file therefore always come from
 * the same place, and a pack never sees Iris's own resources, such as its service files.
 *
 * <p>This loader defines no classes itself.
 */
final class ApiAndJdkClassLoader extends ClassLoader {
    /** Every class in a package with this prefix belongs to the Iris API. Module classes must not use it. */
    static final String API_PACKAGE_PREFIX = "com.volmit.";
    private static final String API_RESOURCE_PREFIX = API_PACKAGE_PREFIX.replace('.', '/');
    private static final String CLASS_FILE_SUFFIX = ".class";
    private static final String NAME = "iris-api-and-jdk";

    static {
        registerAsParallelCapable();
    }

    private final ClassLoader apiLoader;

    ApiAndJdkClassLoader(ClassLoader apiLoader) {
        super(NAME, ClassLoader.getPlatformClassLoader());
        this.apiLoader = Objects.requireNonNull(apiLoader, "apiLoader");
    }

    static boolean isApiClass(String binaryName) {
        return binaryName.startsWith(API_PACKAGE_PREFIX);
    }

    /** Whether this loader can load the class, judged by its class file so that nothing is loaded. */
    boolean hasClass(String binaryName) {
        return getResource(binaryName.replace('.', '/') + CLASS_FILE_SUFFIX) != null;
    }

    @Override
    protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
        if (isApiClass(name)) return apiLoader.loadClass(name);
        return super.loadClass(name, resolve);
    }

    @Override
    public URL getResource(String name) {
        if (isApiResource(name)) return apiLoader.getResource(name);
        return super.getResource(name);
    }

    @Override
    public Enumeration<URL> getResources(String name) throws IOException {
        if (isApiResource(name)) return apiLoader.getResources(name);
        return super.getResources(name);
    }

    private static boolean isApiResource(String name) {
        return name.startsWith(API_RESOURCE_PREFIX);
    }
}
