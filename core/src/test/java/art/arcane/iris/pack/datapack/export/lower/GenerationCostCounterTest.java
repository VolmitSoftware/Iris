package art.arcane.iris.pack.datapack.export.lower;

import art.arcane.iris.pack.datapack.DataVersion;
import art.arcane.iris.pack.datapack.export.GenerationCost;
import art.arcane.iris.pack.datapack.export.ir.AquiferDef;
import art.arcane.iris.pack.datapack.export.ir.Field;
import art.arcane.iris.pack.datapack.export.ir.NoiseDef;
import art.arcane.iris.pack.datapack.export.ir.RouterDef;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class GenerationCostCounterTest {
    private static final Field NOISE = new Field.Noise("iris_export:base", 0.25, 0.0, null, null, null);
    private static final Field OTHER_NOISE = new Field.Noise("iris_export:other", 0.25, 0.0, null, null, null);
    private static final Field ZERO = new Field.Const(0.0);
    private static final VanillaBounds NO_NOISES = VanillaBounds.of("iris_export", List.of());

    @Test
    public void disjointMinMaxCountsOnlyTheInputThatAlwaysWins() {
        Field band = new Field.Shared("band", new Field.Clamp(NOISE, 0.25, 0.75));
        Field floor = new Field.Binary(Field.BinaryOp.MAX, new Field.Binary(Field.BinaryOp.MUL, band, new Field.Const(2.0)), ZERO);
        Field twin = new Field.Shared("twin", new Field.Binary(Field.BinaryOp.MUL, band, new Field.Const(2.0)));
        Field root = new Field.Binary(Field.BinaryOp.ADD, new Field.Shared("floor", floor), twin);
        VanillaBounds bounds = VanillaBounds.of("iris_export", List.of(new NoiseDef("base", 0, List.of(1.0))));
        RouterDef router = new RouterDef(root, ZERO, ZERO, ZERO, ZERO, ZERO, ZERO, ZERO, AquiferDef.dry(ZERO));
        GenerationCost.RootCost cost = GenerationCostCounter.count(router, bounds, DataVersion.V26_3).root(GenerationCost.Root.TEMPERATURE).orElseThrow();

        assertEquals(11.0, cost.expandedNodes(), 0.0);
        assertEquals(11.0, cost.worstCaseNodes(), 0.0);
        assertEquals(6L, cost.distinctNodes());
        assertEquals(12.0, GenerationCostCounter.worstCase(root, NO_NOISES, DataVersion.V26_3), 0.0);
    }

    @Test
    public void sharedReferencesReExpandButCountOnceAsDistinct() {
        Field shared = new Field.Shared("a", NOISE);
        GenerationCost.RootCost cost = temperature(new Field.Binary(Field.BinaryOp.ADD, shared, shared), DataVersion.V26_3);

        assertEquals(5.0, cost.expandedNodes(), 0.0);
        assertEquals(5.0, cost.worstCaseNodes(), 0.0);
        assertEquals(3L, cost.distinctNodes());
    }

    @Test
    public void structurallyEqualSharedBodiesShareOneFile() {
        Field first = new Field.Shared("a", new Field.Binary(Field.BinaryOp.MUL, NOISE, new Field.Const(2.0)));
        Field second = new Field.Shared("b", new Field.Binary(Field.BinaryOp.MUL, NOISE, new Field.Const(2.0)));
        GenerationCost.RootCost cost = temperature(new Field.Binary(Field.BinaryOp.ADD, first, second), DataVersion.V26_3);

        assertEquals(7.0, cost.expandedNodes(), 0.0);
        assertEquals(4L, cost.distinctNodes());
    }

    @Test
    public void inlineDuplicatesAreDistinctNodes() {
        Field product = new Field.Binary(Field.BinaryOp.MUL, NOISE, new Field.Const(2.0));
        GenerationCost.RootCost cost = temperature(new Field.Binary(Field.BinaryOp.ADD, product, product), DataVersion.V26_3);

        assertEquals(5.0, cost.expandedNodes(), 0.0);
        assertEquals(5L, cost.distinctNodes());
    }

    @Test
    public void muxCountsEveryBranchAsSpline() {
        Field mux = new Field.Mux(NOISE, List.of(ZERO, OTHER_NOISE, new Field.Shared("c", OTHER_NOISE)));

        assertEquals(8.0, temperature(mux, DataVersion.V26_3).expandedNodes(), 0.0);
        assertEquals(6.0, temperature(mux, DataVersion.V26_3).worstCaseNodes(), 0.0);
        assertEquals(1.0, temperature(new Field.Mux(NOISE, List.of(OTHER_NOISE)), DataVersion.V26_3).expandedNodes(), 0.0);
    }

    @Test
    public void gateCurveAndConstantsFollowTheEmittedShape() {
        Field gate = new Field.Gate(NOISE, OTHER_NOISE);
        Field nested = new Field.Curve(OTHER_NOISE, List.of(new Field.Knot(0.0, ZERO, 0.0), new Field.Knot(1.0, new Field.Const(1.0), 0.0)));
        Field curve = new Field.Curve(NOISE, List.of(new Field.Knot(-1.0, nested, 0.0), new Field.Knot(1.0, ZERO, 0.0)));

        assertEquals(5.0, temperature(gate, DataVersion.V26_3).expandedNodes(), 0.0);
        assertEquals(5.0, temperature(gate, DataVersion.V26_3).worstCaseNodes(), 0.0);
        assertEquals(5.0, temperature(curve, DataVersion.V26_3).expandedNodes(), 0.0);
        assertEquals(5.0, temperature(curve, DataVersion.V26_3).worstCaseNodes(), 0.0);
        assertEquals(0.0, temperature(ZERO, DataVersion.V26_3).expandedNodes(), 0.0);
        assertEquals(0L, temperature(ZERO, DataVersion.V26_3).distinctNodes());
    }

    @Test
    public void interpolatedNodesEvaluateEightCornersOffLattice() {
        Field grid = new Field.Grid(new Field.Binary(Field.BinaryOp.MUL, NOISE, OTHER_NOISE), 4, 8);
        GenerationCost.RootCost cost = temperature(new Field.Binary(Field.BinaryOp.ADD, grid, NOISE), DataVersion.V26_3);

        assertEquals(6.0, cost.expandedNodes(), 0.0);
        assertEquals(27.0, cost.worstCaseNodes(), 0.0);
        assertEquals(6L, cost.distinctNodes());
    }

    @Test
    public void pointEvaluationTakesTheWorstBranchAndCountsSurfaceProbes() {
        Field choice = new Field.RangeIf(NOISE, 0.0, 1.0, OTHER_NOISE, new Field.Binary(Field.BinaryOp.ADD, OTHER_NOISE, NOISE));
        Field surface = new Field.TopSurface(new Field.Noise("iris_export:base", 0.25, 0.5, null, null, null), 10, 0, 1);

        assertEquals(6.0, temperature(choice, DataVersion.V26_3).expandedNodes(), 0.0);
        assertEquals(5.0, temperature(choice, DataVersion.V26_3).worstCaseNodes(), 0.0);
        assertEquals(2.0, temperature(surface, DataVersion.V26_3).expandedNodes(), 0.0);
        assertEquals(12.0, temperature(surface, DataVersion.V26_3).worstCaseNodes(), 0.0);
    }

    @Test
    public void worstCaseOfASingleFieldMatchesItsRouterRoot() {
        Field choice = new Field.Mux(NOISE, List.of(OTHER_NOISE, new Field.Grid(new Field.Binary(Field.BinaryOp.ADD, NOISE, OTHER_NOISE), 4, 1)));

        assertEquals(temperature(choice, DataVersion.V26_3).worstCaseNodes(), GenerationCostCounter.worstCase(choice, NO_NOISES, DataVersion.V26_3), 0.0);
        assertEquals(29.0, GenerationCostCounter.worstCase(choice, NO_NOISES, DataVersion.V26_3), 0.0);
    }

    @Test
    public void subtractionIsOneNode() {
        Field difference = new Field.Binary(Field.BinaryOp.SUB, NOISE, OTHER_NOISE);

        assertEquals(3.0, temperature(difference, DataVersion.V26_3).expandedNodes(), 0.0);
    }

    @Test
    public void deepSharingCountsWithoutExpandingTheTree() {
        Field current = NOISE;
        for (int level = 0; level < 200; level++) {
            current = new Field.Shared("level/" + level, new Field.Binary(Field.BinaryOp.ADD, current, current));
        }
        GenerationCost.RootCost cost = temperature(current, DataVersion.V26_3);

        assertTrue(Double.isFinite(cost.expandedNodes()));
        assertTrue(cost.expandedNodes() > 1.0e60);
        assertEquals(402L, cost.distinctNodes());
    }

    @Test
    public void everyEmittedRouterSlotIsCounted() {
        Field shared = new Field.Shared("height", NOISE);
        RouterDef router = new RouterDef(shared, OTHER_NOISE, ZERO, ZERO, new Field.Binary(Field.BinaryOp.ADD, shared, shared), ZERO,
                shared, NOISE, AquiferDef.dry(shared));

        GenerationCost current = GenerationCostCounter.count(router, NO_NOISES, DataVersion.V26_3);

        assertEquals(GenerationCost.Root.values().length, current.roots().size());
        assertEquals(5.0, current.root(GenerationCost.Root.DEPTH).orElseThrow().expandedNodes(), 0.0);
        assertEquals(1.0, current.root(GenerationCost.Root.VEGETATION).orElseThrow().expandedNodes(), 0.0);
        assertEquals(0.0, current.root(GenerationCost.Root.CONTINENTS).orElseThrow().expandedNodes(), 0.0);
        assertTrue(current.root(GenerationCost.Root.CHUNK_SURFACE_LEVEL).isPresent());
        assertTrue(current.root(GenerationCost.Root.AQUIFER_EXCLUSION).isPresent());
    }

    private static GenerationCost.RootCost temperature(Field field, DataVersion target) {
        RouterDef router = new RouterDef(field, ZERO, ZERO, ZERO, ZERO, ZERO, ZERO, ZERO, AquiferDef.dry(ZERO));
        return GenerationCostCounter.count(router, NO_NOISES, target).root(GenerationCost.Root.TEMPERATURE).orElseThrow();
    }
}
