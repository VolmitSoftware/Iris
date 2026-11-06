package com.volmit.tooling;

import java.util.List;

/**
 * A file as nodes, each at the place in the file it is written: trees, each drawn growing from its root, such as an
 * object with its parts, and its materials beside it.
 */
public record NodeGraph(List<GraphNode> roots) {
    public NodeGraph {
        roots = List.copyOf(roots);
    }
}
