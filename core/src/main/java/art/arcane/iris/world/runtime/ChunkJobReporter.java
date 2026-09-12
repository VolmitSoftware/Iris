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

package art.arcane.iris.world.runtime;

import art.arcane.iris.configuration.IrisSettings;
import art.arcane.iris.localization.IrisLanguage;
import art.arcane.iris.localization.RuntimeProgressMessages;
import art.arcane.iris.spi.IrisLogging;
import art.arcane.iris.localization.C;
import art.arcane.volmlib.util.math.ChunkSpiral;
import art.arcane.iris.platform.bukkit.plugin.VolmitSender;
import art.arcane.iris.world.task.J;
import art.arcane.volmlib.util.format.Form;
import art.arcane.volmlib.util.localization.MessageArgument;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

public final class ChunkJobReporter {
    private static final int PROGRESS_BAR_WIDTH = 24;
    private static final int REPORT_INTERVAL_TICKS = 5;
    private static final int FINISH_LINGER_TICKS = 60;

    private final VolmitSender sender;
    private final String title;
    private final String worldName;

    private final AtomicReference<String> stage = new AtomicReference<>(
            IrisLanguage.text(RuntimeProgressMessages.CHUNK_STAGE_PREPARING)
    );
    private final AtomicReference<Double> progress = new AtomicReference<>(0.0D);
    private final AtomicBoolean complete = new AtomicBoolean(false);
    private final AtomicBoolean failed = new AtomicBoolean(false);
    private final AtomicInteger applied = new AtomicInteger(0);
    private final AtomicInteger failures = new AtomicInteger(0);
    private volatile int total = 0;
    private volatile long startMs = 0L;

    public ChunkJobReporter(VolmitSender sender, String title, World world) {
        this.sender = sender;
        this.title = title;
        this.worldName = world.getName();
    }

    public static List<int[]> orderedTargets(int centerChunkX, int centerChunkZ, int radius) {
        return ChunkSpiral.centerOut(centerChunkX, centerChunkZ, radius);
    }

    public void start() {
        startMs = System.currentTimeMillis();
        startReporter();
    }

    public void setStage(String value) {
        stage.set(value);
    }

    public void setTotal(int value) {
        total = value;
    }

    public void countApplied(boolean ok) {
        if (ok) {
            applied.incrementAndGet();
        } else {
            failures.incrementAndGet();
        }

        int finished = applied.get() + failures.get();
        if (total > 0) {
            progress.set(Math.min(0.999D, (double) finished / total));
        }
    }

    public void finish(boolean error) {
        progress.set(1.0D);
        failed.set(error || (applied.get() == 0 && total > 0));
        complete.set(true);
    }

    public int applied() {
        return applied.get();
    }

    public int failures() {
        return failures.get();
    }

    private void startReporter() {
        boolean showBossBar = sender.isPlayer() && sender.player() != null
                && IrisSettings.get().getGeneral().isProgressBossBar();
        BossBar bossBar = showBossBar
                ? Bukkit.createBossBar(IrisLanguage.text(
                        RuntimeProgressMessages.CHUNK_BOSSBAR_WORKING,
                        MessageArgument.trusted("title", title)
                ), BarColor.BLUE, BarStyle.SEGMENTED_20)
                : null;
        if (bossBar != null) {
            bossBar.setProgress(0.0D);
            bossBar.addPlayer(sender.player());
            bossBar.setVisible(true);
        }
        AtomicInteger taskId = new AtomicInteger(-1);
        taskId.set(J.ar(() -> {
            double currentProgress = Math.max(0.0D, Math.min(1.0D, progress.get()));
            int percent = (int) Math.round(currentProgress * 100.0D);
            long elapsed = System.currentTimeMillis() - startMs;

            if (complete.get()) {
                J.car(taskId.get());
                finishReporter(bossBar, elapsed);
                return;
            }

            if (bossBar != null) {
                bossBar.setProgress(Math.min(1.0D, currentProgress));
                bossBar.setTitle(IrisLanguage.text(
                        RuntimeProgressMessages.CHUNK_BOSSBAR_PROGRESS,
                        MessageArgument.trusted("title", title),
                        MessageArgument.trusted("stage", stage.get()),
                        MessageArgument.trusted("percent", percent)
                ));
            }
            if (sender.isPlayer()) {
                sender.sendAction(IrisLanguage.text(
                        RuntimeProgressMessages.CHUNK_ACTION_PROGRESS,
                        MessageArgument.trusted("bar", progressBar(currentProgress)),
                        MessageArgument.trusted("percent", percent),
                        MessageArgument.trusted("stage", stage.get()),
                        MessageArgument.trusted("applied", applied.get()),
                        MessageArgument.trusted("total", total <= 0 ? "?" : total)
                ));
            }
        }, REPORT_INTERVAL_TICKS));
        if (complete.get()) {
            J.car(taskId.get());
        }
    }

    private void finishReporter(BossBar bossBar, long elapsed) {
        boolean ok = !failed.get();
        String summary = failures.get() > 0
                ? IrisLanguage.text(
                        RuntimeProgressMessages.CHUNK_SUMMARY_FAILED,
                        MessageArgument.trusted("applied", applied.get()),
                        MessageArgument.trusted("total", total),
                        MessageArgument.trusted("elapsed", Form.duration(elapsed, 1)),
                        MessageArgument.trusted("failures", failures.get())
                )
                : IrisLanguage.text(
                        RuntimeProgressMessages.CHUNK_SUMMARY,
                        MessageArgument.trusted("applied", applied.get()),
                        MessageArgument.trusted("total", total),
                        MessageArgument.trusted("elapsed", Form.duration(elapsed, 1))
                );

        if (bossBar != null) {
            bossBar.setProgress(1.0D);
            bossBar.setColor(ok ? BarColor.GREEN : BarColor.RED);
            bossBar.setTitle(IrisLanguage.text(
                    ok ? RuntimeProgressMessages.CHUNK_BOSSBAR_DONE : RuntimeProgressMessages.CHUNK_BOSSBAR_FAILED,
                    MessageArgument.trusted("title", title),
                    MessageArgument.trusted("summary", summary)
            ));
            J.a(() -> {
                bossBar.removeAll();
                bossBar.setVisible(false);
            }, FINISH_LINGER_TICKS);
        }

        if (sender.isPlayer()) {
            sender.sendAction(IrisLanguage.text(
                    ok ? RuntimeProgressMessages.CHUNK_ACTION_DONE : RuntimeProgressMessages.CHUNK_ACTION_FAILED,
                    MessageArgument.trusted("bar", progressBar(1.0D)),
                    MessageArgument.trusted("summary", summary)
            ));
        }
        sender.sendMessage(IrisLanguage.text(
                ok ? RuntimeProgressMessages.CHUNK_COMPLETE : RuntimeProgressMessages.CHUNK_FAILED,
                MessageArgument.trusted("title", title),
                MessageArgument.trusted("summary", summary)
        ));
        IrisLogging.info(title + " done: world=" + worldName + " " + summary);
    }

    private String progressBar(double value) {
        int filled = (int) Math.round(Math.max(0.0D, Math.min(1.0D, value)) * PROGRESS_BAR_WIDTH);
        StringBuilder bar = new StringBuilder(C.DARK_GRAY + "[");
        for (int i = 0; i < PROGRESS_BAR_WIDTH; i++) {
            bar.append(i < filled ? C.GREEN + "|" : C.DARK_GRAY + "|");
        }
        bar.append(C.DARK_GRAY).append("]");
        return bar.toString();
    }
}
