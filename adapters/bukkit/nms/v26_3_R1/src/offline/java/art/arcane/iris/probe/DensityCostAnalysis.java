package art.arcane.iris.probe;

import net.minecraft.core.Holder;
import net.minecraft.util.CubicSpline;
import net.minecraft.world.level.levelgen.densityfunction.DensityFunction;
import net.minecraft.world.level.levelgen.densityfunction.DensityFunctions;
import net.minecraft.world.level.levelgen.densityfunction.generator.ConstantFunction;
import net.minecraft.world.level.levelgen.densityfunction.op.CacheFunction;
import net.minecraft.world.level.levelgen.densityfunction.op.FindTopSurfaceFunction;
import net.minecraft.world.level.levelgen.densityfunction.op.InterpolatedFunction;
import net.minecraft.world.level.levelgen.densityfunction.op.IntervalSelectFunction;
import net.minecraft.world.level.levelgen.densityfunction.op.RangeChoiceFunction;
import net.minecraft.world.level.levelgen.densityfunction.op.SplineFunction;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Function;

final class DensityCostAnalysis {
    private static final double LATTICE_CORNERS = 8.0;
    private static final int MAX_SURFACE_STEPS = 4096;
    private static final String INLINE = "<inline>";

    private final Function<String, String> categories;
    private final int topCount;
    private final Map<Object, Costs> costs = new IdentityHashMap<>();
    private final Map<DensityFunction, Body> bodies = new IdentityHashMap<>();
    private final Map<DensityFunction, String> names = new IdentityHashMap<>();

    DensityCostAnalysis(Function<String, String> categories, int topCount) {
        this.categories = categories;
        this.topCount = topCount;
    }

    RootCost analyze(String root, DensityFunction function) {
        Costs total = cost(function);
        Reach reach = new Reach();
        reach(function, reach, categories.apply(rootName(function, root)));
        double cached = total.cachedLocal();
        for (CacheFunction cache : reach.caches.keySet()) {
            cached += cost(cache.input()).cachedLocal();
        }
        Body rootBody = new Body();
        local(function, rootBody, true);
        Map<DensityFunction, Double> multiplicity = multiplicity(rootBody);
        List<Offender> offenders = new ArrayList<>(multiplicity.size());
        Map<String, Double> shares = new TreeMap<>();
        double expanded = total.expanded();
        shares.merge(categories.apply(rootName(function, root)), rootBody.self / expanded, Double::sum);
        for (Map.Entry<DensityFunction, Double> entry : multiplicity.entrySet()) {
            DensityFunction value = entry.getKey();
            Body body = body(value);
            String name = names.get(value);
            String category = categories.apply(name);
            double contribution = entry.getValue() * body.self;
            shares.merge(category, contribution / expanded, Double::sum);
            offenders.add(new Offender(name, category, entry.getValue(), body.self, cost(value).expanded(),
                    contribution, entry.getValue() * cost(value).expanded()));
        }
        List<Offender> exclusive = top(offenders, Comparator.comparingDouble(Offender::contribution).reversed());
        List<Offender> inclusive = top(offenders, Comparator.comparingDouble(Offender::inclusive).reversed());
        Map<String, Double> dagShares = new TreeMap<>();
        for (Map.Entry<String, Double> entry : reach.categories.entrySet()) {
            dagShares.put(entry.getKey(), entry.getValue() / Math.max(1L, reach.nodes));
        }
        return new RootCost(root, reach.nodes, cached, expanded, total.lazy(), multiplicity.size(), exclusive, inclusive, shares, dagShares);
    }

    private List<Offender> top(List<Offender> offenders, Comparator<Offender> order) {
        List<Offender> sorted = new ArrayList<>(offenders);
        sorted.sort(order);
        return List.copyOf(sorted.subList(0, Math.min(topCount, sorted.size())));
    }

    private String rootName(DensityFunction function, String root) {
        if (function instanceof DensityFunctions.HolderHolder(Holder<DensityFunction> holder)) {
            return name(holder);
        }
        return root;
    }

    private Costs cost(DensityFunction function) {
        if (function instanceof DensityFunctions.HolderHolder(Holder<DensityFunction> holder)) {
            DensityFunction value = holder.value();
            names.putIfAbsent(value, name(holder));
            return cost(value);
        }
        Costs known = costs.get(function);
        if (known != null) {
            return known;
        }
        Costs computed = compute(function);
        costs.put(function, computed);
        return computed;
    }

    private Costs compute(DensityFunction function) {
        return switch (function) {
            case ConstantFunction ignored -> new Costs(0.0, 0.0, 0.0);
            case CacheFunction cache -> {
                Costs input = cost(cache.input());
                yield new Costs(1.0 + input.expanded(), 1.0 + input.lazy(), 1.0);
            }
            case SplineFunction spline -> {
                Costs inner = spline(spline.spline());
                yield new Costs(1.0 + inner.expanded(), 1.0 + inner.lazy(), 1.0 + inner.cachedLocal());
            }
            case IntervalSelectFunction select -> {
                Costs input = cost(select.input());
                double expanded = 1.0 + input.expanded();
                double cached = 1.0 + input.cachedLocal();
                double lazy = 0.0;
                for (DensityFunction branch : select.functions()) {
                    Costs branchCost = cost(branch);
                    expanded += branchCost.expanded();
                    cached += branchCost.cachedLocal();
                    lazy = Math.max(lazy, branchCost.lazy());
                }
                yield new Costs(expanded, 1.0 + input.lazy() + lazy, cached);
            }
            case RangeChoiceFunction choice -> {
                Costs input = cost(choice.input());
                Costs inside = cost(choice.whenInRange());
                Costs outside = cost(choice.whenOutOfRange());
                yield new Costs(1.0 + input.expanded() + inside.expanded() + outside.expanded(),
                        1.0 + input.lazy() + Math.max(inside.lazy(), outside.lazy()),
                        1.0 + input.cachedLocal() + inside.cachedLocal() + outside.cachedLocal());
            }
            case InterpolatedFunction grid -> {
                Costs input = cost(grid.input());
                yield new Costs(1.0 + input.expanded(), 1.0 + LATTICE_CORNERS * input.expanded(), 1.0 + input.cachedLocal());
            }
            case FindTopSurfaceFunction surface -> {
                Costs density = cost(surface.density());
                Costs upper = cost(surface.upperBound());
                double steps = surfaceSteps(surface);
                yield new Costs(1.0 + density.expanded() + upper.expanded(), 1.0 + upper.lazy() + steps * density.lazy(),
                        1.0 + density.cachedLocal() + upper.cachedLocal());
            }
            default -> {
                double expanded = 1.0;
                double lazy = 1.0;
                double cached = 1.0;
                for (DensityFunction child : children(function)) {
                    Costs childCost = cost(child);
                    expanded += childCost.expanded();
                    lazy += childCost.lazy();
                    cached += childCost.cachedLocal();
                }
                yield new Costs(expanded, lazy, cached);
            }
        };
    }

    private Costs spline(CubicSpline<SplineFunction.Coordinate> spline) {
        if (!(spline instanceof CubicSpline.Multipoint<SplineFunction.Coordinate> multipoint)) {
            return new Costs(0.0, 0.0, 0.0);
        }
        Costs known = costs.get(multipoint);
        if (known != null) {
            return known;
        }
        Costs coordinate = cost(multipoint.coordinate().function());
        List<CubicSpline<SplineFunction.Coordinate>> values = multipoint.values();
        double expanded = 1.0 + coordinate.expanded();
        double cached = 1.0 + coordinate.cachedLocal();
        double[] lazy = new double[values.size()];
        for (int index = 0; index < values.size(); index++) {
            Costs value = spline(values.get(index));
            expanded += value.expanded();
            cached += value.cachedLocal();
            lazy[index] = value.lazy();
        }
        double worstPair = lazy.length == 1 ? lazy[0] : 0.0;
        for (int index = 0; index + 1 < lazy.length; index++) {
            worstPair = Math.max(worstPair, lazy[index] + lazy[index + 1]);
        }
        Costs computed = new Costs(expanded, 1.0 + coordinate.lazy() + worstPair, cached);
        costs.put(multipoint, computed);
        return computed;
    }

    private void reach(DensityFunction function, Reach reach, String category) {
        if (function instanceof DensityFunctions.HolderHolder(Holder<DensityFunction> holder)) {
            reach(holder.value(), reach, categories.apply(name(holder)));
            return;
        }
        if (function instanceof ConstantFunction || reach.visited.put(function, Boolean.TRUE) != null) {
            return;
        }
        reach.count(category);
        if (function instanceof CacheFunction cache) {
            reach.caches.put(cache, Boolean.TRUE);
        }
        if (function instanceof SplineFunction spline) {
            reachSpline(spline.spline(), reach, category);
            return;
        }
        for (DensityFunction child : children(function)) {
            reach(child, reach, category);
        }
    }

    private void reachSpline(CubicSpline<SplineFunction.Coordinate> spline, Reach reach, String category) {
        if (!(spline instanceof CubicSpline.Multipoint<SplineFunction.Coordinate> multipoint)) {
            return;
        }
        if (reach.visited.put(multipoint, Boolean.TRUE) != null) {
            return;
        }
        reach.count(category);
        reach(multipoint.coordinate().function(), reach, category);
        for (CubicSpline<SplineFunction.Coordinate> value : multipoint.values()) {
            reachSpline(value, reach, category);
        }
    }

    private Body body(DensityFunction value) {
        Body known = bodies.get(value);
        if (known != null) {
            return known;
        }
        Body body = new Body();
        local(value, body, true);
        bodies.put(value, body);
        return body;
    }

    private void local(DensityFunction function, Body body, boolean top) {
        if (function instanceof DensityFunctions.HolderHolder(Holder<DensityFunction> holder)) {
            if (!top) {
                DensityFunction value = holder.value();
                names.putIfAbsent(value, name(holder));
                body.references.merge(value, 1.0, Double::sum);
                return;
            }
            local(holder.value(), body, true);
            return;
        }
        if (function instanceof ConstantFunction) {
            return;
        }
        body.self++;
        if (function instanceof SplineFunction spline) {
            localSpline(spline.spline(), body);
            return;
        }
        for (DensityFunction child : children(function)) {
            local(child, body, false);
        }
    }

    private void localSpline(CubicSpline<SplineFunction.Coordinate> spline, Body body) {
        if (!(spline instanceof CubicSpline.Multipoint<SplineFunction.Coordinate> multipoint)) {
            return;
        }
        body.self++;
        local(multipoint.coordinate().function(), body, false);
        for (CubicSpline<SplineFunction.Coordinate> value : multipoint.values()) {
            localSpline(value, body);
        }
    }

    private Map<DensityFunction, Double> multiplicity(Body root) {
        List<DensityFunction> order = new ArrayList<>();
        Map<DensityFunction, Boolean> visited = new IdentityHashMap<>();
        for (DensityFunction child : root.references.keySet()) {
            postOrder(child, visited, order);
        }
        Collections.reverse(order);
        Map<DensityFunction, Double> multiplicity = new IdentityHashMap<>(order.size() * 2);
        for (Map.Entry<DensityFunction, Double> entry : root.references.entrySet()) {
            multiplicity.merge(entry.getKey(), entry.getValue(), Double::sum);
        }
        for (DensityFunction value : order) {
            double times = multiplicity.getOrDefault(value, 0.0);
            for (Map.Entry<DensityFunction, Double> entry : body(value).references.entrySet()) {
                multiplicity.merge(entry.getKey(), times * entry.getValue(), Double::sum);
            }
        }
        return multiplicity;
    }

    private void postOrder(DensityFunction value, Map<DensityFunction, Boolean> visited, List<DensityFunction> order) {
        if (visited.put(value, Boolean.TRUE) != null) {
            return;
        }
        for (DensityFunction child : body(value).references.keySet()) {
            postOrder(child, visited, order);
        }
        order.add(value);
    }

    private static double surfaceSteps(FindTopSurfaceFunction surface) {
        double upper = surface.upperBound().range().max();
        if (!Double.isFinite(upper)) {
            return MAX_SURFACE_STEPS;
        }
        double steps = Math.floor((upper - surface.lowerBound()) / surface.cellHeight()) + 1.0;
        return Math.clamp(steps, 1.0, MAX_SURFACE_STEPS);
    }

    private static List<DensityFunction> children(DensityFunction function) {
        List<DensityFunction> children = new ArrayList<>(4);
        function.rewriteChildren(child -> {
            children.add(child);
            return child;
        });
        return children;
    }

    private static String name(Holder<DensityFunction> holder) {
        return holder.unwrapKey().map(key -> key.identifier().toString()).orElse(INLINE);
    }

    record Costs(double expanded, double lazy, double cachedLocal) {
    }

    record Offender(String name, String category, double multiplicity, double selfNodes, double subtreeNodes,
                    double contribution, double inclusive) {
    }

    record RootCost(String root, long dagNodes, double cachedNodes, double expandedNodes, double uncachedPointWorst,
                    int namedSubtrees, List<Offender> exclusive, List<Offender> inclusive, Map<String, Double> categoryShare,
                    Map<String, Double> dagShare) {
    }

    private static final class Reach {
        private final Map<Object, Boolean> visited = new IdentityHashMap<>();
        private final Map<CacheFunction, Boolean> caches = new IdentityHashMap<>();
        private final Map<String, Double> categories = new TreeMap<>();
        private long nodes;

        private void count(String category) {
            nodes++;
            categories.merge(category, 1.0, Double::sum);
        }
    }

    private static final class Body {
        private final Map<DensityFunction, Double> references = new IdentityHashMap<>();
        private double self;
    }
}
