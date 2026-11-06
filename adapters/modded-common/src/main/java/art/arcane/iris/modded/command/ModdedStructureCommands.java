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

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;

/**
 * The {@code /iris structure} literals that only explain the Bukkit-only import and capture. The structure module's own
 * commands ({@code list}, {@code info}, {@code verify}) are under {@code /iris module structure}.
 */
public final class ModdedStructureCommands {
    private static final String IMPORT_EXPLANATION = "Structure import rebuilds vanilla & datapack structures as editable Iris resources through Bukkit/NMS template managers; run /iris structure import on a Bukkit server against this pack, then copy the pack folder over.";
    private static final String CAPTURE_EXPLANATION = "Structure capture generates each structure in a throwaway Bukkit scratch world to read its blocks; it requires the Bukkit plugin (v26 NMS binding).";

    private ModdedStructureCommands() {
    }

    public static LiteralArgumentBuilder<CommandSourceStack> tree(String name) {
        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal(name).requires(ModdedCommandTree.GATE);

        root.executes((CommandContext<CommandSourceStack> context) -> ModdedCommandHelp.send(context.getSource(), name));

        root.then(message("import", IMPORT_EXPLANATION));
        root.then(message("import-all", IMPORT_EXPLANATION));
        root.then(message("reimport", IMPORT_EXPLANATION));
        root.then(message("imp", IMPORT_EXPLANATION));
        root.then(message("all", IMPORT_EXPLANATION));
        root.then(message("capture", CAPTURE_EXPLANATION));
        root.then(message("cap", CAPTURE_EXPLANATION));

        return root;
    }

    private static LiteralArgumentBuilder<CommandSourceStack> message(String name, String text) {
        return Commands.literal(name)
                .executes((CommandContext<CommandSourceStack> context) -> {
                    IrisModdedCommands.fail(context.getSource(), text);
                    return 0;
                })
                .then(Commands.argument("args", StringArgumentType.greedyString())
                        .executes((CommandContext<CommandSourceStack> context) -> {
                            IrisModdedCommands.fail(context.getSource(), text);
                            return 0;
                        }));
    }
}
