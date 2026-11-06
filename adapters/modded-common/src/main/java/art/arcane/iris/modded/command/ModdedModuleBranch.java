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

package art.arcane.iris.modded.command;

import art.arcane.iris.core.tooling.ToolingCommands;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * {@code /iris module <root> <command...>}: the one branch that hands its words to the commands modules register
 * ({@link ToolingCommands}). The roots belong to the modules of whichever pack the sender resolves to when the line runs,
 * so the branch cannot describe them as Brigadier nodes when the tree is built; it takes the rest of the line as one
 * greedy string. The dispatcher decides who may run what, so the literal has no permission of its own.
 */
final class ModdedModuleBranch {
    private static final String ARGUMENT_NAME = "command";
    private static final int COMMAND_ACCEPTED = 1;
    private static final ToolingCommands TOOLING = ToolingCommands.forBoundPlatform();

    private ModdedModuleBranch() {
    }

    static LiteralArgumentBuilder<CommandSourceStack> tree(String literal) {
        return Commands.literal(literal)
                .executes((CommandContext<CommandSourceStack> context) -> run(context.getSource(), ""))
                .then(Commands.argument(ARGUMENT_NAME, StringArgumentType.greedyString())
                        .suggests((CommandContext<CommandSourceStack> context, SuggestionsBuilder builder) ->
                                suggest(context.getSource(), builder))
                        .executes((CommandContext<CommandSourceStack> context) ->
                                run(context.getSource(), StringArgumentType.getString(context, ARGUMENT_NAME))));
    }

    private static int run(CommandSourceStack source, String typed) {
        List<String> words = Arrays.stream(typed.trim().split("\\s+")).filter(word -> !word.isEmpty()).toList();
        TOOLING.executeOffThread(new ModdedToolingSender(source), words);
        return COMMAND_ACCEPTED;
    }

    private static CompletableFuture<Suggestions> suggest(CommandSourceStack source, SuggestionsBuilder builder) {
        ModdedCommandFeedback.tab(source);
        String typed = builder.getRemaining();
        List<String> words = Arrays.asList(typed.split(" ", -1));
        SuggestionsBuilder lastWord = builder.createOffset(builder.getStart() + typed.lastIndexOf(' ') + 1);
        TOOLING.complete(new ModdedToolingSender(source), words).forEach(lastWord::suggest);
        return lastWord.buildFuture();
    }
}
