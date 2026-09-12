package art.arcane.iris.studio.jigsaw;

import art.arcane.iris.pack.value.IrisPosition;
import org.junit.Test;

import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public class JigsawPlanarTopologyTest {
    @Test
    public void representsEveryFourDirectionMaskExactlyOnce() {
        Set<Integer> masks = new HashSet<>();
        Map<JigsawPlanarTopologyKind, Integer> kindCounts = new EnumMap<>(JigsawPlanarTopologyKind.class);

        for (JigsawPlanarTopology topology : JigsawPlanarTopology.values()) {
            assertTrue(masks.add(topology.mask()));
            assertSame(topology, JigsawPlanarTopology.fromMask(topology.mask()));
            kindCounts.merge(topology.kind(), 1, Integer::sum);
        }

        assertEquals(16, masks.size());
        assertEquals(Integer.valueOf(1), kindCounts.get(JigsawPlanarTopologyKind.BLANK));
        assertEquals(Integer.valueOf(4), kindCounts.get(JigsawPlanarTopologyKind.END));
        assertEquals(Integer.valueOf(2), kindCounts.get(JigsawPlanarTopologyKind.STRAIGHT));
        assertEquals(Integer.valueOf(4), kindCounts.get(JigsawPlanarTopologyKind.CORNER));
        assertEquals(Integer.valueOf(4), kindCounts.get(JigsawPlanarTopologyKind.TEE));
        assertEquals(Integer.valueOf(1), kindCounts.get(JigsawPlanarTopologyKind.CROSS));
    }

    @Test
    public void rotatesMasksAndDirectionsClockwise() {
        assertSame(
                JigsawPlanarTopology.EAST_SOUTH_CORNER,
                JigsawPlanarTopology.NORTH_EAST_CORNER.rotateClockwise(1)
        );
        assertSame(
                JigsawPlanarTopology.SOUTH_WEST_CORNER,
                JigsawPlanarTopology.NORTH_EAST_CORNER.rotateClockwise(2)
        );
        assertSame(
                JigsawPlanarTopology.NORTH_WEST_CORNER,
                JigsawPlanarTopology.NORTH_EAST_CORNER.rotateClockwise(-1)
        );
        assertSame(JigsawPlanarDirection.WEST, JigsawPlanarDirection.NORTH.rotateClockwise(-1));
        assertSame(JigsawPlanarDirection.SOUTH, JigsawPlanarDirection.NORTH.opposite());
    }

    @Test
    public void collapsesAllMasksIntoCanonicalWorkcellArchetypes() {
        int[] canonicalMasks = {0, 1, 1, 3, 1, 5, 3, 11, 1, 3, 5, 11, 3, 11, 11, 15};
        int[] sourceToCanonicalTurns = {0, 0, 3, 0, 2, 0, 3, 3, 1, 1, 1, 0, 2, 1, 2, 0};

        for (int mask = 0; mask < 16; mask++) {
            JigsawPlanarTopology source = JigsawPlanarTopology.fromMask(mask);
            JigsawPlanarArchetype archetype = JigsawPlanarArchetype.fromTopology(source);
            int sourceToCanonical = archetype.sourceToCanonicalQuarterTurns(source);

            assertEquals(canonicalMasks[mask], archetype.canonicalTopology().mask());
            assertEquals(sourceToCanonicalTurns[mask], sourceToCanonical);
            assertSame(archetype.canonicalTopology(), source.rotateClockwise(sourceToCanonical));
            assertSame(source, archetype.canonicalTopology().rotateClockwise(
                    archetype.canonicalToSourceQuarterTurns(source)));
        }
    }

    @Test
    public void inverseDisplayPositionRestoresRotatedSourceConnectorCoordinates() {
        JigsawStudioVariant east = new JigsawStudioVariant(
                "village/east",
                "village/east",
                "",
                Optional.of(new JigsawStudioCellDimensions(5, 3, 3)),
                JigsawStudioMode.PLANAR_JIGSAW,
                Optional.of(JigsawPlanarTopology.EAST_END),
                true,
                true,
                List.of(),
                new JigsawStudioPieceRules(0, 30, 0, 0, false),
                List.of());
        JigsawStudioCellDimensions sourceDimensions = new JigsawStudioCellDimensions(3, 3, 5);
        IrisPosition source = new IrisPosition(2, 1, 1);

        IrisPosition canonical = east.sourceToCanonicalPosition(source, sourceDimensions);
        IrisPosition restored = east.canonicalToSourcePosition(canonical, sourceDimensions);

        assertEquals(new JigsawStudioCellDimensions(5, 3, 3), east.canonicalDimensions(sourceDimensions));
        assertEquals(1, canonical.getX());
        assertEquals(1, canonical.getY());
        assertEquals(0, canonical.getZ());
        assertEquals(source.getX(), restored.getX());
        assertEquals(source.getY(), restored.getY());
        assertEquals(source.getZ(), restored.getZ());
    }
}
