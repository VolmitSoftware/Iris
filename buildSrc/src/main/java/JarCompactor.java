import org.apache.commons.compress.archivers.zip.ExtraFieldUtils;
import org.apache.commons.compress.archivers.zip.Zip64Mode;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream;
import org.gradle.api.GradleException;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Set;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public final class JarCompactor {
    private static final int BUFFER_BYTES = 64 * 1024;

    private JarCompactor() {
    }

    public static void compact(File artifact) {
        if (artifact == null || !artifact.isFile()) {
            throw new GradleException("Cannot compact missing jar artifact: " + artifact);
        }

        Path source = artifact.toPath();
        Path temporary = null;
        try {
            temporary = Files.createTempFile(source.getParent(), artifact.getName(), ".compact");
            rewrite(source, temporary);
            replace(temporary, source);
        } catch (IOException exception) {
            if (temporary != null) {
                try {
                    Files.deleteIfExists(temporary);
                } catch (IOException cleanupFailure) {
                    exception.addSuppressed(cleanupFailure);
                }
            }
            throw new GradleException("Unable to compact jar artifact " + artifact.getAbsolutePath(), exception);
        }
    }

    private static void rewrite(Path source, Path destination) throws IOException {
        byte[] buffer = new byte[BUFFER_BYTES];
        try (ZipFile input = new ZipFile(source.toFile());
             ZipArchiveOutputStream output = new ZipArchiveOutputStream(destination)) {
            if (!output.isSeekable()) {
                throw new IOException("Jar compaction requires seekable output.");
            }
            output.setLevel(9);
            output.setUseZip64(Zip64Mode.Never);
            output.setCreateUnicodeExtraFields(ZipArchiveOutputStream.UnicodeExtraFieldPolicy.NEVER);
            Set<String> names = new HashSet<>();
            Enumeration<? extends ZipEntry> entries = input.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                if (entry.isDirectory()) {
                    continue;
                }
                if (!names.add(entry.getName())) {
                    throw new IOException("Duplicate jar entry: " + entry.getName());
                }
                output.putArchiveEntry(copyMetadata(entry));
                copyEntry(input, output, entry, buffer);
                output.closeArchiveEntry();
            }
        }
    }

    private static void copyEntry(ZipFile input, ZipArchiveOutputStream output, ZipEntry entry, byte[] buffer)
            throws IOException {
        CRC32 crc = new CRC32();
        long size = 0L;
        try (InputStream content = input.getInputStream(entry)) {
            int read;
            while ((read = content.read(buffer)) != -1) {
                output.write(buffer, 0, read);
                crc.update(buffer, 0, read);
                size += read;
            }
        }
        if (size != entry.getSize() || crc.getValue() != entry.getCrc()) {
            throw new IOException("Jar entry failed size or CRC verification: " + entry.getName());
        }
    }

    private static ZipArchiveEntry copyMetadata(ZipEntry source) throws IOException {
        ZipArchiveEntry target = new ZipArchiveEntry(source.getName());
        target.setMethod(ZipEntry.DEFLATED);
        if (source.getTime() >= 0L) {
            target.setTime(source.getTime());
        }
        if (source.getComment() != null) {
            target.setComment(source.getComment());
        }
        if (source.getExtra() != null) {
            target.setExtraFields(ExtraFieldUtils.parse(source.getExtra(), true,
                    ZipArchiveEntry.ExtraFieldParsingMode.BEST_EFFORT));
        }
        return target;
    }

    private static void replace(Path temporary, Path source) throws IOException {
        try {
            Files.move(
                    temporary,
                    source,
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException exception) {
            Files.move(temporary, source, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
