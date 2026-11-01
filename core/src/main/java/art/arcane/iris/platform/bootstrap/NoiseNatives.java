package art.arcane.iris.platform.bootstrap;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Properties;

/**
 * Provides VolmLib's native noise kernels to jars that leave them out (the size-capped Bukkit jar). The build pins
 * each platform's binary in {@link #MANIFEST}; the matching file is downloaded once into a cache folder named by its
 * SHA-256 and handed to VolmLib through {@code volmlib.native.library}. Jars that bundle the kernels never download.
 * Any failure leaves VolmLib on its Java kernels, which produce identical output.
 */
public final class NoiseNatives {
    static final String MANIFEST = "META-INF/iris/noise-natives.properties";
    static final String LIBRARY_PROPERTY = "volmlib.native.library";
    static final String POLICY_PROPERTY = "volmlib.native";
    private static final String RESOURCE_PREFIX = "art/arcane/volmlib/native/";
    private static final String RAW_ROOT = "https://raw.githubusercontent.com/VolmitSoftware/VolmLib/";
    private static final String BLOB_ROOT = "https://api.github.com/repos/VolmitSoftware/VolmLib/git/blobs/";
    private static final int CONNECT_TIMEOUT_MILLIS = 5_000;
    private static final int READ_TIMEOUT_MILLIS = 15_000;

    private NoiseNatives() {
    }

    public static void provision(Path cacheRoot, SlimJar.BootstrapLogger logger) {
        if (System.getProperty(LIBRARY_PROPERTY) != null
                || "off".equalsIgnoreCase(System.getProperty(POLICY_PROPERTY, "").trim())) {
            return;
        }
        String platform = platform(System.getProperty("os.name", ""), System.getProperty("os.arch", ""));
        Path library = install(cacheRoot, platform, NoiseNatives.class.getClassLoader(), NoiseNatives::download, logger);
        if (library != null) {
            System.setProperty(LIBRARY_PROPERTY, library.toAbsolutePath().toString());
        }
    }

    static String platform(String operatingSystem, String architecture) {
        String os = operatingSystem.toLowerCase(Locale.ROOT);
        String family = os.contains("mac") ? "macos" : os.contains("linux") ? "linux" : os.contains("win") ? "windows" : null;
        String arch = switch (architecture) {
            case "aarch64", "arm64" -> "aarch64";
            case "amd64", "x86_64" -> "x86_64";
            default -> null;
        };
        return family == null || arch == null ? null : family + "-" + arch;
    }

    static Path install(Path cacheRoot, String platform, ClassLoader resources, Fetcher fetcher,
                        SlimJar.BootstrapLogger logger) {
        if (platform == null) {
            return null;
        }
        Pin pin = pin(resources, platform);
        if (pin == null || resources.getResource(RESOURCE_PREFIX + platform + "/" + pin.file()) != null) {
            return null;
        }
        Path target = cacheRoot.resolve(pin.sha256()).resolve(pin.file());
        try {
            if (Files.isRegularFile(target)) {
                if (pin.matches(Files.readAllBytes(target))) {
                    return target;
                }
                Files.delete(target);
            }
        } catch (IOException failure) {
            logger.error("Native noise cache " + target + " is unreadable (" + failure.getMessage() + ")");
        }
        List<String> failures = new ArrayList<>();
        for (URI source : pin.sources(platform)) {
            long started = System.nanoTime();
            try {
                byte[] bytes = fetcher.fetch(source, pin.size());
                if (!pin.matches(bytes)) {
                    failures.add(source.getHost() + ": checksum mismatch");
                    continue;
                }
                write(target, bytes);
                logger.info("Native noise kernels for " + platform + ": " + (bytes.length / 1024) + " KiB in "
                        + ((System.nanoTime() - started) / 1_000_000L) + " ms from " + source.getHost());
                return target;
            } catch (IOException failure) {
                failures.add(source.getHost() + ": " + failure.getMessage());
            }
        }
        logger.error("Native noise kernels unavailable for " + platform + " (" + String.join("; ", failures)
                + "); generation uses the Java kernels with identical output. To install manually, save "
                + pin.sources(platform).get(0) + " (sha256 " + pin.sha256() + ") as " + target);
        return null;
    }

    private static Pin pin(ClassLoader resources, String platform) {
        Properties manifest = new Properties();
        try (InputStream input = resources.getResourceAsStream(MANIFEST)) {
            if (input == null) {
                return null;
            }
            try (Reader reader = new InputStreamReader(input, StandardCharsets.UTF_8)) {
                manifest.load(reader);
            }
        } catch (IOException failure) {
            return null;
        }
        String file = manifest.getProperty(platform + ".file");
        String sha256 = manifest.getProperty(platform + ".sha256");
        String blob = manifest.getProperty(platform + ".blob");
        String size = manifest.getProperty(platform + ".size");
        if (file == null || sha256 == null || blob == null || size == null) {
            return null;
        }
        return new Pin(file, Long.parseLong(size), sha256, blob, manifest.getProperty("commit", ""),
                manifest.getProperty("source", ""));
    }

    private static void write(Path target, byte[] bytes) throws IOException {
        Files.createDirectories(target.getParent());
        Path temporary = Files.createTempFile(target.getParent(), target.getFileName().toString(), ".part");
        try {
            Files.write(temporary, bytes);
            try {
                Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException unsupported) {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private static byte[] download(URI source, long size) throws IOException {
        URL url = source.toURL();
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setConnectTimeout(CONNECT_TIMEOUT_MILLIS);
        connection.setReadTimeout(READ_TIMEOUT_MILLIS);
        connection.setRequestProperty("User-Agent", "Iris");
        connection.setRequestProperty("Accept", "application/vnd.github.raw+json");
        try {
            int status = connection.getResponseCode();
            if (status != HttpURLConnection.HTTP_OK) {
                throw new IOException("HTTP " + status);
            }
            try (InputStream input = connection.getInputStream()) {
                byte[] bytes = input.readNBytes((int) Math.min(Integer.MAX_VALUE - 8L, size + 1L));
                if (bytes.length != size) {
                    throw new IOException("expected " + size + " bytes, got " + bytes.length);
                }
                return bytes;
            }
        } finally {
            connection.disconnect();
        }
    }

    @FunctionalInterface
    interface Fetcher {
        byte[] fetch(URI source, long size) throws IOException;
    }

    private record Pin(String file, long size, String sha256, String blob, String commit, String source) {
        boolean matches(byte[] bytes) {
            if (bytes.length != size) {
                return false;
            }
            try {
                return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)).equals(sha256);
            } catch (NoSuchAlgorithmException missing) {
                throw new IllegalStateException(missing);
            }
        }

        List<URI> sources(String platform) {
            List<URI> sources = new ArrayList<>(2);
            if (!commit.isEmpty() && !source.isEmpty()) {
                sources.add(URI.create(RAW_ROOT + commit + "/" + source + "/" + platform + "/" + file));
            }
            sources.add(URI.create(BLOB_ROOT + blob));
            return sources;
        }
    }
}
