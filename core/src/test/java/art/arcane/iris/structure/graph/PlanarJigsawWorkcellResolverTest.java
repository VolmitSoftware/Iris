package art.arcane.iris.structure.graph;

import art.arcane.iris.pack.value.IrisDirection;
import art.arcane.iris.structure.jigsaw.IrisJigsawConnector;
import art.arcane.iris.structure.jigsaw.IrisJigsawPiece;
import art.arcane.iris.structure.jigsaw.IrisJigsawWorkcell;
import art.arcane.iris.structure.jigsaw.IrisJigsawWorkcellArchetype;
import art.arcane.iris.structure.object.IrisObject;
import art.arcane.iris.pack.value.IrisPosition;
import art.arcane.iris.structure.placement.IrisStructure;
import org.junit.Test;

import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;

public class PlanarJigsawWorkcellResolverTest {
    @Test
    public void legacyCellSizeProducesSixEnabledRectangularWorkcells() {
        IrisStructure structure = new IrisStructure().setCellSize(new IrisPosition(12, 7, 18));

        Map<IrisJigsawWorkcellArchetype, PlanarJigsawWorkcellResolver.ResolvedWorkcell> workcells =
                PlanarJigsawWorkcellResolver.resolve(structure);

        assertEquals(6, workcells.size());
        for (PlanarJigsawWorkcellResolver.ResolvedWorkcell workcell : workcells.values()) {
            assertEquals(new IrisPosition(12, 7, 18), workcell.dimensions());
            assertEquals(true, workcell.enabled());
        }
    }

    @Test
    public void explicitWorkcellsRequireEveryUniqueArchetype() {
        IrisStructure structure = new IrisStructure();
        structure.getPlanarWorkcells().add(new IrisJigsawWorkcell(
                "",
                IrisJigsawWorkcellArchetype.BLANK,
                16,
                16,
                16,
                true));

        IllegalArgumentException missing = assertThrows(
                IllegalArgumentException.class,
                () -> PlanarJigsawWorkcellResolver.resolve(structure));
        assertEquals("Planar jigsaw workcell END is missing", missing.getMessage());

        structure.getPlanarWorkcells().add(new IrisJigsawWorkcell(
                "",
                IrisJigsawWorkcellArchetype.BLANK,
                8,
                8,
                8,
                false));
        assertThrows(IllegalArgumentException.class, () -> PlanarJigsawWorkcellResolver.resolve(structure));
    }

    @Test
    public void canonicalDimensionsSwapRectangularRotatedVariants() {
        IrisJigsawPiece eastEnd = new IrisJigsawPiece();
        eastEnd.getConnectors().add(new IrisJigsawConnector().setDirection(IrisDirection.EAST_POSITIVE_X));

        IrisPosition dimensions = PlanarJigsawWorkcellResolver.canonicalDimensions(
                eastEnd,
                new IrisObject(7, 5, 13));

        assertEquals(new IrisPosition(13, 5, 7), dimensions);
        assertEquals(IrisJigsawWorkcellArchetype.END,
                IrisJigsawWorkcellArchetype.fromPiece(eastEnd));
        assertFalse(IrisJigsawWorkcellArchetype.fromPiece(eastEnd)
                .sourceToCanonicalQuarterTurns(eastEnd) % 2 == 0);
    }
}
