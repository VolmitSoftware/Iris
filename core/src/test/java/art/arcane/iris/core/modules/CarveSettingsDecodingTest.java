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

import art.arcane.iris.builtin.anchor.ObjectPlaceMode;
import art.arcane.iris.modules.carve.CarvingBiomeSettings;
import art.arcane.iris.modules.carve.CarvingEnabledSettings;
import art.arcane.iris.modules.carve.CarvingEntry;
import art.arcane.iris.modules.carve.CarvingSettings;
import art.arcane.iris.modules.carve.CaveDepthSettings;
import art.arcane.iris.builtin.carve.CaveLavaHeightSettings;
import art.arcane.iris.modules.carve.CaveProfile;
import art.arcane.iris.modules.carve.UpperDimensionCarvingSettings;
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
import java.util.List;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * The cave settings packs have always written, read by the carve module under their old keys: {@code caveProfile} (an object at a
 * dimension, region or biome), {@code carving} and {@code carvingEnabled} (a list and a boolean at a dimension), {@code carvingBiome} and
 * {@code caveMinDepthBelowSurface} (a biome key and a number at a biome). The dimension, region and biome are files of the overworld pack
 * as they were before the port.
 */
public class CarveSettingsDecodingTest {
    private static final String PACK = "/art/arcane/iris/core/modules/pack-before-the-port";

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    private Path packFolder;
    private LoadedPack pack;
    private ModuleSettingsDecoder decoder;

    @Before
    public void loadBuiltIns() throws IOException, URISyntaxException {
        packFolder = Path.of(CarveSettingsDecodingTest.class.getResource(PACK).toURI());
        pack = LoadedPack.load(packFolder, ModuleHost.of(BuiltInPack.allBuiltIns())
                .reservingFields(ModuleTargets.reservedFields())
                .describingHostTypesIn(IrisModuleHost.HOST_TYPE_PACKAGES).copyingJarsTo(folder.newFolder("copies").toPath()));
        decoder = new ModuleSettingsDecoder(pack.catalog(), packFolder, Gson::new);
    }

    @After
    public void closePack() {
        pack.close();
    }

    @Test
    public void theOverworldReadsItsCaveProfileItsCarvingBandsAndCarvingEnabled() throws IOException {
        ModuleSettings settings = decoder.decode(Level.DIMENSION, file("dimensions/overworld.json"), "dimension overworld");

        CaveProfile profile = settings.find(CaveProfile.class).orElseThrow();
        assertTrue(profile.enabled());
        assertEquals(6, profile.verticalRange().min(), 0);
        assertEquals(700, profile.verticalRange().max(), 0);
        assertEquals("PERLIN_IRIS", profile.baseDensityStyle().style());
        assertEquals(0.72, profile.baseDensityStyle().zoom(), 0);
        assertEquals("FRACTAL_WATER", profile.warpStyle().style());
        assertEquals(-0.14, profile.densityThreshold().min(), 0);
        assertEquals(3, profile.sampleStep());
        assertEquals(5, profile.surfaceClearance());
        assertEquals(14, profile.objectMinDepthBelowSurface());
        assertEquals(ObjectPlaceMode.ORGANIC_STILT, profile.defaultObjectPlaceMode().orElseThrow());
        assertEquals(2, profile.modules().size());
        assertTrue(profile.modules().get(1).invert());
        assertEquals(240, profile.modules().get(1).verticalRange().max(), 0);

        List<CarvingEntry> bands = settings.find(CarvingSettings.class).orElseThrow().entries();
        assertEquals(1, bands.size());
        assertEquals("global-deepdark-band", bands.getFirst().id());
        assertTrue(bands.getFirst().enabled());
        assertEquals("carving/standard-deepdark", bands.getFirst().biome());
        assertEquals(-250, bands.getFirst().worldYRange().min(), 0);
        assertEquals(-175, bands.getFirst().worldYRange().max(), 0);
        assertEquals("a band that writes no children keeps Iris's defaults", 3, bands.getFirst().childRecursionDepth());
        assertEquals(1.5, bands.getFirst().childShrinkFactor(), 0);
        assertEquals("CELLULAR_IRIS_DOUBLE", bands.getFirst().childStyle().style());

        assertTrue(settings.find(CarvingEnabledSettings.class).orElseThrow().enabled());
    }

    @Test
    public void aRegionReadsItsCaveProfile() throws IOException {
        JSONObject region = file("regions/forests.json");

        CaveProfile profile = decoder.decode(Level.REGION, region, "region forests").find(CaveProfile.class).orElseThrow();

        assertTrue(profile.enabled());
        assertEquals(6, profile.verticalRange().min(), 0);
        assertEquals(120, profile.verticalRange().max(), 0);
        assertEquals(1, profile.baseWeight(), 0);
        assertEquals(3, profile.sampleStep());
    }

    @Test
    public void aBiomeReadsItsCaveProfileAndItsMinimumDepth() throws IOException {
        JSONObject biome = file("biomes/carving/amethyst.json");

        ModuleSettings settings = decoder.decode(Level.BIOME, biome, "biome carving/amethyst");

        CaveProfile profile = settings.find(CaveProfile.class).orElseThrow();
        assertTrue(profile.enabled());
        assertEquals(620, profile.verticalRange().max(), 0);
        assertEquals(0.91, profile.baseWeight(), 0);
        assertEquals(2, profile.sampleStep());
        assertEquals(biome.getInt("caveMinDepthBelowSurface"), settings.find(CaveDepthSettings.class).orElseThrow().depth());
        assertEquals(10, settings.find(CaveDepthSettings.class).orElseThrow().depth());
        assertTrue("the biome writes no carvingBiome", settings.find(CarvingBiomeSettings.class).isEmpty());
    }

    @Test
    public void aCaveProfileMayBeWrittenAsASnippetReference() {
        ModuleSettings settings = decoder.decode(Level.BIOME, new JSONObject("{\"caveProfile\": \"snippet/iris/carve/cave-profile/shallow\"}"), "biome test");

        CaveProfile profile = settings.find(CaveProfile.class).orElseThrow();
        assertTrue(profile.enabled());
        assertEquals(100, profile.verticalRange().max(), 0);
        assertEquals(2, profile.sampleStep());
    }

    @Test
    public void carvingBandsReadChildrenAndTheirOptions() {
        JSONObject json = new JSONObject("""
                {"carving": [
                  {"id": "deep", "biome": "carving/deep", "worldYRange": {"min": -64, "max": 0}, "childShrinkFactor": 2, "childRecursionDepth": 5,
                   "children": ["lush", " moss "], "childStyle": {"style": "SIMPLEX", "zoom": 3}},
                  {"id": "off", "enabled": false, "biome": "carving/off"},
                  {}
                ], "carvingEnabled": false}
                """);

        ModuleSettings settings = decoder.decode(Level.DIMENSION, json, "dimension test");

        List<CarvingEntry> bands = settings.find(CarvingSettings.class).orElseThrow().entries();
        assertEquals(3, bands.size());
        assertEquals(List.of("lush", " moss "), bands.getFirst().children());
        assertEquals(2, bands.getFirst().childShrinkFactor(), 0);
        assertEquals(5, bands.getFirst().childRecursionDepth());
        assertEquals("SIMPLEX", bands.getFirst().childStyle().style());
        assertFalse(bands.get(1).enabled());
        assertEquals("an empty band keeps Iris's defaults", -64, bands.get(2).worldYRange().min(), 0);
        assertEquals(320, bands.get(2).worldYRange().max(), 0);
        assertTrue(bands.get(2).enabled());
        assertFalse(settings.find(CarvingEnabledSettings.class).orElseThrow().enabled());
        CarvingSettings carving = settings.find(CarvingSettings.class).orElseThrow();
        assertEquals("a band id stands for its biome", "carving/off", carving.biomeOf("off"));
        assertEquals("anything else stands for itself", "carving/mushroom", carving.biomeOf("carving/mushroom"));
    }

    @Test
    public void carvingBiomeIsABiomeKeyAndRegistersItWithTheBiomeTree() {
        ModuleSettings settings = decoder.decode(Level.BIOME, new JSONObject("{\"carvingBiome\": \"carving/mushroom\"}"), "biome test");

        assertEquals("carving/mushroom", settings.find(CarvingBiomeSettings.class).orElseThrow().biome());
        IrisPackResources resources = PackResourceFixture.empty(packFolder.toFile());
        assertEquals(Set.of("carving/mushroom"), settings.treeBiomes(resources));
        assertEquals(Set.of(), settings.usedBiomes(resources, ModuleSettings.NONE, "overworld"));
    }

    @Test
    public void theOverworldReadsItsUpperDimensionCarvingAndANumberIsTheCaveLavaHeight() throws IOException {
        ModuleSettings overworld = decoder.decode(Level.DIMENSION, file("dimensions/overworld.json"), "dimension overworld");
        assertFalse(overworld.find(UpperDimensionCarvingSettings.class).orElseThrow().enabled());
        assertTrue("the overworld writes no cave lava height", overworld.find(CaveLavaHeightSettings.class).isEmpty());

        ModuleSettings written = decoder.decode(Level.DIMENSION, new JSONObject("{\"caveLavaHeight\": 18, \"upperDimensionCarving\": true}"), "dimension test");
        assertEquals(18, written.find(CaveLavaHeightSettings.class).orElseThrow().height());
        assertTrue(written.find(UpperDimensionCarvingSettings.class).orElseThrow().enabled());
        assertEquals(8, CaveLavaHeightSettings.DEFAULT_HEIGHT);
    }

    @Test
    public void everyKeyIsOnlyReadAtTheLevelsTheOldFieldExistedAt() {
        assertTrue(decoder.keys(Level.DIMENSION).containsAll(List.of("caveProfile", "carving", "carvingEnabled", "caveLavaHeight", "upperDimensionCarving")));
        assertTrue(decoder.keys(Level.REGION).contains("caveProfile"));
        assertTrue(decoder.keys(Level.BIOME).containsAll(List.of("caveProfile", "carvingBiome", "caveMinDepthBelowSurface")));
        assertFalse(decoder.keys(Level.REGION).contains("carving"));
        assertFalse(decoder.keys(Level.DIMENSION).contains("carvingBiome"));
        assertFalse(decoder.keys(Level.DIMENSION).contains("caveMinDepthBelowSurface"));
    }

    private JSONObject file(String name) throws IOException {
        return new JSONObject(Files.readString(packFolder.resolve(name)));
    }
}
