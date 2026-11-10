package art.arcane.iris.core.project;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class UnreadablePackFilesTest {
    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    private final UnreadablePackFiles files = new UnreadablePackFiles();

    private File write(String name, String content) throws IOException {
        File file = new File(temporaryFolder.getRoot(), name);
        Files.writeString(file.toPath(), content);
        return file;
    }

    @Test
    public void acceptsTrailingCommasLikeTheLoaders() throws IOException {
        File biome = write("biome.json", """
                {
                    "name": "Plains",
                    "children": ["a", "b",],
                }
                """);

        assertTrue(files.recheck(List.of(biome)).isEmpty());
    }

    @Test
    public void reportsCommentsBecauseTheLoadersCannotReadThem() throws IOException {
        File biome = write("biome.json", """
                {
                    // the loaders reject this
                    "name": "Plains"
                }
                """);

        assertEquals(1, files.recheck(List.of(biome)).size());
    }

    @Test
    public void acceptsListsAtTheTopOfAFile() throws IOException {
        assertTrue(files.recheck(List.of(write("list.json", "[1, 2]"))).isEmpty());
    }

    @Test
    public void reportsWhereASyntaxErrorIs() throws IOException {
        File biome = write("biome.json", """
                {
                    "name": "Plains",
                    "children": [
                        "a"
                        "b"
                    ]
                }
                """);

        Map<File, String> unreadable = files.recheck(List.of(biome));

        assertTrue(unreadable.get(biome), unreadable.get(biome).contains("line"));
    }

    @Test
    public void reportsAnEmptyFile() throws IOException {
        File biome = write("biome.json", "");

        assertEquals(1, files.recheck(List.of(biome)).size());
    }

    @Test
    public void reportsAFileThatIsNotAnObject() throws IOException {
        File biome = write("biome.json", "42");

        assertEquals(1, files.recheck(List.of(biome)).size());
    }

    @Test
    public void keepsHoldingAFileUntilItParses() throws IOException {
        File broken = write("broken.json", "{");
        File other = write("other.json", "{}");
        files.recheck(List.of(broken));

        assertEquals(1, files.recheck(List.of(other)).size());

        write("broken.json", "{}");
        assertTrue(files.recheck(List.of(other)).isEmpty());
    }

    @Test
    public void forgetsADeletedFile() throws IOException {
        File broken = write("broken.json", "{");
        files.recheck(List.of(broken));

        Files.delete(broken.toPath());

        assertTrue(files.recheck(List.of()).isEmpty());
    }

    @Test
    public void skipsObjectFiles() throws IOException {
        assertTrue(files.recheck(List.of(write("tree.iob", "not json"))).isEmpty());
    }
}
