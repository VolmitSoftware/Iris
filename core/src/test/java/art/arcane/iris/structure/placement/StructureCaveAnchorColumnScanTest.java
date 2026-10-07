package art.arcane.iris.structure.placement;

import art.arcane.iris.generation.hydrology.cave.HydrologyCaveAction;
import art.arcane.iris.generation.hydrology.cave.HydrologyCaveCell;
import art.arcane.iris.generation.hydrology.cave.HydrologyCaveStorage;
import art.arcane.iris.generation.runtime.Engine;
import art.arcane.iris.generation.mantle.EngineMantle;
import art.arcane.iris.generation.terrain.IrisDimension;
import art.arcane.iris.testsupport.PlatformBinding;
import art.arcane.volmlib.util.mantle.runtime.Mantle;
import art.arcane.volmlib.util.math.RNG;
import art.arcane.volmlib.util.matter.MatterCavern;
import art.arcane.volmlib.util.matter.Matter;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.MockedStatic;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

public class StructureCaveAnchorColumnScanTest {
    private static final int HEIGHT = 384;
    private static final int WORLD_MIN = -64;

    @Rule
    public final PlatformBinding platform = PlatformBinding.mockPlatform();

    @Test
    public void columnMatchesIndependentRunReferenceAcrossModesAndScanSteps() {
        MatterCavern[] baseline = new MatterCavern[HEIGHT];
        HydrologyCaveCell[] hydrology = new HydrologyCaveCell[HEIGHT];
        RNG rng = new RNG(583764L);
        for (int y = 0; y < HEIGHT; y++) {
            if (rng.nextInt(6) != 0 || y >= 40 && y <= 125 || y >= 180 && y <= 280) {
                baseline[y] = new MatterCavern(true, "", (byte) (y % 31 == 0 ? 1 : 0));
            }
        }
        hydrology[55] = HydrologyCaveCell.of(HydrologyCaveAction.WET_SOURCE);
        hydrology[56] = HydrologyCaveCell.of(HydrologyCaveAction.SEAL_GUARD);
        hydrology[200] = HydrologyCaveCell.of(HydrologyCaveAction.DRY_AIR);
        Engine engine = fixture(baseline);
        try (MockedStatic<HydrologyCaveStorage> storage = mockStatic(HydrologyCaveStorage.class)) {
            storage.when(() -> HydrologyCaveStorage.getIfPresent(any(Mantle.class), anyInt(), anyInt(), anyInt()))
                    .thenAnswer(invocation -> hydrology[(int) invocation.getArgument(2)]);
            for (IrisStructureAnchorMode mode : IrisStructureAnchorMode.values()) {
                if (!mode.isCave()) {
                    continue;
                }
                for (int step : new int[]{1, 2, 3, 7, 16}) {
                    for (int clearance : new int[]{1, 2, 3, 16, 64}) {
                        for (boolean underwater : new boolean[]{false, true}) {
                            for (int minimum : new int[]{1, 71, 129}) {
                                IrisStructurePlacement placement = new IrisStructurePlacement().setUnderwater(underwater);
                                List<Integer> actual = StructureCaveAnchorResolver.anchorsInColumn(
                                        engine, placement, mode, -47, -103, minimum + WORLD_MIN,
                                        382 + WORLD_MIN, step, clearance);
                                assertEquals(mode + " step=" + step + " clearance=" + clearance
                                                + " underwater=" + underwater + " minimum=" + minimum,
                                        reference(baseline, hydrology, mode, minimum, 382, step, clearance, underwater), actual);
                                storage.clearInvocations();
                            }
                        }
                    }
                }
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static Engine fixture(MatterCavern[] baseline) {
        Engine engine = mock(Engine.class, withSettings().stubOnly());
        EngineMantle engineMantle = mock(EngineMantle.class, withSettings().stubOnly());
        Mantle<Matter> mantle = mock(Mantle.class, withSettings().stubOnly());
        IrisDimension dimension = mock(IrisDimension.class, withSettings().stubOnly());
        when(engine.getHeight()).thenReturn(HEIGHT);
        when(engine.getMinHeight()).thenReturn(WORLD_MIN);
        when(engine.getMantle()).thenReturn(engineMantle);
        when(engineMantle.getMantle()).thenReturn(mantle);
        when(engine.getDimension()).thenReturn(dimension);
        when(dimension.getCaveLavaHeight()).thenReturn(8);
        when(mantle.get(anyInt(), anyInt(), anyInt(), eq(MatterCavern.class)))
                .thenAnswer(invocation -> {
                    int y = invocation.getArgument(1);
                    return y < 0 || y >= HEIGHT ? null : baseline[y];
                });
        return engine;
    }

    private static List<Integer> reference(
            MatterCavern[] baseline, HydrologyCaveCell[] hydrology, IrisStructureAnchorMode mode,
            int minimum, int maximum, int step, int clearance, boolean underwater
    ) {
        boolean[] carved = new boolean[HEIGHT];
        for (int y = 0; y < HEIGHT; y++) {
            MatterCavern cavern = hydrology[y] == null ? baseline[y] : hydrology[y].asCavern();
            carved[y] = cavern != null && cavern.isCavern();
        }
        List<Integer> anchors = new ArrayList<>();
        if (mode == IrisStructureAnchorMode.CAVE_CENTER) {
            for (int lower = 0; lower < HEIGHT; lower++) {
                if (!carved[lower]) {
                    continue;
                }
                int upper = lower;
                while (upper + 1 < HEIGHT && carved[upper + 1]) {
                    upper++;
                }
                int firstSample = minimum + Math.ceilDiv(Math.max(lower, minimum) - minimum, step) * step;
                if (upper - lower + 1 >= clearance && firstSample <= Math.min(upper, maximum)) {
                    int lowCenter = (lower + upper) / 2;
                    int highCenter = (lower + upper + 1) / 2;
                    addReferenceAnchor(anchors, baseline, hydrology, lowCenter, minimum, maximum, underwater);
                    if (highCenter != lowCenter) {
                        addReferenceAnchor(anchors, baseline, hydrology, highCenter, minimum, maximum, underwater);
                    }
                }
                lower = upper;
            }
            return anchors;
        }
        for (int y = minimum; y <= maximum; y += step) {
            int lower = switch (mode) {
                case CAVE_FLOOR -> y;
                case CAVE_CEILING -> y - clearance + 1;
                case CAVE_ANY -> y - (clearance - 1) / 2;
                default -> throw new AssertionError(mode);
            };
            boolean valid = carved[y] && lower >= 0 && lower + clearance <= HEIGHT;
            if (mode == IrisStructureAnchorMode.CAVE_FLOOR) {
                valid &= y == 0 || !carved[y - 1];
            } else if (mode == IrisStructureAnchorMode.CAVE_CEILING) {
                valid &= y + 1 == HEIGHT || !carved[y + 1];
            }
            for (int offset = 0; valid && offset < clearance; offset++) {
                valid = carved[lower + offset];
            }
            if (valid) {
                addReferenceAnchor(anchors, baseline, hydrology, y, minimum, maximum, underwater);
            }
        }
        return anchors;
    }

    private static void addReferenceAnchor(
            List<Integer> anchors, MatterCavern[] baseline, HydrologyCaveCell[] hydrology,
            int y, int minimum, int maximum, boolean underwater
    ) {
        if (y < minimum || y > maximum || hydrology[y] != null) {
            return;
        }
        MatterCavern cavern = baseline[y];
        if (cavern != null && cavern.isCavern()
                && (underwater || cavern.getLiquid() == 3 || cavern.isAir() && y > 8)) {
            anchors.add(y + WORLD_MIN);
        }
    }
}
