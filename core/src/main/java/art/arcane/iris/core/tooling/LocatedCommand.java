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

package art.arcane.iris.core.tooling;

import com.volmit.host.CommandRegistration;
import com.volmit.tooling.Argument;
import com.volmit.tooling.Command;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * The command the leading words of a line name below a module's root, and the words left over as its arguments.
 * A word that names a sub-command always selects it.
 */
record LocatedCommand(CommandRegistration root, Command command, List<String> path, List<String> arguments) {
    static Optional<LocatedCommand> locate(List<CommandRegistration> roots, List<String> words) {
        Optional<CommandRegistration> root = roots.stream()
                .filter(candidate -> candidate.command().answersTo(words.getFirst()))
                .findFirst();
        if (root.isEmpty()) {
            return Optional.empty();
        }
        Command command = root.get().command();
        List<String> path = new ArrayList<>(List.of(command.name()));
        int consumed = 1;
        while (consumed < words.size()) {
            Optional<Command> child = childAnsweringTo(command, words.get(consumed));
            if (child.isEmpty()) {
                break;
            }
            command = child.get();
            path.add(command.name());
            consumed++;
        }
        return Optional.of(new LocatedCommand(root.get(), command, path, words.subList(consumed, words.size())));
    }

    String pathText() {
        return String.join(" ", path);
    }

    String usage() {
        return usageOf(path, command);
    }

    static String usageOf(List<String> path, Command command) {
        StringBuilder usage = new StringBuilder(String.join(" ", path));
        for (Argument<?> argument : command.arguments()) {
            usage.append(argument.isOptional() ? " [" : " <").append(argument.name()).append(argument.isOptional() ? "]" : ">");
        }
        return usage.toString();
    }

    private static Optional<Command> childAnsweringTo(Command command, String word) {
        return command.children().stream().filter(child -> child.answersTo(word)).findFirst();
    }
}
