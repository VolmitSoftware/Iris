package art.arcane.iris.core;

import art.arcane.iris.core.localization.IrisLanguage;
import art.arcane.iris.spi.IrisLogging;
import art.arcane.volmlib.util.hotload.ConfigHotloadEngine;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

public final class SettingsHotloadWatch implements AutoCloseable {
    public static final long POLL_PERIOD_MILLIS = 500L;
    public static final long HOTLOAD_COOLDOWN_MILLIS = 3_000L;
    private static final int MAX_HOTLOAD_BYTES = 2 * 1024 * 1024;
    private static final Timing PRODUCTION_TIMING = new Timing(
            POLL_PERIOD_MILLIS,
            HOTLOAD_COOLDOWN_MILLIS,
            ConfigHotloadEngine.DEFAULT_FULL_WATCH_SCAN_WINDOW_MS,
            ConfigHotloadEngine.DEFAULT_SIGNATURE_SCAN_WINDOW_MS
    );

    private final File settingsFile;
    private final File localeOverrideFolder;
    private final ConfigHotloadEngine hotloadEngine;
    private final Map<String, String> reportedCaptureFailures = new ConcurrentHashMap<>();
    private final Consumer<ConfigHotloadEngine.StableContentSnapshot> beforeSnapshotApply;
    private final BiConsumer<File, String> manualReloadListener;
    private volatile boolean closed;

    public SettingsHotloadWatch(File settingsFile) {
        this(settingsFile, PRODUCTION_TIMING);
    }

    SettingsHotloadWatch(File settingsFile, Timing timing) {
        this(settingsFile, timing, snapshot -> {
        });
    }

    SettingsHotloadWatch(
            File settingsFile,
            Timing timing,
            Consumer<ConfigHotloadEngine.StableContentSnapshot> beforeSnapshotApply
    ) {
        this.settingsFile = Objects.requireNonNull(settingsFile, "Settings file cannot be null").getAbsoluteFile();
        File dataFolder = Objects.requireNonNull(this.settingsFile.getParentFile(), "Settings data folder cannot be null");
        localeOverrideFolder = new File(dataFolder, "languages").getAbsoluteFile();
        Timing resolvedTiming = Objects.requireNonNull(timing, "Hotload timing cannot be null");
        this.beforeSnapshotApply = Objects.requireNonNull(beforeSnapshotApply, "Snapshot apply observer cannot be null");
        manualReloadListener = this::acknowledgeManualLocaleReload;
        hotloadEngine = new ConfigHotloadEngine(
                this::isManagedFile,
                this::knownFiles,
                this::readContent,
                this::normalizeContent,
                resolvedTiming.fullScanWindowMillis(),
                resolvedTiming.signatureScanWindowMillis()
        );
        hotloadEngine.configure(
                resolvedTiming.pollPeriodMillis(),
                resolvedTiming.cooldownMillis(),
                List.of(this.settingsFile),
                List.of(localeOverrideFolder)
        );
        IrisLanguage.addManualReloadListener(manualReloadListener);
    }

    public synchronized void checkConfigHotload() {
        if (closed) {
            return;
        }
        synchronized (IrisLanguage.class) {
            try {
                for (ConfigHotloadEngine.StableContentSnapshot snapshot : hotloadEngine.pollTouchedSnapshots()) {
                    beforeSnapshotApply.accept(snapshot);
                    hotloadEngine.processSnapshotChange(snapshot, this::applySnapshot, this::reportApplied);
                }
            } catch (RuntimeException failure) {
                IrisLogging.error("Iris settings and locale hotload watcher failed: " + failureDetail(failure));
                IrisLogging.reportError(failure);
            }
        }
    }

    @Override
    public synchronized void close() {
        if (closed) {
            return;
        }
        closed = true;
        IrisLanguage.removeManualReloadListener(manualReloadListener);
        hotloadEngine.clear();
        reportedCaptureFailures.clear();
    }

    boolean applySnapshot(ConfigHotloadEngine.StableContentSnapshot snapshot) {
        File file = snapshot.file();
        boolean missing = "missing".equals(snapshot.signature());
        if (isSettingsFile(file)) {
            if (missing) {
                IrisLogging.warn("iris.json was removed; retaining the last valid runtime settings.");
                return true;
            }
            if (snapshot.normalizedContent() == null) {
                reportUnavailableSnapshot(file);
                return false;
            }
            return applySettingsSnapshot(file, snapshot.normalizedContent());
        }
        if (!isLocaleOverrideFile(file) || !IrisLanguage.isLanguageFile(file)) {
            return true;
        }
        if (!missing && snapshot.normalizedContent() == null) {
            reportUnavailableSnapshot(file);
            return applyLocaleSnapshot(file, null);
        }
        return applyLocaleSnapshot(file, missing ? null : snapshot.normalizedContent());
    }

    boolean isSettingsFile(File file) {
        return file != null && settingsFile.equals(file.getAbsoluteFile());
    }

    boolean isLocaleOverrideFile(File file) {
        if (file == null || !file.getName().toLowerCase(Locale.ROOT).endsWith(".toml")) {
            return false;
        }
        File parent = file.getAbsoluteFile().getParentFile();
        return localeOverrideFolder.equals(parent);
    }

    private boolean isManagedFile(File file) {
        return isSettingsFile(file) || isLocaleOverrideFile(file);
    }

    private Collection<File> knownFiles() {
        List<File> files = new ArrayList<>();
        files.add(settingsFile);
        File[] overrides = localeOverrideFolder.listFiles();
        if (overrides == null) {
            return files;
        }
        for (File override : overrides) {
            if (isLocaleOverrideFile(override) && override.isFile()) {
                files.add(override.getAbsoluteFile());
            }
        }
        return files;
    }

    private String readContent(File file) {
        if (file == null) {
            return null;
        }
        if (!file.isFile()) {
            clearCaptureFailure(file);
            return null;
        }
        try (InputStream input = Files.newInputStream(file.toPath())) {
            byte[] content = input.readNBytes(MAX_HOTLOAD_BYTES + 1);
            if (content.length > MAX_HOTLOAD_BYTES) {
                throw new IOException("Hotload file exceeds " + MAX_HOTLOAD_BYTES + " bytes: " + file);
            }
            String decoded = decodeUtf8(content, file);
            clearCaptureFailure(file);
            return decoded;
        } catch (NoSuchFileException missing) {
            clearCaptureFailure(file);
            return null;
        } catch (IOException | SecurityException failure) {
            reportCaptureFailure(file, failure);
            return null;
        }
    }

    private String normalizeContent(String content) {
        return content == null ? null : content.replace("\r\n", "\n").trim();
    }

    private String decodeUtf8(byte[] content, File file) throws IOException {
        try {
            return StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(content))
                    .toString();
        } catch (CharacterCodingException failure) {
            throw new IOException("Hotload file is not valid UTF-8: " + file, failure);
        }
    }

    private boolean applySettingsSnapshot(File file, String content) {
        try {
            return IrisSettings.applyHotloadSnapshot(content, IrisLanguage::reload);
        } catch (RuntimeException failure) {
            IrisLogging.error("Rejected invalid settings hotload from " + file.getAbsolutePath() + ": " + failureDetail(failure));
            IrisLogging.reportError(failure);
            return false;
        }
    }

    private boolean applyLocaleSnapshot(File file, String content) {
        try {
            return IrisLanguage.reloadOverride(file, content);
        } catch (RuntimeException failure) {
            IrisLogging.error("Rejected invalid locale hotload from " + file.getAbsolutePath() + ": " + failureDetail(failure));
            IrisLogging.reportError(failure);
            return false;
        }
    }

    private void acknowledgeManualLocaleReload(File file, String content) {
        if (closed) {
            return;
        }
        hotloadEngine.noteSelfWrite(file, content);
        clearCaptureFailure(file);
    }

    private void reportUnavailableSnapshot(File file) {
        if (file.isFile() && file.length() > MAX_HOTLOAD_BYTES) {
            reportCaptureFailure(
                    file,
                    new IOException("Hotload file exceeds " + MAX_HOTLOAD_BYTES + " bytes: " + file)
            );
        }
    }

    private void reportCaptureFailure(File file, Throwable failure) {
        String path = file.getAbsolutePath();
        String failureKey = failure.getClass().getName() + ":" + failureDetail(failure);
        if (Objects.equals(reportedCaptureFailures.put(path, failureKey), failureKey)) {
            return;
        }
        IrisLogging.error("Failed to read watched Iris file " + path + ": " + failureDetail(failure));
        IrisLogging.reportError(failure);
    }

    private void clearCaptureFailure(File file) {
        reportedCaptureFailures.remove(file.getAbsolutePath());
    }

    private void reportApplied(ConfigHotloadEngine.ContentDelta delta) {
        File file = delta.file();
        if (isSettingsFile(file)) {
            if (delta.after() != null) {
                IrisLogging.debug("Hotloaded iris.json");
            }
            return;
        }
        if (IrisLanguage.isLanguageFile(file)) {
            IrisLogging.debug("Hotloaded language file " + file.getName());
        }
    }

    private String failureDetail(Throwable failure) {
        String message = failure.getMessage();
        return failure.getClass().getSimpleName() + (message == null || message.isBlank() ? "" : " - " + message);
    }

    record Timing(
            long pollPeriodMillis,
            long cooldownMillis,
            long fullScanWindowMillis,
            long signatureScanWindowMillis
    ) {
        Timing {
            if (pollPeriodMillis <= 0L
                    || cooldownMillis <= 0L
                    || fullScanWindowMillis <= 0L
                    || signatureScanWindowMillis <= 0L) {
                throw new IllegalArgumentException("Hotload timing values must be positive");
            }
        }
    }
}
