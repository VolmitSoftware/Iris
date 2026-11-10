import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream;
import org.apache.commons.compress.archivers.zip.ZipFile;
import org.gradle.api.GradleException;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.Enumeration;
import java.util.HexFormat;
import java.util.Map;
import java.util.TreeMap;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class JarCompactorTest {
    @Rule
    public final TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void preservesFilesAndOmitsDirectoryEntries() throws Exception {
        File artifact = temporaryFolder.newFile("artifact.jar");
        byte[] content = "Iris artifact content".getBytes(StandardCharsets.UTF_8);
        try (JarOutputStream output = new JarOutputStream(new FileOutputStream(artifact))) {
            output.putNextEntry(new JarEntry("example/"));
            output.closeEntry();
            output.putNextEntry(new JarEntry("example/value.txt"));
            output.write(content);
            output.closeEntry();
        }

        JarCompactor.compact(artifact);

        try (JarFile jar = new JarFile(artifact)) {
            assertNull(jar.getJarEntry("example/"));
            assertArrayEquals(content, jar.getInputStream(
                    jar.getJarEntry("example/value.txt")).readAllBytes());
        }
    }

    @Test
    public void preservesJarIdentityAndProducesDeterministicSeekableOutput() throws Exception {
        File artifact = temporaryFolder.newFile("identity.jar");
        try (JarOutputStream output = new JarOutputStream(new FileOutputStream(artifact));
             InputStream classResource = getClass().getResourceAsStream("/JarCompactorTest.class")) {
            output.setLevel(9);
            writeEntry(output, "META-INF/MANIFEST.MF", "Manifest-Version: 1.0\r\nMulti-Release: true\r\n\r\n"
                    .getBytes(StandardCharsets.UTF_8));
            writeEntry(output, "JarCompactorTest.class", classResource.readAllBytes());
            writeEntry(output, "META-INF/iris/generation-kernels/abi-1.seal", "registry fixture".getBytes(StandardCharsets.UTF_8));
            writeEntry(output, "example/caf\u00e9.txt", "unicode name".getBytes(StandardCharsets.UTF_8));
            for (int index = 0; index < 100; index++) {
                writeEntry(output, "example/item-" + index + ".txt", ("value-" + index).getBytes(StandardCharsets.UTF_8));
            }
        }
        Map<String, EntryIdentity> original = identity(artifact);
        long originalBytes = artifact.length();

        JarCompactor.compact(artifact);

        assertEquals(original, identity(artifact));
        assertTrue(artifact.length() < originalBytes);
        try (JarFile jar = new JarFile(artifact)) {
            assertEquals("true", jar.getManifest().getMainAttributes().getValue("Multi-Release"));
            assertEquals(0xCA, jar.getInputStream(jar.getJarEntry("JarCompactorTest.class")).read());
        }
        try (ZipFile zip = ZipFile.builder().setFile(artifact).get()) {
            Enumeration<ZipArchiveEntry> entries = zip.getEntries();
            while (entries.hasMoreElements()) {
                assertFalse(entries.nextElement().getGeneralPurposeBit().usesDataDescriptor());
            }
        }
        byte[] first = Files.readAllBytes(artifact.toPath());
        JarCompactor.compact(artifact);
        assertArrayEquals(first, Files.readAllBytes(artifact.toPath()));
    }

    @Test
    public void rejectsMalformedArchivesWithoutReplacingTheSource() throws Exception {
        File artifact = temporaryFolder.newFile("invalid.jar");
        byte[] original = "not a zip archive".getBytes(StandardCharsets.UTF_8);
        Files.write(artifact.toPath(), original);

        assertThrows(GradleException.class, () -> JarCompactor.compact(artifact));

        assertArrayEquals(original, Files.readAllBytes(artifact.toPath()));
        assertEquals(1, temporaryFolder.getRoot().listFiles().length);
    }

    @Test
    public void rejectsCorruptPayloadWithoutReplacingTheSource() throws Exception {
        File artifact = temporaryFolder.newFile("corrupt.jar");
        byte[] payload = "checked-payload".getBytes(StandardCharsets.UTF_8);
        CRC32 crc = new CRC32();
        crc.update(payload);
        try (JarOutputStream output = new JarOutputStream(new FileOutputStream(artifact))) {
            JarEntry entry = new JarEntry("value.txt");
            entry.setMethod(ZipEntry.STORED);
            entry.setSize(payload.length);
            entry.setCrc(crc.getValue());
            output.putNextEntry(entry);
            output.write(payload);
            output.closeEntry();
        }
        byte[] corrupt = Files.readAllBytes(artifact.toPath());
        int payloadOffset = new String(corrupt, StandardCharsets.ISO_8859_1).indexOf("checked-payload");
        assertTrue(payloadOffset > 0);
        corrupt[payloadOffset] ^= 1;
        Files.write(artifact.toPath(), corrupt);

        assertThrows(GradleException.class, () -> JarCompactor.compact(artifact));

        assertArrayEquals(corrupt, Files.readAllBytes(artifact.toPath()));
    }

    @Test
    public void rejectsDuplicateEntriesWithoutReplacingTheSource() throws Exception {
        File artifact = temporaryFolder.newFile("duplicate.jar");
        try (ZipArchiveOutputStream output = new ZipArchiveOutputStream(artifact)) {
            for (int index = 0; index < 2; index++) {
                output.putArchiveEntry(new ZipArchiveEntry("duplicate.txt"));
                output.write(index);
                output.closeArchiveEntry();
            }
        }
        byte[] original = Files.readAllBytes(artifact.toPath());

        assertThrows(GradleException.class, () -> JarCompactor.compact(artifact));

        assertArrayEquals(original, Files.readAllBytes(artifact.toPath()));
    }

    private static void writeEntry(JarOutputStream output, String name, byte[] content) throws Exception {
        JarEntry entry = new JarEntry(name);
        entry.setTime(315_532_800_000L);
        entry.setComment("preserved entry comment");
        entry.setExtra(new byte[]{(byte) 0xFE, (byte) 0xCA, 1, 0, 7});
        output.putNextEntry(entry);
        output.write(content);
        output.closeEntry();
    }

    private static Map<String, EntryIdentity> identity(File artifact) throws Exception {
        Map<String, EntryIdentity> entries = new TreeMap<>();
        try (JarFile jar = new JarFile(artifact)) {
            Enumeration<JarEntry> contents = jar.entries();
            while (contents.hasMoreElements()) {
                JarEntry entry = contents.nextElement();
                byte[] bytes;
                try (InputStream input = jar.getInputStream(entry)) {
                    bytes = input.readAllBytes();
                }
                entries.put(entry.getName(), new EntryIdentity(
                        HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)),
                        entry.getSize(), entry.getCrc(), entry.getTime(), entry.getComment(),
                        entry.getExtra() == null ? "" : HexFormat.of().formatHex(entry.getExtra())));
            }
        }
        return entries;
    }

    private record EntryIdentity(String sha256, long size, long crc, long time, String comment, String extra) {
    }
}
