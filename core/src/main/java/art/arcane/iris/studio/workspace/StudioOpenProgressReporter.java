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

package art.arcane.iris.studio.workspace;

import art.arcane.iris.localization.BukkitRuntimeMessages;
import art.arcane.iris.configuration.IrisSettings;
import art.arcane.iris.localization.IrisLanguage;
import art.arcane.iris.localization.RuntimeProgressMessages;
import art.arcane.iris.localization.ProgressBar;
import art.arcane.iris.platform.bukkit.plugin.VolmitSender;
import art.arcane.iris.world.task.J;
import art.arcane.volmlib.util.format.Form;
import art.arcane.volmlib.util.localization.MessageArgument;
import org.bukkit.Bukkit;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

@SuppressWarnings("ALL")
final class StudioOpenProgressReporter {
    private static final long CONSOLE_HEARTBEAT_MILLIS = 10_000L;

    private StudioOpenProgressReporter() {
    }

    static void startStudioOpenReporter(VolmitSender sender, AtomicReference<String> stage, AtomicReference<Double> progress, AtomicBoolean complete, AtomicBoolean failed) {
        AtomicLong nextConsoleUpdate = new AtomicLong(0L);
        AtomicReference<String> lastConsoleStage = new AtomicReference<>("");
        AtomicLong startMs = new AtomicLong(System.currentTimeMillis());
        AtomicInteger taskId = new AtomicInteger(-1);
        org.bukkit.boss.BossBar bossBar;

        if (sender.isPlayer() && sender.player() != null
                && IrisSettings.get().getGeneral().isProgressBossBar()) {
            bossBar = Bukkit.createBossBar(
                    IrisLanguage.text(RuntimeProgressMessages.STUDIO_OPENING),
                    org.bukkit.boss.BarColor.BLUE,
                    org.bukkit.boss.BarStyle.SEGMENTED_20
            );
            bossBar.setProgress(0.0D);
            bossBar.addPlayer(sender.player());
            bossBar.setVisible(true);
        } else {
            bossBar = null;
        }

        int scheduledTaskId = J.ar(() -> {
            double currentProgress = Math.max(0D, Math.min(0.99D, progress.get()));
            String currentStage = describeStage(stage.get());
            int percent = (int) Math.round(currentProgress * 100.0D);
            long elapsed = System.currentTimeMillis() - startMs.get();

            if (complete.get()) {
                J.car(taskId.get());

                if (failed.get()) {
                    if (bossBar != null) {
                        bossBar.setProgress(Math.max(0.0D, Math.min(1.0D, currentProgress)));
                        bossBar.setColor(org.bukkit.boss.BarColor.RED);
                        bossBar.setTitle(IrisLanguage.text(
                                RuntimeProgressMessages.STUDIO_FAILED_PROGRESS,
                                MessageArgument.trusted("percent", percent)
                        ));
                        J.a(() -> {
                            bossBar.removeAll();
                            bossBar.setVisible(false);
                        }, 60);
                    }
                    if (sender.isPlayer()) {
                        sender.sendAction(IrisLanguage.text(
                                RuntimeProgressMessages.STUDIO_ACTION_FAILED,
                                MessageArgument.trusted("bar", buildStudioProgressBar(currentProgress)),
                                MessageArgument.trusted("stage", currentStage)
                        ));
                    } else {
                        sender.sendMessage(IrisLanguage.text(BukkitRuntimeMessages.IRIS_PROJECT_STUDIO_OPEN_FAILED_2));
                    }
                } else {
                    if (bossBar != null) {
                        bossBar.setProgress(1.0D);
                        bossBar.setColor(org.bukkit.boss.BarColor.GREEN);
                        bossBar.setTitle(IrisLanguage.text(RuntimeProgressMessages.STUDIO_READY_PROGRESS));
                        J.a(() -> {
                            bossBar.removeAll();
                            bossBar.setVisible(false);
                        }, 60);
                    }
                    if (sender.isPlayer()) {
                        sender.sendAction(IrisLanguage.text(
                                RuntimeProgressMessages.STUDIO_ACTION_READY,
                                MessageArgument.trusted("bar", buildStudioProgressBar(1.0D)),
                                MessageArgument.trusted("elapsed", Form.duration(elapsed, 1))
                        ));
                    } else {
                        sender.sendMessage(IrisLanguage.text(BukkitRuntimeMessages.IRIS_PROJECT_STUDIO_READY, MessageArgument.untrusted("value", String.valueOf(Form.duration(elapsed, 1)))));
                    }
                }
                return;
            }

            if (sender.isPlayer() && sender.player() != null) {
                if (bossBar != null) {
                    bossBar.setProgress(Math.max(0.0D, Math.min(1.0D, currentProgress)));
                    bossBar.setTitle(IrisLanguage.text(
                            RuntimeProgressMessages.STUDIO_OPENING_PROGRESS,
                            MessageArgument.trusted("percent", percent)
                    ));
                }

                sender.sendAction(IrisLanguage.text(
                        RuntimeProgressMessages.STUDIO_ACTION_PROGRESS,
                        MessageArgument.trusted("bar", buildStudioProgressBar(currentProgress)),
                        MessageArgument.trusted("percent", percent),
                        MessageArgument.trusted("stage", currentStage),
                        MessageArgument.trusted("elapsed", Form.duration(elapsed, 0))
                ));
            } else {
                long now = System.currentTimeMillis();
                boolean stageChanged = !currentStage.equals(lastConsoleStage.get());
                if (stageChanged || now >= nextConsoleUpdate.get()) {
                    sender.sendMessage(IrisLanguage.text(
                            RuntimeProgressMessages.STUDIO_CONSOLE_PROGRESS,
                            MessageArgument.trusted("bar", ProgressBar.console(currentProgress)),
                            MessageArgument.trusted("percent", percent),
                            MessageArgument.trusted("stage", currentStage),
                            MessageArgument.trusted("elapsed", Form.duration(elapsed, 0))
                    ));
                    lastConsoleStage.set(currentStage);
                    nextConsoleUpdate.set(now + CONSOLE_HEARTBEAT_MILLIS);
                }
            }
        }, 3);

        taskId.set(scheduledTaskId);
        if (complete.get()) {
            J.car(taskId.get());
        }
    }

    private static String buildStudioProgressBar(double progress) {
        return ProgressBar.player(progress);
    }

    private static String describeStage(String stage) {
        if (stage == null || stage.isBlank()) {
            return IrisLanguage.text(RuntimeProgressMessages.STUDIO_STAGE_INITIALIZING);
        }
        return switch (stage) {
            case "Queued" -> IrisLanguage.text(RuntimeProgressMessages.STUDIO_STAGE_QUEUED);
            case "resolve_dimension" -> IrisLanguage.text(RuntimeProgressMessages.STUDIO_STAGE_RESOLVE_DIMENSION);
            case "prepare_world_pack" -> IrisLanguage.text(RuntimeProgressMessages.STUDIO_STAGE_PREPARE_WORLD_PACK);
            case "install_datapacks" -> IrisLanguage.text(RuntimeProgressMessages.STUDIO_STAGE_INSTALL_DATAPACKS);
            case "create_world" -> IrisLanguage.text(RuntimeProgressMessages.STUDIO_STAGE_CREATE_WORLD);
            case "apply_world_rules" -> IrisLanguage.text(RuntimeProgressMessages.STUDIO_STAGE_APPLY_WORLD_RULES);
            case "prepare_generator" -> IrisLanguage.text(RuntimeProgressMessages.STUDIO_STAGE_PREPARE_GENERATOR);
            case "teleport_player" -> IrisLanguage.text(RuntimeProgressMessages.STUDIO_STAGE_TELEPORT_PLAYER);
            case "finalize_open" -> IrisLanguage.text(RuntimeProgressMessages.STUDIO_STAGE_FINALIZE_OPEN);
            case "cleanup" -> IrisLanguage.text(RuntimeProgressMessages.STUDIO_STAGE_CLEANUP);
            default -> Form.capitalizeWords(stage.replace('_', ' '));
        };
    }
}
