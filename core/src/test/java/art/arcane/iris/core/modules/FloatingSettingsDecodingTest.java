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

import art.arcane.iris.builtin.carve.CarvingEntry;
import art.arcane.iris.builtin.carve.CarvingSettings;
import art.arcane.iris.builtin.floating.FloatingBottomPaletteMode;
import art.arcane.iris.builtin.floating.FloatingChildBiome;
import art.arcane.iris.builtin.floating.FloatingChildSettings;
import art.arcane.iris.builtin.floating.MergeFloatingChildrenSettings;
import art.arcane.iris.builtin.floating.TopShapeMode;
import art.arcane.volmlib.util.json.JSONObject;
import com.google.gson.Gson;
import com.volmit.config.Level;
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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * The floating island settings packs have always written, read by the floating module under their old keys: {@code floatingChildBiomes}
 * (a list) and {@code mergeFloatingChildBiomes} (a boolean), both at a biome. The biome is a file of the overworld pack as it was before
 * the port.
 */
public class FloatingSettingsDecodingTest {
    private static final String PACK = "/art/arcane/iris/core/modules/pack-before-the-port";

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    private Path packFolder;
    private LoadedPack pack;
    private ModuleSettingsDecoder decoder;

    @Before
    public void loadBuiltIns() throws IOException, URISyntaxException {
        packFolder = Path.of(FloatingSettingsDecodingTest.class.getResource(PACK).toURI());
        pack = LoadedPack.load(packFolder, ModuleHost.of(BuiltInPack.allBuiltIns())
                .reservingFields(ModuleTargets.reservedFields())
                .describingHostTypesIn(IrisModuleHost.HOST_TYPE_PACKAGE).copyingJarsTo(folder.newFolder("copies").toPath()));
        decoder = new ModuleSettingsDecoder(pack.catalog(), packFolder, Gson::new);
    }

    @After
    public void closePack() {
        pack.close();
    }

    @Test
    public void aBiomeReadsItsFloatingChildrenAndTheMergeFlag() throws IOException {
        JSONObject biome = file("biomes/magnetics/plains.json");

        ModuleSettings settings = decoder.decode(Level.BIOME, biome, "biome magnetics/plains");

        List<FloatingChildBiome> children = settings.find(FloatingChildSettings.class).orElseThrow().children();
        assertEquals(biome.getJSONArray("floatingChildBiomes").length(), children.size());
        assertEquals(2, children.size());
        FloatingChildBiome mushroom = children.getFirst();
        assertEquals("mushroom/forest", mushroom.biome());
        assertEquals("carving/mushroom", mushroom.carving());
        assertEquals(1, mushroom.rarity());
        assertEquals(115, mushroom.minHeightAboveSurface());
        assertEquals(155, mushroom.maxHeightAboveSurface());
        assertEquals(TopShapeMode.NOISE, mushroom.topShapeMode());
        assertEquals("GLOB_IRIS_THICK", mushroom.footprintStyle().style());
        assertEquals(0.47, mushroom.footprintThreshold(), 0);
        assertEquals(FloatingBottomPaletteMode.CUSTOM, mushroom.bottomPaletteMode());
        assertEquals(1, mushroom.bottomPalette().size());
        assertEquals(0.5, mushroom.objectShrinkFactor(), 0);
        assertEquals("tropical/wilds", children.get(1).biome());
        assertEquals("carving/lush-child", children.get(1).carving());
        assertEquals(biome.getBoolean("mergeFloatingChildBiomes"), settings.find(MergeFloatingChildrenSettings.class).orElseThrow().merge());
        assertTrue(settings.find(MergeFloatingChildrenSettings.class).orElseThrow().merge());
    }

    @Test
    public void aBiomeWithoutFloatingChildrenDeclaresNone() {
        ModuleSettings settings = decoder.decode(Level.BIOME, new JSONObject("{\"mergeFloatingChildBiomes\": false, \"floatingChildBiomes\": [{}]}"), "biome test");

        assertFalse(settings.find(MergeFloatingChildrenSettings.class).orElseThrow().merge());
        assertEquals(1, settings.find(FloatingChildSettings.class).orElseThrow().children().size());
        assertEquals("an empty child keeps Iris's defaults", 160, settings.find(FloatingChildSettings.class).orElseThrow().children().getFirst().minHeightAboveSurface());
        assertTrue(decoder.decode(Level.BIOME, new JSONObject("{}"), "biome test").isEmpty());
    }

    @Test
    public void theTargetAndTheCarvingOfEveryChildAreBiomesTheDimensionUses() throws IOException {
        JSONObject biome = file("biomes/magnetics/plains.json");
        ModuleSettings biomeSettings = decoder.decode(Level.BIOME, biome, "biome magnetics/plains");
        ModuleSettings dimensionSettings = decoder.decode(Level.DIMENSION, file("dimensions/overworld.json"), "dimension overworld");

        IrisPackResources resources = PackResourceFixture.empty(packFolder.toFile());
        Set<String> used = biomeSettings.usedBiomes(resources, dimensionSettings, "overworld");

        assertEquals(new LinkedHashSet<>(List.of("mushroom/forest", "carving/mushroom", "tropical/wilds", "carving/lush-child")), used);
        assertEquals(Set.of(), biomeSettings.treeBiomes(resources));
    }

    @Test
    public void aCarvingNamingABandStandsForTheBandsBiome() {
        FloatingChildBiome child = new Gson().fromJson("{\"biome\": \"floating/target\", \"carving\": \"deep\"}", FloatingChildBiome.class);
        CarvingEntry band = new Gson().fromJson("{\"id\": \"deep\", \"enabled\": false, \"biome\": \"carving/deep\"}", CarvingEntry.class);
        ModuleSettings biomeSettings = BuiltInPack.settings(new FloatingChildSettings(List.of(child)));
        ModuleSettings dimensionSettings = BuiltInPack.settings(new CarvingSettings(List.of(band)));

        IrisPackResources resources = PackResourceFixture.empty(packFolder.toFile());
        assertEquals(new LinkedHashSet<>(List.of("floating/target", "carving/deep")), biomeSettings.usedBiomes(resources, dimensionSettings, "overworld"));
        assertEquals("without bands the name is taken as a biome key", new LinkedHashSet<>(List.of("floating/target", "deep")),
                biomeSettings.usedBiomes(resources, ModuleSettings.NONE, "overworld"));
    }

    @Test
    public void bothKeysAreOnlyReadAtBiomes() {
        assertTrue(decoder.keys(Level.BIOME).containsAll(List.of("floatingChildBiomes", "mergeFloatingChildBiomes")));
        assertFalse(decoder.keys(Level.REGION).contains("floatingChildBiomes"));
        assertFalse(decoder.keys(Level.DIMENSION).contains("mergeFloatingChildBiomes"));
    }

    private JSONObject file(String name) throws IOException {
        return new JSONObject(Files.readString(packFolder.resolve(name)));
    }
}
