/*
 * Iris is a World Generator for Minecraft Servers
 * Copyright (c) 2026 Arcane Arts (Volmit Software)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package art.arcane.iris.core.modules;

import art.arcane.iris.builtin.deposit.DepositVariant;
import art.arcane.iris.builtin.deposit.DepositVariantSettings;
import art.arcane.iris.builtin.deposit.OreFrequencySettings;
import art.arcane.iris.builtin.deposit.OreSizeSettings;
import art.arcane.iris.builtin.terrain.HideOresSettings;
import art.arcane.iris.builtin.terrain.OreGenerator;
import art.arcane.iris.builtin.terrain.OreSettings;
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
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * The ore settings packs have always written, read by the terrain and deposit modules under their old keys: {@code ores} (a
 * list at a dimension, region or biome), {@code hideOresForHiddenOre} (a boolean at a dimension), {@code depositVariants} (a list)
 * and the two ore deposit multipliers (numbers at a biome). The dimension is the overworld file as it was before the port.
 */
public class OreSettingsDecodingTest {
    private static final String PACK = "/art/arcane/iris/core/modules/pack-before-the-port";

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    private Path packFolder;
    private LoadedPack pack;
    private ModuleSettingsDecoder decoder;

    @Before
    public void loadBuiltIns() throws IOException, URISyntaxException {
        packFolder = Path.of(OreSettingsDecodingTest.class.getResource(PACK).toURI());
        pack = LoadedPack.load(packFolder, ModuleHost.of(IrisModuleHost.builtInModules())
                .reservingFields(ModuleTargets.reservedFields())
                .describingHostTypesIn(IrisModuleHost.HOST_TYPE_PACKAGE).copyingJarsTo(folder.newFolder("copies").toPath()));
        decoder = new ModuleSettingsDecoder(pack.catalog(), packFolder, Gson::new);
    }

    @After
    public void closePack() {
        pack.close();
    }

    @Test
    public void theOverworldReadsItsOresInTheOrderTheFileListsThem() throws IOException {
        JSONObject dimension = new JSONObject(Files.readString(packFolder.resolve("dimensions/overworld.json")));

        List<OreGenerator> ores = decoder.decode(Target.DIMENSION, dimension, "dimension overworld")
                .find(OreSettings.class).orElseThrow().generators();

        assertEquals(dimension.getJSONArray("ores").length(), ores.size());
        assertEquals(11, ores.size());
        OreGenerator firstBedrock = ores.getFirst();
        assertEquals(1, firstBedrock.threshold(), 0);
        assertEquals(0, firstBedrock.range().min(), 0);
        assertEquals(0, firstBedrock.range().max(), 0);
        assertEquals(List.of(BlockSpec.of("minecraft:bedrock")), firstBedrock.palette().palette());
        assertEquals("FLAT", firstBedrock.chanceStyle().style());
        assertFalse(firstBedrock.generateSurface());
        OreGenerator secondBedrock = ores.get(1);
        assertEquals(0.625, secondBedrock.threshold(), 0);
        assertEquals("a snippet reference reads the snippet's style", "STATIC", secondBedrock.chanceStyle().style());
        OreGenerator lastDeepslate = ores.getLast();
        assertEquals(0.125, lastDeepslate.threshold(), 0);
        assertEquals(64, lastDeepslate.range().min(), 0);
        assertEquals(List.of(BlockSpec.of("minecraft:deepslate")), lastDeepslate.palette().palette());
    }

    @Test
    public void oresOfARegionAndABiomeReadSurfaceFlagsStylesAndPalettes() {
        JSONObject ores = new JSONObject("""
                {"ores": [
                  {"generateSurface": true, "threshold": 0.3, "range": {"min": 50, "max": 90},
                   "chanceStyle": {"style": "SIMPLEX", "zoom": 4},
                   "palette": {"zoom": 2, "palette": [{"block": "minecraft:gold_ore"}, {"block": "minecraft:gold_block", "weight": 3}]}},
                  {}
                ]}
                """);

        for (Target target : List.of(Target.REGION, Target.BIOME)) {
            List<OreGenerator> read = decoder.decode(target, ores, "test").find(OreSettings.class).orElseThrow().generators();

            assertEquals(2, read.size());
            assertTrue(read.getFirst().generateSurface());
            assertEquals(0.3, read.getFirst().threshold(), 0);
            assertEquals("SIMPLEX", read.getFirst().chanceStyle().style());
            assertEquals(4, read.getFirst().chanceStyle().zoom(), 0);
            assertEquals(2, read.getFirst().palette().zoom(), 0);
            assertEquals(List.of(BlockSpec.of("minecraft:gold_ore"), BlockSpec.of("minecraft:gold_block").withWeight(3)), read.getFirst().palette().palette());
            assertEquals("an empty generator keeps Iris's defaults", 0.5, read.get(1).threshold(), 0);
            assertEquals(List.of(), read.get(1).palette().palette());
        }
    }

    @Test
    public void hideOresForHiddenOreIsABooleanOnTheDimension() {
        ModuleSettings hidden = decoder.decode(Target.DIMENSION, new JSONObject("{\"hideOresForHiddenOre\": true}"), "dimension test");
        ModuleSettings shown = decoder.decode(Target.DIMENSION, new JSONObject("{\"hideOresForHiddenOre\": false}"), "dimension test");

        assertTrue(hidden.find(HideOresSettings.class).orElseThrow().hide());
        assertFalse(shown.find(HideOresSettings.class).orElseThrow().hide());
        assertTrue("a dimension that does not write it declares nothing", decoder.decode(Target.DIMENSION, new JSONObject("{}"), "dimension test").isEmpty());
    }

    @Test
    public void depositVariantsAreAListAtEveryLevel() {
        JSONObject json = new JSONObject("""
                {"depositVariants": [
                  {"minHeight": -64, "maxHeight": 0, "remap": {"minecraft:iron_ore": "minecraft:deepslate_iron_ore", "minecraft:coal_ore": "minecraft:deepslate_coal_ore"}},
                  {"maxHeight": 10}
                ]}
                """);

        for (Target target : List.of(Target.DIMENSION, Target.REGION, Target.BIOME)) {
            List<DepositVariant> variants = decoder.decode(target, json, "test").find(DepositVariantSettings.class).orElseThrow().variants();

            assertEquals(2, variants.size());
            assertEquals(-64, variants.getFirst().minHeight());
            assertEquals(0, variants.getFirst().maxHeight());
            assertEquals(Map.of("minecraft:iron_ore", "minecraft:deepslate_iron_ore", "minecraft:coal_ore", "minecraft:deepslate_coal_ore"),
                    variants.getFirst().remap());
            assertEquals(0, variants.get(1).minHeight());
            assertEquals(10, variants.get(1).maxHeight());
            assertEquals(Map.of(), variants.get(1).remap());
        }
    }

    @Test
    public void theTwoOreDepositMultipliersAreNumbersOnTheBiome() {
        ModuleSettings tuned = decoder.decode(Target.BIOME, new JSONObject("{\"oreDepositFrequencyMultiplier\": 0.4, \"oreDepositSizeMultiplier\": 2}"), "biome test");
        ModuleSettings plain = decoder.decode(Target.BIOME, new JSONObject("{}"), "biome test");

        assertEquals(0.4, tuned.find(OreFrequencySettings.class).orElseThrow().multiplier(), 0);
        assertEquals(2, tuned.find(OreSizeSettings.class).orElseThrow().multiplier(), 0);
        assertTrue(plain.find(OreFrequencySettings.class).isEmpty());
        assertTrue(plain.find(OreSizeSettings.class).isEmpty());
    }

    @Test
    public void everyKeyIsOnlyReadAtTheLevelsTheOldFieldExistedAt() {
        assertTrue(decoder.keys(Target.DIMENSION).containsAll(List.of("ores", "hideOresForHiddenOre", "depositVariants")));
        assertTrue(decoder.keys(Target.REGION).containsAll(List.of("ores", "depositVariants")));
        assertFalse(decoder.keys(Target.REGION).contains("hideOresForHiddenOre"));
        assertTrue(decoder.keys(Target.BIOME).containsAll(List.of("ores", "depositVariants", "oreDepositFrequencyMultiplier", "oreDepositSizeMultiplier")));
        assertFalse(decoder.keys(Target.DIMENSION).contains("oreDepositFrequencyMultiplier"));
    }
}
