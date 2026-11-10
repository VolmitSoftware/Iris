package art.arcane.iris.core.studio.scene;

import art.arcane.iris.core.modules.JsonFormsCodecs;
import art.arcane.iris.engine.procedural.ProceduralObjectCatalog;
import art.arcane.iris.engine.procedural.part.PartOutline;
import art.arcane.iris.engine.procedural.part.ProceduralObject;
import art.arcane.iris.modules.objects.ProceduralObjectScenes;
import com.google.gson.GsonBuilder;
import com.volmit.tooling.SceneCreation;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.NoSuchElementException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class SceneFilesTest {
    private static final SceneCreation CREATION = new ProceduralObjectScenes().creation().orElseThrow();

    @Rule
    public TemporaryFolder temporary = new TemporaryFolder();

    private SceneFiles files(Path pack, SceneFiles.DesktopTrash trash) {
        return new SceneFiles(pack, CREATION, trash);
    }

    @Test
    public void keysMustBePlainFolderSafeNames() {
        assertEquals(null, SceneFiles.refusalOfKey("trees/oak-2"));
        assertEquals("Give it a name", SceneFiles.refusalOfKey(" "));
        for (String key : List.of("Trees/Oak", "trees//oak", "/oak", "oak/", "../oak", "trees/../oak", "oak.json", "trees\\oak")) {
            assertTrue(key, SceneFiles.refusalOfKey(key).startsWith("Use lower case"));
        }
    }

    @Test
    public void theStarterIsATreeWithAPartToGrowFrom() {
        ProceduralObject tree = new GsonBuilder().registerTypeAdapterFactory(new JsonFormsCodecs()).create().fromJson(CREATION.starter(), ProceduralObject.class);

        assertEquals(2, PartOutline.of(tree).nodeCount());
        assertEquals(2, tree.materials().size());
    }

    @Test
    public void aNewEntryIsWrittenInTheKindsFolderFromTheStarter() throws Exception {
        Path pack = temporary.newFolder("pack").toPath();

        Path created = files(pack, file -> false).create("trees/oak");

        assertEquals(pack.resolve(ProceduralObjectCatalog.DEFINITIONS.folder()).resolve("trees/oak.json"), created);
        assertEquals(CREATION.starter(), Files.readString(created));
        try (var folder = Files.list(created.getParent())) {
            assertEquals(List.of(created), folder.toList());
        }
    }

    @Test
    public void anEntryThatExistsOrHasNoPlainKeyIsNotCreated() throws Exception {
        Path pack = temporary.newFolder("pack").toPath();
        SceneFiles files = files(pack, file -> false);
        Path created = files.create("trees/oak");
        Files.writeString(created, "{}");

        assertEquals("trees/oak already exists", assertThrows(IllegalArgumentException.class, () -> files.create("trees/oak")).getMessage());
        assertEquals("{}", Files.readString(created));
        assertThrows(IllegalArgumentException.class, () -> files.create("../escape"));
        assertFalse(Files.exists(pack.resolveSibling("escape.json")));
    }

    @Test
    public void usesAreTheJsonFilesNamingTheEntryOutsideItsOwnAndGeneratedFolders() throws Exception {
        Path pack = temporary.newFolder("pack").toPath();
        SceneFiles files = files(pack, file -> false);
        files.create("trees/oak");
        Files.createDirectories(pack.resolve("biomes/temperate"));
        Files.writeString(pack.resolve("biomes/temperate/forest.json"), "{\"objects\": [\"procedural/trees/oak\"]}");
        Files.writeString(pack.resolve("biomes/plains.json"), "{\"objects\": [\"procedural/trees/oak-tall\"]}");
        Files.writeString(pack.resolve("biomes/notes.txt"), "procedural/trees/oak");
        Files.createDirectories(pack.resolve(".iris/schema"));
        Files.writeString(pack.resolve(".iris/schema/biome.json"), "{\"enum\": [\"procedural/trees/oak\"]}");

        assertEquals(List.of("biomes/temperate/forest.json"), files.usesOf("trees/oak"));
    }

    @Test
    public void aDeletedEntryGoesToThePacksTrashWhenTheDesktopHasNone() throws Exception {
        Path pack = temporary.newFolder("pack").toPath();
        SceneFiles files = files(pack, file -> false);
        Path created = files.create("trees/oak");

        assertEquals(".iris/trash", files.moveToTrash("trees/oak"));

        assertFalse(Files.exists(created));
        try (var trashed = Files.list(pack.resolve(".iris/trash"))) {
            assertTrue(trashed.anyMatch(file -> file.getFileName().toString().startsWith("trees_oak-")));
        }
    }

    @Test
    public void aDeletedEntryTheDesktopTookIsLeftToIt() throws Exception {
        Path pack = temporary.newFolder("pack").toPath();
        SceneFiles files = files(pack, file -> {
            try {
                Files.delete(file);
            } catch (java.io.IOException failed) {
                throw new java.io.UncheckedIOException(failed);
            }
            return true;
        });
        Path created = files.create("trees/oak");

        assertEquals("the trash", files.moveToTrash("trees/oak"));

        assertFalse(Files.exists(created));
        assertFalse(Files.exists(pack.resolve(".iris/trash")));
    }

    @Test
    public void anEntryWithoutAFileCannotBeDeleted() throws Exception {
        Path pack = temporary.newFolder("pack").toPath();

        assertThrows(NoSuchElementException.class, () -> files(pack, file -> false).moveToTrash("trees/oak"));
    }
}
