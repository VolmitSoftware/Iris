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

package art.arcane.iris.modded.catalog;

import art.arcane.iris.modded.ModdedEngineBootstrap;
import art.arcane.iris.modded.ModdedIrisLog;
import art.arcane.iris.pack.ingest.catalog.WorldgenCatalog;
import art.arcane.iris.pack.ingest.catalog.WorldgenCatalogStore;
import art.arcane.volmlib.nativelib.minecraft26_2.modded.NativeModdedLoader;
import art.arcane.volmlib.nativelib.minecraft26_2.modded.NativeModdedServer;
import art.arcane.volmlib.nativelib.minecraft26_2.modded.NativeWorldgenScan;
import art.arcane.volmlib.nativelib.minecraft26_2.modded.NativeWorldgenSnapshot;
import art.arcane.volmlib.nativelib.minecraft26_2.modded.NativeWorldgenSnapshot.EncodeFailures;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

public final class ModdedWorldgenCatalogs {
    private static final AtomicReference<WorldgenCatalog> LATEST = new AtomicReference<>();
    private static final AtomicReference<CompletableFuture<CaptureResult>> RUNNING = new AtomicReference<>();

    private ModdedWorldgenCatalogs() {
    }

    public static Optional<WorldgenCatalog> latest() {
        return Optional.ofNullable(LATEST.get());
    }

    public static Path catalogFile() {
        return ModdedEngineBootstrap.loader().configDir().resolve("irisworldgen").resolve("catalog")
                .resolve(WorldgenCatalogStore.FILE_NAME);
    }

    public static CompletableFuture<CaptureResult> capture(NativeModdedServer server) {
        Objects.requireNonNull(server, "server");
        return capture(() -> scan(server));
    }

    static CompletableFuture<CaptureResult> capture(CaptureScan scan) {
        CompletableFuture<CaptureResult> pending = new CompletableFuture<>();
        CompletableFuture<CaptureResult> running = RUNNING.compareAndExchange(null, pending);
        if (running != null) {
            return running;
        }
        CaptureWork work;
        try {
            work = scan.scan();
        } catch (Throwable failure) {
            ModdedIrisLog.error("Iris could not read the worldgen registries for the catalog", failure);
            finish(pending, null, failure);
            rethrowIfVirtualMachineError(failure);
            return pending;
        }
        Thread worker = new Thread(() -> complete(work, pending), "Iris Worldgen Catalog");
        worker.setDaemon(true);
        worker.start();
        return pending;
    }

    private static CaptureWork scan(NativeModdedServer server) {
        long startedAt = System.nanoTime();
        NativeModdedLoader loader = ModdedEngineBootstrap.loader();
        NativeWorldgenScan scan = NativeWorldgenScan.scan(server, loader);
        CatalogCaptureContext context = new CatalogCaptureContext(loader.minecraftVersion(), loader.platformName(),
                System.currentTimeMillis(), loader.mods());
        PendingCapture capture = new PendingCapture(scan, context, catalogFile(), startedAt, elapsedMillis(startedAt));
        return () -> encodeAndWrite(capture);
    }

    private static void complete(CaptureWork work, CompletableFuture<CaptureResult> pending) {
        try {
            finish(pending, work.run(), null);
        } catch (IOException failure) {
            ModdedIrisLog.error("Iris could not write the worldgen catalog", failure);
            finish(pending, null, new UncheckedIOException(failure));
        } catch (Throwable failure) {
            ModdedIrisLog.error("Iris could not build the worldgen catalog", failure);
            finish(pending, null, failure);
            rethrowIfVirtualMachineError(failure);
        }
    }

    private static CaptureResult encodeAndWrite(PendingCapture capture) throws IOException {
        NativeWorldgenSnapshot snapshot = capture.scan().encode();
        WorldgenCatalog catalog = WorldgenCatalogAssembler.assemble(snapshot, capture.context());
        WorldgenCatalogStore.write(capture.file(), catalog);
        LATEST.set(catalog);
        CaptureResult result = new CaptureResult(catalog, capture.file(), elapsedMillis(capture.startedAt()));
        ModdedIrisLog.info("Iris worldgen catalog: {} namespaces, {} biomes, {} placed features, {} structures, "
                        + "{} dimension types, {} level stems -> {} ({}ms server thread, {}ms total)",
                catalog.namespaces().size(), catalog.biomes().size(), catalog.placedFeatures().size(),
                catalog.structures().size(), catalog.dimensionTypes().size(), catalog.levelStems().size(),
                capture.file(), capture.scanMillis(), result.millis());
        reportEncodeFailures(snapshot.failures());
        return result;
    }

    private static void reportEncodeFailures(EncodeFailures failures) {
        if (failures.count() == 0) {
            return;
        }
        String first = failures.samples().isEmpty() ? "" : failures.samples().getFirst();
        if (failures.firstException() != null) {
            ModdedIrisLog.warn("Iris worldgen catalog left {} entries without JSON; first: {}", failures.count(), first,
                    failures.firstException());
            return;
        }
        ModdedIrisLog.warn("Iris worldgen catalog left {} entries without JSON; first: {}", failures.count(), first);
    }

    private static void finish(CompletableFuture<CaptureResult> pending, CaptureResult result, Throwable failure) {
        RUNNING.compareAndSet(pending, null);
        if (failure != null) {
            pending.completeExceptionally(failure);
            return;
        }
        pending.complete(result);
    }

    private static void rethrowIfVirtualMachineError(Throwable failure) {
        if (failure instanceof VirtualMachineError virtualMachineError) {
            throw virtualMachineError;
        }
    }

    private static long elapsedMillis(long startedAt) {
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt);
    }

    public record CaptureResult(WorldgenCatalog catalog, Path file, long millis) {
    }

    @FunctionalInterface
    interface CaptureScan {
        CaptureWork scan();
    }

    @FunctionalInterface
    interface CaptureWork {
        CaptureResult run() throws IOException;
    }

    private record PendingCapture(NativeWorldgenScan scan, CatalogCaptureContext context, Path file, long startedAt, long scanMillis) {
    }
}
