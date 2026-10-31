package art.arcane.iris.pack.datapack.export.compile.biome;

import art.arcane.iris.pack.datapack.export.ExportTestPacks;
import art.arcane.iris.pack.datapack.export.compile.PackCompileContext;
import art.arcane.iris.pack.datapack.export.ir.Axis;
import art.arcane.iris.pack.datapack.export.ir.BiomeDef;
import art.arcane.iris.pack.datapack.export.ir.Field;
import art.arcane.iris.pack.datapack.export.ir.JsonResource;
import art.arcane.iris.pack.datapack.export.ir.NoiseDef;
import art.arcane.iris.pack.loading.IrisData;
import art.arcane.iris.testsupport.PlatformLeakGuard;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public class BiomeStageTest {
    @ClassRule
    public static final PlatformLeakGuard PLATFORM_GUARD = PlatformLeakGuard.clean();

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Rule
    public ExportTestPacks.BoundPlatform platform = new ExportTestPacks.BoundPlatform();

    @Test
    public void stageFillsCodesBiomesAndTags() throws Exception {
        File pack = SelectionTestPacks.threeRegions(temporaryFolder.newFolder("pack"));
        IrisData data = IrisData.openRuntime(pack);
        try {
            PackCompileContext context = BiomeDecisionCompilerTest.context(data);
            new BiomeStage().apply(context);

            BiomeCodes codes = context.biomeCodes();
            Set<String> defined = new TreeSet<>();
            for (BiomeDef biome : context.biomes()) {
                defined.add(context.namespace() + ":" + biome.id());
            }
            assertEquals(codes.loadKeyByBiomeId().keySet(), defined);
            assertEquals(18, defined.size());
            List<String> tagPaths = new ArrayList<>();
            for (JsonResource resource : context.jsonResources()) {
                tagPaths.add(resource.path());
            }
            assertTrue(tagPaths.toString(), tagPaths.contains("data/minecraft/tags/worldgen/biome/is_overworld.json"));
            assertEquals(new Field.Const(0.0), codes.class3d());
        } finally {
            data.close();
        }
    }

    @Test
    public void codesBindToTheHeightOnceItIsSet() throws Exception {
        File pack = SelectionTestPacks.threeRegions(temporaryFolder.newFolder("pack"));
        IrisData data = IrisData.openRuntime(pack);
        try {
            PackCompileContext context = BiomeDecisionCompilerTest.context(data);
            new BiomeStage().apply(context);
            BiomeCodes base = context.biomeCodes();
            Field height = BiomeDecisionCompilerTest.height(context);

            context.setHeight(height);
            BiomeCodes bound = context.biomeCodes();

            assertNotEquals(base.surfaceCode(), bound.surfaceCode());
            assertSame(bound, context.biomeCodes());
            assertEquals(base.caveCode(), bound.caveCode());
            assertEquals(base.layout(), bound.layout());
            assertTrue(bound.class3d().axes().contains(Axis.Y));
            assertFalse(bound.surfaceCode().axes().contains(Axis.Y));
        } finally {
            data.close();
        }
    }

    @Test
    public void climateHeightDecidesTheBiomesUntilTheHeightIsReplaced() throws Exception {
        File pack = SelectionTestPacks.childChain(temporaryFolder.newFolder("pack"));
        IrisData data = IrisData.openRuntime(pack);
        try {
            PackCompileContext context = BiomeDecisionCompilerTest.context(data);
            new BiomeStage().apply(context);
            int seaLevel = context.dimensionDef().fluidHeight();
            Field natural = new Field.Const(seaLevel + 40.0);
            Field climate = new Field.Const(seaLevel - 20.0);
            Map<String, NoiseDef> noises = BiomeDecisionCompilerTest.noises(context);
            int seaFloor = seaLevel - 20;
            context.setHeight(natural);

            context.setClimateHeight(climate);

            assertSame(climate, context.climateHeight());
            assertSame(natural, context.height());
            assertEquals("sea", BiomeDecisionCompilerTest.biomeAt(context.biomeCodes(), noises, 0, seaFloor, 0));
            context.setCarvedHeight(new Field.Const(seaLevel - 30.0));
            assertEquals("sea", BiomeDecisionCompilerTest.biomeAt(context.biomeCodes(), noises, 0, seaFloor, 0));
            context.setHeight(natural);
            assertSame(natural, context.climateHeight());
            assertNotEquals("sea", BiomeDecisionCompilerTest.biomeAt(context.biomeCodes(), noises, 0, seaFloor, 0));
        } finally {
            data.close();
        }
    }

    @Test
    public void carvedHeightKeepsTheBiomesOfTheNaturalHeight() throws Exception {
        File pack = SelectionTestPacks.childChain(temporaryFolder.newFolder("pack"));
        IrisData data = IrisData.openRuntime(pack);
        try {
            PackCompileContext context = BiomeDecisionCompilerTest.context(data);
            new BiomeStage().apply(context);
            int seaLevel = context.dimensionDef().fluidHeight();
            Field natural = new Field.Const(seaLevel + 40.0);
            Field carved = new Field.Const(seaLevel - 20.0);
            context.setHeight(natural);
            BiomeCodes land = context.biomeCodes();

            context.setCarvedHeight(carved);

            assertSame(carved, context.height());
            assertSame(natural, context.naturalHeight());
            assertSame(land, context.biomeCodes());
            Map<String, NoiseDef> noises = BiomeDecisionCompilerTest.noises(context);
            int riverBed = seaLevel - 20;
            assertNotEquals("sea", BiomeDecisionCompilerTest.biomeAt(context.biomeCodes(), noises, 0, riverBed, 0));
            context.setHeight(carved);
            assertEquals("sea", BiomeDecisionCompilerTest.biomeAt(context.biomeCodes(), noises, 0, riverBed, 0));
        } finally {
            data.close();
        }
    }
}
