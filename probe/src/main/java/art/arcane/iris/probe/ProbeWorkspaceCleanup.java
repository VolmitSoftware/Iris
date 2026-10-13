package art.arcane.iris.probe;

import java.io.IOException;
import java.nio.file.DirectoryNotEmptyException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;

final class ProbeWorkspaceCleanup {
    private static final int MAXIMUM_ATTEMPTS = 4;
    private static final SimpleFileVisitor<Path> DELETE_VISITOR = new SimpleFileVisitor<>() {
        @Override
        public FileVisitResult visitFile(Path file, BasicFileAttributes attributes) throws IOException {
            Files.deleteIfExists(file);
            return FileVisitResult.CONTINUE;
        }

        @Override
        public FileVisitResult visitFileFailed(Path file, IOException failure) throws IOException {
            if (failure instanceof NoSuchFileException) {
                return FileVisitResult.CONTINUE;
            }
            throw failure;
        }

        @Override
        public FileVisitResult postVisitDirectory(Path directory, IOException failure) throws IOException {
            if (failure != null) {
                throw failure;
            }
            Files.deleteIfExists(directory);
            return FileVisitResult.CONTINUE;
        }
    };

    private ProbeWorkspaceCleanup() {
    }

    static void deleteRecursively(Path root) throws IOException {
        for (int attempt = 1; ; attempt++) {
            try {
                Files.walkFileTree(root, DELETE_VISITOR);
                return;
            } catch (DirectoryNotEmptyException failure) {
                if (attempt == MAXIMUM_ATTEMPTS) {
                    throw failure;
                }
            }
        }
    }
}
