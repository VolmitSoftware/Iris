package art.arcane.iris.platform.bootstrap;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.Properties;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.stream.Stream;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class NativeRuntimeLibrariesTest {
    @Rule
    public TemporaryFolder temporary = new TemporaryFolder();

    @Test
    public void providersMustBeEmbedded() throws Exception {
        EmbeddedFixture fixture = embeddedFixture();
        fixture.manifest().setProperty("storage", "repository");
        assertThrows(IllegalStateException.class, () -> NativeRuntimeLibraries.from(fixture.manifest(), "26.2.0"));
        fixture.manifest().remove("storage");
        assertThrows(IllegalStateException.class, () -> NativeRuntimeLibraries.from(fixture.manifest(), "26.2.0"));
    }

    @Test
    public void rejectsMinecraftVersionsWithoutProvider() throws Exception {
        EmbeddedFixture fixture = embeddedFixture();
        assertThrows(UnsupportedOperationException.class, () -> NativeRuntimeLibraries.from(fixture.manifest(), "1.21.11"));
        fixture.manifest().setProperty("modules", "native-common,native-v26_2_R1");
        assertThrows(UnsupportedOperationException.class, () -> NativeRuntimeLibraries.from(fixture.manifest(), "26.3.0"));
    }

    @Test
    public void rejectsProviderBytesFromDifferentBuild() throws Exception {
        Path jar = temporary.newFile("native-runtime.jar").toPath();
        Files.write(jar, new byte[0]);
        NativeRuntimeLibraries.verify(jar, "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855");
        Files.writeString(jar, "different build");
        assertThrows(IOException.class, () -> NativeRuntimeLibraries.verify(jar,
                "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"));
    }

    @Test
    public void embeddedProvidersSelectOnlyCommonAndCurrentVersion() throws Exception {
        EmbeddedFixture fixture = embeddedFixture();
        NativeRuntimeLibraries libraries = NativeRuntimeLibraries.from(fixture.manifest(), "26.2.0");
        Path downloads = temporary.newFolder("downloads").toPath();
        try (URLClassLoader resources = new URLClassLoader(new URL[]{fixture.resources().toUri().toURL()}, null)) {
            List<URL> providers = libraries.providerUrls(downloads, resources);
            assertEquals(2, providers.size());
            assertEquals(List.of("native-common", "native-v26_2_R1"), providers.stream()
                    .map(url -> Path.of(URI.create(url.toString())).getParent().getParent().getFileName().toString())
                    .sorted().toList());
            for (URL provider : providers) {
                Path artifact = Path.of(provider.toURI());
                String module = artifact.getParent().getParent().getFileName().toString();
                assertArrayEquals(Files.readAllBytes(fixture.resources().resolve("META-INF/iris/native/" + module + ".jar")),
                        Files.readAllBytes(artifact));
                FileTime timestamp = FileTime.fromMillis(1000);
                Files.setLastModifiedTime(artifact, timestamp);
                libraries.providerUrls(downloads, resources);
                assertEquals(timestamp, Files.getLastModifiedTime(artifact));
            }
        }
    }

    @Test
    public void embeddedProvidersRepairCorruptCachedBytes() throws Exception {
        EmbeddedFixture fixture = embeddedFixture();
        NativeRuntimeLibraries libraries = NativeRuntimeLibraries.from(fixture.manifest(), "26.2.0");
        Path downloads = temporary.newFolder("downloads").toPath();
        try (URLClassLoader resources = new URLClassLoader(new URL[]{fixture.resources().toUri().toURL()}, null)) {
            List<URL> providers = libraries.providerUrls(downloads, resources);
            Path corrupted = Path.of(providers.getFirst().toURI());
            byte[] expected = Files.readAllBytes(corrupted);
            Files.writeString(corrupted, "truncated");
            libraries.providerUrls(downloads, resources);
            assertArrayEquals(expected, Files.readAllBytes(corrupted));
        }
    }

    @Test
    public void embeddedProvidersRejectMissingResourceWithoutCachingIt() throws Exception {
        EmbeddedFixture fixture = embeddedFixture();
        Files.delete(fixture.resources().resolve("META-INF/iris/native/native-v26_2_R1.jar"));
        NativeRuntimeLibraries libraries = NativeRuntimeLibraries.from(fixture.manifest(), "26.2.0");
        Path downloads = temporary.newFolder("downloads").toPath();
        try (URLClassLoader resources = new URLClassLoader(new URL[]{fixture.resources().toUri().toURL()}, null)) {
            IOException failure = assertThrows(IOException.class, () -> libraries.providerUrls(downloads, resources));
            assertTrue(failure.getMessage().contains("native-v26_2_R1"));
            assertNoFailedExtract(downloads);
        }
    }

    @Test
    public void embeddedProvidersRejectTruncatedResourceWithoutCachingIt() throws Exception {
        EmbeddedFixture fixture = embeddedFixture();
        Path resource = fixture.resources().resolve("META-INF/iris/native/native-v26_2_R1.jar");
        byte[] bytes = Files.readAllBytes(resource);
        Files.write(resource, Arrays.copyOf(bytes, bytes.length / 2));
        NativeRuntimeLibraries libraries = NativeRuntimeLibraries.from(fixture.manifest(), "26.2.0");
        Path downloads = temporary.newFolder("downloads").toPath();
        try (URLClassLoader resources = new URLClassLoader(new URL[]{fixture.resources().toUri().toURL()}, null)) {
            IOException failure = assertThrows(IOException.class, () -> libraries.providerUrls(downloads, resources));
            assertTrue(failure.getMessage().contains("native-v26_2_R1"));
            assertNoFailedExtract(downloads);
        }
    }

    @Test
    public void embeddedCoordinatesMustMatchTheirContentHash() throws Exception {
        EmbeddedFixture fixture = embeddedFixture();
        fixture.manifest().setProperty("native-common.coordinate", "com.github.VolmitSoftware.VolmLib:native-common:revision");
        assertThrows(IllegalStateException.class, () -> NativeRuntimeLibraries.from(fixture.manifest(), "26.2.0"));
    }

    private EmbeddedFixture embeddedFixture() throws Exception {
        Properties properties = new Properties();
        List<String> modules = List.of("native-common", "native-v26_2_R1", "native-v26_3_R1");
        properties.setProperty("modules", String.join(",", modules));
        properties.setProperty("storage", "embedded");
        Path resources = temporary.newFolder("resources").toPath();
        Path providers = Files.createDirectories(resources.resolve("META-INF/iris/native"));
        for (String module : modules) {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            try (JarOutputStream jar = new JarOutputStream(output)) {
                jar.putNextEntry(new JarEntry("provider.txt"));
                jar.write(module.getBytes(StandardCharsets.UTF_8));
                jar.closeEntry();
            }
            byte[] bytes = output.toByteArray();
            String hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
            properties.setProperty(module + ".coordinate", "com.github.VolmitSoftware.VolmLib:" + module + ":embedded-" + hash);
            properties.setProperty(module + ".sha256", hash);
            Files.write(providers.resolve(module + ".jar"), bytes);
        }
        return new EmbeddedFixture(properties, resources);
    }

    private static void assertNoFailedExtract(Path downloads) throws IOException {
        try (Stream<Path> files = Files.walk(downloads)) {
            assertFalse(files.anyMatch(path -> Files.isRegularFile(path)
                    && (path.getFileName().toString().endsWith(".tmp") || path.getFileName().toString().startsWith("native-v26_2_R1-"))));
        }
    }

    private record EmbeddedFixture(Properties manifest, Path resources) {
    }
}
