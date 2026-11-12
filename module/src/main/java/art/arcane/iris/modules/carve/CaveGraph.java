/*
 * Iris is a World Generator for Minecraft Servers
 * Copyright (c) 2026 Arcane Arts (Volmit Software)
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

package art.arcane.iris.modules.carve;

import art.arcane.iris.modules.carve.field.CavePart;
import com.volmit.tooling.GraphNode;
import com.volmit.tooling.GraphNode.Edits;
import com.volmit.tooling.GraphNode.Link;
import com.volmit.tooling.GraphNode.NodeSettings;
import com.volmit.tooling.JsonPath;
import com.volmit.tooling.JsonText;
import com.volmit.tooling.NodeGraph;

import java.util.List;
import java.util.OptionalInt;
import java.util.Set;

/**
 * A cave file as nodes: the cave with its depth and surface rules, its tunnels, and its caverns with their pillars.
 * Tunnels and caverns always have a node, written or not, since setting a value of one writes it into the file.
 */
final class CaveGraph {
    private static final String TUNNEL = "tunnel";
    private static final String CAVERN = "cavern";
    private static final String PILLARS = "pillars";
    private static final String UNWRITTEN = " (unset)";
    private static final String HOLDS = "";
    private static final Set<String> NO_NODES = Set.of();

    private CaveGraph() {
    }

    /**
     * @param read what the pack read from the file, which tells which parts the scene draws; it may be older than {@code text}
     * @throws IllegalArgumentException when the file does not hold an object
     */
    static NodeGraph of(String name, JsonText text, Cave read) {
        if (!(text.root() instanceof JsonText.Obj root)) {
            throw new IllegalArgumentException("The file does not hold an object");
        }
        JsonPath cavernPath = JsonPath.ROOT.member(CAVERN);
        GraphNode tunnel = partNode("Tunnel", JsonPath.ROOT.member(TUNNEL), root.get(TUNNEL).isPresent(), read.tunnel().isPresent(),
                CavePart.TUNNEL, new NodeSettings("Tunnel", JsonPath.ROOT.member(TUNNEL), CaveTunnel.class, NO_NODES), List.of());
        GraphNode pillars = new GraphNode("Pillars", titled("Pillars", hasMember(root, CAVERN, PILLARS)), cavernPath.member(PILLARS),
                OptionalInt.empty(), List.of(), List.of(new NodeSettings("Pillars", cavernPath.member(PILLARS), CavePillars.class, NO_NODES)),
                List.of(), Edits.NONE);
        GraphNode cavern = partNode("Cavern", cavernPath, root.get(CAVERN).isPresent(), read.cavern().isPresent(), CavePart.CAVERN,
                new NodeSettings("Cavern", cavernPath, CaveCavern.class, Set.of(PILLARS)), List.of(new Link(HOLDS, pillars)));
        GraphNode cave = new GraphNode("Cave", name, JsonPath.ROOT, OptionalInt.empty(), List.of(),
                List.of(new NodeSettings("Cave", JsonPath.ROOT, Cave.class, Set.of(TUNNEL, CAVERN))),
                List.of(new Link(HOLDS, tunnel), new Link(HOLDS, cavern)), Edits.NONE);
        return new NodeGraph(List.of(cave));
    }

    private static GraphNode partNode(String kind, JsonPath path, boolean written, boolean drawn, CavePart part,
                                      NodeSettings settings, List<Link> children) {
        OptionalInt drawnPart = drawn ? OptionalInt.of(part.ordinal()) : OptionalInt.empty();
        return new GraphNode(kind, titled(kind, written), path, drawnPart, List.of(), List.of(settings), children, Edits.NONE);
    }

    private static String titled(String kind, boolean written) {
        return written ? kind : kind + UNWRITTEN;
    }

    private static boolean hasMember(JsonText.Obj root, String member, String inner) {
        return root.get(member).orElse(null) instanceof JsonText.Obj object && object.get(inner).isPresent();
    }
}
