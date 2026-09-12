package art.arcane.iris.structure.jigsaw;

import art.arcane.iris.structure.placement.IrisStructure;

import art.arcane.iris.pack.schema.annotation.ArrayType;
import art.arcane.iris.pack.schema.annotation.MaxNumber;
import art.arcane.iris.pack.schema.annotation.MinNumber;
import com.google.gson.Gson;
import org.junit.Test;

import java.lang.reflect.Field;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class IrisJigsawRulesModelTest {
    @Test
    public void omittedMetadataPreservesUnthemedCertainOptionalDefaults() {
        Gson gson = new Gson();
        IrisJigsawPiece piece = gson.fromJson("{}", IrisJigsawPiece.class);
        IrisJigsawPieceEntry entry = gson.fromJson("{}", IrisJigsawPieceEntry.class);
        IrisJigsawPool pool = gson.fromJson("{}", IrisJigsawPool.class);
        IrisStructure structure = gson.fromJson("{}", IrisStructure.class);
        IrisJigsawPieceRules rules = piece.resolvedRules();

        assertTrue(piece.getThemes().isEmpty());
        assertEquals(0, rules.getMinimumDepth());
        assertEquals(30, rules.getMaximumDepth());
        assertEquals(0, rules.getMinimumPlacements());
        assertEquals(0, rules.getMaximumPlacements());
        assertFalse(rules.isTerminal());
        assertEquals(1D, entry.getChance(), 0D);
        assertFalse(entry.requiresChanceRoll());
        assertFalse(pool.isMandatoryFallback());
        assertFalse(pool.requiresFallback(false));
        assertTrue(structure.getThemeSets().isEmpty());
        assertFalse(structure.isRequireCaps());
    }

    @Test
    public void pieceThemesAreExactAndEmptyThemesAreUniversal() {
        IrisJigsawPiece universal = new IrisJigsawPiece();
        IrisJigsawPiece themed = new IrisJigsawPiece();
        themed.getThemes().add("spruce");
        themed.getThemes().add("desert");

        assertTrue(universal.supportsTheme("spruce"));
        assertTrue(universal.supportsTheme(""));
        assertTrue(themed.supportsTheme("spruce"));
        assertTrue(themed.supportsTheme("desert"));
        assertFalse(themed.supportsTheme("Spruce"));
        assertFalse(themed.supportsTheme(""));
        assertFalse(themed.supportsTheme(null));
    }

    @Test
    public void chanceSeparatesCertainImpossibleAndRolledMemberships() {
        IrisJigsawPieceEntry certain = new IrisJigsawPieceEntry().setChance(1D);
        IrisJigsawPieceEntry impossible = new IrisJigsawPieceEntry().setChance(0D);
        IrisJigsawPieceEntry rolled = new IrisJigsawPieceEntry().setChance(0.25D);

        assertFalse(certain.requiresChanceRoll());
        assertTrue(certain.passesChance(0.999D));
        assertFalse(impossible.requiresChanceRoll());
        assertFalse(impossible.passesChance(0D));
        assertTrue(rolled.requiresChanceRoll());
        assertTrue(rolled.passesChance(0.249D));
        assertFalse(rolled.passesChance(0.25D));
    }

    @Test(expected = IllegalArgumentException.class)
    public void chanceRejectsRollAtUpperBoundary() {
        new IrisJigsawPieceEntry().passesChance(1D);
    }

    @Test
    public void pieceRulesUseInclusiveDepthAndPrePlacementCounts() {
        IrisJigsawPieceRules rules = new IrisJigsawPieceRules()
                .setMinimumDepth(2)
                .setMaximumDepth(5)
                .setMinimumPlacements(2)
                .setMaximumPlacements(3)
                .setTerminal(true);

        assertFalse(rules.allowsDepth(1));
        assertTrue(rules.allowsDepth(2));
        assertTrue(rules.allowsDepth(5));
        assertFalse(rules.allowsDepth(6));
        assertTrue(rules.requiresMorePlacements(0));
        assertTrue(rules.requiresMorePlacements(1));
        assertFalse(rules.requiresMorePlacements(2));
        assertTrue(rules.allowsPlacement(2));
        assertFalse(rules.allowsPlacement(3));
        assertTrue(rules.isTerminal());
    }

    @Test
    public void zeroMaximumPlacementsIsUnboundedWithinRuntimeSafetyCap() {
        IrisJigsawPieceRules rules = new IrisJigsawPieceRules().setMaximumPlacements(0);

        assertTrue(rules.allowsPlacement(0));
        assertTrue(rules.allowsPlacement(512));
        assertFalse(rules.allowsPlacement(-1));
    }

    @Test
    public void structureAndPoolMetadataExposeSchemaBounds() throws ReflectiveOperationException {
        Field themeSets = IrisStructure.class.getDeclaredField("themeSets");
        Field themes = IrisJigsawPiece.class.getDeclaredField("themes");
        Field chance = IrisJigsawPieceEntry.class.getDeclaredField("chance");
        Field minimumPlacements = IrisJigsawPieceRules.class.getDeclaredField("minimumPlacements");
        Field maximumPlacements = IrisJigsawPieceRules.class.getDeclaredField("maximumPlacements");

        ArrayType themeSetArray = themeSets.getAnnotation(ArrayType.class);
        ArrayType themeArray = themes.getAnnotation(ArrayType.class);
        assertNotNull(themeSetArray);
        assertEquals(IrisJigsawThemeSet.class, themeSetArray.type());
        assertNotNull(themeArray);
        assertEquals(String.class, themeArray.type());
        assertEquals(0D, chance.getAnnotation(MinNumber.class).value(), 0D);
        assertEquals(1D, chance.getAnnotation(MaxNumber.class).value(), 0D);
        assertEquals(0D, minimumPlacements.getAnnotation(MinNumber.class).value(), 0D);
        assertEquals(512D, maximumPlacements.getAnnotation(MaxNumber.class).value(), 0D);
    }

    @Test
    public void mandatoryPoolFallbackCombinesWithStructurePolicy() {
        IrisJigsawPool optional = new IrisJigsawPool();
        IrisJigsawPool mandatory = new IrisJigsawPool().setMandatoryFallback(true);

        assertFalse(optional.requiresFallback(false));
        assertTrue(optional.requiresFallback(true));
        assertTrue(mandatory.requiresFallback(false));
        assertTrue(mandatory.requiresFallback(true));
    }

    @Test
    public void themeSetCarriesExactKeyAndPositiveDefaultWeight() {
        IrisJigsawThemeSet defaults = new IrisJigsawThemeSet();
        IrisJigsawThemeSet configured = new IrisJigsawThemeSet("desert", 3);

        assertEquals("", defaults.getKey());
        assertEquals(1, defaults.getWeight());
        assertEquals("desert", configured.getKey());
        assertEquals(3, configured.getWeight());
    }
}
