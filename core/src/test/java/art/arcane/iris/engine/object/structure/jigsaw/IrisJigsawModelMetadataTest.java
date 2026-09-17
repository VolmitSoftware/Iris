package art.arcane.iris.engine.object.structure.jigsaw;

import art.arcane.iris.engine.object.structure.IrisStructure;
import art.arcane.iris.engine.object.structure.jigsaw.IrisJigsawBranchFailurePolicy;
import art.arcane.iris.engine.object.structure.jigsaw.IrisJigsawCompatibility;
import art.arcane.iris.engine.object.structure.jigsaw.IrisJigsawConnector;
import art.arcane.iris.engine.object.structure.jigsaw.IrisJigsawMode;
import art.arcane.iris.engine.object.structure.jigsaw.IrisJigsawPiece;
import art.arcane.iris.engine.object.value.IrisPosition;
import com.google.gson.Gson;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class IrisJigsawModelMetadataTest {
    @Test
    public void defaultsPreserveSpatialExtendedGraphsAndVanillaConnectorMetadata() {
        IrisStructure structure = new IrisStructure();
        IrisJigsawConnector connector = new IrisJigsawConnector();
        IrisJigsawPiece piece = new IrisJigsawPiece();

        assertEquals(IrisJigsawMode.SPATIAL_JIGSAW, structure.resolvedMode());
        assertEquals(IrisJigsawCompatibility.IRIS_EXTENDED, structure.resolvedCompatibility());
        assertEquals(IrisJigsawBranchFailurePolicy.FAIL_ASSEMBLY,
                structure.resolvedBranchFailurePolicy());
        assertEquals(new IrisPosition(15, 15, 15), structure.getCellSize());
        assertEquals("", connector.getChannel());
        assertEquals("minecraft:air", connector.getFinalState());
        assertEquals(0, connector.getSelectionPriority());
        assertEquals(0, connector.getPlacementPriority());
        assertTrue(piece.isCollidable());
    }

    @Test
    public void nullTopologyMetadataResolvesToBackwardCompatibleDefaults() {
        IrisStructure structure = new IrisStructure()
                .setMode(null)
                .setCompatibility(null)
                .setBranchFailurePolicy(null);

        assertEquals(IrisJigsawMode.SPATIAL_JIGSAW, structure.resolvedMode());
        assertEquals(IrisJigsawCompatibility.IRIS_EXTENDED, structure.resolvedCompatibility());
        assertEquals(IrisJigsawBranchFailurePolicy.FAIL_ASSEMBLY,
                structure.resolvedBranchFailurePolicy());
    }

    @Test
    public void omittedJsonMetadataUsesBackwardCompatibleDefaults() {
        Gson gson = new Gson();
        IrisStructure structure = gson.fromJson("{}", IrisStructure.class);
        IrisJigsawConnector connector = gson.fromJson("{}", IrisJigsawConnector.class);
        IrisJigsawPiece piece = gson.fromJson("{}", IrisJigsawPiece.class);

        assertEquals(IrisJigsawMode.SPATIAL_JIGSAW, structure.resolvedMode());
        assertEquals(IrisJigsawCompatibility.IRIS_EXTENDED, structure.resolvedCompatibility());
        assertEquals(IrisJigsawBranchFailurePolicy.FAIL_ASSEMBLY,
                structure.resolvedBranchFailurePolicy());
        assertEquals(new IrisPosition(15, 15, 15), structure.getCellSize());
        assertEquals("", connector.getChannel());
        assertEquals("minecraft:air", connector.getFinalState());
        assertEquals(0, connector.getSelectionPriority());
        assertEquals(0, connector.getPlacementPriority());
        assertTrue(piece.isCollidable());
    }

    @Test
    public void explicitBranchTerminationPolicySurvivesJsonLoading() {
        IrisStructure structure = new Gson().fromJson(
                "{\"branchFailurePolicy\":\"TERMINATE_BRANCH\"}",
                IrisStructure.class
        );

        assertEquals(IrisJigsawBranchFailurePolicy.TERMINATE_BRANCH,
                structure.resolvedBranchFailurePolicy());
    }

    @Test
    public void connectorPrioritiesPreserveSignedVanillaValues() {
        Gson gson = new Gson();
        IrisJigsawConnector connector = gson.fromJson(
                "{\"selectionPriority\":-17,\"placementPriority\":-29}",
                IrisJigsawConnector.class
        );

        assertEquals(-17, connector.getSelectionPriority());
        assertEquals(-29, connector.getPlacementPriority());
    }

    @Test
    public void explicitNonCollidableMetadataSurvivesJsonLoading() {
        IrisJigsawPiece piece = new Gson().fromJson(
                "{\"collidable\":false}",
                IrisJigsawPiece.class
        );

        assertFalse(piece.isCollidable());
    }
}
