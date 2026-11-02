package art.arcane.iris.localization;

import art.arcane.iris.configuration.IrisSettings;
import art.arcane.iris.spi.CapabilityProbe;
import art.arcane.iris.spi.IrisLogging;
import art.arcane.iris.spi.IrisPlatforms;
import art.arcane.volmlib.util.director.DirectorTextResolver;
import art.arcane.volmlib.util.io.AtomicFileIO;
import art.arcane.volmlib.util.localization.GitHubLanguageSource;
import art.arcane.volmlib.util.localization.LanguageCatalogMerge;
import art.arcane.volmlib.util.localization.LanguageDirectory;
import art.arcane.volmlib.util.localization.LanguageMeta;
import art.arcane.volmlib.util.localization.LanguageReferenceRenderer;
import art.arcane.volmlib.util.localization.LanguageSourceManifest;
import art.arcane.volmlib.util.localization.LanguageUpstreamStore;
import art.arcane.volmlib.util.localization.LanguageUpstreamSync;
import art.arcane.volmlib.util.localization.LinesKey;
import art.arcane.volmlib.util.localization.LanguageAudience;
import art.arcane.volmlib.util.localization.LanguageFileEditor;
import art.arcane.volmlib.util.localization.PluginLanguageEditor;
import art.arcane.volmlib.util.localization.PluginLanguageService;
import art.arcane.volmlib.util.localization.LocalizationValidator;
import art.arcane.volmlib.util.localization.LocaleOverlay;
import art.arcane.volmlib.util.localization.LocalizationCandidate;
import art.arcane.volmlib.util.localization.LocalizationIssue;
import art.arcane.volmlib.util.localization.LocalizationManager;
import art.arcane.volmlib.util.localization.LocalizationReloadResult;
import art.arcane.volmlib.util.localization.LocalizationSnapshot;
import art.arcane.volmlib.util.localization.MessageArgument;
import art.arcane.volmlib.util.localization.MessageArgumentKind;
import art.arcane.volmlib.util.localization.MessageArgs;
import art.arcane.volmlib.util.localization.MessageCatalog;
import art.arcane.volmlib.util.localization.MessageKey;
import art.arcane.volmlib.util.localization.MessageValue;
import art.arcane.volmlib.util.localization.PluralKey;
import art.arcane.volmlib.util.localization.PluralSelector;
import art.arcane.volmlib.util.localization.ResolvedLines;
import art.arcane.volmlib.util.localization.ResolvedText;
import art.arcane.volmlib.util.localization.TextKey;
import art.arcane.volmlib.util.localization.TomlLanguageEditor;
import art.arcane.volmlib.util.localization.TomlLanguageParser;
import art.arcane.volmlib.util.localization.TomlLanguageWriter;
import art.arcane.volmlib.util.localization.VolmitLocales;
import com.google.gson.JsonObject;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.Path;
import java.nio.file.LinkOption;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;
import com.google.gson.Gson;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;
import java.util.regex.Pattern;

public final class IrisLanguage {
    public static final String MANIFEST_RESOURCE = "iris-language-source.properties";
    private static final Object SNAPSHOT_LOCK = new Object();
    private static final Object UPSTREAM_LOCK = new Object();
    private static final long MAX_LOCALE_BYTES = LanguageDirectory.MAXIMUM_BYTES;
    private static final int MAX_REPORTED_ISSUES = 12;
    private static final int MAXIMUM_BASE_DEPTH = 4;
    private static final Pattern LOCALE_NAME = LanguageDirectory.LOCALE_PATTERN;
    private static final Pattern LEGACY_COLOR = Pattern.compile("(?i)\\u00a7[0-9A-FK-ORX]");
    private static final MessageCatalog CATALOG = IrisMessages.catalog();
    private static final LocalizationManager MANAGER = new LocalizationManager(
            LocalizationCandidate.english(CATALOG, PluralSelector.oneOther())
    );
    /**
     * Memoized argument-free {@link #plain(MessageKey)} results. HUD and overlay code calls it several times
     * per frame for fixed labels; resolve plus placeholder render plus colour clean is not free at 60fps.
     * Keyed by message id and pinned to the snapshot it was resolved against, so a locale reload publishes a
     * new snapshot and the whole memo is discarded. Bounded by the catalog size.
     */
    private static final AtomicReference<PlainMemo> PLAIN_MEMO = new AtomicReference<>(null);
    private static final CopyOnWriteArrayList<BiConsumer<File, String>> MANUAL_RELOAD_LISTENERS =
            new CopyOnWriteArrayList<>();
    private static final Map<String, CachedMeta> METAS = new ConcurrentHashMap<>();
    private static final Set<String> PENDING_INSTALLS = ConcurrentHashMap.newKeySet();
    private static volatile File dataFolder;
    private static volatile String activeLocale = CATALOG.englishLocale();
    private static final Set<String> GUIDE_FETCHES = ConcurrentHashMap.newKeySet();
    private static volatile LanguageUpstream upstream;
    private static volatile URI sourceApiRoot = GitHubLanguageSource.API_ROOT;
    private static volatile URI sourceRawRoot = GitHubLanguageSource.RAW_ROOT;
    private static volatile String englishReference;
    private static volatile PluginLanguageService selections;

    private IrisLanguage() {
    }

    public static boolean initialize() {
        if (!IrisPlatforms.isBound()) {
            return false;
        }
        boolean loaded = reload(IrisPlatforms.get().dataFolder(), configuredLocale());
        start();
        return loaded;
    }

    public static synchronized boolean reload() {
        File root = dataFolder;
        if (root == null && IrisPlatforms.isBound()) {
            root = IrisPlatforms.get().dataFolder();
        }
        if (root == null) {
            return false;
        }
        return reload(root, configuredLocale());
    }

    public static synchronized boolean reload(IrisSettings candidate) {
        IrisSettings resolvedCandidate = Objects.requireNonNull(candidate, "Candidate settings cannot be null");
        File root = dataFolder;
        if (root == null && IrisPlatforms.isBound()) {
            root = IrisPlatforms.get().dataFolder();
        }
        if (root == null) {
            return false;
        }
        IrisSettings.IrisSettingsGeneral general = resolvedCandidate.getGeneral();
        String locale = general == null ? CATALOG.englishLocale() : general.getLanguage();
        return reloadResolved(root, locale, false);
    }

    public static synchronized boolean reload(File root, String locale) {
        return reloadResolved(root, locale, true);
    }

    public static void addManualReloadListener(BiConsumer<File, String> listener) {
        MANUAL_RELOAD_LISTENERS.add(Objects.requireNonNull(listener, "Manual reload listener cannot be null"));
    }

    public static void removeManualReloadListener(BiConsumer<File, String> listener) {
        MANUAL_RELOAD_LISTENERS.remove(listener);
    }

    private static boolean reloadResolved(File root, String locale, boolean notifyManualReload) {
        File resolvedRoot = root == null ? null : root.getAbsoluteFile();
        if (resolvedRoot == null) {
            throw new IllegalArgumentException("Iris locale data folder cannot be null");
        }
        String requestedLocale;
        try {
            requestedLocale = normalizeLocale(locale);
        } catch (RuntimeException exception) {
            dataFolder = resolvedRoot;
            IrisLogging.error("Rejected locale setting '" + locale + "'; continuing with " + activeLocale + ".");
            IrisLogging.reportError(exception);
            return false;
        }

        try {
            ensureEnglish(resolvedRoot);
        } catch (IOException failure) {
            IrisLogging.error("Could not create or refresh the editable Iris English language file.");
            IrisLogging.reportError(failure);
        }
        File override = overrideFile(resolvedRoot, requestedLocale);
        if (!CATALOG.englishLocale().equals(requestedLocale)) {
            refreshLanguageGuide(override, requestedLocale);
        }
        SnapshotCapture capture = captureForReload(override, requestedLocale);
        boolean initialLoad = dataFolder == null;
        boolean changedLocale = !requestedLocale.equals(activeLocale);
        boolean applied = applyReload(resolvedRoot, requestedLocale, capture);
        if (!applied && initialLoad) {
            return applyReload(resolvedRoot, requestedLocale,
                    new SnapshotCapture(LocaleHotloadSnapshot.missing(override, requestedLocale), null));
        }
        if (applied && changedLocale && selections != null) {
            requestConfiguredLocale();
        }
        if (applied && notifyManualReload) {
            notifyManualReload(capture.snapshot().file(), capture.snapshot().content());
        }
        return applied;
    }

    public static synchronized boolean reloadOverride(File override, String rawContent) {
        File root = dataFolder;
        if (root == null && IrisPlatforms.isBound()) {
            root = IrisPlatforms.get().dataFolder();
        }
        if (root == null || override == null) {
            return false;
        }

        String requestedLocale;
        try {
            if (!isLanguageFile(override)) {
                return false;
            }
            requestedLocale = normalizeLocale(override.getName().substring(0, override.getName().length() - 5));
        } catch (RuntimeException exception) {
            IrisLogging.error("Rejected Iris language file " + override + ".");
            IrisLogging.reportError(exception);
            return false;
        }

        File expected = overrideFile(root, requestedLocale);
        if (!expected.equals(override.getAbsoluteFile())) {
            return true;
        }
        if (rawContent == null && !Files.notExists(expected.toPath(), LinkOption.NOFOLLOW_LINKS)) {
            IOException failure = new IOException("Language file could not be read: " + expected);
            IrisLogging.error("Could not reload Iris language " + requestedLocale + "; keeping the last valid messages.");
            IrisLogging.reportError(failure);
            return false;
        }

        LocaleHotloadSnapshot snapshot = rawContent == null
                ? LocaleHotloadSnapshot.missing(expected, requestedLocale)
                : LocaleHotloadSnapshot.present(
                        expected,
                        requestedLocale,
                        rawContent,
                        sha256(rawContent.getBytes(StandardCharsets.UTF_8))
                );
        if (requestedLocale.equals(activeLocale)) {
            return applyReload(root.getAbsoluteFile(), requestedLocale, new SnapshotCapture(snapshot, null));
        }
        try {
            LocalizationSnapshot prepared = LocalizationSnapshot.create(loadCandidate(root, requestedLocale, snapshot));
            PluginLanguageService current = selections;
            if (current != null) {
                current.commitUpdate(() -> {
                    current.cache(requestedLocale, prepared);
                    return null;
                });
            }
            return true;
        } catch (Exception failure) {
            IrisLogging.error("Could not reload Iris language " + requestedLocale + ".");
            IrisLogging.reportError(failure);
            return false;
        }
    }

    public static boolean isLanguageFile(File file) {
        File folder = overrideFolder();
        if (file == null || folder == null || !file.getName().endsWith(".toml")) {
            return false;
        }
        File target = file.getAbsoluteFile();
        String locale = target.getName().substring(0, target.getName().length() - 5);
        return folder.getAbsoluteFile().equals(target.getParentFile()) && LOCALE_NAME.matcher(locale).matches();
    }

    public static boolean isActiveOverrideFile(File file) {
        if (file == null) {
            return false;
        }
        File root = dataFolder;
        if (root == null && IrisPlatforms.isBound()) {
            root = IrisPlatforms.get().dataFolder();
        }
        if (root == null) {
            return false;
        }
        File overrideRoot = root;
        return CapabilityProbe.attempt("language file path",
                () -> overrideFile(overrideRoot, configuredLocale()).equals(file.getAbsoluteFile()),
                Boolean.FALSE);
    }

    private static boolean applyReload(File root, String requestedLocale, SnapshotCapture capture) {
        LocalizationReloadResult result;
        PluginLanguageService current = selections;
        try {
            if (capture.failure() != null) {
                throw capture.failure();
            }
            LocalizationSnapshot prepared = LocalizationSnapshot.create(loadCandidate(root, requestedLocale, capture.snapshot()));
            if (current == null) {
                result = installSnapshot(root, requestedLocale, prepared, null);
            } else {
                result = current.commitUpdate(() -> installSnapshot(root, requestedLocale, prepared, current));
            }
        } catch (Exception failure) {
            IrisLogging.error("Rejected locale reload for " + requestedLocale + "; continuing with " + activeLocale + ".");
            IrisLogging.reportError(failure);
            return false;
        }
        if (!result.applied()) {
            reportRejectedReload(requestedLocale, result);
            return false;
        }

        int warnings = result.validation().warnings().size();
        IrisLogging.debug("Loaded locale " + requestedLocale + " with " + warnings + " fallback "
                + (warnings == 1 ? "entry" : "entries") + ".");
        return true;
    }

    private static LocalizationReloadResult installSnapshot(File root, String locale,
                                                            LocalizationSnapshot prepared, PluginLanguageService current) {
        LocalizationReloadResult result;
        synchronized (SNAPSHOT_LOCK) {
            result = MANAGER.install(prepared);
            dataFolder = root;
            if (result.applied()) {
                activeLocale = locale;
            }
        }
        if (result.applied() && current != null) {
            current.cache(locale, prepared);
        }
        return result;
    }

    private static void notifyManualReload(File file, String content) {
        for (BiConsumer<File, String> listener : MANUAL_RELOAD_LISTENERS) {
            try {
                listener.accept(file, content);
            } catch (RuntimeException failure) {
                IrisLogging.error("Failed to acknowledge a manual locale reload: "
                        + failure.getClass().getSimpleName()
                        + (failure.getMessage() == null ? "" : " - " + failure.getMessage()));
                IrisLogging.reportError(failure);
            }
        }
    }

    public static String activeLocale() {
        return activeLocale;
    }

    public static File overrideFolder() {
        File root = dataFolder;
        if (root == null && IrisPlatforms.isBound()) {
            root = IrisPlatforms.get().dataFolder();
        }
        if (root == null) {
            return null;
        }
        return new File(root, "languages");
    }

    public static String text(MessageKey key, MessageArgument... arguments) {
        MessageArgs.Builder builder = MessageArgs.builder();
        for (MessageArgument argument : arguments) {
            builder.add(argument);
        }
        return text(key, builder.build());
    }

    public static String text(MessageKey key) {
        return text(key, MessageArgs.empty());
    }

    public static String text(MessageKey key, MessageArgs arguments) {
        return render(resolve(key, arguments));
    }

    public static String plain(MessageKey key, MessageArgument... arguments) {
        MessageArgs.Builder builder = MessageArgs.builder();
        for (MessageArgument argument : arguments) {
            builder.add(argument);
        }
        return plain(key, builder.build());
    }

    public static String plain(MessageKey key) {
        LocalizationSnapshot snapshot = snapshot();
        PlainMemo memo = PLAIN_MEMO.get();
        if (memo == null || memo.snapshot() != snapshot) {
            memo = new PlainMemo(snapshot, new ConcurrentHashMap<>());
            PLAIN_MEMO.set(memo);
        }
        String cached = memo.values().get(key.id());
        if (cached != null) {
            return cached;
        }
        String resolved = IrisLogging.clean(render(resolve(snapshot, key, MessageArgs.empty())));
        memo.values().put(key.id(), resolved);
        return resolved;
    }

    public static String plain(MessageKey key, MessageArgs arguments) {
        String rendered = render(resolve(key, arguments));
        return IrisLogging.clean(rendered);
    }

    public static String errorDetail(Throwable throwable) {
        String message = throwable == null ? null : throwable.getMessage();
        if (message == null || message.isBlank()) {
            return "";
        }
        return plain(RuntimeUiMessages.ERROR_DETAIL_SUFFIX, MessageArgument.untrusted("error", message));
    }

    public static DirectorTextResolver directorResolver() {
        return (key, arguments) -> {
            MessageKey definition = CATALOG.key(key.id());
            if (!(definition instanceof TextKey textKey)) {
                return DirectorTextResolver.ENGLISH.resolve(key, arguments);
            }
            return plain(textKey, arguments);
        };
    }

    public static MessageCatalog catalog() {
        return CATALOG;
    }

    private static ResolvedText resolve(MessageKey key, MessageArgs arguments) {
        return resolve(snapshot(), key, arguments);
    }

    private static ResolvedText resolve(LocalizationSnapshot snapshot, MessageKey key, MessageArgs arguments) {
        if (key instanceof TextKey textKey) {
            return snapshot.resolve(textKey, arguments);
        }
        if (key instanceof PluralKey pluralKey) {
            return snapshot.resolve(pluralKey, arguments);
        }
        if (key instanceof LinesKey linesKey) {
            ResolvedLines lines = snapshot.resolve(linesKey, arguments);
            return new ResolvedText(lines.key(), lines.locale(), String.join("\n", lines.lines()), lines.arguments());
        }
        throw new IllegalArgumentException("Unsupported Iris message key: " + key.id());
    }

    private static LocalizationCandidate loadCandidate(
            File root,
            String locale,
            LocaleHotloadSnapshot snapshot
    ) throws Exception {
        List<LocaleOverlay> overlays = new ArrayList<>(2);
        if (!snapshot.missing()) {
            overlays.add(parseOverlay(snapshot.file().getPath(), locale, snapshot.content()));
            overlays.addAll(baseOverlays(root, locale, snapshot.content()));
        }
        return new LocalizationCandidate(CATALOG, overlays, PluralSelector.oneOther());
    }

    private static List<LocaleOverlay> baseOverlays(File root, String locale, String content) throws IOException {
        List<LocaleOverlay> overlays = new ArrayList<>(1);
        Set<String> visited = new HashSet<>();
        visited.add(comparableLocale(locale));
        LanguageMeta meta = metaFromContent(locale, content);
        int depth = 0;
        while (meta.base().isPresent() && depth < MAXIMUM_BASE_DEPTH) {
            String base = meta.base().get();
            if (!LOCALE_NAME.matcher(base).matches() || !visited.add(comparableLocale(base))
                    || LanguageDirectory.sameLocale(base, CATALOG.englishLocale())) {
                break;
            }
            File baseFile = overrideFile(root, base);
            if (!Files.isRegularFile(baseFile.toPath(), LinkOption.NOFOLLOW_LINKS)) {
                break;
            }
            String baseContent = LanguageDirectory.readContent(baseFile.toPath());
            overlays.add(parseOverlay(baseFile.getPath(), base, baseContent));
            meta = metaFromContent(base, baseContent);
            depth++;
        }
        return overlays;
    }

    private static LanguageMeta metaFromContent(String locale, String content) {
        try {
            return LanguageMeta.parse(content).withoutSelfBase(locale);
        } catch (IOException invalid) {
            return LanguageMeta.empty();
        }
    }

    private static String comparableLocale(String locale) {
        return locale.replace('-', '_').toLowerCase(Locale.ROOT);
    }

    static LanguageUpstream upstream(File root) {
        Path path = root.toPath().toAbsolutePath().normalize();
        LanguageUpstream current = upstream;
        if (current != null && path.equals(current.root())) {
            return current;
        }
        synchronized (UPSTREAM_LOCK) {
            current = upstream;
            if (current != null && path.equals(current.root())) {
                return current;
            }
            if (current != null) {
                current.close();
            }
            LanguageUpstream created = createUpstream(path);
            upstream = created;
            return created;
        }
    }

    static void useLanguageSource(URI apiRoot, URI rawRoot) {
        LanguageUpstream current;
        synchronized (UPSTREAM_LOCK) {
            sourceApiRoot = Objects.requireNonNull(apiRoot, "apiRoot");
            sourceRawRoot = Objects.requireNonNull(rawRoot, "rawRoot");
            current = upstream;
            upstream = null;
        }
        if (current != null) {
            current.close();
        }
    }

    private static LanguageUpstream createUpstream(Path root) {
        LanguageDirectory directory = new LanguageDirectory(root.resolve("languages"));
        try {
            LanguageSourceManifest manifest = LanguageSourceManifest.load(IrisLanguage.class.getClassLoader(), MANIFEST_RESOURCE);
            GitHubLanguageSource source = new GitHubLanguageSource(manifest, "Iris", sourceApiRoot, sourceRawRoot);
            LanguageUpstreamSync sync = new LanguageUpstreamSync(new LanguageUpstreamSync.Options(
                    "Iris",
                    CATALOG,
                    directory,
                    manifest,
                    source,
                    new LanguageUpstreamStore(directory, manifest),
                    IrisLanguage::englishReference,
                    IrisLanguage::validateDownload,
                    IrisLanguage::commitUpstream,
                    IrisLanguage::noteUpstreamWrite,
                    IrisLanguage::applyUpstream,
                    Logger.getLogger("Iris")
            ));
            return new LanguageUpstream(root, directory, manifest, sync);
        } catch (RuntimeException failure) {
            IrisLogging.error("Iris language source is unavailable; English and installed language files remain active.");
            IrisLogging.reportError(failure);
            return new LanguageUpstream(root, directory, null, null);
        }
    }

    public static CompletableFuture<LanguageUpstreamSync.SyncReport> checkUpstream() {
        File root = dataFolder;
        LanguageUpstream source = root == null ? null : upstream(root);
        if (source == null || source.sync() == null) {
            return CompletableFuture.completedFuture(LanguageUpstreamSync.SyncReport.failed(
                    "The Iris language source is unavailable", System.currentTimeMillis()));
        }
        return source.sync().checkAsync().thenApply(IrisLanguage::logUpstreamReport);
    }

    public static String sourceDescription() {
        File root = dataFolder;
        LanguageUpstream source = root == null ? null : upstream(root);
        if (source == null || source.manifest() == null) {
            return "unavailable";
        }
        return source.manifest().repository() + "@" + source.manifest().branch();
    }

    public static Optional<LanguageUpstreamSync.Status> upstreamStatus() {
        File root = dataFolder;
        LanguageUpstream source = root == null ? null : upstream(root);
        return source == null || source.sync() == null ? Optional.empty() : Optional.of(source.sync().status());
    }

    public static String debugReport() {
        StringBuilder report = new StringBuilder("Language: ").append(activeLocale)
                .append(" (source ").append(sourceDescription()).append(')');
        Optional<LanguageUpstreamSync.Status> status = upstreamStatus();
        if (status.isEmpty()) {
            return report.append("\n  Upstream sync: unavailable").toString();
        }
        LanguageUpstreamSync.Status current = status.get();
        report.append("\n  Revision: ").append(current.revision().orElse("none"));
        report.append("\n  Last check: ").append(timestamp(current.lastCheckMillis()));
        report.append("\n  Last stored check: ").append(timestamp(current.checkedMillis()));
        report.append("\n  Last error: ").append(current.lastError().orElse("none"));
        if (current.checking()) {
            report.append("\n  Check in progress");
        }
        return report.toString();
    }

    private static String timestamp(long millis) {
        return millis == 0L ? "never" : Instant.ofEpochMilli(millis).toString();
    }

    private static LanguageUpstreamSync.SyncReport logUpstreamReport(LanguageUpstreamSync.SyncReport report) {
        String revision = report.revision().map(sha -> sha.substring(0, Math.min(7, sha.length()))).orElse("unknown");
        for (String locale : report.changedLocales()) {
            LanguageCatalogMerge.MergeResult result = report.merged().get(locale);
            String source = locale.equals(CATALOG.englishLocale())
                    ? "the built-in catalog" : sourceDescription() + " (" + revision + ")";
            IrisLogging.info("Updated Iris language " + locale + " from " + source + ": " + result.summary() + ".");
        }
        if (report.coolingDown()) {
            IrisLogging.info("Iris language source " + sourceDescription() + " was checked recently; the next check is allowed in "
                    + report.cooldownRemaining() + ".");
        } else if (report.failure().isEmpty() && report.changedLocales().isEmpty() && report.failures().isEmpty()
                && report.revision().isPresent()) {
            IrisLogging.info("Iris languages match " + sourceDescription() + " (" + revision + ").");
        }
        return report;
    }

    private static <T> T commitUpstream(LanguageUpstreamSync.CommitAction<T> action) throws IOException {
        PluginLanguageService current = selections;
        if (current == null) {
            return action.apply();
        }
        return current.commitUpdate(action::apply);
    }

    private static void noteUpstreamWrite(Path file, String content) {
        notifyManualReload(file.toFile().getAbsoluteFile(), content);
    }

    private static void applyUpstream(String locale, String content, LanguageCatalogMerge.MergeResult result)
            throws Exception {
        File root = dataFolder;
        if (root == null) {
            return;
        }
        File file = overrideFile(root, locale);
        LocaleHotloadSnapshot source = LocaleHotloadSnapshot.present(
                file, locale, content, sha256(content.getBytes(StandardCharsets.UTF_8)));
        LocalizationSnapshot prepared = LocalizationSnapshot.create(loadCandidate(root, locale, source));
        synchronized (SNAPSHOT_LOCK) {
            if (root.equals(dataFolder) && locale.equals(activeLocale)) {
                MANAGER.install(prepared);
            }
        }
        PluginLanguageService current = selections;
        if (current != null && root.equals(dataFolder)) {
            current.cache(locale, prepared);
        }
    }

    public static synchronized void start() {
        if (selections != null || dataFolder == null) {
            return;
        }
        selections = new PluginLanguageService(new PluginLanguageService.Options(
                dataFolder.toPath().resolve("languages/language-preferences.properties"),
                IrisLanguage::availableLocales,
                IrisLanguage::activeLocale,
                MANAGER::snapshot,
                IrisLanguage::prepareLocale,
                IrisLanguage::selectDefault,
                Logger.getLogger("Iris")
        ));
        requestConfiguredLocale();
    }

    public static synchronized void shutdown() {
        dataFolder = null;
        PLAIN_MEMO.set(null);
        PluginLanguageService current = selections;
        selections = null;
        if (current != null) {
            current.close();
        }
        METAS.clear();
        PENDING_INSTALLS.clear();
        LanguageUpstream currentUpstream;
        synchronized (UPSTREAM_LOCK) {
            currentUpstream = upstream;
            upstream = null;
        }
        if (currentUpstream != null) {
            currentUpstream.close();
        }
    }

    public static PluginLanguageService selections() {
        return selections;
    }

    public static Set<String> availableLocales() {
        Set<String> locales = new LinkedHashSet<>();
        locales.add(CATALOG.englishLocale());
        File root = dataFolder;
        if (root != null) {
            LanguageUpstream source = upstream(root);
            if (source.manifest() != null) {
                locales.addAll(source.manifest().locales());
            }
            try {
                locales.addAll(source.directory().scan());
            } catch (IOException failure) {
                IrisLogging.error("Could not list the Iris language files in " + source.directory().root() + ".");
                IrisLogging.reportError(failure);
            }
        }
        return Set.copyOf(locales);
    }

    public static String displayName(String locale) {
        String normalized = locale == null ? "" : locale.trim();
        return meta(normalized).name().or(() -> VolmitLocales.displayName(normalized)).orElse(normalized);
    }

    public static String metaName(String locale) {
        return meta(locale).name().orElse(null);
    }

    public static LanguageMeta meta(String locale) {
        File folder = overrideFolder();
        String normalized = locale == null ? "" : locale.trim();
        if (folder == null || !LOCALE_NAME.matcher(normalized).matches()) {
            return LanguageMeta.empty();
        }
        Path file = folder.toPath().resolve(normalized + ".toml").toAbsolutePath().normalize();
        BasicFileAttributes attributes;
        try {
            attributes = Files.readAttributes(file, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
        } catch (IOException missing) {
            METAS.remove(normalized);
            return LanguageMeta.empty();
        }
        CachedMeta cached = METAS.get(normalized);
        if (cached != null && cached.file().equals(file) && cached.modified().equals(attributes.lastModifiedTime())
                && cached.size() == attributes.size()) {
            return cached.meta();
        }
        LanguageMeta meta = LanguageMeta.empty();
        if (attributes.isRegularFile() && attributes.size() <= MAX_LOCALE_BYTES) {
            try {
                meta = LanguageMeta.parse(LanguageDirectory.readContent(file)).withoutSelfBase(normalized);
            } catch (IOException invalid) {
                IrisLogging.debug("Ignoring unreadable [meta] in " + file + ": " + invalid.getMessage());
            }
        }
        METAS.put(normalized, new CachedMeta(file, attributes.lastModifiedTime(), attributes.size(), meta));
        return meta;
    }

    public static String text(UUID player, MessageKey key, MessageArgs arguments) {
        return LanguageAudience.call(player, () -> text(key, arguments));
    }

    public static String plain(UUID player, MessageKey key, MessageArgs arguments) {
        return LanguageAudience.call(player, () -> plain(key, arguments));
    }

    private static LocalizationSnapshot snapshot() {
        PluginLanguageService current = selections;
        return current == null ? MANAGER.snapshot() : current.snapshot();
    }

    private static LocalizationSnapshot prepareLocale(String locale) throws Exception {
        File root = dataFolder;
        ensureEnglish(root);
        File file = overrideFile(root, locale);
        if (!CATALOG.englishLocale().equals(locale) && !Files.exists(file.toPath(), LinkOption.NOFOLLOW_LINKS)) {
            installMissingLocale(root, locale, file);
        }
        if (!CATALOG.englishLocale().equals(locale)) {
            refreshLanguageGuide(file, locale);
        }
        SnapshotCapture capture = captureForReload(overrideFile(root, locale), locale);
        if (capture.failure() != null) {
            throw capture.failure();
        }
        return LocalizationSnapshot.create(loadCandidate(root, locale, capture.snapshot()));
    }

    public static PluginLanguageEditor.Options editorOptions() {
        return new PluginLanguageEditor.Options(IrisLanguage::prepareLocale, IrisLanguage::writeMessage);
    }

    private static LocalizationSnapshot writeMessage(PluginLanguageEditor.Edit edit) throws IOException {
        LocaleOverlay replacement = LocaleOverlay.builder("editor", edit.locale())
                .put(edit.key(), edit.value()).build();
        try {
            LocalizationValidator.validate(CATALOG, List.of(replacement)).throwIfInvalid();
        } catch (IllegalArgumentException failure) {
            throw new IOException("Invalid language message: " + edit.key(), failure);
        }
        File root = dataFolder;
        File file = overrideFile(root, edit.locale());
        LocalizationSnapshot prepared = LanguageFileEditor.update(file.toPath(), raw -> {
            LocalizationSnapshot current = editorSnapshot(root, file, edit.locale(), raw);
            if (!current.value(CATALOG.require(edit.key())).equals(edit.expected())) {
                throw new IOException("Language message changed; reopen it before saving");
            }
            String updated = TomlLanguageEditor.upsert(raw, edit.key(), edit.value()).content();
            return new LanguageFileEditor.Prepared<>(updated, editorSnapshot(root, file, edit.locale(), updated));
        });
        synchronized (SNAPSHOT_LOCK) {
            if (root.equals(dataFolder) && edit.locale().equals(activeLocale)) {
                MANAGER.install(prepared);
            }
        }
        PluginLanguageService current = selections;
        if (current != null && root.equals(dataFolder)) {
            current.cache(edit.locale(), prepared);
        }
        return prepared;
    }

    private static LocalizationSnapshot editorSnapshot(File root, File file, String locale, String raw) throws IOException {
        try {
            LocaleHotloadSnapshot source = LocaleHotloadSnapshot.present(file, locale, raw, sha256(raw.getBytes(StandardCharsets.UTF_8)));
            return LocalizationSnapshot.create(loadCandidate(root, locale, source));
        } catch (Exception failure) {
            throw new IOException("Could not validate Iris language " + locale, failure);
        }
    }

    private static void selectDefault(String locale, LocalizationSnapshot prepared) throws Exception {
        IrisSettings current = IrisSettings.get();
        JsonObject serialized = new Gson().toJsonTree(current).getAsJsonObject();
        serialized.getAsJsonObject("general").addProperty("language", locale);
        Path target = dataFolder.toPath().resolve("iris.json");
        Path temporary = Files.createTempFile(target.getParent(), "iris-", ".json.tmp");
        try {
            Files.writeString(temporary, serialized.toString());
            Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(temporary);
        }
        current.getGeneral().setLanguage(locale);
        synchronized (SNAPSHOT_LOCK) {
            MANAGER.install(prepared);
            activeLocale = locale;
        }
    }

    private static void installMissingLocale(File root, String locale, File file) throws IOException {
        LanguageUpstream source = upstream(root);
        if (source.manifest() == null || !source.manifest().contains(locale)) {
            createCustomLanguage(file, locale);
            return;
        }
        if (source.sync() == null) {
            throw new IOException("Iris language " + locale + " is not installed and the language source is unavailable");
        }
        IrisLogging.info("Installing Iris language " + locale + " from " + sourceDescription() + "...");
        source.sync().install(locale);
        IrisLogging.info("Installed Iris language " + locale + " to " + file.toPath().toAbsolutePath().normalize() + ".");
    }

    private static void createCustomLanguage(File file, String locale) throws IOException {
        if (Files.exists(file.toPath(), LinkOption.NOFOLLOW_LINKS)) {
            return;
        }
        String rendered = IrisLanguageGuide.englishHeader(locale) + "\n" + LanguageReferenceRenderer.render(
                CATALOG, List.of(), LanguageMeta.of(locale, CATALOG.englishLocale()));
        Files.createDirectories(file.toPath().getParent());
        AtomicFileIO.writeString(file.toPath(), rendered);
        notifyManualReload(file.getAbsoluteFile(), rendered);
    }

    private static void requestConfiguredLocale() {
        String locale = activeLocale;
        File root = dataFolder;
        if (root == null || CATALOG.englishLocale().equals(locale)) {
            return;
        }
        LanguageUpstream source = upstream(root);
        if (source.sync() == null || !source.manifest().contains(locale)
                || Files.exists(overrideFile(root, locale).toPath(), LinkOption.NOFOLLOW_LINKS)
                || !PENDING_INSTALLS.add(locale)) {
            return;
        }
        source.sync().installAsync(locale).whenComplete((content, failure) -> {
            PENDING_INSTALLS.remove(locale);
            if (failure != null) {
                IrisLogging.error("Failed to download Iris locale " + locale + " from " + sourceDescription() + ".");
                IrisLogging.reportError(failure);
                return;
            }
            IrisLogging.info("Installed Iris language " + locale + " from " + sourceDescription() + ".");
            reloadInstalledLocale(root, locale);
        });
    }

    private static synchronized void reloadInstalledLocale(File root, String locale) {
        if (root.equals(dataFolder) && locale.equals(activeLocale)) {
            reloadResolved(root, locale, false);
        }
    }

    static void validateDownload(String locale, String raw) {
        LocaleOverlay overlay = parseOverlay("download:" + locale, locale, raw);
        LocalizationValidator.validate(CATALOG, List.of(overlay)).throwIfInvalid();
    }

    static LocaleOverlay parseOverlay(String source, String locale, String raw) {
        LocaleOverlay.Builder builder = LocaleOverlay.builder(source, locale);
        try {
            for (Map.Entry<String, MessageValue> entry : TomlLanguageParser.parseValidValues(raw, CATALOG).entrySet()) {
                builder.put(entry.getKey(), entry.getValue());
            }
        } catch (IOException failure) {
            throw new IllegalArgumentException("Invalid TOML language file: " + source, failure);
        }
        return LocalizationValidator.validValues(CATALOG, builder.build());
    }

    static String englishReference() {
        String reference = englishReference;
        if (reference != null) {
            return reference;
        }
        Map<String, MessageValue> values = new LinkedHashMap<>();
        for (MessageKey key : CATALOG.keys()) {
            values.put(key.id(), key.englishValue());
        }
        String rendered = IrisLanguageGuide.englishHeader(CATALOG.englishLocale()) + "\n"
                + TomlLanguageWriter.render(values, List.of());
        englishReference = rendered;
        return rendered;
    }

    private static void refreshLanguageGuide(File file, String locale) {
        try {
            ClassLoader resources = IrisLanguage.class.getClassLoader();
            if (!IrisLanguageGuide.refresh(file, locale, resources) && IrisLanguageGuide.fetchable(locale, resources)) {
                fetchLanguageGuide(file, locale, resources);
            }
        } catch (IOException failure) {
            IrisLogging.error("Could not update the language guide in " + file.getPath() + ".");
            IrisLogging.reportError(failure);
        }
    }

    // Jars that leave the guides out (the size-capped Bukkit jar) take this build's guide from the upstream
    // locale file, once per locale per run; it is cached only when its digest matches the build's guide index.
    private static void fetchLanguageGuide(File file, String locale, ClassLoader resources) {
        File root = dataFolder;
        if (root == null || !GUIDE_FETCHES.add(locale)) {
            return;
        }
        LanguageSourceManifest manifest = upstream(root).manifest();
        if (manifest == null) {
            IrisLogging.debug("Language guide for " + locale + " has no language source");
            return;
        }
        GitHubLanguageSource source = new GitHubLanguageSource(manifest, "Iris", sourceApiRoot, sourceRawRoot);
        Path cache = IrisLanguageGuide.guideCache(file);
        CompletableFuture.runAsync(() -> {
            try {
                String revision = source.latestRevision(null).revision().orElseThrow(() ->
                        new IOException("GitHub reported no language revision"));
                String content = LanguageDirectory.decode(source.fetch(manifest.canonical(locale), revision));
                if (IrisLanguageGuide.cacheUpstream(cache, locale, content, resources)) {
                    IrisLanguageGuide.refresh(file, locale, resources);
                } else {
                    IrisLogging.debug("Language guide for " + locale + " is not available upstream for this build");
                }
            } catch (IOException failure) {
                IrisLogging.debug("Language guide for " + locale + " could not be cached: " + failure.getMessage());
            }
        });
    }

    private static void ensureEnglish(File root) throws IOException {
        File english = overrideFile(Objects.requireNonNull(root, "Language data folder"), CATALOG.englishLocale());
        LanguageUpstream source = upstream(root);
        if (source.sync() == null) {
            if (!Files.exists(english.toPath(), LinkOption.NOFOLLOW_LINKS)) {
                Files.createDirectories(english.toPath().getParent());
                AtomicFileIO.writeString(english.toPath(), englishReference());
                notifyManualReload(english, englishReference());
                return;
            }
        } else {
            try {
                source.sync().mergeEnglish().filter(LanguageCatalogMerge.MergeResult::changed)
                        .ifPresent(result -> IrisLogging.info("Updated Iris language " + CATALOG.englishLocale()
                                + " from the built-in catalog: " + result.summary() + "."));
            } catch (IOException failure) {
                throw failure;
            } catch (Exception failure) {
                throw new IOException("Could not refresh the Iris English language file", failure);
            }
        }
        refreshLanguageGuide(english, CATALOG.englishLocale());
    }

    private static String render(ResolvedText resolved) {
        String prepared = resolved.template();
        List<RenderedArgument> replacements = new ArrayList<>(resolved.arguments().size());
        int index = 0;
        for (MessageArgument argument : resolved.arguments().arguments().values()) {
            String token = "\uE000" + index + "\uE001";
            prepared = prepared.replace("{" + argument.name() + "}", token);
            replacements.add(new RenderedArgument(token, argument));
            index++;
        }

        String rendered = translateColors(prepared);
        StringBuilder output = new StringBuilder(rendered.length());
        int cursor = 0;
        while (cursor < rendered.length()) {
            if (rendered.charAt(cursor) != '\uE000') {
                output.append(rendered.charAt(cursor));
                cursor++;
                continue;
            }
            int end = rendered.indexOf('\uE001', cursor + 1);
            int replacementIndex = end < 0 ? -1 : parseReplacementIndex(rendered, cursor + 1, end);
            if (replacementIndex < 0 || replacementIndex >= replacements.size()) {
                output.append(rendered.charAt(cursor));
                cursor++;
                continue;
            }
            RenderedArgument replacement = replacements.get(replacementIndex);
            if (replacement.token().length() != end - cursor + 1
                    || !rendered.regionMatches(cursor, replacement.token(), 0, replacement.token().length())) {
                output.append(rendered.charAt(cursor));
                cursor++;
                continue;
            }
            MessageArgument argument = replacement.argument();
            String value = String.valueOf(argument.value());
            output.append(argument.kind() == MessageArgumentKind.TRUSTED
                    ? translateColors(value)
                    : escapeUntrusted(value));
            cursor = end + 1;
        }
        return output.toString();
    }

    private static int parseReplacementIndex(String value, int start, int end) {
        if (start >= end) {
            return -1;
        }
        int result = 0;
        for (int index = start; index < end; index++) {
            char character = value.charAt(index);
            if (character < '0' || character > '9') {
                return -1;
            }
            int digit = character - '0';
            if (result > (Integer.MAX_VALUE - digit) / 10) {
                return -1;
            }
            result = result * 10 + digit;
        }
        return result;
    }

    private static String translateColors(String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        if (value.indexOf('&') < 0 && value.indexOf('[') < 0) {
            return value;
        }
        StringBuilder output = new StringBuilder(value.length() + 16);
        for (int index = 0; index < value.length(); index++) {
            char current = value.charAt(index);
            if (current == '\\' && index + 1 < value.length()
                    && (value.charAt(index + 1) == '&' || value.charAt(index + 1) == '[')) {
                output.append(value.charAt(++index));
                continue;
            }
            if (current == '[' && index + 7 < value.length() && value.charAt(index + 7) == ']'
                    && isHex(value, index + 1)) {
                appendHexColor(output, value.substring(index + 1, index + 7));
                index += 7;
                continue;
            }
            if (current == '&' && index + 1 < value.length()) {
                char code = Character.toLowerCase(value.charAt(index + 1));
                if ((code == '#' || code == 'x') && isHex(value, index + 2)) {
                    appendHexColor(output, value.substring(index + 2, index + 8));
                    index += 7;
                    continue;
                }
                if (code == 'x') {
                    String expanded = expandedHex(value, index);
                    if (expanded != null) {
                        appendHexColor(output, expanded);
                        index += 13;
                        continue;
                    }
                }
                if (isColorCode(code)) {
                    output.append('\u00a7').append(code);
                    index++;
                    continue;
                }
            }
            output.append(current);
        }
        return output.toString();
    }

    private static boolean isHex(String value, int offset) {
        if (offset + 6 > value.length()) {
            return false;
        }
        for (int index = offset; index < offset + 6; index++) {
            if (Character.digit(value.charAt(index), 16) < 0) {
                return false;
            }
        }
        return true;
    }

    private static String expandedHex(String value, int offset) {
        if (offset + 14 > value.length()) {
            return null;
        }
        StringBuilder hex = new StringBuilder(6);
        for (int index = 0; index < 6; index++) {
            int marker = offset + 2 + index * 2;
            char digit = value.charAt(marker + 1);
            if (value.charAt(marker) != '&' || Character.digit(digit, 16) < 0) {
                return null;
            }
            hex.append(digit);
        }
        return hex.toString();
    }

    private static void appendHexColor(StringBuilder output, String hex) {
        output.append('\u00a7').append('x');
        for (int index = 0; index < hex.length(); index++) {
            output.append('\u00a7').append(Character.toLowerCase(hex.charAt(index)));
        }
    }

    private static boolean isColorCode(char value) {
        char lowered = Character.toLowerCase(value);
        return lowered >= '0' && lowered <= '9' || lowered >= 'a' && lowered <= 'f'
                || lowered >= 'k' && lowered <= 'o' || lowered == 'r';
    }

    private static String escapeUntrusted(String value) {
        return LEGACY_COLOR.matcher(value).replaceAll("")
                .replace("&", "＆")
                .replace("<", "‹")
                .replace(">", "›");
    }

    private static String configuredLocale() {
        IrisSettings.IrisSettingsGeneral general = IrisSettings.get().getGeneral();
        return general == null ? CATALOG.englishLocale() : general.getLanguage();
    }

    private static String normalizeLocale(String locale) {
        String value = locale == null || locale.isBlank() ? CATALOG.englishLocale() : locale.trim();
        if (!LOCALE_NAME.matcher(value).matches()) {
            throw new IllegalArgumentException("Invalid locale name: " + value);
        }
        return value;
    }

    private static File overrideFile(File root, String locale) {
        return new File(new File(root, "languages"), normalizeLocale(locale) + ".toml").getAbsoluteFile();
    }

    static LocaleHotloadSnapshot captureHotloadSnapshot(File file, String locale) throws IOException {
        File resolvedFile = Objects.requireNonNull(file, "Language file cannot be null").getAbsoluteFile();
        String resolvedLocale = normalizeLocale(locale);
        BasicFileAttributes before;
        try {
            before = Files.readAttributes(resolvedFile.toPath(), BasicFileAttributes.class);
        } catch (NoSuchFileException failure) {
            return LocaleHotloadSnapshot.missing(resolvedFile, resolvedLocale);
        }
        if (!before.isRegularFile()) {
            throw new IllegalArgumentException("Language file is not a regular file: " + resolvedFile.getPath());
        }
        if (before.size() > MAX_LOCALE_BYTES) {
            throw new IllegalArgumentException("Language file is too large: " + resolvedFile.getPath());
        }

        byte[] bytes;
        try (InputStream input = Files.newInputStream(resolvedFile.toPath())) {
            bytes = input.readNBytes((int) MAX_LOCALE_BYTES + 1);
        } catch (NoSuchFileException failure) {
            return null;
        }
        if (bytes.length > MAX_LOCALE_BYTES) {
            throw new IllegalArgumentException("Language file is too large: " + resolvedFile.getPath());
        }

        BasicFileAttributes after;
        try {
            after = Files.readAttributes(resolvedFile.toPath(), BasicFileAttributes.class);
        } catch (NoSuchFileException failure) {
            return null;
        }
        if (!sameIdentity(before, after) || bytes.length != after.size()) {
            return null;
        }

        String content;
        try {
            content = StandardCharsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(bytes)).toString();
        } catch (CharacterCodingException failure) {
            throw new IOException("Language file is not valid UTF-8: " + resolvedFile.getPath(), failure);
        }
        return LocaleHotloadSnapshot.present(resolvedFile, resolvedLocale, content, sha256(bytes));
    }

    private static SnapshotCapture captureForReload(File file, String locale) {
        try {
            LocaleHotloadSnapshot snapshot = captureHotloadSnapshot(file, locale);
            if (snapshot == null) {
                return new SnapshotCapture(
                        null,
                        new IOException("Language file changed while being read: " + file.getPath())
                );
            }
            return new SnapshotCapture(snapshot, null);
        } catch (Exception failure) {
            return new SnapshotCapture(null, failure);
        }
    }

    private static boolean sameIdentity(BasicFileAttributes before, BasicFileAttributes after) {
        return before.isRegularFile() == after.isRegularFile()
                && before.size() == after.size()
                && before.lastModifiedTime().equals(after.lastModifiedTime())
                && Objects.equals(before.fileKey(), after.fileKey());
    }

    private static String sha256(byte[] content) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(content));
        } catch (NoSuchAlgorithmException failure) {
            throw new IllegalStateException("SHA-256 is unavailable", failure);
        }
    }

    private static void reportRejectedReload(String locale, LocalizationReloadResult result) {
        IrisLogging.error("Rejected locale reload for " + locale + "; continuing with " + activeLocale + ".");
        List<LocalizationIssue> issues = result.validation().errors();
        for (int index = 0; index < Math.min(issues.size(), MAX_REPORTED_ISSUES); index++) {
            LocalizationIssue issue = issues.get(index);
            IrisLogging.error(issue.source() + " [" + issue.key() + "]: " + issue.detail());
        }
        if (issues.size() > MAX_REPORTED_ISSUES) {
            IrisLogging.error((issues.size() - MAX_REPORTED_ISSUES) + " additional locale errors were omitted.");
        }
        if (result.failure() != null) {
            IrisLogging.reportError(result.failure());
        }
    }

    private record RenderedArgument(String token, MessageArgument argument) {
    }

    private record PlainMemo(LocalizationSnapshot snapshot, Map<String, String> values) {
    }

    private record SnapshotCapture(LocaleHotloadSnapshot snapshot, Exception failure) {
    }

    private record CachedMeta(Path file, FileTime modified, long size, LanguageMeta meta) {
    }

    record LanguageUpstream(Path root, LanguageDirectory directory, LanguageSourceManifest manifest,
                            LanguageUpstreamSync sync) {
        void close() {
            if (sync != null) {
                sync.close();
            }
        }
    }
}
