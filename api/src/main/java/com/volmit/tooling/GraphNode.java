package com.volmit.tooling;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * One node of a {@link NodeGraph}: a JSON object of the file, shown as a box of its settings, with the nodes it holds.
 *
 * @param kind       what it is, such as {@code Stem}; nodes of one kind are drawn alike
 * @param title      what its box is headed, such as the object's name
 * @param path       where it is written; a node in an array can be duplicated and deleted, the root can't be deleted
 * @param partId     the part of the scene it draws, so choosing one chooses the other; {@link #NO_PART} for none
 * @param roles      the block roles it places or decorates, shown as tags
 * @param settings   the objects whose settings its box shows, its own first
 * @param children   the nodes it holds, each with what the wire to it means
 * @param childList  the array a node moved under this one is added to; empty when nothing can be moved here
 * @param movable    whether it can be moved under another node with a child list
 * @param additions  the nodes that can be added under it
 */
public record GraphNode(String kind, String title, JsonPath path, int partId, List<String> roles, List<NodeSettings> settings,
                        List<Link> children, Optional<JsonPath> childList, boolean movable, List<Addition> additions) {
    public static final int NO_PART = -1;

    public GraphNode {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(title, "title");
        Objects.requireNonNull(path, "path");
        Objects.requireNonNull(childList, "childList");
        roles = List.copyOf(roles);
        settings = List.copyOf(settings);
        children = List.copyOf(children);
        additions = List.copyOf(additions);
        if (movable && !path.isElement()) {
            throw new IllegalArgumentException("Only a node in an array can move, not the one at " + path);
        }
    }

    /** A node held by another, and what holding it means, such as {@code each copy}; empty for plain growing. */
    public record Link(String wire, GraphNode node) {
        public Link {
            Objects.requireNonNull(wire, "wire");
            Objects.requireNonNull(node, "node");
        }
    }

    /**
     * The settings of the JSON object at {@code path}, which the pack reads into {@code type}, so the studio shows them
     * as the pack's schema describes that type. Members in {@code shownAsNodes} are left out, as they are nodes of
     * their own. The object may not be written yet; setting a value writes it.
     */
    public record NodeSettings(String title, JsonPath path, Class<?> type, Set<String> shownAsNodes) {
        public NodeSettings {
            Objects.requireNonNull(title, "title");
            Objects.requireNonNull(path, "path");
            Objects.requireNonNull(type, "type");
            shownAsNodes = Set.copyOf(shownAsNodes);
        }
    }

    /** A node that can be added: {@code literal} appended to the array at {@code list}, which is written when missing. */
    public record Addition(String label, JsonPath list, String literal) {
        public Addition {
            Objects.requireNonNull(label, "label");
            Objects.requireNonNull(list, "list");
            if (!JsonText.isValue(literal)) {
                throw new IllegalArgumentException("Node " + label + " starts as " + literal + ", which is not JSON");
            }
        }
    }
}
