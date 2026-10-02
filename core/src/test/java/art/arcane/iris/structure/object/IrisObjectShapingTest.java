package art.arcane.iris.structure.object;

import art.arcane.iris.spi.IrisPlatform;
import art.arcane.iris.spi.IrisPlatforms;
import art.arcane.volmlib.nativelib.terrain.NativeBlockState;
import art.arcane.iris.spi.PlatformRegistries;
import art.arcane.iris.testsupport.PlatformLeakGuard;
import art.arcane.iris.generation.geometry.IrisBlockVector;
import org.junit.After;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class IrisObjectShapingTest {
    @ClassRule
    public static final PlatformLeakGuard PLATFORM_GUARD = PlatformLeakGuard.clean();

    @Before
    public void bindPlatform() {
        IrisPlatforms.unbind();
        PlatformRegistries registries = mock(PlatformRegistries.class);
        Map<String, NativeBlockState> states = new HashMap<>();
        when(registries.block(anyString())).thenAnswer(invocation -> states.computeIfAbsent(
                invocation.getArgument(0), key -> mock(NativeBlockState.class)));
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
        NativeBlockState stone = mock(NativeBlockState.class);
        for (int fixture = 0; fixture < 24; fixture++) {
            int offset = switch (fixture % 3) {
                case 0 -> -1050;
                case 1 -> 1020;
                default -> -3;
            };
            IrisObject object = new IrisObject(6, 6, 6);
            Map<Cell, NativeBlockState> expected = new HashMap<>();
            for (int index = 0; index < 36; index++) {
                Cell cell = new Cell(offset + random.nextInt(6), offset + random.nextInt(6), offset + random.nextInt(6));
                NativeBlockState state = switch (index % 3) {
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
            for (Map.Entry<Cell, NativeBlockState> entry : expected.entrySet()) {
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

    @Test
    public void smartBoreVariantIsReusedAndInvalidatedBySourceEdits() {
        IrisObject source = new IrisObject(3, 1, 1);
        source.getBlocks().put(new IrisBlockVector(-1, 0, 0), IrisObject.States.stone());
        source.getBlocks().put(new IrisBlockVector(1, 0, 0), IrisObject.States.stone());
        long sourceRevision = source.getBlocks().modificationRevision();

        IrisObject first = IrisObjectShaping.smartBoredVariant(source);

        assertFalse(source.isSmartBored());
        assertEquals(sourceRevision, source.getBlocks().modificationRevision());
        assertEquals(2, source.getBlocks().size());
        assertEquals(3, first.getBlocks().size());
        assertSame(first, IrisObjectShaping.smartBoredVariant(source));
        source.getBlocks().put(new IrisBlockVector(0, 0, 0), IrisObject.States.air());
        IrisObject second = IrisObjectShaping.smartBoredVariant(source);
        assertNotSame(first, second);
        assertSame(IrisObject.States.air(), second.getBlocks().get(new IrisBlockVector(0, 0, 0)));
        assertSame(second, IrisObjectShaping.smartBoredVariant(source));
    }

    private static void closeAxis(Map<Cell, NativeBlockState> blocks, int axis) {
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
