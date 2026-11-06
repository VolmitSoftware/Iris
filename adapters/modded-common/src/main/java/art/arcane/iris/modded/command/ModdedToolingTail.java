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
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * The tail of a command node that hands the rest of the line to the commands modules register
 * ({@link ToolingCommands}). It is a greedy string next to the node's literals, and Brigadier takes a literal that matches
 * before any argument, so a literal command always wins over a module's.
 */
final class ModdedToolingTail {
    private static final String ARGUMENT_NAME = "tooling";
    private static final int COMMAND_ACCEPTED = 1;
    private static final ToolingCommands TOOLING = ToolingCommands.forBoundPlatform();

    private ModdedToolingTail() {
    }

    /** A tail for the node reached after {@code leadingWords}, which the dispatcher puts before the words the sender types. */
    static RequiredArgumentBuilder<CommandSourceStack, String> after(String... leadingWords) {
        List<String> leading = List.of(leadingWords);
        return Commands.argument(ARGUMENT_NAME, StringArgumentType.greedyString())
                .suggests((CommandContext<CommandSourceStack> context, SuggestionsBuilder builder) -> suggest(context.getSource(), leading, builder))
                .executes((CommandContext<CommandSourceStack> context) -> run(
                        context.getSource(), leading, StringArgumentType.getString(context, ARGUMENT_NAME)));
    }

    private static int run(CommandSourceStack source, List<String> leading, String typed) {
        List<String> words = new ArrayList<>(leading);
        words.addAll(Arrays.stream(typed.trim().split("\\s+")).filter(word -> !word.isEmpty()).toList());
        TOOLING.executeOffThread(new ModdedToolingSender(source), words);
        return COMMAND_ACCEPTED;
    }

    private static CompletableFuture<Suggestions> suggest(CommandSourceStack source, List<String> leading, SuggestionsBuilder builder) {
        ModdedCommandFeedback.tab(source);
        String typed = builder.getRemaining();
        List<String> words = new ArrayList<>(leading);
        words.addAll(Arrays.asList(typed.split(" ", -1)));
        SuggestionsBuilder lastWord = builder.createOffset(builder.getStart() + typed.lastIndexOf(' ') + 1);
        TOOLING.complete(new ModdedToolingSender(source), words).forEach(lastWord::suggest);
        return lastWord.buildFuture();
    }
}
