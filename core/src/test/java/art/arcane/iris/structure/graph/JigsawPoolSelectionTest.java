package art.arcane.iris.structure.graph;

import art.arcane.iris.structure.jigsaw.IrisJigsawPiece;
import art.arcane.iris.structure.jigsaw.IrisJigsawPieceEntry;
import art.arcane.iris.structure.jigsaw.IrisJigsawPieceRules;
import art.arcane.iris.structure.jigsaw.IrisJigsawThemeSet;
import art.arcane.iris.structure.placement.IrisStructure;
import art.arcane.volmlib.util.math.RNG;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class JigsawPoolSelectionTest {
    @Test
    public void omittedAndSingleThemesDoNotAdvanceLegacyRngState() {
        RNG omitted = new RNG(31L);
        RNG omittedBaseline = new RNG(31L);

        assertEquals("", JigsawPoolSelection.selectTheme(new IrisStructure(), omitted));
        assertEquals(omittedBaseline.nextLong(), omitted.nextLong());

        IrisStructure themed = new IrisStructure();
        themed.getThemeSets().add(new IrisJigsawThemeSet("variant-1", 1));
        RNG single = new RNG(47L);
        RNG singleBaseline = new RNG(47L);

        assertEquals("variant-1", JigsawPoolSelection.selectTheme(themed, single));
        assertEquals(singleBaseline.nextLong(), single.nextLong());
    }

    @Test
    public void defaultChanceDoesNotAdvanceLegacyRngState() {
        RNG actual = new RNG(53L);
        RNG baseline = new RNG(53L);

        assertTrue(JigsawPoolSelection.passesChance(new IrisJigsawPieceEntry(), actual));
        assertEquals(baseline.nextLong(), actual.nextLong());
    }

    @Test
    public void chanceAndPieceRulesFormOneEligibilityGate() {
        IrisJigsawPiece piece = new IrisJigsawPiece()
                .setRules(new IrisJigsawPieceRules()
                        .setMinimumDepth(2)
                        .setMaximumDepth(4)
                        .setMaximumPlacements(2));
        piece.getThemes().add("variant-1");

        assertFalse(JigsawPoolSelection.pieceEligible(piece, "variant-1", 1, 0));
        assertTrue(JigsawPoolSelection.pieceEligible(piece, "variant-1", 2, 0));
        assertTrue(JigsawPoolSelection.pieceEligible(piece, "variant-1", 4, 1));
        assertFalse(JigsawPoolSelection.pieceEligible(piece, "variant-1", 4, 2));
        assertFalse(JigsawPoolSelection.pieceEligible(piece, "variant-2", 2, 0));
    }
}
