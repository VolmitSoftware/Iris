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
import com.volmit.FeatureRegistry;
import com.volmit.FeatureAttachment.Target;
import com.volmit.IrisFeature;
import com.volmit.IrisModule;
import com.volmit.ModuleInfo;
import com.volmit.block.BlockState;
import com.volmit.config.Feature;
import com.volmit.config.Snippet;
import com.volmit.host.LoadedPack;
import com.volmit.host.ModuleHost;
import com.volmit.resource.ResourceId;
import com.volmit.stage.Stage;
import com.volmit.stage.StageGraph;
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
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class ModuleSettingsDecoderTest {
    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    private Path packFolder;
    private LoadedPack pack;
    private ModuleSettingsDecoder decoder;

    @Feature(id = "iris:moss", name = "Moss", alias = "moss", targets = {Target.BIOME, Target.REGION})
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

    @Feature(id = "iris:moss/cover", name = "Cover", alias = "cover", targets = Target.BIOME)
    public static final class CoverSettings implements IrisFeature {
        private double amount = 1;
    }

    @Feature(id = "iris:moss/marks", name = "Marks", alias = "marks", targets = Target.BIOME)
    public static final class MarksSettings implements IrisFeature {
        private List<IrisPosition> marks = List.of();
    }

    public static final class MossModule implements IrisModule {
        @Override
        public ModuleInfo info() {
            return ModuleInfo.of("iris:moss", "Moss", "1");
        }

        @Override
        public void install(StageGraph graph) {
            graph.define(Stage.chunk("iris:moss/spread")).when(MossSettings.class).run((context, moss) -> {
            });
        }

        @Override
        public void register(FeatureRegistry registry) {
            registry.registerSettings(CoverSettings.class);
            registry.registerSettings(MarksSettings.class);
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
        return decoder.decode(Target.BIOME, new JSONObject(json), "biome desert/dunes").find(MossSettings.class).orElseThrow();
    }

    @Test
    public void readsSettingsUnderTheirIdOrBuiltInAliasKeepingOmittedDefaults() {
        MossSettings byId = moss("{\"iris:moss\": {\"radius\": 7, \"block\": \"minecraft:oak_log[axis=y]\"}}");
        MossSettings byAlias = moss("{\"moss\": {\"palette\": \"iris:moss/wet\"}}");

        assertEquals(7, byId.radius);
        assertEquals(BlockState.parse("minecraft:oak_log[axis=y]"), byId.block);
        assertEquals(ResourceId.parse("iris:moss/default"), byId.palette);
        assertEquals(3, byAlias.radius);
        assertEquals(ResourceId.parse("iris:moss/wet"), byAlias.palette);
        assertEquals(1, byAlias.patch.size);
    }

    @Test
    public void declaresNothingWhereNoKeyIsPresent() {
        assertSame(ModuleSettings.NONE, decoder.decode(Target.BIOME, new JSONObject("{\"name\": \"Dunes\"}"), "biome desert/dunes"));
        assertSame(ModuleSettings.NONE, decoder.decode(Target.DIMENSION, new JSONObject("{\"moss\": {}}"), "dimension overworld"));
    }

    @Test
    public void bothTheAliasAndTheIdInOneObjectFail() {
        IllegalArgumentException failure = assertThrows(IllegalArgumentException.class,
                () -> moss("{\"moss\": {}, \"iris:moss\": {}}"));

        assertEquals("biome desert/dunes sets both \"moss\" and \"iris:moss\", the alias and the id of settings iris:moss; keep one",
                failure.getMessage());
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
    public void badValuesNameTheSettingsKeyAndResource() {
        IllegalArgumentException failure = assertThrows(IllegalArgumentException.class,
                () -> moss("{\"moss\": {\"block\": \"stone\"}}"));

        assertTrue(failure.getMessage(), failure.getMessage().startsWith("Cannot read settings iris:moss under \"moss\" in biome desert/dunes: "));
    }

    @Test
    public void listsTheKeysModulesReadPerTarget() {
        assertEquals(Set.of("iris:moss", "moss", "iris:moss/cover", "cover", "iris:moss/marks", "marks"), decoder.keys(Target.BIOME));
        assertEquals(Set.of(), decoder.keys(Target.DIMENSION));
    }

    @Test
    public void settingsWithOneOptionReadABareValueAsThatOption() {
        assertEquals(0.5, decoder.decode(Target.BIOME, new JSONObject("{\"cover\": 0.5}"), "biome desert/dunes")
                .find(CoverSettings.class).orElseThrow().amount, 0);
        assertEquals(0.25, decoder.decode(Target.BIOME, new JSONObject("{\"cover\": {\"amount\": 0.25}}"), "biome desert/dunes")
                .find(CoverSettings.class).orElseThrow().amount, 0);

        IllegalArgumentException several = assertThrows(IllegalArgumentException.class, () -> moss("{\"moss\": 4}"));
        assertEquals("Cannot read settings iris:moss under \"moss\" in biome desert/dunes: write an object; only settings with exactly"
                + " one option may be written as that option's value, and these have 4", several.getMessage());
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

        List<IrisPosition> marks = withIrisGson.decode(Target.BIOME, new JSONObject("{\"marks\": [\"a\", \"b\"]}"), "biome desert/dunes")
                .find(MarksSettings.class).orElseThrow().marks;

        assertEquals(List.of(marked, marked), marks);
    }
}
