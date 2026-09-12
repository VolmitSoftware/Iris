package art.arcane.iris.structure.graph;

import art.arcane.iris.structure.placement.PlacedStructurePiece;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;

public class StructureAssemblyResultTest {
    @Test
    public void completeResultCarriesImmutableOutputAndNormalizedTheme() {
        PlacedStructurePiece piece = mock(PlacedStructurePiece.class);
        StructureAssemblyResult result = StructureAssemblyResult.complete(
                List.of(piece),
                " spruce ");

        assertEquals(StructureAssemblyStatus.COMPLETE, result.status());
        assertEquals(List.of(piece), result.pieces());
        assertEquals("spruce", result.selectedTheme());
        assertEquals("", result.detail());
        assertTrue(result.status().isComplete());
        assertFalse(result.status().isFailure());
        assertTrue(result.hasOutput());
    }

    @Test
    public void intentionalEmptyIsCompleteWithoutOutput() {
        StructureAssemblyResult result = StructureAssemblyResult.intentionalEmpty(
                "desert",
                "No start membership passed its chance gate");

        assertEquals(StructureAssemblyStatus.INTENTIONAL_EMPTY, result.status());
        assertTrue(result.pieces().isEmpty());
        assertTrue(result.status().isComplete());
        assertFalse(result.hasOutput());
    }

    @Test
    public void failedResultRetainsPartialPiecesForDiagnostics() {
        PlacedStructurePiece piece = mock(PlacedStructurePiece.class);
        StructureAssemblyResult result = StructureAssemblyResult.failed(
                StructureAssemblyStatus.FAILED_UNCAPPED,
                List.of(piece),
                "spruce",
                "Direct fallback could not place a terminal cap");

        assertEquals(StructureAssemblyStatus.FAILED_UNCAPPED, result.status());
        assertEquals(List.of(piece), result.pieces());
        assertTrue(result.status().isFailure());
        assertFalse(result.hasOutput());
    }

    @Test(expected = IllegalArgumentException.class)
    public void completeResultRequiresOutput() {
        StructureAssemblyResult.complete(List.of(), "");
    }

    @Test(expected = IllegalArgumentException.class)
    public void failureRequiresDetail() {
        StructureAssemblyResult.failed(
                StructureAssemblyStatus.FAILED_RULES,
                List.of(),
                "",
                " ");
    }

    @Test(expected = IllegalArgumentException.class)
    public void failureFactoryRejectsCompleteStatus() {
        StructureAssemblyResult.failed(
                StructureAssemblyStatus.COMPLETE,
                List.of(),
                "",
                "invalid");
    }
}
