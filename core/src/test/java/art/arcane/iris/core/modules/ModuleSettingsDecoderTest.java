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

import art.arcane.volmlib.util.json.JSONObject;
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
    }

    @Before
    public void loadPack() throws IOException {
        packFolder = folder.newFolder("pack").toPath();
        pack = LoadedPack.load(packFolder, ModuleHost.of(List.of(new MossModule())).copyingJarsTo(folder.newFolder("copies").toPath()));
        decoder = new ModuleSettingsDecoder(pack.catalog(), packFolder);
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
        assertEquals(Set.of("iris:moss", "moss"), decoder.keys(Target.BIOME));
        assertEquals(Set.of(), decoder.keys(Target.DIMENSION));
    }

    @Test
    public void moreSpecificSettingsReplaceLessSpecificOnesOfTheSameType() {
        ModuleSettings region = decoder.decode(Target.REGION, new JSONObject("{\"moss\": {\"radius\": 2}}"), "region hot");
        ModuleSettings biome = decoder.decode(Target.BIOME, new JSONObject("{\"moss\": {\"radius\": 5}}"), "biome desert/dunes");

        assertEquals(5, region.overriddenBy(biome).find(MossSettings.class).orElseThrow().radius);
        assertEquals(2, region.overriddenBy(ModuleSettings.NONE).find(MossSettings.class).orElseThrow().radius);
        assertEquals(5, ModuleSettings.NONE.overriddenBy(biome).find(MossSettings.class).orElseThrow().radius);
    }
}
