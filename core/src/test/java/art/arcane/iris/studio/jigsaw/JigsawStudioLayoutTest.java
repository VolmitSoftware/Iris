package art.arcane.iris.studio.jigsaw;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class JigsawStudioLayoutTest {
    private static final JigsawStudioCellDimensions CELL = new JigsawStudioCellDimensions(9, 6, 9);

    @Test
    public void planarLayoutHasSixCanonicalWorkcellsInStableOrder() {
        JigsawStudioVariant northEnd = planarVariant("village/end", JigsawPlanarTopology.NORTH_END);
        JigsawStudioVariant eastEnd = planarVariant("village/east", JigsawPlanarTopology.EAST_END);
        JigsawStudioLayout layout = JigsawStudioLayout.create(
                JigsawStudioMode.PLANAR_JIGSAW,
                CELL,
                new JigsawStudioVariantCatalog(List.of(northEnd, eastEnd)));

        assertEquals(6, layout.bays().size());
        JigsawPlanarArchetype[] archetypes = JigsawPlanarArchetype.values();
        for (int index = 0; index < archetypes.length; index++) {
            JigsawStudioBay workcell = layout.bays().get(index);
            assertEquals(JigsawStudioBayKind.PLANAR_WORKCELL, workcell.kind());
            assertEquals(archetypes[index].stableId(), workcell.stableId());
            assertSame(archetypes[index], workcell.archetype().orElseThrow());
            assertSame(archetypes[index].canonicalTopology(), workcell.topology().orElseThrow());
            assertSame(workcell, layout.findAt(
                    workcell.bounds().originX(),
                    workcell.bounds().originY(),
                    workcell.bounds().originZ()));
        }
        assertEquals(List.of(northEnd, eastEnd), layout.variants(layout.get("workcell/end")));
        assertSame(northEnd, layout.defaultVariant(layout.get("workcell/end")).orElseThrow());
        assertTrue(layout.defaultVariant(layout.get("workcell/blank")).isEmpty());
    }

    @Test
    public void planarWorkcellsUseDirectOneBlockSpacing() {
        JigsawStudioLayout layout = JigsawStudioLayout.create(
                JigsawStudioMode.PLANAR_JIGSAW,
                new JigsawStudioCellDimensions(16, 8, 16),
                JigsawStudioVariantCatalog.empty());
        JigsawStudioBay blank = layout.get("workcell/blank");
        JigsawStudioBay end = layout.get("workcell/end");
        JigsawStudioBay corner = layout.get("workcell/corner");

        assertEquals(17, end.bounds().originX() - blank.bounds().originX());
        assertEquals(17, corner.bounds().originZ() - blank.bounds().originZ());
        assertEquals(1, end.bounds().originX() - blank.bounds().maxX() - 1);
        assertEquals(1, corner.bounds().originZ() - blank.bounds().maxZ() - 1);
        assertEquals(3, layout.columns());
        assertEquals(1, layout.gap());
    }

    @Test
    public void planarWorkcellsPackArbitraryPersistentBoundsWithoutOverlap() {
        List<JigsawStudioWorkcellSpec> specs = List.of(
                new JigsawStudioWorkcellSpec(JigsawPlanarArchetype.BLANK, "",
                        new JigsawStudioCellDimensions(8, 4, 9), true),
                new JigsawStudioWorkcellSpec(JigsawPlanarArchetype.END, "",
                        new JigsawStudioCellDimensions(20, 5, 7), true),
                new JigsawStudioWorkcellSpec(JigsawPlanarArchetype.STRAIGHT, "",
                        new JigsawStudioCellDimensions(6, 6, 11), true),
                new JigsawStudioWorkcellSpec(JigsawPlanarArchetype.CORNER, "",
                        new JigsawStudioCellDimensions(12, 7, 5), true),
                new JigsawStudioWorkcellSpec(JigsawPlanarArchetype.TEE, "",
                        new JigsawStudioCellDimensions(7, 8, 15), false),
                new JigsawStudioWorkcellSpec(JigsawPlanarArchetype.CROSS, "",
                        new JigsawStudioCellDimensions(10, 9, 6), true));
        JigsawStudioLayout layout = JigsawStudioLayout.createPlanar(
                CELL,
                specs,
                JigsawStudioVariantCatalog.empty());

        JigsawStudioBay blank = layout.get("workcell/blank");
        JigsawStudioBay end = layout.get("workcell/end");
        JigsawStudioBay straight = layout.get("workcell/straight");
        JigsawStudioBay corner = layout.get("workcell/corner");
        JigsawStudioBay tee = layout.get("workcell/tee");

        assertEquals(13, end.bounds().originX() - blank.bounds().originX());
        assertEquals(34, straight.bounds().originX() - blank.bounds().originX());
        assertEquals(12, corner.bounds().originZ() - blank.bounds().originZ());
        assertEquals(new JigsawStudioCellDimensions(7, 8, 15), tee.bounds().dimensions());
        assertFalse(tee.enabled());
        assertTrue(blank.enabled());
    }

    @Test
    public void spatialVariantsReceiveDedicatedAdjacentWorkcells() {
        JigsawStudioVariant hall = spatialVariant("stronghold/hall");
        JigsawStudioVariant stairs = spatialVariant("stronghold/stairs");
        JigsawStudioVariant tower = spatialVariant("stronghold/tower");
        JigsawStudioLayout layout = JigsawStudioLayout.create(
                JigsawStudioMode.SPATIAL_JIGSAW,
                CELL,
                new JigsawStudioVariantCatalog(List.of(hall, stairs, tower)));

        assertEquals(3, layout.bays().size());
        JigsawStudioBay workcell = layout.bays().getFirst();
        JigsawStudioBay second = layout.bays().get(1);
        JigsawStudioBay third = layout.bays().get(2);
        assertEquals(JigsawStudioLayout.SPATIAL_WORKCELL_ID, workcell.stableId());
        assertEquals(JigsawStudioBayKind.SPATIAL_WORKCELL, workcell.kind());
        assertTrue(workcell.archetype().isEmpty());
        assertTrue(workcell.topology().isEmpty());
        assertSame(hall, layout.defaultVariant(workcell).orElseThrow());
        assertSame(stairs, layout.defaultVariant(second).orElseThrow());
        assertSame(tower, layout.defaultVariant(third).orElseThrow());
        assertEquals(1, second.bounds().originX() - workcell.bounds().maxX() - 1);
        assertEquals(1, third.bounds().originX() - second.bounds().maxX() - 1);
        assertTrue(layout.accepts(second, stairs));
        assertFalse(layout.accepts(second, hall));
        assertSame(second, layout.workcellForVariant(stairs.pieceKey()).orElseThrow());
        assertNull(layout.findAt(-1, JigsawStudioLayout.FLOOR_Y + 1, -1));
    }

    @Test
    public void controlChestIsOutsideEveryCaptureAndCage() {
        JigsawStudioLayout layout = JigsawStudioLayout.create(
                JigsawStudioMode.PLANAR_JIGSAW,
                CELL,
                JigsawStudioVariantCatalog.empty());
        JigsawStudioControlPosition control = layout.controlPosition();

        for (JigsawStudioBay workcell : layout.bays()) {
            JigsawStudioBounds bounds = workcell.bounds();
            assertFalse(bounds.contains(control.worldX(), control.worldY(), control.worldZ()));
            assertTrue(control.worldX() < bounds.originX() - 1 || control.worldX() > bounds.maxX() + 1
                    || control.worldZ() < bounds.originZ() - 1 || control.worldZ() > bounds.maxZ() + 1);
        }
    }

    @Test
    public void rejectsPathologicalCellAndCatalogSizes() {
        assertThrows(IllegalArgumentException.class,
                () -> new JigsawStudioCellDimensions(129, 16, 16));
        assertThrows(IllegalArgumentException.class,
                () -> new JigsawStudioCellDimensions(128, 192, 128));

        List<JigsawStudioVariant> variants = new ArrayList<>();
        for (int index = 0; index <= JigsawStudioLayout.MAX_VARIANTS; index++) {
            variants.add(spatialVariant("stronghold/room-" + index));
        }
        assertThrows(IllegalArgumentException.class, () -> new JigsawStudioVariantCatalog(variants));
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
                List.of());
    }

    private static JigsawStudioVariant spatialVariant(String key) {
        return new JigsawStudioVariant(
                key,
                key,
                "",
                Optional.of(new JigsawStudioCellDimensions(16, 16, 16)),
                JigsawStudioMode.SPATIAL_JIGSAW,
                Optional.empty(),
                true,
                true,
                List.of(),
                new JigsawStudioPieceRules(0, 30, 0, 0, false),
                List.of());
    }
}
