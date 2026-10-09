package art.arcane.iris.core.pack;

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

public class PackBiomeLayerValidatorTest {
    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void rejectsMoreCeilingLayersThanLayers() throws Exception {
        File pack = temporaryFolder.newFolder("pack");
        write(pack, "biomes/nested/cave.json",
                "{\"layers\":[{}],\"caveCeilingLayers\":[{},{},{}]}");

        assertEquals(List.of(
                "Biome 'nested/cave' declares 3 caveCeilingLayers but only 1 layers. caveCeilingLayers reuses the layers height generators and must not have more entries."
        ), PackBiomeLayerValidator.validateCeilingLayerCounts(new File(pack, "biomes")));
    }

    @Test
    public void respectsIndependentDefaultsWhenFieldsAreAbsent() throws Exception {
        File pack = temporaryFolder.newFolder("pack");
        write(pack, "biomes/defaulted.json", "{\"name\":\"Defaulted\"}");
        write(pack, "biomes/implicit.json", "{\"caveCeilingLayers\":[{},{}]}");

        assertEquals(List.of(
                "Biome 'implicit' declares 2 caveCeilingLayers but only 1 layers. caveCeilingLayers reuses the layers height generators and must not have more entries."
        ), PackBiomeLayerValidator.validateCeilingLayerCounts(new File(pack, "biomes")));
    }

    @Test
    public void acceptsEqualOrFewerCeilingLayers() throws Exception {
        File pack = temporaryFolder.newFolder("pack");
        write(pack, "biomes/ok.json", "{\"layers\":[{},{},{}],\"caveCeilingLayers\":[{},{}]}");

        assertTrue(PackBiomeLayerValidator.validateCeilingLayerCounts(new File(pack, "biomes")).isEmpty());
    }

    @Test
    public void rejectsNonArrayLayerFields() throws Exception {
        File pack = temporaryFolder.newFolder("pack");
        write(pack, "biomes/bad.json", "{\"layers\":7,\"caveCeilingLayers\":\"nope\"}");

        assertEquals(List.of(
                "Biome 'bad' layers must be an array.",
                "Biome 'bad' caveCeilingLayers must be an array."
        ), PackBiomeLayerValidator.validateCeilingLayerCounts(new File(pack, "biomes")));
    }

    @Test
    public void ceilingLayerOverflowBlocksFullPackValidation() throws Exception {
        File pack = temporaryFolder.newFolder("pack");
        write(pack, "dimensions/main.json", "{\"regions\":[\"region\"]}");
        write(pack, "regions/region.json", "{\"landBiomes\":[\"biome\"]}");
        write(pack, "biomes/biome.json", "{\"name\":\"Biome\",\"layers\":[{}],\"caveCeilingLayers\":[{},{}]}");

        PackValidationResult result = PackValidator.validate(pack);

        assertFalse(result.isLoadable());
        assertTrue(result.getBlockingErrors().contains(
                "Biome 'biome' declares 2 caveCeilingLayers but only 1 layers. caveCeilingLayers reuses the layers height generators and must not have more entries."));
    }

    @Test
    public void rejectsEmbeddedDecoratorWithoutPalette() throws Exception {
        File pack = temporaryFolder.newFolder("pack");
        write(pack, "biomes/bad.json", "{\"decorators\":[{\"block\":\"minecraft:magma_block\"}]}");

        assertEquals(List.of(
                "Biome 'bad' decorators[0] must declare a non-empty palette."
        ), PackBiomeLayerValidator.validateDecoratorPalettes(
                new File(pack, "biomes"), new File(pack, "snippet/decorator")));
    }

    @Test
    public void rejectsDecoratorSnippetWithoutPalette() throws Exception {
        File pack = temporaryFolder.newFolder("pack");
        write(pack, "snippet/decorator/bad.json", "{\"chance\":0.5}");

        assertEquals(List.of(
                "Decorator snippet 'bad' must declare a non-empty palette."
        ), PackBiomeLayerValidator.validateDecoratorPalettes(
                new File(pack, "biomes"), new File(pack, "snippet/decorator")));
    }

    @Test
    public void acceptsDecoratorPalettesAndSnippetReferences() throws Exception {
        File pack = temporaryFolder.newFolder("pack");
        write(pack, "biomes/ok.json",
                "{\"decorators\":[{\"palette\":[{\"block\":\"minecraft:magma_block\"}]},\"snippet/decorator/ok\"]}");
        write(pack, "snippet/decorator/ok.json",
                "{\"palette\":[{\"block\":\"minecraft:magma_block\"}]}");

        assertTrue(PackBiomeLayerValidator.validateDecoratorPalettes(
                new File(pack, "biomes"), new File(pack, "snippet/decorator")).isEmpty());
    }

    private void write(File root, String relative, String content) throws Exception {
        Path target = new File(root, relative).toPath();
        Files.createDirectories(target.getParent());
        Files.writeString(target, content, StandardCharsets.UTF_8);
    }
}
