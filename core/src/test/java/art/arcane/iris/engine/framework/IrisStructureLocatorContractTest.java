/*
 * Iris is a World Generator for Minecraft Bukkit Servers
 * Copyright (c) 2022 Arcane Arts (Volmit Software)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package art.arcane.iris.engine.framework;

import art.arcane.iris.builtin.structure.StructureTerrain;
import com.google.gson.Gson;

import art.arcane.iris.builtin.anchor.ObjectPlaceMode;
import art.arcane.iris.engine.framework.structure.StructureResources;
import art.arcane.iris.core.modules.ModuleResourceFixture;
import art.arcane.iris.core.loader.IrisData;
import art.arcane.iris.engine.framework.structure.StructureGraphCatalog;
import art.arcane.iris.engine.object.value.IrisDirection;
import art.arcane.iris.engine.object.world.dimension.IrisDimension;
import art.arcane.iris.engine.object.structure.jigsaw.IrisJigsawConnector;
import art.arcane.iris.engine.object.structure.jigsaw.IrisJigsawPiece;
import art.arcane.iris.engine.object.structure.jigsaw.IrisJigsawPieceEntry;
import art.arcane.iris.engine.object.structure.jigsaw.IrisJigsawPool;
import art.arcane.iris.builtin.structure.NativeStructureSource;
import art.arcane.iris.engine.object.prefab.IrisObject;
import art.arcane.iris.engine.object.value.IrisPosition;
import art.arcane.iris.engine.object.world.region.IrisRegion;
import art.arcane.iris.engine.object.structure.IrisStructure;
import art.arcane.iris.builtin.structure.StructureAnchorMode;
import art.arcane.iris.builtin.structure.StructurePlacement;
import art.arcane.iris.builtin.structure.NativeStructureSuppression;
import art.arcane.iris.builtin.structure.StructureDistribution;
import art.arcane.volmlib.util.collection.KList;
import art.arcane.volmlib.util.math.RNG;
import com.volmit.host.ModuleLogs;
import org.junit.Test;

import java.util.Set;
import art.arcane.iris.engine.object.TestSettings;

import static art.arcane.iris.engine.framework.StructurePlacementFixtures.placement;
import static art.arcane.iris.engine.framework.StructurePlacementFixtures.nativeSource;
import static art.arcane.iris.engine.framework.StructurePlacementFixtures.declaring;
import static art.arcane.iris.engine.framework.StructurePlacementFixtures.withAdded;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class IrisStructureLocatorContractTest {
    @Test
    public void placedKeysIsEmptyForNullEngine() {
        assertTrue(IrisStructureLocator.placedKeys(null).isEmpty());
    }

    @Test
    public void isPlacedIsFalseForNullEngine() {
        assertFalse(IrisStructureLocator.isPlaced(null, "minecraft:ancient_city"));
    }

    @Test
    public void isPlacedIsFalseForNullOrEmptyKey() {
        Engine engine = mock(Engine.class);
        when(engine.getGeneration()).thenReturn(new EngineGeneration(engine));
        assertFalse(IrisStructureLocator.isPlaced(engine, null));
        assertFalse(IrisStructureLocator.isPlaced(engine, ""));
    }

    @Test
    public void locatablePlacementsExcludeDisabledDensityConfigurations() {
        Engine disabled = densityEngine(0.0, false, -64, 384, -2032, 2032);
        Engine active = densityEngine(0.01, false, -64, 384, -2032, 2032);

        assertTrue(IrisStructureLocator.isPlaced(disabled, "test:density"));
        assertFalse(IrisStructureLocator.hasLocatablePlacement(disabled, "test:density"));
        assertTrue(IrisStructureLocator.locatableKeys(disabled).isEmpty());
        assertTrue(IrisStructureLocator.hasLocatablePlacement(active, "test:density"));
        assertTrue(IrisStructureLocator.locatableKeys(active).contains("test:density"));
        assertFalse(IrisStructureLocator.hasLocatablePlacement(null, "test:density"));
        assertTrue(IrisStructureLocator.locatableKeys(null).isEmpty());
    }

    @Test
    public void locatablePlacementsExcludeEveryDistributionOutsideWorldHeight() {
        Engine randomSpread = densityEngine(1.0, false, -64, 384, 400, 500);
        TestSettings.with(StructurePlacementScope.placementsOf(randomSpread.getDimension()).get(0),
                "distribution", StructureDistribution.RANDOM_SPREAD);
        Engine concentricRings = densityEngine(1.0, false, -64, 384, 400, 500);
        TestSettings.with(StructurePlacementScope.placementsOf(concentricRings.getDimension()).get(0),
                "distribution", StructureDistribution.CONCENTRIC_RINGS);

        assertFalse(IrisStructureLocator.hasLocatablePlacement(randomSpread, "test:density"));
        assertFalse(IrisStructureLocator.hasLocatablePlacement(concentricRings, "test:density"));
    }

    @Test
    public void locatableIndexIncludesAliasesAndSeparatesEditableFromNativePlacements() {
        IrisData data = mock(IrisData.class);
        IrisStructure structure = new IrisStructure();
        structure.setLoadKey("test:city");
        structure.setVanillaSource("source:city");
        ModuleResourceFixture.of(data).resource(StructureResources.STRUCTURES, "test:city_definition", structure);

        StructurePlacement editable = new StructurePlacement();
        withAdded(editable, "structures", "test:city_definition");
        StructurePlacement nativePlacement = new StructurePlacement();
        withAdded(nativePlacement, "nativeStructures", nativeSource("source:native_city"));
        IrisDimension dimension = mock(IrisDimension.class);
        KList<StructurePlacement> placements = new KList<>();
        placements.add(editable);
        placements.add(nativePlacement);
        Engine engine = mock(Engine.class);
        when(engine.getGeneration()).thenReturn(new EngineGeneration(engine));
        when(engine.getData()).thenReturn(data);
        when(engine.getDimension()).thenReturn(dimension);
        when(engine.getMinHeight()).thenReturn(-64);
        when(engine.getHeight()).thenReturn(384);
        when(dimension.getModuleSettings()).thenReturn(declaring(placements));
        when(dimension.getAllRegions(engine)).thenReturn(new KList<>());
        when(dimension.getReachableBiomes(engine)).thenReturn(new KList<>());

        assertEquals(Set.of("test:city_definition", "test:city", "source:city", "source:native_city"),
                IrisStructureLocator.locatableKeys(engine));
        assertEquals(Set.of("test:city_definition", "test:city", "source:city"),
                IrisStructureLocator.locatableEditableKeys(engine));
        assertEquals(Set.of("source:native_city"), IrisStructureLocator.locatableNativeKeys(engine));
        assertTrue(IrisStructureLocator.hasLocatableEditablePlacement(engine, "SOURCE:CITY"));
        assertFalse(IrisStructureLocator.hasLocatableEditablePlacement(engine, "source:native_city"));
        assertTrue(IrisStructureLocator.hasLocatableNativePlacement(engine, "SOURCE:NATIVE_CITY"));
        assertFalse(IrisStructureLocator.hasLocatableNativePlacement(engine, "source:city"));
    }

    @Test
    public void suppressesVanillaIsFalseForNullEngine() {
        assertFalse(IrisStructureLocator.suppressesVanilla(null, "minecraft:ancient_city"));
    }

    @Test
    public void nativeStructureReplacementSuppressesOnlyItsRegisteredSource() {
        IrisData data = mock(IrisData.class);
        Engine engine = mock(Engine.class);
        when(engine.getGeneration()).thenReturn(new EngineGeneration(engine));
        IrisDimension dimension = mock(IrisDimension.class);
        StructurePlacement placement = placement("nativeSuppression", NativeStructureSuppression.REPLACE_SOURCE);
        withAdded(placement, "nativeStructures", nativeSource("minecraft:ancient_city"));
        KList<StructurePlacement> placements = new KList<>();
        placements.add(placement);
        when(engine.getData()).thenReturn(data);
        when(engine.getDimension()).thenReturn(dimension);
        when(dimension.getModuleSettings()).thenReturn(declaring(placements));
        when(dimension.getAllRegions(engine)).thenReturn(new KList<>());
        when(dimension.getReachableBiomes(engine)).thenReturn(new KList<>());

        assertTrue(IrisStructureLocator.isPlaced(engine, "minecraft:ancient_city"));
        assertTrue(IrisStructureLocator.suppressesVanilla(engine, "minecraft:ancient_city"));
        assertFalse(IrisStructureLocator.suppressesVanilla(engine, "minecraft:village"));
    }

    @Test
    public void suppressesVanillaIsFalseForNullOrEmptyKey() {
        Engine engine = mock(Engine.class);
        when(engine.getGeneration()).thenReturn(new EngineGeneration(engine));
        assertFalse(IrisStructureLocator.suppressesVanilla(engine, null));
        assertFalse(IrisStructureLocator.suppressesVanilla(engine, ""));
    }

    @Test
    public void vanillaSourceRequiresExplicitDimensionSuppression() {
        IrisData data = mock(IrisData.class);
        IrisStructure structure = new IrisStructure();
        structure.setLoadKey("test:city");
        structure.setVanillaSource("minecraft:ancient_city");
        ModuleResourceFixture.of(data).resource(StructureResources.STRUCTURES, "test:city", structure);
        registerSinglePieceGraph(data, structure, "test:city");

        StructurePlacement placement = new StructurePlacement();
        withAdded(placement, "structures", "test:city");
        IrisDimension dimension = mock(IrisDimension.class);
        KList<StructurePlacement> placements = new KList<>();
        placements.add(placement);
        when(dimension.getModuleSettings()).thenReturn(declaring(placements));
        when(dimension.getAllRegions(any())).thenReturn(new KList<>());
        when(dimension.getReachableBiomes(any())).thenReturn(new KList<>());

        Engine engine = mock(Engine.class);

        when(engine.getGeneration()).thenReturn(new EngineGeneration(engine));
        when(engine.getData()).thenReturn(data);
        when(engine.getDimension()).thenReturn(dimension);

        assertTrue(IrisStructureLocator.isPlaced(engine, "minecraft:ancient_city"));
        assertFalse(IrisStructureLocator.suppressesVanilla(engine, "minecraft:ancient_city"));
        TestSettings.with(placement, "nativeSuppression", NativeStructureSuppression.REPLACE_SOURCE);
        when(engine.getGeneration()).thenReturn(new EngineGeneration(engine));
        assertTrue(IrisStructureLocator.suppressesVanilla(engine, "minecraft:ancient_city"));
    }

    @Test
    public void regionReplacementFailsInsteadOfFallingBackToNative() {
        IrisData data = mock(IrisData.class);
        IrisStructure structure = new IrisStructure();
        structure.setLoadKey("test:city");
        structure.setVanillaSource("minecraft:ancient_city");
        ModuleResourceFixture.of(data).resource(StructureResources.STRUCTURES, "test:city", structure);
        registerSinglePieceGraph(data, structure, "test:city");

        StructurePlacement placement = new StructurePlacement();
        withAdded(placement, "structures", "test:city");
        TestSettings.with(placement, "nativeSuppression", NativeStructureSuppression.REPLACE_SOURCE);
        IrisRegion region = mock(IrisRegion.class);
        when(region.getModuleSettings()).thenReturn(declaring(new KList<StructurePlacement>().qadd(placement)));
        IrisDimension dimension = mock(IrisDimension.class);
        when(dimension.getModuleSettings()).thenReturn(declaring(new KList<>()));
        when(dimension.getAllRegions(any())).thenReturn(new KList<IrisRegion>().qadd(region));
        when(dimension.getReachableBiomes(any())).thenReturn(new KList<>());

        Engine engine = mock(Engine.class);

        when(engine.getGeneration()).thenReturn(new EngineGeneration(engine));
        when(engine.getData()).thenReturn(data);
        when(engine.getDimension()).thenReturn(dimension);

        assertReplacementFailure(
                () -> IrisStructureLocator.suppressesVanilla(engine, "minecraft:ancient_city"),
                "dimension-level");
    }

    @Test
    public void geometryFailureFailsInsteadOfFallingBackToNative() {
        IrisData data = mock(IrisData.class);
        IrisStructure structure = new IrisStructure()
                .setStartPool("test:start")
                .setMaxDepth(1)
                .setMaxSizeChunks(1)
                .setVanillaSource("minecraft:ancient_city");
        structure.setLoadKey("test:city");
        IrisJigsawConnector source = new IrisJigsawConnector()
                .setPosition(new IrisPosition(0, 0, 0))
                .setDirection(IrisDirection.EAST_POSITIVE_X)
                .setPool("test:target")
                .setName("source")
                .setTargetName("door");
        IrisJigsawConnector target = new IrisJigsawConnector()
                .setPosition(new IrisPosition(50, 0, 0))
                .setDirection(IrisDirection.WEST_NEGATIVE_X)
                .setPool("test:target")
                .setName("door")
                .setTargetName("unused");
        IrisJigsawPiece startPiece = new IrisJigsawPiece().setObject("test:start-object").setRotatable(false);
        startPiece.getConnectors().add(source);
        IrisJigsawPiece targetPiece = new IrisJigsawPiece().setObject("test:target-object").setRotatable(false);
        targetPiece.getConnectors().add(target);
        IrisJigsawPool startPool = new IrisJigsawPool();
        startPool.getPieces().add(new IrisJigsawPieceEntry("test:start-piece", 1));
        IrisJigsawPool targetPool = new IrisJigsawPool();
        targetPool.getPieces().add(new IrisJigsawPieceEntry("test:target-piece", 1));
        IrisObject startObject = mock(IrisObject.class);
        IrisObject targetObject = mock(IrisObject.class);
        when(startObject.getW()).thenReturn(1);
        when(startObject.getH()).thenReturn(1);
        when(startObject.getD()).thenReturn(1);
        when(targetObject.getW()).thenReturn(100);
        when(targetObject.getH()).thenReturn(1);
        when(targetObject.getD()).thenReturn(1);
        ModuleResourceFixture.of(data).resource(StructureResources.STRUCTURES, "test:city", structure);
        ModuleResourceFixture.of(data).resource(StructureResources.POOLS, "test:start", startPool);
        ModuleResourceFixture.of(data).resource(StructureResources.POOLS, "test:target", targetPool);
        ModuleResourceFixture.of(data).resource(StructureResources.PIECES, "test:start-piece", startPiece);
        ModuleResourceFixture.of(data).resource(StructureResources.PIECES, "test:target-piece", targetPiece);
        when(data.load(IrisObject.class, "test:start-object", false)).thenReturn(startObject);
        when(data.load(IrisObject.class, "test:target-object", false)).thenReturn(targetObject);
        assertFalse(StructureGraphCatalog.compile(data, structure, ModuleLogs.discarding()).isAssemblyViable());

        StructurePlacement placement = placement("nativeSuppression", NativeStructureSuppression.REPLACE_SOURCE);
        withAdded(placement, "structures", "test:city");
        IrisDimension dimension = mock(IrisDimension.class);
        when(dimension.getModuleSettings()).thenReturn(declaring(new KList<StructurePlacement>().qadd(placement)));
        when(dimension.getAllRegions(any())).thenReturn(new KList<>());
        when(dimension.getReachableBiomes(any())).thenReturn(new KList<>());
        Engine engine = mock(Engine.class);
        when(engine.getGeneration()).thenReturn(new EngineGeneration(engine));
        when(engine.getData()).thenReturn(data);
        when(engine.getDimension()).thenReturn(dimension);

        assertReplacementFailure(
                () -> IrisStructureLocator.suppressesVanilla(engine, "minecraft:ancient_city"),
                "not runtime-viable");
    }

    @Test
    public void missingVanillaSourceFailsInsteadOfFallingBackToNative() {
        IrisData data = mock(IrisData.class);
        IrisStructure structure = new IrisStructure();
        structure.setLoadKey("test:city");
        ModuleResourceFixture.of(data).resource(StructureResources.STRUCTURES, "test:city", structure);
        registerSinglePieceGraph(data, structure, "test:city");
        StructurePlacement placement = placement("nativeSuppression", NativeStructureSuppression.REPLACE_SOURCE);
        withAdded(placement, "structures", "test:city");
        IrisDimension dimension = mock(IrisDimension.class);
        when(dimension.getModuleSettings()).thenReturn(declaring(new KList<StructurePlacement>().qadd(placement)));
        when(dimension.getAllRegions(any())).thenReturn(new KList<>());
        when(dimension.getReachableBiomes(any())).thenReturn(new KList<>());
        Engine engine = mock(Engine.class);
        when(engine.getGeneration()).thenReturn(new EngineGeneration(engine));
        when(engine.getData()).thenReturn(data);
        when(engine.getDimension()).thenReturn(dimension);

        assertReplacementFailure(
                () -> IrisStructureLocator.suppressesVanilla(engine, "minecraft:ancient_city"),
                "valid namespaced vanillaSource");
    }

    @Test
    public void missingNonReplacementStructureFailsInsteadOfBeingSkipped() {
        IrisData data = mock(IrisData.class);
        ModuleResourceFixture.serve(data);
        StructurePlacement placement = new StructurePlacement();
        withAdded(placement, "structures", "test:missing");
        IrisDimension dimension = mock(IrisDimension.class);
        when(dimension.getModuleSettings()).thenReturn(declaring(new KList<StructurePlacement>().qadd(placement)));
        when(dimension.getAllRegions(any())).thenReturn(new KList<>());
        when(dimension.getReachableBiomes(any())).thenReturn(new KList<>());
        Engine engine = mock(Engine.class);
        when(engine.getGeneration()).thenReturn(new EngineGeneration(engine));
        when(engine.getData()).thenReturn(data);
        when(engine.getDimension()).thenReturn(dimension);

        assertReplacementFailure(
                () -> IrisStructureLocator.isPlaced(engine, "test:missing"),
                "references missing structure 'test:missing'");
    }

    @Test
    public void placementResolutionRejectsAnUnboundEngine() {
        assertReplacementFailure(
                () -> IrisStructureLocator.resolvePlacement(null, new StructurePlacement(), 0, 0),
                "fully bound engine");
    }

    @Test
    public void replacementOutputFailureIncludesStructureAndChunkContext() {
        StructurePlacement placement = placement("nativeSuppression", NativeStructureSuppression.REPLACE_SOURCE);
        withAdded(placement, "structures", "test:city");

        assertReplacementFailure(
                () -> IrisStructureLocator.requirePlacementOutput(
                        placement, "test:city", 12, -7, false, "runtime assembly produced no pieces"),
                "structure 'test:city' failed in chunk 12,-7: runtime assembly produced no pieces");
    }

    @Test
    public void nonReplacementOutputAbsenceRemainsSkippable() {
        StructurePlacement placement = new StructurePlacement();
        withAdded(placement, "structures", "test:city");

        assertFalse(IrisStructureLocator.requirePlacementOutput(
                placement, "test:city", 12, -7, false, "runtime assembly produced no pieces"));
        assertTrue(IrisStructureLocator.requirePlacementOutput(
                placement, "test:city", 12, -7, true, "runtime assembly produced no pieces"));
    }

    @Test
    public void startsInChunkIsFalseForNullEngine() {
        assertFalse(IrisStructureLocator.startsInChunk(null, "minecraft:ancient_city", 0, 0));
    }

    @Test
    public void locateReturnsNotFoundForNullEngine() {
        assertEquals(IrisStructureLocator.LocateStatus.NOT_FOUND,
                IrisStructureLocator.locate(null, "minecraft:village_taiga", 0, 0, 100).status());
    }

    @Test
    public void locateReturnsNotFoundForNullOrEmptyKey() {
        Engine engine = mock(Engine.class);
        when(engine.getGeneration()).thenReturn(new EngineGeneration(engine));
        assertEquals(IrisStructureLocator.LocateStatus.NOT_FOUND,
                IrisStructureLocator.locate(engine, null, 0, 0, 100).status());
        assertEquals(IrisStructureLocator.LocateStatus.NOT_FOUND,
                IrisStructureLocator.locate(engine, "", 0, 0, 100).status());
    }

    @Test
    public void zeroDensityLocateReturnsWithoutScanningPlacementGraphs() {
        Engine engine = densityEngine(0.0, false, -64, 384, -2032, 2032);

        IrisStructureLocator.LocateResult result =
                IrisStructureLocator.locate(engine, "test:density", 0, 0, 100);

        assertEquals(IrisStructureLocator.LocateStatus.NOT_FOUND, result.status());
        verify(engine, never()).getComplex();
    }

    @Test
    public void impossibleDensityHeightBandReturnsWithoutScanningPlacementGraphs() {
        Engine engine = densityEngine(1.0, true, -64, 384, 500, 600);

        IrisStructureLocator.LocateResult result =
                IrisStructureLocator.locate(engine, "test:density", 0, 0, 100);

        assertEquals(IrisStructureLocator.LocateStatus.NOT_FOUND, result.status());
        verify(engine, never()).getComplex();
    }

    @Test
    public void densityLocateReportsSafetyLimitAfterFixedCandidateBudget() {
        Engine engine = densityEngine(1.0, false, -64, 384, -2032, 2032);

        IrisStructureLocator.LocateResult result =
                IrisStructureLocator.locate(engine, "test:density", 0, 0, 2048);

        assertEquals(IrisStructureLocator.LocateStatus.SEARCH_LIMIT_REACHED, result.status());
        assertFalse(result.found());
        verify(engine, times(4_096)).getComplex();
    }

    @Test
    public void densityLocateDoesNotReturnPartialRingWinnerAtSafetyLimit() {
        Engine engine = densityEngine(1.0, false, -64, 384, -2032, 2032);

        IrisStructureLocator.LocateResult result = IrisStructureLocator.locate(
                engine, "test:density", 0, 0, 2048,
                (chunkX, chunkZ) -> chunkX == 31 && chunkZ == 31
                        || chunkX == 0 && chunkZ == 32);

        assertEquals(IrisStructureLocator.LocateStatus.SEARCH_LIMIT_REACHED, result.status());
        assertFalse(result.found());
    }

    @Test
    public void zeroRadiusDoesNotSearchAdjacentDensityChunks() {
        Engine engine = densityEngine(1.0, false, -64, 384, -2032, 2032);

        IrisStructureLocator.LocateResult result = IrisStructureLocator.locate(
                engine, "test:density", 0, 0, 0,
                (chunkX, chunkZ) -> chunkX == 1 && chunkZ == 0);

        assertEquals(IrisStructureLocator.LocateStatus.NOT_FOUND, result.status());
    }

    @Test
    public void locateRanksEditableStartsByResolvedBlockOrigin() {
        Engine engine = densityEngine(1.0, false, -64, 384, -2032, 2032);
        when(engine.getHeight(anyInt(), anyInt(), eq(true))).thenReturn(128);
        when(engine.getDimension().getFluidHeight()).thenReturn(63);
        StructurePlacement placement = StructurePlacementScope.placementsOf(engine.getDimension()).get(0);
        NearestScenario scenario = nearestEqualChunkDistanceScenario(engine, placement);
        assertNotNull(scenario);

        IrisStructureLocator.LocateResult result = IrisStructureLocator.locate(
                engine, "test:density", scenario.fromBlockX(), scenario.fromBlockZ(), 1,
                (chunkX, chunkZ) -> chunkX == scenario.firstChunkX() && chunkZ == scenario.firstChunkZ()
                        || chunkX == scenario.nearestChunkX() && chunkZ == scenario.nearestChunkZ());

        assertEquals(IrisStructureLocator.LocateStatus.FOUND, result.status());
        assertEquals(scenario.nearestOriginX(), result.originX());
        assertEquals(scenario.nearestOriginZ(), result.originZ());
    }

    @Test
    public void diagonalRadiusBoundaryUsesVanillaChebyshevSemantics() {
        assertTrue(IrisStructureLocator.withinRadius(1, 1, 0, 0, 1));
        assertTrue(IrisStructureLocator.withinRadius(-1, -1, 0, 0, 1));
        assertFalse(IrisStructureLocator.withinRadius(2, 1, 0, 0, 1));
        assertFalse(IrisStructureLocator.withinRadius(1, 1, 0, 0, 0));
    }

    @Test
    public void searchableDensityRequiresPositiveProbabilityAndWorldHeightOverlap() {
        Engine engine = mock(Engine.class);
        when(engine.getGeneration()).thenReturn(new EngineGeneration(engine));
        when(engine.getMinHeight()).thenReturn(-64);
        when(engine.getHeight()).thenReturn(384);
        StructurePlacement placement = new StructurePlacement();
        TestSettings.with(placement, "distribution", StructureDistribution.DENSITY);
        TestSettings.with(placement, "minHeight", -32);
        TestSettings.with(placement, "maxHeight", 128);

        TestSettings.with(placement, "density", Double.NaN);
        assertFalse(IrisStructureLocator.isSearchableDensityPlacement(engine, placement));
        TestSettings.with(placement, "density", 0.0);
        assertFalse(IrisStructureLocator.isSearchableDensityPlacement(engine, placement));
        TestSettings.with(placement, "density", 0.01);
        assertTrue(IrisStructureLocator.isSearchableDensityPlacement(engine, placement));
        TestSettings.with(placement, "minHeight", 400);
        TestSettings.with(placement, "maxHeight", 500);
        assertFalse(IrisStructureLocator.isSearchableDensityPlacement(engine, placement));
    }

    @Test
    public void placementIndexIsKeptPerWorld() {
        IrisData sharedData = mock(IrisData.class);
        Engine firstEngine = emptyEngine(sharedData);
        Engine secondEngine = emptyEngine(sharedData);
        Set<String> firstIndex = IrisStructureLocator.placedKeys(firstEngine);
        assertSame(firstIndex, IrisStructureLocator.placedKeys(firstEngine));
        assertNotSame(firstIndex, IrisStructureLocator.placedKeys(secondEngine));

        when(firstEngine.getGeneration()).thenReturn(new EngineGeneration(firstEngine));
        assertNotSame("a new world builds its own index", firstIndex, IrisStructureLocator.placedKeys(firstEngine));
    }

    @Test
    public void assembledBoundsMustFitVerticallyAndHorizontally() {
        KList<PlacedStructurePiece> fitting = new KList<>();
        fitting.add(piece(-4, 1, -4, 4, 20, 4));
        assertTrue(IrisStructureLocator.fitsWorldBounds(fitting, 0, 20, 0, 0, 4));

        KList<PlacedStructurePiece> belowWorld = new KList<>();
        belowWorld.add(piece(-4, -1, -4, 4, 20, 4));
        assertFalse(IrisStructureLocator.fitsWorldBounds(belowWorld, 0, 20, 0, 0, 4));

        KList<PlacedStructurePiece> outsideRadius = new KList<>();
        outsideRadius.add(piece(-4, 1, -4, 5, 20, 4));
        assertFalse(IrisStructureLocator.fitsWorldBounds(outsideRadius, 0, 20, 0, 0, 4));
    }

    @Test
    public void emptyAssemblyDoesNotFit() {
        assertFalse(IrisStructureLocator.fitsWorldBounds(new KList<>(), 0, 20, 0, 0, 4));
    }

    @Test
    public void selectedStructureKeyDoesNotMatchUnselectedPlacementMember() {
        StructurePlacement placement = new StructurePlacement();
        withAdded(placement, "structures", "test:first");
        withAdded(placement, "structures", "test:second");
        RNG selectsLast = new RNG(1L) {
            @Override
            public int nextInt(int bound) {
                return bound - 1;
            }
        };
        String selectedKey = IrisStructureLocator.selectStructureKey(placement, selectsLast);
        assertEquals("test:second", selectedKey);

        IrisStructure selected = new IrisStructure();
        selected.setLoadKey("test:second");
        selected.setVanillaSource("minecraft:second");
        IrisStructureLocator.ResolvedPlacement resolved = new IrisStructureLocator.ResolvedPlacement(
                placement, selectedKey, selected, new KList<>(), selectsLast, 0, 64, 0, false);
        assertFalse(IrisStructureLocator.matchesResolved(resolved, "test:first"));
        assertTrue(IrisStructureLocator.matchesResolved(resolved, "test:second"));
        assertTrue(IrisStructureLocator.matchesResolved(resolved, "minecraft:second"));
    }

    @Test
    public void undergroundAssemblyIsShiftedToFitOrRejectedWhole() {
        StructurePlacement placement = new StructurePlacement();
        TestSettings.with(placement, "underground", true);
        TestSettings.with(placement, "minHeight", -10);
        TestSettings.with(placement, "maxHeight", 10);

        KList<PlacedStructurePiece> movable = new KList<>();
        movable.add(piece(-4, -10, -4, 4, 5, 4));
        assertEquals(Integer.valueOf(10), IrisStructureLocator.resolveVerticalShift(movable, placement, -10, 0, 319));

        KList<PlacedStructurePiece> tooTall = new KList<>();
        tooTall.add(piece(-4, -100, -4, 4, 400, 4));
        assertNull(IrisStructureLocator.resolveVerticalShift(tooTall, placement, 0, 0, 319));

        TestSettings.with(placement, "underground", false);
        KList<PlacedStructurePiece> clippedSurface = new KList<>();
        clippedSurface.add(piece(-4, 300, -4, 4, 330, 4));
        assertNull(IrisStructureLocator.resolveVerticalShift(clippedSurface, placement, 300, -64, 319));
    }

    @Test
    public void caveAssemblyBoundsAlignToTheRequestedAnchorFace() {
        KList<PlacedStructurePiece> pieces = new KList<>();
        pieces.add(piece(-2, 10, -2, 2, 14, 2));
        StructurePlacement placement = new StructurePlacement();

        TestSettings.with(placement, "anchor", StructureAnchorMode.CAVE_FLOOR);
        KList<PlacedStructurePiece> floor = IrisStructureLocator.alignCavePieces(pieces, placement, 40);
        assertEquals(40, floor.getFirst().getMinY());
        assertEquals(44, floor.getFirst().getMaxY());

        TestSettings.with(placement, "anchor", StructureAnchorMode.CAVE_CEILING);
        KList<PlacedStructurePiece> ceiling = IrisStructureLocator.alignCavePieces(pieces, placement, 40);
        assertEquals(36, ceiling.getFirst().getMinY());
        assertEquals(40, ceiling.getFirst().getMaxY());

        TestSettings.with(placement, "anchor", StructureAnchorMode.CAVE_CENTER);
        KList<PlacedStructurePiece> center = IrisStructureLocator.alignCavePieces(pieces, placement, 40);
        assertEquals(38, center.getFirst().getMinY());
        assertEquals(42, center.getFirst().getMaxY());
    }

    @Test
    public void caveSearchabilityReservesVerticalBoundaryCells() {
        Engine engine = mock(Engine.class);
        when(engine.getGeneration()).thenReturn(new EngineGeneration(engine));
        when(engine.getMinHeight()).thenReturn(-64);
        when(engine.getHeight()).thenReturn(384);
        StructurePlacement placement = placement("distribution", StructureDistribution.DENSITY, "density", 1D, "anchor", StructureAnchorMode.CAVE_FLOOR, "minHeight", 319, "maxHeight", 319);

        assertFalse(IrisStructureLocator.isSearchableDensityPlacement(engine, placement));
        TestSettings.with(placement, "minHeight", 318, "maxHeight", 318);
        assertTrue(IrisStructureLocator.isSearchableDensityPlacement(engine, placement));

        when(engine.getHeight()).thenReturn(2);
        TestSettings.with(placement, "minHeight", -2032, "maxHeight", 2032);
        assertFalse(IrisStructureLocator.isSearchableDensityPlacement(engine, placement));
    }

    @Test
    public void undergroundAssemblyIsShiftedBelowEveryTerrainColumn() {
        Engine engine = mock(Engine.class);
        when(engine.getGeneration()).thenReturn(new EngineGeneration(engine));
        when(engine.getMinHeight()).thenReturn(-64);
        when(engine.getHeight(anyInt(), anyInt(), eq(true)))
                .thenAnswer(invocation -> invocation.<Integer>getArgument(0) == 1 ? 140 : 164);
        StructurePlacement placement = new StructurePlacement();
        TestSettings.with(placement, "underground", true);
        TestSettings.with(placement, "minHeight", -64);
        TestSettings.with(placement, "maxHeight", 100);
        KList<PlacedStructurePiece> pieces = new KList<>();
        pieces.add(piece(0, 60, 0, 1, 80, 0));

        assertEquals(Integer.valueOf(-5), IrisStructureLocator.resolveUndergroundBurialShift(
                engine, pieces, placement, 60, -63, 319));

        TestSettings.with(placement, "minHeight", 60);
        TestSettings.with(placement, "maxHeight", 60);
        assertNull(IrisStructureLocator.resolveUndergroundBurialShift(
                engine, pieces, placement, 60, -63, 319));
    }

    @Test
    public void undergroundBurialIncludesForceCarveCeiling() {
        Engine engine = mock(Engine.class);
        when(engine.getGeneration()).thenReturn(new EngineGeneration(engine));
        when(engine.getMinHeight()).thenReturn(-64);
        when(engine.getHeight(anyInt(), anyInt(), eq(true))).thenReturn(164);
        StructurePlacement placement = new StructurePlacement();
        TestSettings.with(placement, "underground", true);
        TestSettings.with(placement, "minHeight", -64);
        TestSettings.with(placement, "maxHeight", 100);
        TestSettings.with(placement, "terrain", forceCarve("\"horizontalPadding\":2,\"ceilingPadding\":20,\"shape\":\"ERODED\""));
        KList<PlacedStructurePiece> pieces = new KList<>();
        pieces.add(piece(0, 60, 0, 1, 80, 1));

        assertEquals(Integer.valueOf(-14), IrisStructureLocator.resolveUndergroundBurialShift(
                engine, pieces, placement, 60, -63, 319));

        TestSettings.with(placement, "terrain", forceCarve("\"horizontalPadding\":2,\"ceilingPadding\":20,\"shape\":\"ERODED\",\"erosionStrength\":0"));
        assertEquals(Integer.valueOf(-1), IrisStructureLocator.resolveUndergroundBurialShift(
                engine, pieces, placement, 60, -63, 319));

        TestSettings.with(placement, "terrain", forceCarve("\"horizontalPadding\":2,\"ceilingPadding\":20,\"shape\":\"ROUNDED\",\"erosionStrength\":0"));
        assertEquals(Integer.valueOf(-1), IrisStructureLocator.resolveUndergroundBurialShift(
                engine, pieces, placement, 60, -63, 319));

        TestSettings.with(placement, "terrain", forceCarve("\"horizontalPadding\":2,\"ceilingPadding\":0,\"shape\":\"ROUNDED\",\"erosionStrength\":0"));
        assertEquals(Integer.valueOf(0), IrisStructureLocator.resolveUndergroundBurialShift(
                engine, pieces, placement, 60, -63, 319));
    }

    @Test
    public void zeroForceCarvePaddingDoesNotExpandTheBurialEnvelope() {
        Engine engine = mock(Engine.class);
        when(engine.getGeneration()).thenReturn(new EngineGeneration(engine));
        when(engine.getMinHeight()).thenReturn(-64);
        when(engine.getHeight(anyInt(), anyInt(), eq(true))).thenReturn(164);
        StructurePlacement placement = placement("underground", true, "minHeight", -64, "maxHeight", 100, "terrain", forceCarve("\"shape\":\"ROUNDED\",\"horizontalPadding\":0,\"ceilingPadding\":0"));
        KList<PlacedStructurePiece> pieces = new KList<>();
        pieces.add(piece(4, 60, 7, 4, 80, 7));

        assertEquals(Integer.valueOf(0), IrisStructureLocator.resolveUndergroundBurialShift(
                engine, pieces, placement, 60, -63, 319));
        verify(engine, times(1)).getHeight(4, 7, true);
    }

    @Test
    public void exactYOnlyCoversPlacementPathsWithResolvedAbsoluteAnchors() {
        StructurePlacement placement = new StructurePlacement();
        IrisStructure structure = new IrisStructure();
        structure.setPlaceMode(ObjectPlaceMode.CENTER_HEIGHT);
        KList<PlacedStructurePiece> onePiece = new KList<>();
        onePiece.add(piece(-4, 1, -4, 4, 20, 4));
        assertFalse(IrisStructureLocator.hasExactY(placement, structure, onePiece));

        structure.setPlaceMode(ObjectPlaceMode.STRUCTURE_PIECE);
        assertTrue(IrisStructureLocator.hasExactY(placement, structure, onePiece));

        structure.setPlaceMode(ObjectPlaceMode.CENTER_HEIGHT);
        TestSettings.with(placement, "underground", true);
        assertTrue(IrisStructureLocator.hasExactY(placement, structure, onePiece));

        TestSettings.with(placement, "underground", false);
        KList<PlacedStructurePiece> multiplePieces = new KList<>(onePiece);
        multiplePieces.add(piece(5, 1, -4, 12, 20, 4));
        assertTrue(IrisStructureLocator.hasExactY(placement, structure, multiplePieces));
    }

    @Test
    public void randomSpreadCellRingStopUsesGeometricLowerBound() {
        assertEquals(0L, IrisStructureLocator.cellRingDistanceLowerBound(0, 32, 31, 8, 0, 0));
        assertEquals(1L, IrisStructureLocator.cellRingDistanceLowerBound(1, 32, 31, 8, 0, 0));
        assertEquals(33L, IrisStructureLocator.cellRingDistanceLowerBound(2, 32, 31, 8, 0, 0));
        assertEquals(1L, IrisStructureLocator.cellRingDistanceLowerBound(1, 32, 0, 0, 0, 0));
    }

    @Test
    public void chunkBlockLowerBoundSaturatesAtExtremeCoordinates() {
        assertEquals(Long.MAX_VALUE, IrisStructureLocator.chunkBlockDistanceSquaredLowerBound(
                Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE));
        assertEquals(1L, IrisStructureLocator.chunkBlockDistanceSquaredLowerBound(1, 0, 15, 8));
        assertEquals(0L, IrisStructureLocator.chunkBlockDistanceSquaredLowerBound(0, 0, 15, 8));
    }

    private NearestScenario nearestEqualChunkDistanceScenario(
            Engine engine, StructurePlacement placement) {
        int[][] chunks = {
                {-1, -1}, {-1, 0}, {-1, 1}, {0, -1},
                {0, 1}, {1, -1}, {1, 0}, {1, 1}
        };
        IrisStructureLocator.ResolvedPlacement[] resolved =
                new IrisStructureLocator.ResolvedPlacement[chunks.length];
        for (int i = 0; i < chunks.length; i++) {
            resolved[i] = IrisStructureLocator.resolvePlacement(
                    engine, placement, chunks[i][0], chunks[i][1]);
            assertNotNull(resolved[i]);
        }
        for (int fromBlockX = 0; fromBlockX < 16; fromBlockX++) {
            for (int fromBlockZ = 0; fromBlockZ < 16; fromBlockZ++) {
                for (int first = 0; first < chunks.length; first++) {
                    int firstChunkDistance = chunks[first][0] * chunks[first][0]
                            + chunks[first][1] * chunks[first][1];
                    long firstDistance = resolvedDistanceSquared(
                            resolved[first], fromBlockX, fromBlockZ);
                    for (int later = first + 1; later < chunks.length; later++) {
                        int laterChunkDistance = chunks[later][0] * chunks[later][0]
                                + chunks[later][1] * chunks[later][1];
                        long laterDistance = resolvedDistanceSquared(
                                resolved[later], fromBlockX, fromBlockZ);
                        if (firstChunkDistance == laterChunkDistance && laterDistance < firstDistance) {
                            return new NearestScenario(
                                    fromBlockX, fromBlockZ,
                                    chunks[first][0], chunks[first][1],
                                    chunks[later][0], chunks[later][1],
                                    resolved[later].originX(), resolved[later].originZ());
                        }
                    }
                }
            }
        }
        return null;
    }

    private long resolvedDistanceSquared(
            IrisStructureLocator.ResolvedPlacement resolved, int fromBlockX, int fromBlockZ) {
        long dx = (long) resolved.originX() - fromBlockX;
        long dz = (long) resolved.originZ() - fromBlockZ;
        return dx * dx + dz * dz;
    }

    private PlacedStructurePiece piece(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        return new PlacedStructurePiece(null, null, 0, 0, 0, null, minX, minY, minZ, maxX, maxY, maxZ);
    }

    private Engine emptyEngine(IrisData data) {
        Engine engine = mock(Engine.class);
        when(engine.getGeneration()).thenReturn(new EngineGeneration(engine));
        IrisDimension dimension = mock(IrisDimension.class);
        when(engine.getData()).thenReturn(data);
        when(engine.getDimension()).thenReturn(dimension);
        when(dimension.getModuleSettings()).thenReturn(declaring(new KList<>()));
        when(dimension.getAllRegions(engine)).thenReturn(new KList<>());
        when(dimension.getReachableBiomes(engine)).thenReturn(new KList<>());
        return engine;
    }

    private Engine densityEngine(double density, boolean underground, int worldMin, int worldHeight,
                                 int placementMin, int placementMax) {
        IrisData data = mock(IrisData.class);
        IrisStructure structure = new IrisStructure();
        structure.setLoadKey("test:density");
        ModuleResourceFixture.of(data).resource(StructureResources.STRUCTURES, "test:density", structure);
        registerSinglePieceGraph(data, structure, "test:density");

        StructurePlacement placement = new StructurePlacement();
        withAdded(placement, "structures", "test:density");
        TestSettings.with(placement, "distribution", StructureDistribution.DENSITY);
        TestSettings.with(placement, "density", density);
        TestSettings.with(placement, "underground", underground);
        TestSettings.with(placement, "minHeight", placementMin);
        TestSettings.with(placement, "maxHeight", placementMax);

        Engine engine = mock(Engine.class);

        when(engine.getGeneration()).thenReturn(new EngineGeneration(engine));
        IrisDimension dimension = mock(IrisDimension.class);
        KList<StructurePlacement> placements = new KList<>();
        placements.add(placement);
        when(engine.getData()).thenReturn(data);
        when(engine.getDimension()).thenReturn(dimension);
        when(engine.getSeedManager()).thenReturn(new SeedManager(77L));
        when(engine.getMinHeight()).thenReturn(worldMin);
        when(engine.getHeight()).thenReturn(worldHeight);
        when(dimension.getModuleSettings()).thenReturn(declaring(placements));
        when(dimension.getAllRegions(engine)).thenReturn(new KList<>());
        when(dimension.getReachableBiomes(engine)).thenReturn(new KList<>());
        return engine;
    }

    private void registerSinglePieceGraph(IrisData data, IrisStructure structure, String prefix) {
        String poolKey = prefix + "/start";
        String pieceKey = prefix + "/piece";
        String objectKey = prefix + "/object";
        structure.setStartPool(poolKey);
        IrisJigsawPool pool = new IrisJigsawPool();
        pool.getPieces().add(new IrisJigsawPieceEntry().setPiece(pieceKey).setWeight(1));
        IrisJigsawPiece piece = new IrisJigsawPiece();
        piece.setObject(objectKey);
        IrisObject object = mock(IrisObject.class);
        when(object.getW()).thenReturn(1);
        when(object.getH()).thenReturn(1);
        when(object.getD()).thenReturn(1);
        ModuleResourceFixture.of(data).resource(StructureResources.POOLS, poolKey, pool);
        ModuleResourceFixture.of(data).resource(StructureResources.PIECES, pieceKey, piece);
        when(data.load(IrisObject.class, objectKey, false)).thenReturn(object);
    }

    private void assertReplacementFailure(Runnable operation, String expectedMessage) {
        try {
            operation.run();
        } catch (IllegalStateException e) {
            assertTrue(e.getMessage(), e.getMessage().contains(expectedMessage));
            return;
        }
        throw new AssertionError("Expected strict native replacement failure containing '" + expectedMessage + "'");
    }

    private record NearestScenario(
            int fromBlockX,
            int fromBlockZ,
            int firstChunkX,
            int firstChunkZ,
            int nearestChunkX,
            int nearestChunkZ,
            int nearestOriginX,
            int nearestOriginZ) {
    }

    private static StructureTerrain forceCarve(String options) {
        return new Gson().fromJson("{\"mode\":\"FORCE_CARVE\"," + options + "}", StructureTerrain.class);
    }
}
