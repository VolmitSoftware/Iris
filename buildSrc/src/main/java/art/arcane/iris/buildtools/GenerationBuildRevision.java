package art.arcane.iris.buildtools;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Pattern;
import java.util.jar.Attributes;
import java.util.jar.Manifest;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public final class GenerationBuildRevision {
    public static final String FORMAT = "iris-generation-build-revision-v1";
    private static final Pattern HASH = Pattern.compile("[0-9a-f]{64}");
    private static final long MAXIMUM_REVISION_BYTES = 8L * 1024L * 1024L;
    private static final int MAXIMUM_ENTRIES = 30_000;

    private GenerationBuildRevision() {
    }

    public static void main(String[] arguments) throws IOException {
        if (arguments.length < 4 || arguments.length % 2 != 0 || !arguments[0].equals("verify-dependencies")) {
            throw new IllegalArgumentException("Expected verify-dependencies <revision> <identity> <artifact> pairs.");
        }
        Map<String, Path> dependencies = new TreeMap<>();
        for (int index = 2; index < arguments.length; index += 2) {
            if (dependencies.putIfAbsent(arguments[index], Path.of(arguments[index + 1])) != null) {
                throw new IllegalArgumentException("Duplicate resolved generation dependency.");
            }
        }
        verifyDependencySubset(Path.of(arguments[1]), dependencies);
    }

    public static void verifyDependencySubset(Path revision, Map<String, Path> dependencies) throws IOException {
        SourceManifest expected = read(revision);
        for (Map.Entry<String, String> dependency : dependencyHashes(dependencies).entrySet()) {
            if (!Objects.equals(expected.dependencies().get(dependency.getKey()), dependency.getValue())) {
                throw new IOException("Resolved platform dependency does not match generation ABI " + expected.abi()
                        + ": " + dependency.getKey());
            }
        }
    }

    public static void verifyCatalog(Path catalog, Map<Integer, SourceManifest> manifests) throws IOException {
        if (!Files.isRegularFile(catalog) || Files.size(catalog) > 16 * 1024) {
            throw new IOException("Missing or oversized generation kernel catalog.");
        }
        String source = Files.readString(catalog, StandardCharsets.UTF_8);
        String[] lines = source.split("\n", -1);
        if (!source.endsWith("\n") || source.indexOf('\r') >= 0 || lines.length < 4 || lines.length > 128
                || !lines[0].equals("iris-generation-kernel-catalog-v1")) {
            throw new IOException("Invalid generation kernel catalog.");
        }
        try {
            String[] current = lines[1].split("\t", -1);
            if (current.length != 4 || !current[0].equals("current")) {
                throw new IllegalArgumentException("Missing current generation version.");
            }
            int currentAbi = positive(current[1]);
            AlgorithmVersion algorithms = new AlgorithmVersion(positive(current[2]), positive(current[3]));
            SourceManifest selected = manifests.get(currentAbi);
            if (selected == null || !selected.algorithms().contains(algorithms)) {
                throw new IllegalArgumentException("Current generation version has no matching build factory.");
            }
            TreeSet<Integer> registered = new TreeSet<>();
            int previous = 0;
            for (int index = 2; index < lines.length - 1; index++) {
                String[] kernel = lines[index].split("\t", -1);
                if (kernel.length != 2 || !kernel[0].equals("kernel")) {
                    throw new IllegalArgumentException("Invalid kernel registration.");
                }
                int abi = positive(kernel[1]);
                if (abi <= previous) {
                    throw new IllegalArgumentException("Kernel registrations must be unique and sorted.");
                }
                previous = abi;
                registered.add(abi);
            }
            if (!registered.equals(manifests.keySet())) {
                throw new IllegalArgumentException("Kernel catalog must register exactly the packaged kernels.");
            }
        } catch (IllegalArgumentException failure) {
            throw new IOException("Invalid generation kernel catalog.", failure);
        }
    }

    private static int positive(String value) {
        if (!value.matches("[1-9][0-9]{0,8}")) {
            throw new IllegalArgumentException("Invalid generation version.");
        }
        return Integer.parseInt(value);
    }

    public static SourceManifest capture(CaptureOptions options, Map<String, Path> dependencies) throws IOException {
        CaptureOptions required = Objects.requireNonNull(options, "options");
        Map<String, String> sourceHashes = sources(required.repository(), required.roots(), required.exclusions());
        if (sourceHashes.isEmpty()) {
            throw new IOException("Generation kernel source scope is empty.");
        }
        return new SourceManifest(required.abi(), required.factoryClass(), required.algorithms(), required.roots(), required.exclusions(),
                sourceHashes, dependencyHashes(dependencies));
    }

    public static void verifySnapshot(Path repository, Path revision, Map<String, Path> dependencies) throws IOException {
        SourceManifest expected = read(revision);
        SourceManifest actual = capture(new CaptureOptions(repository, expected.abi(), expected.factoryClass(), expected.algorithms(),
                expected.roots(), expected.exclusions()), dependencies);
        List<String> failures = new ArrayList<>();
        differences("source", expected.sources(), actual.sources(), failures);
        differences("dependency", expected.dependencies(), actual.dependencies(), failures);
        if (!failures.isEmpty()) {
            throw new IOException("Generation build inputs changed after revision capture:\n"
                    + String.join("\n", failures));
        }
    }

    public static void write(Path destination, SourceManifest manifest) throws IOException {
        Files.createDirectories(destination.toAbsolutePath().getParent());
        Files.writeString(destination, encode(manifest), StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
    }

    public static SourceManifest read(Path path) throws IOException {
        if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS) || Files.size(path) > MAXIMUM_REVISION_BYTES) {
            throw new IOException("Missing or oversized generation build revision: " + path);
        }
        List<String> lines = Files.readAllLines(path, StandardCharsets.UTF_8);
        if (lines.size() < 4 || !FORMAT.equals(lines.getFirst()) || lines.size() > MAXIMUM_ENTRIES) {
            throw new IOException("Invalid generation build revision: " + path);
        }
        int abi = 0;
        String factory = null;
        List<String> roots = new ArrayList<>();
        List<AlgorithmVersion> algorithms = new ArrayList<>();
        List<String> exclusions = new ArrayList<>();
        Map<String, String> sources = new TreeMap<>();
        Map<String, String> dependencies = new TreeMap<>();
        try {
            for (int index = 1; index < lines.size(); index++) {
                String[] entry = lines.get(index).split("\t", -1);
                if (entry.length < 2 || entry.length > 3) {
                    throw new IllegalArgumentException("Invalid revision entry.");
                }
                switch (entry[0]) {
                    case "abi" -> {
                        if (abi != 0 || entry.length != 2) {
                            throw new IllegalArgumentException("Duplicate or invalid ABI.");
                        }
                        abi = Integer.parseInt(entry[1]);
                    }
                    case "factory" -> {
                        if (factory != null || entry.length != 2) {
                            throw new IllegalArgumentException("Duplicate or invalid factory.");
                        }
                        factory = entry[1];
                    }
                    case "scope" -> addPath(roots, entry);
                    case "algorithm" -> {
                        if (entry.length != 3) {
                            throw new IllegalArgumentException("Invalid algorithm version.");
                        }
                        algorithms.add(new AlgorithmVersion(Integer.parseInt(entry[1]), Integer.parseInt(entry[2])));
                    }
                    case "exclude" -> addPath(exclusions, entry);
                    case "source" -> addHash(sources, entry, true);
                    case "dependency" -> addHash(dependencies, entry, false);
                    default -> throw new IllegalArgumentException("Unknown generation build revision entry.");
                }
            }
            SourceManifest manifest = new SourceManifest(abi, factory, algorithms, roots, exclusions, sources, dependencies);
            if (!Files.readString(path, StandardCharsets.UTF_8).equals(encode(manifest))) {
                throw new IllegalArgumentException("Generation build revision is not canonical.");
            }
            return manifest;
        } catch (RuntimeException failure) {
            throw new IOException("Invalid generation build revision: " + path, failure);
        }
    }

    public static String fingerprint(SourceManifest manifest) {
        return hash(encode(manifest).getBytes(StandardCharsets.UTF_8));
    }

    public static String encode(SourceManifest manifest) {
        StringBuilder text = new StringBuilder(FORMAT).append('\n');
        text.append("abi\t").append(manifest.abi()).append('\n');
        text.append("factory\t").append(manifest.factoryClass()).append('\n');
        for (AlgorithmVersion algorithm : manifest.algorithms()) {
            text.append("algorithm\t").append(algorithm.rng()).append('\t').append(algorithm.seed()).append('\n');
        }
        for (String scope : manifest.roots()) {
            text.append("scope\t").append(scope).append('\n');
        }
        for (String exclusion : manifest.exclusions()) {
            text.append("exclude\t").append(exclusion).append('\n');
        }
        appendHashes(text, "source", manifest.sources());
        appendHashes(text, "dependency", manifest.dependencies());
        return text.toString();
    }

    public static String artifactHash(Path artifact) throws IOException {
        if (!Files.isRegularFile(artifact)) {
            throw new IOException("Missing generation dependency artifact: " + artifact);
        }
        MessageDigest digest = digest();
        try (ZipFile zip = new ZipFile(artifact.toFile())) {
            hashRuntimeManifest(zip, digest);
            Map<String, ZipEntry> entries = new TreeMap<>();
            for (ZipEntry entry : zip.stream().toList()) {
                String name = entry.getName();
                if (entry.isDirectory() || metadata(name)) {
                    continue;
                }
                if (entries.putIfAbsent(name, entry) != null) {
                    throw new IOException("Duplicate dependency artifact entry: " + name);
                }
            }
            byte[] buffer = new byte[16 * 1024];
            for (Map.Entry<String, ZipEntry> entry : entries.entrySet()) {
                update(digest, entry.getKey());
                try (InputStream input = zip.getInputStream(entry.getValue())) {
                    MessageDigest content = digest();
                    int read;
                    while ((read = input.read(buffer)) != -1) {
                        content.update(buffer, 0, read);
                    }
                    update(digest, HexFormat.of().formatHex(content.digest()));
                }
            }
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    private static void hashRuntimeManifest(ZipFile zip, MessageDigest digest) throws IOException {
        ZipEntry entry = zip.getEntry("META-INF/MANIFEST.MF");
        if (entry == null) {
            return;
        }
        Manifest manifest;
        try (InputStream input = zip.getInputStream(entry)) {
            manifest = new Manifest(input);
        }
        List<String> runtimeAttributes = List.of("Multi-Release", "Automatic-Module-Name", "Class-Path", "Sealed",
                "Agent-Class", "Premain-Class", "Launcher-Agent-Class", "Boot-Class-Path",
                "Can-Redefine-Classes", "Can-Retransform-Classes", "Can-Set-Native-Method-Prefix");
        for (String name : runtimeAttributes) {
            String value = manifest.getMainAttributes().getValue(name);
            if (value != null) {
                update(digest, "manifest/" + name);
                update(digest, value);
            }
        }
        for (Map.Entry<String, Attributes> section : new TreeMap<>(manifest.getEntries()).entrySet()) {
            String sealed = section.getValue().getValue("Sealed");
            if (sealed != null) {
                update(digest, "manifest/section/" + section.getKey());
                update(digest, sealed);
            }
        }
    }

    private static Map<String, String> sources(Path repository, List<String> roots, List<String> exclusions)
            throws IOException {
        Path base = repository.toAbsolutePath().normalize();
        Map<String, String> hashes = new TreeMap<>();
        for (String root : roots) {
            Path sourceRoot = base.resolve(root).normalize();
            if (!sourceRoot.startsWith(base) || !Files.exists(sourceRoot, LinkOption.NOFOLLOW_LINKS)) {
                throw new IOException("Missing generation source scope: " + root);
            }
            try (Stream<Path> walk = Files.walk(sourceRoot)) {
                for (Path source : walk.toList()) {
                    String relative = base.relativize(source).toString().replace('\\', '/');
                    if (excluded(relative, exclusions)) {
                        continue;
                    }
                    if (Files.isSymbolicLink(source)) {
                        throw new IOException("Generation source must not be a symbolic link: " + relative);
                    }
                    if (Files.isRegularFile(source) && (source.equals(sourceRoot)
                            || source.getFileName().toString().endsWith(".java"))) {
                        String content = Files.readString(source, StandardCharsets.UTF_8).replace("\r\n", "\n");
                        hashes.put(relative, hash(content.getBytes(StandardCharsets.UTF_8)));
                    }
                }
            }
        }
        return hashes;
    }

    private static Map<String, String> dependencyHashes(Map<String, Path> dependencies) throws IOException {
        Map<String, String> hashes = new TreeMap<>();
        for (Map.Entry<String, Path> dependency : Objects.requireNonNull(dependencies, "dependencies").entrySet()) {
            String key = token(dependency.getKey());
            hashes.put(key, artifactHash(dependency.getValue()));
        }
        if (hashes.isEmpty()) {
            throw new IOException("Generation kernel dependency scope is empty.");
        }
        return hashes;
    }

    private static void differences(String type, Map<String, String> expected, Map<String, String> actual,
                                    List<String> failures) {
        TreeSet<String> keys = new TreeSet<>(expected.keySet());
        keys.addAll(actual.keySet());
        for (String key : keys) {
            if (!Objects.equals(expected.get(key), actual.get(key))) {
                String change = !expected.containsKey(key) ? "added" : !actual.containsKey(key) ? "deleted" : "changed";
                failures.add(type + " " + change + ": " + key);
            }
        }
    }

    private static boolean excluded(String path, List<String> exclusions) {
        for (String exclusion : exclusions) {
            if (path.equals(exclusion) || path.startsWith(exclusion + "/")) {
                return true;
            }
        }
        return false;
    }

    private static boolean metadata(String name) {
        return name.equals("META-INF/MANIFEST.MF") || name.startsWith("META-INF/maven/")
                || name.startsWith("META-INF/") && (name.endsWith(".SF") || name.endsWith(".RSA") || name.endsWith(".DSA"));
    }

    private static void addPath(List<String> paths, String[] entry) {
        if (entry.length != 2 || paths.contains(entry[1])) {
            throw new IllegalArgumentException("Duplicate or invalid source scope.");
        }
        paths.add(relative(entry[1]));
    }

    private static void addHash(Map<String, String> hashes, String[] entry, boolean path) {
        if (entry.length != 3 || !HASH.matcher(entry[2]).matches()) {
            throw new IllegalArgumentException("Invalid revision hash.");
        }
        String key = path ? relative(entry[1]) : token(entry[1]);
        if (hashes.putIfAbsent(key, entry[2]) != null) {
            throw new IllegalArgumentException("Duplicate revision entry.");
        }
    }

    private static void appendHashes(StringBuilder text, String type, Map<String, String> hashes) {
        for (Map.Entry<String, String> entry : new TreeMap<>(hashes).entrySet()) {
            text.append(type).append('\t').append(entry.getKey()).append('\t').append(entry.getValue()).append('\n');
        }
    }

    private static String relative(String value) {
        String path = token(value);
        if (path.startsWith("/") || path.contains("\\") || path.contains(":")
                || !Path.of(path).normalize().toString().replace('\\', '/').equals(path)
                || path.equals("..") || path.startsWith("../")) {
            throw new IllegalArgumentException("Invalid generation source path: " + path);
        }
        return path;
    }

    private static String token(String value) {
        String required = Objects.requireNonNull(value, "revision token");
        if (required.isBlank() || required.chars().anyMatch(Character::isWhitespace)) {
            throw new IllegalArgumentException("Invalid generation revision token.");
        }
        return required;
    }

    private static List<String> paths(Collection<String> values) {
        TreeSet<String> sorted = new TreeSet<>();
        for (String value : values) {
            if (!sorted.add(relative(value))) {
                throw new IllegalArgumentException("Duplicate generation source scope.");
            }
        }
        return List.copyOf(sorted);
    }

    private static String hash(byte[] bytes) {
        return HexFormat.of().formatHex(digest().digest(bytes));
    }

    private static MessageDigest digest() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException failure) {
            throw new IllegalStateException(failure);
        }
    }

    private static void update(MessageDigest digest, String value) {
        digest.update(value.getBytes(StandardCharsets.UTF_8));
        digest.update((byte) '\n');
    }

    public record CaptureOptions(Path repository, int abi, String factoryClass, List<AlgorithmVersion> algorithms, List<String> roots,
                                 List<String> exclusions) {
        public CaptureOptions {
            Objects.requireNonNull(repository, "repository");
            if (abi < 1) {
                throw new IllegalArgumentException("Generation ABI must be positive.");
            }
            factoryClass = token(factoryClass);
            TreeSet<AlgorithmVersion> sortedAlgorithms = new TreeSet<>(algorithms);
            if (sortedAlgorithms.isEmpty() || sortedAlgorithms.size() != algorithms.size()) {
                throw new IllegalArgumentException("Generation algorithm versions must be nonempty and unique.");
            }
            algorithms = List.copyOf(sortedAlgorithms);
            roots = paths(roots);
            exclusions = paths(exclusions);
            if (roots.isEmpty()) {
                throw new IllegalArgumentException("Generation source scope must not be empty.");
            }
        }
    }

    public record SourceManifest(int abi, String factoryClass, List<AlgorithmVersion> algorithms, List<String> roots, List<String> exclusions,
                           Map<String, String> sources, Map<String, String> dependencies) {
        public SourceManifest {
            CaptureOptions options = new CaptureOptions(Path.of("."), abi, factoryClass, algorithms, roots, exclusions);
            algorithms = options.algorithms();
            roots = options.roots();
            exclusions = options.exclusions();
            sources = Map.copyOf(sources);
            dependencies = Map.copyOf(dependencies);
            if (sources.isEmpty() || dependencies.isEmpty() || sources.size() + dependencies.size() > MAXIMUM_ENTRIES) {
                throw new IllegalArgumentException("Invalid generation build revision entry count.");
            }
        }
    }

    public record AlgorithmVersion(int rng, int seed) implements Comparable<AlgorithmVersion> {
        public AlgorithmVersion {
            if (rng < 1 || seed < 1) {
                throw new IllegalArgumentException("Generation algorithm versions must be positive.");
            }
        }

        @Override
        public int compareTo(AlgorithmVersion other) {
            int byRng = Integer.compare(rng, other.rng);
            return byRng == 0 ? Integer.compare(seed, other.seed) : byRng;
        }
    }
}
