package art.arcane.iris.core.pack;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/** A dimension's {@code importedStructures} as pack validation sees it: decoded into its typed settings, then checked. */
public class PackValidatorImportedStructurePolicyTest {
    private static final String CANNOT_LOAD = "Dimension 'main' cannot be loaded: Cannot read settings iris:structure"
            + " under \"importedStructures\" in Dimension main: ";

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void theShippingPolicyShapesAreAccepted() throws Exception {
        List<String> errors = validate("""
                {"disabled": ["minecraft:stronghold"], "disabledExact": ["minecraft:ruined_portal"],
                 "frequencyOverrides": [{"structureSet": "minecraft:nether_complexes", "multiplier": 1.1}],
                 "adjustments": [
                   {"match": ["minecraft:stronghold"], "yBand": {"min": -120, "max": -20},
                    "terrain": {"mode": "ENCASE", "horizontalPadding": 4, "ceilingPadding": 4, "floorPadding": 4,
                                "encasePalette": {"palette": [{"block": "minecraft:stone_bricks", "weight": 6}]}}},
                   {"match": ["towns_and_towers:"], "terrain": {"mode": "VACUUM"}}
                 ]}
                """);

        assertTrue(errors.toString(), errors.isEmpty());
    }

    @Test
    public void anOptionAboveItsBoundBlocksThePackNamingItsPath() throws Exception {
        List<String> errors = validate("{\"frequencyOverrides\": [{\"structureSet\": \"minecraft:villages\", \"multiplier\": 99}]}");

        assertEquals(List.of(CANNOT_LOAD + "importedStructures.frequencyOverrides[0].multiplier is 99.0; it must be at most 16"), errors);
    }

    @Test
    public void aBoundInsideNestedSettingsNamesTheWholePath() throws Exception {
        List<String> errors = validate("{\"adjustments\": [{\"match\": [\"minecraft:stronghold\"], \"yBand\": {\"min\": -5000}}]}");

        assertEquals(List.of(CANNOT_LOAD + "importedStructures.adjustments[0].yBand.min is -5000; it must be at least -4064"), errors);
    }

    @Test
    public void anUnknownTerrainModeBlocksThePack() throws Exception {
        List<String> errors = validate("{\"adjustments\": [{\"match\": [\"minecraft:stronghold\"], \"terrain\": {\"mode\": \"SINK\"}}]}");

        assertEquals(1, errors.size());
        assertTrue(errors.get(0), errors.get(0).startsWith(CANNOT_LOAD) && errors.get(0).contains("\"SINK\" is not one of"));
    }

    @Test
    public void blankKeysAndAStructureSetWithoutNamespaceAreErrors() throws Exception {
        List<String> errors = validate("""
                {"disabled": ["minecraft:village", " "],
                 "frequencyOverrides": [{"structureSet": "villages"}],
                 "adjustments": [{"match": [""]}]}
                """);

        assertEquals(List.of(
                "pack:dimension/main: importedStructures.disabled[1] is blank; write a structure key",
                "pack:dimension/main: importedStructures.frequencyOverrides[0].structureSet is \"villages\"; it must be a"
                        + " namespaced key such as minecraft:villages",
                "pack:dimension/main: importedStructures.adjustments[0].match[0] is blank; write a structure key"), errors);
    }

    /** The blocking errors of a pack whose one dimension declares {@code policy} as its {@code importedStructures}. */
    private List<String> validate(String policy) throws Exception {
        File pack = temporaryFolder.newFolder("pack");
        write(pack, "dimensions/main.json", "{\"regions\": [\"region\"], \"importedStructures\": " + policy + "}");
        write(pack, "regions/region.json", "{\"landBiomes\": [\"biome\"]}");
        write(pack, "biomes/biome.json", "{\"name\": \"Biome\"}");
        return PackValidator.validate(pack).getBlockingErrors();
    }

    private void write(File root, String relative, String content) throws Exception {
        art.arcane.iris.core.modules.BuiltInModulesJar.installInto(root.toPath());
        Path target = new File(root, relative).toPath();
        Files.createDirectories(target.getParent());
        Files.writeString(target, content, StandardCharsets.UTF_8);
    }
}
