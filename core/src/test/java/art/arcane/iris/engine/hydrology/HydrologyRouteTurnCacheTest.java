package art.arcane.iris.engine.hydrology;

import org.junit.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;

public class HydrologyRouteTurnCacheTest {
    @Test
    public void repeatedRefinementMatchesExhaustiveUncachedRoutes() throws Exception {
        Random random = new Random(712319L);
        Solver solver = new Solver();
        for (int scenario = 0; scenario < 40; scenario++) {
            int count = 4 + random.nextInt(4);
            HydrologyPoint[][] points = new HydrologyPoint[count][];
            for (int layer = 0; layer < count; layer++) {
                points[layer] = new HydrologyPoint[layer == 0 || layer == count - 1 ? 1 : 3];
                for (int candidate = 0; candidate < points[layer].length; candidate++) {
                    points[layer][candidate] = new HydrologyPoint(layer * 12 + random.nextInt(5), 80,
                            candidate * 9 + random.nextInt(7));
                }
            }
            double maximumTurn = scenario % 2 == 0 ? 35D : 115D;
            double[][][][] cache = new double[count][][][];
            for (int refinement = 0; refinement < 8; refinement++) {
                Fixture fixture = fixture(points, random);
                assertArrayEquals("scenario=" + scenario + " refinement=" + refinement,
                        exhaustive(fixture, maximumTurn), solver.solve(fixture, maximumTurn, cache));
                assertCacheGeometry(fixture, maximumTurn, cache);
            }
        }
    }

    @Test
    public void straightTiesAndDegenerateSegmentsKeepOriginalFirstChoice() throws Exception {
        HydrologyPoint[][] points = {
                {new HydrologyPoint(0, 80, 0)},
                {new HydrologyPoint(0, 90, 0), new HydrologyPoint(0, 70, 0)},
                {new HydrologyPoint(12, 80, 0), new HydrologyPoint(12, 80, 0)},
                {new HydrologyPoint(24, 80, 0)}
        };
        Fixture fixture = fixture(points, new Random(2L));
        for (double[] scores : fixture.scores()) {
            Arrays.fill(scores, 0D);
        }
        for (double[] penalties : fixture.penalties()) {
            Arrays.fill(penalties, 0D);
        }
        for (double[][] transitions : fixture.transitions()) {
            if (transitions != null) {
                for (double[] row : transitions) {
                    Arrays.fill(row, 0D);
                }
            }
        }
        assertArrayEquals(new int[]{0, 0, 0, 0}, new Solver().solve(fixture, 75D, new double[4][][][]));
    }

    private static Fixture fixture(HydrologyPoint[][] coordinates, Random random) {
        int count = coordinates.length;
        HydrologyPoint[][] points = new HydrologyPoint[count][];
        double[][] scores = new double[count][];
        double[][] penalties = new double[count][];
        double[][][] transitions = new double[count][][];
        for (int layer = 0; layer < count; layer++) {
            int size = coordinates[layer].length;
            points[layer] = new HydrologyPoint[size];
            scores[layer] = new double[size];
            penalties[layer] = new double[size];
            for (int candidate = 0; candidate < size; candidate++) {
                HydrologyPoint point = coordinates[layer][candidate];
                points[layer][candidate] = new HydrologyPoint(point.x(), random.nextInt(200), point.z());
                scores[layer][candidate] = random.nextDouble() * 20D;
                penalties[layer][candidate] = random.nextDouble() * 10D;
            }
            if (layer > 0) {
                transitions[layer] = new double[coordinates[layer - 1].length][size];
                for (double[] row : transitions[layer]) {
                    for (int current = 0; current < size; current++) {
                        row[current] = random.nextInt(8) == 0 ? Double.POSITIVE_INFINITY : random.nextDouble() * 30D;
                    }
                }
            }
        }
        return new Fixture(points, scores, penalties, transitions);
    }

    private static int[] exhaustive(Fixture fixture, double maximumTurn) {
        ArrayList<int[]> routes = new ArrayList<>();
        enumerate(fixture.points(), new int[fixture.points().length], 0, routes);
        int[] selected = new int[0];
        double selectedCost = Double.POSITIVE_INFINITY;
        for (int[] route : routes) {
            double cost = fixture.scores()[0][route[0]] + fixture.scores()[1][route[1]]
                    + fixture.penalties()[1][route[1]] + fixture.transitions()[1][route[0]][route[1]];
            for (int layer = 2; layer < route.length && Double.isFinite(cost); layer++) {
                double turn = turn(fixture.points()[layer - 2][route[layer - 2]],
                        fixture.points()[layer - 1][route[layer - 1]], fixture.points()[layer][route[layer]]);
                if (turn > maximumTurn) {
                    cost = Double.POSITIVE_INFINITY;
                    break;
                }
                double excess = Math.max(0D, turn - 8D);
                cost = cost + fixture.scores()[layer][route[layer]] + fixture.penalties()[layer][route[layer]]
                        + fixture.transitions()[layer][route[layer - 1]][route[layer]]
                        + excess * excess * 12D / maximumTurn;
            }
            if (cost < selectedCost || Double.isFinite(cost) && cost == selectedCost && precedes(route, selected)) {
                selectedCost = cost;
                selected = route;
            }
        }
        return selected;
    }

    private static boolean precedes(int[] candidate, int[] selected) {
        int last = candidate.length - 1;
        if (candidate[last - 1] != selected[last - 1]) {
            return candidate[last - 1] < selected[last - 1];
        }
        if (candidate[last] != selected[last]) {
            return candidate[last] < selected[last];
        }
        for (int index = last - 2; index >= 0; index--) {
            if (candidate[index] != selected[index]) {
                return candidate[index] < selected[index];
            }
        }
        return false;
    }

    private static void enumerate(HydrologyPoint[][] points, int[] route, int layer, List<int[]> routes) {
        if (layer == route.length) {
            routes.add(route.clone());
            return;
        }
        for (int candidate = 0; candidate < points[layer].length; candidate++) {
            route[layer] = candidate;
            enumerate(points, route, layer + 1, routes);
        }
    }

    private static void assertCacheGeometry(Fixture fixture, double maximumTurn, double[][][][] cache) {
        int populated = 0;
        for (int layer = 2; layer < cache.length; layer++) {
            for (int previous = 0; previous < cache[layer].length; previous++) {
                for (int current = 0; current < cache[layer][previous].length; current++) {
                    double[] costs = cache[layer][previous][current];
                    if (costs == null) {
                        continue;
                    }
                    for (int before = 0; before < costs.length; before++) {
                        if (Double.isNaN(costs[before])) {
                            continue;
                        }
                        double turn = turn(fixture.points()[layer - 2][before],
                                fixture.points()[layer - 1][previous], fixture.points()[layer][current]);
                        double excess = Math.max(0D, turn - 8D);
                        double expected = turn > maximumTurn ? Double.POSITIVE_INFINITY : excess * excess * 12D / maximumTurn;
                        assertEquals(Double.doubleToLongBits(expected), Double.doubleToLongBits(costs[before]));
                        populated++;
                    }
                }
            }
        }
        assertTrue(populated > 0);
    }

    private static double turn(HydrologyPoint before, HydrologyPoint previous, HydrologyPoint current) {
        double incomingX = previous.x() - before.x();
        double incomingZ = previous.z() - before.z();
        double outgoingX = current.x() - previous.x();
        double outgoingZ = current.z() - previous.z();
        double incomingLength = StrictMath.hypot(incomingX, incomingZ);
        double outgoingLength = StrictMath.hypot(outgoingX, outgoingZ);
        if (incomingLength == 0D || outgoingLength == 0D) {
            return 0D;
        }
        return StrictMath.toDegrees(StrictMath.acos(Math.max(-1D, Math.min(1D,
                (incomingX * outgoingX + incomingZ * outgoingZ) / (incomingLength * outgoingLength)))));
    }

    private record Fixture(HydrologyPoint[][] points, double[][] scores, double[][] penalties, double[][][] transitions) {
    }

    private static final class Solver {
        private final HydrologyRouteGeometry geometry = mock(HydrologyRouteGeometry.class, CALLS_REAL_METHODS);
        private final Constructor<?> candidateConstructor;
        private final Object tangent;
        private final Method solve;
        private final Method indices;

        private Solver() throws Exception {
            Class<?> direction = RouteDirection.class;
            Constructor<?> directionConstructor = direction.getDeclaredConstructor(double.class, double.class);
            directionConstructor.setAccessible(true);
            tangent = directionConstructor.newInstance(1D, 0D);
            Class<?> candidate = RouteCandidate.class;
            candidateConstructor = candidate.getDeclaredConstructor(HydrologyPoint.class, double.class, double.class,
                    double.class, direction, boolean.class);
            candidateConstructor.setAccessible(true);
            solve = HydrologyRouteGeometry.class.getDeclaredMethod("selectCurvatureAwareTerrainRoute", List.class,
                    double.class, double.class, double.class, double[][].class, double[][][].class, double[][][][].class);
            solve.setAccessible(true);
            indices = solve.getReturnType().getDeclaredMethod("indices");
            indices.setAccessible(true);
        }

        private int[] solve(Fixture fixture, double maximumTurn, double[][][][] cache) throws Exception {
            ArrayList<List<Object>> layers = new ArrayList<>();
            for (int layer = 0; layer < fixture.points().length; layer++) {
                ArrayList<Object> candidates = new ArrayList<>();
                for (int candidate = 0; candidate < fixture.points()[layer].length; candidate++) {
                    candidates.add(candidateConstructor.newInstance(fixture.points()[layer][candidate], 0D,
                            fixture.scores()[layer][candidate], 0D, tangent, true));
                }
                layers.add(candidates);
            }
            return (int[]) indices.invoke(solve.invoke(geometry, layers, 12D, maximumTurn, 12D,
                    fixture.penalties(), fixture.transitions(), cache));
        }
    }
}
