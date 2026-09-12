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

package art.arcane.iris.pack;

import art.arcane.iris.localization.IrisLanguage;
import art.arcane.iris.localization.PackDownloadMessages;
import art.arcane.iris.pack.loading.IrisData;
import art.arcane.iris.generation.terrain.IrisDimension;
import art.arcane.iris.spi.IrisLogging;
import art.arcane.iris.spi.IrisPlatforms;
import art.arcane.iris.platform.bootstrap.WebCache;
import art.arcane.volmlib.util.io.IO;
import art.arcane.volmlib.util.localization.MessageArgument;
import org.zeroturnaround.zip.commons.FileUtils;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.io.OutputStream;
import java.net.URI;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public final class PackDownloader {
    private static final String DEFAULT_OVERWORLD_PACK = "overworld";
    private static final String DEFAULT_OVERWORLD_URL =
            "https://github.com/IrisDimensions/overworld/releases/latest/download/overworld.zip";
    private static final String UNDERWORLD_PACK = "underworld";
    private static final String UNDERWORLD_URL =
            "https://github.com/IrisDimensions/underworld/releases/latest/download/underworld.zip";
    private static final List<String> BUILT_IN_PACK_KEYS = List.of(DEFAULT_OVERWORLD_PACK, UNDERWORLD_PACK);
    private static final Map<String, String> BUILT_IN_PACK_URLS = Map.of(
            DEFAULT_OVERWORLD_PACK, DEFAULT_OVERWORLD_URL,
            UNDERWORLD_PACK, UNDERWORLD_URL
    );
    private static final Pattern PACK_KEY = Pattern.compile("[a-z0-9_-]+");
    private static final ArchiveLimits ARCHIVE_LIMITS = new ArchiveLimits(
            512L * 1024L * 1024L,
            100_000,
            2L * 1024L * 1024L * 1024L,
            256L * 1024L * 1024L
    );
    private static final ConcurrentHashMap<String, DownloadLock> DOWNLOAD_LOCKS = new ConcurrentHashMap<>();
    private static final AtomicBoolean DOWNLOAD_ACTIVE = new AtomicBoolean();
    private static final DownloadProgressListener NO_DOWNLOAD_PROGRESS = progress -> {
    };

    private PackDownloader() {
    }

    public static boolean isDefaultOverworld(String pack) {
        return DEFAULT_OVERWORLD_PACK.equals(pack);
    }

    public static boolean isBuiltInPack(String pack) {
        return pack != null && BUILT_IN_PACK_URLS.containsKey(pack);
    }

    public static List<String> builtInPacks() {
        return BUILT_IN_PACK_KEYS;
    }

    public static String downloadCommandFor(String pack) {
        return isBuiltInPack(pack)
                ? "/iris download pack=" + pack
                : "/iris download link=<zip-url>";
    }

    public static boolean isDirectZipUrl(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }
        try {
            URI uri = URI.create(value.trim());
            String scheme = uri.getScheme();
            String path = uri.getPath();
            return ("https".equalsIgnoreCase(scheme) || "http".equalsIgnoreCase(scheme))
                    && uri.getHost() != null
                    && !uri.getHost().isBlank()
                    && path != null
                    && path.toLowerCase(Locale.ROOT).endsWith(".zip");
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    public static String defaultOverworldPack() {
        return DEFAULT_OVERWORLD_PACK;
    }

    /**
     * Whether a pack folder for {@code key} already exists with at least one dimension file.
     * Presence is judged on disk, not on loadability: a pack that exists but fails to parse must
     * surface as an error, never as a redownload. A folder without any dimensions/*.json is a
     * partial import (an interrupted copy) and counts as absent so it can be replaced.
     */
    public static boolean isPackPresent(File packsFolder, String key) {
        if (packsFolder == null || !isSafePackKey(key)) {
            return false;
        }
        Path packsRoot = packsFolder.toPath().toAbsolutePath().normalize();
        File resolvedPack = PackDirectoryResolver.resolveExisting(packsFolder, key);
        if (resolvedPack == null) {
            return false;
        }
        try {
            PackDirectoryResolver.requireSafePackTree(resolvedPack);
        } catch (IOException exception) {
            return false;
        }
        Path pack = resolvedPack.toPath().toAbsolutePath().normalize();
        Path dimensions = pack.resolve("dimensions");
        if (!Objects.equals(pack.getParent(), packsRoot)
                || Files.isSymbolicLink(dimensions)
                || !Files.isDirectory(pack)
                || !Files.isDirectory(dimensions, LinkOption.NOFOLLOW_LINKS)) {
            return false;
        }
        try (Stream<Path> entries = Files.list(dimensions)) {
            return entries.anyMatch(path -> path.getFileName().toString().endsWith(".json")
                    && !Files.isSymbolicLink(path)
                    && Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS));
        } catch (IOException exception) {
            return false;
        }
    }

    public static boolean isBuiltInPackPresent(File packsFolder, String key) {
        if (!isBuiltInPack(key) || !isPackPresent(packsFolder, key)) {
            return false;
        }
        File resolvedPack = PackDirectoryResolver.resolveExisting(packsFolder, key);
        if (resolvedPack == null) {
            return false;
        }
        Path primaryDimension = resolvedPack.toPath().toAbsolutePath().normalize()
                .resolve("dimensions")
                .resolve(key + ".json");
        return !Files.isSymbolicLink(primaryDimension)
                && Files.isRegularFile(primaryDimension, LinkOption.NOFOLLOW_LINKS);
    }

    public static PackInstallResult downloadDefaultOverworld(File packsFolder, boolean forceOverwrite, Consumer<String> feedback) throws IOException {
        return downloadBuiltIn(packsFolder, DEFAULT_OVERWORLD_PACK, forceOverwrite, feedback);
    }

    public static PackInstallResult downloadDefaultOverworld(File packsFolder, boolean forceOverwrite,
                                                             Consumer<String> feedback,
                                                             DownloadProgressListener progressListener) throws IOException {
        return downloadBuiltIn(packsFolder, DEFAULT_OVERWORLD_PACK, forceOverwrite, feedback, progressListener);
    }

    public static PackInstallResult downloadBuiltIn(File packsFolder, String pack, boolean forceOverwrite,
                                                    Consumer<String> feedback) throws IOException {
        return downloadBuiltIn(
                packsFolder,
                pack,
                forceOverwrite,
                feedback,
                new DownloadCancellation(),
                NO_DOWNLOAD_PROGRESS
        );
    }

    public static PackInstallResult downloadBuiltIn(File packsFolder, String pack, boolean forceOverwrite,
                                                    Consumer<String> feedback,
                                                    DownloadProgressListener progressListener) throws IOException {
        return downloadBuiltIn(
                packsFolder,
                pack,
                forceOverwrite,
                feedback,
                new DownloadCancellation(),
                progressListener
        );
    }

    public static PackInstallResult downloadBuiltIn(File packsFolder, String pack, boolean forceOverwrite,
                                                    Consumer<String> feedback, DownloadCancellation cancellation) throws IOException {
        return downloadBuiltIn(
                packsFolder,
                pack,
                forceOverwrite,
                feedback,
                cancellation,
                NO_DOWNLOAD_PROGRESS
        );
    }

    public static PackInstallResult downloadBuiltIn(File packsFolder, String pack, boolean forceOverwrite,
                                                    Consumer<String> feedback, DownloadCancellation cancellation,
                                                    DownloadProgressListener progressListener) throws IOException {
        String url = pack == null ? null : BUILT_IN_PACK_URLS.get(pack);
        if (url == null) {
            throw new IllegalArgumentException("Pack '" + pack + "' is not a built-in Iris download");
        }
        return downloadArchive(
                packsFolder,
                url,
                forceOverwrite,
                pack,
                feedback,
                cancellation,
                progressListener
        );
    }

    public static PackInstallResult downloadUrl(File packsFolder, String url, boolean forceOverwrite,
                                                Consumer<String> feedback) throws IOException {
        return downloadUrl(
                packsFolder,
                url,
                forceOverwrite,
                feedback,
                new DownloadCancellation(),
                NO_DOWNLOAD_PROGRESS
        );
    }

    public static PackInstallResult downloadUrl(File packsFolder, String url, boolean forceOverwrite,
                                                Consumer<String> feedback,
                                                DownloadProgressListener progressListener) throws IOException {
        return downloadUrl(
                packsFolder,
                url,
                forceOverwrite,
                feedback,
                new DownloadCancellation(),
                progressListener
        );
    }

    public static PackInstallResult downloadUrl(File packsFolder, String url, boolean forceOverwrite,
                                                Consumer<String> feedback, DownloadCancellation cancellation) throws IOException {
        return downloadUrl(
                packsFolder,
                url,
                forceOverwrite,
                feedback,
                cancellation,
                NO_DOWNLOAD_PROGRESS
        );
    }

    public static PackInstallResult downloadUrl(File packsFolder, String url, boolean forceOverwrite,
                                                Consumer<String> feedback, DownloadCancellation cancellation,
                                                DownloadProgressListener progressListener) throws IOException {
        if (!isDirectZipUrl(url)) {
            throw new IllegalArgumentException("Pack URL must be an HTTP or HTTPS .zip link");
        }
        return downloadArchive(
                packsFolder,
                url.trim(),
                forceOverwrite,
                null,
                feedback,
                cancellation,
                progressListener
        );
    }

    private static PackInstallResult downloadArchive(File packsFolder, String url, boolean forceOverwrite,
                                                     String expectedKey, Consumer<String> feedback,
                                                     DownloadCancellation cancellation,
                                                     DownloadProgressListener progressListener) throws IOException {
        Objects.requireNonNull(packsFolder, "packsFolder");
        DownloadCancellation control = Objects.requireNonNull(cancellation, "cancellation");
        Consumer<String> output = feedback == null ? ignored -> {
        } : feedback;
        DownloadProgressListener progress = progressListener == null ? NO_DOWNLOAD_PROGRESS : progressListener;
        if (expectedKey != null && !expectedKey.isBlank() && !isSafePackKey(expectedKey)) {
            throw new IllegalArgumentException("Invalid expected pack key '" + expectedKey + "'");
        }
        if (!DOWNLOAD_ACTIVE.compareAndSet(false, true)) {
            throw new PackDownloadBusyException();
        }
        try {
            control.attachCurrentThread();
            control.checkpoint();
            String lockKey = expectedKey != null && !expectedKey.isBlank()
                    ? "key:" + expectedKey
                    : "url:" + IO.hash(url);
            return withDownloadLock(lockKey, () -> {
                control.checkpoint();
                boolean present = isBuiltInPack(expectedKey)
                        ? isBuiltInPackPresent(packsFolder, expectedKey)
                        : isPackPresent(packsFolder, expectedKey);
                if (!forceOverwrite && present) {
                    sendFeedback(output, IrisLanguage.plain(PackDownloadMessages.ALREADY_INSTALLED, MessageArgument.untrusted("key", expectedKey)));
                    return new PackInstallResult(expectedKey, false, false);
                }
                return downloadLocked(
                        packsFolder,
                        url,
                        forceOverwrite,
                        expectedKey,
                        lockKey,
                        output,
                        control,
                        progress
                );
            });
        } finally {
            control.complete();
            DOWNLOAD_ACTIVE.set(false);
        }
    }

    private static PackInstallResult downloadLocked(File packsFolder, String url, boolean forceOverwrite,
                                                    String expectedKey, String heldLockKey, Consumer<String> feedback,
                                                    DownloadCancellation cancellation,
                                                    DownloadProgressListener progressListener) throws IOException {
        cancellation.checkpoint();
        String source = expectedKey == null || expectedKey.isBlank()
                ? IrisLanguage.plain(PackDownloadMessages.PROGRESS_SOURCE_REMOTE)
                : expectedKey;
        sendProgress(progressListener, DownloadProgress.phase(DownloadPhase.CONNECTING));
        sendFeedback(feedback, IrisLanguage.plain(
                PackDownloadMessages.DOWNLOADING,
                MessageArgument.untrusted("url", source)
        ) + " ");
        File zip;
        try {
            zip = WebCache.getNonCachedFile(
                    "pack-archive",
                    url,
                    ARCHIVE_LIMITS.maxArchiveBytes(),
                    transfer -> sendProgress(progressListener, DownloadProgress.transfer(transfer))
            );
        } catch (InterruptedIOException exception) {
            cancellation.checkpoint();
            throw exception;
        }
        cancellation.checkpoint();
        File temp = WebCache.getTemp();
        File work = new File(temp, "dl-" + UUID.randomUUID());

        try {
            if (!zip.exists()) {
                throw new IOException("Downloaded pack archive is missing before unpacking.");
            }
            sendProgress(progressListener, DownloadProgress.phase(DownloadPhase.UNPACKING));
            sendFeedback(feedback, IrisLanguage.plain(
                    PackDownloadMessages.UNPACKING,
                    MessageArgument.untrusted("repository", source)
            ));
            try {
                unpackArchive(zip.toPath(), work.toPath(), ARCHIVE_LIMITS, cancellation);
            } catch (IOException exception) {
                if (exception instanceof PackDownloadCancelledException) {
                    throw exception;
                }
                IrisLogging.reportError(exception);
                sendFeedback(feedback, IrisLanguage.plain(PackDownloadMessages.UNPACK_FAILED));
                return null;
            }
            cancellation.checkpoint();
            File[] zipFiles = work.listFiles();
            if (zipFiles == null) {
                sendFeedback(feedback, IrisLanguage.plain(PackDownloadMessages.NO_EXTRACTED_FILES));
                return null;
            }
            File directory = findExtractedPackDirectory(work, zipFiles);
            if (directory == null) {
                sendFeedback(feedback, IrisLanguage.plain(PackDownloadMessages.INVALID_ARCHIVE_FORMAT));
                return null;
            }
            return installExtractedPack(
                    packsFolder,
                    directory,
                    forceOverwrite,
                    expectedKey,
                    heldLockKey,
                    feedback,
                    cancellation,
                    progressListener
            );
        } finally {
            deleteDirectory(work);
        }
    }

    private static File findExtractedPackDirectory(File work, File[] extractedFiles) {
        if (new File(work, "dimensions").isDirectory()) {
            return work;
        }
        File candidate = null;
        for (File extracted : extractedFiles) {
            if (!extracted.isDirectory() || !new File(extracted, "dimensions").isDirectory()) {
                continue;
            }
            if (candidate != null) {
                return work;
            }
            candidate = extracted;
        }
        if (candidate != null) {
            return candidate;
        }
        return extractedFiles.length == 1 && extractedFiles[0].isDirectory() ? extractedFiles[0] : work;
    }

    static PackInstallResult installExtractedPack(File packsFolder, File extractedPack, boolean forceOverwrite,
                                                  String expectedKey, Consumer<String> feedback) throws IOException {
        Objects.requireNonNull(packsFolder, "packsFolder");
        Objects.requireNonNull(extractedPack, "extractedPack");
        Consumer<String> output = feedback == null ? ignored -> {
        } : feedback;
        return installExtractedPack(
                packsFolder,
                extractedPack,
                forceOverwrite,
                expectedKey,
                null,
                output,
                null,
                NO_DOWNLOAD_PROGRESS
        );
    }

    private static PackInstallResult installExtractedPack(File packsFolder, File extractedPack, boolean forceOverwrite,
                                                          String expectedKey, String heldLockKey, Consumer<String> feedback,
                                                          DownloadCancellation cancellation,
                                                          DownloadProgressListener progressListener) throws IOException {
        if (expectedKey != null && !expectedKey.isBlank() && !isSafePackKey(expectedKey)) {
            throw new IllegalArgumentException("Invalid expected pack key '" + expectedKey + "'");
        }
        Path packsRoot = packsFolder.toPath().toAbsolutePath().normalize();
        Files.createDirectories(packsRoot);
        Path staging = packsRoot.resolve(".iris-import-" + UUID.randomUUID());
        try {
            checkpoint(cancellation);
            if (cancellation == null) {
                FileUtils.copyDirectory(extractedPack, staging.toFile());
            } else {
                copyDirectory(extractedPack.toPath(), staging, cancellation);
            }
            checkpoint(cancellation);
            sendProgress(progressListener, DownloadProgress.phase(DownloadPhase.VALIDATING));
            PreparedPack prepared = prepareStagedPack(staging.toFile(), expectedKey, feedback);
            if (prepared == null) {
                return null;
            }
            checkpoint(cancellation);
            String destinationLockKey = "key:" + prepared.key();
            if (destinationLockKey.equals(heldLockKey)) {
                return publishPreparedPack(
                        packsFolder,
                        packsRoot,
                        staging,
                        prepared,
                        forceOverwrite,
                        feedback,
                        cancellation,
                        progressListener
                );
            }
            return withDownloadLock(destinationLockKey,
                    () -> publishPreparedPack(
                            packsFolder,
                            packsRoot,
                            staging,
                            prepared,
                            forceOverwrite,
                            feedback,
                            cancellation,
                            progressListener
                    ));
        } finally {
            deleteDirectory(staging.toFile());
        }
    }

    private static PreparedPack prepareStagedPack(File staging, String expectedKey,
                                                  Consumer<String> feedback) throws IOException {
        IrisData data = IrisData.openDatapackCompiler(staging);
        String key;
        String name;
        try {
            String[] dimensions = data.getDimensionLoader().getPossibleKeys();
            if (dimensions == null || dimensions.length == 0) {
                sendFeedback(feedback, IrisLanguage.plain(PackDownloadMessages.NO_DIMENSION_FILE));
                sendFeedback(feedback, IrisLanguage.plain(PackDownloadMessages.CHECK_GITHUB));
                return null;
            }
            String selectedDimension = selectDimensionKey(dimensions, expectedKey, feedback);
            if (selectedDimension == null) {
                return null;
            }
            IrisDimension dimension = data.getDimensionLoader().load(selectedDimension);
            if (dimension == null) {
                sendFeedback(feedback, IrisLanguage.plain(PackDownloadMessages.INVALID_DIMENSION));
                return null;
            }
            key = dimension.getLoadKey();
            name = dimension.getName();
        } finally {
            data.close();
        }
        if (!isSafePackKey(key)) {
            throw new IOException("Downloaded pack has unsafe dimension key '" + key + "'");
        }
        if (expectedKey != null && !expectedKey.isBlank() && !expectedKey.equals(key)) {
            throw new IOException("Downloaded pack key '" + key + "' does not match requested key '" + expectedKey + "'");
        }

        PackValidationResult stagedValidation;
        try {
            stagedValidation = PackValidator.validateForDatapackBootstrap(staging);
        } catch (RuntimeException exception) {
            throw new IOException("Pack validation failed before publication for '" + key + "'", exception);
        }
        PackValidationResult validation = new PackValidationResult(
                key,
                stagedValidation.getBlockingErrors(),
                stagedValidation.getWarnings(),
                stagedValidation.getValidatedAtMillis()
        );
        if (!validation.isLoadable()) {
            sendValidationFeedback(validation, feedback);
            return null;
        }
        sendFeedback(feedback, IrisLanguage.plain(
                PackDownloadMessages.IMPORTING,
                MessageArgument.untrusted("name", name),
                MessageArgument.untrusted("key", key)
        ));
        return new PreparedPack(key, name, validation);
    }

    private static String selectDimensionKey(String[] dimensions, String expectedKey,
                                             Consumer<String> feedback) throws IOException {
        if (expectedKey == null || expectedKey.isBlank()) {
            return Arrays.stream(dimensions)
                    .min((left, right) -> {
                        int lengthComparison = Integer.compare(left.length(), right.length());
                        return lengthComparison == 0 ? left.compareTo(right) : lengthComparison;
                    })
                    .orElseThrow(() -> new IOException("Downloaded pack contains no dimensions"));
        }

        int matches = 0;
        for (String dimension : dimensions) {
            if (expectedKey.equals(dimension)) {
                matches++;
            }
        }
        if (matches != 1) {
            throw new IOException("Downloaded pack dimensions " + Arrays.toString(dimensions)
                    + " do not contain exactly one requested key '" + expectedKey + "'");
        }
        return expectedKey;
    }

    private static PackInstallResult publishPreparedPack(File packsFolder, Path packsRoot, Path staging, PreparedPack prepared,
                                                         boolean forceOverwrite, Consumer<String> feedback,
                                                         DownloadCancellation cancellation,
                                                         DownloadProgressListener progressListener) throws IOException {
        sendProgress(progressListener, DownloadProgress.phase(DownloadPhase.PUBLISHING));
        Path target = packsRoot.resolve(prepared.key()).normalize();
        if (!Objects.equals(target.getParent(), packsRoot)) {
            throw new IOException("Pack target escapes the packs folder: " + target);
        }
        if (Files.isSymbolicLink(target)) {
            sendFeedback(feedback, "Pack '" + prepared.key() + "' is a symbolic-link source and cannot be replaced by Iris.");
            return null;
        }

        Path conflictingPack = findConflictingPack(packsRoot, staging, target, prepared.key());
        if (conflictingPack != null) {
            sendFeedback(feedback, IrisLanguage.plain(
                    PackDownloadMessages.DIMENSION_KEY_CONFLICT,
                    MessageArgument.untrusted("key", prepared.key())
            ));
            return null;
        }
        boolean present = isBuiltInPack(prepared.key())
                ? isBuiltInPackPresent(packsRoot.toFile(), prepared.key())
                : isPackPresent(packsRoot.toFile(), prepared.key());
        if (!forceOverwrite && present) {
            sendFeedback(feedback, IrisLanguage.plain(
                    PackDownloadMessages.PACK_KEY_CONFLICT,
                    MessageArgument.untrusted("key", prepared.key())
            ));
            return null;
        }
        if (!forceOverwrite && Files.exists(target) && !present) {
            IrisLogging.warn("Replacing partial pack folder " + target
                    + " (required primary dimension is missing).");
        }

        // A registered loader alone is not "active": startup validation registers a loader for
        // every visible pack (permanently), which made force-updating any installed pack
        // impossible. Live engines only ever attach to detached openRuntime loaders, so the
        // gate must go through the engine index, not the cached loader's own engine list.
        // Stale cached registrations are closed so the swap cannot race a loader holding the
        // old tree.
        if (IrisData.hasActiveEngines(target.toFile())) {
            sendFeedback(
                    feedback,
                    "Pack '" + prepared.key() + "' is active and cannot be replaced safely. Unload its worlds before retrying."
            );
            return null;
        }
        if (cancellation != null) {
            cancellation.beginPublication();
        }
        IrisData.getLoaded(new File(packsFolder, prepared.key())).ifPresent(IrisData::close);
        IrisData.getLoaded(target.toFile()).ifPresent(IrisData::close);
        try (AtomicDirectoryPublisher.Publication publication = AtomicDirectoryPublisher.publish(staging, target)) {
            publication.commit();
            try {
                publication.cleanupBackup();
            } catch (IOException exception) {
                IrisLogging.reportError(
                        "Pack '" + prepared.key() + "' was published, but its transaction backup could not be cleaned.",
                        exception
                );
            }
        }
        PackValidationRegistry.publish(prepared.validation());
        sendValidationFeedback(prepared.validation(), feedback);
        sendFeedback(feedback, IrisLanguage.plain(
                PackDownloadMessages.ACQUIRED,
                MessageArgument.untrusted("name", prepared.name())
        ));
        sendProgress(progressListener, DownloadProgress.terminal());
        return new PackInstallResult(prepared.key(), true, true);
    }

    private static Path findConflictingPack(Path packsRoot, Path staging, Path target, String key) throws IOException {
        Set<Path> roots = new LinkedHashSet<>();
        roots.add(packsRoot);
        if (IrisPlatforms.isBound()) {
            roots.add(IrisPlatforms.get().packsFolder().toPath().toAbsolutePath().normalize());
        }
        for (Path root : roots) {
            if (!Files.isDirectory(root)) {
                continue;
            }
            try (Stream<Path> entries = Files.list(root)) {
                List<Path> candidates = entries.toList();
                for (Path candidate : candidates) {
                    Path normalized = candidate.toAbsolutePath().normalize();
                    String candidateName = candidate.getFileName().toString();
                    if (normalized.equals(staging)
                            || normalized.equals(target)
                            || PackDirectoryResolver.isHiddenName(candidateName)
                            || !PackDirectoryResolver.isVisiblePackDirectory(candidate.toFile())) {
                        continue;
                    }
                    Path dimension = candidate.resolve("dimensions").resolve(key + ".json");
                    if (Files.isRegularFile(dimension)) {
                        return normalized;
                    }
                }
            }
        }
        return null;
    }

    private static boolean isSafePackKey(String key) {
        return key != null && PACK_KEY.matcher(key).matches();
    }

    private static PackInstallResult withDownloadLock(String key, DownloadOperation operation) throws IOException {
        DownloadLock lock = DOWNLOAD_LOCKS.compute(key, (ignored, existing) -> {
            DownloadLock selected = existing == null ? new DownloadLock() : existing;
            selected.references++;
            return selected;
        });
        try {
            synchronized (lock) {
                return operation.run();
            }
        } finally {
            DOWNLOAD_LOCKS.computeIfPresent(key, (ignored, existing) -> {
                if (existing != lock) {
                    return existing;
                }
                existing.references--;
                return existing.references == 0 ? null : existing;
            });
        }
    }

    static int downloadLockCount() {
        return DOWNLOAD_LOCKS.size();
    }

    static void unpackArchive(Path archive, Path destination, ArchiveLimits limits) throws IOException {
        unpackArchive(archive, destination, limits, null);
    }

    static void unpackArchive(Path archive, Path destination, ArchiveLimits limits,
                              DownloadCancellation cancellation) throws IOException {
        Path source = Objects.requireNonNull(archive, "archive").toAbsolutePath().normalize();
        Path root = Objects.requireNonNull(destination, "destination").toAbsolutePath().normalize();
        ArchiveLimits safety = Objects.requireNonNull(limits, "limits");
        checkpoint(cancellation);
        if (Files.isSymbolicLink(source) || !Files.isRegularFile(source, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("Pack archive is missing or unsafe: " + source);
        }
        if (Files.size(source) > safety.maxArchiveBytes()) {
            throw new IOException("Pack archive exceeds the compressed size limit.");
        }
        if (Files.exists(root, LinkOption.NOFOLLOW_LINKS) && !Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("Pack extraction target is not a directory: " + root);
        }
        Files.createDirectories(root);
        if (Files.isSymbolicLink(root)) {
            throw new IOException("Pack extraction target is unsafe: " + root);
        }

        int entryCount = 0;
        long expandedBytes = 0L;
        Set<String> paths = new HashSet<>();
        try (InputStream input = Files.newInputStream(source); ZipInputStream zip = new ZipInputStream(input)) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                checkpoint(cancellation);
                entryCount++;
                if (entryCount > safety.maxEntries()) {
                    throw new IOException("Pack archive contains too many entries.");
                }
                String normalizedName = normalizeArchiveEntry(entry.getName());
                String collisionKey = normalizedName.toLowerCase(Locale.ROOT);
                if (!paths.add(collisionKey)) {
                    throw new IOException("Pack archive contains a duplicate path: " + normalizedName);
                }
                Path output = root.resolve(normalizedName).normalize();
                if (!output.startsWith(root)) {
                    throw new IOException("Pack archive entry escapes extraction: " + entry.getName());
                }
                if (entry.isDirectory()) {
                    Files.createDirectories(output);
                    zip.closeEntry();
                    continue;
                }
                long declaredSize = entry.getSize();
                if (declaredSize > safety.maxEntryBytes()) {
                    throw new IOException("Pack archive entry exceeds the file size limit: " + normalizedName);
                }
                Files.createDirectories(output.getParent());
                long entryBytes = 0L;
                try (OutputStream file = Files.newOutputStream(output, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
                    byte[] buffer = new byte[8192];
                    int read;
                    while ((read = zip.read(buffer)) != -1) {
                        checkpoint(cancellation);
                        if (read == 0) {
                            continue;
                        }
                        entryBytes += read;
                        expandedBytes += read;
                        if (entryBytes > safety.maxEntryBytes()) {
                            throw new IOException("Pack archive entry exceeds the file size limit: " + normalizedName);
                        }
                        if (expandedBytes > safety.maxExpandedBytes()) {
                            throw new IOException("Pack archive expands beyond the safety limit.");
                        }
                        file.write(buffer, 0, read);
                    }
                }
                zip.closeEntry();
            }
        }
        if (entryCount == 0) {
            throw new IOException("Pack archive is empty.");
        }
    }

    private static void copyDirectory(Path source, Path destination,
                                      DownloadCancellation cancellation) throws IOException {
        Path sourceRoot = source.toAbsolutePath().normalize();
        Path destinationRoot = destination.toAbsolutePath().normalize();
        Files.walkFileTree(sourceRoot, new SimpleFileVisitor<Path>() {
            private final byte[] buffer = new byte[8192];

            @Override
            public FileVisitResult preVisitDirectory(Path directory, BasicFileAttributes attributes) throws IOException {
                cancellation.checkpoint();
                Files.createDirectories(destinationRoot.resolve(sourceRoot.relativize(directory)));
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attributes) throws IOException {
                cancellation.checkpoint();
                if (Files.isSymbolicLink(file)) {
                    throw new IOException("Downloaded pack contains an unsafe symbolic link: " + file);
                }
                Path target = destinationRoot.resolve(sourceRoot.relativize(file));
                try (InputStream input = Files.newInputStream(file);
                     OutputStream output = Files.newOutputStream(
                             target,
                             StandardOpenOption.CREATE_NEW,
                             StandardOpenOption.WRITE
                     )) {
                    int read;
                    while ((read = input.read(buffer)) != -1) {
                        cancellation.checkpoint();
                        if (read > 0) {
                            output.write(buffer, 0, read);
                        }
                    }
                }
                return FileVisitResult.CONTINUE;
            }
        });
    }

    private static void checkpoint(DownloadCancellation cancellation) throws PackDownloadCancelledException {
        if (cancellation != null) {
            cancellation.checkpoint();
        }
    }

    private static String normalizeArchiveEntry(String rawName) throws IOException {
        if (rawName == null || rawName.isBlank() || rawName.indexOf('\0') >= 0
                || rawName.startsWith("/") || rawName.startsWith("\\")) {
            throw new IOException("Pack archive contains an invalid path.");
        }
        String slashNormalized = rawName.replace('\\', '/');
        if (slashNormalized.matches("^[A-Za-z]:.*")) {
            throw new IOException("Pack archive contains an unsafe path: " + rawName);
        }
        Path normalized = Path.of(slashNormalized).normalize();
        String result = normalized.toString().replace('\\', '/');
        if (normalized.isAbsolute() || normalized.startsWith("..") || result.isBlank() || ".".equals(result)) {
            throw new IOException("Pack archive contains an unsafe path: " + rawName);
        }
        return result;
    }

    private static void deleteDirectory(File directory) {
        try {
            AtomicDirectoryPublisher.deleteTree(directory.toPath());
        } catch (IOException exception) {
            IrisLogging.reportError("Failed to clean temporary pack directory '" + directory.getPath() + "'", exception);
        }
    }

    private static void sendFeedback(Consumer<String> feedback, String message) {
        try {
            feedback.accept(message);
        } catch (RuntimeException exception) {
            IrisLogging.reportError("Pack download feedback delivery failed", exception);
        }
    }

    private static void sendProgress(DownloadProgressListener listener, DownloadProgress progress) {
        try {
            listener.onProgress(progress);
        } catch (RuntimeException exception) {
            IrisLogging.reportError("Pack download progress delivery failed", exception);
        }
    }

    private static void sendValidationFeedback(PackValidationResult result, Consumer<String> feedback) {
        if (!result.isLoadable()) {
            sendFeedback(feedback, IrisLanguage.plain(
                    PackDownloadMessages.VALIDATION_FAILED,
                    MessageArgument.untrusted("pack", result.getPackName())
            ));
            for (String reason : result.getBlockingErrors()) {
                sendFeedback(feedback, IrisLanguage.plain(
                        PackDownloadMessages.VALIDATION_REASON,
                        MessageArgument.untrusted("reason", reason)
                ));
            }
            return;
        }
        if (!result.getWarnings().isEmpty()) {
            sendFeedback(feedback, IrisLanguage.plain(
                    PackDownloadMessages.VALIDATED_WITH_WARNINGS,
                    MessageArgument.untrusted("pack", result.getPackName()),
                    MessageArgument.trusted("count", result.getWarnings().size())
            ));
            return;
        }
        sendFeedback(feedback, IrisLanguage.plain(
                PackDownloadMessages.VALIDATED,
                MessageArgument.untrusted("pack", result.getPackName())
        ));
    }

    static String builtInPackUrl(String pack) {
        return BUILT_IN_PACK_URLS.get(pack);
    }

    private record PreparedPack(String key, String name, PackValidationResult validation) {
    }

    public record PackInstallResult(String key, boolean changed, boolean restartRequired) {
    }

    public record DownloadProgress(DownloadPhase phase, long transferredBytes, long totalBytes,
                                   long elapsedMillis, boolean complete) {
        private static DownloadProgress phase(DownloadPhase phase) {
            return new DownloadProgress(phase, 0L, -1L, 0L, false);
        }

        private static DownloadProgress transfer(WebCache.TransferProgress transfer) {
            return new DownloadProgress(
                    DownloadPhase.DOWNLOADING,
                    transfer.transferredBytes(),
                    transfer.contentLength(),
                    transfer.elapsedMillis(),
                    false
            );
        }

        private static DownloadProgress terminal() {
            return new DownloadProgress(DownloadPhase.PUBLISHING, 0L, -1L, 0L, true);
        }
    }

    public enum DownloadPhase {
        CONNECTING,
        DOWNLOADING,
        UNPACKING,
        VALIDATING,
        PUBLISHING
    }

    @FunctionalInterface
    public interface DownloadProgressListener {
        void onProgress(DownloadProgress progress);
    }

    public static final class PackDownloadBusyException extends IOException {
        public PackDownloadBusyException() {
            super(IrisLanguage.plain(PackDownloadMessages.IN_PROGRESS));
        }
    }

    public static final class PackDownloadCancelledException extends InterruptedIOException {
        private PackDownloadCancelledException() {
            super("Pack download cancelled.");
        }
    }

    public static final class DownloadCancellation {
        private final Object monitor = new Object();
        private boolean cancelled;
        private boolean publishing;
        private Thread worker;

        public void cancel() {
            Thread interruptTarget;
            synchronized (monitor) {
                cancelled = true;
                interruptTarget = publishing ? null : worker;
            }
            if (interruptTarget != null) {
                interruptTarget.interrupt();
            }
        }

        public boolean isPublishing() {
            synchronized (monitor) {
                return publishing;
            }
        }

        void attachCurrentThread() throws PackDownloadCancelledException {
            synchronized (monitor) {
                Thread current = Thread.currentThread();
                if (worker != null && worker != current) {
                    throw new IllegalStateException("Pack download cancellation is already attached to another thread.");
                }
                worker = current;
                checkCancelled(current);
            }
        }

        void checkpoint() throws PackDownloadCancelledException {
            synchronized (monitor) {
                checkCancelled(Thread.currentThread());
            }
        }

        void beginPublication() throws PackDownloadCancelledException {
            synchronized (monitor) {
                checkCancelled(Thread.currentThread());
                publishing = true;
            }
        }

        void complete() {
            boolean clearInterrupt;
            synchronized (monitor) {
                clearInterrupt = cancelled && worker == Thread.currentThread();
                publishing = false;
                worker = null;
            }
            if (clearInterrupt) {
                Thread.interrupted();
            }
        }

        private void checkCancelled(Thread current) throws PackDownloadCancelledException {
            if (cancelled || current.isInterrupted()) {
                throw new PackDownloadCancelledException();
            }
        }
    }

    record ArchiveLimits(long maxArchiveBytes, int maxEntries, long maxExpandedBytes, long maxEntryBytes) {
        ArchiveLimits {
            if (maxArchiveBytes < 1L || maxEntries < 1 || maxExpandedBytes < 1L || maxEntryBytes < 1L) {
                throw new IllegalArgumentException("Archive limits must be positive.");
            }
        }
    }

    @FunctionalInterface
    private interface DownloadOperation {
        PackInstallResult run() throws IOException;
    }

    private static final class DownloadLock {
        private int references;
    }
}
