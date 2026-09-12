package art.arcane.iris.structure.authoring;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

public class StructureTransactionClaimTest {
    private static final StructureKey KEY = new StructureKey("iris", "village");

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void failedManifestInstallLeavesExistingResourcesUnchangedAndUnowned() throws Exception {
        Path root = temporaryFolder.newFolder("claim-rollback").toPath();
        Path resource = root.resolve("objects/village.iob");
        Files.createDirectories(resource.getParent());
        byte[] content = "existing".getBytes(StandardCharsets.UTF_8);
        Files.write(resource, content);
        String hash = StructureHash.sha256(content);
        StructureOwnershipManifest manifest = new StructureOwnershipManifest(
                StructureOwnershipManifest.CURRENT_SCHEMA_VERSION,
                KEY,
                StructureSource.of(StructureSource.Kind.IRIS, KEY),
                StructureBackend.IRIS_ASSEMBLY,
                List.of(StructureCapability.BLOCKS),
                List.of(),
                Map.of("objects/village.iob", hash));
        StructureTransactionReadSet readSet = StructureTransactionReadSet.builder()
                .file("objects/village.iob", hash)
                .absent(manifest.relativePath())
                .build();
        StructureTransactionWriter writer = new StructureTransactionWriter(
                root,
                new FailManifestMoveOperations());

        StructureWriteResult result = writer.claimExisting(manifest, readSet);

        assertEquals(StructureWriteResult.Status.ROLLED_BACK, result.status());
        assertArrayEquals(content, Files.readAllBytes(resource));
        assertFalse(Files.exists(writer.ownershipManifestPath(KEY)));
    }

    private static final class FailManifestMoveOperations implements StructureFileOperations {
        private final NioStructureFileOperations delegate = new NioStructureFileOperations();
        private boolean failed;

        @Override
        public boolean exists(Path path) {
            return delegate.exists(path);
        }

        @Override
        public boolean isRegularFile(Path path) {
            return delegate.isRegularFile(path);
        }

        @Override
        public byte[] readAllBytes(Path path) throws IOException {
            return delegate.readAllBytes(path);
        }

        @Override
        public String sha256(Path path) throws IOException {
            return delegate.sha256(path);
        }

        @Override
        public void createDirectories(Path path) throws IOException {
            delegate.createDirectories(path);
        }

        @Override
        public void writeNew(Path path, byte[] content) throws IOException {
            delegate.writeNew(path, content);
        }

        @Override
        public void move(Path source, Path target) throws IOException {
            delegate.move(source, target);
        }

        @Override
        public void moveNew(Path source, Path target) throws IOException {
            String portable = source.toString().replace('\\', '/');
            if (!failed && portable.endsWith("staged/ownership-manifest.json")) {
                failed = true;
                throw new IOException("Injected manifest install failure");
            }
            delegate.moveNew(source, target);
        }

        @Override
        public void deleteIfExists(Path path) throws IOException {
            delegate.deleteIfExists(path);
        }

        @Override
        public void deleteTree(Path root) throws IOException {
            delegate.deleteTree(root);
        }
    }
}
