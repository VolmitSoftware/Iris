package art.arcane.iris.studio.bridge;

import art.arcane.iris.generation.procedural.part.PartBlob;
import art.arcane.iris.generation.procedural.part.ProceduralMaterial;
import art.arcane.iris.generation.procedural.part.ProceduralObject;
import art.arcane.iris.generation.runtime.Engine;
import art.arcane.iris.pack.loading.IrisData;
import art.arcane.iris.pack.loading.ResourceLoader;
import art.arcane.iris.studio.scene.JsonPath;
import com.google.gson.Gson;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class StudioEditorsTest {
    private static final String VIEW = "proceduralParts";
    private static final String KEY = "rocks/boulder";
    private static final String BOULDER = """
            {
              // A lone boulder
              "materials": [{"role": "rock", "block": "minecraft:stone"}],
              "parts": [{"role": "rock", "blob": {"radius": [2, 4]}}]
            }""";
    private static final SceneFiles.DesktopTrash NO_DESKTOP = file -> false;

    @Rule
    public final TemporaryFolder folder = new TemporaryFolder();

    private Path pack;
    private Path boulder;
    private Engine engine;

    @Before
    public void writePack() throws IOException {
        pack = folder.newFolder("pack").toPath();
        boulder = pack.resolve("procedural/rocks/boulder.json");
        Files.createDirectories(boulder.getParent());
        Files.writeString(boulder, BOULDER, StandardCharsets.UTF_8);
        engine = studioEngine(true);
    }

    @Test
    public void anEntryIsReadAsNodesWithTheSettingsTypesTheyShow() throws IOException {
        StudioEditors.EditorState state = editors().read(VIEW, KEY);

        assertEquals("procedural/rocks/boulder.json", state.file());
        assertEquals(BOULDER, state.text());
        assertNull(state.problem());
        assertNull(state.locked());
        assertEquals(List.of("Object", "Material"), state.roots().stream().map(StudioEditors.NodeWire::kind).toList());
        StudioEditors.NodeWire blob = state.roots().get(0).children().get(0).node();
        assertEquals(List.of("parts", 0), blob.path());
        assertEquals(0, blob.partId());
        assertTrue(blob.duplicable());
        assertTrue(state.types().containsKey(ProceduralObject.class.getName()));
        assertEquals(Map.of("kind", "any"), optionOf(state, PartBlob.class, "radius").kind());
        assertEquals("BLOCK_TYPE", optionOf(state, ProceduralMaterial.class, "block").kind().get("reference"));
    }

    @Test
    public void anEditChangesOnlyTheValueItSets() throws IOException {
        StudioEditors editors = editors();
        String revision = editors.read(VIEW, KEY).revision();

        StudioEditors.EditorState edited = editors.edit(VIEW, KEY, revision, new GraphEdit.Set(JsonPath.ROOT, "variants", "3"));

        String expected = BOULDER.replace("\"parts\": [{\"role\": \"rock\", \"blob\": {\"radius\": [2, 4]}}]",
                "\"parts\": [{\"role\": \"rock\", \"blob\": {\"radius\": [2, 4]}}],\n  \"variants\": 3");
        assertEquals(expected, Files.readString(boulder, StandardCharsets.UTF_8));
        assertEquals(expected, edited.text());
        assertNotEquals(revision, edited.revision());
    }

    @Test
    public void anEditOnTextThatChangedSinceIsRefused() throws IOException {
        StudioEditors editors = editors();
        String revision = editors.read(VIEW, KEY).revision();
        Files.writeString(boulder, BOULDER.replace("A lone boulder", "Edited elsewhere"), StandardCharsets.UTF_8);

        assertThrows(StudioEditors.StaleEditException.class, () -> editors.edit(VIEW, KEY, revision, new GraphEdit.Unset(JsonPath.ROOT, "parts")));
    }

    @Test
    public void undoingWritesTheWholeFileBack() throws IOException {
        StudioEditors editors = editors();
        String revision = editors.edit(VIEW, KEY, editors.read(VIEW, KEY).revision(), new GraphEdit.Unset(JsonPath.ROOT, "materials")).revision();

        editors.write(VIEW, KEY, revision, BOULDER);

        assertEquals(BOULDER, Files.readString(boulder, StandardCharsets.UTF_8));
    }

    @Test
    public void outsideAStudioWorldTheFileIsShownButNotEdited() throws IOException {
        engine = studioEngine(false);
        StudioEditors editors = editors();
        String revision = editors.read(VIEW, KEY).revision();

        assertNotNull(editors.read(VIEW, KEY).locked());
        assertThrows(StudioEditors.EditsLockedException.class, () -> editors.edit(VIEW, KEY, revision, new GraphEdit.Unset(JsonPath.ROOT, "parts")));
        assertThrows(StudioEditors.EditsLockedException.class, () -> editors.create(VIEW, "rocks/pebble"));
    }

    @Test
    public void aFileThatIsNoLongerJsonIsShownWithWhy() throws IOException {
        Files.writeString(boulder, "{\"parts\": [", StandardCharsets.UTF_8);

        StudioEditors.EditorState state = editors().read(VIEW, KEY);

        assertNull(state.roots());
        assertNotNull(state.problem());
    }

    @Test
    public void aNewEntryStartsFromTheStarterAndIsNotWrittenTwice() throws IOException {
        StudioEditors editors = editors();

        editors.create(VIEW, "rocks/pebble");

        Path pebble = pack.resolve("procedural/rocks/pebble.json");
        assertTrue(Files.readString(pebble, StandardCharsets.UTF_8).contains("\"blob\":{\"radius\":[2,4],\"height\":[2,3]}"));
        assertThrows(IllegalArgumentException.class, () -> editors.create(VIEW, "rocks/pebble"));
        assertThrows(IllegalArgumentException.class, () -> editors.create(VIEW, "Rocks/../escape"));
    }

    @Test
    public void theFilesThatPlaceAnEntryAreItsUses() throws IOException {
        Path forest = pack.resolve("biomes/forest.json");
        Files.createDirectories(forest.getParent());
        Files.writeString(forest, "{\"objects\": [{\"place\": [\"procedural/rocks/boulder\"]}]}", StandardCharsets.UTF_8);
        Path schema = pack.resolve(".iris/schema/biomes.json");
        Files.createDirectories(schema.getParent());
        Files.writeString(schema, "{\"enum\": [\"procedural/rocks/boulder\"]}", StandardCharsets.UTF_8);

        assertEquals(List.of("biomes/forest.json"), editors().usesOf(VIEW, KEY));
    }

    @Test
    public void aDeletedEntryGoesToThePacksTrashWithoutADesktop() throws IOException {
        String trashedTo = editors().delete(VIEW, KEY);

        assertEquals(".iris/trash", trashedTo);
        assertFalse(Files.exists(boulder));
        try (var trashed = Files.list(pack.resolve(".iris/trash"))) {
            assertEquals(1, trashed.count());
        }
    }

    private StudioEditors editors() {
        return new StudioEditors(new StudioScenes(), engine, NO_DESKTOP);
    }

    private static SettingsTypes.OptionWire optionOf(StudioEditors.EditorState state, Class<?> type, String name) {
        return state.types().get(type.getName()).options().stream().filter(option -> option.name().equals(name)).findFirst().orElseThrow();
    }

    @SuppressWarnings("unchecked")
    private Engine studioEngine(boolean studio) {
        ResourceLoader<ProceduralObject> definitions = mock(ResourceLoader.class);
        when(definitions.getPossibleKeys()).thenReturn(new String[]{KEY});
        when(definitions.findFile(anyString())).thenAnswer(asked -> pack.resolve("procedural").resolve(asked.getArgument(0) + ".json").toFile());
        IrisData data = mock(IrisData.class);
        when(data.getDataFolder()).thenReturn(pack.toFile());
        when(data.getProceduralObjectLoader()).thenReturn(definitions);
        when(data.getGson()).thenReturn(new Gson());
        Engine studioEngine = mock(Engine.class);
        when(studioEngine.getData()).thenReturn(data);
        when(studioEngine.isStudio()).thenReturn(studio);
        return studioEngine;
    }
}
