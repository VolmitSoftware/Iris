package art.arcane.iris.platform.bootstrap;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.IOException;
import java.net.URI;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class NoiseNativesTest {
    private static final String PLATFORM = "linux-x86_64";
    private static final String FILE = "libvolmlib_noise.so";
    private static final byte[] NATIVE = "native-kernels".getBytes(StandardCharsets.UTF_8);
    private static final String COMMIT = "0123456789abcdef0123456789abcdef01234567";

    @Rule
    public final TemporaryFolder temporaryFolder = new TemporaryFolder();

    private final List<String> errors = new ArrayList<>();
    private final SlimJar.BootstrapLogger logger = new SlimJar.BootstrapLogger() {
        @Override
        public void info(String message) {
        }

        @Override
        public void error(String message) {
            errors.add(message);
        }

        @Override
        public void debug(String message) {
        }
    };

    @Test
    public void platformMatchesVolmLibNaming() {
        assertEquals("macos-aarch64", NoiseNatives.platform("Mac OS X", "aarch64"));
        assertEquals("windows-x86_64", NoiseNatives.platform("Windows 11", "amd64"));
        assertEquals("linux-aarch64", NoiseNatives.platform("Linux", "arm64"));
        assertNull(NoiseNatives.platform("FreeBSD", "amd64"));
        assertNull(NoiseNatives.platform("Linux", "riscv64"));
    }

    @Test
    public void installsAVerifiedDownloadIntoAHashedCacheAndReusesIt() throws Exception {
        List<URI> requested = new ArrayList<>();
        Path cache = temporaryFolder.newFolder("cache").toPath();
        try (URLClassLoader resources = resources(manifest(""), Map.of())) {
            Path installed = NoiseNatives.install(cache, PLATFORM, resources, serving(requested, Map.of()), logger);
            assertEquals(cache.resolve(sha256(NATIVE)).resolve(FILE), installed);
            assertArrayEquals(NATIVE, Files.readAllBytes(installed));
            assertEquals(List.of(URI.create("https://api.github.com/repos/VolmitSoftware/VolmLib/git/blobs/" + blob(NATIVE))), requested);

            Path reused = NoiseNatives.install(cache, PLATFORM, resources, (uri, limit) -> {
                throw new IOException("must not fetch");
            }, logger);
            assertEquals(installed, reused);
        }
        assertTrue(errors.isEmpty());
    }

    @Test
    public void rejectsBytesThatDoNotMatchThePinAndTriesTheNextSource() throws Exception {
        Path cache = temporaryFolder.newFolder("cache").toPath();
        URI raw = URI.create("https://raw.githubusercontent.com/VolmitSoftware/VolmLib/" + COMMIT
                + "/shared/native/noise/prebuilt/" + PLATFORM + "/" + FILE);
        List<URI> requested = new ArrayList<>();
        try (URLClassLoader resources = resources(manifest(COMMIT), Map.of())) {
            Path installed = NoiseNatives.install(cache, PLATFORM, resources,
                    serving(requested, Map.of(raw, "tampered!!!!!!".getBytes(StandardCharsets.UTF_8))), logger);
            assertArrayEquals(NATIVE, Files.readAllBytes(installed));
            assertEquals(raw, requested.get(0));
            assertEquals(2, requested.size());
        }
    }

    @Test
    public void fallsBackToJavaWithOneErrorWhenEverySourceFails() throws Exception {
        Path cache = temporaryFolder.newFolder("cache").toPath();
        try (URLClassLoader resources = resources(manifest(COMMIT), Map.of())) {
            assertNull(NoiseNatives.install(cache, PLATFORM, resources, (uri, limit) -> {
                throw new IOException("offline");
            }, logger));
        }
        assertEquals(1, errors.size());
        assertTrue(errors.get(0).contains(sha256(NATIVE)));
        try (Stream<Path> files = Files.walk(cache)) {
            assertFalse(files.anyMatch(Files::isRegularFile));
        }
    }

    @Test
    public void bundledNativesAndMissingManifestsNeverDownload() throws Exception {
        Path cache = temporaryFolder.newFolder("cache").toPath();
        NoiseNatives.Fetcher refuse = (uri, limit) -> {
            throw new AssertionError("must not fetch " + uri);
        };
        try (URLClassLoader bundled = resources(manifest(""), Map.of("art/arcane/volmlib/native/" + PLATFORM + "/" + FILE, NATIVE));
             URLClassLoader unpinned = resources(null, Map.of())) {
            assertNull(NoiseNatives.install(cache, PLATFORM, bundled, refuse, logger));
            assertNull(NoiseNatives.install(cache, PLATFORM, unpinned, refuse, logger));
            assertNull(NoiseNatives.install(cache, "macos-x86_64", bundled, refuse, logger));
        }
        assertTrue(errors.isEmpty());
    }

    private NoiseNatives.Fetcher serving(List<URI> requested, Map<URI, byte[]> overrides) {
        return (uri, limit) -> {
            requested.add(uri);
            return overrides.getOrDefault(uri, NATIVE);
        };
    }

    private String manifest(String commit) {
        return "commit=" + commit + "\nsource=shared/native/noise/prebuilt\nplatforms=" + PLATFORM + "\n"
                + PLATFORM + ".file=" + FILE + "\n" + PLATFORM + ".size=" + NATIVE.length + "\n"
                + PLATFORM + ".sha256=" + sha256(NATIVE) + "\n" + PLATFORM + ".blob=" + blob(NATIVE) + "\n";
    }

    private URLClassLoader resources(String manifest, Map<String, byte[]> extra) throws IOException {
        Path root = temporaryFolder.newFolder().toPath();
        if (manifest != null) {
            Path file = root.resolve(NoiseNatives.MANIFEST);
            Files.createDirectories(file.getParent());
            Files.writeString(file, manifest, StandardCharsets.UTF_8);
        }
        for (Map.Entry<String, byte[]> entry : extra.entrySet()) {
            Path file = root.resolve(entry.getKey());
            Files.createDirectories(file.getParent());
            Files.write(file, entry.getValue());
        }
        return new URLClassLoader(new URL[]{root.toUri().toURL()}, null);
    }

    private static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (Exception failure) {
            throw new IllegalStateException(failure);
        }
    }

    private static String blob(byte[] bytes) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-1");
            digest.update(("blob " + bytes.length + "\0").getBytes(StandardCharsets.US_ASCII));
            return HexFormat.of().formatHex(digest.digest(bytes));
        } catch (Exception failure) {
            throw new IllegalStateException(failure);
        }
    }
}
