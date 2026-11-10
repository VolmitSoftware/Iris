package art.arcane.iris.core.localization;

import art.arcane.iris.core.IrisSettings;
import art.arcane.iris.spi.IrisLogging;
import art.arcane.iris.spi.IrisPlatforms;
import art.arcane.volmlib.util.director.DirectorTextResolver;
import art.arcane.volmlib.util.localization.LinesKey;
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
import art.arcane.volmlib.util.localization.PluralKey;
import art.arcane.volmlib.util.localization.PluralSelector;
import art.arcane.volmlib.util.localization.ResolvedLines;
import art.arcane.volmlib.util.localization.ResolvedText;
import art.arcane.volmlib.util.localization.TextKey;
import art.arcane.volmlib.util.localization.VolmitLocales;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
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
    private static final long MAX_LOCALE_BYTES = 2L * 1024L * 1024L;
    private static final int MAX_REPORTED_ISSUES = 12;
    private static final Pattern LOCALE_NAME = Pattern.compile("[A-Za-z0-9_-]+");
    private static final Pattern LEGACY_COLOR = Pattern.compile("(?i)\\u00a7[0-9A-FK-ORX]");
    private static final MessageCatalog CATALOG = IrisMessages.catalog();
    private static final List<String> BUKKIT_MESSAGE_IDS = CATALOG.ids().stream()
            .filter(id -> !id.startsWith("iris.modded."))
            .sorted()
            .toList();
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
    private static volatile File dataFolder;
    private static volatile String activeLocale = CATALOG.englishLocale();

    private IrisLanguage() {
    }

    public static synchronized void shutdown() {
        dataFolder = null;
        PLAIN_MEMO.set(null);
    }

    public static boolean initialize() {
        if (!IrisPlatforms.isBound()) {
            return false;
        }
        return reload(IrisPlatforms.get().dataFolder(), configuredLocale());
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

        File override = overrideFile(resolvedRoot, requestedLocale);
        SnapshotCapture capture = captureForReload(override, requestedLocale);
        boolean applied = applyReload(resolvedRoot, requestedLocale, capture);
        if (applied && notifyManualReload) {
            notifyManualReload(capture.snapshot());
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

        String configured = configuredLocale();
        String requestedLocale;
        try {
            requestedLocale = normalizeLocale(configured);
        } catch (RuntimeException exception) {
            IrisLogging.error("Rejected locale setting '" + configured + "'; continuing with " + activeLocale + ".");
            IrisLogging.reportError(exception);
            return false;
        }

        File expected = overrideFile(root, requestedLocale);
        if (!expected.equals(override.getAbsoluteFile())) {
            return true;
        }

        LocaleHotloadSnapshot snapshot = rawContent == null
                ? LocaleHotloadSnapshot.missing(expected, requestedLocale)
                : LocaleHotloadSnapshot.present(
                        expected,
                        requestedLocale,
                        rawContent,
                        sha256(rawContent.getBytes(StandardCharsets.UTF_8))
                );
        return applyReload(root.getAbsoluteFile(), requestedLocale, new SnapshotCapture(snapshot, null));
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
        try {
            return overrideFile(root, configuredLocale()).equals(file.getAbsoluteFile());
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    private static boolean applyReload(File root, String requestedLocale, SnapshotCapture capture) {
        LocalizationReloadResult result;
        if (capture.failure() == null) {
            result = MANAGER.reload(() -> loadCandidate(root, requestedLocale, capture.snapshot()));
        } else {
            result = MANAGER.reload(() -> {
                throw capture.failure();
            });
        }
        dataFolder = root;
        if (!result.applied()) {
            reportRejectedReload(requestedLocale, result);
            return false;
        }

        activeLocale = requestedLocale;
        int warnings = result.validation().warnings().size();
        IrisLogging.debug("Loaded locale " + requestedLocale + " with " + warnings + " fallback "
                + (warnings == 1 ? "entry" : "entries") + ".");
        return true;
    }

    private static void notifyManualReload(LocaleHotloadSnapshot snapshot) {
        for (BiConsumer<File, String> listener : MANUAL_RELOAD_LISTENERS) {
            try {
                listener.accept(snapshot.file(), snapshot.content());
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
        return new File(root, "languages/overrides");
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
        LocalizationSnapshot snapshot = MANAGER.snapshot();
        PlainMemo memo = PLAIN_MEMO.get();
        if (memo == null || memo.snapshot() != snapshot) {
            memo = new PlainMemo(snapshot, new ConcurrentHashMap<>());
            PLAIN_MEMO.set(memo);
        }
        String cached = memo.values().get(key.id());
        if (cached != null) {
            return cached;
        }
        String resolved = plain(key, MessageArgs.empty());
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
        if (key instanceof TextKey textKey) {
            return MANAGER.snapshot().resolve(textKey, arguments);
        }
        if (key instanceof PluralKey pluralKey) {
            return MANAGER.snapshot().resolve(pluralKey, arguments);
        }
        if (key instanceof LinesKey linesKey) {
            ResolvedLines lines = MANAGER.snapshot().resolve(linesKey, arguments);
            return new ResolvedText(lines.key(), lines.locale(), String.join("\n", lines.lines()), lines.arguments());
        }
        throw new IllegalArgumentException("Unsupported Iris message key: " + key.id());
    }

    private static LocalizationCandidate loadCandidate(
            File root,
            String locale,
            LocaleHotloadSnapshot snapshot
    ) throws Exception {
        File folder = new File(root, "languages/overrides");
        Files.createDirectories(folder.toPath());
        List<LocaleOverlay> overlays = new ArrayList<>(2);
        if (!snapshot.missing()) {
            overlays.add(parseOverlay(snapshot.file().getPath(), locale, snapshot.content()));
        }

        if (!CATALOG.englishLocale().equals(locale)) {
            LocaleOverlay bundled = loadBundledOverlay(locale);
            if (bundled != null) {
                overlays.add(bundled);
            }
        }
        return new LocalizationCandidate(CATALOG, overlays, PluralSelector.oneOther());
    }

    static LocaleOverlay loadBundledOverlay(String locale) throws Exception {
        String normalizedLocale = normalizeLocale(locale);
        if (CATALOG.englishLocale().equals(normalizedLocale)) {
            return null;
        }
        String resourceName = "/languages/" + normalizedLocale + ".json";
        InputStream input = IrisLanguage.class.getResourceAsStream(resourceName);
        if (input == null) {
            if (VolmitLocales.isBundled(normalizedLocale)) {
                throw new IllegalStateException("Missing bundled Iris locale resource: " + resourceName);
            }
            return null;
        }
        try (InputStream stream = input) {
            byte[] bytes = stream.readNBytes((int) MAX_LOCALE_BYTES + 1);
            if (bytes.length > MAX_LOCALE_BYTES) {
                throw new IllegalArgumentException("Bundled locale is too large: " + resourceName);
            }
            return parseOverlay("bundled:" + resourceName, normalizedLocale, new String(bytes, StandardCharsets.UTF_8));
        }
    }

    static LocaleOverlay parseOverlay(String source, String locale, String raw) {
        JsonElement parsed = JsonParser.parseString(raw == null || raw.isBlank() ? "{}" : raw);
        if (parsed.isJsonArray()) {
            return parseCompactBukkitOverlay(source, locale, parsed.getAsJsonArray());
        }
        if (!parsed.isJsonObject()) {
            throw new IllegalArgumentException("Locale source is not a JSON object: " + source);
        }
        JsonObject root = parsed.getAsJsonObject();
        for (String key : root.keySet()) {
            if (!key.equals("locale") && !key.equals("messages")) {
                throw new IllegalArgumentException("Unknown locale root key: " + key);
            }
        }
        if (root.has("locale")) {
            JsonElement declaredLocale = root.get("locale");
            if (!declaredLocale.isJsonPrimitive() || !locale.equals(normalizeLocale(declaredLocale.getAsString()))) {
                throw new IllegalArgumentException("Locale source declares a different locale than its file: " + source);
            }
        }
        LocaleOverlay.Builder builder = LocaleOverlay.builder(source, locale);
        if (!root.has("messages")) {
            return builder.build();
        }
        JsonElement messages = root.get("messages");
        if (!messages.isJsonObject()) {
            throw new IllegalArgumentException("Locale messages must be a JSON object: " + source);
        }
        appendMessages(builder, messages.getAsJsonObject(), "");
        return builder.build();
    }

    private static LocaleOverlay parseCompactBukkitOverlay(
            String source,
            String locale,
            JsonArray root
    ) {
        if (root.size() != 2 || !root.get(0).isJsonPrimitive() || !root.get(1).isJsonArray()) {
            throw new IllegalArgumentException("Compact locale source is invalid: " + source);
        }
        if (!locale.equals(normalizeLocale(root.get(0).getAsString()))) {
            throw new IllegalArgumentException("Locale source declares a different locale than its file: " + source);
        }
        JsonArray messages = root.get(1).getAsJsonArray();
        if (messages.size() != BUKKIT_MESSAGE_IDS.size()) {
            throw new IllegalArgumentException("Compact locale message count is invalid: " + source);
        }
        LocaleOverlay.Builder builder = LocaleOverlay.builder(source, locale);
        for (int i = 0; i < messages.size(); i++) {
            JsonElement value = messages.get(i);
            if (value == null || value.isJsonNull()) {
                continue;
            }
            appendCompactMessage(builder, BUKKIT_MESSAGE_IDS.get(i), value);
        }
        return builder.build();
    }

    private static void appendCompactMessage(
            LocaleOverlay.Builder builder,
            String key,
            JsonElement value
    ) {
        MessageKey definition = CATALOG.key(key);
        if (value.isJsonObject() && definition instanceof PluralKey) {
            builder.plural(key, readPlural(key, value.getAsJsonObject()));
        } else if (value.isJsonArray()) {
            builder.lines(key, readLines(key, value.getAsJsonArray()));
        } else if (value.isJsonPrimitive() && value.getAsJsonPrimitive().isString()) {
            builder.text(key, value.getAsString());
        } else {
            throw new IllegalArgumentException("Compact locale value has an invalid shape: " + key);
        }
    }

    private static void appendMessages(LocaleOverlay.Builder builder, JsonObject object, String prefix) {
        for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
            String key = prefix.isEmpty() ? entry.getKey() : prefix + "." + entry.getKey();
            JsonElement value = entry.getValue();
            if (value == null || value.isJsonNull()) {
                throw new IllegalArgumentException("Locale value cannot be null: " + key);
            }
            MessageKey definition = CATALOG.key(key);
            if (value.isJsonObject() && definition instanceof PluralKey) {
                builder.plural(key, readPlural(key, value.getAsJsonObject()));
            } else if (value.isJsonObject()) {
                appendMessages(builder, value.getAsJsonObject(), key);
            } else if (value.isJsonArray()) {
                builder.lines(key, readLines(key, value.getAsJsonArray()));
            } else if (value.isJsonPrimitive() && value.getAsJsonPrimitive().isString()) {
                builder.text(key, value.getAsString());
            } else {
                throw new IllegalArgumentException("Locale value must be text, lines, or plural forms: " + key);
            }
        }
    }

    private static List<String> readLines(String key, JsonArray array) {
        List<String> lines = new ArrayList<>(array.size());
        for (JsonElement value : array) {
            if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) {
                throw new IllegalArgumentException("Locale line must be text: " + key);
            }
            lines.add(value.getAsString());
        }
        return lines;
    }

    private static Map<String, String> readPlural(String key, JsonObject object) {
        Map<String, String> forms = new LinkedHashMap<>();
        for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
            JsonElement value = entry.getValue();
            if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) {
                throw new IllegalArgumentException("Locale plural form must be text: " + key + "." + entry.getKey());
            }
            forms.put(entry.getKey(), value.getAsString());
        }
        return forms;
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
        char[] characters = value.toCharArray();
        for (int index = 0; index < characters.length - 1; index++) {
            if (characters[index] == '&' && isColorCode(characters[index + 1])) {
                characters[index] = '\u00a7';
                characters[index + 1] = Character.toLowerCase(characters[index + 1]);
            }
        }
        return new String(characters);
    }

    private static boolean isColorCode(char value) {
        char lowered = Character.toLowerCase(value);
        return lowered >= '0' && lowered <= '9' || lowered >= 'a' && lowered <= 'f'
                || lowered >= 'k' && lowered <= 'o' || lowered == 'r' || lowered == 'x';
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
        return new File(new File(root, "languages/overrides"), normalizeLocale(locale) + ".json").getAbsoluteFile();
    }

    static LocaleHotloadSnapshot captureHotloadSnapshot(File file, String locale) throws IOException {
        File resolvedFile = Objects.requireNonNull(file, "Locale override file cannot be null").getAbsoluteFile();
        String resolvedLocale = normalizeLocale(locale);
        BasicFileAttributes before;
        try {
            before = Files.readAttributes(resolvedFile.toPath(), BasicFileAttributes.class);
        } catch (NoSuchFileException failure) {
            return LocaleHotloadSnapshot.missing(resolvedFile, resolvedLocale);
        }
        if (!before.isRegularFile()) {
            throw new IllegalArgumentException("Locale override is not a regular file: " + resolvedFile.getPath());
        }
        if (before.size() > MAX_LOCALE_BYTES) {
            throw new IllegalArgumentException("Locale override is too large: " + resolvedFile.getPath());
        }

        byte[] bytes;
        try (InputStream input = Files.newInputStream(resolvedFile.toPath())) {
            bytes = input.readNBytes((int) MAX_LOCALE_BYTES + 1);
        } catch (NoSuchFileException failure) {
            return null;
        }
        if (bytes.length > MAX_LOCALE_BYTES) {
            throw new IllegalArgumentException("Locale override is too large: " + resolvedFile.getPath());
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
            throw new IOException("Locale override is not valid UTF-8: " + resolvedFile.getPath(), failure);
        }
        return LocaleHotloadSnapshot.present(resolvedFile, resolvedLocale, content, sha256(bytes));
    }

    private static SnapshotCapture captureForReload(File file, String locale) {
        try {
            LocaleHotloadSnapshot snapshot = captureHotloadSnapshot(file, locale);
            if (snapshot == null) {
                return new SnapshotCapture(
                        null,
                        new IOException("Locale override changed while being read: " + file.getPath())
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
}
