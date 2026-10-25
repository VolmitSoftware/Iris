package art.arcane.iris.pack;

import art.arcane.iris.generation.hydrology.config.IrisHydrology;
import art.arcane.iris.pack.loading.IrisData;
import art.arcane.iris.testsupport.CompatTestPlatform;
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
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class PackHydrologyValidatorTest {
    private static final String WATER_ONLY = "{\"enabled\": true, \"fluids\": [{\"name\": \"water\"}]}";

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void acceptsDefaultAndFullyWrittenHydrology() throws Exception {
        assertTrue(validate(pack(dimension("{\"enabled\": true}", ""))).errors().isEmpty());
        PackHydrologyValidator.Validation full = validate(pack(dimension("""
                {
                  "enabled": true,
                  "tiers": [
                    {"name": "stream", "minCatchment": 1500, "halfWidth": [0.5, 2], "fluid": "water"},
                    {"name": "river", "minCatchment": 20000, "halfWidth": [2, 64], "fluid": "lava"}
                  ],
                  "ponds": {"freeboard": [0, 2], "fluid": "lava",
                            "seeded": {"radius": [4, 12], "depth": [2, 8], "channelSpacing": [48, 160]}},
                  "underground": {
                    "seaInlets": {"length": [24, 96], "halfWidth": [2, 5.5], "depth": [3, 6], "headroom": [0, 3],
                                  "chamberRadius": [4, 9]},
                    "cavePools": {"freeboard": [0, 1], "fluid": "water"}
                  },
                  "fluids": [
                    {"name": "water", "palette": {"palette": [{"block": "minecraft:water"}]}},
                    {"name": "lava", "palette": {"palette": [{"block": "minecraft:lava"}]}}
                  ]
                }
                """, "\"hydrologyPolicy\": {\"fluid\": \"lava\"},")));

        assertTrue(full.errors().toString(), full.errors().isEmpty());
        assertTrue(full.warnings().isEmpty());
    }

    @Test
    public void rejectsMalformedAndInvertedRanges() throws Exception {
        PackHydrologyValidator.Validation validation = validate(pack(dimension("""
                {
                  "enabled": true,
                  "tiers": [{"name": "stream", "halfWidth": [2]}, {"name": "river", "minCatchment": 9, "halfWidth": [8, 2]}],
                  "ponds": {"freeboard": "1", "seeded": {"radius": [4, 12, 16], "depth": [2.5, 8]}},
                  "underground": {"seaInlets": {"length": ["24", 96]}}
                }
                """, "")));

        assertContains(validation.errors(), "Dimension 'main'.hydrology.tiers[0].halfWidth must be written as [min, max].");
        assertContains(validation.errors(), "tiers[1].halfWidth has its min above its max.");
        assertContains(validation.errors(), "ponds.freeboard must be written as [min, max].");
        assertContains(validation.errors(), "ponds.seeded.radius must be written as [min, max].");
        assertContains(validation.errors(), "ponds.seeded.depth must hold whole numbers.");
        assertContains(validation.errors(), "underground.seaInlets.length must be written as [min, max].");
        assertEquals(validation.errors().toString(), 6, validation.errors().size());
    }

    @Test
    public void rejectsRangesBelowTheirFloorOrAboveTheHalfWidthCap() throws Exception {
        PackHydrologyValidator.Validation validation = validate(pack(dimension("""
                {
                  "enabled": true,
                  "tiers": [{"name": "stream", "halfWidth": [0, 2]}, {"name": "river", "minCatchment": 9, "halfWidth": [2, 65]}],
                  "ponds": {"freeboard": [-1, 2], "seeded": {"channelSpacing": [0, 160]}},
                  "underground": {"seaInlets": {"headroom": [0, 3], "chamberRadius": [-2, 9]}, "cavePools": {"freeboard": [-1, 0]}}
                }
                """, "")));

        assertContains(validation.errors(), "tiers[0].halfWidth must stay above 0.");
        assertContains(validation.errors(), "tiers[1].halfWidth must not go above 64.");
        assertContains(validation.errors(), "ponds.freeboard must not go below 0.");
        assertContains(validation.errors(), "ponds.seeded.channelSpacing must stay above 0.");
        assertContains(validation.errors(), "underground.seaInlets.chamberRadius must stay above 0.");
        assertContains(validation.errors(), "underground.cavePools.freeboard must not go below 0.");
        assertEquals(validation.errors().toString(), 6, validation.errors().size());
    }

    @Test
    public void rejectsFluidReferencesTheFluidProfilesDoNotDefine() throws Exception {
        File pack = pack(dimension("""
                {
                  "enabled": true,
                  "tiers": [{"name": "stream", "fluid": "acid"}],
                  "ponds": {"fluid": "lava"},
                  "underground": {"cavePools": {"fluid": "mud"}},
                  "fluids": [{"name": "water"}]
                }
                """, "\"hydrologyPolicy\": {\"fluid\": \"brine\"},"));
        write(pack, "regions/region.json", """
                {"landBiomes": ["biome"], "hydrologyPolicy": {"fluid": "tar"}}
                """);
        write(pack, "biomes/biome.json", """
                {"name": "Biome", "children": ["child"], "hydrologyPolicy": {"fluid": "water"}}
                """);
        write(pack, "biomes/child.json", """
                {"name": "Child", "hydrologyPolicy": {"fluid": "oil"}}
                """);
        write(pack, "biomes/unreached.json", """
                {"name": "Unreached", "hydrologyPolicy": {"fluid": "ink"}}
                """);

        List<String> errors = validate(pack).errors();

        assertContains(errors, "Dimension 'main'.hydrology.tiers[0].fluid names fluid 'acid', which Dimension 'main'.hydrology.fluids does not define [water].");
        assertContains(errors, "hydrology.ponds.fluid names fluid 'lava'");
        assertContains(errors, "hydrology.underground.cavePools.fluid names fluid 'mud'");
        assertContains(errors, "Dimension 'main'.hydrologyPolicy.fluid names fluid 'brine'");
        assertContains(errors, "Region 'region'.hydrologyPolicy.fluid names fluid 'tar'");
        assertContains(errors, "Biome 'child'.hydrologyPolicy.fluid names fluid 'oil'");
        assertEquals(errors.toString(), 6, errors.size());
    }

    @Test
    public void checksBiomesReachedOnlyThroughABiomeImageMap() throws Exception {
        File pack = pack(dimension(WATER_ONLY, """
                "imageMaps": [{"key": "biomes", "map": "biomes", "application": "BIOME"}],
                """));
        write(pack, "image-maps/biomes.json", """
                {"source": "biomes.png", "colors": {"#336699": "iris:imaged"}}
                """);
        write(pack, "biomes/imaged.json", """
                {"name": "Imaged", "hydrologyPolicy": {"fluid": "slime"}}
                """);

        List<String> errors = validate(pack).errors();

        assertContains(errors, "Biome 'imaged'.hydrologyPolicy.fluid names fluid 'slime'");
        assertEquals(errors.toString(), 1, errors.size());
    }

    @Test
    public void checksBiomesReachedOnlyAsFloatingChildren() throws Exception {
        File pack = pack(dimension(WATER_ONLY, ""));
        write(pack, "biomes/biome.json", """
                {"name": "Biome", "floatingChildBiomes": [{"biome": "floating"}]}
                """);
        write(pack, "biomes/floating.json", """
                {"name": "Floating", "hydrologyPolicy": {"fluid": "cloud"}}
                """);

        List<String> errors = validate(pack).errors();

        assertContains(errors, "Biome 'floating'.hydrologyPolicy.fluid names fluid 'cloud'");
        assertEquals(errors.toString(), 1, errors.size());
    }

    @Test
    public void checksBiomesReachedOnlyThroughDimensionCarving() throws Exception {
        File pack = pack(dimension(WATER_ONLY, """
                "carving": [{"id": "deep", "biome": "carved"}],
                """));
        write(pack, "biomes/carved.json", """
                {"name": "Carved", "hydrologyPolicy": {"fluid": "magma"}}
                """);

        List<String> errors = validate(pack).errors();

        assertContains(errors, "Biome 'carved'.hydrologyPolicy.fluid names fluid 'magma'");
        assertEquals(errors.toString(), 1, errors.size());
    }

    @Test
    public void defaultFluidReferencesNeedTheWaterProfile() throws Exception {
        List<String> errors = validate(pack(dimension("""
                {"enabled": true, "fluids": [{"name": "lava"}]}
                """, ""))).errors();

        assertContains(errors, "Dimension 'main'.hydrology.tiers names fluid 'water'");
        assertContains(errors, "hydrology.ponds.fluid names fluid 'water'");
        assertContains(errors, "hydrology.underground.cavePools.fluid names fluid 'water'");
    }

    @Test
    public void disabledOrAbsentHydrologyIsNotChecked() throws Exception {
        assertTrue(validate(pack(dimension("""
                {"enabled": false, "ponds": {"freeboard": [5, 1], "fluid": "acid"}}
                """, ""))).errors().isEmpty());
        assertTrue(validate(pack("{\"regions\": [\"region\"], \"hydrologyPolicy\": {\"fluid\": \"acid\"}}")).errors().isEmpty());
        assertContains(validate(pack("{\"regions\": [\"region\"], \"hydrology\": true}")).errors(),
                "Dimension 'main'.hydrology must be an object.");
    }

    @Test
    public void unknownHydrologyKeysAreIgnoredAndAMissingEnabledFlagLoadsAsOff() throws Exception {
        File pack = pack(dimension("""
                {"unknownSection": {"enabled": true, "tiers": 4}, "unknownList": [{"enabled": true}]}
                """, ""));

        assertTrue(validate(pack).errors().isEmpty());
        IrisData data = IrisData.openDatapackCompiler(pack);
        try {
            IrisHydrology hydrology = data.getDimensionLoader().load("main").getHydrology();
            assertNotNull(hydrology);
            assertFalse(hydrology.isEnabled());
        } finally {
            data.close();
        }
    }

    @Test
    public void aMalformedKnownHydrologyValueFailsTheDimensionLoadEvenWhenDisabled() throws Exception {
        File pack = pack(dimension("""
                {"enabled": false, "tiers": "wide"}
                """, ""));

        IrisData data = IrisData.openDatapackCompiler(pack);
        try {
            assertNull(data.getDimensionLoader().load("main"));
        } finally {
            data.close();
        }
    }

    @Test
    public void reportsCompileErrorsTheJsonChecksCannotSeeForEachDimension() throws Exception {
        File pack = pack(dimension("{\"enabled\": true, \"tiers\": []}", ""));
        write(pack, "dimensions/shared.json", dimension("""
                {"enabled": true, "tiers": [{"name": "stream", "minCatchment": 900}, {"name": "brook", "minCatchment": 900}]}
                """, ""));
        write(pack, "dimensions/banked.json", dimension("""
                {"enabled": true, "tiers": [{"name": "stream", "bankBand": 70}]}
                """, ""));
        write(pack, "dimensions/areas.json", dimension("""
                {"enabled": true, "ponds": {"natural": {"minArea": 500, "maxArea": 20}}}
                """, ""));
        write(pack, "dimensions/twice.json", dimension("""
                {"enabled": true, "fluids": [{"name": "water"}, {"name": "water"}]}
                """, ""));
        write(pack, "dimensions/blank.json", dimension("""
                {"enabled": true, "fluids": [{"name": "water", "palette": {}}]}
                """, ""));

        List<String> errors = validate(pack).errors();

        assertContains(errors, "Dimension 'main'.hydrology does not compile: Hydrology needs at least one tier");
        assertContains(errors, "Dimension 'shared'.hydrology does not compile: Hydrology tiers 'stream' and 'brook' share minCatchment");
        assertContains(errors, "Dimension 'banked'.hydrology does not compile: Hydrology tiers[0].bankBand must stay within 0..64");
        assertContains(errors, "Dimension 'areas'.hydrology does not compile: Hydrology ponds.natural.minArea 500 is above");
        assertContains(errors, "Dimension 'twice'.hydrology does not compile: Duplicate hydrology fluid profile 'water' in fluids[1].name");
        assertContains(errors, "Dimension 'blank'.hydrology does not compile: Hydrology fluid profile 'water' has no blocks in fluids[0].palette");
        assertEquals(errors.toString(), 6, errors.size());
        PackValidationResult result = PackValidator.validateForPackaging(pack);
        assertFalse(result.isLoadable());
        assertContains(result.getBlockingErrors(), "Dimension 'banked'.hydrology does not compile");
    }

    @Test
    public void rejectsWaterDecoratorsThatCannotGrowWithoutLoadingBlocks() throws Exception {
        File pack = pack(dimension("""
                {"enabled": true, "tiers": [{"name": "stream", "decorators": [{"partOf": "NONE", "palette": [{"block": "minecraft:fern"}]}]}]}
                """, """
                "hydrologyPolicy": {"decorators": [{"partOf": "SEA_SURFACE", "palette": []}]},
                """));
        write(pack, "regions/region.json", """
                {"landBiomes": ["biome"], "hydrologyPolicy": {"decorators": [{"partOf": "SHORE_LINE", "chance": 0.2}]}}
                """);
        write(pack, "biomes/biome.json", """
                {"name": "Biome", "hydrologyPolicy": {"decorators": [{"partOf": "SHORE_LINE", "forceBlock": {"block": "minecraft:air"},
                  "palette": [{"block": "minecraft:fern"}]}]}}
                """);

        List<String> errors = validate(pack).errors();

        assertContains(errors, "Dimension 'main'.hydrologyPolicy.decorators[0].palette has no blocks");
        assertContains(errors, "Region 'region'.hydrologyPolicy.decorators[0].palette is missing or misspelt");
        assertContains(errors, "Biome 'biome'.hydrologyPolicy.decorators[0].forceBlock is not allowed");
        assertEquals(errors.toString(), 3, errors.size());

        write(pack, "dimensions/main.json", dimension("""
                {"enabled": true, "tiers": [{"name": "stream", "decorators": [{"partOf": "NONE", "palette": [{"block": "minecraft:fern"}]}]}]}
                """, ""));
        write(pack, "regions/region.json", "{\"landBiomes\": [\"biome\"]}");
        write(pack, "biomes/biome.json", "{\"name\": \"Biome\"}");
        List<String> compiled = validate(pack).errors();
        assertContains(compiled, "Dimension 'main'.hydrology does not compile: Hydrology tiers[0].decorators[0].partOf is NONE");
        assertEquals(compiled.toString(), 1, compiled.size());
    }

    @Test
    public void rejectsUnknownOrUnsafeBlocksInEveryWaterDecoratorList() throws Exception {
        File pack = pack(dimension("""
                {"enabled": true,
                 "tiers": [{"name": "stream", "decorators": [
                    {"partOf": "SEA_FLOOR", "palette": [{"block": "minecraft:kelp_plant"}], "topPalette": [{"block": "minecraft:kelpp"}]},
                    {"partOf": "SEA_FLOOR", "palette": [{"block": "minecraft:fern"}]}]}],
                 "ponds": {"decorators": [{"partOf": "SEA_SURFACE", "palette": [{"block": "minecraft:water"}]}]}}
                """, """
                "hydrologyPolicy": {"decorators": [{"partOf": "SHORE_LINE", "palette": [{"block": "minecraft:cattail"}],
                  "whitelist": [{"block": "minecraft:mud"}]}]},
                """));
        File folder = temporaryFolder.newFolder("compat");
        CompatTestPlatform platform = CompatTestPlatform.bind(folder, List.of("minecraft:air", "minecraft:water", "minecraft:kelp_plant",
                "minecraft:kelp", "minecraft:fern", "minecraft:lily_pad", "minecraft:sugar_cane", "minecraft:mud", "minecraft:seagrass"));
        try {
            List<String> policyErrors = validate(pack).errors();
            assertContains(policyErrors, "Dimension 'main'.hydrologyPolicy.decorators[0].palette[0] names the block 'minecraft:cattail'");
            assertEquals(policyErrors.toString(), 1, policyErrors.size());

            write(pack, "dimensions/main.json", dimension("""
                    {"enabled": true,
                     "tiers": [{"name": "stream", "decorators": [
                        {"partOf": "SEA_FLOOR", "palette": [{"block": "minecraft:kelp_plant"}], "topPalette": [{"block": "minecraft:kelpp"}]}]}],
                     "ponds": {"decorators": [{"partOf": "SEA_SURFACE", "palette": [{"block": "minecraft:lily_pad"}]}]}}
                    """, ""));
            List<String> errors = validate(pack).errors();
            assertContains(errors, "Dimension 'main'.hydrology.tiers[0].decorators[0].topPalette[0] names the block 'minecraft:kelpp',"
                    + " which does not exist on this server");
            assertEquals(errors.toString(), 1, errors.size());

            write(pack, "dimensions/main.json", dimension("""
                    {"enabled": true,
                     "tiers": [{"name": "stream", "decorators": [
                        {"partOf": "SEA_FLOOR", "palette": [{"block": "minecraft:kelp_plant"}], "topPalette": [{"block": "minecraft:kelp"}]},
                        {"partOf": "SEA_FLOOR", "palette": [{"block": "minecraft:fern"}]}]}],
                     "ponds": {"decorators": [{"partOf": "SEA_SURFACE", "palette": [{"block": "minecraft:water"}]}]}}
                    """, ""));
            List<String> unsafe = validate(pack).errors();
            assertContains(unsafe, "Dimension 'main'.hydrology.tiers[0].decorators[1].palette[0] names 'minecraft:fern', which cannot stand in water");
            assertContains(unsafe, "Dimension 'main'.hydrology.ponds.decorators[0].palette[0] names the fluid 'minecraft:water'");
            assertEquals(unsafe.toString(), 2, unsafe.size());

            write(pack, "dimensions/main.json", dimension("""
                    {"enabled": true,
                     "tiers": [{"name": "stream", "decorators": [
                        {"partOf": "SEA_FLOOR", "palette": [{"block": "minecraft:kelp_plant"}], "topPalette": [{"block": "minecraft:kelp"}]},
                        {"partOf": "SEA_FLOOR", "palette": [{"block": "minecraft:seagrass"}]}]}],
                     "ponds": {"decorators": [{"partOf": "SEA_SURFACE", "palette": [{"block": "minecraft:lily_pad"}]}]}}
                    """, """
                    "hydrologyPolicy": {"decorators": [{"partOf": "SHORE_LINE", "palette": [{"block": "minecraft:sugar_cane"}],
                      "whitelist": [{"block": "minecraft:mud"}]}]},
                    """));
            List<String> clean = validate(pack).errors();
            assertTrue(clean.toString(), clean.isEmpty());
        } finally {
            platform.unbind();
        }
    }

    @Test
    public void packValidatorReportsHydrologyErrorsAsBlocking() throws Exception {
        File pack = pack(dimension("""
                {"enabled": true, "ponds": {"freeboard": [3, 1]}}
                """, ""));

        PackValidationResult result = PackValidator.validateForPackaging(pack);

        assertFalse(result.isLoadable());
        assertContains(result.getBlockingErrors(), "ponds.freeboard has its min above its max.");
    }

    private PackHydrologyValidator.Validation validate(File pack) {
        File[] dimensions = new File(pack, "dimensions").listFiles(
                file -> file.isFile() && file.getName().endsWith(".json"));
        return PackHydrologyValidator.validate(pack, dimensions);
    }

    private static String dimension(String hydrology, String policy) {
        return "{\"regions\": [\"region\"], " + policy + " \"hydrology\": " + hydrology + "}";
    }

    private File pack(String dimensionJson) throws Exception {
        File pack = temporaryFolder.newFolder("pack-" + System.nanoTime());
        write(pack, "dimensions/main.json", dimensionJson);
        write(pack, "regions/region.json", """
                {
                  "landBiomes": ["biome"],
                  "seaBiomes": [],
                  "shoreBiomes": [],
                  "caveBiomes": []
                }
                """);
        write(pack, "biomes/biome.json",
                "{\"name\":\"Biome\",\"derivative\":\"minecraft:plains\"}");
        return pack;
    }

    private void assertContains(List<String> messages, String fragment) {
        for (String message : messages) {
            if (message.contains(fragment)) {
                return;
            }
        }
        throw new AssertionError("No message contains '" + fragment + "' in " + messages);
    }

    private void write(File root, String relative, String content) throws Exception {
        Path target = new File(root, relative).toPath();
        Files.createDirectories(target.getParent());
        Files.writeString(target, content, StandardCharsets.UTF_8);
    }
}
