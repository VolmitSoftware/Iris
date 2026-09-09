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
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class PackValidatorStructureTerrainBackendTest {
    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void rejectsNativeAnchorsInNestedResourcesAcrossEveryHost() throws Exception {
        File pack = temporaryFolder.newFolder("native-anchors");
        List<String> expected = new ArrayList<>();
        for (String folder : List.of("dimensions", "regions", "biomes")) {
            JSONArray placements = new JSONArray();
            for (String anchor : List.of("SURFACE", "HEIGHT_BAND", "CAVE_FLOOR", "CAVE_CEILING")) {
                placements.put(new JSONObject()
                        .put("placementId", folder + "-" + anchor)
                        .put("anchor", anchor)
                        .put("nativeStructures", new JSONArray().put(
                                new JSONObject().put("structure", "minecraft:village_plains"))));
                String host = switch (folder) {
                    case "dimensions" -> "Dimension";
                    case "regions" -> "Region";
                    default -> "Biome";
                };
                expected.add(host + " 'nested/host' structures[" + (placements.length() - 1)
                        + "].anchor must be omitted, null, or LEGACY for nativeStructures.");
            }
            write(pack, folder + "/nested/host.json", new JSONObject().put("structures", placements).toString());
        }
        List<String> errors = new ArrayList<>();

        PackStructurePlacementValidator.validateStructurePlacements(pack, Set.of(), false, errors);

        assertEquals(expected, errors);
        PackValidationResult result = PackValidator.validateForPackaging(pack);
        assertFalse(result.isLoadable());
        assertTrue(result.getBlockingErrors().toString(), result.getBlockingErrors().containsAll(expected));
    }

    @Test
    public void rejectsMixedAndEmptyBackendsAcrossEveryHost() throws Exception {
        File pack = temporaryFolder.newFolder("invalid-backends");
        List<String> expected = new ArrayList<>();
        for (String folder : List.of("dimensions", "regions", "biomes")) {
            JSONArray placements = new JSONArray()
                    .put(new JSONObject().put("placementId", folder + "-mixed")
                            .put("structures", new JSONArray().put("tower"))
                            .put("nativeStructures", new JSONArray().put(
                                    new JSONObject().put("structure", "minecraft:village_plains"))))
                    .put(new JSONObject().put("placementId", folder + "-empty")
                            .put("structures", new JSONArray()).put("nativeStructures", new JSONArray()));
            write(pack, folder + "/host.json", new JSONObject().put("structures", placements).toString());
            String host = switch (folder) {
                case "dimensions" -> "Dimension";
                case "regions" -> "Region";
                default -> "Biome";
            };
            for (int index = 0; index < 2; index++) {
                expected.add(host + " 'host' structures[" + index
                        + "] must declare exactly one non-empty backend: structures or nativeStructures.");
            }
        }
        List<String> errors = new ArrayList<>();

        PackStructurePlacementValidator.validateStructurePlacements(pack, Set.of("tower"), false, errors);

        assertEquals(expected, errors);
    }

    @Test
    public void acceptsNativeDefaultAndLegacyAnchorsAndEditableSurfaceAnchor() throws Exception {
        File pack = temporaryFolder.newFolder("supported-anchors");
        JSONArray placements = new JSONArray();
        for (int index = 0; index < 3; index++) {
            JSONObject placement = new JSONObject().put("placementId", "native-" + index)
                    .put("nativeStructures", new JSONArray().put(
                            new JSONObject().put("structure", "minecraft:village_plains")));
            if (index == 1) {
                placement.put("anchor", "LEGACY");
            } else if (index == 2) {
                placement.put("anchor", JSONObject.NULL);
            }
            placements.put(placement);
        }
        placements.put(new JSONObject().put("placementId", "editable")
                .put("anchor", "SURFACE").put("structures", new JSONArray().put("tower")));
        write(pack, "dimensions/main.json", new JSONObject().put("structures", placements).toString());
        List<String> errors = new ArrayList<>();

        PackStructurePlacementValidator.validateStructurePlacements(pack, Set.of("tower"), false, errors);

        assertTrue(errors.toString(), errors.isEmpty());
    }

    @Test
    public void rejectsNativeOnlyTerrainModesForEditablePlacementsAcrossEveryHost() throws Exception {
        for (String mode : List.of("VACUUM", "FLATTEN", "ENCASE")) {
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
        for (String mode : List.of("VACUUM", "FLATTEN", "ENCASE")) {
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
        for (String mode : List.of("VACUUM", "FLATTEN", "ENCASE")) {
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

    @Test
    public void flattenRequiresIntegralBoundedRangeAndBlendSettings() {
        for (String field : List.of("flattenRange", "horizontalPadding")) {
            for (Object value : List.of(0, 128, -1, 129, 1.5D, "64")) {
                List<String> errors = new ArrayList<>();
                JSONObject terrain = new JSONObject().put("mode", "FLATTEN").put(field, value);

                PackStructurePlacementValidator.validateNativeTerrain("placement",
                        new JSONObject().put("terrain", terrain), errors);

                if (value.equals(0) || value.equals(128)) {
                    assertTrue(value + ": " + errors, errors.isEmpty());
                } else {
                    assertEquals(value.toString(), 1, errors.size());
                    assertTrue(errors.getFirst(), errors.getFirst().contains("terrain." + field));
                }
            }
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
