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
import static org.junit.Assert.assertTrue;

public class PackGeneratorDuplicateValidatorTest {
    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void warnsWhenTwoReferencedGeneratorsAreContentIdentical() throws Exception {
        File pack = temporaryFolder.newFolder("pack");
        write(pack, "generators/a.json", "{\"seed\": 1,\n  \"zoom\": 2}");
        write(pack, "generators/b.json", "{\"zoom\":2,\"seed\":1}");
        write(pack, "biomes/one.json", "{\"generators\":[{\"generator\":\"a\"}]}");
        write(pack, "biomes/two.json", "{\"generators\":[{\"generator\":\"b\"}]}");

        List<String> warnings = PackGeneratorDuplicateValidator.validateDuplicateGenerators(pack);

        assertEquals(1, warnings.size());
        assertTrue(warnings.get(0).startsWith("Generators a, b have identical content"));
    }

    @Test
    public void doesNotWarnWhenOnlyOneDuplicateIsReferenced() throws Exception {
        File pack = temporaryFolder.newFolder("pack");
        write(pack, "generators/a.json", "{\"seed\":1}");
        write(pack, "generators/b.json", "{\"seed\":1}");
        write(pack, "biomes/one.json", "{\"generators\":[{\"generator\":\"a\"}]}");

        assertTrue(PackGeneratorDuplicateValidator.validateDuplicateGenerators(pack).isEmpty());
    }

    @Test
    public void doesNotWarnWhenGeneratorsDifferBySeed() throws Exception {
        File pack = temporaryFolder.newFolder("pack");
        write(pack, "generators/a.json", "{\"seed\":1}");
        write(pack, "generators/b.json", "{\"seed\":2}");
        write(pack, "biomes/one.json", "{\"generators\":[{\"generator\":\"a\"},{\"generator\":\"b\"}]}");

        assertTrue(PackGeneratorDuplicateValidator.validateDuplicateGenerators(pack).isEmpty());
    }

    @Test
    public void treatsNumericFormattingAsIdentical() throws Exception {
        File pack = temporaryFolder.newFolder("pack");
        write(pack, "generators/a.json", "{\"zoom\":1}");
        write(pack, "generators/b.json", "{\"zoom\":1.0}");
        write(pack, "biomes/one.json", "{\"generators\":[{\"generator\":\"a\"},{\"generator\":\"b\"}]}");

        assertEquals(1, PackGeneratorDuplicateValidator.validateDuplicateGenerators(pack).size());
    }

    @Test
    public void resolvesSnippetGeneratorLinks() throws Exception {
        File pack = temporaryFolder.newFolder("pack");
        write(pack, "generators/a.json", "{\"seed\":1}");
        write(pack, "generators/b.json", "{\"seed\":1}");
        write(pack, "biomes/one.json", "{\"generators\":[\"snippet/generator-layer/x\",{\"generator\":\"b\"}]}");
        write(pack, "snippet/generator-layer/x.json", "{\"generator\":\"a\"}");

        assertEquals(1, PackGeneratorDuplicateValidator.validateDuplicateGenerators(pack).size());
    }

    @Test
    public void warningOrderIsStableAndSorted() throws Exception {
        File pack = temporaryFolder.newFolder("pack");
        write(pack, "generators/c.json", "{\"seed\":1}");
        write(pack, "generators/a.json", "{\"seed\":1}");
        write(pack, "generators/b.json", "{\"seed\":1}");
        write(pack, "biomes/one.json",
                "{\"generators\":[{\"generator\":\"c\"},{\"generator\":\"a\"},{\"generator\":\"b\"}]}");

        List<String> warnings = PackGeneratorDuplicateValidator.validateDuplicateGenerators(pack);

        assertEquals(1, warnings.size());
        assertTrue(warnings.get(0).startsWith("Generators a, b, c have identical content"));
    }

    @Test
    public void duplicatesSurfaceAsPackWarningsNotErrors() throws Exception {
        File pack = temporaryFolder.newFolder("pack");
        write(pack, "dimensions/main.json", "{\"regions\":[\"region\"]}");
        write(pack, "regions/region.json", "{\"landBiomes\":[\"biome\"]}");
        write(pack, "biomes/biome.json", "{\"name\":\"Biome\",\"generators\":[{\"generator\":\"a\"},{\"generator\":\"b\"}]}");
        write(pack, "generators/a.json", "{\"seed\":1}");
        write(pack, "generators/b.json", "{\"seed\":1}");

        PackValidationResult result = PackValidator.validate(pack);

        assertTrue(result.isLoadable());
        assertTrue(result.getWarnings().stream().anyMatch(w -> w.startsWith("Generators a, b have identical content")));
    }

    private void write(File root, String relative, String content) throws Exception {
        Path target = new File(root, relative).toPath();
        Files.createDirectories(target.getParent());
        Files.writeString(target, content, StandardCharsets.UTF_8);
    }
}
