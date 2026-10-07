package art.arcane.iris.pack;

import art.arcane.iris.generation.terrain.IrisDimensionType;
import art.arcane.iris.generation.terrain.IrisDimensionTypeOptions;
import art.arcane.iris.pack.datapack.IDataFixer;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class PackValidatorDimensionHeightTest {
    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void rejectsNonMultipleOfSixteenSpan() throws Exception {
        PackValidationResult result = validate("{\"regions\":[\"region\"],\"dimensionHeight\":{\"min\":-64,\"max\":300}}");

        assertFalse(result.isLoadable());
        assertTrue(result.getBlockingErrors().contains(
                "Dimension 'main' dimensionHeight span (max - min) is 364; it must be a multiple of 16."));
    }

    @Test
    public void rejectsMinYNotMultipleOfSixteen() throws Exception {
        PackValidationResult result = validate("{\"regions\":[\"region\"],\"dimensionHeight\":{\"min\":-60,\"max\":324}}");

        assertFalse(result.isLoadable());
        assertTrue(result.getBlockingErrors().contains(
                "Dimension 'main' dimensionHeight.min is -60; it must be a multiple of 16."));
    }

    @Test
    public void rejectsSpanOutsideBounds() throws Exception {
        assertTrue(validate("{\"regions\":[\"region\"],\"dimensionHeight\":{\"min\":0,\"max\":0}}")
                .getBlockingErrors().contains(
                        "Dimension 'main' dimensionHeight span (max - min) is 0; it must be between 16 and 4064."));
        assertTrue(validate("{\"regions\":[\"region\"],\"dimensionHeight\":{\"min\":-2032,\"max\":2048}}")
                .getBlockingErrors().contains(
                        "Dimension 'main' dimensionHeight span (max - min) is 4080; it must be between 16 and 4064."));
    }

    @Test
    public void rejectsMinYOutsideBounds() throws Exception {
        assertTrue(validate("{\"regions\":[\"region\"],\"dimensionHeight\":{\"min\":-2048,\"max\":-1664}}")
                .getBlockingErrors().contains(
                        "Dimension 'main' dimensionHeight.min is -2048; it must be between -2032 and 2031."));
    }

    @Test
    public void rejectsLogicalHeightAboveTotalHeight() throws Exception {
        PackValidationResult result = validate(
                "{\"regions\":[\"region\"],\"logicalHeight\":512,\"dimensionHeight\":{\"min\":-64,\"max\":320}}");

        assertFalse(result.isLoadable());
        assertTrue(result.getBlockingErrors().contains(
                "Dimension 'main' logicalHeight is 512; it cannot be greater than the dimension height of 384."));
    }

    @Test
    public void acceptsShippingOverworldShape() throws Exception {
        PackValidationResult result = validate(
                "{\"regions\":[\"region\"],\"logicalHeight\":512,\"dimensionHeight\":{\"min\":-256,\"max\":512}}");

        assertTrue(result.getBlockingErrors().toString(), result.isLoadable());
    }

    @Test
    public void acceptsDimensionWithoutExplicitHeight() throws Exception {
        PackValidationResult result = validate("{\"regions\":[\"region\"]}");

        assertTrue(result.getBlockingErrors().toString(), result.isLoadable());
    }

    @Test
    public void resolvesDimensionHeightSnippet() throws Exception {
        File pack = pack("{\"regions\":[\"region\"],\"dimensionHeight\":\"snippet/range/tall\"}");
        write(pack, "snippet/range/tall.json", "{\"min\":-60,\"max\":324}");

        PackValidationResult result = PackValidator.validate(pack);

        assertTrue(result.getBlockingErrors().contains(
                "Dimension 'main' dimensionHeight.min is -60; it must be a multiple of 16."));
    }

    @Test
    public void rejectsNullAndUnresolvedHeightRanges() throws Exception {
        for (String height : List.of("null", "\"missing\"", "\"snippet/range/missing\"", "[]")) {
            PackValidationResult result = validate("{\"regions\":[\"region\"],\"dimensionHeight\":" + height + "}");
            assertFalse(height + " " + result.getBlockingErrors(), result.isLoadable());
            assertTrue(result.getBlockingErrors().toString(), result.getBlockingErrors().stream()
                    .anyMatch(error -> error.contains("dimensionHeight")));
        }
    }

    @Test
    public void rejectsMalformedHeightSnippet() throws Exception {
        File pack = pack("{\"regions\":[\"region\"],\"dimensionHeight\":\"snippet/range/broken\"}");
        write(pack, "snippet/range/broken.json", "{");
        assertFalse(PackValidator.validate(pack).isLoadable());
    }

    @Test
    public void rejectsInvalidRangeNumbersWithoutInventingDefaults() throws Exception {
        for (String value : List.of("\"NaN\"", "\"Infinity\"", "\"not-a-number\"", "{}", "true")) {
            PackValidationResult result = validate("{\"regions\":[\"region\"],\"logicalHeight\":16,"
                    + "\"dimensionHeight\":{\"min\":" + value + ",\"max\":32}}");
            assertFalse(value + " " + result.getBlockingErrors(), result.isLoadable());
        }
    }

    @Test
    public void rejectsMalformedLogicalHeightWithoutTruncatingOrDefaulting() throws Exception {
        for (String value : List.of("16.5", "4294967552", "\"not-a-number\"", "{}", "true")) {
            PackValidationResult result = validate("{\"regions\":[\"region\"],\"logicalHeight\":" + value + "}");
            assertTrue(value + " " + result.getBlockingErrors(), result.getBlockingErrors().contains(
                    "Dimension 'main' logicalHeight must be an integer."));
        }
    }

    @Test
    public void acceptsNumericRangeStringsAndSnippetRerooting() throws Exception {
        File pack = pack("{\"regions\":[\"region\"],\"dimensionHeight\":\"snippet/tall\"}");
        write(pack, "snippet/range/tall.json", "{\"min\":\"-64\",\"max\":\"320\"}");
        PackValidationResult result = PackValidator.validate(pack);
        assertTrue(result.getBlockingErrors().toString(), result.isLoadable());
        PackValidationResult numericLogical = validate("{\"regions\":[\"region\"],\"logicalHeight\":\"16.0\","
                + "\"dimensionHeight\":{\"min\":16,\"max\":32}}");
        assertTrue(numericLogical.getBlockingErrors().toString(), numericLogical.isLoadable());
    }

    @Test
    public void rejectsMissingUpperDimensionButAllowsSelfReferenceAndDisabledUpper() throws Exception {
        PackValidationResult missing = validate("{\"regions\":[\"region\"],\"upperDimension\":\"absent\"}");
        assertTrue(missing.getBlockingErrors().contains(
                "Dimension 'main' upperDimension references missing dimension 'absent'."));
        for (String upper : List.of("main", "", "none", "NONE")) {
            PackValidationResult result = validate("{\"regions\":[\"region\"],\"upperDimension\":\"" + upper + "\"}");
            assertTrue(upper + " " + result.getBlockingErrors(), result.isLoadable());
        }
    }

    @Test
    public void validatorAgreesWithIrisDimensionTypeExactly() {
        int[][] triples = {
                // {minY, maxY, logicalHeight}
                {-64, 320, 256}, {-256, 512, 512}, {-64, 300, 256}, {-60, 324, 256},
                {0, 0, 0}, {-2032, 2048, 256}, {-2048, -1664, 0}, {0, 16, 16},
                {0, 16, 17}, {0, 16, -1}, {16, 32, 16}, {-64, 320, 384},
                {-64, 320, 385}, {2016, 2032, 16}, {2032, 2048, 16}, {-2032, -2016, 16},
                {8, 24, 16}, {0, 4064, 4064}, {0, 4080, 4064}, {-64, 4000, 4064},
        };

        for (int[] triple : triples) {
            int minY = triple[0];
            int maxY = triple[1];
            int logicalHeight = triple[2];

            boolean constructorRejects;
            try {
                new IrisDimensionType(IDataFixer.Dimension.OVERWORLD, new IrisDimensionTypeOptions(),
                        logicalHeight, maxY - minY, minY);
                constructorRejects = false;
            } catch (IllegalArgumentException e) {
                constructorRejects = true;
            }

            List<String> errors = new ArrayList<>();
            PackDimensionValidator.validateDimensionHeights(null, "main", new art.arcane.volmlib.util.json.JSONObject(
                    "{\"logicalHeight\":" + logicalHeight
                            + ",\"dimensionHeight\":{\"min\":" + minY + ",\"max\":" + maxY + "}}"), errors);

            assertEquals("triple (" + minY + "," + maxY + "," + logicalHeight + ") errors=" + errors,
                    constructorRejects, !errors.isEmpty());
        }
    }

    private PackValidationResult validate(String dimensionJson) throws Exception {
        return PackValidator.validate(pack(dimensionJson));
    }

    private File pack(String dimensionJson) throws Exception {
        File pack = temporaryFolder.newFolder("pack-" + System.nanoTime());
        write(pack, "dimensions/main.json", dimensionJson);
        write(pack, "regions/region.json", "{\"landBiomes\":[\"biome\"]}");
        write(pack, "biomes/biome.json", "{\"name\":\"Biome\"}");
        return pack;
    }

    private void write(File root, String relative, String content) throws Exception {
        Path target = new File(root, relative).toPath();
        Files.createDirectories(target.getParent());
        Files.writeString(target, content, StandardCharsets.UTF_8);
    }
}
