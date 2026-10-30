package art.arcane.iris.pack.datapack.export;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class AtomicDatapackPublisherTest {
    private static final String PREFIX = ".iris-worldgen-export-";

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void stagingDirectoryUsesCallerPrefixAndIsRemoved() throws IOException {
        Path parent = temporaryFolder.getRoot().toPath();
        Path destination = parent.resolve("worldgen");
        byte[] metadata = "{}\n".getBytes(StandardCharsets.UTF_8);

        AtomicDatapackPublisher.Publication publication = new AtomicDatapackPublisher().publish(
                Map.of("pack.mcmeta", metadata), destination, DatapackExportFormat.DIRECTORY, false, PREFIX);

        assertTrue(publication.cleanupWarnings().isEmpty());
        assertArrayEquals(metadata, Files.readAllBytes(destination.resolve("pack.mcmeta")));
        assertEquals(List.of(), entriesWithPrefix(parent));
    }

    @Test
    public void existingDestinationWithoutReplaceIsLeftUntouched() throws IOException {
        Path parent = temporaryFolder.getRoot().toPath();
        Path destination = parent.resolve("worldgen.zip");
        byte[] original = "original".getBytes(StandardCharsets.UTF_8);
        Files.write(destination, original);

        assertThrows(IOException.class, () -> new AtomicDatapackPublisher().publish(
                Map.of("pack.mcmeta", new byte[]{1}), destination, DatapackExportFormat.ZIP, false, PREFIX));

        assertArrayEquals(original, Files.readAllBytes(destination));
        assertEquals(List.of(), entriesWithPrefix(parent));
    }

    private static List<String> entriesWithPrefix(Path parent) throws IOException {
        try (Stream<Path> entries = Files.list(parent)) {
            return entries.map(path -> path.getFileName().toString())
                    .filter(name -> name.startsWith(PREFIX))
                    .toList();
        }
    }
}
