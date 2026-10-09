package art.arcane.iris.core.modules;

import art.arcane.iris.builtin.structure.StructureAdjustment;
import art.arcane.iris.builtin.structure.StructureSettings;
import art.arcane.iris.engine.object.structure.IrisStructureStiltSettings;
import art.arcane.iris.engine.object.structure.IrisStructureTerrain;
import art.arcane.volmlib.util.json.JSONObject;
import com.google.gson.Gson;
import com.volmit.FeatureAttachment.Target;
import com.volmit.block.BlockSpec;
import com.volmit.host.LoadedPack;
import com.volmit.host.ModuleHost;
import com.volmit.vanilla.StructureTerrain;
import com.volmit.vanilla.YBand;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

/**
 * The dimension's {@code importedStructures} as packs have always written it, read into
 * {@link StructureSettings}, IrisApi types only, with the values Iris's own classes read from the same JSON.
 */
public class StructureSettingsDecodingTest {
    /** The overworld test pack's dimensions/overworld.json member, plus the options it leaves out. */
    private static final String MIGRATED_DIMENSION = """
            {"importedStructures": {
              "datapackOverrides": true,
              "adjustments": [
                {
                  "match": ["minecraft:stronghold"],
                  "yBand": {"min": -120, "max": -20},
                  "terrain": {
                    "mode": "ENCASE", "horizontalPadding": 4, "ceilingPadding": 4, "floorPadding": 4,
                    "encasePalette": {"zoom": 1, "palette": [
                      {"block": "minecraft:stone_bricks", "weight": 6}, {"block": "minecraft:mossy_stone_bricks", "weight": 2},
                      {"block": "minecraft:cracked_stone_bricks", "weight": 2}, {"block": "minecraft:cobblestone", "weight": 1}
                    ]}
                  }
                },
                {"match": ["minecraft:trial_chambers"], "yShift": -64},
                {"match": ["minecraft:mineshaft"], "preserveSourceY": true},
                {"match": ["minecraft:village"], "stilt": {"maxDepth": 2, "palette": {"palette": [{"block": "minecraft:dirt"}]}}}
              ],
              "disabled": ["minecraft:village", "nova_structures:"],
              "disabledExact": ["minecraft:ruined_portal"],
              "undergroundYShift": -32,
              "frequencyOverrides": [{"structureSet": "minecraft:nether_complexes", "multiplier": 1.5}]
            }}
            """;

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    private LoadedPack pack;
    private ModuleSettingsDecoder decoder;

    @Before
    public void loadBuiltIns() throws IOException {
        Path packFolder = folder.newFolder("pack").toPath();
        pack = LoadedPack.load(packFolder, ModuleHost.of(IrisModuleHost.builtInModules())
                .describingHostTypesIn(IrisModuleHost.HOST_TYPE_PACKAGE).copyingJarsTo(folder.newFolder("copies").toPath()));
        decoder = new ModuleSettingsDecoder(pack.catalog(), packFolder, Gson::new);
    }

    @After
    public void closePack() {
        pack.close();
    }

    @Test
    public void theMigratedDimensionReadsEveryOption() {
        StructureSettings settings = settings(MIGRATED_DIMENSION);

        assertTrue(settings.datapackOverrides());
        assertEquals(List.of("minecraft:village", "nova_structures:"), settings.disabled());
        assertEquals(List.of("minecraft:ruined_portal"), settings.disabledExact());
        assertEquals(-32, settings.undergroundYShift());
        assertEquals("minecraft:nether_complexes", settings.frequencyOverrides().getFirst().structureSet());
        assertEquals(1.5, settings.frequencyOverrides().getFirst().multiplier(), 0);
        List<StructureAdjustment> adjustments = settings.adjustments();
        assertEquals(4, adjustments.size());
        StructureAdjustment stronghold = adjustments.getFirst();
        assertEquals(Optional.of(YBand.of(-120, -20)), stronghold.yBand());
        StructureTerrain terrain = stronghold.terrain().orElseThrow();
        assertEquals(StructureTerrain.Mode.ENCASE, terrain.mode());
        assertEquals(4, terrain.horizontalPadding());
        assertEquals(BlockSpec.of("minecraft:stone_bricks").withWeight(6), terrain.encasePalette().orElseThrow().palette().getFirst());
        assertEquals(-64, adjustments.get(1).yShift());
        assertTrue(adjustments.get(2).preserveSourceY());
        assertEquals(2, adjustments.get(3).stilt().orElseThrow().maxDepth());
        assertEquals(List.of(BlockSpec.of("minecraft:dirt")), adjustments.get(3).stilt().orElseThrow().palette().palette());
    }

    @Test
    public void fitsBecomeTheIrisClassesTheOldDimensionFieldReadFromTheSameJson() {
        JSONObject json = new JSONObject(MIGRATED_DIMENSION).getJSONObject("importedStructures").getJSONArray("adjustments").getJSONObject(0);
        JSONObject stiltJson = new JSONObject(MIGRATED_DIMENSION).getJSONObject("importedStructures").getJSONArray("adjustments")
                .getJSONObject(3).getJSONObject("stilt");
        List<StructureAdjustment> adjustments = settings(MIGRATED_DIMENSION).adjustments();
        Gson gson = new Gson();

        assertEquals(gson.toJson(gson.fromJson(json.getJSONObject("terrain").toString(), IrisStructureTerrain.class)),
                gson.toJson(IrisValues.structureTerrain(adjustments.getFirst().terrain().orElseThrow())));
        assertEquals(gson.toJson(gson.fromJson(stiltJson.toString(), IrisStructureStiltSettings.class)),
                gson.toJson(IrisValues.structureStilts(adjustments.get(3).stilt().orElseThrow())));
    }

    @Test
    public void theFeatureIdIsNotAKeyAndEmptySettingsHaveTheDefaults() {
        JSONObject byId = new JSONObject("{\"iris:structure\": {\"disabled\": [\"minecraft:village\"]}}");

        assertSame(ModuleSettings.NONE, decoder.decode(Target.DIMENSION, byId, "dimension overworld"));
        assertTrue(decoder.keys(Target.DIMENSION).contains("importedStructures"));
        assertFalse(decoder.keys(Target.DIMENSION).contains("iris:structure"));
        StructureSettings defaults = settings("{\"importedStructures\": {}}");
        assertTrue(defaults.datapackOverrides());
        assertEquals(List.of(), defaults.disabled());
        assertEquals(List.of(), defaults.adjustments());
        assertEquals(0, defaults.undergroundYShift());
    }

    @Test
    public void aListUnderImportedStructuresIsRefused() {
        JSONObject list = new JSONObject("{\"importedStructures\": [\"minecraft:village\"]}");

        IllegalArgumentException refused = assertThrows(IllegalArgumentException.class,
                () -> decoder.decode(Target.DIMENSION, list, "dimension overworld"));

        assertEquals("Cannot read settings iris:structure under \"importedStructures\" in dimension overworld:"
                + " write the settings as an object, {\"<option>\": ...}", refused.getMessage());
    }

    private StructureSettings settings(String dimensionJson) {
        return decoder.decode(Target.DIMENSION, new JSONObject(dimensionJson), "dimension overworld")
                .find(StructureSettings.class).orElseThrow();
    }
}
