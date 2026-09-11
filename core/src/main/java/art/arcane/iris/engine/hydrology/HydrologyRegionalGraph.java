package art.arcane.iris.engine.hydrology;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.PriorityQueue;

final class HydrologyRegionalGraph {
    private static final int LABELS_PER_GRID_NODE = 64;
    private static final int MAXIMUM_LABELS = 1_048_576;
    private static final Comparator<Label> LABEL_ORDER = Comparator.comparingInt((Label label) -> label.state.requiredHead())
            .thenComparingDouble(label -> label.state.cost()).thenComparingDouble(label -> label.state.length())
            .thenComparingInt(label -> label.state.index()).thenComparingInt(label -> label.state.root())
            .thenComparingInt(label -> label.state.id());

    private final HydrologyPlanner planner;
    private final HydrologyRegionalHydraulics hydraulics;

    HydrologyRegionalGraph(HydrologyPlanner planner) {
        this.planner = planner;
        this.hydraulics = new HydrologyRegionalHydraulics(planner.settings);
    }

    Tree route(HydrologySampledGrid grid, List<OutletCandidate> outlets, boolean coastal) {
        int count = grid.nodes().size();
        int maximumLabels = (int) Math.min(MAXIMUM_LABELS, Math.max(count, (long) count * LABELS_PER_GRID_NODE));
        ArrayList<List<Label>> frontiers = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            frontiers.add(new ArrayList<>());
        }
        ArrayList<Label> labels = new ArrayList<>(count);
        PriorityQueue<Label> pending = new PriorityQueue<>(LABEL_ORDER);
        for (int outletIndex = 0; outletIndex < outlets.size(); outletIndex++) {
            int index = outlets.get(outletIndex).landIndex();
            Label label = new Label(new LabelState(labels.size(), index, planner.settings.seaLevel(),
                    0D, 0D, outletIndex), null);
            admit(label, frontiers.get(index), labels, pending);
        }
        HydrologyPlannerSettings.Inlet inlet = planner.settings.surface().banks().inlet();
        double possibleInletLength = (inlet.length() + inlet.length() / 2D) * StrictMath.sqrt(2D);
        while (!pending.isEmpty() && labels.size() < maximumLabels) {
            Label current = pending.remove();
            if (!current.active) {
                continue;
            }
            HydrologyGridNode downstream = grid.node(current.state.index());
            if (current.downstream == null) {
                HydrologyPoint landward = outlets.get(current.state.root()).outlet().landwardPoint();
                HydrologyTerrainSample terrain = planner.sampler.sample(landward.x(), landward.z());
                downstream = new HydrologyGridNode(downstream.index(), downstream.gridX(), downstream.gridZ(),
                        landward.x(), landward.z(), downstream.id(), terrain);
            }
            for (HydrologyGridOffset offset : HydrologySourcePlanner.ROUTING_OFFSETS) {
                if (labels.size() >= maximumLabels) {
                    break;
                }
                HydrologyGridNode upstream = grid.nodeAt(downstream.gridX() + offset.x(), downstream.gridZ() + offset.z());
                if (upstream == null || !allowed(upstream.terrain(), downstream.terrain(), coastal)) {
                    continue;
                }
                double distance = StrictMath.hypot(upstream.x() - downstream.x(), upstream.z() - downstream.z());
                double nextLength = current.state.length() + distance;
                if (nextLength > planner.settings.routing().maximumRouteLength()) {
                    continue;
                }
                int nodeMinimum = possibleInletLength > 0D && nextLength < possibleInletLength
                        ? hydraulics.inletMinimumHead(upstream.terrain()) : hydraulics.minimumHead(upstream.terrain());
                int needed = coastal ? planner.settings.seaLevel() : Math.max(current.state.requiredHead(), nodeMinimum);
                if (!coastal && needed > hydraulics.maximumHead(upstream.terrain())) {
                    continue;
                }
                double candidate = current.state.cost() + edgeCost(upstream, downstream, distance, coastal);
                Label label = new Label(new LabelState(labels.size(), upstream.index(), needed, candidate,
                        nextLength, current.state.root()), current);
                admit(label, frontiers.get(upstream.index()), labels, pending);
            }
        }
        Label[] selected = new Label[count];
        int[] accumulation = new int[labels.size()];
        for (int index = 0; index < count; index++) {
            for (Label label : frontiers.get(index)) {
                if (selected[index] == null || LABEL_ORDER.compare(label, selected[index]) < 0) {
                    selected[index] = label;
                }
            }
            if (selected[index] != null && grid.node(index).terrain().surfaceSourceAllowed()) {
                accumulation[selected[index].state.id()]++;
            }
        }
        for (int index = labels.size() - 1; index >= 0; index--) {
            Label label = labels.get(index);
            if (label.downstream != null) {
                accumulation[label.downstream.state.id()] += accumulation[label.state.id()];
            }
        }
        return new Tree(selected, accumulation, List.copyOf(outlets));
    }

    List<Integer> path(Tree tree, int source) {
        ArrayList<Integer> indices = new ArrayList<>();
        Label current = tree.selected()[source];
        while (current != null) {
            indices.add(current.state.index());
            current = current.downstream;
        }
        return List.copyOf(indices);
    }

    private static void admit(Label candidate, List<Label> frontier, List<Label> labels, PriorityQueue<Label> pending) {
        for (Label existing : frontier) {
            if (dominates(existing, candidate)) {
                return;
            }
        }
        for (int index = frontier.size() - 1; index >= 0; index--) {
            Label existing = frontier.get(index);
            if (dominates(candidate, existing)) {
                existing.active = false;
                frontier.remove(index);
            }
        }
        frontier.add(candidate);
        labels.add(candidate);
        pending.add(candidate);
    }

    private static boolean dominates(Label first, Label second) {
        if ((first.downstream == null || second.downstream == null)
                && (first.downstream != null || second.downstream != null || first.state.root() != second.state.root())) {
            return false;
        }
        return first.state.requiredHead() <= second.state.requiredHead()
                && first.state.cost() <= second.state.cost() && first.state.length() <= second.state.length();
    }

    private boolean allowed(HydrologyTerrainSample upstream, HydrologyTerrainSample downstream, boolean coastal) {
        if (upstream.ocean() || upstream.naturalHeight() < planner.settings.seaLevel() || !upstream.transitAllowed() || !upstream.drainsInto(downstream)
                || !HydrologySurfaceProfiles.sharesProfile(upstream, downstream)) {
            return false;
        }
        if (coastal) {
            int incision = upstream.naturalHeight() - planner.settings.seaLevel() + planner.settings.surface().minimumDepth();
            return incision <= planner.settings.routing().regional().maximumCoastalIncision()
                    && downstream.drainsInto(upstream);
        }
        return true;
    }

    private double edgeCost(HydrologyGridNode upstream, HydrologyGridNode downstream, double distance, boolean coastal) {
        HydrologyPlannerSettings.Routing routing = planner.settings.routing();
        HydrologyTerrainSample first = upstream.terrain();
        HydrologyTerrainSample second = downstream.terrain();
        double rise = Math.max(0D, second.naturalHeight() - first.naturalHeight());
        double slope = (first.slope() + second.slope()) * 0.5D;
        double incision = Math.max(0D, first.naturalHeight() - planner.settings.seaLevel());
        double resistance = coastal ? incision * incision * routing.uphillPenalty() : rise * routing.uphillPenalty();
        double policy = Math.max(0.01D, (first.routingMultiplier() + second.routingMultiplier()) * 0.5D);
        return Math.max(0.001D, (distance + resistance + slope * routing.slopePenalty()
                + first.routingCost() + second.routingCost()) * policy);
    }

    record Tree(Label[] selected, int[] accumulation, List<OutletCandidate> outlets) {
        int root(int source) {
            return selected[source] == null ? -1 : selected[source].state.root();
        }

        double cost(int source) {
            return selected[source] == null ? Double.POSITIVE_INFINITY : selected[source].state.cost();
        }

        double length(int source) {
            return selected[source] == null ? 0D : selected[source].state.length();
        }

        int[] contributions(int source) {
            int count = 0;
            for (Label label = selected[source]; label != null; label = label.downstream) {
                count++;
            }
            int[] contributions = new int[count];
            int index = 0;
            for (Label label = selected[source]; label != null; label = label.downstream) {
                contributions[index++] = accumulation[label.state.id()];
            }
            return contributions;
        }
    }

    static final class Label {
        private final LabelState state;
        private final Label downstream;
        private boolean active = true;

        Label(LabelState state, Label downstream) {
            this.state = state;
            this.downstream = downstream;
        }
    }

    record LabelState(int id, int index, int requiredHead, double cost, double length, int root) {
    }
}
