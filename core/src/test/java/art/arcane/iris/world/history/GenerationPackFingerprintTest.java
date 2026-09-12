package art.arcane.iris.world.history;

import org.junit.Assume;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertThrows;

public class GenerationPackFingerprintTest {
    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void versionOneMatchesItsGoldenContentAddress() throws Exception {
        Path pack = temporaryFolder.newFolder("golden-pack").toPath();
        Files.createDirectories(pack.resolve("objects"));
        Files.writeString(pack.resolve("objects/tree.json"), "oak");
        Files.createDirectories(pack.resolve("dimensions"));
        Files.writeString(pack.resolve("dimensions/main.json"), "{}");
        Files.createDirectories(pack.resolve(".git"));
        Files.writeString(pack.resolve(".git/config"), "ignored");
        Files.writeString(pack.resolve("workspace.code-workspace"), "ignored");

        String fingerprint = GenerationPackFingerprint.compute(
                pack,
                1
        );

        assertEquals("ad7d39c2b95bd3001248778911ce6bbd6b4cbdcece28145838c2b923a0bf73b3", fingerprint);
        Files.writeString(pack.resolve("objects/.DS_Store"), "finder");
        assertEquals("b36c630e300a9939fabe3fbd02f546f2a76b31d2ebccbf9106a13fb7d8b8eb98",
                GenerationPackFingerprint.compute(pack, 1));
        assertEquals(fingerprint, GenerationPackFingerprint.compute(pack, 2));
    }

    @Test
    public void versionTwoIgnoresFinderMetadataAtEveryDepth() throws Exception {
        Path source = temporaryFolder.newFolder("metadata-source").toPath();
        Files.createDirectories(source.resolve("objects/trees"));
        Files.writeString(source.resolve("objects/trees/oak.json"), "{}");
        Files.writeString(source.resolve("objects/.DS_Store"), "source metadata");
        Path target = temporaryFolder.getRoot().toPath().resolve("metadata-copy");
        GenerationPackRepository.copyPackTree(source, target);
        String expected = GenerationPackFingerprint.compute(source, 2);

        Files.writeString(target.resolve(".DS_Store"), "root metadata");
        Files.writeString(target.resolve("objects/.DS_Store"), "destination metadata");
        Files.writeString(target.resolve("objects/trees/.DS_Store"), "nested metadata");

        assertEquals(expected, GenerationPackFingerprint.compute(target, 2));
        assertNotEquals(GenerationPackFingerprint.compute(source, 1),
                GenerationPackFingerprint.compute(target, 1));
        Files.writeString(target.resolve("objects/trees/oak.json"), "{\"changed\":true}");
        assertNotEquals(expected, GenerationPackFingerprint.compute(target, 2));
    }

    @Test
    public void versionTwoStillIncludesOtherNestedHiddenResources() throws Exception {
        Path pack = temporaryFolder.newFolder("nested-hidden-pack").toPath();
        Files.createDirectories(pack.resolve("objects"));
        Files.writeString(pack.resolve("objects/.hidden"), "first");
        String first = GenerationPackFingerprint.compute(pack, 2);
        Files.writeString(pack.resolve("objects/.hidden"), "second");

        assertNotEquals(first, GenerationPackFingerprint.compute(pack, 2));
    }

    @Test
    public void contentChangesProduceDifferentAddresses() throws Exception {
        Path pack = temporaryFolder.newFolder("changed-pack").toPath();
        Files.writeString(pack.resolve("dimension.json"), "first");
        String first = GenerationPackFingerprint.compute(pack, GenerationPackFingerprint.CURRENT_VERSION);
        Files.writeString(pack.resolve("dimension.json"), "second");

        String second = GenerationPackFingerprint.compute(pack, GenerationPackFingerprint.CURRENT_VERSION);

        assertNotEquals(first, second);
    }

    @Test
    public void unsupportedVersionsAndSymbolicLinksFailClosed() throws Exception {
        Path pack = temporaryFolder.newFolder("unsafe-pack").toPath();
        assertThrows(IOException.class, () -> GenerationPackFingerprint.compute(pack, 3));

        Path target = temporaryFolder.newFile("outside.json").toPath();
        try {
            Files.createSymbolicLink(pack.resolve("linked.json"), target);
        } catch (IOException | UnsupportedOperationException exception) {
            Assume.assumeNoException(exception);
        }
        assertThrows(
                IOException.class,
                () -> GenerationPackFingerprint.compute(pack, GenerationPackFingerprint.CURRENT_VERSION)
        );
    }
}
