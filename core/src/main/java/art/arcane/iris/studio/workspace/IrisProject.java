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

import art.arcane.iris.spi.IrisLogging;
import art.arcane.iris.studio.object.StudioOpenCoordinator;
import art.arcane.iris.world.IrisToolbelt;
import art.arcane.iris.platform.generation.PlatformChunkGenerator;
import art.arcane.volmlib.util.collection.KList;
import art.arcane.volmlib.util.exceptions.IrisException;
import art.arcane.iris.platform.bukkit.plugin.VolmitSender;
import art.arcane.iris.world.task.J;
import lombok.Data;
import org.bukkit.World;

import java.io.File;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import art.arcane.iris.localization.BukkitRuntimeMessages;
import art.arcane.iris.localization.IrisLanguage;
import art.arcane.volmlib.util.localization.MessageArgument;
@SuppressWarnings("ALL")
@Data
public class IrisProject {
    private File path;
    private String name;
    private PlatformChunkGenerator activeProvider;
    private StudioOpenCoordinator.StudioOpenKind activeOpenKind;

    public IrisProject(File path) {
        this.path = path;
        this.name = path.getName();
    }

    public boolean isOpen() {
        return activeProvider != null;
    }

    public KList<File> collectFiles(File f, String fileExtension) {
        KList<File> l = new KList<>();

        if (f.isDirectory()) {
            for (File i : f.listFiles()) {
                l.addAll(collectFiles(i, fileExtension));
            }
        } else if (f.getName().endsWith("." + fileExtension)) {
            l.add(f);
        }

        return l;
    }

    public KList<File> collectFiles(String json) {
        return collectFiles(path, json);
    }

    public void open(VolmitSender sender) throws IrisException {
        open(sender, 1337, StudioOpenCoordinator.StudioOpenKind.STANDARD, (w) ->
        {
        });
    }

    public CompletableFuture<StudioOpenCoordinator.StudioOpenResult> open(
            VolmitSender sender,
            long seed,
            StudioOpenCoordinator.StudioOpenKind openKind,
            Consumer<World> onDone
    ) throws IrisException {
        return open(sender, seed, openKind, onDone, System.nanoTime());
    }

    public CompletableFuture<StudioOpenCoordinator.StudioOpenResult> open(
            VolmitSender sender,
            long seed,
            StudioOpenCoordinator.StudioOpenKind openKind,
            Consumer<World> onDone,
            long requestedAtNanos
    ) throws IrisException {
        if (isOpen()) {
            return close().thenCompose(ignored -> openInternal(
                    sender,
                    seed,
                    openKind,
                    onDone,
                    requestedAtNanos));
        }

        return openInternal(sender, seed, openKind, onDone, requestedAtNanos);
    }

    private CompletableFuture<StudioOpenCoordinator.StudioOpenResult> openInternal(
            VolmitSender sender,
            long seed,
            StudioOpenCoordinator.StudioOpenKind openKind,
            Consumer<World> onDone,
            long requestedAtNanos
    ) {
        AtomicReference<String> stage = new AtomicReference<>("Queued");
        AtomicReference<Double> progress = new AtomicReference<>(0.01D);
        AtomicBoolean complete = new AtomicBoolean(false);
        AtomicBoolean failed = new AtomicBoolean(false);
        CompletableFuture<StudioOpenCoordinator.StudioOpenResult> future = StudioOpenCoordinator.get().open(
                StudioOpenCoordinator.StudioOpenRequest.studioProject(
                        this,
                        sender,
                        seed,
                        openKind,
                        update -> {
                            if (update.stage() != null && !update.stage().isBlank()) {
                                stage.set(update.stage());
                            }
                            progress.set(Math.max(0D, Math.min(0.99D, update.progress())));
                        },
                        onDone,
                        requestedAtNanos
                )
        );
        StudioOpenProgressReporter.startStudioOpenReporter(sender, stage, progress, complete, failed);
        future.whenComplete((result, throwable) -> {
            World maintenanceWorld = null;
            boolean maintenanceActive = false;
            try {
                if (throwable != null) {
                    failed.set(true);
                    Throwable error = throwable instanceof java.util.concurrent.CompletionException completionException && completionException.getCause() != null
                            ? completionException.getCause()
                            : throwable;
                    IrisLogging.reportError("Studio open failed for project \"" + getName() + "\".", error);
                    sender.sendMessage(IrisLanguage.text(BukkitRuntimeMessages.IRIS_PROJECT_STUDIO_OPEN_FAILED, MessageArgument.untrusted("error", String.valueOf(error.getMessage()))));
                    return;
                }

                maintenanceWorld = result.world();
                if (maintenanceWorld != null) {
                    IrisToolbelt.beginWorldMaintenance(maintenanceWorld, "studio-open");
                    maintenanceActive = true;
                }
            } finally {
                if (maintenanceActive && maintenanceWorld != null) {
                    World worldToRelease = maintenanceWorld;
                    J.a(() -> IrisToolbelt.endWorldMaintenance(worldToRelease, "studio-open"), 300);
                }
                complete.set(true);
            }
        });
        return future;
    }

    public CompletableFuture<StudioOpenCoordinator.StudioCloseResult> close() {
        if (activeProvider == null) {
            return CompletableFuture.completedFuture(new StudioOpenCoordinator.StudioCloseResult(null, true, true, false, null));
        }

        return StudioOpenCoordinator.get().closeProject(this);
    }
}
