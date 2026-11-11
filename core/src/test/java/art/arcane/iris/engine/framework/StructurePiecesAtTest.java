package art.arcane.iris.engine.framework;

import art.arcane.iris.builtin.structure.StructureDistribution;
import art.arcane.iris.builtin.structure.StructurePlacement;
import art.arcane.iris.core.loader.IrisData;
import art.arcane.iris.core.modules.ModuleResourceFixture;
import art.arcane.iris.engine.framework.structure.JigsawStructureAssembly;
import art.arcane.iris.engine.framework.structure.StructureResources;
import art.arcane.iris.engine.framework.structure.assembly.AssembledPiece;
import art.arcane.iris.engine.mantle.components.StructureModule;
import art.arcane.iris.engine.mantle.components.StructurePiecesAt;
import art.arcane.iris.engine.object.TestSettings;
import art.arcane.iris.engine.object.structure.IrisStructure;
import art.arcane.iris.engine.object.structure.jigsaw.IrisJigsawPiece;
import art.arcane.iris.engine.object.structure.jigsaw.IrisJigsawPieceEntry;
import art.arcane.iris.engine.object.structure.jigsaw.IrisJigsawPool;
import art.arcane.iris.modules.jigsaw.JigsawModule;
import com.volmit.host.ModuleLogs;
import com.volmit.object.PlaceableObject;
import com.volmit.placement.PlacementRecord;
import org.junit.Test;

import java.util.Optional;

import static art.arcane.iris.engine.framework.StructurePlacementFixtures.withAdded;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** The structure piece iris:structure plans at a block, for block inspection where generation recorded nothing. */
public class StructurePiecesAtTest {
    private static final String STRUCTURE = "test:ruin";
    private static final int CHUNKS_SEARCHED = 64;

    @Test
    public void theBlockOfAPlannedPieceNamesThePieceAndItsStructure() {
        StructureTestWorld world = worldWithStructures();
        AssembledPiece piece = firstStartedPiece(world);

        Optional<PlacementRecord> planned = new StructurePiecesAt().placementAt(
                world, piece.minX(), heightAboveBottom(world, piece.minY()), piece.minZ());

        assertTrue(planned.isPresent());
        assertEquals(Optional.of(world.resources().idOf(STRUCTURE)), planned.get().structure());
        assertEquals(piece.object(), planned.get().object());
    }

    @Test
    public void aBlockNoPieceCoversHasNoPlannedPlacement() {
        StructureTestWorld world = worldWithStructures();
        AssembledPiece piece = firstStartedPiece(world);
        int besideXInTheSameChunk = (piece.minX() & ~15) | ((piece.minX() + 8) & 15);

        assertFalse(new StructurePiecesAt().placementAt(
                world, besideXInTheSameChunk, heightAboveBottom(world, piece.minY()), piece.minZ()).isPresent());
    }

    private static int heightAboveBottom(StructureTestWorld world, int worldY) {
        return worldY - world.minWorldY();
    }

    /** The piece of the first structure that starts in a chunk along the X axis from chunk 0, 0. */
    private static AssembledPiece firstStartedPiece(StructureTestWorld world) {
        StructurePlacement placement = world.dimensionPlacements().get(0);
        for (int chunkX = 0; chunkX < CHUNKS_SEARCHED; chunkX++) {
            IrisStructureLocator.ResolvedPlacement resolved = IrisStructureLocator.resolvePlacement(world, placement, chunkX, 0);
            if (resolved != null) {
                return resolved.pieces().getFirst();
            }
        }
        throw new AssertionError("No structure starts within " + CHUNKS_SEARCHED + " chunks");
    }

    /** A world whose one placement starts structures of a single 1x1x1 piece at the highest density. */
    private static StructureTestWorld worldWithStructures() {
        IrisData data = mock(IrisData.class);
        ModuleResourceFixture.serveModules(data, new JigsawModule(), new StructureModule());
        IrisStructure structure = new IrisStructure();
        ModuleResourceFixture.of(data).resource(StructureResources.STRUCTURES, STRUCTURE, structure);
        registerSinglePieceGraph(data, structure);

        StructurePlacement placement = new StructurePlacement();
        withAdded(placement, "structures", STRUCTURE);
        TestSettings.with(placement, "distribution", StructureDistribution.DENSITY);
        TestSettings.with(placement, "density", 1D);
        TestSettings.with(placement, "minHeight", -2032);
        TestSettings.with(placement, "maxHeight", 2032);

        return new StructureTestWorld()
                .resources(data.getPackResources())
                .assembling(new JigsawStructureAssembly(ModuleLogs.discarding()))
                .highest((x, z) -> 128)
                .fluidHeight(63)
                .placementSeed(new SeedManager(77L).getMantle())
                .minY(-64)
                .height(384)
                .dimension(placement);
    }

    private static void registerSinglePieceGraph(IrisData data, IrisStructure structure) {
        String poolKey = STRUCTURE + "/start";
        String pieceKey = STRUCTURE + "/piece";
        String objectKey = STRUCTURE + "/object";
        structure.setStartPool(poolKey);
        IrisJigsawPool pool = new IrisJigsawPool();
        pool.getPieces().add(new IrisJigsawPieceEntry().setPiece(pieceKey).setWeight(1));
        IrisJigsawPiece piece = new IrisJigsawPiece();
        piece.setObject(objectKey);
        ModuleResourceFixture.of(data).object(objectKey, 1, 1, 1);
        PlaceableObject object = mock(PlaceableObject.class);
        when(object.name()).thenReturn(objectKey);
        when(data.getPackResources().objects().object(data.getPackResources().idOf(objectKey))).thenReturn(Optional.of(object));
        ModuleResourceFixture.of(data).resource(StructureResources.POOLS, poolKey, pool);
        ModuleResourceFixture.of(data).resource(StructureResources.PIECES, pieceKey, piece);
    }
}
