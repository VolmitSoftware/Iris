/*
 * Iris is a World Generator for Minecraft Bukkit Servers
 * Copyright (c) 2022 Arcane Arts (Volmit Software)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package art.arcane.iris.engine.procedural.part;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * The part tree of a procedural object as a viewer lists it, with an id per node that {@link PartTrace} tags blocks
 * with. A part that copies another, like branches, shares its node with the copied part, so the tree reads one level
 * shallower than the settings. Ids number the nodes in order, so they stay the same across seeds and variants.
 */
public final class PartOutline {
    public static final int NO_PART = -1;

    /**
     * One row of the tree.
     *
     * @param shape  what the part is, like {@code Branches ×12 · Stem}
     * @param detail the role its blocks get, and whether it carves them away instead
     */
    public record Node(int id, String shape, String detail, List<Node> children) {
    }

    private final Map<ProceduralPart, Integer> idsByPart = new IdentityHashMap<>();
    private final List<Node> roots;
    private int nodeCount;

    private PartOutline(ProceduralObject definition) {
        this.roots = nodesFor(definition.parts(), definition.defaultRole());
    }

    public static PartOutline of(ProceduralObject definition) {
        return new PartOutline(definition);
    }

    public List<Node> roots() {
        return roots;
    }

    public int nodeCount() {
        return nodeCount;
    }

    public int idOf(ProceduralPart part) {
        return idsByPart.getOrDefault(part, NO_PART);
    }

    private List<Node> nodesFor(List<ProceduralPart> parts, String parentRole) {
        List<Node> nodes = new ArrayList<>();
        for (ProceduralPart part : parts) {
            if (part != null) {
                nodes.add(nodeFor(part, parentRole));
            }
        }
        return nodes;
    }

    // Children follow the order the grower draws them in: the copies' own children first, then the part's.
    private Node nodeFor(ProceduralPart part, String parentRole) {
        int id = nodeCount++;
        idsByPart.put(part, id);
        String role = roleOf(part, parentRole);
        ProceduralPart copied = copiedPart(part);
        List<Node> children = new ArrayList<>();
        String shape;
        String detail;
        if (copied != null && copiedPart(copied) == null) {
            idsByPart.put(copied, id);
            String copiedRole = roleOf(copied, role);
            shape = copiesLabel(part) + " · " + shapeName(copied);
            detail = detailOf(copied, copiedRole);
            children.addAll(nodesFor(copied.children(), copiedRole));
        } else {
            shape = copied != null ? copiesLabel(part) : shapeName(part);
            detail = detailOf(part, role);
            if (copied != null) {
                children.add(nodeFor(copied, role));
            }
        }
        children.addAll(nodesFor(part.children(), role));
        return new Node(id, shape, detail, List.copyOf(children));
    }

    private static String roleOf(ProceduralPart part, String parentRole) {
        return part.role() == null || part.role().isBlank() ? parentRole : part.role();
    }

    private static ProceduralPart copiedPart(ProceduralPart part) {
        if (part.branches() != null) {
            return part.branches().part();
        }
        return part.cluster() != null ? part.cluster().part() : null;
    }

    private static String copiesLabel(ProceduralPart part) {
        if (part.branches() != null) {
            return part.branches().chance() != null ? "Branches by chance" : "Branches ×" + part.branches().count().summary();
        }
        return "Cluster ×" + part.cluster().count().summary();
    }

    private static String shapeName(ProceduralPart part) {
        if (part.stem() != null) {
            return "Stem";
        }
        if (part.blob() != null) {
            return "Blob";
        }
        return "Group";
    }

    // Carving places no blocks, so it is named to explain why nothing lights up.
    private static String detailOf(ProceduralPart part, String role) {
        return part.mode() == PartMode.CARVE ? role + " · carve" : role;
    }
}
