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

import com.volmit.tooling.GraphNode;
import com.volmit.tooling.GraphNode.Edits;
import com.volmit.tooling.GraphNode.NodeSettings;
import com.volmit.tooling.JsonPath;
import com.volmit.tooling.JsonText;
import com.volmit.tooling.NodeGraph;

import java.util.List;
import java.util.OptionalInt;
import java.util.Set;

/**
 * A cave file as one node of settings, a section each for the cave, its tunnels, its caverns and their pillars. Every
 * section is there, written or not, since setting a value in one writes it into the file.
 */
final class CaveGraph {
    private static final String TUNNEL = "tunnel";
    private static final String CAVERN = "cavern";
    private static final String PILLARS = "pillars";
    private static final String UNWRITTEN = " (unset)";
    private static final Set<String> NONE_ELSEWHERE = Set.of();

    private CaveGraph() {
    }

    /** @throws IllegalArgumentException when the file does not hold an object */
    static NodeGraph of(String name, JsonText text) {
        if (!(text.root() instanceof JsonText.Obj root)) {
            throw new IllegalArgumentException("The file does not hold an object");
        }
        JsonPath tunnelPath = JsonPath.ROOT.member(TUNNEL);
        JsonPath cavernPath = JsonPath.ROOT.member(CAVERN);
        JsonPath pillarsPath = cavernPath.member(PILLARS);
        List<NodeSettings> sections = List.of(
                new NodeSettings("Cave", JsonPath.ROOT, Cave.class, Set.of(TUNNEL, CAVERN)),
                new NodeSettings(titled("Tunnel", root.get(TUNNEL).isPresent()), tunnelPath, CaveTunnel.class, NONE_ELSEWHERE),
                new NodeSettings(titled("Cavern", root.get(CAVERN).isPresent()), cavernPath, CaveCavern.class, Set.of(PILLARS)),
                new NodeSettings(titled("Pillars", hasMember(root, CAVERN, PILLARS)), pillarsPath, CavePillars.class, NONE_ELSEWHERE));
        GraphNode cave = new GraphNode("Cave", name, JsonPath.ROOT, OptionalInt.empty(), List.of(), sections, List.of(), Edits.NONE);
        return new NodeGraph(List.of(cave));
    }

    private static String titled(String section, boolean written) {
        return written ? section : section + UNWRITTEN;
    }

    private static boolean hasMember(JsonText.Obj root, String member, String inner) {
        return root.get(member).orElse(null) instanceof JsonText.Obj object && object.get(inner).isPresent();
    }
}
