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

import art.arcane.iris.modules.post.PostProcessingSettings;
import art.arcane.iris.modules.post.PostProcessingSlabsSettings;
import art.arcane.iris.modules.post.PostProcessingWallsSettings;
import art.arcane.iris.modules.decorant.DecorateSettings;
import art.arcane.iris.builtin.terrain.BedrockSettings;
import art.arcane.iris.modules.terrain.CaveCeilingLayersSettings;
import art.arcane.iris.modules.terrain.ExplodeBiomePaletteSizeSettings;
import art.arcane.iris.modules.terrain.ExplodeBiomePalettesSettings;
import art.arcane.iris.modules.terrain.FluidPaletteSettings;
import art.arcane.iris.builtin.terrain.LayersSettings;
import art.arcane.iris.modules.terrain.LockLayersMaxSettings;
import art.arcane.iris.modules.terrain.LockLayersSettings;
import art.arcane.iris.builtin.terrain.PaletteLayer;
import art.arcane.iris.modules.terrain.RockPaletteSettings;
import art.arcane.iris.modules.terrain.SeaLayersSettings;
import art.arcane.iris.modules.terrain.SlabSettings;
import art.arcane.iris.modules.terrain.WallSettings;
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
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

/**
 * The layers, slab, wall, sea layers, cave ceiling layers and locked layers of a biome, the rock and fluid palettes of a
 * dimension and its post processing switches, as packs have always written them (files of the overworld pack as it was
 * before the port, copied from the pack backup), read into the settings of the terrain and post modules. The dimension
 * keys and {@code lockLayersMax} are in no file of that pack, so they are written here.
 */
public class TerrainSettingsDecodingTest {
    private static final String PACK = "/art/arcane/iris/core/modules/pack-terrain";

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    private Path packFolder;
    private LoadedPack pack;
    private ModuleSettingsDecoder decoder;

    @Before
    public void loadBuiltIns() throws IOException, URISyntaxException {
        packFolder = Path.of(TerrainSettingsDecodingTest.class.getResource(PACK).toURI());
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
    public void aLockedBiomeReadsItsLayersAndWall() throws IOException {
        JSONObject biome = file("biomes/mesa/yellow.json");

        ModuleSettings settings = decoder.decode(Level.BIOME, biome, "biome mesa/yellow");

        List<PaletteLayer> layers = settings.find(LayersSettings.class).orElseThrow().layers();
        assertEquals(biome.getJSONArray("layers").length(), layers.size());
        PaletteLayer first = layers.getFirst();
        assertEquals(2, first.minHeight());
        assertEquals(3, first.maxHeight());
        assertEquals("STATIC", first.style().style());
        assertEquals(List.of(BlockSpec.of("minecraft:terracotta")), first.palette());
        assertEquals(5, first.zoom(), 0);
        assertTrue(first.slopeCondition().isDefault());
        assertTrue(settings.find(LockLayersSettings.class).orElseThrow().locked());
        assertTrue("lockLayersMax is not written in this file", settings.find(LockLayersMaxSettings.class).isEmpty());

        PaletteLayer wall = settings.find(WallSettings.class).orElseThrow().layer();
        assertEquals("NOWHERE", wall.style().style());
        assertEquals(List.of(BlockSpec.of("minecraft:red_terracotta"), BlockSpec.of("minecraft:gray_terracotta"), BlockSpec.of("minecraft:yellow_terracotta")),
                wall.palette());
        assertTrue(settings.find(SlabSettings.class).isEmpty());
    }

    @Test
    public void aSlabIsOneLayerWithIrisDefaults() throws IOException {
        ModuleSettings settings = decoder.decode(Level.BIOME, file("biomes/mountain/shore/beach.json"), "biome mountain/shore/beach");

        PaletteLayer slab = settings.find(SlabSettings.class).orElseThrow().layer();

        assertEquals("STATIC", slab.style().style());
        assertEquals(List.of(BlockSpec.of("minecraft:stone_slab"), BlockSpec.of("minecraft:air")), slab.palette());
        assertEquals(1, slab.minHeight());
        assertEquals(1, slab.maxHeight());
        assertEquals(5, slab.zoom(), 0);
        assertEquals(2, settings.find(LayersSettings.class).orElseThrow().layers().size());
        assertEquals(List.of(BlockSpec.of("minecraft:stone"), BlockSpec.of("minecraft:andesite")), settings.find(LayersSettings.class).orElseThrow().layers().getFirst().palette());
        assertTrue(settings.find(WallSettings.class).isEmpty());
    }

    @Test
    public void seaLayersKeepTheirNoiseAndWeights() throws IOException {
        JSONObject biome = file("biomes/frozen/sea/river.json");

        List<PaletteLayer> seaLayers = decoder.decode(Level.BIOME, biome, "biome frozen/sea/river").find(SeaLayersSettings.class).orElseThrow().layers();

        assertEquals(biome.getJSONArray("seaLayers").length(), seaLayers.size());
        PaletteLayer first = seaLayers.getFirst();
        assertEquals(3, first.minHeight());
        assertEquals(6, first.maxHeight());
        assertEquals("NOWHERE", first.style().style());
        assertEquals(1.16, first.zoom(), 0);
        assertEquals(5, first.palette().size());
        assertEquals(2, first.palette().getFirst().weight());
        assertEquals("minecraft:ice", first.palette().getFirst().block());
        assertEquals(1, first.palette().get(2).weight());
    }

    @Test
    public void caveCeilingLayersAreAListOfLayers() throws IOException {
        JSONObject biome = file("biomes/carving/polarity-grotto.json");

        List<PaletteLayer> ceiling = decoder.decode(Level.BIOME, biome, "biome carving/polarity-grotto")
                .find(CaveCeilingLayersSettings.class).orElseThrow().layers();

        assertEquals(1, ceiling.size());
        assertEquals(1, ceiling.getFirst().minHeight());
        assertEquals(2, ceiling.getFirst().maxHeight());
        assertEquals(List.of(BlockSpec.of("minecraft:tuff").withWeight(4), BlockSpec.of("minecraft:moss_block").withWeight(3),
                BlockSpec.of("minecraft:andesite").withWeight(3)), ceiling.getFirst().palette());
    }

    @Test
    public void lockLayersMaxIsANumberAndLockLayersABoolean() {
        ModuleSettings settings = decoder.decode(Level.BIOME, new JSONObject("{\"lockLayers\": false, \"lockLayersMax\": 4}"), "biome mesa");

        assertFalse(settings.find(LockLayersSettings.class).orElseThrow().locked());
        assertEquals(4, settings.find(LockLayersMaxSettings.class).orElseThrow().layers());
        assertEquals(7, new LockLayersMaxSettings().layers());
    }

    @Test
    public void aLayerWrittenWithoutOptionsHasIrisDefaults() {
        ModuleSettings settings = decoder.decode(Level.BIOME, new JSONObject("{\"layers\": [{}], \"slab\": {}}"), "biome plain");

        PaletteLayer layer = settings.find(LayersSettings.class).orElseThrow().layers().getFirst();
        assertEquals(List.of(BlockSpec.of("grass_block")), layer.palette());
        assertEquals(1, layer.minHeight());
        assertEquals(1, layer.maxHeight());
        assertEquals(5, layer.zoom(), 0);
        assertEquals("a slab written as {} is a layer like any other", List.of(BlockSpec.of("grass_block")),
                settings.find(SlabSettings.class).orElseThrow().layer().palette());
        assertTrue("a biome that writes nothing has the one grass layer", new LayersSettings().layers().size() == 1);
        assertTrue(new SlabSettings().layer().palette().isEmpty());
        assertTrue(new WallSettings().layer().palette().isEmpty());
    }

    @Test
    public void aLayerMayBeASnippet() throws IOException {
        Path packFolder = folder.newFolder("snippet-pack").toPath();
        Path snippets = Files.createDirectories(packFolder.resolve("snippet/iris/terrain/biome-palette"));
        Files.writeString(snippets.resolve("sandy.json"), "{\"minHeight\": 2, \"maxHeight\": 4, \"palette\": [{\"block\": \"sand\"}]}");
        ModuleSettingsDecoder snippetDecoder = new ModuleSettingsDecoder(pack.catalog(), packFolder, Gson::new);

        PaletteLayer layer = snippetDecoder.decode(Level.BIOME, new JSONObject("{\"layers\": [\"snippet/iris/terrain/biome-palette/sandy\", \"snippet/sandy\"]}"), "biome dunes")
                .find(LayersSettings.class).orElseThrow().layers().getFirst();

        assertEquals(4, layer.maxHeight());
        assertEquals(List.of(BlockSpec.of("sand")), layer.palette());
    }

    @Test
    public void theDimensionReadsItsPalettesAndPostProcessingSwitches() {
        ModuleSettings settings = decoder.decode(Level.DIMENSION, new JSONObject("""
                {"rockPalette": {"style": {"style": "SIMPLEX", "zoom": 2}, "zoom": 9,
                                 "palette": [{"block": "minecraft:stone", "weight": 3}, {"block": "minecraft:granite"}]},
                 "fluidPalette": {"palette": [{"block": "minecraft:lava"}]},
                 "postProcessing": false, "postProcessingSlabs": false, "postProcessingWalls": true}
                """), "dimension nether");

        var rock = settings.find(RockPaletteSettings.class).orElseThrow().palette();
        assertEquals("SIMPLEX", rock.style().style());
        assertEquals(2, rock.style().zoom(), 0);
        assertEquals(9, rock.zoom(), 0);
        assertEquals(List.of(BlockSpec.of("minecraft:stone").withWeight(3), BlockSpec.of("minecraft:granite")), rock.palette());
        assertEquals(List.of(BlockSpec.of("minecraft:lava")), settings.find(FluidPaletteSettings.class).orElseThrow().palette().palette());
        assertFalse(settings.find(PostProcessingSettings.class).orElseThrow().enabled());
        assertFalse(settings.find(PostProcessingSlabsSettings.class).orElseThrow().enabled());
        assertTrue(settings.find(PostProcessingWallsSettings.class).orElseThrow().enabled());
    }

    @Test
    public void theDimensionReadsItsBedrockDecoratingAndExplodedPaletteKeysAsPacksWriteThem() {
        ModuleSettings settings = decoder.decode(Level.DIMENSION, new JSONObject(
                "{\"bedrock\": false, \"decorate\": false, \"explodeBiomePalettes\": true, \"explodeBiomePaletteSize\": 5}"), "dimension flat");

        assertFalse(settings.find(BedrockSettings.class).orElseThrow().bedrock());
        assertFalse(settings.find(DecorateSettings.class).orElseThrow().decorate());
        assertTrue(settings.find(ExplodeBiomePalettesSettings.class).orElseThrow().explode());
        assertEquals(5, settings.find(ExplodeBiomePaletteSizeSettings.class).orElseThrow().size());
        for (String key : Set.of("bedrock", "decorate", "explodeBiomePalettes", "explodeBiomePaletteSize")) {
            assertTrue(key, decoder.keys(Level.DIMENSION).contains(key));
            assertFalse(key + " is no field of Iris any more", ModuleTargets.reservedFields().get(Level.DIMENSION).contains(key));
        }
    }

    @Test
    public void aDimensionThatWritesNothingIsStoneAndWaterWithPostProcessingOn() {
        ModuleSettings settings = decoder.decode(Level.DIMENSION, new JSONObject("{}"), "dimension overworld");

        assertTrue(settings.find(RockPaletteSettings.class).isEmpty());
        assertEquals(List.of(BlockSpec.of("stone")), new RockPaletteSettings().palette().palette());
        assertEquals(List.of(BlockSpec.of("water")), new FluidPaletteSettings().palette().palette());
        assertTrue(new PostProcessingSettings().enabled());
        assertTrue(new PostProcessingSlabsSettings().enabled());
        assertTrue(new PostProcessingWallsSettings().enabled());
        assertTrue("bedrock and decorating are on, and the palettes unexploded, unless written", new BedrockSettings().bedrock()
                && new DecorateSettings().decorate() && !new ExplodeBiomePalettesSettings().explode());
    }

    @Test
    public void aBooleanWrittenAsAnObjectIsRefused() {
        IllegalArgumentException refused = assertThrows(IllegalArgumentException.class,
                () -> decoder.decode(Level.BIOME, new JSONObject("{\"lockLayers\": {\"locked\": true}}"), "biome mesa"));

        assertTrue(refused.getMessage(), refused.getMessage().startsWith("Cannot read settings iris:terrain/lock-layers under \"lockLayers\" in biome mesa:"));
    }

    @Test
    public void layersWrittenAsAnObjectOfOptionsAreRefused() {
        IllegalArgumentException refused = assertThrows(IllegalArgumentException.class,
                () -> decoder.decode(Level.BIOME, new JSONObject("{\"layers\": {\"layers\": []}}"), "biome mesa"));

        assertEquals("Cannot read settings iris:terrain/layers under \"layers\" in biome mesa: write a list, [...], of the layers, not an object of options",
                refused.getMessage());
    }

    @Test
    public void theTerrainAndPostKeysAreModuleKeysAndNoLongerFieldsOfIris() {
        assertTrue(decoder.keys(Level.BIOME).containsAll(Set.of("layers", "seaLayers", "caveCeilingLayers", "slab", "wall", "lockLayers", "lockLayersMax")));
        assertTrue(decoder.keys(Level.DIMENSION).containsAll(Set.of("rockPalette", "fluidPalette", "postProcessing", "postProcessingSlabs", "postProcessingWalls")));
        for (String key : Set.of("layers", "seaLayers", "caveCeilingLayers", "slab", "wall", "lockLayers", "lockLayersMax")) {
            assertFalse(key, ModuleTargets.reservedFields().get(Level.BIOME).contains(key));
        }
        for (String key : Set.of("rockPalette", "fluidPalette", "postProcessing", "postProcessingSlabs", "postProcessingWalls")) {
            assertFalse(key, ModuleTargets.reservedFields().get(Level.DIMENSION).contains(key));
        }
    }

    @Test
    public void everyTopLevelKeyOfTheseFilesIsAFieldOfIrisOrAKeyOfAModule() throws IOException {
        Map<String, Level> files = Map.of("biomes/mesa/yellow.json", Level.BIOME, "biomes/mountain/shore/beach.json", Level.BIOME,
                "biomes/frozen/sea/river.json", Level.BIOME, "biomes/carving/polarity-grotto.json", Level.BIOME);

        for (Map.Entry<String, Level> file : files.entrySet()) {
            Set<String> unknown = new TreeSet<>(file(file.getKey()).keySet());
            unknown.removeAll(ModuleTargets.reservedFields().get(file.getValue()));
            unknown.removeAll(decoder.keys(file.getValue()));
            assertEquals(file.getKey(), Set.of(), unknown);
        }
    }

    private JSONObject file(String name) throws IOException {
        return new JSONObject(Files.readString(packFolder.resolve(name)));
    }
}
