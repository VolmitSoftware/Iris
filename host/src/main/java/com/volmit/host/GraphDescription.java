package com.volmit.host;

import com.volmit.mantle.MantleKey;
import com.volmit.resource.ResourceId;
import com.volmit.stage.PackReach;
import com.volmit.stage.StageKind;
import java.util.List;
import java.util.stream.Collectors;

/** Text rendering of a resolved graph for pack authors and logs. */
final class GraphDescription {
    private static final String INDENT = "  ";
    private static final String DETAIL_INDENT = "      ";

    private GraphDescription() {}

    static String describe(ResolvedGraph graph) {
        var text = new StringBuilder();
        var passes = graph.blockPasses();
        text.append(graph.pipeline()).append(" pipeline: ")
                .append(graph.stages().size()).append(" stages, ")
                .append(passes.size()).append(passes.size() == 1 ? " block pass\n" : " block passes\n");
        appendOrder(text, graph, passes);
        appendBlockPasses(text, passes);
        appendUnorderedPairs(text, graph.unorderedPairs());
        return text.toString();
    }

    private static void appendOrder(StringBuilder text, ResolvedGraph graph, List<List<ResourceId>> passes) {
        text.append("order:\n");
        var stages = graph.stages();
        for (int position = 0; position < stages.size(); position++) {
            var stage = stages.get(position);
            text.append(INDENT).append(position + 1).append(". ").append(stage.stage().id())
                    .append(" [").append(kindLabel(stage, passes)).append("] module ").append(stage.module());
            stage.settings().ifPresent(settings -> text.append(" when ").append(settings.id()));
            text.append('\n');
            appendEdges(text, stage.incoming(), StageEdge.Kind.REQUIRED, "requires ");
            appendEdges(text, stage.incoming(), StageEdge.Kind.SOFT, "soft after ");
            appendReach(text, stage.reach());
            if (!stage.absentSoftTargets().isEmpty()) {
                text.append(DETAIL_INDENT).append("absent soft targets ").append(joined(stage.absentSoftTargets())).append('\n');
            }
        }
    }

    private static void appendReach(StringBuilder text, StageReach reach) {
        if (reach.equals(StageReach.NONE)) {
            return;
        }
        text.append(DETAIL_INDENT).append("reach ").append(reachText(reach.blocks()));
        if (!reach.mantleLayers().isEmpty()) {
            text.append(", writes mantle ").append(joined(reach.mantleLayers().stream().map(MantleKey::id).toList()));
        }
        text.append('\n');
    }

    private static String reachText(PackReach reach) {
        if (!(reach instanceof FixedReach fixed)) {
            return "worked out when the pack loads";
        }
        return fixed.blocks() + (fixed.blocks() == 1 ? " block" : " blocks");
    }

    private static String kindLabel(ResolvedStage stage, List<List<ResourceId>> passes) {
        if (stage.stage().kind() == StageKind.INVOCATION) {
            return "invocation";
        }
        for (int pass = 0; pass < passes.size(); pass++) {
            if (passes.get(pass).contains(stage.stage().id())) {
                return "block pass " + (pass + 1);
            }
        }
        throw new IllegalStateException("Block stage outside every pass: " + stage.stage());
    }

    private static void appendEdges(StringBuilder text, List<StageEdge> edges, StageEdge.Kind kind, String label) {
        var sources = edges.stream().filter(edge -> edge.kind() == kind).map(StageEdge::from).toList();
        if (sources.isEmpty()) {
            return;
        }
        text.append(DETAIL_INDENT).append(label).append(joined(sources)).append('\n');
    }

    private static void appendBlockPasses(StringBuilder text, List<List<ResourceId>> passes) {
        text.append("block passes:\n");
        if (passes.isEmpty()) {
            text.append(INDENT).append("none\n");
        }
        for (int pass = 0; pass < passes.size(); pass++) {
            text.append(INDENT).append("pass ").append(pass + 1).append(": ").append(joined(passes.get(pass))).append('\n');
        }
    }

    private static void appendUnorderedPairs(StringBuilder text, List<ResolvedGraph.UnorderedPair> pairs) {
        text.append("mutually unordered:\n");
        if (pairs.isEmpty()) {
            text.append(INDENT).append("none\n");
        }
        for (var pair : pairs) {
            text.append(INDENT).append(pair.first()).append(" <-> ").append(pair.second()).append('\n');
        }
    }

    private static String joined(List<?> values) {
        return values.stream().map(Object::toString).collect(Collectors.joining(", "));
    }
}
