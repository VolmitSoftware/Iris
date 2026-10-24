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

import art.arcane.iris.engine.object.value.IrisPosition;
import art.arcane.volmlib.util.json.JSONObject;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import com.volmit.config.Level;
import com.volmit.IrisFeature;
import com.volmit.IrisModule;
import com.volmit.ModuleInfo;
import com.volmit.ModuleSetup;
import com.volmit.block.BlockSpec;
import com.volmit.block.BlockState;
import com.volmit.config.Range;
import com.volmit.config.Feature;
import com.volmit.config.Snippet;
import com.volmit.host.LoadedPack;
import com.volmit.host.ModuleHost;
import com.volmit.noise.NoiseStyle;
import com.volmit.noise.StyledRange;
import com.volmit.resource.ResourceId;
import com.volmit.stage.Stage;
import com.volmit.geometry.BlockPosition;
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
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class ModuleSettingsDecoderTest {
    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    private Path packFolder;
    private LoadedPack pack;
    private ModuleSettingsDecoder decoder;

    @Feature(id = "iris:moss", name = "Moss", key = "moss", targets = {Level.BIOME, Level.REGION})
    public static final class MossSettings implements IrisFeature {
        private int radius = 3;
        private BlockState block = new BlockState("minecraft:moss_block");
        private ResourceId palette = ResourceId.parse("iris:moss/default");
        private Patch patch = new Patch();
    }

    @Snippet("moss-patch")
    public static final class Patch {
        private int size = 1;
    }

    @Feature(id = "iris:moss/cover", name = "Cover", key = "mossCover", targets = Level.BIOME)
    public static final class CoverSettings implements IrisFeature {
        private double amount = 1;
    }

    @Feature(id = "iris:moss/marks", name = "Marks", key = "mossMarks", targets = Level.BIOME)
    public static final class MarksSettings implements IrisFeature {
        private List<IrisPosition> marks = List.of();
    }

    @Feature(id = "iris:moss/values", name = "Values", key = "mossValues", targets = Level.BIOME)
    public static final class ValuesSettings implements IrisFeature {
        private List<BlockSpec> palette = List.of();
        private NoiseStyle style = NoiseStyle.of("SIMPLEX");
        private StyledRange height = StyledRange.of(1, 2, NoiseStyle.of(NoiseStyle.FLAT));
        private Range light = Range.of(0, 15);
        private BlockPosition offset = new BlockPosition(0, 0, 0);
        private Map<String, Object> extra = Map.of();
    }

    public static final class MossModule implements IrisModule {
        @Override
        public ModuleInfo info() {
            return ModuleInfo.of("iris:moss", "Moss", "1");
        }

        @Override
        public void setup(ModuleSetup setup) {
            setup.stages().define(Stage.chunk("iris:moss/spread")).when(MossSettings.class).run(context -> {
            });
            var registry = setup.registry();
            registry.registerSettings(CoverSettings.class);
            registry.registerSettings(MarksSettings.class);
            registry.registerSettings(ValuesSettings.class);
        }
    }

    @Before
    public void loadPack() throws IOException {
        packFolder = folder.newFolder("pack").toPath();
        pack = LoadedPack.load(packFolder, ModuleHost.of(List.of(new MossModule()))
                .describingHostTypesIn(IrisModuleHost.HOST_TYPE_PACKAGE).copyingJarsTo(folder.newFolder("copies").toPath()));
        decoder = new ModuleSettingsDecoder(pack.catalog(), packFolder, Gson::new);
    }

    @After
    public void closePack() {
        pack.close();
    }

    private MossSettings moss(String json) {
        return decoder.decode(Level.BIOME, new JSONObject(json), "biome desert/dunes").find(MossSettings.class).orElseThrow();
    }

    @Test
    public void readsSettingsUnderTheirKeyKeepingOmittedDefaults() {
        MossSettings byKey = moss("{\"moss\": {\"radius\": 7, \"block\": \"minecraft:oak_log[axis=y]\"}}");
        MossSettings partial = moss("{\"moss\": {\"palette\": \"iris:moss/wet\"}}");

        assertEquals(7, byKey.radius);
        assertEquals(BlockState.parse("minecraft:oak_log[axis=y]"), byKey.block);
        assertEquals(ResourceId.parse("iris:moss/default"), byKey.palette);
        assertEquals(3, partial.radius);
        assertEquals(ResourceId.parse("iris:moss/wet"), partial.palette);
        assertEquals(1, partial.patch.size);
    }

    @Test
    public void declaresNothingWhereNoKeyIsPresent() {
        assertSame(ModuleSettings.NONE, decoder.decode(Level.BIOME, new JSONObject("{\"name\": \"Dunes\"}"), "biome desert/dunes"));
        assertSame(ModuleSettings.NONE, decoder.decode(Level.DIMENSION, new JSONObject("{\"moss\": {}}"), "dimension overworld"));
    }

    @Test
    public void theFeatureIdIsNotAKeyOfTheSettings() {
        assertSame(ModuleSettings.NONE, decoder.decode(Level.BIOME, new JSONObject("{\"iris:moss\": {\"radius\": 7}}"), "biome desert/dunes"));
    }

    @Test
    public void snippetTypesReadSnippetFilesFromTheirFolder() throws IOException {
        Path snippets = Files.createDirectories(packFolder.resolve("snippet/moss-patch"));
        Files.writeString(snippets.resolve("wide.json"), "{\"size\": 9}");

        assertEquals(9, moss("{\"moss\": {\"patch\": \"snippet/wide\"}}").patch.size);

        IllegalArgumentException missing = assertThrows(IllegalArgumentException.class,
                () -> moss("{\"moss\": {\"patch\": \"snippet/narrow\"}}"));
        assertTrue(missing.getMessage(), missing.getMessage().contains("Snippet snippet/narrow has no file snippet/moss-patch/narrow.json"));
    }

    @Test
    public void snippetReferencesMayNameTheSnippetFolderAsIrisSnippetsDo() throws IOException {
        Path snippets = Files.createDirectories(packFolder.resolve("snippet/moss-patch"));
        Files.writeString(snippets.resolve("wide.json"), "{\"size\": 9}");

        assertEquals(9, moss("{\"moss\": {\"patch\": \"snippet/moss-patch/wide\"}}").patch.size);
    }

    @Test
    public void apiValueTypesReadTheJsonIrisClassesRead() throws IOException {
        Path styles = Files.createDirectories(packFolder.resolve("snippet/style"));
        Files.writeString(styles.resolve("deepslate.json"), "{\"style\": \"STATIC\"}");
        ValuesSettings values = decoder.decode(Level.BIOME, new JSONObject("""
                {"mossValues": {
                  "palette": [{"block": "minecraft:oak_log", "data": {"axis": "y", "waterlogged": true, "level": 3}, "weight": 2,
                               "backup": {"block": "stone"}, "tileData": {"Delay": 20, "SpawnData": {"entity": {"id": "zombie"}}}},
                              {"block": "iron_ore"}],
                  "style": "snippet/style/deepslate",
                  "height": {"min": 4, "style": {"style": "SIMPLEX", "zoom": 8, "fracture": {"style": "PERLIN", "multiplier": 2}}},
                  "light": {"max": 7},
                  "offset": {"x": 1, "z": 3},
                  "extra": {"list": [1, "a", true]}
                }}
                """), "biome desert/dunes").find(ValuesSettings.class).orElseThrow();

        BlockSpec log = values.palette.getFirst();
        assertEquals("minecraft:oak_log", log.block());
        assertEquals(Map.of("axis", "y", "waterlogged", "true", "level", "3"), log.data());
        assertEquals(2, log.weight());
        assertEquals(Optional.of(BlockSpec.of("stone")), log.backup());
        assertEquals(Map.of("Delay", 20.0, "SpawnData", Map.of("entity", Map.of("id", "zombie"))), log.tileData());
        assertEquals(BlockSpec.of("iron_ore"), values.palette.get(1));
        assertEquals(NoiseStyle.of("STATIC"), values.style);
        assertEquals(4, values.height.min(), 0);
        assertEquals(32, values.height.max(), 0);
        assertEquals("SIMPLEX", values.height.style().style());
        assertEquals(8, values.height.style().zoom(), 0);
        assertEquals("PERLIN", values.height.style().fracture().orElseThrow().style());
        assertEquals(2, values.height.style().fracture().orElseThrow().multiplier(), 0);
        assertEquals(Range.of(16, 7), values.light);
        assertEquals(new BlockPosition(1, 0, 3), values.offset);
        assertEquals(Map.of("list", List.of(1.0, "a", true)), values.extra);
    }

    @Test
    public void badValuesNameTheSettingsKeyAndResource() {
        IllegalArgumentException failure = assertThrows(IllegalArgumentException.class,
                () -> moss("{\"moss\": {\"block\": \"stone\"}}"));

        assertTrue(failure.getMessage(), failure.getMessage().startsWith("Cannot read settings iris:moss under \"moss\" in biome desert/dunes: "));
    }

    @Test
    public void listsTheKeysModulesReadPerTarget() {
        assertEquals(Set.of("moss", "mossCover", "mossMarks", "mossValues"), decoder.keys(Level.BIOME));
        assertEquals(Set.of(), decoder.keys(Level.DIMENSION));
    }

    @Test
    public void settingsAreAlwaysAnObjectEvenWithOneOption() {
        assertEquals(0.25, decoder.decode(Level.BIOME, new JSONObject("{\"mossCover\": {\"amount\": 0.25}}"), "biome desert/dunes")
                .find(CoverSettings.class).orElseThrow().amount, 0);

        for (String bare : List.of("0.5", "[1, 2]", "true")) {
            IllegalArgumentException refused = assertThrows(IllegalArgumentException.class,
                    () -> decoder.decode(Level.BIOME, new JSONObject("{\"mossCover\": " + bare + "}"), "biome desert/dunes"));
            assertEquals("Cannot read settings iris:moss/cover under \"mossCover\" in biome desert/dunes: write the settings as an object,"
                    + " {\"<option>\": ...}", refused.getMessage());
        }
    }

    @Test
    public void irisClassesInBuiltInSettingsAreReadByThePacksGson() {
        IrisPosition marked = new IrisPosition(1, 2, 3);
        Gson iris = new GsonBuilder().registerTypeAdapter(IrisPosition.class, new TypeAdapter<IrisPosition>() {
            @Override
            public void write(JsonWriter out, IrisPosition value) throws IOException {
                out.value("marked");
            }

            @Override
            public IrisPosition read(JsonReader in) throws IOException {
                in.nextString();
                return marked;
            }
        }).create();
        ModuleSettingsDecoder withIrisGson = new ModuleSettingsDecoder(pack.catalog(), packFolder, () -> iris);

        List<IrisPosition> marks = withIrisGson.decode(Level.BIOME, new JSONObject("{\"mossMarks\": {\"marks\": [\"a\", \"b\"]}}"), "biome desert/dunes")
                .find(MarksSettings.class).orElseThrow().marks;

        assertEquals(List.of(marked, marked), marks);
    }
}
