package art.arcane.iris.generation.terrain;

import art.arcane.volmlib.util.collection.KList;
import art.arcane.volmlib.util.collection.KMap;
import art.arcane.volmlib.util.collection.KSet;
import art.arcane.volmlib.util.json.JSONArray;
import art.arcane.volmlib.util.json.JSONObject;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

public class IrisDimensionBiomeTagTest {
    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void biomeTagWritesAreSortedAndDeduplicated() throws Exception {
        Path output = temporaryFolder.getRoot().toPath().resolve("allows_surface_slime_spawns.json");

        IrisDimension.writeBiomeTag(output, Set.of("overworld:swamp_b"));
        IrisDimension.writeBiomeTag(output, Set.of("overworld:swamp_a", "overworld:swamp_b"));

        JSONObject tag = new JSONObject(Files.readString(output, StandardCharsets.UTF_8));
        JSONArray values = tag.getJSONArray("values");
        assertFalse(tag.getBoolean("replace"));
        assertEquals(2, values.length());
        assertEquals("overworld:swamp_a", values.getString(0));
        assertEquals("overworld:swamp_b", values.getString(1));
    }

    @Test
    public void customBiomeTagUsesTheMinecraftBiomeTagPath() throws Exception {
        KList<String> tags = new KList<>();
        tags.add("minecraft:allows_surface_slime_spawns");
        KMap<String, KSet<String>> membership = new KMap<>();
        IrisDimension.collectBiomeTags(membership, "overworld:swamp", tags);
        IrisDimension.installBiomeTags(temporaryFolder.getRoot(), membership);

        Path output = temporaryFolder.getRoot().toPath()
                .resolve("data/minecraft/tags/worldgen/biome/allows_surface_slime_spawns.json");
        JSONObject tag = new JSONObject(Files.readString(output, StandardCharsets.UTF_8));

        assertEquals("overworld:swamp", tag.getJSONArray("values").getString(0));
    }

    @Test
    public void accumulatedTagsAreWrittenOncePerTagAndMergeWithExistingFiles() throws Exception {
        KList<String> tags = new KList<>();
        tags.add("minecraft:is_overworld");
        KMap<String, KSet<String>> first = new KMap<>();
        IrisDimension.collectBiomeTags(first, "overworld:swamp", tags);
        IrisDimension.collectBiomeTags(first, "overworld:plains", tags);
        IrisDimension.installBiomeTags(temporaryFolder.getRoot(), first);

        KMap<String, KSet<String>> second = new KMap<>();
        IrisDimension.collectBiomeTags(second, "nether:ash", tags);
        IrisDimension.installBiomeTags(temporaryFolder.getRoot(), second);

        Path output = temporaryFolder.getRoot().toPath()
                .resolve("data/minecraft/tags/worldgen/biome/is_overworld.json");
        JSONArray values = new JSONObject(Files.readString(output, StandardCharsets.UTF_8))
                .getJSONArray("values");

        assertEquals(3, values.length());
        assertEquals("nether:ash", values.getString(0));
        assertEquals("overworld:plains", values.getString(1));
        assertEquals("overworld:swamp", values.getString(2));
    }
}
