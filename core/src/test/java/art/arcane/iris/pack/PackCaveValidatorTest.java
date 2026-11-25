package art.arcane.iris.pack;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class PackCaveValidatorTest {
    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void aPackWithoutCavesHasNothingToReport() throws Exception {
        File pack = temporaryFolder.newFolder("pack");
        write(pack, "dimensions/main.json", "{\"regions\":[\"region\"]}");

        PackCaveValidator.Validation validation = PackCaveValidator.validate(pack);

        assertTrue(validation.errors().isEmpty());
        assertTrue(validation.warnings().isEmpty());
    }

    @Test
    public void everyFileThatStillHasACaveProfileGetsOneWarning() throws Exception {
        File pack = temporaryFolder.newFolder("pack");
        write(pack, "dimensions/main.json", "{\"caveProfile\":{\"enabled\":true}}");
        write(pack, "regions/hot.json", "{\"caveProfile\":{\"enabled\":false}}");
        write(pack, "biomes/nested/deep.json", "{\"caveProfile\":{}}");
        write(pack, "snippet/cave-profile/old.json", "{\"enabled\":true}");
        write(pack, "biomes/fine.json", "{}");

        PackCaveValidator.Validation validation = PackCaveValidator.validate(pack);

        assertTrue(validation.errors().isEmpty());
        assertEquals(validation.warnings().toString(), 4, validation.warnings().size());
        assertTrue(validation.warnings().contains("Dimension 'main' still has caveProfile, which was removed and carves no caves; list caves under \"caves\" instead."));
        assertTrue(validation.warnings().stream().anyMatch(warning -> warning.startsWith("Region 'hot' still has caveProfile")));
        assertTrue(validation.warnings().stream().anyMatch(warning -> warning.startsWith("Biome 'nested/deep' still has caveProfile")));
        assertTrue(validation.warnings().stream().anyMatch(warning -> warning.startsWith("Snippet 'snippet/cave-profile/old'")));
        assertTrue(validation.warnings().stream().allMatch(warning -> warning.contains("list caves under \"caves\"")));
    }

    @Test
    public void aCaveKeyThatNamesNoFileIsAnErrorWhereverItIsListed() throws Exception {
        File pack = temporaryFolder.newFolder("pack");
        write(pack, "caves/tunnels.json", "{\"tunnel\":{}}");
        write(pack, "caves/deep/halls.json", "{\"cavern\":{}}");
        write(pack, "dimensions/main.json", "{\"caves\":[\"tunnels\",\"deep/halls\",\"ghost\"]}");
        write(pack, "regions/hot.json", "{\"caves\":[\"missing\"]}");
        write(pack, "biomes/b.json", "{\"caves\":[\"tunnels\",\"gone\",7]}");

        PackCaveValidator.Validation validation = PackCaveValidator.validate(pack);

        assertEquals(validation.errors().toString(), List.of(
                "Dimension 'main' lists the cave 'ghost', but the pack has no caves/ghost.json.",
                "Region 'hot' lists the cave 'missing', but the pack has no caves/missing.json.",
                "Biome 'b' lists the cave 'gone', but the pack has no caves/gone.json.",
                "Biome 'b' caves[2] must be the name of a file in caves/."), validation.errors());
    }

    @Test
    public void valuesOutsideTheirBoundsAreErrorsNamingTheCaveAndTheKey() throws Exception {
        File pack = temporaryFolder.newFolder("pack");
        write(pack, "caves/bad.json", "{\"fade\": 200, \"tunnel\": {\"width\": {\"min\": 0, \"max\": 6, \"style\": \"snippet/style/wobble\"}}}");
        write(pack, "caves/good.json", "{\"fade\": 20, \"cavern\": {\"size\": 60, \"pillars\": {\"flare\": 2}}}");

        PackCaveValidator.Validation validation = PackCaveValidator.validate(pack);

        assertEquals(List.of("Cave 'bad' fade is 200; it must be at most 128.",
                "Cave 'bad' tunnel.width.min is 0; it must be at least 1."), validation.errors());
    }

    @Test
    public void aCaveThatIsNotJsonIsAnError() throws Exception {
        File pack = temporaryFolder.newFolder("pack");
        write(pack, "caves/broken.json", "{\"fade\": ");

        PackCaveValidator.Validation validation = PackCaveValidator.validate(pack);

        assertEquals(1, validation.errors().size());
        assertTrue(validation.errors().getFirst().startsWith("Cave 'broken' is not valid JSON"));
    }

    @Test
    public void theFullValidationBlocksOnAnUnknownCaveAndOnlyWarnsAboutALeftoverProfile() throws Exception {
        File pack = temporaryFolder.newFolder("pack");
        write(pack, "dimensions/main.json", "{\"regions\":[\"region\"],\"caves\":[\"ghost\"],\"caveProfile\":{\"enabled\":true}}");
        write(pack, "regions/region.json", "{\"landBiomes\":[\"biome\"]}");
        write(pack, "biomes/biome.json", "{\"name\":\"Biome\"}");

        PackValidationResult blocked = PackValidator.validate(pack);

        assertFalse(blocked.isLoadable());
        assertTrue(blocked.getBlockingErrors().toString(), blocked.getBlockingErrors().stream().anyMatch(error -> error.contains("lists the cave 'ghost'")));
        assertTrue(blocked.getWarnings().toString(), blocked.getWarnings().stream().anyMatch(warning -> warning.contains("still has caveProfile")));

        write(pack, "dimensions/main.json", "{\"regions\":[\"region\"],\"caveProfile\":{\"enabled\":true}}");
        PackValidationResult warned = PackValidator.validate(pack);

        assertTrue(warned.getBlockingErrors().toString(), warned.getBlockingErrors().stream().noneMatch(error -> error.contains("cave")));
        assertTrue(warned.getWarnings().stream().anyMatch(warning -> warning.contains("still has caveProfile")));
    }

    private void write(File root, String relative, String content) throws Exception {
        Path target = new File(root, relative).toPath();
        Files.createDirectories(target.getParent());
        Files.writeString(target, content, StandardCharsets.UTF_8);
    }
}
