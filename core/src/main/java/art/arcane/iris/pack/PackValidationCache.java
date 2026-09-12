package art.arcane.iris.pack;

import art.arcane.iris.pack.datapack.ServerConfigurator;
import art.arcane.iris.pack.validation.CompatAction;
import art.arcane.iris.pack.validation.CompatFinding;
import art.arcane.iris.pack.validation.CompatRegistry;
import art.arcane.iris.world.history.GenerationKernelRegistry;
import art.arcane.iris.spi.IrisLogging;
import art.arcane.iris.spi.IrisPlatform;
import art.arcane.iris.spi.IrisPlatforms;
import art.arcane.iris.spi.PlatformRegistries;
import art.arcane.iris.spi.PlatformStructureHooks;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

public final class PackValidationCache {
    private static final int SCHEMA_VERSION = 3;
    private static final long MAX_CACHE_BYTES = 16L * 1024L * 1024L;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private PackValidationCache() {
    }

    public static String contentFingerprint(File packsRoot) {
        return ServerConfigurator.computePackFingerprint(packsRoot);
    }

    public static String contextFingerprint() {
        if (!IrisPlatforms.isBound()) {
            return "";
        }
        return contextFingerprint(ImplementationIdentity.FINGERPRINT);
    }

    static String contextFingerprint(String implementationFingerprint) {
        if (!IrisPlatforms.isBound() || implementationFingerprint == null || implementationFingerprint.isBlank()) {
            return "";
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            IrisPlatform platform = IrisPlatforms.get();
            update(digest, platform.platformName());
            update(digest, platform.minecraftVersion());
            update(digest, Integer.toString(platform.irisVersionNumber()));
            update(digest, implementationFingerprint);
            update(digest, Boolean.toString(ContentKeyValidator.strictContent()));

            PlatformRegistries registries = Objects.requireNonNull(
                    platform.registries(), "Pack validation platform registries");
            updateSorted(digest, registries.blockKeys());
            updateSorted(digest, registries.blockTypeKeys());
            updateSorted(digest, registries.biomeKeys());
            updateSorted(digest, registries.itemKeys());
            updateSorted(digest, registries.entityKeys());

            PlatformStructureHooks hooks = Objects.requireNonNull(
                    platform.structureHooks(), "Pack validation structure hooks");
            updateSorted(digest, hooks.structureKeys());
            updateSorted(digest, hooks.jigsawStructureKeys());
            updateSorted(digest, hooks.templatePoolKeys());
            updateSorted(digest, hooks.structureSetKeys());
            updateSorted(digest, hooks.objectFeatureKeys());
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    public static Optional<List<PackValidationResult>> load(
            Path cacheFile,
            String contentFingerprint,
            String contextFingerprint,
            List<String> expectedPackNames
    ) {
        if (cacheFile == null
                || contentFingerprint == null || contentFingerprint.isBlank()
                || contextFingerprint == null || contextFingerprint.isBlank()
                || !Files.isRegularFile(cacheFile, LinkOption.NOFOLLOW_LINKS)
                || Files.isSymbolicLink(cacheFile)) {
            return Optional.empty();
        }
        try {
            if (Files.size(cacheFile) > MAX_CACHE_BYTES) {
                return Optional.empty();
            }
            CacheState state = GSON.fromJson(Files.readString(cacheFile, StandardCharsets.UTF_8), CacheState.class);
            if (state == null
                    || state.schemaVersion != SCHEMA_VERSION
                    || !contentFingerprint.equals(state.contentFingerprint)
                    || !contextFingerprint.equals(state.contextFingerprint)
                    || state.results == null) {
                return Optional.empty();
            }
            Set<String> expected = new HashSet<>(expectedPackNames == null ? List.of() : expectedPackNames);
            List<PackValidationResult> results = new ArrayList<>(state.results.size());
            Set<String> actual = new HashSet<>();
            for (CachedResult cached : state.results) {
                if (cached == null || cached.packName == null || cached.packName.isBlank()
                        || cached.blockingErrors == null || cached.warnings == null
                        || !actual.add(cached.packName)) {
                    return Optional.empty();
                }
                List<CompatFinding> findings = readFindings(cached.compatFindings);
                if (findings == null) {
                    return Optional.empty();
                }
                results.add(new PackValidationResult(
                        cached.packName,
                        cached.blockingErrors,
                        cached.warnings,
                        cached.validatedAtMillis,
                        findings,
                        cached.minecraftVersion));
            }
            if (!expected.equals(actual)) {
                return Optional.empty();
            }
            results.sort(Comparator.comparing(PackValidationResult::getPackName));
            return Optional.of(List.copyOf(results));
        } catch (IOException | RuntimeException exception) {
            return Optional.empty();
        }
    }

    public static void save(
            Path cacheFile,
            String contentFingerprint,
            String contextFingerprint,
            List<PackValidationResult> results
    ) throws IOException {
        if (cacheFile == null || contentFingerprint == null || contentFingerprint.isBlank()
                || contextFingerprint == null || contextFingerprint.isBlank()) {
            return;
        }
        List<PackValidationResult> sorted = new ArrayList<>(results == null ? List.of() : results);
        sorted.sort(Comparator.comparing(PackValidationResult::getPackName));
        CacheState state = new CacheState();
        state.schemaVersion = SCHEMA_VERSION;
        state.contentFingerprint = contentFingerprint;
        state.contextFingerprint = contextFingerprint;
        state.results = new ArrayList<>(sorted.size());
        for (PackValidationResult result : sorted) {
            CachedResult cached = new CachedResult();
            cached.packName = result.getPackName();
            cached.blockingErrors = List.copyOf(result.getBlockingErrors());
            cached.warnings = List.copyOf(result.getWarnings());
            cached.validatedAtMillis = result.getValidatedAtMillis();
            cached.minecraftVersion = result.getMinecraftVersion();
            cached.compatFindings = writeFindings(result.getCompatFindings());
            state.results.add(cached);
        }

        Path absolute = cacheFile.toAbsolutePath().normalize();
        Path parent = Objects.requireNonNull(absolute.getParent(), "Pack validation cache parent");
        Files.createDirectories(parent);
        Path staged = Files.createTempFile(parent, ".pack-validation-", ".tmp");
        try {
            byte[] content = GSON.toJson(state).getBytes(StandardCharsets.UTF_8);
            if (content.length > MAX_CACHE_BYTES) {
                throw new IOException("Pack validation cache exceeds " + MAX_CACHE_BYTES + " bytes");
            }
            Files.write(staged, content);
            try {
                Files.move(staged, absolute, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException exception) {
                Files.move(staged, absolute, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(staged);
        }
    }

    /** Null when a persisted finding is malformed; the caller discards the whole cache file. */
    private static List<CompatFinding> readFindings(List<CachedFinding> cached) {
        if (cached == null) {
            return List.of();
        }
        List<CompatFinding> findings = new ArrayList<>(cached.size());
        for (CachedFinding finding : cached) {
            if (finding == null || finding.key == null || finding.registry == null || finding.action == null) {
                return null;
            }
            CompatRegistry registry;
            CompatAction action;
            try {
                registry = CompatRegistry.valueOf(finding.registry);
                action = CompatAction.valueOf(finding.action);
            } catch (IllegalArgumentException unknownEnum) {
                return null;
            }
            findings.add(new CompatFinding(
                    registry, finding.key, action, finding.subjectType, finding.subjectKey, finding.detail));
        }
        return List.copyOf(findings);
    }

    private static List<CachedFinding> writeFindings(List<CompatFinding> findings) {
        if (findings == null || findings.isEmpty()) {
            return List.of();
        }
        List<CachedFinding> cached = new ArrayList<>(findings.size());
        for (CompatFinding finding : findings) {
            CachedFinding entry = new CachedFinding();
            entry.registry = finding.registry().name();
            entry.key = finding.key();
            entry.action = finding.action().name();
            entry.subjectType = finding.subjectType();
            entry.subjectKey = finding.subjectKey();
            entry.detail = finding.detail();
            cached.add(entry);
        }
        return cached;
    }

    private static void updateSorted(MessageDigest digest, List<String> values) {
        List<String> sorted = new ArrayList<>(Objects.requireNonNull(values, "Pack validation registry keys"));
        sorted.sort(String::compareTo);
        update(digest, Integer.toString(sorted.size()));
        for (String value : sorted) {
            update(digest, value);
        }
    }

    private static String implementationFingerprint() {
        try {
            GenerationKernelRegistry kernels = GenerationKernelRegistry.standard();
            return kernels.requireSupported(kernels.current()).implementationFingerprint();
        } catch (IOException | RuntimeException | LinkageError failure) {
            IrisLogging.reportError(
                    "Cannot identify the Iris validation implementation; persisted pack validation is disabled.", failure);
            return "";
        }
    }

    private static void update(MessageDigest digest, String value) {
        byte[] bytes = Objects.requireNonNullElse(value, "").getBytes(StandardCharsets.UTF_8);
        digest.update((byte) (bytes.length >>> 24));
        digest.update((byte) (bytes.length >>> 16));
        digest.update((byte) (bytes.length >>> 8));
        digest.update((byte) bytes.length);
        digest.update(bytes);
    }

    private static final class CacheState {
        private int schemaVersion;
        private String contentFingerprint;
        private String contextFingerprint;
        private List<CachedResult> results;
    }

    private static final class ImplementationIdentity {
        private static final String FINGERPRINT = implementationFingerprint();
    }

    private static final class CachedResult {
        private String packName;
        private List<String> blockingErrors;
        private List<String> warnings;
        private long validatedAtMillis;
        private String minecraftVersion;
        private List<CachedFinding> compatFindings;
    }

    private static final class CachedFinding {
        private String registry;
        private String key;
        private String action;
        private String subjectType;
        private String subjectKey;
        private String detail;
    }
}
