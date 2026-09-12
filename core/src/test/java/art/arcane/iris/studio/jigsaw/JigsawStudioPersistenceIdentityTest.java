package art.arcane.iris.studio.jigsaw;

import art.arcane.iris.structure.authoring.StructureHash;
import art.arcane.iris.structure.jigsaw.IrisJigsawThemeSet;
import art.arcane.iris.testsupport.DurabilityMode;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Stream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class JigsawStudioPersistenceIdentityTest {
    @ClassRule
    public static final DurabilityMode DURABILITY = DurabilityMode.relaxed();

    private static final String STRUCTURE_KEY = "studio/identity";
    private static final String PIECE_POOL_KEY = "studio/identity/pieces";
    private static final String RECORDED_AT_FIELD = "recordedAtEpochMilli";
    private static final String SCRIPTED_SESSION_DIGEST =
            "318d9338a63a44063c85e59a9e74f869473d6e0f15c362536172f7723d960faa";

    @Rule
    public final TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void scriptedEditingSessionWritesTheSameProjectAndHistoryBytes() throws Exception {
        assertEquals(SCRIPTED_SESSION_DIGEST, digestOf(runScriptedSession("session-one")));
    }

    @Test
    public void repeatedScriptedEditingSessionsAreByteIdentical() throws Exception {
        assertEquals(digestOf(runScriptedSession("session-two")), digestOf(runScriptedSession("session-three")));
    }

    private Path runScriptedSession(String folderName) throws Exception {
        Path packRoot = temporaryFolder.newFolder(folderName).toPath();
        assertTrue(JigsawStudioProjectCreator.create(
                packRoot,
                new JigsawStudioProjectCreator.Options(
                        STRUCTURE_KEY,
                        JigsawStudioMode.PLANAR_JIGSAW,
                        JigsawStudioCompatibilityTarget.IRIS_EXTENDED,
                        new JigsawStudioCellDimensions(16, 16, 16))).successful());
        JigsawStudioHistoryStore history = new JigsawStudioHistoryStore(packRoot, STRUCTURE_KEY);

        history.append(history.snapshotCurrent(STRUCTURE_KEY + "/end"));
        assertTrue(JigsawStudioPoolEditor.updateWeightAtIndex(
                packRoot, STRUCTURE_KEY, PIECE_POOL_KEY, 0, STRUCTURE_KEY + "/end", 4).changed());
        assertTrue(JigsawStudioPoolEditor.updateChanceAtIndex(
                packRoot, STRUCTURE_KEY, PIECE_POOL_KEY, 0, STRUCTURE_KEY + "/end", 0.35D).changed());

        history.append(history.snapshotCurrent(STRUCTURE_KEY + "/end"));
        JigsawStudioGraphEditor.VariantFamilyCreation family = JigsawStudioGraphEditor.duplicateActiveFamily(
                packRoot, STRUCTURE_KEY, planarSources(), "variant-2");
        assertTrue(family.writeResult().successful());

        assertTrue(JigsawStudioGraphEditor.updatePieceThemes(
                packRoot, STRUCTURE_KEY, STRUCTURE_KEY + "/blank", List.of()).successful());
        assertTrue(JigsawStudioGraphEditor.updatePieceDisplayName(
                packRoot, STRUCTURE_KEY, STRUCTURE_KEY + "/end", "Sealed End").successful());
        assertTrue(JigsawStudioGraphEditor.updateRotatable(
                packRoot, STRUCTURE_KEY, STRUCTURE_KEY + "/end", false).successful());
        assertTrue(JigsawStudioGraphEditor.updatePieceRules(
                packRoot,
                STRUCTURE_KEY,
                STRUCTURE_KEY + "/end",
                new JigsawStudioPieceRules(0, 30, 0, 0, true)).successful());

        assertTrue(JigsawStudioStructureEditor.updateThemeSets(
                packRoot,
                STRUCTURE_KEY,
                List.of(new IrisJigsawThemeSet("variant-1", 2), new IrisJigsawThemeSet("variant-2", 3)))
                .successful());
        assertTrue(JigsawStudioStructureEditor.updateLimits(packRoot, STRUCTURE_KEY, 9, 5).successful());

        history.append(history.snapshotCurrent(STRUCTURE_KEY + "/end"));
        assertTrue(JigsawStudioGraphEditor.createPool(
                packRoot, STRUCTURE_KEY, STRUCTURE_KEY + "/spares", PIECE_POOL_KEY).successful());
        assertTrue(JigsawStudioGraphEditor.duplicatePiece(
                packRoot,
                STRUCTURE_KEY,
                STRUCTURE_KEY + "/end",
                STRUCTURE_KEY + "/variants/end/variant-3").successful());
        assertTrue(JigsawStudioPoolEditor.addPiece(
                packRoot,
                STRUCTURE_KEY,
                STRUCTURE_KEY + "/spares",
                STRUCTURE_KEY + "/variants/end/variant-3",
                6).changed());
        assertTrue(JigsawStudioPoolEditor.updateFallback(
                packRoot, STRUCTURE_KEY, STRUCTURE_KEY + "/spares", "").changed());

        history.append(history.snapshotCurrent(STRUCTURE_KEY + "/end"));
        JigsawStudioCellDimensions endCapacity = new JigsawStudioCellDimensions(14, 12, 14);
        for (String endVariant : List.of(
                STRUCTURE_KEY + "/end",
                family.pieceKeysByWorkcell().get(JigsawPlanarArchetype.END.stableId()),
                STRUCTURE_KEY + "/variants/end/variant-3")) {
            assertTrue(JigsawStudioGraphEditor.resizePieceObject(
                    packRoot, STRUCTURE_KEY, endVariant, endCapacity).writeResult().successful());
        }
        assertTrue(JigsawStudioGraphEditor.updatePlanarWorkcellCapacity(
                packRoot, STRUCTURE_KEY, JigsawPlanarArchetype.END, endCapacity)
                .writeResult().successful());

        history.append(history.snapshotCurrent(STRUCTURE_KEY + "/end"));
        assertTrue(JigsawStudioGraphEditor.deletePieceVariant(
                packRoot,
                STRUCTURE_KEY,
                family.pieceKeysByWorkcell().get(JigsawPlanarArchetype.CORNER.stableId()))
                .writeResult().successful());

        JigsawStudioHistoryStore.UndoResult undo = history.undoLatest();
        assertTrue(undo.available());
        assertTrue(undo.successful());
        return packRoot;
    }

    private static Map<String, String> planarSources() {
        Map<String, String> sources = new LinkedHashMap<>();
        for (JigsawPlanarArchetype archetype : JigsawPlanarArchetype.values()) {
            sources.put(archetype.stableId(), STRUCTURE_KEY + "/" + archetype.name().toLowerCase(Locale.ROOT));
        }
        return sources;
    }

    private static String digestOf(Path packRoot) throws IOException {
        List<String> entries = new ArrayList<>();
        try (Stream<Path> files = Files.walk(packRoot)) {
            for (Path file : files.toList()) {
                if (!Files.isRegularFile(file)) {
                    continue;
                }
                String relative = packRoot.relativize(file).toString().replace('\\', '/');
                entries.add(relative + ' ' + StructureHash.sha256(stableContent(file, relative)));
            }
        }
        Collections.sort(entries);
        return StructureHash.sha256(String.join("\n", entries).getBytes(StandardCharsets.UTF_8));
    }

    private static byte[] stableContent(Path file, String relative) throws IOException {
        byte[] content = Files.readAllBytes(file);
        if (!relative.startsWith(".iris/jigsaw-history/")) {
            return content;
        }
        JsonElement document = JsonParser.parseString(new String(content, StandardCharsets.UTF_8));
        return canonicalJson(document).getBytes(StandardCharsets.UTF_8);
    }

    private static String canonicalJson(JsonElement element) {
        if (element.isJsonObject()) {
            StringBuilder builder = new StringBuilder("{");
            TreeMap<String, JsonElement> members = new TreeMap<>();
            for (Map.Entry<String, JsonElement> member : element.getAsJsonObject().entrySet()) {
                members.put(member.getKey(), member.getValue());
            }
            boolean first = true;
            for (Map.Entry<String, JsonElement> member : members.entrySet()) {
                if (!first) {
                    builder.append(',');
                }
                first = false;
                builder.append('"').append(member.getKey()).append("\":");
                if (RECORDED_AT_FIELD.equals(member.getKey())) {
                    builder.append('0');
                } else {
                    builder.append(canonicalJson(member.getValue()));
                }
            }
            return builder.append('}').toString();
        }
        if (element.isJsonArray()) {
            StringBuilder builder = new StringBuilder("[");
            boolean first = true;
            for (JsonElement entry : element.getAsJsonArray()) {
                if (!first) {
                    builder.append(',');
                }
                first = false;
                builder.append(canonicalJson(entry));
            }
            return builder.append(']').toString();
        }
        return element.toString();
    }
}
