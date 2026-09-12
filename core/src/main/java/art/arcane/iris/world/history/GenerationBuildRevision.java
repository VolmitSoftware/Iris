package art.arcane.iris.world.history;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

final class GenerationBuildRevision {
    private static final int MAXIMUM_BYTES = 8 * 1024 * 1024;
    private static final Pattern SHA256 = Pattern.compile("[0-9a-f]{64}");

    private GenerationBuildRevision() {
    }

    static String requireFingerprint(int abi, Class<?> factory) {
        Descriptor descriptor = load(abi);
        if (!descriptor.factoryClass().equals(factory.getName())) {
            throw new IllegalStateException("Generation ABI " + abi + " declares factory " + descriptor.factoryClass() + ".");
        }
        return descriptor.fingerprint();
    }

    static Descriptor load(int abi) {
        String resource = "/META-INF/iris/generation-kernels/abi-" + abi + ".revision";
        try (InputStream input = GenerationBuildRevision.class.getResourceAsStream(resource)) {
            if (input == null) {
                throw new IOException("Missing generation kernel build revision " + resource + ".");
            }
            byte[] bytes = input.readNBytes(MAXIMUM_BYTES + 1);
            String[] lines = new String(bytes, StandardCharsets.UTF_8).split("\n", -1);
            if (lines.length < 4 || !lines[2].startsWith("factory\t")) {
                throw new IOException("Generation build revision has no factory identity.");
            }
            String factoryClass = lines[2].substring("factory\t".length());
            if (!factoryClass.matches("[A-Za-z_$][A-Za-z0-9_$]*(\\.[A-Za-z_$][A-Za-z0-9_$]*)+")) {
                throw new IOException("Invalid generation kernel factory identity.");
            }
            String fingerprint = fingerprint(abi, factoryClass, bytes);
            List<GenerationKernelRegistry.AlgorithmVersion> algorithms = new ArrayList<>();
            for (String line : lines) {
                if (line.startsWith("algorithm\t")) {
                    String[] fields = line.split("\t", -1);
                    algorithms.add(new GenerationKernelRegistry.AlgorithmVersion(
                            Integer.parseInt(fields[1]), Integer.parseInt(fields[2])));
                }
            }
            return new Descriptor(abi, factoryClass, List.copyOf(algorithms), fingerprint);
        } catch (IOException failure) {
            throw new IllegalStateException("Cannot load Iris generation build revision for ABI " + abi + ".", failure);
        }
    }

    static String fingerprint(int abi, String factoryClass, byte[] bytes) throws IOException {
        Objects.requireNonNull(factoryClass, "factoryClass");
        Objects.requireNonNull(bytes, "bytes");
        if (abi < 1 || bytes.length == 0 || bytes.length > MAXIMUM_BYTES) {
            throw new IOException("Invalid generation build revision size or ABI.");
        }
        String source = new String(bytes, StandardCharsets.UTF_8);
        String prefix = "iris-generation-build-revision-v1\nabi\t" + abi + "\nfactory\t" + factoryClass + "\n";
        if (!source.startsWith(prefix) || !source.endsWith("\n") || source.indexOf('\r') >= 0
                || !source.contains("\nsource\t") || !source.contains("\ndependency\t")) {
            throw new IOException("Generation build revision does not match ABI " + abi + " and factory " + factoryClass + ".");
        }
        requireCanonicalEntries(source.substring(prefix.length()));
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException failure) {
            throw new IllegalStateException(failure);
        }
    }

    private static void requireCanonicalEntries(String source) throws IOException {
        String[] entries = source.split("\n", -1);
        if (entries.length > 30_000) {
            throw new IOException("Generation build revision has too many entries.");
        }
        int previousType = -1;
        String previousKey = "";
        boolean hasScope = false;
        boolean hasAlgorithm = false;
        int previousRng = 0;
        int previousSeed = 0;
        for (int index = 0; index < entries.length - 1; index++) {
            String[] fields = entries[index].split("\t", -1);
            if (fields[0].equals("algorithm")) {
                if (previousType != -1 || fields.length != 3 || !fields[1].matches("[1-9][0-9]{0,8}")
                        || !fields[2].matches("[1-9][0-9]{0,8}")) {
                    throw new IOException("Generation build revision contains an invalid algorithm version.");
                }
                int rng = Integer.parseInt(fields[1]);
                int seed = Integer.parseInt(fields[2]);
                if (rng < previousRng || (rng == previousRng && seed <= previousSeed)) {
                    throw new IOException("Generation kernel algorithm versions are not canonical.");
                }
                previousRng = rng;
                previousSeed = seed;
                hasAlgorithm = true;
                continue;
            }
            int type = switch (fields[0]) {
                case "scope" -> 0;
                case "exclude" -> 1;
                case "source" -> 2;
                case "dependency" -> 3;
                default -> -1;
            };
            int expectedFields = type < 2 ? 2 : 3;
            if (type < previousType || type < 0 || fields.length != expectedFields || fields[1].isBlank()
                    || fields[1].chars().anyMatch(Character::isWhitespace)
                    || (type == previousType && fields[1].compareTo(previousKey) <= 0)
                    || (type >= 2 && !SHA256.matcher(fields[2]).matches())) {
                throw new IOException("Generation build revision contains an invalid or noncanonical entry.");
            }
            previousType = type;
            previousKey = fields[1];
            hasScope |= type == 0;
        }
        if (!hasScope || !hasAlgorithm) {
            throw new IOException("Generation build revision has no source scope or algorithm version.");
        }
    }

    record Descriptor(int abi, String factoryClass, List<GenerationKernelRegistry.AlgorithmVersion> algorithms,
                      String fingerprint) {
    }
}
