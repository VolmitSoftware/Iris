package art.arcane.iris.core.modules;

import art.arcane.iris.builtin.deposit.DepositBiomeScope;
import art.arcane.iris.builtin.deposit.DepositGenerator;
import art.arcane.iris.builtin.deposit.DepositHeightDistribution;
import art.arcane.iris.builtin.deposit.DepositPlacementScope;
import art.arcane.iris.builtin.deposit.DepositSettings;
import art.arcane.iris.builtin.deposit.DepositShape;
import art.arcane.volmlib.util.json.JSONObject;
import com.google.gson.Gson;
import com.volmit.FeatureAttachment.Target;
import com.volmit.block.BlockSpec;
import com.volmit.host.LoadedPack;
import com.volmit.host.ModuleHost;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

/**
 * The deposit generators packs have always written, the {@code "deposits": [...]} list, read into {@link DepositGenerator},
 * a settings class of IrisApi types only, with the values Iris's own deposit generator class read from the same JSON.
 */
public class DepositSettingsDecodingTest {
    private static final String REGION_DEPOSITS = """
            {"deposits": [
              {"minHeight": 15, "maxPerChunk": 26, "maxHeight": 410, "minPerChunk": 4, "minSize": 3, "maxSize": 8,
               "palette": [{"block": "minecraft:iron_ore"}, {"block": "minecraft:coal_ore"}], "varience": 4},
              {"heightDistribution": "TRIANGLE", "placementScope": "FULL_HEIGHT", "surfaceClearance": 0, "shape": "VANILLA_ELLIPSOID",
               "spawnChance": 0.5, "perClumpSpawnChance": 0.25, "discardChanceOnAirExposure": 0.7, "replaceableBlocks": ["stone", "deepslate"],
               "biomeScope": "SURFACE", "includedBiomes": ["plains"], "excludedBiomes": ["desert/dunes"], "replaceBedrock": true,
               "palette": [{"block": "oak_log", "data": {"axis": "y"}, "weight": 2, "backup": {"block": "stone"}}]},
              "snippet/deposit/diamonds",
              "snippet/diamonds",
              {}
            ]}
            """;

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    private LoadedPack pack;
    private ModuleSettingsDecoder decoder;

    @Before
    public void loadBuiltIns() throws IOException {
        Path packFolder = folder.newFolder("pack").toPath();
        Path snippets = Files.createDirectories(packFolder.resolve("snippet/deposit"));
        Files.writeString(snippets.resolve("diamonds.json"), "{\"maxHeight\": 16, \"palette\": [{\"block\": \"minecraft:diamond_ore\"}]}");
        pack = LoadedPack.load(packFolder, ModuleHost.of(IrisModuleHost.builtInModules())
                .describingHostTypesIn(IrisModuleHost.HOST_TYPE_PACKAGE).copyingJarsTo(folder.newFolder("copies").toPath()));
        decoder = new ModuleSettingsDecoder(pack.catalog(), packFolder, Gson::new);
    }

    @After
    public void closePack() {
        pack.close();
    }

    @Test
    public void existingDepositJsonReadsTheValuesIrisReadFromIt() {
        List<DepositGenerator> deposits = deposits(REGION_DEPOSITS);

        DepositGenerator ores = deposits.getFirst();
        assertEquals(15, ores.minHeight());
        assertEquals(410, ores.maxHeight());
        assertEquals(4, ores.minPerChunk());
        assertEquals(26, ores.maxPerChunk());
        assertEquals(3, ores.minSize());
        assertEquals(8, ores.maxSize());
        assertEquals(4, ores.varience());
        assertEquals(List.of(BlockSpec.of("minecraft:iron_ore"), BlockSpec.of("minecraft:coal_ore")), ores.palette());
        assertEquals(DepositHeightDistribution.CLIPPED_UNIFORM, ores.heightDistribution());
        assertEquals(DepositPlacementScope.TERRAIN, ores.placementScope());
        assertEquals(7, ores.surfaceClearance());
        assertEquals(DepositShape.IRIS, ores.shape());
        assertEquals(1, ores.spawnChance(), 0);
        assertEquals(DepositBiomeScope.CAVE, ores.biomeScope());
        assertEquals(List.of(), ores.replaceableBlocks());
        assertFalse(ores.replaceBedrock());
        assertEquals(2, ores.maxDimension());

        DepositGenerator tuned = deposits.get(1);
        assertEquals(DepositHeightDistribution.TRIANGLE, tuned.heightDistribution());
        assertEquals(DepositPlacementScope.FULL_HEIGHT, tuned.placementScope());
        assertEquals(0, tuned.surfaceClearance());
        assertEquals(DepositShape.VANILLA_ELLIPSOID, tuned.shape());
        assertEquals(0.5, tuned.spawnChance(), 0);
        assertEquals(0.25, tuned.perClumpSpawnChance(), 0);
        assertEquals(0.7, tuned.discardChanceOnAirExposure(), 0);
        assertEquals(List.of("stone", "deepslate"), tuned.replaceableBlocks());
        assertEquals(DepositBiomeScope.SURFACE, tuned.biomeScope());
        assertEquals(List.of("plains"), tuned.includedBiomes());
        assertEquals(List.of("desert/dunes"), tuned.excludedBiomes());
        assertTrue(tuned.replaceBedrock());
        assertFalse(tuned.usesCaveBiomeFilter());
        BlockSpec log = tuned.palette().getFirst();
        assertEquals("oak_log", log.block());
        assertEquals(Map.of("axis", "y"), log.data());
        assertEquals(2, log.weight());
        assertEquals(Optional.of(BlockSpec.of("stone")), log.backup());
    }

    @Test
    public void snippetsReadUnderEitherReferenceForm() {
        List<DepositGenerator> deposits = deposits(REGION_DEPOSITS);

        for (DepositGenerator diamonds : List.of(deposits.get(2), deposits.get(3))) {
            assertEquals(16, diamonds.maxHeight());
            assertEquals(1, diamonds.minHeight());
            assertEquals(List.of(BlockSpec.of("minecraft:diamond_ore")), diamonds.palette());
        }
    }

    @Test
    public void anEmptyGeneratorHasIrisDefaults() {
        DepositGenerator defaults = deposits(REGION_DEPOSITS).get(4);

        assertEquals(1, defaults.minHeight());
        assertEquals(75, defaults.maxHeight());
        assertEquals(0, defaults.minSize());
        assertEquals(128, defaults.maxSize());
        assertEquals(0, defaults.minPerChunk());
        assertEquals(3, defaults.maxPerChunk());
        assertEquals(3, defaults.varience());
        assertEquals(List.of(), defaults.palette());
        assertEquals(6, defaults.maxDimension());
    }

    @Test
    public void theFeatureIdIsNotAKey() {
        JSONObject byId = new JSONObject("{\"iris:deposit\": [{\"maxHeight\": 16}]}");

        assertSame(ModuleSettings.NONE, decoder.decode(Target.REGION, byId, "region forests"));
        assertTrue(decoder.keys(Target.REGION).contains("deposits"));
        assertFalse(decoder.keys(Target.REGION).contains("iris:deposit"));
    }

    @Test
    public void anObjectOfOptionsUnderDepositsIsRefused() {
        JSONObject object = new JSONObject("{\"deposits\": {\"generators\": [{\"maxHeight\": 16}]}}");

        IllegalArgumentException refused = assertThrows(IllegalArgumentException.class, () -> decoder.decode(Target.REGION, object, "region forests"));

        assertEquals("Cannot read settings iris:deposit under \"deposits\" in region forests: write a list, [...], of the generators,"
                + " not an object of options", refused.getMessage());
    }

    @Test
    public void nullUnderDepositsIsRefused() {
        JSONObject nothing = new JSONObject("{\"deposits\": null}");

        IllegalArgumentException refused = assertThrows(IllegalArgumentException.class, () -> decoder.decode(Target.REGION, nothing, "region forests"));

        assertTrue(refused.getMessage(), refused.getMessage().contains("under \"deposits\" in region forests: the value is null; write a list"));
    }

    @Test
    public void aWronglyShapedGeneratorNamesTheKeyAndTheFile() {
        JSONObject broken = new JSONObject("{\"deposits\": [{\"maxHeight\": \"tall\"}]}");

        IllegalArgumentException refused = assertThrows(IllegalArgumentException.class, () -> decoder.decode(Target.REGION, broken, "region forests"));

        assertTrue(refused.getMessage(), refused.getMessage().startsWith("Cannot read settings iris:deposit under \"deposits\" in region forests: "));
    }

    private List<DepositGenerator> deposits(String regionJson) {
        return decoder.decode(Target.REGION, new JSONObject(regionJson), "region forests")
                .find(DepositSettings.class).orElseThrow().generators();
    }
}
