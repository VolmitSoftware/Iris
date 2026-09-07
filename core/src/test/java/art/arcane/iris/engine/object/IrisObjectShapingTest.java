package art.arcane.iris.engine.object;

import art.arcane.iris.spi.IrisPlatform;
import art.arcane.iris.spi.IrisPlatforms;
import art.arcane.iris.spi.PlatformBlockState;
import art.arcane.iris.spi.PlatformRegistries;
import art.arcane.iris.util.common.math.IrisBlockVector;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class IrisObjectShapingTest {
    @Before
    public void bindPlatform() {
        IrisPlatforms.unbind();
        PlatformRegistries registries = mock(PlatformRegistries.class);
        Map<String, PlatformBlockState> states = new HashMap<>();
        when(registries.block(anyString())).thenAnswer(invocation -> states.computeIfAbsent(
                invocation.getArgument(0), key -> mock(PlatformBlockState.class)));
        IrisPlatform platform = mock(IrisPlatform.class);
        when(platform.registries()).thenReturn(registries);
        IrisPlatforms.bind(platform);
    }

    @After
    public void unbindPlatform() {
        IrisPlatforms.unbind();
    }

    @Test
    public void smartBoreMatchesOrderedAxisClosureAndPreservesOriginalBlocks() {
        Random random = new Random(617431L);
        PlatformBlockState stone = mock(PlatformBlockState.class);
        for (int fixture = 0; fixture < 24; fixture++) {
            int offset = switch (fixture % 3) {
                case 0 -> -1050;
                case 1 -> 1020;
                default -> -3;
            };
            IrisObject object = new IrisObject(6, 6, 6);
            Map<Cell, PlatformBlockState> expected = new HashMap<>();
            for (int index = 0; index < 36; index++) {
                Cell cell = new Cell(offset + random.nextInt(6), offset + random.nextInt(6), offset + random.nextInt(6));
                PlatformBlockState state = switch (index % 3) {
                    case 0 -> IrisObject.States.air();
                    case 1 -> IrisObject.States.vair();
                    default -> stone;
                };
                object.getBlocks().put(cell.vector(), state);
                expected.put(cell, state);
            }
            for (int axis = 0; axis < 3; axis++) {
                closeAxis(expected, axis);
            }

            IrisObjectShaping.ensureSmartBored(object);

            assertTrue(object.isSmartBored());
            assertEquals("fixture " + fixture, expected.size(), object.getBlocks().size());
            for (Map.Entry<Cell, PlatformBlockState> entry : expected.entrySet()) {
                assertSame(entry.getValue(), object.getBlocks().get(entry.getKey().vector()));
            }
            long revision = object.getBlocks().modificationRevision();
            IrisObjectShaping.ensureSmartBored(object);
            assertEquals(revision, object.getBlocks().modificationRevision());
        }
    }

    @Test
    public void emptySmartBoreCompletesWithoutAddingBlocks() {
        IrisObject object = new IrisObject(1, 1, 1);

        IrisObjectShaping.ensureSmartBored(object);

        assertTrue(object.isSmartBored());
        assertTrue(object.getBlocks().isEmpty());
    }

    private static void closeAxis(Map<Cell, PlatformBlockState> blocks, int axis) {
        List<Cell> occupied = new ArrayList<>(blocks.keySet());
        for (Cell start : occupied) {
            for (Cell end : occupied) {
                if (!start.with(axis, 0).equals(end.with(axis, 0))) {
                    continue;
                }
                for (int position = start.coordinate(axis); position <= end.coordinate(axis); position++) {
                    blocks.putIfAbsent(start.with(axis, position), IrisObject.States.vair());
                }
            }
        }
    }

    private record Cell(int x, int y, int z) {
        private int coordinate(int axis) {
            return switch (axis) {
                case 0 -> x;
                case 1 -> y;
                default -> z;
            };
        }

        private Cell with(int axis, int coordinate) {
            return switch (axis) {
                case 0 -> new Cell(coordinate, y, z);
                case 1 -> new Cell(x, coordinate, z);
                default -> new Cell(x, y, coordinate);
            };
        }

        private IrisBlockVector vector() {
            return new IrisBlockVector(x, y, z);
        }
    }
}
