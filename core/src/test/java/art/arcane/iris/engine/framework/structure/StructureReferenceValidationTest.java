package art.arcane.iris.engine.framework.structure;

import art.arcane.iris.engine.object.structure.jigsaw.IrisJigsawConnector;
import art.arcane.iris.engine.object.structure.jigsaw.IrisJigsawPiece;
import com.volmit.block.BlockRegistry;
import com.volmit.block.BlockState;
import com.volmit.resource.PackResources;
import com.volmit.resource.ResourceId;
import com.volmit.validation.Problem;
import org.junit.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class StructureReferenceValidationTest {
    private static final String KNOWN_BLOCK = "minecraft:structure_void";
    private static final String UNKNOWN_BLOCK = "example:not_a_block";
    private static final ResourceId PIECE = new ResourceId("test", "castle/keep");
    private static final ResourceId OBJECT = new ResourceId("test", "castle/keep");

    @Test
    public void aConnectorWhoseFinalStateIsNoBlockOfThePlatformIsAnError() {
        List<Problem> problems = StructureReferenceValidation.validate(
                packWithAPieceWhoseConnectorBecomes(UNKNOWN_BLOCK), Optional.of(blocksKnowing(KNOWN_BLOCK)));

        assertEquals(problems.toString(), 1, problems.size());
        assertTrue(problems.get(0).message(), problems.get(0).message().contains(
                "Jigsaw piece 'castle/keep' connectors[0] finalState '" + UNKNOWN_BLOCK + "' is not a block."));
    }

    @Test
    public void aConnectorWhoseFinalStateIsABlockOfThePlatformIsAccepted() {
        List<Problem> problems = StructureReferenceValidation.validate(
                packWithAPieceWhoseConnectorBecomes(KNOWN_BLOCK), Optional.of(blocksKnowing(KNOWN_BLOCK)));

        assertTrue(problems.toString(), problems.isEmpty());
    }

    @Test
    public void withoutAPlatformNoFinalStateIsChecked() {
        List<Problem> problems = StructureReferenceValidation.validate(
                packWithAPieceWhoseConnectorBecomes(UNKNOWN_BLOCK), Optional.empty());

        assertTrue(problems.toString(), problems.isEmpty());
    }

    private static BlockRegistry blocksKnowing(String known) {
        BlockRegistry blocks = mock(BlockRegistry.class);
        when(blocks.find(known)).thenReturn(Optional.of(BlockState.parse(known)));
        when(blocks.find(UNKNOWN_BLOCK)).thenReturn(Optional.empty());
        return blocks;
    }

    private static PackResources packWithAPieceWhoseConnectorBecomes(String finalState) {
        PackResources pack = mock(PackResources.class);
        IrisJigsawPiece piece = new IrisJigsawPiece().setObject("castle/keep")
                .setConnectors(List.of(new IrisJigsawConnector().setFinalState(finalState)));
        when(pack.ids(StructureResources.STRUCTURES)).thenReturn(List.of());
        when(pack.ids(StructureResources.POOLS)).thenReturn(List.of());
        when(pack.ids(StructureResources.PIECES)).thenReturn(List.of(PIECE));
        when(pack.ids(PackResources.OBJECTS)).thenReturn(List.of(OBJECT));
        when(pack.find(StructureResources.PIECES, PIECE)).thenReturn(Optional.of(piece));
        return pack;
    }
}
