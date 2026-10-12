package art.arcane.iris.util.common.director.specialhandlers;

import art.arcane.iris.builtin.structure.StructureSettings;
import art.arcane.iris.core.modules.BuiltInPack;
import art.arcane.iris.engine.framework.structure.StructureResources;
import art.arcane.iris.core.modules.ModuleResourceFixture;
import art.arcane.iris.engine.mantle.components.StructureModule;
import art.arcane.iris.core.loader.IrisData;
import art.arcane.iris.engine.framework.Engine;
import art.arcane.iris.engine.framework.EngineGeneration;
import art.arcane.iris.engine.mantle.components.StructureModule;
import art.arcane.iris.engine.object.world.dimension.IrisDimension;
import art.arcane.iris.builtin.structure.NativeStructureSource;
import art.arcane.iris.engine.object.structure.IrisStructure;
import art.arcane.iris.builtin.structure.StructurePlacement;
import art.arcane.iris.engine.object.world.IrisWorld;
import art.arcane.iris.engine.framework.NativeStructureGenerationStatus;
import art.arcane.iris.builtin.structure.StructureDistribution;
import art.arcane.iris.spi.IrisPlatform;
import art.arcane.iris.spi.IrisPlatforms;
import art.arcane.iris.spi.PlatformStructureHooks;
import art.arcane.iris.spi.PlatformWorld;
import art.arcane.volmlib.util.collection.KList;
import art.arcane.volmlib.util.director.exceptions.DirectorParsingException;
import org.junit.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import art.arcane.iris.engine.object.TestSettings;

import static art.arcane.iris.engine.framework.StructurePlacementFixtures.nativeSource;
import static art.arcane.iris.engine.framework.StructurePlacementFixtures.placementSettings;
import static art.arcane.iris.engine.framework.StructurePlacementFixtures.withAdded;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class StructureHandlerTest {
    @Test
    public void registeredEligibilityMatchesFindExecutionTruthTable() {
        NativeStructureGenerationStatus replacement = NativeStructureGenerationStatus.REPLACED_BY_IRIS;
        NativeStructureGenerationStatus generated = NativeStructureGenerationStatus.GENERATE_NATIVE;
        NativeStructureGenerationStatus disabled = NativeStructureGenerationStatus.DISABLED_BY_PACK;

        assertTrue(StructureHandler.isEligibleRegisteredKey(
                replacement, false, false, true, false, false));
        assertFalse(StructureHandler.isEligibleRegisteredKey(
                replacement, false, false, false, true, true));
        assertTrue(StructureHandler.isEligibleRegisteredKey(
                replacement, true, true, false, false, true));
        assertFalse(StructureHandler.isEligibleRegisteredKey(
                replacement, true, true, false, false, false));
        assertTrue(StructureHandler.isEligibleRegisteredKey(
                generated, true, true, false, false, true));
        assertFalse(StructureHandler.isEligibleRegisteredKey(
                generated, true, false, false, false, true));
        assertTrue(StructureHandler.isEligibleRegisteredKey(
                generated, false, false, false, true, true));
        assertFalse(StructureHandler.isEligibleRegisteredKey(
                generated, false, false, false, true, false));
        assertFalse(StructureHandler.isEligibleRegisteredKey(
                disabled, true, true, false, true, true));
    }

    @Test
    public void requiresActiveIrisEngineBeforeReadingPlatformRegistries() throws DirectorParsingException {
        IrisPlatforms.unbind();
        TestStructureHandler handler = new TestStructureHandler(null, true);

        assertTrue(handler.getPossibilities().isEmpty());
        assertEquals("manual:structure", handler.parse("manual:structure", false));
    }

    @Test
    public void filtersAndDeduplicatesSuggestionsForActiveEngine() {
        IrisData data = mock(IrisData.class);
        Engine engine = mock(Engine.class);
        IrisDimension dimension = mock(IrisDimension.class);
        IrisWorld world = mock(IrisWorld.class);
        PlatformWorld platformWorld = mock(PlatformWorld.class);
        IrisPlatform platform = mock(IrisPlatform.class);
        PlatformStructureHooks hooks = mock(PlatformStructureHooks.class);
        StructureSettings settings = new StructureSettings(List.of("pack:disabled", "pack:replacement", "pack:dormant_replacement"),
                List.of(), 0, true, List.of(), List.of());

        KList<StructurePlacement> placements = new KList<>();
        placements.add(nativePlacement("pack:replacement", StructureDistribution.RANDOM_SPREAD, 1.0));
        placements.add(nativePlacement("pack:dormant_replacement", StructureDistribution.DENSITY, 0.0));
        placements.add(nativePlacement("pack:explicit", StructureDistribution.RANDOM_SPREAD, 1.0));
        placements.add(nativePlacement("pack:dormant_explicit", StructureDistribution.DENSITY, 0.0));
        placements.add(nativePlacement("pack:unregistered_native", StructureDistribution.RANDOM_SPREAD, 1.0));
        placements.add(nativePlacement("iris:custom", StructureDistribution.RANDOM_SPREAD, 1.0));
        placements.add(editablePlacement("iris:custom_definition"));
        placements.add(editablePlacement("iris:collision_definition"));

        IrisStructure custom = new IrisStructure();
        custom.setLoadKey("iris:custom");
        IrisStructure collision = new IrisStructure();
        collision.setLoadKey("pack:collision");
        ModuleResourceFixture.serveModules(data, new StructureModule());
        ModuleResourceFixture.of(data).resource(StructureResources.STRUCTURES, "iris:custom_definition", custom);
        ModuleResourceFixture.of(data).resource(StructureResources.STRUCTURES, "iris:collision_definition", collision);
        when(engine.getData()).thenReturn(data);
        ModuleResourceFixture.serveModules(data, new StructureModule());
        when(engine.getGeneration()).thenReturn(new EngineGeneration(engine));
        when(engine.getDimension()).thenReturn(dimension);
        when(engine.getWorld()).thenReturn(world);
        when(engine.getMinHeight()).thenReturn(-64);
        when(engine.getHeight()).thenReturn(384);
        when(world.platformWorld()).thenReturn(platformWorld);
        when(dimension.getModuleSettings()).thenReturn(BuiltInPack.settings(settings, placementSettings(placements)));
        when(dimension.getAllRegions(engine)).thenReturn(new KList<>());
        when(dimension.getReachableBiomes(engine)).thenReturn(new KList<>());
        when(platform.structureHooks()).thenReturn(hooks);
        when(hooks.structureKeys()).thenReturn(List.of(
                "pack:reachable",
                "PACK:REACHABLE",
                "pack:unreachable",
                "pack:disabled",
                "pack:replacement",
                "pack:dormant_replacement",
                "pack:explicit",
                "pack:dormant_explicit",
                "PACK:COLLISION"));
        when(hooks.reachableStructureKeys(platformWorld)).thenReturn(List.of("PACK:REACHABLE"));

        IrisPlatforms.unbind();
        IrisPlatforms.bind(platform);
        try {
            KList<String> possibilities = new TestStructureHandler(engine, true).getPossibilities();
            Set<String> distinctPossibilities = new LinkedHashSet<>(possibilities);

            assertEquals(Set.of(
                    "pack:reachable",
                    "pack:replacement",
                    "pack:explicit",
                    "iris:custom_definition",
                    "iris:collision_definition"), distinctPossibilities);
            assertEquals(distinctPossibilities.size(), possibilities.size());
            assertFalse(possibilities.stream().anyMatch("pack:collision"::equalsIgnoreCase));
            assertFalse(possibilities.contains("pack:unregistered_native"));
            verify(hooks, times(1)).structureKeys();
            verify(hooks, times(1)).reachableStructureKeys(platformWorld);
        } finally {
            IrisPlatforms.unbind();
        }
    }

    @Test
    public void disabledWorldKeepsOnlyUnregisteredEditablePlacements() {
        IrisData data = mock(IrisData.class);
        Engine engine = mock(Engine.class);
        IrisDimension dimension = mock(IrisDimension.class);
        IrisWorld world = mock(IrisWorld.class);
        PlatformWorld platformWorld = mock(PlatformWorld.class);
        IrisPlatform platform = mock(IrisPlatform.class);
        PlatformStructureHooks hooks = mock(PlatformStructureHooks.class);
        StructureSettings settings = new StructureSettings(List.of("minecraft:replacement"), List.of(), 0, true, List.of(), List.of());

        IrisStructure editable = new IrisStructure();
        editable.setLoadKey("iris:editable");
        IrisStructure replacement = new IrisStructure();
        replacement.setLoadKey("iris:replacement");
        replacement.setVanillaSource("minecraft:replacement");
        KList<StructurePlacement> placements = new KList<>();
        placements.add(editablePlacement("iris:editable_definition"));
        placements.add(editablePlacement("iris:replacement_definition"));
        placements.add(nativePlacement("minecraft:explicit", StructureDistribution.RANDOM_SPREAD, 1.0));

        ModuleResourceFixture.serveModules(data, new StructureModule());
        ModuleResourceFixture.of(data).resource(StructureResources.STRUCTURES, "iris:editable_definition", editable);
        ModuleResourceFixture.of(data).resource(StructureResources.STRUCTURES, "iris:replacement_definition", replacement);
        when(engine.getData()).thenReturn(data);
        ModuleResourceFixture.serveModules(data, new StructureModule());
        when(engine.getGeneration()).thenReturn(new EngineGeneration(engine));
        when(engine.getDimension()).thenReturn(dimension);
        when(engine.getWorld()).thenReturn(world);
        when(engine.getMinHeight()).thenReturn(-64);
        when(engine.getHeight()).thenReturn(384);
        when(world.platformWorld()).thenReturn(platformWorld);
        when(dimension.getModuleSettings()).thenReturn(BuiltInPack.settings(settings, placementSettings(placements)));
        when(dimension.getAllRegions(engine)).thenReturn(new KList<>());
        when(dimension.getReachableBiomes(engine)).thenReturn(new KList<>());
        when(platform.structureHooks()).thenReturn(hooks);
        when(hooks.structureKeys()).thenReturn(List.of(
                "minecraft:replacement", "minecraft:explicit", "minecraft:reachable"));
        when(hooks.reachableStructureKeys(platformWorld)).thenReturn(List.of("minecraft:reachable"));

        IrisPlatforms.unbind();
        IrisPlatforms.bind(platform);
        try {
            Set<String> possibilities = new LinkedHashSet<>(
                    new TestStructureHandler(engine, false).getPossibilities());

            assertEquals(Set.of(
                    "minecraft:replacement",
                    "iris:editable_definition",
                    "iris:editable",
                    "iris:replacement_definition",
                    "iris:replacement"), possibilities);
            assertFalse(possibilities.contains("minecraft:explicit"));
            assertFalse(possibilities.contains("minecraft:reachable"));
        } finally {
            IrisPlatforms.unbind();
        }
    }

    @Test
    public void propagatesRegistryFailures() {
        Engine engine = mock(Engine.class);
        IrisPlatform platform = mock(IrisPlatform.class);
        PlatformStructureHooks hooks = mock(PlatformStructureHooks.class);
        IllegalStateException failure = new IllegalStateException("registry unavailable");
        when(platform.structureHooks()).thenReturn(hooks);
        when(hooks.structureKeys()).thenThrow(failure);

        IrisPlatforms.unbind();
        IrisPlatforms.bind(platform);
        try {
            IllegalStateException thrown = assertThrows(
                    IllegalStateException.class, () -> new TestStructureHandler(engine, true).getPossibilities());
            assertSame(failure, thrown);
        } finally {
            IrisPlatforms.unbind();
        }
    }

    @Test
    public void completionUsesOneReachabilitySnapshotAndLocatableKeys() throws IOException {
        Path sourcePath = Path.of(
                "src/main/java/art/arcane/iris/util/common/director/specialhandlers/StructureHandler.java");
        String source = Files.readString(sourcePath).replace("\r\n", "\n");
        int methodStart = source.indexOf("public KList<String> getPossibilities()");
        int methodEnd = source.indexOf("@Override\n    public String toString", methodStart);
        String method = source.substring(methodStart, methodEnd);

        assertEquals(1, occurrences(method, "StructureReachability.reachableKeys(activeEngine)"));
        assertEquals(0, occurrences(method, "IrisStructureLocator.locatableKeys(activeEngine)"));
        assertEquals(1, occurrences(method, "ModuleServices.locatableStructures(activeEngine)"));
        assertTrue(method.indexOf("if (activeEngine == null)")
                < method.indexOf("IrisPlatforms.get().structureHooks()"));
        assertFalse(method.contains("INMS"));
        assertFalse(method.contains("catch ("));
    }


    private static StructurePlacement nativePlacement(String key, StructureDistribution distribution,
                                                          double density) {
        StructurePlacement placement = new StructurePlacement();
        withAdded(placement, "nativeStructures", nativeSource(key));
        TestSettings.with(placement, "distribution", distribution);
        TestSettings.with(placement, "density", density);
        return placement;
    }

    private static StructurePlacement editablePlacement(String key) {
        StructurePlacement placement = new StructurePlacement();
        withAdded(placement, "structures", key);
        return placement;
    }

    private static int occurrences(String source, String target) {
        int count = 0;
        int offset = 0;
        while ((offset = source.indexOf(target, offset)) >= 0) {
            count++;
            offset += target.length();
        }
        return count;
    }

    private static final class TestStructureHandler extends StructureHandler {
        private final Engine activeEngine;
        private final boolean nativeGenerationEnabled;

        private TestStructureHandler(Engine activeEngine, boolean nativeGenerationEnabled) {
            this.activeEngine = activeEngine;
            this.nativeGenerationEnabled = nativeGenerationEnabled;
        }

        @Override
        public Engine engine() {
            return activeEngine;
        }

        @Override
        protected boolean nativeStructureGenerationEnabled() {
            return nativeGenerationEnabled;
        }
    }
}
