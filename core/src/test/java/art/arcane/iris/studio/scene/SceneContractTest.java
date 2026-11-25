package art.arcane.iris.studio.scene;

import art.arcane.iris.generation.runtime.Engine;
import art.arcane.iris.pack.schema.annotation.MaxNumber;
import art.arcane.iris.pack.schema.annotation.MinNumber;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import org.junit.Test;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.Set;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class SceneContractTest {
    private static final Gson GSON = new Gson();

    @Test
    public void everyRegisteredViewIsFoundByItsOwnId() {
        for (SceneView view : SceneViews.all()) {
            assertEquals(view, SceneViews.byId(view.id()));
        }
    }

    @Test
    public void theViewsTheStudioAlreadyShowsKeepTheirIds() {
        List<String> ids = SceneViews.all().stream().map(SceneView::id).toList();

        assertEquals(List.of("treeProfiles", "formationProfiles", "fluidProfiles"), ids);
    }

    @Test
    public void anUnknownViewIsReportedAsMissing() {
        assertThrows(NoSuchElementException.class, () -> SceneViews.byId("nothing"));
    }

    @Test
    public void twoViewsOfOneIdAreRefused() {
        assertThrows(IllegalStateException.class, () -> SceneViews.distinct(List.of(new BlocksOnlyView(), new BlocksOnlyView())));
    }

    @Test
    public void theStudiosOwnViewsFollowTheFeatures() {
        SceneView studioView = new BlocksOnlyView();

        List<SceneView> views = SceneViews.withStudioViews(List.of(studioView));

        assertEquals(SceneViews.all().size() + 1, views.size());
        assertEquals(studioView, views.get(views.size() - 1));
    }

    @Test
    public void aStudioViewNamedLikeAFeaturesIsRefused() {
        SceneView clashing = SceneViews.all().get(0);

        assertThrows(IllegalStateException.class, () -> SceneViews.withStudioViews(List.of(clashing)));
    }

    @Test
    public void aViewOfBlocksAloneOffersNothingElse() {
        SceneView view = new BlocksOnlyView();

        assertFalse(view.hasForm());
        assertFalse(view.isBaked());
        assertFalse(view.tracesParts());
        assertEquals(null, view.sizes());
        assertThrows(NoSuchElementException.class, () -> view.form(null, "one", Map.of()));
        assertThrows(UnsupportedOperationException.class, () -> view.timeBake(null, "one"));
    }

    @Test
    public void aSceneIsEncodedFromItsLowestCornerWithoutAir() {
        SceneBlocks scene = new SceneBlocks();
        scene.add(10, 5, 10, "minecraft:stone");
        scene.add(11, 5, 10, "minecraft:air");
        scene.add(12, 6, 10, "minecraft:dirt");
        scene.add(12, 7, 10, "minecraft:stone");

        ByteBuffer wire = ByteBuffer.wrap(scene.encode(GSON)).order(ByteOrder.LITTLE_ENDIAN);
        int headerLength = wire.getInt();
        byte[] headerBytes = new byte[headerLength];
        wire.get(headerBytes);
        JsonObject header = GSON.fromJson(new String(headerBytes, StandardCharsets.UTF_8), JsonObject.class);
        wire.position((Integer.BYTES + headerLength + 3) / 4 * 4);
        int[] blocks = new int[wire.remaining() / Integer.BYTES];
        wire.asIntBuffer().get(blocks);

        assertEquals(3, scene.count());
        assertEquals(3, header.get("blockCount").getAsInt());
        assertEquals("[\"minecraft:stone\",\"minecraft:dirt\"]", header.get("palette").toString());
        assertEquals("[3,3,1]", header.get("max").toString());
        assertArrayEquals(new int[]{0, 0, 0, 0, 2, 1, 0, 1, 2, 2, 0, 0}, blocks);
    }

    @Test
    public void anEmptySceneIsOneBlockAcross() {
        JsonObject header = headerOf(new SceneBlocks());

        assertEquals("[1,1,1]", header.get("max").toString());
        assertEquals(0, header.get("blockCount").getAsInt());
    }

    @Test
    public void partsFollowTheBlocksThatWereKept() {
        SceneBlocks scene = new SceneBlocks();
        scene.add(0, 0, 0, "minecraft:stone", 2);
        scene.add(0, 1, 0, "minecraft:air", 5);
        scene.add(0, 2, 0, "minecraft:stone");
        scene.add(0, 3, 0, "minecraft:dirt", 0);

        assertArrayEquals(new int[]{2, SceneBlocks.NO_PART, 0}, scene.parts());
    }

    @Test
    public void aFormListsPlainSettingsWithTheirLimitsAndDefaults() {
        Settings settings = new Settings();
        settings.width = 9;

        SceneForm form = SceneForm.of("Settings", settings, Set.of("hiddenSetting"), Map.of("width", "9"), List.of("A note"));

        assertEquals(List.of("width", "open", "scale", "shape"), form.fields().stream().map(SceneForm.Field::name).toList());
        SceneForm.Field width = form.fields().get(0);
        assertEquals("integer", width.kind());
        assertEquals(9, width.value());
        assertEquals(4, width.defaultValue());
        assertEquals(Double.valueOf(1), width.minimum());
        assertEquals(Double.valueOf(32), width.maximum());
        assertTrue(width.changed());
        SceneForm.Field shape = form.fields().get(3);
        assertEquals("enum", shape.kind());
        assertEquals("ROUND", shape.value());
        assertEquals(List.of("ROUND", "FLAT"), shape.choices());
        assertFalse(shape.changed());
    }

    @Test
    public void typedValuesAreSetOnACopy() {
        Settings settings = new Settings();

        Settings typed = SceneForm.withOverrides(settings, Map.of("width", "12", "open", "true", "scale", "0.5", "shape", "FLAT"));

        assertEquals(12, typed.width);
        assertTrue(typed.open);
        assertEquals(0.5, typed.scale, 0);
        assertEquals(Shape.FLAT, typed.shape);
        assertEquals(4, settings.width);
        assertEquals(Shape.ROUND, settings.shape);
    }

    @Test
    public void aTypedValueThatDoesNotReadIsIgnored() {
        Settings typed = SceneForm.withOverrides(new Settings(), Map.of("width", "wide", "shape", "CUBE"));

        assertEquals(4, typed.width);
        assertEquals(Shape.ROUND, typed.shape);
    }

    @Test
    public void aPresetEntryIsNamedReadably() {
        SceneEntry entry = SceneEntry.preset(Shape.ROUND, 3);

        assertEquals(new SceneEntry("ROUND", "Round", "Presets", 3), entry);
    }

    @Test
    public void theOpeningSizeIsOneOfTheSizes() {
        assertThrows(IllegalArgumentException.class, () -> new SceneSizes(List.of(16, 32), 64));
    }

    @Test
    public void aPathIsBuiltStepByStepFromTheRoot() {
        JsonPath stem = JsonPath.ROOT.member("parts").element(0).member("stem");

        assertEquals("parts.0.stem", stem.toString());
        assertEquals(JsonPath.ROOT.member("parts").element(0), stem.parent());
        assertTrue(stem.startsWith(JsonPath.ROOT.member("parts")));
        assertFalse(stem.isElement());
        assertTrue(stem.parent().isElement());
        assertThrows(IllegalStateException.class, JsonPath.ROOT::parent);
    }

    @Test
    public void onlyANodeInAnArrayCanMove() {
        GraphNode.Edits movable = new GraphNode.Edits(Optional.empty(), true, List.of());

        assertThrows(IllegalArgumentException.class, () -> nodeAt(JsonPath.ROOT, movable));
        assertEquals(movable, nodeAt(JsonPath.ROOT.member("parts").element(1), movable).edits());
    }

    @Test
    public void aNodeThatCanBeAddedStartsAsJson() {
        JsonPath parts = JsonPath.ROOT.member("parts");

        assertThrows(IllegalArgumentException.class, () -> new GraphNode.Addition("Stem", parts, "{stem:"));
        assertEquals("{\"stem\":{}}", new GraphNode.Addition("Stem", parts, "{\"stem\":{}}").literal());
    }

    private static GraphNode nodeAt(JsonPath path, GraphNode.Edits edits) {
        return new GraphNode("Stem", "Stem", path, SceneBlocks.NO_PART, List.of(), List.of(), List.of(), edits);
    }

    private static JsonObject headerOf(SceneBlocks scene) {
        ByteBuffer wire = ByteBuffer.wrap(scene.encode(GSON)).order(ByteOrder.LITTLE_ENDIAN);
        byte[] headerBytes = new byte[wire.getInt()];
        wire.get(headerBytes);
        return GSON.fromJson(new String(headerBytes, StandardCharsets.UTF_8), JsonObject.class);
    }

    private enum Shape {
        ROUND, FLAT
    }

    private static final class Settings {
        @MinNumber(1)
        @MaxNumber(32)
        private int width = 4;
        private boolean open = false;
        private double scale = 1;
        private Shape shape = Shape.ROUND;
        private int hiddenSetting = 1;
        private List<String> nested = List.of();
    }

    private static final class BlocksOnlyView implements SceneView {
        @Override
        public String id() {
            return "blocksOnly";
        }

        @Override
        public String label() {
            return "Blocks only";
        }

        @Override
        public String description() {
            return "A view with nothing but scenes";
        }

        @Override
        public List<SceneEntry> entries(Engine engine) {
            return List.of(new SceneEntry("one", "One", "", 1));
        }

        @Override
        public SceneBlocks scene(Engine engine, SceneRequest request) {
            return new SceneBlocks();
        }
    }
}
