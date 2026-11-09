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

package art.arcane.iris.core.structure.tooling;

import com.volmit.tooling.Command;

import java.util.Optional;
import java.util.function.Supplier;

/**
 * The {@code jigsaw} command tree: the Jigsaw Studio (create, open, close, edit and export a jigsaw structure in a tool
 * world), and the {@code import} and {@code capture} that turn the platform's structures into jigsaw structures. Platforms
 * mount it below their module branch, so the studio commands run as {@code /iris module jigsaw ...}.
 */
public final class JigsawCommands {
    private static final String ROOT_NAME = "jigsaw";

    private JigsawCommands() {
    }

    /**
     * The tree.
     *
     * @param hosts the studio host of the server, which is empty on a platform that runs no studio; read when a command
     *              runs, so a host that binds late is found
     */
    public static Command root(Supplier<Optional<JigsawStudioHost>> hosts) {
        StudioLifecycleCommands lifecycle = new StudioLifecycleCommands(hosts);
        GraphEditCommands graph = new GraphEditCommands(hosts);
        Command root = Command.named(ROOT_NAME).alias("jig", "jgs").describe("Iris Jigsaw Studio tools");
        for (Command command : lifecycle.commands()) {
            root = root.child(command);
        }
        return root
                .child(new JigsawExportCommand(hosts).command())
                .child(new AdoptionCommands(hosts).command())
                .child(graph.connector())
                .child(graph.pool())
                .child(graph.piece())
                .child(graph.workcell())
                .child(graph.variant())
                .child(graph.rules())
                .child(graph.preview())
                .child(ImportCommands.importAll())
                .child(ImportCommands.capture());
    }
}
