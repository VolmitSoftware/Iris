package art.arcane.iris.core.modules;

import art.arcane.iris.builtin.decorant.DecorantSettings;
import art.arcane.iris.builtin.decorant.DecorationPart;
import art.arcane.iris.builtin.decorant.Decorator;
import art.arcane.volmlib.util.json.JSONObject;
import com.google.gson.Gson;
import com.volmit.config.Level;
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
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

/**
 * The decorators packs have always written, the {@code "decorators": [...]} list, read into {@link Decorator}, a settings
 * class of IrisApi types only.
 */
public class DecorantSettingsDecodingTest {
    private static final String BIOME_DECORATORS = """
            {"name": "Meadow", "decorators": [
              {"chance": 0.003, "variance": {"style": "STATIC"}, "zoom": 1,
               "palette": [{"block": "minecraft:white_tulip"}, {"block": "minecraft:cornflower", "weight": 3}],
               "style": {"style": "SIMPLEX", "zoom": 4}},
              {"partOf": "SHORE_LINE", "chance": 1, "stackMin": 2, "stackMax": 5, "scaleStack": true, "absoluteMaxStack": 12,
               "heightVariance": {"style": "PERLIN", "zoom": 2}, "forcePlace": true, "forceBlock": {"block": "farmland", "data": {"moisture": "7"}},
               "whitelist": [{"block": "sand"}], "blacklist": [], "slopeCondition": {"minimumSlope": 1, "maximumSlope": 4},
               "palette": [{"block": "sugar_cane"}], "topPalette": [{"block": "bamboo"}], "topThreshold": 0.5},
              "snippet/decorator/bush",
              "snippet/bush",
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
        Path snippets = Files.createDirectories(packFolder.resolve("snippet/decorator"));
        Files.writeString(snippets.resolve("bush.json"), "{\"chance\": 0.2, \"palette\": [{\"block\": \"minecraft:fern\"}]}");
        pack = LoadedPack.load(packFolder, ModuleHost.of(BuiltInPack.allBuiltIns())
                .describingHostTypesIn(IrisModuleHost.HOST_TYPE_PACKAGE).copyingJarsTo(folder.newFolder("copies").toPath()));
        decoder = new ModuleSettingsDecoder(pack.catalog(), packFolder, Gson::new);
    }

    @After
    public void closePack() {
        pack.close();
    }

    @Test
    public void existingDecoratorJsonReadsWhatIrisReadFromIt() {
        List<Decorator> decorators = decorators(BIOME_DECORATORS);

        Decorator flowers = decorators.getFirst();
        assertEquals(0.003, flowers.chance(), 0);
        assertEquals(List.of(BlockSpec.of("minecraft:white_tulip"), BlockSpec.of("minecraft:cornflower").withWeight(3)), flowers.palette());
        assertEquals("SIMPLEX", flowers.style().style());
        assertEquals(4, flowers.style().zoom(), 0);
        assertEquals("STATIC", flowers.variance().style());
        assertEquals(DecorationPart.NONE, flowers.partOf());
        assertFalse(flowers.isStacking());
        assertFalse(flowers.hasWhitelist());
        assertTrue(flowers.slopeCondition().isDefault());

        Decorator cane = decorators.get(1);
        assertEquals(DecorationPart.SHORE_LINE, cane.partOf());
        assertEquals(2, cane.stackMin());
        assertEquals(5, cane.stackMax());
        assertTrue(cane.isStacking());
        assertTrue(cane.scaleStack());
        assertEquals(12, cane.absoluteMaxStack());
        assertEquals("PERLIN", cane.heightVariance().style());
        assertTrue(cane.forcePlace());
        assertEquals("farmland", cane.forceBlock().orElseThrow().block());
        assertEquals(List.of(BlockSpec.of("sand")), cane.whitelist());
        assertTrue("an explicitly empty blacklist is still a blacklist", cane.hasBlacklist());
        assertEquals(List.of(), cane.blacklist());
        assertEquals(1, cane.slopeCondition().minimumSlope(), 0);
        assertFalse(cane.slopeCondition().isValid(5));
        assertEquals(List.of(BlockSpec.of("bamboo")), cane.topPalette());
        assertEquals(0.5, cane.topThreshold(), 0);
    }

    @Test
    public void snippetsReadUnderEitherReferenceForm() {
        List<Decorator> decorators = decorators(BIOME_DECORATORS);

        for (Decorator bush : List.of(decorators.get(2), decorators.get(3))) {
            assertEquals(0.2, bush.chance(), 0);
            assertEquals(List.of(BlockSpec.of("minecraft:fern")), bush.palette());
        }
    }

    @Test
    public void anEmptyDecoratorHasIrisDefaults() {
        Decorator defaults = decorators(BIOME_DECORATORS).get(4);

        assertEquals(0.1, defaults.chance(), 0);
        assertEquals(List.of(BlockSpec.of("grass")), defaults.palette());
        assertEquals(List.of(), defaults.topPalette());
        assertEquals("STATIC", defaults.style().style());
        assertEquals("STATIC", defaults.variance().style());
        assertEquals("STATIC", defaults.heightVariance().style());
        assertEquals(1, defaults.stackMin());
        assertEquals(1, defaults.stackMax());
        assertEquals(30, defaults.absoluteMaxStack());
        assertEquals(1.0, defaults.topThreshold(), 0);
        assertFalse(defaults.forcePlace());
        assertTrue(defaults.forceBlock().isEmpty());
        assertFalse(defaults.hasWhitelist());
        assertFalse(defaults.hasBlacklist());
    }

    @Test
    public void theFeatureIdIsNotAKey() {
        JSONObject byId = new JSONObject("{\"iris:decorant\": [{\"chance\": 1}]}");

        assertSame(ModuleSettings.NONE, decoder.decode(Level.BIOME, byId, "biome meadow"));
        assertTrue(decoder.keys(Level.BIOME).containsAll(Set.of("decorators", "deposits")));
        assertFalse(decoder.keys(Level.BIOME).contains("iris:decorant"));
    }

    @Test
    public void anObjectOfOptionsUnderDecoratorsIsRefused() {
        JSONObject object = new JSONObject("{\"decorators\": {\"decorators\": [{\"chance\": 1}]}}");

        IllegalArgumentException refused = assertThrows(IllegalArgumentException.class, () -> decoder.decode(Level.BIOME, object, "biome meadow"));

        assertEquals("Cannot read settings iris:decorant under \"decorators\" in biome meadow: write a list, [...], of the decorators,"
                + " not an object of options", refused.getMessage());
    }

    private List<Decorator> decorators(String biomeJson) {
        return decoder.decode(Level.BIOME, new JSONObject(biomeJson), "biome meadow")
                .find(DecorantSettings.class).orElseThrow().decorators();
    }
}
