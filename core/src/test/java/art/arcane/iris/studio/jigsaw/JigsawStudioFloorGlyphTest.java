package art.arcane.iris.studio.jigsaw;

import art.arcane.iris.pack.value.IrisDirection;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;

public class JigsawStudioFloorGlyphTest {
    @Test
    public void emptyConnectorListPaintsOnlyTheBase() {
        JigsawStudioFloorGlyph glyph = JigsawStudioFloorGlyph.of(5, 5, List.of());

        assertRows(glyph, 5, 5, List.of(".....", ".....", ".....", ".....", "....."));
    }

    @Test
    public void canonicalPlanarSocketsDrawStraightLinesFromTheCenter() {
        Map<JigsawPlanarArchetype, List<String>> expected = Map.of(
                JigsawPlanarArchetype.END, List.of("..o..", "..#..", "..#..", ".....", "....."),
                JigsawPlanarArchetype.STRAIGHT, List.of("..o..", "..#..", "..#..", "..#..", "..o.."),
                JigsawPlanarArchetype.CORNER, List.of("..o..", "..#..", "..##o", ".....", "....."),
                JigsawPlanarArchetype.TEE, List.of("..o..", "..#..", "o###o", ".....", "....."),
                JigsawPlanarArchetype.CROSS, List.of("..o..", "..#..", "o###o", "..#..", "..o.."));

        for (Map.Entry<JigsawPlanarArchetype, List<String>> entry : expected.entrySet()) {
            List<JigsawStudioFloorGlyph.Marker> markers = new ArrayList<>();
            for (JigsawPlanarDirection direction : entry.getKey().canonicalTopology().directions()) {
                markers.add(canonicalMarker(5, 5, direction.irisDirection()));
            }
            assertRows(JigsawStudioFloorGlyph.of(5, 5, markers), 5, 5, entry.getValue());
        }
    }

    @Test
    public void evenFootprintsUseFloorDivisionForTheCenter() {
        JigsawStudioFloorGlyph glyph = JigsawStudioFloorGlyph.of(4, 4, List.of(
                canonicalMarker(4, 4, IrisDirection.NORTH_NEGATIVE_Z),
                canonicalMarker(4, 4, IrisDirection.WEST_NEGATIVE_X)));

        assertRows(glyph, 4, 4, List.of("..o.", "..#.", "o##.", "...."));
    }

    @Test
    public void verticalConnectorsUseDistinctSocketsAndMergeOnOneColumn() {
        JigsawStudioFloorGlyph up = JigsawStudioFloorGlyph.of(5, 5, List.of(
                new JigsawStudioFloorGlyph.Marker(2, 2, IrisDirection.UP_POSITIVE_Y)));
        JigsawStudioFloorGlyph down = JigsawStudioFloorGlyph.of(5, 5, List.of(
                new JigsawStudioFloorGlyph.Marker(2, 2, IrisDirection.DOWN_NEGATIVE_Y)));
        JigsawStudioFloorGlyph both = JigsawStudioFloorGlyph.of(5, 5, List.of(
                new JigsawStudioFloorGlyph.Marker(2, 2, IrisDirection.UP_POSITIVE_Y),
                new JigsawStudioFloorGlyph.Marker(2, 2, IrisDirection.DOWN_NEGATIVE_Y),
                canonicalMarker(5, 5, IrisDirection.EAST_POSITIVE_X)));

        assertEquals(JigsawStudioFloorGlyph.Cell.UP_SOCKET, up.cell(2, 2));
        assertEquals(JigsawStudioFloorGlyph.Cell.DOWN_SOCKET, down.cell(2, 2));
        assertRows(both, 5, 5, List.of(".....", ".....", "..B#o", ".....", "....."));
    }

    @Test
    public void offCenterVerticalConnectorsGetALineToTheirColumn() {
        JigsawStudioFloorGlyph glyph = JigsawStudioFloorGlyph.of(5, 5, List.of(
                new JigsawStudioFloorGlyph.Marker(0, 2, IrisDirection.DOWN_NEGATIVE_Y)));

        assertRows(glyph, 5, 5, List.of(".....", ".....", "v##..", ".....", "....."));
    }

    @Test
    public void interiorHorizontalConnectorsExtendTheirLineOutOfTheFaceTheyOpen() {
        JigsawStudioFloorGlyph glyph = JigsawStudioFloorGlyph.of(5, 5, List.of(
                new JigsawStudioFloorGlyph.Marker(3, 2, IrisDirection.SOUTH_POSITIVE_Z)));

        assertRows(glyph, 5, 5, List.of(".....", ".....", "..#o.", "...#.", "...#."));
    }

    @Test
    public void diagonalLinesStayConnectedAndInsideTheFootprint() {
        JigsawStudioFloorGlyph glyph = JigsawStudioFloorGlyph.of(7, 5, List.of(
                new JigsawStudioFloorGlyph.Marker(6, 0, IrisDirection.EAST_POSITIVE_X)));

        assertEquals(JigsawStudioFloorGlyph.Cell.HORIZONTAL_SOCKET, glyph.cell(6, 0));
        assertEquals(JigsawStudioFloorGlyph.Cell.LINE, glyph.cell(3, 2));
        int lineCells = 0;
        for (int z = 0; z < 5; z++) {
            for (int x = 0; x < 7; x++) {
                if (glyph.cell(x, z) == JigsawStudioFloorGlyph.Cell.LINE) {
                    lineCells++;
                }
            }
        }
        assertEquals(3, lineCells);
        assertEquals(JigsawStudioFloorGlyph.Cell.BASE, glyph.cell(-1, 0));
        assertEquals(JigsawStudioFloorGlyph.Cell.BASE, glyph.cell(7, 0));
    }

    @Test
    public void markersOutsideTheFootprintAreClampedToItsEdge() {
        JigsawStudioFloorGlyph glyph = JigsawStudioFloorGlyph.of(3, 3, List.of(
                new JigsawStudioFloorGlyph.Marker(9, 1, IrisDirection.EAST_POSITIVE_X)));

        assertRows(glyph, 3, 3, List.of("...", ".#o", "..."));
    }

    private static JigsawStudioFloorGlyph.Marker canonicalMarker(int width, int depth, IrisDirection direction) {
        int centerX = width / 2;
        int centerZ = depth / 2;
        return switch (direction) {
            case NORTH_NEGATIVE_Z -> new JigsawStudioFloorGlyph.Marker(centerX, 0, direction);
            case SOUTH_POSITIVE_Z -> new JigsawStudioFloorGlyph.Marker(centerX, depth - 1, direction);
            case EAST_POSITIVE_X -> new JigsawStudioFloorGlyph.Marker(width - 1, centerZ, direction);
            case WEST_NEGATIVE_X -> new JigsawStudioFloorGlyph.Marker(0, centerZ, direction);
            case UP_POSITIVE_Y, DOWN_NEGATIVE_Y -> new JigsawStudioFloorGlyph.Marker(centerX, centerZ, direction);
        };
    }

    private static void assertRows(JigsawStudioFloorGlyph glyph, int width, int depth, List<String> rows) {
        for (int z = 0; z < depth; z++) {
            StringBuilder actual = new StringBuilder(width);
            for (int x = 0; x < width; x++) {
                actual.append(symbol(glyph.cell(x, z)));
            }
            assertEquals("row " + z, rows.get(z), actual.toString());
        }
    }

    private static char symbol(JigsawStudioFloorGlyph.Cell cell) {
        return switch (cell) {
            case BASE -> '.';
            case LINE -> '#';
            case HORIZONTAL_SOCKET -> 'o';
            case UP_SOCKET -> '^';
            case DOWN_SOCKET -> 'v';
            case UP_AND_DOWN_SOCKET -> 'B';
        };
    }
}
