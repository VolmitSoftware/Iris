package art.arcane.iris.core.pack;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class PackValidatorStructureTerrainBackendTest {
    private static final String TERRAIN_MODE_PATH = ".terrain.mode ";

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void rejectsNativeOnlyTerrainModesForEditablePlacementsAcrossEveryHost() throws Exception {
        for (String mode : List.of("VACUUM", "ENCASE")) {
            String packName = "editable-" + mode.toLowerCase(Locale.ROOT);
            File pack = temporaryFolder.newFolder(packName);
            write(pack, "dimensions/main.json", "{\"regions\":[{\"region\":\"forest\"}]," + placement("dimension", "city", false, mode) + "}");
            write(pack, "regions/forest.json", "{\"landBiomes\":[{\"biome\":\"plains\"}]," +placement("region", "tower", false, mode) + "}");
            write(pack, "biomes/plains.json", "{\"name\":\"Plains\"," + placement("biome", "farm", false, mode) + "}");
            for (String structure : List.of("city", "tower", "farm")) {
                write(pack, "structures/" + structure + ".json", "{\"startPool\":\"start\"}");
            }
            write(pack, "jigsaw-pools/start.json", "{\"pieces\":[]}");

            String suffix = ": structures[0].terrain.mode " + mode + " cannot target editable Iris structures;"
                    + " use nativeStructures or importedStructures.adjustments for native terrain preparation";
            assertEquals(List.of(
                    packName + ":dimension/main" + suffix,
                    packName + ":region/forest" + suffix,
                    packName + ":biome/plains" + suffix
            ), terrainModeErrors(pack));
        }
    }

    @Test
    public void acceptsNativeOnlyTerrainModesForNativeStructurePlacements() throws Exception {
        for (String mode : List.of("VACUUM", "ENCASE")) {
            File pack = singleDimensionPack("native-" + mode.toLowerCase(Locale.ROOT),
                    placement("native", "minecraft:village_plains", true, mode));

            assertTrue(mode + ": " + terrainModeErrors(pack), terrainModeErrors(pack).isEmpty());
        }
    }

    @Test
    public void acceptsSupportedTerrainModesForEditablePlacements() throws Exception {
        for (String mode : List.of("SOURCE", "PRESERVE", "BORE", "FORCE_CARVE")) {
            File pack = singleDimensionPack("editable-" + mode.toLowerCase(Locale.ROOT),
                    placement("editable", "city", false, mode));
            write(pack, "structures/city.json", "{\"startPool\":\"start\"}");
            write(pack, "jigsaw-pools/start.json", "{\"pieces\":[]}");

            assertTrue(mode + ": " + terrainModeErrors(pack), terrainModeErrors(pack).isEmpty());
        }
    }

    private File singleDimensionPack(String packName, String dimensionMembers) throws Exception {
        File pack = temporaryFolder.newFolder(packName);
        write(pack, "dimensions/main.json", "{\"regions\":[{\"region\":\"region\"}]," + dimensionMembers + "}");
        write(pack, "regions/region.json", "{\"landBiomes\":[{\"biome\":\"biome\"}]}");
        write(pack, "biomes/biome.json", "{\"name\":\"Biome\"}");
        return pack;
    }

    /** A {@code structures} member holding one placement of {@code structureKey} with terrain {@code terrainMode}. */
    private static String placement(String placementId, String structureKey, boolean nativeStructure, String terrainMode) {
        String source = nativeStructure
                ? "\"nativeStructures\":[{\"structure\":\"" + structureKey + "\"}]"
                : "\"structures\":[\"" + structureKey + "\"]";
        return "\"structures\":[{\"placementId\":\"" + placementId + "\"," + source
                + ",\"terrain\":{\"mode\":\"" + terrainMode + "\"}}]";
    }

    private static List<String> terrainModeErrors(File pack) {
        return PackValidator.validate(pack).getBlockingErrors().stream()
                .filter(error -> error.contains(TERRAIN_MODE_PATH))
                .toList();
    }

    private void write(File root, String relativePath, String content) throws Exception {
        art.arcane.iris.core.modules.BuiltInModulesJar.installInto(root.toPath());
        Path path = root.toPath().resolve(relativePath);
        Files.createDirectories(path.getParent());
        Files.writeString(path, content, StandardCharsets.UTF_8);
    }
}
