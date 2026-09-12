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

package art.arcane.iris.world.task.jobs;

import art.arcane.iris.localization.IrisLanguage;
import art.arcane.iris.localization.RuntimeUiMessages;
import art.arcane.iris.platform.bukkit.BukkitPlatform;
import art.arcane.iris.localization.C;
import art.arcane.volmlib.util.format.Form;
import art.arcane.volmlib.util.localization.MessageArgument;
import art.arcane.iris.platform.bukkit.plugin.VolmitSender;
import art.arcane.iris.world.task.J;
import art.arcane.volmlib.util.scheduling.PrecisionStopwatch;

import java.util.concurrent.CompletableFuture;

public interface Job {
    String getName();

    void execute();

    void completeWork();

    int getTotalWork();

    default int getWorkRemaining() {
        return getTotalWork() - getWorkCompleted();
    }

    int getWorkCompleted();

    default String getProgressString() {
        return Form.pc(getProgress(), 0);
    }

    default double getProgress() {
        return (double) getWorkCompleted() / (double) getTotalWork();
    }


    default void execute(VolmitSender sender) {
        execute(sender, () -> {
        });
    }


    default void execute(VolmitSender sender, Runnable whenComplete) {
        execute(sender, false, whenComplete);
    }

    default void execute(VolmitSender sender, boolean silentMsg, Runnable whenComplete) {
        PrecisionStopwatch p = PrecisionStopwatch.start();
        CompletableFuture<?> f = J.afut(this::execute);
        int c = J.ar(() -> {
            if (sender.isPlayer()) {
                sender.sendProgress(getProgress(), getName());
                BukkitPlatform.showProgressLane(sender.player(), "iris:job", getName() + " " + getProgressString(), getProgress(), 4000L);
            } else {
                sender.sendMessage(getName() + ": " + getProgressString());
            }
        }, sender.isPlayer() ? 0 : 20);
        f.whenComplete((fs, ff) -> {
            J.car(c);
            if (sender.isPlayer()) {
                sender.sendAction(" ");
                BukkitPlatform.hudLanes().hide(sender.player(), "iris:job");
            }
            if (!silentMsg) {
                sender.sendMessage(C.AQUA + IrisLanguage.text(
                        RuntimeUiMessages.JOB_COMPLETED,
                        MessageArgument.untrusted("job", getName()),
                        MessageArgument.trusted("duration", Form.duration(p.getMilliseconds(), 1))
                ));
            }
            whenComplete.run();
        });
    }
}
