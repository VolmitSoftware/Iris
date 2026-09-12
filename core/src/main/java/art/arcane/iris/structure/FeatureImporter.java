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

package art.arcane.iris.structure;

import art.arcane.iris.world.IrisWorldStorage;
import art.arcane.iris.pack.datapack.ServerConfigurator;
import art.arcane.iris.world.WorldCreatorCompat;
import art.arcane.iris.world.lifecycle.LifecycleOperationCoordinator;
import art.arcane.iris.world.lifecycle.WorldLifecycleService;
import art.arcane.iris.pack.loading.IrisData;
import art.arcane.iris.platform.bukkit.nms.INMS;
import art.arcane.iris.pack.AtomicDirectoryPublisher;
import art.arcane.iris.world.runtime.WorldDeletionQueue;
import art.arcane.iris.world.IrisToolbelt;
import art.arcane.iris.studio.tree.TreePlausibilizer;
import art.arcane.iris.structure.object.IrisObject;
import art.arcane.iris.platform.generation.PlatformChunkGenerator;
import art.arcane.iris.spi.IrisLogging;
import art.arcane.iris.spi.IrisServices;
import art.arcane.iris.localization.C;
import art.arcane.iris.platform.bukkit.plugin.VolmitSender;
import art.arcane.iris.world.task.J;
import art.arcane.volmlib.util.collection.KList;
import art.arcane.volmlib.util.bukkit.WorldIdentity;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.WorldType;
import org.bukkit.block.Block;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import java.util.function.Function;
import java.util.function.Supplier;

import art.arcane.iris.localization.BukkitRuntimeMessages;
import art.arcane.iris.localization.IrisLanguage;
import art.arcane.volmlib.util.localization.MessageArgument;
public final class FeatureImporter {
    public record Report(int total, int imported, int skipped, int failed) {
    }

    private static final String SCRATCH_WORLD_PREFIX = "iris-feature-import-";
    private static final int SCRATCH_ID_ATTEMPTS = 32;
    private static final long SCRATCH_CREATE_TIMEOUT_SECONDS = 120L;
    private static final long SCRATCH_TEARDOWN_TIMEOUT_SECONDS = 120L;
    private static final int CAPTURE_RADIUS = 16;
    private static final int CAPTURE_HEIGHT = 40;
    private static final int CELL_STRIDE = 48;
    private static final int CELL_COLUMNS = 16;
    private static final int PLACE_ATTEMPTS = 6;
    private static final long REGION_TIMEOUT_SECONDS = 30L;
    private static final Set<String> RESERVED_SCRATCH_NAMES = ConcurrentHashMap.newKeySet();
    private static final ConcurrentHashMap<String, ScratchWorldState> ACTIVE_SCRATCH_WORLDS = new ConcurrentHashMap<>();

    private FeatureImporter() {
    }

    public static Report importAllObjectFeatures(IrisData data, int variants, VolmitSender sender) {
        int wantVariants = Math.max(1, variants);

        List<Row> rows = parseRows(INMS.get().getObjectFeatureKeys());
        int total = rows.size();
        if (total == 0) {
            sender.sendMessage(IrisLanguage.text(BukkitRuntimeMessages.FEATURE_IMPORTER_NO_VANILLA_TREE_OBJECT_FEATURES_ARE_EXPOSED_BY_ACTIVE_NMS_BINDING_IMPORTING));
            return new Report(0, 0, 0, 0);
        }

        sender.sendMessage(IrisLanguage.text(BukkitRuntimeMessages.FEATURE_IMPORTER_IMPORTING_VANILLA_TREE_OBJECT_FEATURES_VARIANTS_EACH_INTO_SCRATCH_WORLD, MessageArgument.untrusted("total", String.valueOf(total)), MessageArgument.untrusted("wantVariants", String.valueOf(wantVariants))));

        World scratch = createScratchWorld(sender);
        if (scratch == null) {
            return new Report(total, 0, 0, total);
        }

        int imported = 0;
        int skipped = 0;
        int failed = 0;
        int cellIndex = 0;

        try {
            for (Row row : rows) {
                try {
                    int written = 0;
                    Set<Long> hashes = new HashSet<>();
                    for (int v = 0; v < wantVariants; v++) {
                        CaptureResult capture = captureOne(scratch, row.key(), v, cellIndex++);
                        if (capture == null || !capture.placed() || capture.object() == null) {
                            continue;
                        }
                        IrisObject object = capture.object();
                        if (object.getBlocks().isEmpty()) {
                            continue;
                        }
                        object.shrinkwrap();
                        if (row.group().equals("trees") || row.group().equals("fallen_trees")) {
                            try {
                                TreePlausibilizer.apply(object, TreePlausibilizer.seedOf(row.key() + "#" + written), TreePlausibilizer.DEFAULT_REACH);
                            } catch (Throwable e) {
                                IrisLogging.reportError(e);
                            }
                        }
                        long hash = hashOf(object);
                        if (!hashes.add(hash)) {
                            continue;
                        }
                        write(objectFile(data, row.group(), row.safeName(), written), object);
                        written++;
                    }

                    if (written > 0) {
                        imported++;
                        sender.sendMessage(IrisLanguage.text(BukkitRuntimeMessages.FEATURE_IMPORTER_OBJ_OBJECTS_VANILLA, MessageArgument.untrusted("key", String.valueOf(row.key())), MessageArgument.untrusted("group", String.valueOf(row.group())), MessageArgument.untrusted("safeName", String.valueOf(row.safeName())), MessageArgument.untrusted("written", String.valueOf(written))));
                    } else {
                        skipped++;
                        sender.sendMessage(IrisLanguage.text(BukkitRuntimeMessages.FEATURE_IMPORTER_SKIP_FEATURE_PLACED_NOTHING_AFTER_RETRIES, MessageArgument.untrusted("key", String.valueOf(row.key()))));
                    }
                } catch (Throwable e) {
                    failed++;
                    sender.sendMessage(IrisLanguage.text(BukkitRuntimeMessages.FEATURE_IMPORTER_FAIL, MessageArgument.untrusted("key", String.valueOf(row.key())), MessageArgument.untrusted("error", String.valueOf(e.getMessage()))));
                    IrisLogging.reportError(e);
                }

                int processed = imported + skipped + failed;
                if (processed % 25 == 0) {
                    sender.sendMessage(IrisLanguage.text(BukkitRuntimeMessages.FEATURE_IMPORTER_IMPORTED_SKIPPED_FAILED, MessageArgument.untrusted("processed", String.valueOf(processed)), MessageArgument.untrusted("total", String.valueOf(total)), MessageArgument.untrusted("imported", String.valueOf(imported)), MessageArgument.untrusted("skipped", String.valueOf(skipped)), MessageArgument.untrusted("failed", String.valueOf(failed))));
                }
            }
        } finally {
            destroyScratchWorld(scratch, sender);
        }

        sender.sendMessage(IrisLanguage.text(BukkitRuntimeMessages.FEATURE_IMPORTER_FEATURE_IMPORT_COMPLETE_FEATURES_WRITTEN_SKIPPED_FAILED_TOTAL, MessageArgument.untrusted("imported", String.valueOf(imported)), MessageArgument.untrusted("skipped", String.valueOf(skipped)), MessageArgument.untrusted("failed", String.valueOf(failed)), MessageArgument.untrusted("total", String.valueOf(total))));
        return new Report(total, imported, skipped, failed);
    }

    private static List<Row> parseRows(KList<String> raw) {
        List<Row> rows = new ArrayList<>();
        if (raw == null) {
            return rows;
        }
        for (String entry : raw) {
            if (entry == null) {
                continue;
            }
            int pipe = entry.indexOf('|');
            if (pipe <= 0 || pipe >= entry.length() - 1) {
                continue;
            }
            String group = entry.substring(0, pipe);
            String key = entry.substring(pipe + 1);
            if (key.isBlank()) {
                continue;
            }
            rows.add(new Row(group, key, BulkStructureImporter.templateNameFor(key)));
        }
        rows.sort(Comparator.comparing(r -> r.group() + "/" + r.safeName()));
        return rows;
    }

    private static CaptureResult captureOne(World world, String featureKey, int variant, int cellIndex) {
        int originX = (cellIndex % CELL_COLUMNS) * CELL_STRIDE;
        int originZ = (cellIndex / CELL_COLUMNS) * CELL_STRIDE;
        int centerX = originX + CELL_STRIDE / 2;
        int centerZ = originZ + CELL_STRIDE / 2;
        int anchorChunkX = centerX >> 4;
        int anchorChunkZ = centerZ >> 4;

        AtomicReference<IrisObject> objectRef = new AtomicReference<>();
        AtomicBoolean placedRef = new AtomicBoolean(false);
        AtomicReference<Throwable> errorRef = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);

        boolean scheduled = J.runRegion(world, anchorChunkX, anchorChunkZ, () -> {
            try {
                int chunkMinX = (centerX - CAPTURE_RADIUS) >> 4;
                int chunkMaxX = (centerX + CAPTURE_RADIUS) >> 4;
                int chunkMinZ = (centerZ - CAPTURE_RADIUS) >> 4;
                int chunkMaxZ = (centerZ + CAPTURE_RADIUS) >> 4;
                for (int cx = chunkMinX; cx <= chunkMaxX; cx++) {
                    for (int cz = chunkMinZ; cz <= chunkMaxZ; cz++) {
                        world.getChunkAt(cx, cz);
                    }
                }

                int baseY = world.getHighestBlockYAt(centerX, centerZ) + 1;

                boolean placed = false;
                for (int attempt = 0; attempt < PLACE_ATTEMPTS; attempt++) {
                    if (INMS.get().placeFeature(world, centerX, baseY, centerZ, featureKey, featureSeed(featureKey, variant, attempt))) {
                        placed = true;
                        break;
                    }
                }
                placedRef.set(placed);

                if (placed) {
                    int width = CAPTURE_RADIUS * 2 + 1;
                    int depth = CAPTURE_RADIUS * 2 + 1;
                    int minX = centerX - CAPTURE_RADIUS;
                    int minZ = centerZ - CAPTURE_RADIUS;
                    IrisObject object = new IrisObject(width, CAPTURE_HEIGHT, depth);
                    for (int dx = 0; dx < width; dx++) {
                        for (int dy = 0; dy < CAPTURE_HEIGHT; dy++) {
                            for (int dz = 0; dz < depth; dz++) {
                                Block block = world.getBlockAt(minX + dx, baseY + dy, minZ + dz);
                                if (block.getType() == Material.AIR) {
                                    continue;
                                }
                                object.setUnsigned(dx, dy, dz, block, false);
                            }
                        }
                    }
                    objectRef.set(object);
                }

                for (int cx = chunkMinX; cx <= chunkMaxX; cx++) {
                    for (int cz = chunkMinZ; cz <= chunkMaxZ; cz++) {
                        try {
                            world.unloadChunk(cx, cz, false);
                        } catch (Throwable ignored) {
                        }
                    }
                }
            } catch (Throwable e) {
                errorRef.set(e);
            } finally {
                latch.countDown();
            }
        });

        if (!scheduled) {
            return null;
        }

        try {
            if (!latch.await(REGION_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                return null;
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        }

        if (errorRef.get() != null) {
            IrisLogging.reportError(errorRef.get());
            return null;
        }
        return new CaptureResult(placedRef.get(), objectRef.get());
    }

    private static long featureSeed(String key, int variant, int attempt) {
        long h = key.hashCode() & 0xFFFFFFFFL;
        h = h * 0x9E3779B97F4A7C15L + variant * 0x100000001B3L + attempt * 31L + 0x5DEECE66DL;
        return h;
    }

    private static long hashOf(IrisObject object) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            object.write(out);
            byte[] bytes = out.toByteArray();
            long h = 1125899906842597L;
            for (byte b : bytes) {
                h = 31 * h + b;
            }
            return h;
        } catch (Throwable e) {
            return object.getW() * 73856093L + object.getH() * 19349663L + object.getD();
        }
    }

    private static File objectFile(IrisData data, String group, String safeName, int writtenIndex) {
        String suffix = writtenIndex == 0 ? "" : "_" + (writtenIndex + 1);
        return new File(data.getDataFolder(), "objects/vanilla/" + group + "/" + safeName + suffix + ".iob");
    }

    private static void write(File file, IrisObject object) throws IOException {
        File parent = file.getParentFile();
        if (parent != null && !parent.exists()) {
            parent.mkdirs();
        }
        object.write(file);
    }

    static World createScratchWorld(VolmitSender sender) {
        LifecycleOperationCoordinator.Lease lease = null;
        ScratchWorldReservation reservation = null;
        World createdWorld = null;
        try {
            reservation = reserveScratchWorld();
            lease = LifecycleOperationCoordinator.get().acquire(
                    LifecycleOperationCoordinator.Domain.WORLD_MUTATION,
                    LifecycleOperationCoordinator.OperationKind.WORLD_CREATE,
                    reservation.key().toString()
            );
            requireUnusedReservation(reservation);
            WorldCreator creator = WorldCreatorCompat.ofKey(reservation.key())
                    .environment(World.Environment.NORMAL)
                    .type(WorldType.FLAT)
                    .generateStructures(false);
            createdWorld = J.sfut(() -> INMS.get().createWorldAsync(creator))
                    .thenCompose(Function.identity())
                    .get(SCRATCH_CREATE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            if (createdWorld == null) {
                throw new IllegalStateException("Scratch world creation returned no world.");
            }
            if (!reservation.key().equals(WorldIdentity.key(createdWorld))) {
                throw new IllegalStateException("Scratch world creation returned an unexpected world identity.");
            }
            Path createdFolder = createdWorld.getWorldFolder().toPath().toAbsolutePath().normalize();
            if (!reservation.folder().equals(createdFolder)) {
                throw new IllegalStateException("Scratch world creation returned an unexpected storage folder.");
            }

            String identity = WorldIdentity.serialize(createdWorld);
            ScratchWorldState state = new ScratchWorldState(reservation, lease);
            if (ACTIVE_SCRATCH_WORLDS.putIfAbsent(identity, state) != null) {
                throw new IllegalStateException("Scratch world identity is already active: " + identity);
            }
            lease = null;
            reservation = null;
            return createdWorld;
        } catch (Throwable e) {
            Throwable failure = unwrapFailure(e);
            IrisLogging.reportError(failure);
            boolean settled = createdWorld != null
                    && reservation != null
                    && settleFailedScratchCreation(createdWorld, reservation, failure);
            if (reservation != null && (!settled || !matchesReservation(createdWorld, reservation))) {
                queueScratchCleanup(reservation.name(), failure);
            }
            if (containsTimeout(failure)) {
                ServerConfigurator.restart("Feature-import scratch world creation timed out for \""
                        + (reservation == null ? "unreserved" : reservation.name()) + "\".");
            }
            if (sender != null) {
                sender.sendMessage(IrisLanguage.text(BukkitRuntimeMessages.FEATURE_IMPORTER_COULD_NOT_CREATE_SCRATCH_WORLD_FEATURE_IMPORT_SKIPPING_TREE_OBJECT_PASS, MessageArgument.untrusted("error", String.valueOf(failure.getMessage()))));
            }
            return null;
        } finally {
            if (reservation != null) {
                RESERVED_SCRATCH_NAMES.remove(reservation.name());
            }
            if (lease != null) {
                lease.close();
            }
        }
    }

    private static boolean settleFailedScratchCreation(
            World world,
            ScratchWorldReservation expectedReservation,
            Throwable creationFailure
    ) {
        ScratchWorldReservation actualReservation = reservationForCreatedScratch(world, creationFailure);
        if (actualReservation == null) {
            return false;
        }

        PlatformChunkGenerator generator = null;
        try {
            generator = IrisToolbelt.access(world);
        } catch (Throwable accessFailure) {
            creationFailure.addSuppressed(accessFailure);
        }

        AtomicBoolean terminalTimeout = new AtomicBoolean(false);
        PlatformChunkGenerator capturedGenerator = generator;
        CompletableFuture<Void> sequence = sequenceScratchTeardown(
                () -> WorldLifecycleService.get().unloadAsync(world, false),
                () -> WorldIdentity.resolve(actualReservation.key()).isPresent(),
                () -> capturedGenerator == null
                        ? CompletableFuture.completedFuture(null)
                        : capturedGenerator.closeAsync(),
                () -> {
                    try {
                        deleteScratchFolder(actualReservation, world);
                        return CompletableFuture.completedFuture(null);
                    } catch (IOException deletionFailure) {
                        return CompletableFuture.failedFuture(deletionFailure);
                    }
                },
                actualReservation.name(),
                terminalTimeout::get);
        try {
            guardScratchTeardown(sequence, terminalTimeout, actualReservation.name()).join();
            if (!expectedReservation.name().equals(actualReservation.name())) {
                queueScratchCleanup(expectedReservation.name(), creationFailure);
            }
            return true;
        } catch (Throwable cleanupFailure) {
            Throwable cause = unwrapFailure(cleanupFailure);
            creationFailure.addSuppressed(cause);
            queueScratchCleanup(actualReservation.name(), creationFailure);
            if (!expectedReservation.name().equals(actualReservation.name())) {
                queueScratchCleanup(expectedReservation.name(), creationFailure);
            }
            if (containsTimeout(cause) || WorldIdentity.resolve(actualReservation.key()).isPresent()) {
                ServerConfigurator.restart("Feature-import scratch cleanup did not reach a safe boundary for \""
                        + actualReservation.name() + "\".");
            }
            return false;
        }
    }

    static void destroyScratchWorld(World world, VolmitSender sender) {
        if (world == null) {
            return;
        }

        String identity = WorldIdentity.serialize(world);
        ScratchWorldState state = ACTIVE_SCRATCH_WORLDS.remove(identity);
        if (state == null) {
            IrisLogging.warn("Refusing to destroy unreserved feature-import scratch world \"" + world.getName() + "\".");
            return;
        }

        Throwable failure = null;
        try {
            PlatformChunkGenerator generator = IrisToolbelt.access(world);
            AtomicBoolean terminalTimeout = new AtomicBoolean(false);
            CompletableFuture<Void> sequence = sequenceScratchTeardown(
                    () -> WorldLifecycleService.get().unloadAsync(world, false),
                    () -> WorldIdentity.resolve(state.reservation().key()).isPresent(),
                    () -> generator == null ? CompletableFuture.completedFuture(null) : generator.closeAsync(),
                    () -> {
                        try {
                            deleteScratchFolder(state, world);
                            return CompletableFuture.completedFuture(null);
                        } catch (IOException deletionFailure) {
                            return CompletableFuture.failedFuture(deletionFailure);
                        }
                    },
                    state.reservation().name(),
                    terminalTimeout::get
            );
            guardScratchTeardown(sequence, terminalTimeout, state.reservation().name()).join();
        } catch (Throwable e) {
            failure = unwrapFailure(e);
            queueScratchCleanup(state.reservation().name(), failure);
            if (containsTimeout(failure) || WorldIdentity.resolve(state.reservation().key()).isPresent()) {
                ServerConfigurator.restart("Feature-import scratch cleanup did not reach a safe boundary for \""
                        + state.reservation().name() + "\".");
            }
            IrisLogging.reportError("Feature-import scratch world cleanup failed for \""
                    + state.reservation().name() + "\"; startup cleanup was queued.", failure);
        } finally {
            RESERVED_SCRATCH_NAMES.remove(state.reservation().name());
            state.lease().close();
        }
        if (failure != null && sender != null) {
            sender.sendMessage("Feature-import scratch world cleanup was deferred until the next startup: "
                    + failure.getMessage());
        }
    }

    private static ScratchWorldReservation reserveScratchWorld() throws IOException {
        for (int attempt = 0; attempt < SCRATCH_ID_ATTEMPTS; attempt++) {
            String name = SCRATCH_WORLD_PREFIX + UUID.randomUUID();
            if (!RESERVED_SCRATCH_NAMES.add(name)) {
                continue;
            }

            NamespacedKey key = IrisWorldStorage.managedKeyFromName(name);
            Path folder = IrisWorldStorage.requireSafeManagedDimensionRoot(key)
                    .toPath()
                    .toAbsolutePath()
                    .normalize();
            ScratchWorldReservation reservation = new ScratchWorldReservation(name, key, folder);
            if (isReservationUnused(reservation)) {
                return reservation;
            }
            RESERVED_SCRATCH_NAMES.remove(name);
        }
        throw new IOException("Could not reserve a collision-free feature-import scratch world identity.");
    }

    private static void requireUnusedReservation(ScratchWorldReservation reservation) throws IOException {
        if (!RESERVED_SCRATCH_NAMES.contains(reservation.name()) || !isReservationUnused(reservation)) {
            throw new IOException("Feature-import scratch world reservation was claimed before creation: "
                    + reservation.name());
        }
    }

    private static boolean isReservationUnused(ScratchWorldReservation reservation) {
        return WorldIdentity.resolve(reservation.key()).isEmpty()
                && !Files.exists(reservation.folder(), LinkOption.NOFOLLOW_LINKS)
                && !Files.isSymbolicLink(reservation.folder());
    }

    private static boolean matchesReservation(World world, ScratchWorldReservation reservation) {
        if (world == null || reservation == null) {
            return false;
        }
        try {
            return reservation.key().equals(WorldIdentity.key(world))
                    && reservation.folder().equals(world.getWorldFolder().toPath().toAbsolutePath().normalize());
        } catch (Throwable failure) {
            return false;
        }
    }

    private static ScratchWorldReservation reservationForCreatedScratch(World world, Throwable failure) {
        try {
            NamespacedKey key = WorldIdentity.key(world);
            String name = IrisWorldStorage.logicalName(key);
            if (!isReservedScratchWorldName(name)) {
                failure.addSuppressed(new IllegalStateException(
                        "Refusing cleanup for unexpected scratch world identity \"" + key + "\"."));
                return null;
            }
            Path folder = IrisWorldStorage.requireSafeManagedDimensionRoot(key)
                    .toPath()
                    .toAbsolutePath()
                    .normalize();
            return new ScratchWorldReservation(name, key, folder);
        } catch (Throwable identityFailure) {
            failure.addSuppressed(identityFailure);
            return null;
        }
    }

    private static void deleteScratchFolder(ScratchWorldState state, World world) throws IOException {
        deleteScratchFolder(state.reservation(), world);
    }

    private static void deleteScratchFolder(ScratchWorldReservation reservation, World world) throws IOException {
        Path actualFolder = world.getWorldFolder().toPath().toAbsolutePath().normalize();
        if (!reservation.folder().equals(actualFolder)) {
            throw new IOException("Scratch world storage changed during import; refusing deletion.");
        }
        if (WorldIdentity.resolve(reservation.key()).isPresent()) {
            throw new IOException("Scratch world is still loaded; refusing deletion.");
        }
        AtomicDirectoryPublisher.deleteTree(reservation.folder());
    }

    private static void queueScratchCleanup(String worldName, Throwable failure) {
        try {
            WorldDeletionQueue queue = IrisServices.getOrNull(WorldDeletionQueue.class);
            if (queue == null) {
                throw new IllegalStateException("World deletion queue is unavailable.");
            }
            queue.queueExactForStartupDeletion(List.of(worldName));
        } catch (Throwable queueFailure) {
            if (failure != null) {
                failure.addSuppressed(queueFailure);
            }
            IrisLogging.reportError("Failed to queue startup cleanup for feature-import scratch world \""
                    + worldName + "\".", queueFailure);
        }
    }

    private static Throwable unwrapFailure(Throwable throwable) {
        Throwable cursor = throwable;
        while (cursor instanceof CompletionException || cursor instanceof ExecutionException) {
            if (cursor.getCause() == null) {
                break;
            }
            cursor = cursor.getCause();
        }
        return cursor;
    }

    static CompletableFuture<Void> sequenceScratchTeardown(
            Supplier<CompletableFuture<Boolean>> unload,
            BooleanSupplier stillLoaded,
            Supplier<CompletableFuture<Void>> closeGenerator,
            Supplier<CompletableFuture<Void>> deleteFolder,
            String worldName
    ) {
        return sequenceScratchTeardown(
                unload,
                stillLoaded,
                closeGenerator,
                deleteFolder,
                worldName,
                () -> false);
    }

    static CompletableFuture<Void> sequenceScratchTeardown(
            Supplier<CompletableFuture<Boolean>> unload,
            BooleanSupplier stillLoaded,
            Supplier<CompletableFuture<Void>> closeGenerator,
            Supplier<CompletableFuture<Void>> deleteFolder,
            String worldName,
            BooleanSupplier terminalTimeout
    ) {
        CompletableFuture<Boolean> unloadFuture;
        try {
            unloadFuture = unload.get();
        } catch (Throwable failure) {
            return CompletableFuture.failedFuture(failure);
        }
        if (unloadFuture == null) {
            return CompletableFuture.failedFuture(new IllegalStateException("Scratch world unload returned no completion future."));
        }

        return unloadFuture.thenCompose(unloaded -> {
            if (terminalTimeout.getAsBoolean()) {
                return CompletableFuture.failedFuture(new TimeoutException(
                        "Scratch world cleanup stopped after its terminal timeout."));
            }
            if (!Boolean.TRUE.equals(unloaded) || stillLoaded.getAsBoolean()) {
                return CompletableFuture.failedFuture(new IllegalStateException(
                        "Scratch world unload was not confirmed for \"" + worldName + "\"."));
            }
            return invokeScratchPhase(closeGenerator, "generator close");
        }).thenCompose(ignored -> {
            if (terminalTimeout.getAsBoolean()) {
                return CompletableFuture.failedFuture(new TimeoutException(
                        "Scratch world cleanup stopped after its terminal timeout."));
            }
            return invokeScratchPhase(deleteFolder, "folder deletion");
        });
    }

    private static CompletableFuture<Void> guardScratchTeardown(
            CompletableFuture<Void> source,
            AtomicBoolean terminalTimeout,
            String worldName
    ) {
        CompletableFuture<Void> guarded = new CompletableFuture<>();
        AtomicBoolean settled = new AtomicBoolean(false);
        source.whenComplete((ignored, throwable) -> {
            if (!settled.compareAndSet(false, true)) {
                return;
            }
            if (throwable == null) {
                guarded.complete(null);
            } else {
                guarded.completeExceptionally(throwable);
            }
        });
        CompletableFuture.delayedExecutor(SCRATCH_TEARDOWN_TIMEOUT_SECONDS, TimeUnit.SECONDS).execute(() -> {
            if (!settled.compareAndSet(false, true)) {
                return;
            }
            terminalTimeout.set(true);
            TimeoutException timeout = new TimeoutException(
                    "Scratch world cleanup did not settle within " + SCRATCH_TEARDOWN_TIMEOUT_SECONDS
                            + " seconds for \"" + worldName + "\".");
            ServerConfigurator.restart("Feature-import scratch cleanup timed out for \"" + worldName + "\".");
            guarded.completeExceptionally(timeout);
        });
        return guarded;
    }

    private static CompletableFuture<Void> invokeScratchPhase(
            Supplier<CompletableFuture<Void>> phase,
            String phaseName
    ) {
        try {
            CompletableFuture<Void> future = phase.get();
            if (future == null) {
                return CompletableFuture.failedFuture(new IllegalStateException(
                        "Scratch world " + phaseName + " returned no completion future."));
            }
            return future;
        } catch (Throwable failure) {
            return CompletableFuture.failedFuture(failure);
        }
    }

    private static boolean containsTimeout(Throwable throwable) {
        Throwable cursor = throwable;
        while (cursor != null) {
            if (cursor instanceof TimeoutException) {
                return true;
            }
            cursor = cursor.getCause();
        }
        return false;
    }

    static boolean isReservedScratchWorldName(String worldName) {
        if (worldName == null || !worldName.startsWith(SCRATCH_WORLD_PREFIX)) {
            return false;
        }
        try {
            String identifier = worldName.substring(SCRATCH_WORLD_PREFIX.length());
            return UUID.fromString(identifier).toString().equals(identifier);
        } catch (IllegalArgumentException failure) {
            return false;
        }
    }

    private record Row(String group, String key, String safeName) {
    }

    private record CaptureResult(boolean placed, IrisObject object) {
    }

    private record ScratchWorldReservation(String name, NamespacedKey key, Path folder) {
    }

    private record ScratchWorldState(
            ScratchWorldReservation reservation,
            LifecycleOperationCoordinator.Lease lease
    ) {
    }
}
