package art.arcane.iris.core.pack;

import art.arcane.volmlib.util.json.JSONArray;
import art.arcane.volmlib.util.json.JSONObject;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class PackValidatorStructureTerrainBackendTest {
    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void rejectsNativeOnlyTerrainModesForEditablePlacementsAcrossEveryHost() throws Exception {
        for (String mode : List.of("VACUUM", "ENCASE")) {
            String folderName = "editable-" + mode.toLowerCase(Locale.ROOT);
            File pack = temporaryFolder.newFolder(folderName);
            writePlacement(pack, "dimensions/main.json", "dimension-" + folderName,
                    "dimension_city", false, mode);
            writePlacement(pack, "regions/forest.json", "region-" + folderName,
                    "forest_tower", false, mode);
            writePlacement(pack, "biomes/plains.json", "biome-" + folderName,
                    "plains_farm", false, mode);
            List<String> errors = new ArrayList<>();

            PackStructurePlacementValidator.validateStructurePlacements(
                    pack, Set.of("dimension_city", "forest_tower", "plains_farm"), false, errors);

            String suffix = ".terrain.mode " + mode + " cannot target editable Iris structures; "
                    + "use nativeStructures or importedStructures.adjustments for native terrain preparation.";
            assertEquals(List.of(
                    "Dimension 'main' structures[0]" + suffix,
                    "Region 'forest' structures[0]" + suffix,
                    "Biome 'plains' structures[0]" + suffix
            ), errors);
        }
    }

    @Test
    public void acceptsNativeOnlyTerrainModesForNativeStructurePlacements() throws Exception {
        for (String mode : List.of("VACUUM", "ENCASE")) {
            File pack = temporaryFolder.newFolder("native-" + mode.toLowerCase(Locale.ROOT));
            writePlacement(pack, "dimensions/main.json", "native-" + mode,
                    "minecraft:village_plains", true, mode);
            List<String> errors = new ArrayList<>();

            PackStructurePlacementValidator.validateStructurePlacements(pack, Set.of(), false, errors);

            assertTrue(mode + ": " + errors, errors.isEmpty());
        }
    }

    @Test
    public void acceptsSupportedTerrainModesForEditablePlacements() throws Exception {
        for (String mode : List.of("SOURCE", "PRESERVE", "BORE", "FORCE_CARVE")) {
            File pack = temporaryFolder.newFolder("editable-" + mode.toLowerCase(Locale.ROOT));
            writePlacement(pack, "dimensions/main.json", "editable-" + mode,
                    "dimension_city", false, mode);
            List<String> errors = new ArrayList<>();

            PackStructurePlacementValidator.validateStructurePlacements(
                    pack, Set.of("dimension_city"), false, errors);

            assertTrue(mode + ": " + errors, errors.isEmpty());
        }
    }

    @Test
    public void acceptsNativeOnlyTerrainModesForImportedStructureAdjustments() {
        for (String mode : List.of("VACUUM", "ENCASE")) {
            JSONObject policy = new JSONObject().put("adjustments", new JSONArray().put(
                    new JSONObject()
                            .put("match", new JSONArray().put("towns_and_towers:"))
                            .put("terrain", new JSONObject().put("mode", mode))));
            List<String> errors = new ArrayList<>();

            PackDimensionValidator.validateImportedStructurePolicy(
                    "overworld", new JSONObject().put("importedStructures", policy),
                    errors, new ArrayList<>());

            assertTrue(mode + ": " + errors, errors.isEmpty());
        }
    }

    private void writePlacement(File pack, String relativePath, String placementId,
                                String structureKey, boolean nativeStructure,
                                String terrainMode) throws Exception {
        String source = nativeStructure
                ? "\"nativeStructures\":[{\"structure\":\"" + structureKey + "\"}]"
                : "\"structures\":[\"" + structureKey + "\"]";
        write(pack, relativePath, "{\"structures\":[{\"placementId\":\"" + placementId
                + "\"," + source + ",\"terrain\":{\"mode\":\"" + terrainMode + "\"}}]}");
    }

    private void write(File root, String relativePath, String content) throws Exception {
        Path path = root.toPath().resolve(relativePath);
        Files.createDirectories(path.getParent());
        Files.writeString(path, content, StandardCharsets.UTF_8);
    }
}
