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

import art.arcane.iris.localization.IrisMessages;
import art.arcane.iris.localization.IrisLanguage;
import art.arcane.iris.modded.localization.ModdedCommandMessages;
import art.arcane.iris.localization.RuntimeUiMessages;
import art.arcane.iris.generation.runtime.Engine;
import art.arcane.volmlib.util.localization.MessageArgument;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.DimensionArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;

final class ModdedPregenCommands {
    private ModdedPregenCommands() {
    }

    static int pregenStart(CommandContext<CommandSourceStack> context, boolean withDimension, boolean withCenter, boolean gui, boolean sync, boolean nocache) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        int radius = IntegerArgumentType.getInteger(context, "radius");
        int centerX = withCenter ? IntegerArgumentType.getInteger(context, "x") : 0;
        int centerZ = withCenter ? IntegerArgumentType.getInteger(context, "z") : 0;
        ServerLevel level = withDimension ? DimensionArgument.getDimension(context, "dimension") : source.getLevel();
        Engine engine = IrisModdedCommands.engineFor(level);
        if (engine == null) {
            if (withDimension) {
                IrisModdedCommands.fail(source, IrisLanguage.plain(ModdedCommandMessages.IRIS_MODDED_COMMANDS_IS_NOT_GENERATED_BY_IRIS_SEE_IRIS_INFO_LOADED_IRIS, MessageArgument.untrusted("value", level.dimension().identifier())));
            } else {
                IrisModdedCommands.fail(source, IrisLanguage.plain(ModdedCommandMessages.IRIS_MODDED_COMMANDS_CURRENT_DIMENSION_IS_NOT_GENERATED_BY_IRIS_NAME_ONE_EXPLICITLY, MessageArgument.untrusted("value", level.dimension().identifier()), MessageArgument.untrusted("radius", radius)));
            }
            return 0;
        }
        boolean showGui = gui && ModdedGuiHost.isGuiLaunchable();
        boolean started;
        try {
            started = ModdedPregenJob.start(source.getServer(), level, engine, radius, centerX, centerZ, showGui, sync, !nocache);
        } catch (IllegalArgumentException failure) {
            IrisModdedCommands.fail(source, failure.getMessage());
            return 0;
        }
        if (!started) {
            IrisModdedCommands.fail(source, IrisLanguage.plain(IrisMessages.PREGEN_ALREADY_RUNNING));
            return 0;
        }
        ModdedPregenBossBar.begin(source.getPlayer());
        String guiNote;
        if (!gui) {
            guiNote = "";
        } else if (showGui) {
            guiNote = " A progress map window is opening on the server display.";
        } else {
            guiNote = " (GUI requested but unavailable: " + ModdedGuiHost.guiUnavailableReason() + ")";
        }
        String modeNote = " Mode: " + (sync ? "sync" : "async") + (nocache ? ", cache disabled." : ", resumable (checkpoint cache).");
        IrisModdedCommands.ok(source, IrisLanguage.plain(ModdedCommandMessages.IRIS_MODDED_COMMANDS_PREGEN_STARTED_BY_BLOCKS_FROM_PROGRESS_LOGS_CONSOLE_SEE_IRIS, MessageArgument.untrusted("value", level.dimension().identifier()), MessageArgument.untrusted("value2", (radius * 2)), MessageArgument.untrusted("value3", (radius * 2)), MessageArgument.untrusted("centerX", centerX), MessageArgument.untrusted("centerZ", centerZ), MessageArgument.untrusted("modeNote", modeNote), MessageArgument.untrusted("guiNote", guiNote)));
        return 1;
    }

    static int pregenStop(CommandSourceStack source) {
        if (ModdedPregenJob.stop()) {
            ModdedPregenBossBar.clear();
            IrisModdedCommands.ok(source, IrisLanguage.plain(ModdedCommandMessages.IRIS_MODDED_COMMANDS_STOPPING_PREGENERATION_FINISHING_UP_CURRENT_REGION));
            return 1;
        }
        IrisModdedCommands.fail(source, IrisLanguage.plain(ModdedCommandMessages.IRIS_MODDED_COMMANDS_NO_ACTIVE_PREGENERATION_TASK_STOP));
        return 0;
    }

    static int pregenPause(CommandSourceStack source) {
        Boolean paused = ModdedPregenJob.pauseResume();
        if (paused == null) {
            IrisModdedCommands.fail(source, IrisLanguage.plain(ModdedCommandMessages.IRIS_MODDED_COMMANDS_NO_ACTIVE_PREGENERATION_TASK_PAUSE_RESUME));
            return 0;
        }
        IrisModdedCommands.ok(source, IrisLanguage.plain(ModdedCommandMessages.IRIS_MODDED_COMMANDS_PREGENERATION_IS_NOW, MessageArgument.trusted("value", IrisLanguage.plain(paused.booleanValue() ? RuntimeUiMessages.STATUS_PAUSED_LOWER : RuntimeUiMessages.STATUS_RUNNING_LOWER))));
        return 1;
    }

    static int pregenStatus(CommandSourceStack source) {
        Component status = ModdedPregenJob.statusComponent();
        if (status == null) {
            IrisModdedCommands.fail(source, IrisLanguage.plain(ModdedCommandMessages.IRIS_MODDED_COMMANDS_NO_ACTIVE_PREGENERATION_TASK));
            return 0;
        }
        IrisModdedCommands.ok(source, status);
        return 1;
    }
}
