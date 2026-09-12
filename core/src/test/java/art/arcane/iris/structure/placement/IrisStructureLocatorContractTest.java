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

package art.arcane.iris.structure.placement;

import art.arcane.iris.generation.runtime.Engine;
import art.arcane.iris.generation.runtime.SeedManager;

import art.arcane.iris.pack.loading.IrisData;
import art.arcane.iris.generation.runtime.IrisComplex;
import art.arcane.iris.structure.graph.StructureGraphCatalog;
import art.arcane.iris.pack.value.IrisDirection;
import art.arcane.iris.generation.terrain.IrisDimension;
import art.arcane.iris.structure.jigsaw.IrisJigsawConnector;
import art.arcane.iris.structure.jigsaw.IrisJigsawPiece;
import art.arcane.iris.structure.jigsaw.IrisJigsawPieceEntry;
import art.arcane.iris.structure.jigsaw.IrisJigsawPool;
import art.arcane.iris.structure.nativegen.IrisNativeStructure;
import art.arcane.iris.structure.object.IrisObject;
import art.arcane.iris.pack.value.IrisPosition;
import art.arcane.iris.generation.terrain.IrisRegion;
import art.arcane.iris.structure.nativegen.NativeStructureSuppression;
import art.arcane.iris.structure.object.ObjectPlaceMode;
import art.arcane.volmlib.util.collection.KList;
import art.arcane.volmlib.util.math.RNG;
import art.arcane.volmlib.util.stream.ProceduralStream;
import org.junit.Test;

import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyDouble;
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
        randomSpread.getDimension().getStructures().get(0)
                .setDistribution(StructureDistribution.RANDOM_SPREAD);
        Engine concentricRings = densityEngine(1.0, false, -64, 384, 400, 500);
        concentricRings.getDimension().getStructures().get(0)
                .setDistribution(StructureDistribution.CONCENTRIC_RINGS);

        assertFalse(IrisStructureLocator.hasLocatablePlacement(randomSpread, "test:density"));
        assertFalse(IrisStructureLocator.hasLocatablePlacement(concentricRings, "test:density"));
    }

    @Test
    public void locatableIndexIncludesAliasesAndSeparatesEditableFromNativePlacements() {
        IrisData data = mock(IrisData.class);
        IrisStructure structure = new IrisStructure();
        structure.setLoadKey("test:city");
        structure.setVanillaSource("source:city");
        when(data.load(IrisStructure.class, "test:city_definition", false)).thenReturn(structure);

        IrisStructurePlacement editable = new IrisStructurePlacement();
        editable.getStructures().add("test:city_definition");
        IrisStructurePlacement nativePlacement = new IrisStructurePlacement();
        nativePlacement.getNativeStructures().add(new IrisNativeStructure()
                .setStructure("source:native_city"));
        IrisDimension dimension = mock(IrisDimension.class);
        KList<IrisStructurePlacement> placements = new KList<>();
        placements.add(editable);
        placements.add(nativePlacement);
        Engine engine = mock(Engine.class);
        when(engine.getData()).thenReturn(data);
        when(engine.getDimension()).thenReturn(dimension);
        when(engine.getMinHeight()).thenReturn(-64);
        when(engine.getHeight()).thenReturn(384);
        when(dimension.getStructures()).thenReturn(placements);
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
        IrisDimension dimension = mock(IrisDimension.class);
        IrisStructurePlacement placement = new IrisStructurePlacement()
                .setNativeSuppression(NativeStructureSuppression.REPLACE_SOURCE);
        placement.getNativeStructures().add(new IrisNativeStructure()
                .setStructure("minecraft:ancient_city"));
        KList<IrisStructurePlacement> placements = new KList<>();
        placements.add(placement);
        when(engine.getData()).thenReturn(data);
        when(engine.getDimension()).thenReturn(dimension);
        when(dimension.getStructures()).thenReturn(placements);
        when(dimension.getAllRegions(engine)).thenReturn(new KList<>());
        when(dimension.getReachableBiomes(engine)).thenReturn(new KList<>());

        assertTrue(IrisStructureLocator.isPlaced(engine, "minecraft:ancient_city"));
        assertTrue(IrisStructureLocator.suppressesVanilla(engine, "minecraft:ancient_city"));
        assertFalse(IrisStructureLocator.suppressesVanilla(engine, "minecraft:village"));
    }

    @Test
    public void suppressesVanillaIsFalseForNullOrEmptyKey() {
        Engine engine = mock(Engine.class);
        assertFalse(IrisStructureLocator.suppressesVanilla(engine, null));
        assertFalse(IrisStructureLocator.suppressesVanilla(engine, ""));
    }

    @Test
    public void vanillaSourceRequiresExplicitDimensionSuppression() {
        IrisData data = mock(IrisData.class);
        IrisStructure structure = new IrisStructure();
        structure.setLoadKey("test:city");
        structure.setVanillaSource("minecraft:ancient_city");
        when(data.load(IrisStructure.class, "test:city", false)).thenReturn(structure);
        registerSinglePieceGraph(data, structure, "test:city");

        IrisStructurePlacement placement = new IrisStructurePlacement();
        placement.getStructures().add("test:city");
        IrisDimension dimension = mock(IrisDimension.class);
        KList<IrisStructurePlacement> placements = new KList<>();
        placements.add(placement);
        when(dimension.getStructures()).thenReturn(placements);
        when(dimension.getAllRegions(any())).thenReturn(new KList<>());
        when(dimension.getReachableBiomes(any())).thenReturn(new KList<>());

        Engine engine = mock(Engine.class);
        when(engine.getData()).thenReturn(data);
        when(engine.getDimension()).thenReturn(dimension);

        assertTrue(IrisStructureLocator.isPlaced(engine, "minecraft:ancient_city"));
        assertFalse(IrisStructureLocator.suppressesVanilla(engine, "minecraft:ancient_city"));
        placement.setNativeSuppression(NativeStructureSuppression.REPLACE_SOURCE);
        IrisStructureLocator.invalidate(engine);
        assertTrue(IrisStructureLocator.suppressesVanilla(engine, "minecraft:ancient_city"));
    }

    @Test
    public void regionReplacementFailsInsteadOfFallingBackToNative() {
        IrisData data = mock(IrisData.class);
        IrisStructure structure = new IrisStructure();
        structure.setLoadKey("test:city");
        structure.setVanillaSource("minecraft:ancient_city");
        when(data.load(IrisStructure.class, "test:city", false)).thenReturn(structure);
        registerSinglePieceGraph(data, structure, "test:city");

        IrisStructurePlacement placement = new IrisStructurePlacement();
        placement.getStructures().add("test:city");
        placement.setNativeSuppression(NativeStructureSuppression.REPLACE_SOURCE);
        IrisRegion region = mock(IrisRegion.class);
        when(region.getStructures()).thenReturn(new KList<IrisStructurePlacement>().qadd(placement));
        IrisDimension dimension = mock(IrisDimension.class);
        when(dimension.getStructures()).thenReturn(new KList<>());
        when(dimension.getAllRegions(any())).thenReturn(new KList<IrisRegion>().qadd(region));
        when(dimension.getReachableBiomes(any())).thenReturn(new KList<>());

        Engine engine = mock(Engine.class);
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
        when(data.load(IrisStructure.class, "test:city", false)).thenReturn(structure);
        when(data.load(IrisJigsawPool.class, "test:start", false)).thenReturn(startPool);
        when(data.load(IrisJigsawPool.class, "test:target", false)).thenReturn(targetPool);
        when(data.load(IrisJigsawPiece.class, "test:start-piece", false)).thenReturn(startPiece);
        when(data.load(IrisJigsawPiece.class, "test:target-piece", false)).thenReturn(targetPiece);
        when(data.load(IrisObject.class, "test:start-object", false)).thenReturn(startObject);
        when(data.load(IrisObject.class, "test:target-object", false)).thenReturn(targetObject);
        assertFalse(StructureGraphCatalog.compile(data, structure).isAssemblyViable());

        IrisStructurePlacement placement = new IrisStructurePlacement()
                .setNativeSuppression(NativeStructureSuppression.REPLACE_SOURCE);
        placement.getStructures().add("test:city");
        IrisDimension dimension = mock(IrisDimension.class);
        when(dimension.getStructures()).thenReturn(new KList<IrisStructurePlacement>().qadd(placement));
        when(dimension.getAllRegions(any())).thenReturn(new KList<>());
        when(dimension.getReachableBiomes(any())).thenReturn(new KList<>());
        Engine engine = mock(Engine.class);
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
        when(data.load(IrisStructure.class, "test:city", false)).thenReturn(structure);
        registerSinglePieceGraph(data, structure, "test:city");
        IrisStructurePlacement placement = new IrisStructurePlacement()
                .setNativeSuppression(NativeStructureSuppression.REPLACE_SOURCE);
        placement.getStructures().add("test:city");
        IrisDimension dimension = mock(IrisDimension.class);
        when(dimension.getStructures()).thenReturn(new KList<IrisStructurePlacement>().qadd(placement));
        when(dimension.getAllRegions(any())).thenReturn(new KList<>());
        when(dimension.getReachableBiomes(any())).thenReturn(new KList<>());
        Engine engine = mock(Engine.class);
        when(engine.getData()).thenReturn(data);
        when(engine.getDimension()).thenReturn(dimension);

        assertReplacementFailure(
                () -> IrisStructureLocator.suppressesVanilla(engine, "minecraft:ancient_city"),
                "valid namespaced vanillaSource");
    }

    @Test
    public void missingNonReplacementStructureFailsInsteadOfBeingSkipped() {
        IrisData data = mock(IrisData.class);
        IrisStructurePlacement placement = new IrisStructurePlacement();
        placement.getStructures().add("test:missing");
        IrisDimension dimension = mock(IrisDimension.class);
        when(dimension.getStructures()).thenReturn(new KList<IrisStructurePlacement>().qadd(placement));
        when(dimension.getAllRegions(any())).thenReturn(new KList<>());
        when(dimension.getReachableBiomes(any())).thenReturn(new KList<>());
        Engine engine = mock(Engine.class);
        when(engine.getData()).thenReturn(data);
        when(engine.getDimension()).thenReturn(dimension);

        assertReplacementFailure(
                () -> IrisStructureLocator.isPlaced(engine, "test:missing"),
                "references missing structure 'test:missing'");
    }

    @Test
    public void placementResolutionRejectsAnUnboundEngine() {
        assertReplacementFailure(
                () -> IrisStructureLocator.resolvePlacement(null, new IrisStructurePlacement(), 0, 0),
                "fully bound engine");
    }

    @Test
    public void replacementOutputFailureIncludesStructureAndChunkContext() {
        IrisStructurePlacement placement = new IrisStructurePlacement()
                .setNativeSuppression(NativeStructureSuppression.REPLACE_SOURCE);
        placement.getStructures().add("test:city");

        assertReplacementFailure(
                () -> IrisStructureLocator.requirePlacementOutput(
                        placement, "test:city", 12, -7, false, "runtime assembly produced no pieces"),
                "structure 'test:city' failed in chunk 12,-7: runtime assembly produced no pieces");
    }

    @Test
    public void nonReplacementOutputAbsenceRemainsSkippable() {
        IrisStructurePlacement placement = new IrisStructurePlacement();
        placement.getStructures().add("test:city");

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
        verify(engine, times(8_192)).getComplex();
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
        IrisComplex complex = mock(IrisComplex.class);
        when(engine.getComplex()).thenReturn(complex);
        when(complex.allowsNewGenerationFootprint(anyInt(), anyInt(), anyInt(), anyInt())).thenReturn(true);
        @SuppressWarnings("unchecked")
        ProceduralStream<Double> riverHeight = mock(ProceduralStream.class);
        when(complex.getRiverWaterSurfaceStream()).thenReturn(riverHeight);
        when(riverHeight.get(anyDouble(), anyDouble())).thenReturn(63D);
        when(engine.getAllBiomes()).thenReturn(new KList<>());
        when(engine.getHeight(anyInt(), anyInt(), eq(true))).thenReturn(128);
        when(engine.getDimension().getFluidHeight()).thenReturn(63);
        IrisStructurePlacement placement = engine.getDimension().getStructures().get(0);
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
        when(engine.getMinHeight()).thenReturn(-64);
        when(engine.getHeight()).thenReturn(384);
        IrisStructurePlacement placement = new IrisStructurePlacement();
        placement.setDistribution(StructureDistribution.DENSITY);
        placement.setMinHeight(-32);
        placement.setMaxHeight(128);

        placement.setDensity(Double.NaN);
        assertFalse(IrisStructureLocator.isSearchableDensityPlacement(engine, placement));
        placement.setDensity(0.0);
        assertFalse(IrisStructureLocator.isSearchableDensityPlacement(engine, placement));
        placement.setDensity(0.01);
        assertTrue(IrisStructureLocator.isSearchableDensityPlacement(engine, placement));
        placement.setMinHeight(400);
        placement.setMaxHeight(500);
        assertFalse(IrisStructureLocator.isSearchableDensityPlacement(engine, placement));
    }

    @Test
    public void invalidateIsNullSafe() {
        IrisStructureLocator.invalidate(null);
    }

    @Test
    public void placementIndexIsScopedAndInvalidatedPerEngine() {
        IrisData sharedData = mock(IrisData.class);
        Engine firstEngine = emptyEngine(sharedData);
        Engine secondEngine = emptyEngine(sharedData);
        Set<String> firstIndex = IrisStructureLocator.placedKeys(firstEngine);
        Set<String> secondIndex = IrisStructureLocator.placedKeys(secondEngine);
        assertNotSame(firstIndex, secondIndex);

        IrisStructureLocator.invalidate(firstEngine);
        assertNotSame(firstIndex, IrisStructureLocator.placedKeys(firstEngine));
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
        IrisStructurePlacement placement = new IrisStructurePlacement();
        placement.getStructures().add("test:first");
        placement.getStructures().add("test:second");
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
        IrisStructurePlacement placement = new IrisStructurePlacement();
        placement.setUnderground(true);
        placement.setMinHeight(-10);
        placement.setMaxHeight(10);

        KList<PlacedStructurePiece> movable = new KList<>();
        movable.add(piece(-4, -10, -4, 4, 5, 4));
        assertEquals(Integer.valueOf(10), IrisStructureLocator.resolveVerticalShift(movable, placement, -10, 0, 319));

        KList<PlacedStructurePiece> tooTall = new KList<>();
        tooTall.add(piece(-4, -100, -4, 4, 400, 4));
        assertNull(IrisStructureLocator.resolveVerticalShift(tooTall, placement, 0, 0, 319));

        placement.setUnderground(false);
        KList<PlacedStructurePiece> clippedSurface = new KList<>();
        clippedSurface.add(piece(-4, 300, -4, 4, 330, 4));
        assertNull(IrisStructureLocator.resolveVerticalShift(clippedSurface, placement, 300, -64, 319));
    }

    @Test
    public void caveAssemblyBoundsAlignToTheRequestedAnchorFace() {
        KList<PlacedStructurePiece> pieces = new KList<>();
        pieces.add(piece(-2, 10, -2, 2, 14, 2));
        IrisStructurePlacement placement = new IrisStructurePlacement();

        placement.setAnchor(IrisStructureAnchorMode.CAVE_FLOOR);
        KList<PlacedStructurePiece> floor = IrisStructureLocator.alignCavePieces(pieces, placement, 40);
        assertEquals(40, floor.getFirst().getMinY());
        assertEquals(44, floor.getFirst().getMaxY());

        placement.setAnchor(IrisStructureAnchorMode.CAVE_CEILING);
        KList<PlacedStructurePiece> ceiling = IrisStructureLocator.alignCavePieces(pieces, placement, 40);
        assertEquals(36, ceiling.getFirst().getMinY());
        assertEquals(40, ceiling.getFirst().getMaxY());

        placement.setAnchor(IrisStructureAnchorMode.CAVE_CENTER);
        KList<PlacedStructurePiece> center = IrisStructureLocator.alignCavePieces(pieces, placement, 40);
        assertEquals(38, center.getFirst().getMinY());
        assertEquals(42, center.getFirst().getMaxY());
    }

    @Test
    public void caveSearchabilityReservesVerticalBoundaryCells() {
        Engine engine = mock(Engine.class);
        when(engine.getMinHeight()).thenReturn(-64);
        when(engine.getHeight()).thenReturn(384);
        IrisStructurePlacement placement = new IrisStructurePlacement()
                .setDistribution(StructureDistribution.DENSITY)
                .setDensity(1D)
                .setAnchor(IrisStructureAnchorMode.CAVE_FLOOR)
                .setMinHeight(319)
                .setMaxHeight(319);

        assertFalse(IrisStructureLocator.isSearchableDensityPlacement(engine, placement));
        placement.setMinHeight(318).setMaxHeight(318);
        assertTrue(IrisStructureLocator.isSearchableDensityPlacement(engine, placement));

        when(engine.getHeight()).thenReturn(2);
        placement.setMinHeight(-2032).setMaxHeight(2032);
        assertFalse(IrisStructureLocator.isSearchableDensityPlacement(engine, placement));
    }

    @Test
    public void undergroundAssemblyIsShiftedBelowEveryTerrainColumn() {
        Engine engine = mock(Engine.class);
        when(engine.getMinHeight()).thenReturn(-64);
        when(engine.getHeight(anyInt(), anyInt(), eq(true)))
                .thenAnswer(invocation -> invocation.<Integer>getArgument(0) == 1 ? 140 : 164);
        IrisStructurePlacement placement = new IrisStructurePlacement();
        placement.setUnderground(true);
        placement.setMinHeight(-64);
        placement.setMaxHeight(100);
        KList<PlacedStructurePiece> pieces = new KList<>();
        pieces.add(piece(0, 60, 0, 1, 80, 0));

        assertEquals(Integer.valueOf(-5), IrisStructureLocator.resolveUndergroundBurialShift(
                engine, pieces, placement, 60, -63, 319));

        placement.setMinHeight(60);
        placement.setMaxHeight(60);
        assertNull(IrisStructureLocator.resolveUndergroundBurialShift(
                engine, pieces, placement, 60, -63, 319));
    }

    @Test
    public void undergroundBurialIncludesForceCarveCeiling() {
        Engine engine = mock(Engine.class);
        when(engine.getMinHeight()).thenReturn(-64);
        when(engine.getHeight(anyInt(), anyInt(), eq(true))).thenReturn(164);
        IrisStructurePlacement placement = new IrisStructurePlacement();
        placement.setUnderground(true);
        placement.setMinHeight(-64);
        placement.setMaxHeight(100);
        IrisStructureTerrain terrain = new IrisStructureTerrain()
                .setMode(IrisStructureTerrainMode.FORCE_CARVE)
                .setHorizontalPadding(2)
                .setCeilingPadding(20)
                .setShape(IrisStructureCarveShape.ERODED);
        placement.setTerrain(terrain);
        KList<PlacedStructurePiece> pieces = new KList<>();
        pieces.add(piece(0, 60, 0, 1, 80, 1));

        assertEquals(Integer.valueOf(-14), IrisStructureLocator.resolveUndergroundBurialShift(
                engine, pieces, placement, 60, -63, 319));

        terrain.setErosionStrength(0D);
        assertEquals(Integer.valueOf(-1), IrisStructureLocator.resolveUndergroundBurialShift(
                engine, pieces, placement, 60, -63, 319));

        terrain.setShape(IrisStructureCarveShape.ROUNDED);
        assertEquals(Integer.valueOf(-1), IrisStructureLocator.resolveUndergroundBurialShift(
                engine, pieces, placement, 60, -63, 319));

        terrain.setCeilingPadding(0);
        assertEquals(Integer.valueOf(0), IrisStructureLocator.resolveUndergroundBurialShift(
                engine, pieces, placement, 60, -63, 319));
    }

    @Test
    public void zeroForceCarvePaddingDoesNotExpandTheBurialEnvelope() {
        Engine engine = mock(Engine.class);
        when(engine.getMinHeight()).thenReturn(-64);
        when(engine.getHeight(anyInt(), anyInt(), eq(true))).thenReturn(164);
        IrisStructurePlacement placement = new IrisStructurePlacement()
                .setUnderground(true)
                .setMinHeight(-64)
                .setMaxHeight(100)
                .setTerrain(new IrisStructureTerrain()
                        .setMode(IrisStructureTerrainMode.FORCE_CARVE)
                        .setShape(IrisStructureCarveShape.ROUNDED)
                        .setHorizontalPadding(0)
                        .setCeilingPadding(0));
        KList<PlacedStructurePiece> pieces = new KList<>();
        pieces.add(piece(4, 60, 7, 4, 80, 7));

        assertEquals(Integer.valueOf(0), IrisStructureLocator.resolveUndergroundBurialShift(
                engine, pieces, placement, 60, -63, 319));
        verify(engine, times(1)).getHeight(4, 7, true);
    }

    @Test
    public void exactYOnlyCoversPlacementPathsWithResolvedAbsoluteAnchors() {
        IrisStructurePlacement placement = new IrisStructurePlacement();
        IrisStructure structure = new IrisStructure();
        structure.setPlaceMode(ObjectPlaceMode.CENTER_HEIGHT);
        KList<PlacedStructurePiece> onePiece = new KList<>();
        onePiece.add(piece(-4, 1, -4, 4, 20, 4));
        assertFalse(IrisStructureLocator.hasExactY(placement, structure, onePiece));

        structure.setPlaceMode(ObjectPlaceMode.STRUCTURE_PIECE);
        assertTrue(IrisStructureLocator.hasExactY(placement, structure, onePiece));

        structure.setPlaceMode(ObjectPlaceMode.CENTER_HEIGHT);
        placement.setUnderground(true);
        assertTrue(IrisStructureLocator.hasExactY(placement, structure, onePiece));

        placement.setUnderground(false);
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
            Engine engine, IrisStructurePlacement placement) {
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
        IrisDimension dimension = mock(IrisDimension.class);
        when(engine.getData()).thenReturn(data);
        when(engine.getDimension()).thenReturn(dimension);
        when(dimension.getStructures()).thenReturn(new KList<>());
        when(dimension.getAllRegions(engine)).thenReturn(new KList<>());
        when(dimension.getReachableBiomes(engine)).thenReturn(new KList<>());
        return engine;
    }

    private Engine densityEngine(double density, boolean underground, int worldMin, int worldHeight,
                                 int placementMin, int placementMax) {
        IrisData data = mock(IrisData.class);
        IrisStructure structure = new IrisStructure();
        structure.setLoadKey("test:density");
        when(data.load(IrisStructure.class, "test:density", false)).thenReturn(structure);
        registerSinglePieceGraph(data, structure, "test:density");

        IrisStructurePlacement placement = new IrisStructurePlacement();
        placement.getStructures().add("test:density");
        placement.setDistribution(StructureDistribution.DENSITY);
        placement.setDensity(density);
        placement.setUnderground(underground);
        placement.setMinHeight(placementMin);
        placement.setMaxHeight(placementMax);

        Engine engine = mock(Engine.class);
        IrisDimension dimension = mock(IrisDimension.class);
        KList<IrisStructurePlacement> placements = new KList<>();
        placements.add(placement);
        when(engine.getData()).thenReturn(data);
        when(engine.getDimension()).thenReturn(dimension);
        when(engine.getSeedManager()).thenReturn(new SeedManager(77L));
        when(engine.getMinHeight()).thenReturn(worldMin);
        when(engine.getHeight()).thenReturn(worldHeight);
        when(dimension.getStructures()).thenReturn(placements);
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
        when(data.load(IrisJigsawPool.class, poolKey, false)).thenReturn(pool);
        when(data.load(IrisJigsawPiece.class, pieceKey, false)).thenReturn(piece);
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
}
