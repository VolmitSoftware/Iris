package art.arcane.iris.core.structure.export;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import java.util.stream.Stream;

final class AtomicDatapackPublisher {
    Publication publish(
            Map<String, byte[]> resources,
            Path output,
            VanillaJigsawExportFormat format,
            boolean replaceExisting
    ) throws IOException {
        Path parent = output.getParent();
        if (parent == null) {
            throw new IOException("Export output has no parent directory: " + output);
        }
        Files.createDirectories(parent);
        Path staging = Files.createTempDirectory(parent, ".iris-jigsaw-export-");
        List<String> cleanupWarnings = new ArrayList<>();
        try {
            Path artifact = format == VanillaJigsawExportFormat.DIRECTORY
                    ? createDirectoryArtifact(staging, resources)
                    : createZipArtifact(staging, resources);
            replaceAtomically(artifact, output, replaceExisting, cleanupWarnings);
        } finally {
            try {
                deleteTree(staging);
            } catch (IOException exception) {
                cleanupWarnings.add("Could not remove export staging path '" + staging + "': " + exception.getMessage());
            }
        }
        return new Publication(List.copyOf(cleanupWarnings));
    }

    private Path createDirectoryArtifact(Path staging, Map<String, byte[]> resources) throws IOException {
        Path root = staging.resolve("pack");
        Files.createDirectory(root);
        for (String resource : sortedPaths(resources)) {
            Path target = root.resolve(resource).normalize();
            if (!target.startsWith(root)) {
                throw new IOException("Export resource escapes staging root: " + resource);
            }
            Files.createDirectories(target.getParent());
            Files.write(target, resources.get(resource));
        }
        return root;
    }

    private Path createZipArtifact(Path staging, Map<String, byte[]> resources) throws IOException {
        Path zip = staging.resolve("pack.zip");
        try (ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(zip))) {
            for (String resource : sortedPaths(resources)) {
                ZipEntry entry = new ZipEntry(resource);
                entry.setTime(0L);
                output.putNextEntry(entry);
                output.write(resources.get(resource));
                output.closeEntry();
            }
        }
        return zip;
    }

    private void replaceAtomically(
            Path artifact,
            Path output,
            boolean replaceExisting,
            List<String> cleanupWarnings
    ) throws IOException {
        Path backup = null;
        if (Files.exists(output)) {
            if (!replaceExisting) {
                throw new IOException("Export output already exists: " + output);
            }
            backup = uniqueBackup(output);
            atomicMove(output, backup);
        }

        try {
            atomicMove(artifact, output);
        } catch (IOException publicationFailure) {
            if (backup != null && Files.exists(backup) && !Files.exists(output)) {
                try {
                    atomicMove(backup, output);
                } catch (IOException restoreFailure) {
                    publicationFailure.addSuppressed(restoreFailure);
                }
            }
            throw publicationFailure;
        }

        if (backup != null) {
            try {
                deleteTree(backup);
            } catch (IOException exception) {
                cleanupWarnings.add("Export succeeded, but the replaced output backup remains at '"
                        + backup + "': " + exception.getMessage());
            }
        }
    }

    private void atomicMove(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException exception) {
            throw new IOException("Filesystem does not support an atomic export move from '"
                    + source + "' to '" + target + "'.", exception);
        }
    }

    private Path uniqueBackup(Path output) {
        return output.resolveSibling("." + output.getFileName() + ".iris-backup-" + UUID.randomUUID());
    }

    private List<String> sortedPaths(Map<String, byte[]> resources) {
        List<String> paths = new ArrayList<>(resources.keySet());
        paths.sort(String::compareTo);
        return paths;
    }

    private void deleteTree(Path root) throws IOException {
        if (!Files.exists(root)) {
            return;
        }
        try (Stream<Path> paths = Files.walk(root)) {
            List<Path> ordered = paths.sorted(Comparator.reverseOrder()).toList();
            IOException failure = null;
            for (Path path : ordered) {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException exception) {
                    if (failure == null) {
                        failure = exception;
                    } else {
                        failure.addSuppressed(exception);
                    }
                }
            }
            if (failure != null) {
                throw failure;
            }
        }
    }

    record Publication(List<String> cleanupWarnings) {
        Publication {
            cleanupWarnings = List.copyOf(cleanupWarnings);
        }
    }
}
