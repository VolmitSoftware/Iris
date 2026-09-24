package art.arcane.iris.platform.bootstrap;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.tukaani.xz.LZMA2Options;
import org.tukaani.xz.XZOutputStream;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;
import java.util.stream.Stream;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertSame;

public class PackedRuntimeTest {
    @Rule
    public TemporaryFolder directory = new TemporaryFolder();

    @Test
    public void extractsAndVerifiesTheExactPayload() throws Exception {
        byte[] content = "Iris runtime contents".getBytes(StandardCharsets.UTF_8);
        String hash = hash(content);
        Path target = directory.getRoot().toPath().resolve("runtime/" + hash + ".jar");
        PackedRuntime.extract(new ByteArrayInputStream(compress(content)), target, hash);
        PackedRuntime.verify(target, hash);
        assertArrayEquals(content, Files.readAllBytes(target));
    }

    @Test
    public void rejectsAnIncorrectHashWithoutPublishingOrLeavingTemporaryFiles() throws Exception {
        byte[] content = "Iris runtime contents".getBytes(StandardCharsets.UTF_8);
        Path target = directory.getRoot().toPath().resolve("runtime/runtime.jar");
        assertThrows(IOException.class, () -> PackedRuntime.extract(
                new ByteArrayInputStream(compress(content)), target, "0".repeat(64)));
        assertFalse(Files.exists(target));
        try (Stream<Path> files = Files.list(target.getParent())) {
            assertEquals(0L, files.count());
        }
    }

    @Test
    public void rejectsTruncatedCompressedData() throws Exception {
        byte[] content = "Iris runtime contents".getBytes(StandardCharsets.UTF_8);
        byte[] compressed = compress(content);
        Path target = directory.getRoot().toPath().resolve("runtime/runtime.jar");
        assertThrows(IOException.class, () -> PackedRuntime.extract(
                new ByteArrayInputStream(compressed, 0, compressed.length / 2), target, hash(content)));
        assertFalse(Files.exists(target));
    }

    @Test
    public void detectsModifiedCachedRuntime() throws Exception {
        byte[] content = "Iris runtime contents".getBytes(StandardCharsets.UTF_8);
        Path target = directory.newFile("runtime.jar").toPath();
        Files.writeString(target, "changed");
        assertThrows(IOException.class, () -> PackedRuntime.verify(target, hash(content)));
    }

    @Test
    public void nativeArchiveReplacementKeepsClassIdentityAndClosesOwnedHandles() throws Exception {
        Path outer = createArchive("outer.jar", false);
        Path payload = createArchive("payload.jar", true);
        JarFile original = new JarFile(outer.toFile());
        PackedRuntime.RuntimeArchive replacement;
        try (ArchiveLoader loader = new ArchiveLoader(original)) {
            assertThrows(ClassNotFoundException.class, () -> loader.loadClass(Fixture.class.getName()));
            replacement = PackedRuntime.replaceArchive(loader, ArchiveLoader.class.getDeclaredField("jar"), payload, true);
            Class<?> loaded = loader.loadClass(Fixture.class.getName());
            assertSame(loader, loaded.getClassLoader());
            assertEquals("runtime", loaded.getMethod("value").invoke(null));
            assertEquals(1, loader.definitions);
        }
        assertThrows(IllegalStateException.class, original::size);
        assertThrows(IllegalStateException.class, replacement::size);
    }

    @Test
    public void temporaryArchiveCloseLeavesTheSharedOriginalOpen() throws Exception {
        Path outer = createArchive("outer.jar", false);
        Path payload = createArchive("payload.jar", true);
        try (JarFile original = new JarFile(outer.toFile());
             ArchiveLoader loader = new ArchiveLoader(original)) {
            PackedRuntime.RuntimeArchive replacement = PackedRuntime.replaceArchive(
                    loader, ArchiveLoader.class.getDeclaredField("jar"), payload, false);
            replacement.close();
            assertEquals(0, original.size());
            assertThrows(IllegalStateException.class, replacement::size);
        }
    }

    private Path createArchive(String name, boolean withFixture) throws IOException {
        Path path = directory.getRoot().toPath().resolve(name);
        try (JarOutputStream output = new JarOutputStream(Files.newOutputStream(path))) {
            if (withFixture) {
                String entry = Fixture.class.getName().replace('.', '/') + ".class";
                output.putNextEntry(new JarEntry(entry));
                try (InputStream bytes = getClass().getClassLoader().getResourceAsStream(entry)) {
                    bytes.transferTo(output);
                }
                output.closeEntry();
            }
        }
        return path;
    }

    private static byte[] compress(byte[] content) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (XZOutputStream output = new XZOutputStream(bytes, new LZMA2Options(1))) {
            output.write(content);
        }
        return bytes.toByteArray();
    }

    private static String hash(byte[] content) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
    }

    public static class Fixture {
        public static String value() {
            return "runtime";
        }
    }

    private static final class ArchiveLoader extends URLClassLoader {
        private final JarFile jar;
        private int definitions;

        private ArchiveLoader(JarFile jar) {
            super(new URL[0], ClassLoader.getPlatformClassLoader());
            this.jar = jar;
        }

        @Override
        protected Class<?> findClass(String name) throws ClassNotFoundException {
            JarEntry entry = jar.getJarEntry(name.replace('.', '/') + ".class");
            if (entry == null) {
                throw new ClassNotFoundException(name);
            }
            try (InputStream input = jar.getInputStream(entry)) {
                byte[] bytes = input.readAllBytes();
                definitions++;
                return defineClass(name, bytes, 0, bytes.length);
            } catch (IOException failure) {
                throw new ClassNotFoundException(name, failure);
            }
        }

        @Override
        public void close() throws IOException {
            try (jar) {
                super.close();
            }
        }
    }
}
