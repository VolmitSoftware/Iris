package art.arcane.iris.platform.bukkit;

import art.arcane.iris.spi.PlatformBiome;
import com.github.benmanes.caffeine.cache.Cache;
import org.bukkit.NamespacedKey;
import org.junit.Before;
import org.junit.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public class BukkitBiomeTest {
    private Class<?> biomeType;
    private Method wrap;

    @Before
    public void loadAdapterWithoutServerRegistryInitialization() throws Exception {
        ClassLoader loader = new BiomeClassLoader();
        biomeType = loader.loadClass("org.bukkit.block.Biome");
        wrap = loader.loadClass(BukkitBiome.class.getName()).getMethod("of", biomeType);
    }

    @Test
    public void cachedHandlesKeepCanonicalIdentityWithoutReadingTheKeyAgain() throws Exception {
        AtomicInteger keyReads = new AtomicInteger();
        Object biome = biome("iris", "riverbank", keyReads);

        PlatformBiome wrapped = wrap(biome);
        assertEquals("iris:riverbank", wrapped.key());
        assertEquals("iris", wrapped.namespace());
        assertSame(biome, wrapped.nativeHandle());
        assertSame(wrapped, wrap(biome));
        assertSame(wrapped, wrap(biome));
        assertEquals(1, keyReads.get());
    }

    @Test
    public void replacementRegistryHandleCannotReturnThePreviousNativeBiome() throws Exception {
        Object previous = biome("iris", "registry_replacement", new AtomicInteger());
        Object replacement = biome("iris", "registry_replacement", new AtomicInteger());

        PlatformBiome oldHandle = wrap(previous);
        PlatformBiome newHandle = wrap(replacement);

        assertNotSame(oldHandle, newHandle);
        assertEquals(oldHandle.key(), newHandle.key());
        assertSame(previous, oldHandle.nativeHandle());
        assertSame(replacement, newHandle.nativeHandle());
        assertSame(newHandle, wrap(replacement));
    }

    @Test
    public void customAndVanillaNamespacesRemainDistinct() throws Exception {
        PlatformBiome vanilla = wrap(biome("minecraft", "plains", new AtomicInteger()));
        PlatformBiome custom = wrap(biome("iris", "plains", new AtomicInteger()));

        assertEquals("minecraft:plains", vanilla.key());
        assertEquals("minecraft", vanilla.namespace());
        assertEquals("iris:plains", custom.key());
        assertNotSame(vanilla, custom);
    }

    @Test
    public void cacheEvictionPreservesBiomeKeysAndNativeHandles() throws Exception {
        Object retained = biome("iris", "retained", new AtomicInteger());
        PlatformBiome original = wrap(retained);
        for (int index = 0; index < 8_192; index++) {
            wrap(biome("iris", "eviction_" + index, new AtomicInteger()));
        }
        Field field = wrap.getDeclaringClass().getDeclaredField("CACHE");
        field.setAccessible(true);
        Cache<?, ?> cache = (Cache<?, ?>) field.get(null);
        cache.cleanUp();

        assertTrue(cache.estimatedSize() <= 4_096);
        PlatformBiome restored = wrap(retained);
        assertEquals(original.key(), restored.key());
        assertEquals(original.namespace(), restored.namespace());
        assertSame(retained, restored.nativeHandle());
    }

    private PlatformBiome wrap(Object biome) throws Exception {
        return (PlatformBiome) wrap.invoke(null, biome);
    }

    private Object biome(String namespace, String name, AtomicInteger keyReads) {
        NamespacedKey key = new NamespacedKey(namespace, name);
        return Proxy.newProxyInstance(biomeType.getClassLoader(), new Class<?>[]{biomeType}, (proxy, method, arguments) -> switch (method.getName()) {
            case "getKey" -> {
                keyReads.incrementAndGet();
                yield key;
            }
            case "hashCode" -> System.identityHashCode(proxy);
            case "equals" -> proxy == arguments[0];
            case "toString" -> key.toString();
            default -> throw new UnsupportedOperationException(method.getName());
        });
    }

    private static final class BiomeClassLoader extends ClassLoader {
        private BiomeClassLoader() {
            super(BukkitBiomeTest.class.getClassLoader());
        }

        @Override
        protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            if (!name.equals("org.bukkit.block.Biome") && !name.equals(BukkitBiome.class.getName())) {
                return super.loadClass(name, resolve);
            }
            synchronized (getClassLoadingLock(name)) {
                Class<?> loaded = findLoadedClass(name);
                if (loaded == null) {
                    try (InputStream source = getParent().getResourceAsStream(name.replace('.', '/') + ".class")) {
                        if (source == null) {
                            throw new ClassNotFoundException(name);
                        }
                        byte[] bytecode = source.readAllBytes();
                        if (name.equals("org.bukkit.block.Biome")) {
                            ClassWriter writer = new ClassWriter(0);
                            new ClassReader(bytecode).accept(new ClassVisitor(Opcodes.ASM9, writer) {
                                @Override
                                public MethodVisitor visitMethod(int access, String method, String descriptor, String signature, String[] exceptions) {
                                    return method.equals("<clinit>") ? null : super.visitMethod(access, method, descriptor, signature, exceptions);
                                }
                            }, 0);
                            bytecode = writer.toByteArray();
                        }
                        loaded = defineClass(name, bytecode, 0, bytecode.length);
                    } catch (IOException exception) {
                        throw new ClassNotFoundException(name, exception);
                    }
                }
                if (resolve) {
                    resolveClass(loaded);
                }
                return loaded;
            }
        }
    }
}
