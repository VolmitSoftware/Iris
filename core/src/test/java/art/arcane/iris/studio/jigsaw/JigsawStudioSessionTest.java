package art.arcane.iris.studio.jigsaw;

import org.junit.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class JigsawStudioSessionTest {
    private static final JigsawStudioCellDimensions CELL = new JigsawStudioCellDimensions(9, 6, 9);

    @Test
    public void initializesOneActiveVariantPerPopulatedWorkcell() {
        JigsawStudioVariant north = planarVariant("village/north", JigsawPlanarTopology.NORTH_END);
        JigsawStudioVariant corner = planarVariant("village/corner", JigsawPlanarTopology.NORTH_EAST_CORNER);
        JigsawStudioLayout layout = planarLayout(north, corner);
        JigsawStudioSession session = new JigsawStudioSession("overworld", "village", layout);

        assertSame(north, session.activeVariant("workcell/end").orElseThrow());
        assertSame(corner, session.activeVariant("workcell/corner").orElseThrow());
        assertTrue(session.activeVariant("workcell/blank").isEmpty());
        assertTrue(session.selectBay("workcell/end"));
        assertEquals("workcell/end", session.selectedBayId().orElseThrow());
        assertFalse(session.selectBay("topology/01"));
        assertFalse(session.isDirty());
    }

    @Test
    public void switchKeepsCommittedVariantUntilExpectedCompletion() {
        JigsawStudioVariant north = planarVariant("village/north", JigsawPlanarTopology.NORTH_END);
        JigsawStudioVariant east = planarVariant("village/east", JigsawPlanarTopology.EAST_END);
        JigsawStudioSession session = new JigsawStudioSession("overworld", "village", planarLayout(north, east));
        long initialLoad = session.workcellSnapshot("workcell/end").loadGeneration();

        assertEquals(
                JigsawStudioSession.DirtyStatus.MARKED,
                session.markWorkcellDirty("workcell/end").status());
        assertEquals(
                JigsawStudioSession.SwitchStatus.DIRTY,
                session.beginVariantSwitch("workcell/end", east.pieceKey(), false).status());
        JigsawStudioSession.SwitchStart start = session.beginVariantSwitch(
                "workcell/end", east.pieceKey(), true);
        JigsawStudioSession.VariantSwitchToken token = start.token().orElseThrow();

        assertEquals(JigsawStudioSession.SwitchStatus.STARTED, start.status());
        assertSame(north, token.previousVariant().orElseThrow());
        assertSame(east, token.targetVariant());
        assertEquals(initialLoad, token.loadGeneration());
        assertSame(north, session.activeVariant("workcell/end").orElseThrow());
        assertTrue(session.isVariantSwitchCurrent(token));
        assertEquals(
                JigsawStudioSession.DirtyStatus.SWITCH_IN_PROGRESS,
                session.markWorkcellDirty("workcell/end").status());
        assertTrue(session.completeVariantSwitch(token));
        assertSame(east, session.activeVariant("workcell/end").orElseThrow());
        assertTrue(session.workcellSnapshot("workcell/end").loadGeneration() > initialLoad);
        assertFalse(session.workcellSnapshot("workcell/end").dirty());
        assertFalse(session.isVariantSwitchCurrent(token));
    }

    @Test
    public void saveClearsDirtyOnlyWhenIdentityStillMatches() {
        JigsawStudioVariant end = planarVariant("village/end", JigsawPlanarTopology.NORTH_END);
        JigsawStudioSession session = new JigsawStudioSession("overworld", "village", planarLayout(end));
        JigsawStudioSession.DirtyMark initialMark = session.markWorkcellDirty("workcell/end");
        assertEquals(JigsawStudioSession.DirtyStatus.MARKED, initialMark.status());
        assertTrue(initialMark.newlyDirty());
        JigsawStudioSession.SaveIdentity first = session.beginSave("workcell/end").identity().orElseThrow();

        assertTrue(session.isSaveCurrent(first));
        JigsawStudioSession.DirtyMark laterMark = session.markWorkcellDirty("workcell/end");
        assertEquals(JigsawStudioSession.DirtyStatus.MARKED, laterMark.status());
        assertFalse(laterMark.newlyDirty());
        assertFalse(session.isSaveCurrent(first));
        assertFalse(session.markWorkcellSaved(first));
        assertTrue(session.workcellSnapshot("workcell/end").dirty());
        assertFalse(session.workcellSnapshot("workcell/end").saveInProgress());
        assertTrue(session.isDirtyCurrent(laterMark.identity().orElseThrow()));

        JigsawStudioSession.SaveIdentity second = session.beginSave("workcell/end").identity().orElseThrow();
        assertTrue(session.isSaveCurrent(second));
        assertTrue(session.markWorkcellSaved(second));
        assertFalse(session.workcellSnapshot("workcell/end").dirty());
        assertFalse(session.isDirty());
    }

    @Test
    public void dirtyIdentityAdvancesForEveryAcceptedEdit() {
        JigsawStudioVariant end = planarVariant("village/end", JigsawPlanarTopology.NORTH_END);
        JigsawStudioSession session = new JigsawStudioSession("overworld", "village", planarLayout(end));

        JigsawStudioSession.DirtyMark first = session.markWorkcellDirty("workcell/end");
        JigsawStudioSession.DirtyMark second = session.markWorkcellDirty("workcell/end");
        JigsawStudioSession.DirtyIdentity firstIdentity = first.identity().orElseThrow();
        JigsawStudioSession.DirtyIdentity secondIdentity = second.identity().orElseThrow();

        assertTrue(first.newlyDirty());
        assertFalse(second.newlyDirty());
        assertFalse(session.isDirtyCurrent(firstIdentity));
        assertTrue(session.isDirtyCurrent(secondIdentity));
        assertTrue(secondIdentity.mutationGeneration() > firstIdentity.mutationGeneration());

        JigsawStudioSession.SaveIdentity save = session.beginSave("workcell/end").identity().orElseThrow();
        assertTrue(session.markWorkcellSaved(save));
        assertFalse(session.isDirtyCurrent(secondIdentity));
    }

    @Test
    public void dirtyMarkReportsWhyAnEditCannotBeTracked() {
        JigsawStudioSession empty = new JigsawStudioSession("overworld", "village", planarLayout());

        assertEquals(
                JigsawStudioSession.DirtyStatus.UNKNOWN_WORKCELL,
                empty.markWorkcellDirty("workcell/missing").status());
        JigsawStudioSession.DirtyMark noVariant = empty.markWorkcellDirty("workcell/end");
        assertEquals(JigsawStudioSession.DirtyStatus.NO_ACTIVE_VARIANT, noVariant.status());
        assertTrue(noVariant.identity().isEmpty());
        assertFalse(noVariant.newlyDirty());
    }

    @Test
    public void saveAndSwitchReservationsExcludeEachOtherAndAbortSafely() {
        JigsawStudioVariant north = planarVariant("village/north", JigsawPlanarTopology.NORTH_END);
        JigsawStudioVariant east = planarVariant("village/east", JigsawPlanarTopology.EAST_END);
        JigsawStudioSession session = new JigsawStudioSession("overworld", "village", planarLayout(north, east));
        JigsawStudioSession.SaveIdentity save = session.beginSave("workcell/end").identity().orElseThrow();

        assertEquals(
                JigsawStudioSession.SwitchStatus.SAVE_IN_PROGRESS,
                session.beginVariantSwitch("workcell/end", east.pieceKey(), false).status());
        assertTrue(session.abortSave(save));
        JigsawStudioSession.VariantSwitchToken switchToken = session.beginVariantSwitch(
                "workcell/end", east.pieceKey(), false).token().orElseThrow();
        assertEquals(
                JigsawStudioSession.SaveStatus.SWITCH_IN_PROGRESS,
                session.beginSave("workcell/end").status());
        assertTrue(session.abortVariantSwitch(switchToken));
        assertSame(north, session.activeVariant("workcell/end").orElseThrow());
        assertFalse(session.isVariantSwitchCurrent(switchToken));
        assertFalse(session.completeVariantSwitch(switchToken));
    }

    @Test
    public void replacementOperationsRejectPreviouslyAbortedTokens() {
        JigsawStudioVariant north = planarVariant("village/north", JigsawPlanarTopology.NORTH_END);
        JigsawStudioVariant east = planarVariant("village/east", JigsawPlanarTopology.EAST_END);
        JigsawStudioSession session = new JigsawStudioSession("overworld", "village", planarLayout(north, east));
        JigsawStudioSession.VariantSwitchToken firstSwitch = session.beginVariantSwitch(
                "workcell/end", east.pieceKey(), false).token().orElseThrow();
        assertTrue(session.abortVariantSwitch(firstSwitch));
        JigsawStudioSession.VariantSwitchToken secondSwitch = session.beginVariantSwitch(
                "workcell/end", east.pieceKey(), false).token().orElseThrow();

        assertFalse(session.isVariantSwitchCurrent(firstSwitch));
        assertTrue(session.isVariantSwitchCurrent(secondSwitch));
        assertFalse(session.completeVariantSwitch(firstSwitch));
        assertTrue(session.abortVariantSwitch(secondSwitch));

        JigsawStudioSession.SaveIdentity firstSave = session.beginSave("workcell/end").identity().orElseThrow();
        assertTrue(session.abortSave(firstSave));
        JigsawStudioSession.SaveIdentity secondSave = session.beginSave("workcell/end").identity().orElseThrow();

        assertFalse(session.isSaveCurrent(firstSave));
        assertTrue(session.isSaveCurrent(secondSave));
        assertFalse(session.markWorkcellSaved(firstSave));
        assertTrue(session.abortSave(secondSave));
    }

    @Test
    public void replacingCatalogRetainsOrFallsBackWithoutMovingWorkcells() {
        JigsawStudioVariant north = planarVariant("village/north", JigsawPlanarTopology.NORTH_END);
        JigsawStudioVariant east = planarVariant("village/east", JigsawPlanarTopology.EAST_END);
        JigsawStudioSession session = new JigsawStudioSession("overworld", "village", planarLayout(north, east));
        JigsawStudioSession.VariantSwitchToken switchToken = session.beginVariantSwitch(
                "workcell/end", east.pieceKey(), false).token().orElseThrow();
        assertTrue(session.completeVariantSwitch(switchToken));
        long selectedLoad = session.workcellSnapshot("workcell/end").loadGeneration();
        assertFalse(session.workcellSnapshot("workcell/end").connectorsVisible());
        assertTrue(session.setConnectorsVisible("workcell/end", true));
        assertFalse(session.setConnectorsVisible("workcell/end", true));

        assertTrue(session.replaceLayout(planarLayout(north, east)));
        assertSame(east, session.activeVariant("workcell/end").orElseThrow());
        assertEquals(selectedLoad, session.workcellSnapshot("workcell/end").loadGeneration());
        assertTrue(session.workcellSnapshot("workcell/end").connectorsVisible());

        assertTrue(session.replaceLayout(planarLayout(north)));
        assertSame(north, session.activeVariant("workcell/end").orElseThrow());
        assertTrue(session.workcellSnapshot("workcell/end").loadGeneration() > selectedLoad);
        assertTrue(session.workcellSnapshot("workcell/end").connectorsVisible());
    }

    @Test
    public void replacingCatalogDoesNotMaterializeNewVariantsIntoPreviouslyEmptyWorkcells() {
        JigsawStudioSession session = new JigsawStudioSession(
                "overworld",
                "village",
                planarLayout());
        JigsawStudioVariant end = planarVariant("village/end", JigsawPlanarTopology.NORTH_END);

        assertTrue(session.replaceLayout(planarLayout(end)));

        assertTrue(session.activeVariant("workcell/end").isEmpty());
        assertEquals("", session.workcellSnapshot("workcell/end").activeVariantKey());
    }

    @Test
    public void coherentFamilyRebindIsAtomicAcrossEveryWorkcell() {
        JigsawStudioVariant endOne = planarVariant("village/end-one", JigsawPlanarTopology.NORTH_END);
        JigsawStudioVariant endTwo = planarVariant("village/end-two", JigsawPlanarTopology.EAST_END);
        JigsawStudioVariant cornerOne = planarVariant(
                "village/corner-one",
                JigsawPlanarTopology.NORTH_EAST_CORNER);
        JigsawStudioVariant cornerTwo = planarVariant(
                "village/corner-two",
                JigsawPlanarTopology.SOUTH_WEST_CORNER);
        JigsawStudioSession session = new JigsawStudioSession(
                "overworld",
                "village",
                planarLayout(endOne, cornerOne));
        JigsawStudioLayout familyLayout = planarLayout(endOne, endTwo, cornerOne, cornerTwo);

        assertTrue(session.replaceLayoutAndRebind(familyLayout, Map.of(
                "workcell/end", endTwo.pieceKey(),
                "workcell/corner", cornerTwo.pieceKey())));
        assertSame(endTwo, session.activeVariant("workcell/end").orElseThrow());
        assertSame(cornerTwo, session.activeVariant("workcell/corner").orElseThrow());

        assertThrows(IllegalArgumentException.class, () -> session.replaceLayoutAndRebind(
                familyLayout,
                Map.of(
                        "workcell/end", cornerOne.pieceKey(),
                        "workcell/corner", cornerTwo.pieceKey())));
        assertSame(endTwo, session.activeVariant("workcell/end").orElseThrow());
        assertSame(cornerTwo, session.activeVariant("workcell/corner").orElseThrow());
    }

    @Test
    public void activeVariantCanReserveAnExactLiveReloadAfterResize() {
        JigsawStudioVariant end = planarVariant("village/end", JigsawPlanarTopology.NORTH_END);
        JigsawStudioSession session = new JigsawStudioSession("overworld", "village", planarLayout(end));
        long originalLoad = session.workcellSnapshot("workcell/end").loadGeneration();

        JigsawStudioSession.SwitchStart reload = session.beginVariantReload("workcell/end");

        assertEquals(JigsawStudioSession.SwitchStatus.STARTED, reload.status());
        assertEquals(end.pieceKey(), reload.token().orElseThrow().targetVariant().pieceKey());
        assertTrue(session.completeVariantSwitch(reload.token().orElseThrow()));
        assertTrue(session.workcellSnapshot("workcell/end").loadGeneration() > originalLoad);
    }

    @Test
    public void reportsDirtyWorkcellsByStablePhysicalIdentity() {
        JigsawStudioVariant end = planarVariant("village/end", JigsawPlanarTopology.NORTH_END);
        JigsawStudioVariant corner = planarVariant("village/corner", JigsawPlanarTopology.NORTH_EAST_CORNER);
        JigsawStudioSession session = new JigsawStudioSession("overworld", "village", planarLayout(end, corner));

        assertEquals(
                JigsawStudioSession.DirtyStatus.MARKED,
                session.markWorkcellDirty("workcell/corner").status());
        assertEquals(
                JigsawStudioSession.DirtyStatus.MARKED,
                session.markWorkcellDirty("workcell/end").status());
        assertEquals(List.of("workcell/end", "workcell/corner"), session.dirtyWorkcellIds());
    }

    private static JigsawStudioLayout planarLayout(JigsawStudioVariant... variants) {
        return JigsawStudioLayout.create(
                JigsawStudioMode.PLANAR_JIGSAW,
                CELL,
                new JigsawStudioVariantCatalog(List.of(variants)));
    }

    private static JigsawStudioVariant planarVariant(String key, JigsawPlanarTopology topology) {
        return new JigsawStudioVariant(
                key,
                key,
                "",
                Optional.of(new JigsawStudioCellDimensions(16, 16, 16)),
                JigsawStudioMode.PLANAR_JIGSAW,
                Optional.of(topology),
                true,
                true,
                List.of(),
                new JigsawStudioPieceRules(0, 30, 0, 0, false),
                List.of(new JigsawStudioPoolMembership("village/start", 0, 1, 1D)));
    }
}
