package art.arcane.iris.pack;

import art.arcane.iris.generation.block.IrisBlockData;
import art.arcane.iris.generation.decoration.IrisDecorator;
import art.arcane.iris.generation.hydrology.config.IrisHydrology;
import art.arcane.iris.generation.hydrology.config.IrisHydrologyPermission;
import art.arcane.iris.generation.hydrology.config.IrisHydrologyPolicy;
import art.arcane.iris.generation.hydrology.config.IrisHydrologyTier;
import art.arcane.iris.generation.hydrology.config.IrisHydrologyWetness;
import art.arcane.iris.generation.hydrology.plan.HydrologySettings;
import art.arcane.iris.generation.noise.IrisGeneratorStyle;
import art.arcane.iris.generation.terrain.IrisMaterialPalette;
import art.arcane.iris.pack.loading.IrisData;
import art.arcane.iris.testsupport.CompatTestPlatform;
import art.arcane.volmlib.util.json.JSONObject;
import com.google.gson.Gson;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class PackHydrologyValidatorTest {
    private static final String WATER = "{\"name\": \"water\", \"palette\": {\"palette\": [{\"block\": \"minecraft:water\"}]}}";
    private static final String LAVA = "{\"name\": \"lava\", \"palette\": {\"palette\": [{\"block\": \"minecraft:lava\"}]}}";
    private static final String CRUST = "{\"name\": \"crust\", \"palette\": {\"palette\": [{\"block\": \"minecraft:obsidian\"}]}}";
    private static final String SEA_IS_LAVA = "\"fluidPalette\": {\"palette\": [{\"block\": \"minecraft:lava\"}]},";
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
                    {"name": "stream", "minCatchment": 1500, "halfWidth": [0.5, 2], "bankBand": 0, "fluid": "water"},
                    {"name": "river", "minCatchment": 20000, "halfWidth": [2, 64], "bankBand": 32, "fluid": "lava"}
                  ],
                  "ponds": {"freeboard": [0, 2], "fluid": "lava",
                            "seeded": {"radius": [4, 12], "depth": [2, 8], "channelSpacing": [48, 160]}},
                  "underground": {
                    "seaInlets": {"length": [24, 96], "mouthHalfWidth": [8, 12.5], "headHalfWidth": [2, 3.5], "depth": [3, 6]},
                    "cavePools": {"freeboard": [0, 1], "fluid": "water"}
                  },
                  "fluids": [
                    {"name": "water", "palette": {"palette": [{"block": "minecraft:water"}]}},
                    {"name": "lava", "palette": {"palette": [{"block": "minecraft:lava"}]}, "seam": "crust"},
                    {"name": "crust", "palette": {"palette": [{"block": "minecraft:obsidian"}]}}
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
                  "underground": {"seaInlets": {"mouthHalfWidth": [-2, 9]}, "cavePools": {"freeboard": [-1, 0]}}
                }
                """, "")));

        assertContains(validation.errors(), "tiers[0].halfWidth must stay above 0.");
        assertContains(validation.errors(), "tiers[1].halfWidth must not go above 64.");
        assertContains(validation.errors(), "ponds.freeboard must not go below 0.");
        assertContains(validation.errors(), "ponds.seeded.channelSpacing must stay above 0.");
        assertContains(validation.errors(), "underground.seaInlets.mouthHalfWidth must stay above 0.");
        assertContains(validation.errors(), "underground.cavePools.freeboard must not go below 0.");
        assertEquals(validation.errors().toString(), 6, validation.errors().size());
    }

    @Test
    public void rejectsPoliciesThatFillWaterWithASeamProfile() throws Exception {
        File pack = pack(dimension("""
                {
                  "enabled": true,
                  "fluids": [
                    {"name": "water"},
                    {"name": "lava", "palette": {"palette": [{"block": "minecraft:lava"}]}, "seam": "crust"},
                    {"name": "crust", "palette": {"palette": [{"block": "minecraft:obsidian"}]}}
                  ]
                }
                """, ""));
        write(pack, "regions/region.json", """
                {"landBiomes": ["biome"], "hydrologyPolicy": {"fluid": "lava"}}
                """);
        write(pack, "biomes/biome.json", """
                {"name": "Biome", "hydrologyPolicy": {"fluid": "crust"}}
                """);

        List<String> errors = validate(pack).errors();

        assertContains(errors, "Biome 'biome'.hydrologyPolicy.fluid names fluid 'crust', which is a seam profile");
        assertEquals(errors.toString(), 1, errors.size());
    }

    @Test
    public void rejectsPolicyWetnessOutsideItsOrderedBounds() throws Exception {
        File pack = pack(dimension(WATER_ONLY, "\"hydrologyPolicy\": {\"wetness\": {\"min\": 0.2, \"max\": 0.6}},"));
        write(pack, "regions/region.json", """
                {"landBiomes": ["biome"], "hydrologyPolicy": {"wetness": {"max": 1.5}}}
                """);
        write(pack, "biomes/biome.json", """
                {"name": "Biome", "hydrologyPolicy": {"wetness": {"min": 0.8, "max": 0.3}}}
                """);

        List<String> errors = validate(pack).errors();

        assertContains(errors, "Region 'region'.hydrologyPolicy.wetness must have 0 <= min <= max <= 1, got min 0.0 and max 1.5.");
        assertContains(errors, "Biome 'biome'.hydrologyPolicy.wetness must have 0 <= min <= max <= 1, got min 0.8 and max 0.3.");
        assertEquals(errors.toString(), 2, errors.size());
    }

    @Test
    public void rejectsPolicyMultipliersOutsideZeroToSixteen() throws Exception {
        File pack = pack(dimension(WATER_ONLY, "\"hydrologyPolicy\": {\"rainfall\": 16, \"pondDensity\": 0},"));
        write(pack, "regions/region.json", """
                {"landBiomes": ["biome"], "hydrologyPolicy": {"pondDensity": 17}}
                """);
        write(pack, "biomes/biome.json", """
                {"name": "Biome", "hydrologyPolicy": {"rainfall": -0.5, "pondDensity": 16.5}}
                """);

        List<String> errors = validate(pack).errors();

        assertContains(errors, "Region 'region'.hydrologyPolicy.pondDensity must be a finite value from 0 to 16, got 17.0.");
        assertContains(errors, "Biome 'biome'.hydrologyPolicy.rainfall must be a finite value from 0 to 16, got -0.5.");
        assertContains(errors, "Biome 'biome'.hydrologyPolicy.pondDensity must be a finite value from 0 to 16, got 16.5.");
        assertEquals(errors.toString(), 3, errors.size());
    }

    @Test
    public void rejectsADeepFluidLineOutsideTheWorld() throws Exception {
        String hydrology = """
                {
                  "enabled": true,
                  "underground": {"cavePools": {"deepFluid": "lava", "deepFluidMaxY": %d}},
                  "fluids": [
                    {"name": "water"},
                    {"name": "lava", "palette": {"palette": [{"block": "minecraft:lava"}]}}
                  ]
                }
                """;
        assertTrue(validate(pack(dimension(hydrology.formatted(-64), ""))).errors().isEmpty());
        PackHydrologyValidator.Validation outside = validate(pack(dimension(hydrology.formatted(-2000), "")));
        assertTrue(outside.errors().toString(), outside.errors().toString().contains("deepFluidMaxY"));
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
    public void warnsAboutAnUnknownKeyAtTheTopOfHydrology() throws Exception {
        List<String> warnings = unknownKeyWarnings(validate(pack(dimension("{\"enabled\": true, \"unknownSection\": {\"enabled\": true, \"tiers\": 4}}", ""))));

        assertContains(warnings, "Dimension 'main'.hydrology.unknownSection is not a known key, so its value is ignored.");
        assertContains(warnings, "prefetchRings");
        assertEquals(warnings.toString(), 1, warnings.size());
    }

    @Test
    public void warnsAboutAMisspeltEnabledFlagThoughTheBlockLoadsAsOff() throws Exception {
        File pack = pack(dimension("{\"enable\": true, \"ponds\": {\"dryDrawdon\": 3}}", ""));

        List<String> warnings = unknownKeyWarnings(validate(pack));

        assertContains(warnings, "Dimension 'main'.hydrology.enable is not a known key");
        assertContains(warnings, "Dimension 'main'.hydrology.ponds.dryDrawdon is not a known key");
        assertEquals(warnings.toString(), 2, warnings.size());
        assertFalse(loadHydrology(pack).isEnabled());
    }

    @Test
    public void warnsAboutUnknownKeysInNestedObjects() throws Exception {
        List<String> warnings = unknownKeyWarnings(validate(pack(dimension("""
                {
                  "enabled": true,
                  "ponds": {"seeded": {"radiusRange": [4, 12]}},
                  "underground": {"cavePools": {"surface": 3}, "seaInlets": {"length": [24, 96], "chamber": {"radius": 4}}}
                }
                """, ""))));

        assertContains(warnings, "Dimension 'main'.hydrology.ponds.seeded.radiusRange is not a known key");
        assertContains(warnings, "Dimension 'main'.hydrology.underground.cavePools.surface is not a known key");
        assertContains(warnings, "Dimension 'main'.hydrology.underground.seaInlets.chamber is not a known key");
        assertEquals(warnings.toString(), 3, warnings.size());
    }

    @Test
    public void warnsAboutUnknownKeysInsideTierArrayElements() throws Exception {
        File pack = pack(dimension("""
                {"enabled": true, "fluids": [{"name": "water", "palette": {"palette": [{"block": "minecraft:water", "bloc": 1}]}, "sealed": true}],
                 "tiers": [
                  {"name": "stream", "minCatchment": 900, "halfWidth": [0.5, 2.5], "bankBand": 8},
                  {"name": "major", "minCatchment": 200000, "halfWidth": [9, 18], "bankBand": 48, "notATierField": {"depth": 3},
                   "estuary": {"flair": 2},
                   "decorators": [{"partOf": "SEA_FLOOR", "palette": [{"block": "minecraft:kelp"}], "stacks": 1}]}
                ]}
                """, ""));

        List<String> warnings = unknownKeyWarnings(validate(pack));

        assertContains(warnings, "Dimension 'main'.hydrology.tiers[1].notATierField is not a known key");
        assertContains(warnings, "Dimension 'main'.hydrology.tiers[1].estuary.flair is not a known key");
        assertContains(warnings, "Dimension 'main'.hydrology.tiers[1].decorators[0].stacks is not a known key");
        assertContains(warnings, "Dimension 'main'.hydrology.fluids[0].sealed is not a known key");
        assertContains(warnings, "Dimension 'main'.hydrology.fluids[0].palette.palette[0].bloc is not a known key");
        assertEquals(warnings.toString(), 5, warnings.size());
        for (IrisHydrologyTier tier : loadHydrology(pack).getTiers()) {
            assertEquals(tier.getName(), 48, tier.getEstuary().getLength());
            assertEquals(tier.getName(), 2.5D, tier.getEstuary().getFlare(), 0D);
        }
    }

    @Test
    public void treatsMapKeysAsDataAndChecksTheObjectsInside() throws Exception {
        List<String> warnings = new ArrayList<>();
        JSONObject written = new JSONObject("""
                {"zones": {"swamp": {"min": 0.7, "max": 1}, "desert": {"minimum": 0.1}}}
                """);

        PackHydrologyValidator.warnUnknownKeys("root", written, NamedZones.class, temporaryFolder.getRoot(), warnings);

        assertContains(warnings, "root.zones.desert.minimum is not a known key");
        assertEquals(warnings.toString(), 1, warnings.size());
    }

    @Test
    public void acceptsAnyKeyInsideABlockDataMap() throws Exception {
        PackHydrologyValidator.Validation validation = validate(pack(dimension("""
                {"enabled": true, "fluids": [{"name": "water", "palette": {"palette": [
                  {"block": "minecraft:water", "data": {"level": 0, "anything": {"at": "all"}}, "tileData": {"SpawnData": {"entity": {}}}}
                ]}}]}
                """, "")));

        assertEquals(List.of(), unknownKeyWarnings(validation));
    }

    @Test
    public void acceptsEveryKeyGsonWritesForTheConfigClasses() throws Exception {
        Gson gson = new Gson();
        PackHydrologyValidator.Validation defaults = validate(pack(dimension(gson.toJson(new IrisHydrology().setEnabled(true)), "")));
        assertEquals(List.of(), unknownKeyWarnings(defaults));

        IrisHydrology hydrology = new IrisHydrology().setEnabled(true);
        hydrology.getTiers().get(0).getMaterials().setBed(new IrisMaterialPalette()).setShore(new IrisMaterialPalette()).setBank(new IrisMaterialPalette());
        IrisDecorator decorator = new IrisDecorator().setForceBlock(new IrisBlockData("minecraft:stone"));
        decorator.getStyle().setFracture(new IrisGeneratorStyle());
        hydrology.getTiers().get(0).getDecorators().add(decorator);
        IrisHydrologyPolicy policy = new IrisHydrologyPolicy().setRainfall(1D).setWetness(new IrisHydrologyWetness()).setStreams(IrisHydrologyPermission.ALLOW);

        PackHydrologyValidator.Validation written = validate(pack(dimension(gson.toJson(hydrology), "\"hydrologyPolicy\": " + gson.toJson(policy) + ",")));

        assertEquals(List.of(), keyWarnings(written));
    }

    @Test
    public void warnsAboutEachRenamedKeyByItsOldName() throws Exception {
        List<String> warnings = unknownKeyWarnings(validate(pack(dimension("""
                {
                  "enabled": true,
                  "drainage": {"minimumHeadKeep": 0.6},
                  "tiers": [
                    {"name": "stream", "halfWidth": [0.5, 2], "bankBand": 0, "waterfallMinimumDrop": 6},
                    {"name": "river", "minCatchment": 20000, "halfWidth": [2, 8], "bankBand": 32, "waterfallMinimumDrop": 8}
                  ],
                  "ponds": {"climateDrawdown": 3},
                  "underground": {"seaInlets": {"minimumCliffHeight": 8}}
                }
                """, ""))));

        assertContains(warnings, "Dimension 'main'.hydrology.drainage.minimumHeadKeep is not a known key");
        assertContains(warnings, "Dimension 'main'.hydrology.tiers[0].waterfallMinimumDrop is not a known key");
        assertContains(warnings, "Dimension 'main'.hydrology.tiers[1].waterfallMinimumDrop is not a known key");
        assertContains(warnings, "Dimension 'main'.hydrology.ponds.climateDrawdown is not a known key");
        assertContains(warnings, "Dimension 'main'.hydrology.underground.seaInlets.minimumCliffHeight is not a known key");
        assertEquals(warnings.toString(), 5, warnings.size());
    }

    @Test
    public void warnsAboutUnknownKeysInHydrologyPoliciesAtEveryScope() throws Exception {
        File pack = pack(dimension(WATER_ONLY, "\"hydrologyPolicy\": {\"rainFall\": 1},"));
        write(pack, "regions/region.json", """
                {"landBiomes": ["biome"], "hydrologyPolicy": {"streamz": "BLOCK"}}
                """);
        write(pack, "biomes/biome.json", """
                {"name": "Biome", "hydrologyPolicy": {"wetness": {"middle": 0.5},
                 "decorators": [{"partOf": "SHORE_LINE", "palette": [{"block": "minecraft:fern"}], "odds": 2}]}}
                """);

        List<String> warnings = unknownKeyWarnings(validate(pack));

        assertContains(warnings, "Dimension 'main'.hydrologyPolicy.rainFall is not a known key");
        assertContains(warnings, "Region 'region'.hydrologyPolicy.streamz is not a known key");
        assertContains(warnings, "Biome 'biome'.hydrologyPolicy.wetness.middle is not a known key");
        assertContains(warnings, "Biome 'biome'.hydrologyPolicy.decorators[0].odds is not a known key");
        assertEquals(warnings.toString(), 4, warnings.size());
    }

    @Test
    public void followsSnippetsAndNamesThemInThePath() throws Exception {
        File pack = pack(dimension("""
                {"enabled": true, "tiers": [{"name": "stream", "halfWidth": [0.5, 2], "bankBand": 0,
                 "decorators": ["snippet/reeds", "snippet/decorator/fern", "snippet/decorator/missing"]}]}
                """, ""));
        write(pack, "snippet/decorator/reeds.json", """
                {"partOf": "SHORE_LINE", "palette": [{"block": "minecraft:sugar_cane"}], "odds": 2}
                """);
        write(pack, "snippet/decorator/fern.json", """
                {"partOf": "SHORE_LINE", "palette": [{"block": "minecraft:fern"}]}
                """);

        List<String> warnings = keyWarnings(validate(pack));

        assertContains(warnings, "Dimension 'main'.hydrology.tiers[0].decorators[0] (snippet/decorator/reeds).odds is not a known key");
        assertEquals(warnings.toString(), 1, warnings.size());
    }

    @Test
    public void aSnippetThatRefersToItselfEnds() throws Exception {
        File pack = temporaryFolder.newFolder("cycle");
        write(pack, "snippet/style/loop.json", """
                {"fracture": "snippet/style/loop", "fractures": 1}
                """);
        List<String> warnings = new ArrayList<>();

        PackHydrologyValidator.warnUnknownKeys("style", "snippet/style/loop", IrisGeneratorStyle.class, pack, warnings);

        assertContains(warnings, "style (snippet/style/loop).fractures is not a known key");
        assertEquals(warnings.toString(), 1, warnings.size());
    }

    @Test
    public void leavesKeysOutsideTheHydrologyBlockAlone() throws Exception {
        File pack = pack("{\"regions\": [\"region\"], \"notADimensionKey\": 1, \"hydrology\": " + WATER_ONLY + "}");
        write(pack, "biomes/biome.json", "{\"name\": \"Biome\", \"notABiomeKey\": {\"a\": 1}}");

        assertEquals(List.of(), unknownKeyWarnings(validate(pack)));
    }

    @Test
    public void unknownKeysNeverBlockAPackOnAnyValidationPath() throws Exception {
        File pack = pack(dimension("{\"enabled\": true, \"ponds\": {\"climateDrawdown\": 3}}", "\"hydrologyPolicy\": {\"rainFall\": 1},"));
        String key = "not a known key";

        for (PackValidationResult result : List.of(PackValidator.validate(pack), PackValidator.validateForPackaging(pack), PackValidator.validateForDatapackBootstrap(pack))) {
            assertFalse(result.getBlockingErrors().toString(), result.getBlockingErrors().stream().anyMatch(message -> message.contains(key)));
            assertContains(result.getWarnings(), "Dimension 'main'.hydrology.ponds.climateDrawdown is " + key);
            assertContains(result.getWarnings(), "Dimension 'main'.hydrologyPolicy.rainFall is " + key);
        }
    }

    @Test
    public void anObjectWhereAListIsDeclaredReportsNoListInternals() throws Exception {
        List<String> warnings = new ArrayList<>();
        JSONObject written = new JSONObject("{\"tiers\": {\"name\": \"stream\"}, \"fluids\": {\"elementData\": 1}}");

        PackHydrologyValidator.warnUnknownKeys("root", written, IrisHydrology.class, temporaryFolder.getRoot(), warnings);

        assertEquals(List.of(), warnings);
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

    private static IrisHydrology loadHydrology(File pack) {
        IrisData data = IrisData.openDatapackCompiler(pack);
        try {
            IrisHydrology hydrology = data.getDimensionLoader().load("main").getHydrology();
            assertNotNull(hydrology);
            return hydrology;
        } finally {
            data.close();
        }
    }

    @Test
    public void rejectsSeaInletSpacingBelowOne() throws Exception {
        File pack = pack(dimension("{\"enabled\": true, \"underground\": {\"seaInlets\": {\"spacing\": 0}}}", ""));
        write(pack, "dimensions/edge.json", dimension("{\"enabled\": true, \"underground\": {\"seaInlets\": {\"spacing\": 1}}}", ""));

        assertEquals(List.of("Dimension 'main'.hydrology does not compile: Hydrology underground.seaInlets.spacing must be at least 1, got 0"),
                validate(pack).errors());
    }

    @Test
    public void rejectsPrefetchRingsOutsideZeroToFour() throws Exception {
        File pack = pack(dimension("{\"enabled\": true, \"prefetchRings\": 5}", ""));
        write(pack, "dimensions/low.json", dimension("{\"enabled\": true, \"prefetchRings\": -1}", ""));
        write(pack, "dimensions/edge.json", dimension("{\"enabled\": true, \"prefetchRings\": 4}", ""));

        assertEquals(List.of(
                "Dimension 'low'.hydrology does not compile: Hydrology prefetchRings must stay within 0..4, got -1",
                "Dimension 'main'.hydrology does not compile: Hydrology prefetchRings must stay within 0..4, got 5"), validate(pack).errors());
    }

    @Test
    public void rejectsSeededPondDensityOutsideZeroToSixtyFour() throws Exception {
        File pack = pack(dimension("{\"enabled\": true, \"ponds\": {\"seeded\": {\"density\": 64.5}}}", ""));
        write(pack, "dimensions/low.json", dimension("{\"enabled\": true, \"ponds\": {\"seeded\": {\"density\": -1}}}", ""));
        write(pack, "dimensions/edge.json", dimension("{\"enabled\": true, \"ponds\": {\"seeded\": {\"density\": 64}}}", ""));

        assertEquals(List.of(
                "Dimension 'low'.hydrology does not compile: Hydrology ponds.seeded.density must stay within 0..64, got -1.0",
                "Dimension 'main'.hydrology does not compile: Hydrology ponds.seeded.density must stay within 0..64, got 64.5"),
                validate(pack).errors());
    }

    @Test
    public void rejectsFillEpsilonAtOrBelowZero() throws Exception {
        File pack = pack(dimension("{\"enabled\": true, \"drainage\": {\"fillEpsilon\": 0}}", ""));
        write(pack, "dimensions/low.json", dimension("{\"enabled\": true, \"drainage\": {\"fillEpsilon\": -0.001}}", ""));
        write(pack, "dimensions/edge.json", dimension("{\"enabled\": true, \"drainage\": {\"fillEpsilon\": 0.00001}}", ""));

        assertEquals(List.of(
                "Dimension 'low'.hydrology does not compile: Hydrology drainage.fillEpsilon must be a finite value above 0, got -0.001",
                "Dimension 'main'.hydrology does not compile: Hydrology drainage.fillEpsilon must be a finite value above 0, got 0.0"),
                validate(pack).errors());
    }

    @Test
    public void rejectsWaterfallMinDropBelowTwo() throws Exception {
        File pack = pack(dimension("""
                {"enabled": true, "tiers": [{"name": "stream", "halfWidth": [0.5, 2], "bankBand": 0},
                  {"name": "river", "minCatchment": 20000, "halfWidth": [2, 8], "bankBand": 32, "waterfallMinDrop": 1}]}
                """, ""));
        write(pack, "dimensions/edge.json", dimension("""
                {"enabled": true, "tiers": [{"name": "stream", "halfWidth": [0.5, 2], "bankBand": 0, "waterfallMinDrop": 2}]}
                """, ""));

        assertEquals(List.of("Dimension 'main'.hydrology does not compile: Hydrology tiers[1].waterfallMinDrop must be at least 2, got 1"),
                validate(pack).errors());
    }

    @Test
    public void rejectsShoreDepthBelowOne() throws Exception {
        File pack = pack(dimension("""
                {"enabled": true, "tiers": [{"name": "stream", "halfWidth": [0.5, 2], "bankBand": 0, "materials": {"shoreDepth": 0}}]}
                """, ""));
        write(pack, "dimensions/ponds.json", dimension("{\"enabled\": true, \"ponds\": {\"materials\": {\"shoreDepth\": 0}}}", ""));
        write(pack, "dimensions/edge.json", dimension("""
                {"enabled": true, "tiers": [{"name": "stream", "halfWidth": [0.5, 2], "bankBand": 0, "materials": {"shoreDepth": 1}}],
                 "ponds": {"materials": {"shoreDepth": 1}}}
                """, ""));

        assertEquals(List.of(
                "Dimension 'main'.hydrology does not compile: Hydrology tiers[0].materials.shoreDepth must be at least 1, got 0",
                "Dimension 'ponds'.hydrology does not compile: Hydrology ponds.materials.shoreDepth must be at least 1, got 0"),
                validate(pack).errors());
    }

    @Test
    public void rejectsNegativeWidthFactorDepthRatioAndMaxFill() throws Exception {
        File pack = pack(dimension("""
                {"enabled": true, "tiers": [{"name": "stream", "halfWidth": [0.5, 2], "bankBand": 0, "widthFactor": -1}]}
                """, ""));
        write(pack, "dimensions/ratio.json", dimension("""
                {"enabled": true, "tiers": [{"name": "stream", "halfWidth": [0.5, 2], "bankBand": 0, "depthRatio": -0.1}]}
                """, ""));
        write(pack, "dimensions/fill.json", dimension("{\"enabled\": true, \"deformation\": {\"maxFill\": -1}}", ""));
        write(pack, "dimensions/edge.json", dimension("""
                {"enabled": true, "tiers": [{"name": "stream", "halfWidth": [0.5, 2], "bankBand": 0, "widthFactor": 0, "depthRatio": 0}],
                 "deformation": {"maxFill": 0}}
                """, ""));

        assertEquals(List.of(
                "Dimension 'fill'.hydrology does not compile: Hydrology deformation.maxFill must be at least 0, got -1",
                "Dimension 'main'.hydrology does not compile: Hydrology tiers[0].widthFactor must be a finite value of at least 0, got -1.0",
                "Dimension 'ratio'.hydrology does not compile: Hydrology tiers[0].depthRatio must be a finite value of at least 0, got -0.1"),
                validate(pack).errors());
    }

    @Test
    public void rejectsCavePoolFreeboardThatReachesMaxDepth() throws Exception {
        File pack = pack(dimension("{\"enabled\": true, \"underground\": {\"cavePools\": {\"maxDepth\": 2, \"freeboard\": [0, 2]}}}", ""));
        write(pack, "dimensions/edge.json", dimension("""
                {"enabled": true, "underground": {"cavePools": {"maxDepth": 3, "freeboard": [0, 2]}}}
                """, ""));
        write(pack, "dimensions/off.json", dimension("""
                {"enabled": true, "underground": {"cavePools": {"enabled": false, "maxDepth": 2, "freeboard": [0, 2]}}}
                """, ""));

        assertEquals(List.of(
                "Dimension 'main'.hydrology does not compile: Hydrology underground.cavePools.freeboard max 2 must stay below maxDepth 2"),
                validate(pack).errors());
    }

    @Test
    public void rejectsLakesWiderThanTheLakeExtent() throws Exception {
        File pack = pack(dimension("{\"enabled\": true, \"lakes\": {\"maxArea\": 4194305}}", ""));
        write(pack, "dimensions/edge.json", dimension("{\"enabled\": true, \"lakes\": {\"maxArea\": 4194304}}", ""));

        assertEquals(List.of("Dimension 'main'.hydrology does not compile: Hydrology lakes.maxArea must not go above 4194304"
                + " (2048 by 2048 blocks, the widest a lake can span), got 4194305"), validate(pack).errors());
    }

    @Test
    public void rejectsCavePoolsWiderThanTheirReach() throws Exception {
        File pack = pack(dimension("{\"enabled\": true, \"underground\": {\"cavePools\": {\"maxArea\": 962}}}", ""));
        write(pack, "dimensions/edge.json", dimension("{\"enabled\": true, \"underground\": {\"cavePools\": {\"maxArea\": 961}}}", ""));
        write(pack, "dimensions/off.json", dimension("""
                {"enabled": true, "underground": {"cavePools": {"enabled": false, "maxArea": 962}}}
                """, ""));

        assertEquals(List.of("Dimension 'main'.hydrology does not compile: Hydrology underground.cavePools.maxArea must not go above 961"
                + " (31 by 31 blocks, the widest a pool can spread), got 962"), validate(pack).errors());
    }

    @Test
    public void rejectsRegionAndDimensionPolicyFluidsWhileCavePoolsAreOff() throws Exception {
        File pack = pack(dimension("""
                {"enabled": true, "underground": {"cavePools": {"enabled": false}}, "fluids": [{"name": "water"}]}
                """, "\"hydrologyPolicy\": {\"fluid\": \"brine\"},"));
        write(pack, "regions/region.json", """
                {"landBiomes": ["biome"], "hydrologyPolicy": {"fluid": "tar"}}
                """);

        assertEquals(List.of(
                "Dimension 'main'.hydrologyPolicy.fluid names fluid 'brine', which Dimension 'main'.hydrology.fluids does not define [water].",
                "Region 'region'.hydrologyPolicy.fluid names fluid 'tar', which Dimension 'main'.hydrology.fluids does not define [water]."),
                validate(pack).errors());
    }

    @Test
    public void warnsWhenAWrittenTierLeavesHalfWidthOrBankBandUnset() throws Exception {
        PackHydrologyValidator.Validation validation = validate(pack(dimension("""
                {"enabled": true, "tiers": [
                  {"name": "stream", "halfWidth": [0.5, 2], "bankBand": 4},
                  {"name": "river", "minCatchment": 20000, "bankBand": 32},
                  {"name": "major", "minCatchment": 320000, "halfWidth": [8, 16]}]}
                """, "")));

        assertTrue(validation.errors().toString(), validation.errors().isEmpty());
        assertEquals(List.of(
                "Dimension 'main'.hydrology.tiers[1] ('river') does not set halfWidth, so it takes the default [0.5, 2.0].",
                "Dimension 'main'.hydrology.tiers[2] ('major') does not set bankBand, so it takes the default 0"
                        + " and leaves the ground beside its channels as it is."), validation.warnings());
        assertTrue(validate(pack(dimension("{\"enabled\": true}", ""))).warnings().isEmpty());
    }

    @Test
    public void warnsWhenTheMantleCannotWriteHydrology() throws Exception {
        File pack = pack(dimension("{\"enabled\": true}", "\"useMantle\": false,"));
        write(pack, "dimensions/other.json", dimension("{\"enabled\": true}", "\"disabledComponents\": [\"RIVER_HYDROLOGY\"],"));
        write(pack, "dimensions/off.json", dimension("{\"enabled\": false}", "\"useMantle\": false,"));

        PackHydrologyValidator.Validation validation = validate(pack);

        assertTrue(validation.errors().toString(), validation.errors().isEmpty());
        assertEquals(List.of(
                "Dimension 'main' sets useMantle to false while hydrology is enabled, so the falling water at waterfalls and cascades"
                        + " and cave pools are not generated.",
                "Dimension 'other' disables the RIVER_HYDROLOGY component while hydrology is enabled, so the falling water at waterfalls"
                        + " and cascades and cave pools are not generated."), validation.warnings());
    }

    @Test
    public void shoreBiomesWithoutWidthWarn() throws Exception {
        PackHydrologyValidator.Validation unset = validate(shorePack(""));
        PackHydrologyValidator.Validation widened = validate(shorePack(", \"shoreBiomeWidth\": 3"));

        assertTrue(unset.errors().toString(), unset.errors().isEmpty());
        assertEquals(List.of(
                "Dimension 'main'.hydrologyPolicy.shoreBiomes lists shore biomes, but no shoreBiomeWidth above 0 applies to them,"
                        + " so none are placed.",
                "Region 'region'.hydrologyPolicy.shoreBiomes lists shore biomes, but no shoreBiomeWidth above 0 applies to them,"
                        + " so none are placed.",
                "Biome 'dune'.hydrologyPolicy.shoreBiomes lists shore biomes, but no shoreBiomeWidth above 0 applies to them,"
                        + " so none are placed."), unset.warnings());
        assertTrue(widened.errors().toString(), widened.errors().isEmpty());
        assertTrue(widened.warnings().toString(), widened.warnings().isEmpty());
    }

    @Test
    public void warnsWhenALavaRiverWithoutASeamCanMeetTheSea() throws Exception {
        File pack = pack(dimension(hydrology("lava", "lava", LAVA), ""));

        PackHydrologyValidator.Validation validation = validate(pack);

        assertTrue(validation.errors().toString(), validation.errors().isEmpty());
        assertEquals(List.of("Dimension 'main'.hydrology.fluids profile 'lava' sets no seam, so where its rivers meet the sea"
                + " the fluids lie side by side. Set seam on the profile to line that edge. Its rivers come from"
                + " Dimension 'main'.hydrology.tiers[0].fluid, Dimension 'main'.hydrology.tiers[1].fluid."), validation.warnings());
    }

    @Test
    public void warnsAgainstADifferentRiverFluidAndNamesTheBiomeItComesFrom() throws Exception {
        File pack = pack(dimension(hydrology("water", "water", WATER + ", " + LAVA), SEA_IS_LAVA));
        write(pack, "biomes/biome.json", """
                {"name": "Biome", "hydrologyPolicy": {"fluid": "lava"}}
                """);
        write(pack, "regions/region.json", """
                {"landBiomes": ["biome", "plain"]}
                """);
        write(pack, "biomes/plain.json", "{\"name\": \"Plain\"}");

        PackHydrologyValidator.Validation validation = validate(pack);

        assertTrue(validation.errors().toString(), validation.errors().isEmpty());
        assertEquals(List.of("Dimension 'main'.hydrology.fluids profile 'lava' sets no seam, so where its rivers meet fluid 'water'"
                + " the fluids lie side by side. Set seam on the profile to line that edge. Its rivers come from"
                + " Biome 'biome'.hydrologyPolicy.fluid."), validation.warnings());
    }

    @Test
    public void aSeamOnTheOtherRiverFluidLinesTheEdgeBetweenThem() throws Exception {
        String fluids = WATER + ", " + LAVA + ", " + """
                {"name": "tar", "palette": {"palette": [{"block": "mod:tar"}]}, "seam": "crust"}, %s
                """.formatted(CRUST);
        File pack = pack(dimension(hydrology("lava", "tar", fluids), SEA_IS_LAVA));

        PackHydrologyValidator.Validation validation = validate(pack);

        assertTrue(validation.errors().toString(), validation.errors().isEmpty());
        assertTrue(validation.warnings().toString(), validation.warnings().isEmpty());
    }

    @Test
    public void silentWhenTheFluidHasASeam() throws Exception {
        String fluids = """
                {"name": "lava", "palette": {"palette": [{"block": "minecraft:lava"}]}, "seam": "crust"}, %s, %s
                """.formatted(CRUST, WATER);
        PackHydrologyValidator.Validation validation = validate(pack(dimension(hydrology("lava", "water", fluids), "")));

        assertTrue(validation.errors().toString(), validation.errors().isEmpty());
        assertTrue(validation.warnings().toString(), validation.warnings().isEmpty());
    }

    @Test
    public void silentForWaterRiversWhateverTheProfileIsCalled() throws Exception {
        String fluids = """
                {"name": "marsh", "palette": {"palette": [{"block": "water"}]}}, %s
                """.formatted(WATER);
        File pack = pack(dimension(hydrology("marsh", "water", fluids), SEA_IS_LAVA));

        PackHydrologyValidator.Validation validation = validate(pack);

        assertTrue(validation.errors().toString(), validation.errors().isEmpty());
        assertTrue(validation.warnings().toString(), validation.warnings().isEmpty());
    }

    @Test
    public void silentWhenTheFluidsAreTheSame() throws Exception {
        String fluids = LAVA + ", " + """
                {"name": "magma", "palette": {"palette": [{"block": "lava"}]}}
                """;
        PackHydrologyValidator.Validation validation = validate(pack(dimension(hydrology("lava", "magma", fluids), SEA_IS_LAVA)));

        assertTrue(validation.errors().toString(), validation.errors().isEmpty());
        assertTrue(validation.warnings().toString(), validation.warnings().isEmpty());
    }

    @Test
    public void silentWhenAPolicyFluidReplacesEveryTierFluid() throws Exception {
        String config = hydrology("water", "water", WATER + ", " + LAVA);
        PackHydrologyValidator.Validation validation = validate(pack(dimension(config,
                SEA_IS_LAVA + " \"hydrologyPolicy\": {\"fluid\": \"lava\"},")));

        assertTrue(validation.errors().toString(), validation.errors().isEmpty());
        assertTrue(validation.warnings().toString(), validation.warnings().isEmpty());
    }

    @Test
    public void aCaveBiomePolicyFluidIsNotARiverFluid() throws Exception {
        File pack = pack(dimension(hydrology("water", "water", WATER + ", " + LAVA), ""));
        write(pack, "regions/region.json", """
                {"landBiomes": ["biome"], "caveBiomes": ["hollow"]}
                """);
        write(pack, "biomes/hollow.json", """
                {"name": "Hollow", "hydrologyPolicy": {"fluid": "lava"}}
                """);

        PackHydrologyValidator.Validation validation = validate(pack);

        assertTrue(validation.errors().toString(), validation.errors().isEmpty());
        assertTrue(validation.warnings().toString(), validation.warnings().isEmpty());
    }

    @Test
    public void silentAboutTheSeaWhenTheDimensionHasNoFluidHeight() throws Exception {
        PackHydrologyValidator.Validation validation = validate(pack(dimension(hydrology("lava", "lava", LAVA), "\"fluidHeight\": -64,")));

        assertTrue(validation.errors().toString(), validation.errors().isEmpty());
        assertTrue(validation.warnings().toString(), validation.warnings().isEmpty());
    }

    @Test
    public void warnsAboutTheSeaOneBlockAboveTheDimensionFloor() throws Exception {
        String noInlets = hydrology("lava", "lava", LAVA).replace("\"underground\": {", "\"underground\": {\"seaInlets\": {\"enabled\": false}, ");
        PackHydrologyValidator.Validation validation = validate(pack(dimension(noInlets, "\"fluidHeight\": -63,")));

        assertTrue(validation.errors().toString(), validation.errors().isEmpty());
        assertContains(validation.warnings(), "where its rivers meet the sea");
    }

    private static String hydrology(String streamFluid, String riverFluid, String fluids) {
        return """
                {"enabled": true,
                 "tiers": [
                   {"name": "stream", "minCatchment": 1500, "halfWidth": [0.5, 2], "bankBand": 0, "fluid": "%s"},
                   {"name": "river", "minCatchment": 20000, "halfWidth": [2, 16], "bankBand": 8, "fluid": "%s"}],
                 "ponds": {"fluid": "%s"},
                 "underground": {"cavePools": {"fluid": "%s"}},
                 "fluids": [%s]}
                """.formatted(streamFluid, riverFluid, streamFluid, streamFluid, fluids);
    }

    private File shorePack(String dimensionWidth) throws Exception {
        File pack = pack(dimension(WATER_ONLY, "\"hydrologyPolicy\": {\"shoreBiomes\": [\"beach\"]" + dimensionWidth + "},"));
        write(pack, "regions/region.json", """
                {"landBiomes": ["biome", "dune", "bare"], "hydrologyPolicy": {"shoreBiomes": ["beach"]}}
                """);
        write(pack, "biomes/dune.json", """
                {"name": "Dune", "hydrologyPolicy": {"shoreBiomes": ["beach"]}}
                """);
        write(pack, "biomes/bare.json", """
                {"name": "Bare", "hydrologyPolicy": {"shoreBiomes": []}}
                """);
        write(pack, "biomes/beach.json", "{\"name\": \"Beach\"}");
        return pack;
    }

    private static final class NamedZones {
        private Map<String, IrisHydrologyWetness> zones;
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

    private List<String> unknownKeyWarnings(PackHydrologyValidator.Validation validation) {
        assertEquals(List.of(), validation.errors());
        return keyWarnings(validation);
    }

    private List<String> keyWarnings(PackHydrologyValidator.Validation validation) {
        return validation.warnings().stream().filter(warning -> warning.contains("not a known key")).toList();
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
