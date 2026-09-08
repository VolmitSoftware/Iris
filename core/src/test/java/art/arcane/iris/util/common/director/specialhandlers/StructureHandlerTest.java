package art.arcane.iris.util.common.director.specialhandlers;

import art.arcane.iris.core.loader.IrisData;
import art.arcane.iris.engine.framework.Engine;
import art.arcane.iris.engine.framework.IrisStructureLocator;
import art.arcane.iris.engine.framework.StructureReachability;
import art.arcane.iris.engine.object.IrisDimension;
import art.arcane.iris.engine.object.IrisImportedStructureControl;
import art.arcane.iris.engine.object.IrisNativeStructure;
import art.arcane.iris.engine.object.IrisNativeStructureDecision;
import art.arcane.iris.engine.object.IrisStructure;
import art.arcane.iris.engine.object.IrisStructurePlacement;
import art.arcane.iris.engine.object.IrisWorld;
import art.arcane.iris.engine.object.NativeStructureGenerationStatus;
import art.arcane.iris.engine.object.StructureDistribution;
import art.arcane.iris.spi.IrisPlatform;
import art.arcane.iris.spi.IrisPlatforms;
import art.arcane.iris.spi.PlatformStructureHooks;
import art.arcane.iris.spi.PlatformWorld;
import art.arcane.iris.testsupport.PlatformLeakGuard;
import art.arcane.volmlib.util.collection.KList;
import art.arcane.volmlib.util.director.exceptions.DirectorParsingException;
import org.junit.ClassRule;
import org.junit.Test;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

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
    @ClassRule
    public static final PlatformLeakGuard PLATFORM_GUARD = PlatformLeakGuard.clean();

    @Test
    public void registeredEligibilityMatchesFindExecutionTruthTable() {
        IrisNativeStructureDecision replacement = decision(NativeStructureGenerationStatus.REPLACED_BY_IRIS);
        IrisNativeStructureDecision generated = decision(NativeStructureGenerationStatus.GENERATE_NATIVE);
        IrisNativeStructureDecision disabled = decision(NativeStructureGenerationStatus.DISABLED_BY_PACK);

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
        IrisImportedStructureControl control = new IrisImportedStructureControl();
        control.getDisabled().add("pack:disabled");
        control.getDisabled().add("pack:replacement");
        control.getDisabled().add("pack:dormant_replacement");

        KList<IrisStructurePlacement> placements = new KList<>();
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
        when(data.load(IrisStructure.class, "iris:custom_definition", false)).thenReturn(custom);
        when(data.load(IrisStructure.class, "iris:collision_definition", false)).thenReturn(collision);
        when(engine.getData()).thenReturn(data);
        when(engine.getDimension()).thenReturn(dimension);
        when(engine.getWorld()).thenReturn(world);
        when(engine.getMinHeight()).thenReturn(-64);
        when(engine.getHeight()).thenReturn(384);
        when(world.platformWorld()).thenReturn(platformWorld);
        when(dimension.getImportedStructures()).thenReturn(control);
        when(dimension.getStructures()).thenReturn(placements);
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
            IrisStructureLocator.invalidate(engine);
            StructureReachability.invalidate(engine);
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
        IrisImportedStructureControl control = new IrisImportedStructureControl();
        control.getDisabled().add("minecraft:replacement");

        IrisStructure editable = new IrisStructure();
        editable.setLoadKey("iris:editable");
        IrisStructure replacement = new IrisStructure();
        replacement.setLoadKey("iris:replacement");
        replacement.setVanillaSource("minecraft:replacement");
        KList<IrisStructurePlacement> placements = new KList<>();
        placements.add(editablePlacement("iris:editable_definition"));
        placements.add(editablePlacement("iris:replacement_definition"));
        placements.add(nativePlacement("minecraft:explicit", StructureDistribution.RANDOM_SPREAD, 1.0));

        when(data.load(IrisStructure.class, "iris:editable_definition", false)).thenReturn(editable);
        when(data.load(IrisStructure.class, "iris:replacement_definition", false)).thenReturn(replacement);
        when(engine.getData()).thenReturn(data);
        when(engine.getDimension()).thenReturn(dimension);
        when(engine.getWorld()).thenReturn(world);
        when(engine.getMinHeight()).thenReturn(-64);
        when(engine.getHeight()).thenReturn(384);
        when(world.platformWorld()).thenReturn(platformWorld);
        when(dimension.getImportedStructures()).thenReturn(control);
        when(dimension.getStructures()).thenReturn(placements);
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
            IrisStructureLocator.invalidate(engine);
            StructureReachability.invalidate(engine);
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

    private static IrisNativeStructureDecision decision(NativeStructureGenerationStatus status) {
        return new IrisNativeStructureDecision(status, 0, null, false, null, null);
    }

    private static IrisStructurePlacement nativePlacement(String key, StructureDistribution distribution,
                                                          double density) {
        IrisStructurePlacement placement = new IrisStructurePlacement();
        placement.getNativeStructures().add(new IrisNativeStructure().setStructure(key));
        placement.setDistribution(distribution);
        placement.setDensity(density);
        return placement;
    }

    private static IrisStructurePlacement editablePlacement(String key) {
        IrisStructurePlacement placement = new IrisStructurePlacement();
        placement.getStructures().add(key);
        return placement;
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
