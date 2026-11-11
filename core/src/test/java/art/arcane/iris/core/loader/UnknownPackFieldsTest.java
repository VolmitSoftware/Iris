package art.arcane.iris.core.loader;

import art.arcane.iris.spi.IrisLogging;
import org.junit.Test;

import java.io.File;
import java.util.List;

import static org.junit.Assert.assertEquals;

public class UnknownPackFieldsTest {
    private static final UnknownPackFields.Pack PACK_WITHOUT_MODULES = new UnknownPackFields.Pack(new File("npfixed"), "World npfixed", false);

    @Test
    public void listsEachFieldOnceWithItsFirstFileAndHowManyOthersHoldIt() {
        List<UnknownPackFields.Field> fields = List.of(
                new UnknownPackFields.Field("Biome", "layers", "biomes/carving/swamp.json", 13, null),
                new UnknownPackFields.Field("Biome", "layers", "biomes/carving/deep.json", 13, null),
                new UnknownPackFields.Field("Biome", "layers", "biomes/2026/lakes.json", 14, null),
                new UnknownPackFields.Field("Biome", "decorators", "biomes/carving/swamp.json", 20, "generators"),
                new UnknownPackFields.Field("Dimension", "bedrock", "dimensions/natureplanet.json", 10, null));

        String report = IrisLogging.clean(UnknownPackFields.describe(PACK_WITHOUT_MODULES, fields));

        assertEquals("""
                World npfixed has 3 fields nothing reads, since it loads no modules:
                  Biome decorators in biomes/carving/swamp.json:20, did you mean generators?
                  Biome layers in biomes/2026/lakes.json:14 and 2 other files
                  Dimension bedrock in dimensions/natureplanet.json:10""", report);
    }

    @Test
    public void namesNoModuleCauseWhenThePackLoadsModules() {
        UnknownPackFields.Pack pack = new UnknownPackFields.Pack(new File("overworld"), "Pack overworld", true);
        List<UnknownPackFields.Field> fields = List.of(new UnknownPackFields.Field("Region", "biomez", "regions/plains.json", 0, "biomes"));

        assertEquals("""
                Pack overworld has 1 field nothing reads:
                  Region biomez in regions/plains.json, did you mean biomes?""", IrisLogging.clean(UnknownPackFields.describe(pack, fields)));
    }
}
