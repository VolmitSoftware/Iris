package com.volmit.host;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Objects;

/** The class loader of one {@link ToolingPart}, a child of its pack's class loader. Open it with
 * {@link PackModules#openToolingPart}, close it before the pack closes: closing it never closes the pack's loader.
 *
 * <p>Where a class comes from:
 * <ul>
 * <li>The part's own packages: defined by this loader from the same jar copy as the pack's classes, child first, so the
 *     part sees the platform and the pack's loader never defines it.
 * <li>A denied package of the platform, unless the host exposes the class to modules: refused with a
 *     {@link ClassNotFoundException}.
 * <li>The platform's api packages: the platform's loader.
 * <li>Everything else: the pack's loader, which gives the very {@code Class} objects the pack's modules registered, so a part
 *     can cast what the pack returns, such as a resource of a type the jar defines.
 * </ul>
 */
public final class ToolingPartLoader implements AutoCloseable {
    private final ToolingPart part;
    private final PartClassLoader loader;

    private ToolingPartLoader(ToolingPart part, PartClassLoader loader) {
        this.part = part;
        this.loader = loader;
    }

    static ToolingPartLoader open(ToolingPart part, ClassLoader packLoader, ExposedPackages exposed, PlatformApi platformApi) {
        Objects.requireNonNull(part, "part");
        var view = new PlatformView(part, exposed, platformApi);
        return new ToolingPartLoader(part, new PartClassLoader(urlOf(part), packLoader, view));
    }

    /** Loads the part's entry class without initializing it, defined by this loader.
     *
     * @throws ModuleLoadException naming the jar when the class cannot be loaded
     */
    public Class<?> loadEntryClass() {
        try {
            return Class.forName(part.entryClass(), false, loader);
        } catch (ClassNotFoundException | LinkageError exception) {
            throw new ModuleLoadException(null, part.jarOrigin(), "Module jar " + part.jarOrigin() + " has a tooling part "
                    + part.declaration() + " whose entry class cannot be loaded: " + exception, exception);
        }
    }

    /** Closes this loader and the jar file it opened; the pack's loader and the platform's stay open. */
    @Override
    public void close() {
        try {
            loader.close();
        } catch (IOException exception) {
            throw new UncheckedIOException("Cannot close tooling part class loader " + loader.getName(), exception);
        }
    }

    private static URL urlOf(ToolingPart part) {
        try {
            return part.jar().toUri().toURL();
        } catch (MalformedURLException exception) {
            throw new ModuleLoadException(null, part.jarOrigin(), "Module jar " + part.jarOrigin() + " has no URL form", exception);
        }
    }

    /** What decides which loader a class name goes to. */
    private record PlatformView(ToolingPart part, ExposedPackages exposed, PlatformApi platformApi) {
        boolean isOwnClass(String name) {
            return part.contains(name);
        }

        boolean isDenied(String name) {
            return platformApi.platform().deniesPackageOf(name) && !exposed.exposes(name);
        }

        boolean isPlatformApi(String name) {
            return platformApi.platform().hasApiPackageOf(name);
        }
    }

    private static final class PartClassLoader extends URLClassLoader {
        static {
            registerAsParallelCapable();
        }

        private final PlatformView view;

        PartClassLoader(URL jar, ClassLoader packLoader, PlatformView view) {
            super("tooling-part:" + view.part().declaration(), new URL[] {jar}, packLoader);
            this.view = view;
        }

        @Override
        protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            synchronized (getClassLoadingLock(name)) {
                var type = findLoadedClass(name);
                if (type == null) type = loadFromWhereItBelongs(name);
                if (resolve) resolveClass(type);
                return type;
            }
        }

        private Class<?> loadFromWhereItBelongs(String name) throws ClassNotFoundException {
            if (view.isOwnClass(name)) return findClass(name);
            if (view.isDenied(name)) {
                throw new ClassNotFoundException(name + " is not visible to a " + view.platformApi().platform().name() + " tooling part");
            }
            if (view.isPlatformApi(name)) return view.platformApi().classes().loadClass(name);
            return getParent().loadClass(name);
        }
    }
}
