package art.arcane.iris.purity;

import art.arcane.iris.engine.object.prefab.matter.IrisMatterObject;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * A classloader that behaves like a Fabric/Forge/NeoForge JVM: {@code org.bukkit.**} and
 * {@code io.papermc.paper.**} do not exist, no matter that paper-api sits on the test classpath.
 * <p>
 * Two properties make it a real gate rather than a decoration:
 * <ol>
 *   <li>Bukkit and Paper are refused outright - the app classloader is never consulted, so
 *       parent-first delegation cannot leak Paper in through the back door.</li>
 *   <li>Every {@code art.arcane.iris.**} class (except this test-support package) and every
 *       {@code art.arcane.volmlib.**} class is <em>defined by this loader</em> from the parent's
 *       class bytes. Definition, not delegation, is what routes all of the class's own symbol
 *       resolution - supertypes, field types, annotation values, everything the JVM links lazily -
 *       back through the filter. Delegating these to the parent would have them resolve org.bukkit
 *       happily and the gate would pass even on code that cannot load on a mod loader.</li>
 * </ol>
 * VolmLib is self-defined for the same reason Iris is: pack types hold VolmLib values ({@code
 * IrisMatterObject} holds a {@code Matter}), VolmLib's matter slicers reference a dozen Bukkit types,
 * and VolmLib decides at runtime whether to install them by probing for {@code org.bukkit.Bukkit}.
 * Delegated to the parent, that probe sees paper-api and answers "yes" - the exact opposite of what
 * happens on a mod loader, so the gate would exercise the Bukkit branch it is supposed to forbid.
 * <p>
 * Everything else (JDK, gson, fastutil, ...) delegates to the parent normally; the parent is also
 * used purely as a byte source for the classes this loader defines itself.
 */
public final class BukkitHidingClassLoader extends ClassLoader {
    private static final String[] HIDDEN_PREFIXES = {"org.bukkit.", "io.papermc.paper."};
    private static final String[] SELF_DEFINE_PREFIXES = {"art.arcane.iris.", "art.arcane.volmlib."};
    private static final String TEST_SUPPORT_PREFIX = "art.arcane.iris.purity.";

    public BukkitHidingClassLoader(ClassLoader parent) {
        super("bukkit-hiding", parent);
    }

    @Override
    protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
        if (isHidden(name)) {
            throw new ClassNotFoundException("hidden by the Bukkit purity gate: " + name);
        }

        synchronized (getClassLoadingLock(name)) {
            Class<?> loaded = findLoadedClass(name);
            if (loaded == null) {
                loaded = shouldSelfDefine(name) ? define(name) : getParent().loadClass(name);
            }
            if (resolve) {
                resolveClass(loaded);
            }
            return loaded;
        }
    }

    private boolean isHidden(String name) {
        if (name.equals("org.bukkit.Bukkit")) {
            return true;
        }
        for (String prefix : HIDDEN_PREFIXES) {
            if (name.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    private boolean shouldSelfDefine(String name) {
        if (name.startsWith(TEST_SUPPORT_PREFIX)) {
            return false;
        }
        for (String prefix : SELF_DEFINE_PREFIXES) {
            if (name.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    private Class<?> define(String name) throws ClassNotFoundException {
        byte[] bytes = readClassBytes(getParent(), name);
        if (bytes == null) {
            throw new ClassNotFoundException(name);
        }
        return defineClass(name, bytes, 0, bytes.length);
    }

    /** Reads the raw class file for {@code name} off {@code source}'s resource path. */
    public static byte[] readClassBytes(ClassLoader source, String name) {
        String resource = name.replace('.', '/') + ".class";
        try (InputStream in = source.getResourceAsStream(resource)) {
            if (in == null) {
                return null;
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream(16384);
            in.transferTo(out);
            return out.toByteArray();
        } catch (IOException e) {
            return null;
        }
    }
}
