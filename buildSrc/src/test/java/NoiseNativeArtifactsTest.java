import org.gradle.api.GradleException;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.TreeMap;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class NoiseNativeArtifactsTest {
    private static final String LINUX = "art/arcane/volmlib/native/linux-x86_64/libvolmlib_noise.so";
    private static final String WINDOWS = "art/arcane/volmlib/native/windows-x86_64/volmlib_noise.dll";
    private static final byte[] LINUX_BYTES = "linux-native".getBytes(StandardCharsets.UTF_8);
    private static final byte[] WINDOWS_BYTES = "windows-native".getBytes(StandardCharsets.UTF_8);

    @Rule
    public final TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void blobMatchesGitHashObject() throws Exception {
        File file = temporaryFolder.newFile("blob.bin");
        Files.write(file.toPath(), LINUX_BYTES);
        assertEquals(git(temporaryFolder.getRoot(), "hash-object", file.getAbsolutePath()), NoiseNativeArtifacts.blob(LINUX_BYTES));
    }

    @Test
    public void generateRecordsEveryPlatformWithoutCommitWhenNoCheckoutIsKnown() throws Exception {
        File output = temporaryFolder.getRoot().toPath().resolve("out/noise-natives.properties").toFile();
        List<String> warnings = NoiseNativeArtifacts.generate(List.of(volmLibJar()), null, output);

        Properties manifest = read(output);
        assertEquals("", manifest.getProperty("commit"));
        assertEquals("linux-x86_64,windows-x86_64", manifest.getProperty("platforms"));
        assertEquals("libvolmlib_noise.so", manifest.getProperty("linux-x86_64.file"));
        assertEquals(String.valueOf(LINUX_BYTES.length), manifest.getProperty("linux-x86_64.size"));
        assertEquals(NoiseNativeArtifacts.sha256(LINUX_BYTES), manifest.getProperty("linux-x86_64.sha256"));
        assertEquals(NoiseNativeArtifacts.blob(WINDOWS_BYTES), manifest.getProperty("windows-x86_64.blob"));
        assertTrue(warnings.isEmpty());
    }

    @Test
    public void generatePinsTheLastCommitThatHoldsTheSameBytes() throws Exception {
        File checkout = checkout(LINUX_BYTES, WINDOWS_BYTES);
        File output = temporaryFolder.newFile("pinned.properties");
        NoiseNativeArtifacts.generate(List.of(volmLibJar()), checkout, output);

        assertEquals(git(checkout, "rev-parse", "HEAD"), read(output).getProperty("commit"));
    }

    @Test
    public void generateDropsTheCommitWhenTheCheckoutBytesDiffer() throws Exception {
        File checkout = checkout("stale".getBytes(StandardCharsets.UTF_8), WINDOWS_BYTES);
        File output = temporaryFolder.newFile("stale.properties");
        List<String> warnings = NoiseNativeArtifacts.generate(List.of(volmLibJar()), checkout, output);

        assertEquals("", read(output).getProperty("commit"));
        assertFalse(warnings.isEmpty());
    }

    @Test
    public void generateRejectsJarsWithoutNatives() throws Exception {
        File empty = jar("empty.jar", Map.of("plugin.yml", new byte[]{1}));
        File output = temporaryFolder.newFile("none.properties");
        assertThrows(GradleException.class, () -> NoiseNativeArtifacts.generate(List.of(empty), null, output));
    }

    @Test
    public void verifyRejectsBundledNativesAndMissingManifest() throws Exception {
        File manifest = temporaryFolder.newFile("manifest.properties");
        NoiseNativeArtifacts.generate(List.of(volmLibJar()), null, manifest);
        byte[] manifestBytes = Files.readAllBytes(manifest.toPath());

        NoiseNativeArtifacts.verify(jar("clean.jar", Map.of(NoiseNativeArtifacts.MANIFEST, manifestBytes)));
        assertThrows(GradleException.class, () -> NoiseNativeArtifacts.verify(
                jar("bundled.jar", Map.of(NoiseNativeArtifacts.MANIFEST, manifestBytes, LINUX, LINUX_BYTES))));
        assertThrows(GradleException.class, () -> NoiseNativeArtifacts.verify(
                jar("unpinned.jar", Map.of("plugin.yml", new byte[]{1}))));
    }

    private File volmLibJar() throws IOException {
        return jar("volmlib-shared.jar", Map.of(LINUX, LINUX_BYTES, WINDOWS, WINDOWS_BYTES,
                "art/arcane/volmlib/util/Noise.class", new byte[]{(byte) 0xCA, (byte) 0xFE}));
    }

    private File checkout(byte[] linux, byte[] windows) throws Exception {
        File root = temporaryFolder.newFolder();
        Path prebuilt = root.toPath().resolve(NoiseNativeArtifacts.SOURCE_PATH);
        Files.createDirectories(prebuilt.resolve("linux-x86_64"));
        Files.createDirectories(prebuilt.resolve("windows-x86_64"));
        Files.write(prebuilt.resolve("linux-x86_64/libvolmlib_noise.so"), linux);
        Files.write(prebuilt.resolve("windows-x86_64/volmlib_noise.dll"), windows);
        git(root, "init", "-q");
        git(root, "add", ".");
        git(root, "-c", "user.name=test", "-c", "user.email=test@example.com", "-c", "commit.gpgSign=false", "commit", "-q", "-m", "natives");
        return root;
    }

    private File jar(String name, Map<String, byte[]> entries) throws IOException {
        File file = new File(temporaryFolder.getRoot(), name);
        try (JarOutputStream output = new JarOutputStream(new FileOutputStream(file))) {
            for (Map.Entry<String, byte[]> entry : new TreeMap<>(entries).entrySet()) {
                output.putNextEntry(new JarEntry(entry.getKey()));
                output.write(entry.getValue());
                output.closeEntry();
            }
        }
        return file;
    }

    private static Properties read(File file) throws IOException {
        Properties properties = new Properties();
        try (Reader reader = Files.newBufferedReader(file.toPath(), StandardCharsets.UTF_8)) {
            properties.load(reader);
        }
        return properties;
    }

    private static String git(File directory, String... arguments) throws Exception {
        List<String> command = new ArrayList<>(List.of("git", "-C", directory.getAbsolutePath()));
        command.addAll(List.of(arguments));
        Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
        assertEquals(output, 0, process.waitFor());
        return output;
    }
}
