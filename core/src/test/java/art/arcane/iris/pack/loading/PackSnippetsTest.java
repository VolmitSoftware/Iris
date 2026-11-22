package art.arcane.iris.pack.loading;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;

import static org.junit.Assert.assertEquals;

public class PackSnippetsTest {
    @Rule
    public final TemporaryFolder temporary = new TemporaryFolder();

    @Test
    public void foldersOfATypeAreDiscoveredOncePerInstance() throws IOException {
        File pack = temporary.newFolder("pack");
        createFolder(pack, "snippet/iris/spawn/rate");
        PackSnippets snippets = new PackSnippets(pack);
        assertEquals(List.of("snippet/iris/spawn/rate"), snippets.folders("rate").namespaced());

        createFolder(pack, "snippet/iris/creatures/rate");

        assertEquals(List.of("snippet/iris/spawn/rate"), snippets.folders("rate").namespaced());
        assertEquals(List.of("snippet/iris/creatures/rate", "snippet/iris/spawn/rate"),
                new PackSnippets(pack).folders("rate").namespaced());
    }

    private static void createFolder(File pack, String relative) throws IOException {
        Files.createDirectories(pack.toPath().resolve(relative));
    }
}
