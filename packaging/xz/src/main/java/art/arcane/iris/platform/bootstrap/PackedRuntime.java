package art.arcane.iris.platform.bootstrap;

import art.arcane.iris.util.slimjar.injector.loader.factory.InjectableFactory;
import org.tukaani.xz.XZInputStream;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Field;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.jar.JarFile;
import java.util.logging.Logger;

public final class PackedRuntime {
    private static final long MAXIMUM_BYTES = 256L * 1024 * 1024;
    private static final int DECODER_MEMORY_KIB = 128 * 1024;
    private static Path runtime;
    private static RuntimeArchive archive;
    private static Field archiveField;

    private PackedRuntime() {
    }

    public static synchronized void install() {
        if (runtime != null) {
            return;
        }
        try {
            Path pluginJar = Path.of(PackedRuntime.class.getProtectionDomain().getCodeSource().getLocation().toURI());
            Path cache = pluginJar.getParent().resolve("Iris/cache/runtime");
            String hash;
            try (InputStream input = resource("runtime.sha256")) {
                hash = new String(input.readNBytes(128), StandardCharsets.US_ASCII).strip();
            }
            if (!hash.matches("[0-9a-f]{64}")) {
                throw new IOException("Invalid embedded runtime checksum");
            }
            Path payload = cache.resolve(hash + ".jar");
            if (Files.exists(payload)) {
                verify(payload, hash);
            } else {
                try (InputStream input = resource("runtime.jar.xz")) {
                    extract(input, payload, hash);
                }
            }
            InjectableFactory.UNSAFE.create(cache, List.of(), PackedRuntime.class.getClassLoader())
                    .inject(payload.toUri().toURL());
            ClassLoader loader = PackedRuntime.class.getClassLoader();
            archiveField = archiveOwner(loader).getDeclaredField("jar");
            archive = replaceArchive(loader, archiveField, payload, !temporaryLoader(loader));
            runtime = payload;
            Logger.getLogger("Iris").info("Loaded embedded XZ runtime: " + payload.getFileName());
        } catch (IOException | URISyntaxException | ReflectiveOperationException failure) {
            throw new IllegalStateException("Unable to load the embedded Iris runtime", failure);
        }
    }

    public static synchronized void releaseTemporary(Throwable failure) {
        ClassLoader loader = PackedRuntime.class.getClassLoader();
        if (!temporaryLoader(loader) || archive == null) {
            return;
        }
        RuntimeArchive released = archive;
        archive = null;
        try (released) {
            archiveField.set(loader, released.original);
        } catch (IOException | IllegalAccessException cleanupFailure) {
            if (failure != null) {
                failure.addSuppressed(cleanupFailure);
            } else {
                throw new IllegalStateException("Unable to close the Iris runtime loader archive", cleanupFailure);
            }
        }
    }

    public static File runtimeJar() {
        install();
        return runtime.toFile();
    }

    static RuntimeArchive replaceArchive(ClassLoader loader, Field field, Path payload, boolean closeOriginal)
            throws IOException, IllegalAccessException {
        if (field.getType() != JarFile.class || !field.getDeclaringClass().isInstance(loader)) {
            throw new IllegalArgumentException("Unsupported plugin archive field: " + field);
        }
        field.setAccessible(true);
        JarFile original = (JarFile) field.get(loader);
        RuntimeArchive replacement = new RuntimeArchive(new ArchiveOptions(payload, original, closeOriginal));
        try {
            field.set(loader, replacement);
        } catch (IllegalAccessException | RuntimeException failure) {
            try {
                replacement.closePayload();
            } catch (IOException closeFailure) {
                failure.addSuppressed(closeFailure);
            }
            throw failure;
        }
        return replacement;
    }

    static void extract(InputStream compressed, Path target, String expectedHash) throws IOException {
        Files.createDirectories(target.getParent());
        Path temporary = Files.createTempFile(target.getParent(), ".runtime-", ".jar");
        try {
            MessageDigest digest = digest();
            try (InputStream input = new DigestInputStream(new XZInputStream(compressed, DECODER_MEMORY_KIB), digest);
                 OutputStream output = Files.newOutputStream(temporary)) {
                byte[] buffer = new byte[65536];
                long size = 0;
                int count;
                while ((count = input.read(buffer)) != -1) {
                    size += count;
                    if (size > MAXIMUM_BYTES) {
                        throw new IOException("Embedded Iris runtime exceeds the size limit");
                    }
                    output.write(buffer, 0, count);
                }
            }
            requireHash(digest.digest(), expectedHash);
            Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    static void verify(Path payload, String expectedHash) throws IOException {
        if (Files.size(payload) > MAXIMUM_BYTES) {
            throw new IOException("Embedded Iris runtime exceeds the size limit");
        }
        MessageDigest digest = digest();
        try (InputStream input = new DigestInputStream(Files.newInputStream(payload), digest)) {
            input.transferTo(OutputStream.nullOutputStream());
        }
        requireHash(digest.digest(), expectedHash);
    }

    private static InputStream resource(String name) throws IOException {
        InputStream input = PackedRuntime.class.getResourceAsStream("/META-INF/iris/" + name);
        if (input == null) {
            throw new IOException("Missing embedded Iris runtime resource: " + name);
        }
        return input;
    }

    private static Class<?> archiveOwner(ClassLoader loader) {
        for (Class<?> type = loader.getClass(); type != null; type = type.getSuperclass()) {
            if (type.getName().equals("org.bukkit.plugin.java.PluginClassLoader")
                    || type.getName().equals("io.papermc.paper.plugin.entrypoint.classloader.PaperSimplePluginClassLoader")) {
                return type;
            }
        }
        throw new IllegalStateException("Unsupported Iris plugin classloader: " + loader.getClass().getName());
    }

    private static boolean temporaryLoader(ClassLoader loader) {
        return loader.getClass().getName()
                .equals("io.papermc.paper.plugin.entrypoint.classloader.PaperSimplePluginClassLoader");
    }

    private static MessageDigest digest() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException failure) {
            throw new IllegalStateException("SHA-256 is unavailable", failure);
        }
    }

    private static void requireHash(byte[] actual, String expected) throws IOException {
        if (!HexFormat.of().formatHex(actual).equals(expected)) {
            throw new IOException("Embedded Iris runtime checksum does not match");
        }
    }

    static final class RuntimeArchive extends JarFile {
        private final JarFile original;
        private final boolean closeOriginal;

        RuntimeArchive(ArchiveOptions options) throws IOException {
            super(options.payload().toFile());
            this.original = options.original();
            this.closeOriginal = options.closeOriginal();
        }

        @Override
        public void close() throws IOException {
            if (closeOriginal) {
                try (original) {
                    super.close();
                }
            } else {
                super.close();
            }
        }

        private void closePayload() throws IOException {
            super.close();
        }
    }

    private record ArchiveOptions(Path payload, JarFile original, boolean closeOriginal) {
    }
}
