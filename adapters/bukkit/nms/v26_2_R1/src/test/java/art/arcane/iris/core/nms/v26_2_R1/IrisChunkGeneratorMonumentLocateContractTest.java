package art.arcane.iris.core.nms.v26_2_R1;

import art.arcane.iris.nativegen.NativeStructureLocateResults;
import com.mojang.datafixers.util.Pair;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.world.level.levelgen.structure.Structure;
import org.junit.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;

public class IrisChunkGeneratorMonumentLocateContractTest {
    @Test
    public void nativeLocateAllowsMonumentsAfterPolicyAndReachabilityChecks() throws IOException {
        String source = Files.readString(Path.of(System.getProperty("iris.nmsChunkGeneratorSource"))).replace("\r\n", "\n");
        int findStart = source.indexOf("findNearestMapStructure(ServerLevel level");
        assertTrue(findStart >= 0);
        int irisHelperStart = source.indexOf("private Pair<BlockPos, Holder<Structure>> findNearestIrisStructure(", findStart);
        int filterStart = source.indexOf("private HolderSet<Structure> filterReachableStructures", irisHelperStart);
        assertTrue(irisHelperStart > findStart);
        assertTrue(filterStart > irisHelperStart);
        String outerMethod = source.substring(findStart, irisHelperStart);
        String irisHelper = source.substring(irisHelperStart, filterStart);
        int registryLookup = irisHelper.indexOf("level.registryAccess().lookupOrThrow(Registries.STRUCTURE)");
        int placedCheck = irisHelper.indexOf(
                "if (!IrisStructureLocator.hasNativePlacement(engine, structureId))");
        int irisLocate = irisHelper.indexOf("NativeStructureLocatePersistence.search(", placedCheck);
        int searchLimit = irisHelper.indexOf("LocateStatus.SEARCH_LIMIT_REACHED", irisLocate);
        int nativeFilter = outerMethod.indexOf("filterReachableStructures(level, holders)");
        int nativePrediction = outerMethod.indexOf("NativeStructureVanillaLocator.predict(", nativeFilter);
        int irisResolution = outerMethod.indexOf("return findNearestIrisStructure(", nativePrediction);
        int nearestSelection = irisHelper.indexOf(
                "NativeStructureLocateResults.nearest(pos, predicted, nativeLocated)");
        int selectedVerification = irisHelper.indexOf("bestSearch.search().verify(bestResult)", nearestSelection);
        int selectedReference = irisHelper.indexOf(
                "selectedSearch.search().reference(selectedStart)", selectedVerification);
        int reachabilityStart = source.indexOf("private Set<String> reachableStructureKeys", filterStart);
        assertTrue(reachabilityStart > filterStart);
        String filterMethod = source.substring(filterStart, reachabilityStart);
        int policyFilter = filterMethod.indexOf("if (!NativeStructureGenerationPolicy.generates(engine, key))");
        int filterContinue = filterMethod.indexOf("continue;", policyFilter);
        int emptyNativePartition = filterMethod.indexOf("if (candidates.isEmpty())", filterContinue);
        int reachabilityLookup = filterMethod.indexOf("reachableStructureKeys(level)", emptyNativePartition);

        assertTrue(registryLookup >= 0);
        assertTrue(placedCheck > registryLookup);
        assertTrue(irisLocate > placedCheck);
        assertTrue(searchLimit > irisLocate);
        assertTrue(nativeFilter >= 0);
        assertTrue(nativePrediction > nativeFilter);
        assertTrue(irisResolution > nativePrediction);
        assertTrue(nearestSelection > irisLocate);
        assertTrue(selectedVerification > nearestSelection);
        assertTrue(selectedReference > selectedVerification);
        assertTrue(policyFilter >= 0);
        assertTrue(filterContinue > policyFilter);
        assertTrue(emptyNativePartition > filterContinue);
        assertTrue(reachabilityLookup > emptyNativePartition);
        assertFalse(filterMethod.contains("NativeStructureLocateCapability"));
        assertFalse(irisHelper.contains("NativeStructureLocateCapability.isPaperUnavailable(structureId)"));
        assertTrue(irisHelper.contains("NativeStructureLocatePersistence.probe("));
        assertTrue(irisHelper.contains("NativeStructureLocateResults.selectAndReference("));
        assertTrue(irisHelper.contains("selectedSearch.search().reference(selectedStart)"));
        assertTrue(irisHelper.contains("findUnexplored"));
        assertTrue(irisHelper.contains("verified.ownership().locatorY()"));
        assertFalse(outerMethod.contains("delegate.findNearestMapStructure("));
    }

    @Test
    public void mixedLocateSelectsNearestProviderAndPrefersNativeOnTie() {
        BlockPos origin = BlockPos.ZERO;
        Pair<BlockPos, Holder<Structure>> irisNear = Pair.of(new BlockPos(4, 70, 0), null);
        Pair<BlockPos, Holder<Structure>> nativeFar = Pair.of(new BlockPos(8, 70, 0), null);
        Pair<BlockPos, Holder<Structure>> nativeNear = Pair.of(new BlockPos(2, 70, 0), null);
        Pair<BlockPos, Holder<Structure>> nativeTie = Pair.of(new BlockPos(0, 70, 4), null);

        assertSame(irisNear, NativeStructureLocateResults.nearest(origin, irisNear, nativeFar));
        assertSame(nativeNear, NativeStructureLocateResults.nearest(origin, irisNear, nativeNear));
        assertSame(nativeTie, NativeStructureLocateResults.nearest(origin, irisNear, nativeTie));
    }

    @Test
    public void mixedUnexploredLocateReferencesOnlyTheSelectedProvider() {
        BlockPos origin = BlockPos.ZERO;
        Pair<BlockPos, Holder<Structure>> irisNear = Pair.of(new BlockPos(4, 70, 0), null);
        Pair<BlockPos, Holder<Structure>> nativeFar = Pair.of(new BlockPos(8, 70, 0), null);
        AtomicInteger irisReferences = new AtomicInteger();
        AtomicInteger nativeReferences = new AtomicInteger();

        Pair<BlockPos, Holder<Structure>> irisSelected =
                NativeStructureLocateResults.selectAndReference(
                        origin,
                        irisNear, () -> irisReferences.incrementAndGet(),
                        nativeFar, () -> nativeReferences.incrementAndGet());

        assertSame(irisNear, irisSelected);
        assertEquals(1, irisReferences.get());
        assertEquals(0, nativeReferences.get());

        Pair<BlockPos, Holder<Structure>> nativeNear = Pair.of(new BlockPos(2, 70, 0), null);
        Pair<BlockPos, Holder<Structure>> nativeSelected =
                NativeStructureLocateResults.selectAndReference(
                        origin,
                        irisNear, () -> irisReferences.incrementAndGet(),
                        nativeNear, () -> nativeReferences.incrementAndGet());

        assertSame(nativeNear, nativeSelected);
        assertEquals(1, irisReferences.get());
        assertEquals(1, nativeReferences.get());
    }

    @Test
    public void nativePredictionIsReadOnlyUntilTheWinnerIsCommitted() throws IOException {
        Path nativegen = Path.of(System.getProperty("iris.nativeStructureFitBridgeSource")).getParent();
        String source = Files.readString(nativegen.resolve("NativeStructureVanillaLocator.java")).replace("\r\n", "\n");
        int predictionStart = source.indexOf("private static Candidate predictAt(");
        int candidateStart = source.indexOf("public static final class Candidate", predictionStart);
        String prediction = source.substring(predictionStart, candidateStart);
        String candidate = source.substring(candidateStart);

        assertFalse(prediction.contains("addReference("));
        assertTrue(candidate.contains("structureManager.addReference(referenceStart)"));
        assertTrue(candidate.contains("committed.compareAndSet(false, true)"));
    }

    @Test
    public void verticalPlacementMovesPiecesMonumentChildrenJigsawJunctionsAndCachedBoundsTogether() throws IOException {
        String source = Files.readString(
                Path.of(System.getProperty("iris.nativeStructureFitBridgeSource"))
                        .resolveSibling("NativeStructureVerticalPlacer.java")).replace("\r\n", "\n");
        int placementStart = source.indexOf("public static int applyVerticalPlacement");
        int shiftStart = source.indexOf("public static int applyVerticalShift", placementStart);
        int alignmentStart = source.indexOf("static int alignOceanMonumentToSeaLevel", shiftStart);
        int moveStart = source.indexOf("private static void moveStructureStart", alignmentStart);
        int movePieceStart = source.indexOf("private static void moveStructurePiece", moveStart);
        int monumentChildrenStart = source.indexOf("static List<StructurePiece> monumentChildPieces", movePieceStart);

        assertTrue(placementStart >= 0);
        assertTrue(shiftStart > placementStart);
        assertTrue(alignmentStart > shiftStart);
        assertTrue(moveStart > alignmentStart);
        assertTrue(movePieceStart > moveStart);
        assertTrue(monumentChildrenStart > movePieceStart);
        String placementMethod = source.substring(placementStart, shiftStart);
        String shiftMethod = source.substring(shiftStart, alignmentStart);
        String moveMethod = source.substring(moveStart, movePieceStart);
        String movePieceMethod = source.substring(movePieceStart, monumentChildrenStart);

        assertTrue(placementMethod.contains("return alignOceanMonumentToSeaLevel("));
        assertTrue(placementMethod.contains("return applyVerticalShift("));
        assertTrue(shiftMethod.contains("StructureVerticalBounds.clampOffset"));
        assertTrue(shiftMethod.contains("moveStructureStart(start, bounds, offsetY)"));
        assertTrue(moveMethod.contains("moveStructurePiece(piece, offsetY)"));
        assertTrue(moveMethod.contains("cachedBounds.move(0, offsetY, 0)"));
        assertTrue(movePieceMethod.contains("piece.move(0, offsetY, 0)"));
        assertTrue(movePieceMethod.contains("child.move(0, offsetY, 0)"));
        assertTrue(movePieceMethod.contains("junction.getSourceGroundY() + offsetY"));
    }
}
