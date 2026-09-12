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

package art.arcane.iris.command;

import art.arcane.iris.localization.IrisMessages;
import art.arcane.iris.Iris;
import art.arcane.iris.studio.view.PregeneratorJob;
import art.arcane.iris.world.pregen.PregenTask;
import art.arcane.iris.world.IrisToolbelt;
import art.arcane.volmlib.util.director.annotations.Director;
import art.arcane.volmlib.util.director.annotations.Param;
import art.arcane.iris.localization.C;
import art.arcane.volmlib.util.format.Form;
import art.arcane.volmlib.util.math.Position2;
import org.bukkit.World;
import org.bukkit.util.Vector;

import art.arcane.iris.localization.IrisLanguage;
import art.arcane.iris.localization.BukkitCommandMessagesExtended;
import art.arcane.iris.localization.RuntimeUiMessages;
import art.arcane.volmlib.util.localization.MessageArgument;
@Director(name = "pregen", aliases = "pregenerate", description = "Pregenerate your Iris worlds!", descriptionKey = "iris.director.commandpregen.director.pregenerate_your_iris_worlds")
public class CommandPregen implements DirectorExecutor {
    @Director(description = "Pregenerate a world", descriptionKey = "iris.director.commandpregen.director.pregenerate_world")
    public void start(
            @Param(description = "The radius of the pregen in blocks", descriptionKey = "iris.director.commandpregen.param.radius_pregen_blocks", aliases = "size")
            int radius,
            @Param(description = "The world to pregen", descriptionKey = "iris.director.commandpregen.param.world_pregen", contextual = true, contextualOverride = true)
            World world,
            @Param(aliases = "middle", description = "The center location of the pregen. Use \"me\" for your current location", descriptionKey = "iris.director.commandpregen.param.center_location_pregen_use_me_your_current_location", defaultValue = "0,0")
            Vector center,
            @Param(description = "Open the Iris pregen gui", descriptionKey = "iris.director.commandpregen.param.open_iris_pregen_gui", defaultValue = "true")
            boolean gui,
            @Param(name = "serial", description = "Generate only one chunk at a time", descriptionKey = "iris.director.commandpregen.param.generate_only_one_chunk_at_time", defaultValue = "false")
            boolean serial
            ) {
        if (radius <= 0) {
            sender().sendMessage(IrisLanguage.text(BukkitCommandMessagesExtended.COMMAND_PREGEN_PREGEN_RADIUS_MUST_BE_GREATER_THAN_ZERO_BLOCKS));
            return;
        }
        if (serial && !IrisToolbelt.supportsStrictSerialPregeneration()) {
            sender().sendMessage(IrisLanguage.text(BukkitCommandMessagesExtended.COMMAND_PREGEN_STRICT_SERIAL_PREGENERATION_REQUIRES_PAPER_PAPER_COMPATIBLE_SERVER));
            return;
        }

        if (PregeneratorJob.getInstance() != null) {
            // Reject like the modded adapter does; never silently kill a running job.
            sender().sendMessage(IrisLanguage.text(IrisMessages.PREGEN_ALREADY_RUNNING));
            return;
        }

        try {
            if (sender().isPlayer() && access() == null) {
                sender().sendMessage(IrisLanguage.text(BukkitCommandMessagesExtended.COMMAND_PREGEN_ENGINE_ACCESS_THIS_WORLD_IS_NULL));
                sender().sendMessage(IrisLanguage.text(BukkitCommandMessagesExtended.COMMAND_PREGEN_PLEASE_MAKE_SURE_WORLD_IS_LOADED_ENGINE_IS_INITIALIZED_GENERATE));
            }
            PregenTask task = PregenTask
                    .builder()
                    .center(new Position2(center.getBlockX(), center.getBlockZ()))
                    .gui(gui)
                    .radiusX(radius)
                    .radiusZ(radius)
                    .build();
            if (serial) {
                IrisToolbelt.pregenerateSerial(task, world);
            } else {
                IrisToolbelt.pregenerate(task, world);
            }
            String msg = C.GREEN + "Pregen started in " + C.GOLD + world.getName() + C.GREEN + " of " + C.GOLD + (radius * 2) + C.GREEN + " by " + C.GOLD + (radius * 2) + C.GREEN + " blocks from " + C.GOLD + center.getX() + "," + center.getZ();
            sender().sendMessage(msg);
            Iris.info(msg);
        } catch (Throwable e) {
            sender().sendMessage(IrisLanguage.text(BukkitCommandMessagesExtended.COMMAND_PREGEN_FAILED_START_PREGENERATION_SEE_CONSOLE_DETAILS));
            Iris.reportError(e);
        }
    }

    @Director(description = "Stop the active pregeneration task", descriptionKey = "iris.director.commandpregen.director.stop_active_pregeneration_task", aliases = "x")
    public void stop() {
        if (PregeneratorJob.shutdownInstance()) {
            String message = C.BLUE + "Pregen stop requested; finishing active work before cancellation.";
            sender().sendMessage(message);
            Iris.info(message);
        } else {
            sender().sendMessage(IrisLanguage.text(BukkitCommandMessagesExtended.COMMAND_PREGEN_NO_ACTIVE_PREGENERATION_TASKS_STOP));
        }
    }

    @Director(description = "Pause / continue the active pregeneration task", descriptionKey = "iris.director.commandpregen.director.pause_continue_active_pregeneration_task", aliases = {"resume"})
    public void pause() {
        if (PregeneratorJob.pauseResume()) {
            sender().sendMessage(IrisLanguage.text(BukkitCommandMessagesExtended.COMMAND_PREGEN_PAUSED_UNPAUSED_PREGENERATION_TASK_NOW, MessageArgument.trusted("value", IrisLanguage.text(PregeneratorJob.isPaused() ? RuntimeUiMessages.STATUS_PAUSED : RuntimeUiMessages.STATUS_RUNNING))));
        } else {
            sender().sendMessage(IrisLanguage.text(BukkitCommandMessagesExtended.COMMAND_PREGEN_NO_ACTIVE_PREGENERATION_TASKS_PAUSE_UNPAUSE));
        }
    }

    @Director(description = "Show the active pregeneration status", descriptionKey = "iris.director.commandpregen.director.show_active_pregeneration_status")
    public void status() {
        PregeneratorJob.PregenProgress progress = PregeneratorJob.progressSnapshot();
        if (progress == null) {
            sender().sendMessage(IrisLanguage.text(BukkitCommandMessagesExtended.COMMAND_PREGEN_NO_ACTIVE_PREGENERATION_TASK));
            return;
        }

        String world = progress.worldName() == null ? "?" : progress.worldName();
        sender().sendMessage(IrisLanguage.text(BukkitCommandMessagesExtended.COMMAND_PREGEN_PREGEN, MessageArgument.untrusted("world", world), MessageArgument.untrusted("value", Form.f(progress.generated())), MessageArgument.untrusted("value2", Form.f(progress.totalChunks())), MessageArgument.untrusted("value3", String.format("%.1f", progress.percent())), MessageArgument.untrusted("value4", (progress.paused() ? C.YELLOW + " PAUSED" : ""))));
        sender().sendMessage(IrisLanguage.text(
                BukkitCommandMessagesExtended.COMMAND_PREGEN_SPEED_S_ETA_ELAPSED_METHOD,
                MessageArgument.untrusted("overall", Form.f(progress.overallChunksPerSecond(), 1)),
                MessageArgument.untrusted("tenSecond", Form.f(progress.chunksPerSecond(), 1)),
                MessageArgument.untrusted("thirtySecond", Form.f(progress.thirtySecondChunksPerSecond(), 1)),
                MessageArgument.untrusted("sixtySecond", Form.f(progress.sixtySecondChunksPerSecond(), 1)),
                MessageArgument.untrusted("eta", Form.duration(progress.eta(), 2)),
                MessageArgument.untrusted("elapsed", Form.duration(progress.elapsed(), 2)),
                MessageArgument.untrusted("method", progress.method()),
                MessageArgument.untrusted("failures", progress.failed() > 0 ? C.RED + " Failed: " + Form.f(progress.failed()) : "")));
    }
}
