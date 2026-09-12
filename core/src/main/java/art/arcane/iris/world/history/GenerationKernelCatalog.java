package art.arcane.iris.world.history;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;

final class GenerationKernelCatalog {
    private static final int MAXIMUM_BYTES = 16 * 1024;

    private GenerationKernelCatalog() {
    }

    static GenerationKernelRegistry load() {
        try (InputStream input = GenerationKernelCatalog.class.getResourceAsStream(
                "/META-INF/iris/generation-kernels/catalog.tsv")) {
            if (input == null) {
                throw new IOException("Missing Iris generation kernel catalog.");
            }
            Catalog catalog = parse(input.readNBytes(MAXIMUM_BYTES + 1));
            List<GenerationKernelRegistry.Kernel> kernels = new ArrayList<>();
            for (int abi : catalog.abis()) {
                GenerationBuildRevision.Descriptor revision = GenerationBuildRevision.load(abi);
                Class<? extends GenerationKernelRegistry.RuntimeFactory> factoryType = Class.forName(
                        revision.factoryClass(), true, GenerationKernelCatalog.class.getClassLoader())
                        .asSubclass(GenerationKernelRegistry.RuntimeFactory.class);
                Map<GenerationKernelRegistry.AlgorithmVersion, GenerationKernelRegistry.RuntimeFactory> factories = new LinkedHashMap<>();
                for (GenerationKernelRegistry.AlgorithmVersion algorithm : revision.algorithms()) {
                    factories.put(algorithm, factoryType.getDeclaredConstructor().newInstance());
                }
                kernels.add(new GenerationKernelRegistry.Kernel(abi, revision.fingerprint(), factories));
            }
            return new GenerationKernelRegistry(catalog.current(), kernels);
        } catch (IOException | ReflectiveOperationException | IllegalArgumentException failure) {
            throw new IllegalStateException("Cannot load the Iris generation kernel catalog.", failure);
        }
    }

    static Catalog parse(byte[] bytes) throws IOException {
        if (bytes.length == 0 || bytes.length > MAXIMUM_BYTES) {
            throw new IOException("Invalid generation kernel catalog size.");
        }
        String text = new String(bytes, StandardCharsets.UTF_8);
        String[] lines = text.split("\n", -1);
        if (!text.endsWith("\n") || text.indexOf('\r') >= 0 || lines.length < 4 || lines.length > 128
                || !lines[0].equals("iris-generation-kernel-catalog-v1")) {
            throw new IOException("Invalid generation kernel catalog format.");
        }
        try {
            String[] current = lines[1].split("\t", -1);
            if (current.length != 4 || !current[0].equals("current")) {
                throw new IllegalArgumentException("Missing current generation version.");
            }
            GenerationKernelRegistry.Version version = new GenerationKernelRegistry.Version(
                    positive(current[1]), positive(current[2]), positive(current[3]));
            List<Integer> abis = new ArrayList<>();
            int previous = 0;
            for (int index = 2; index < lines.length - 1; index++) {
                String[] kernel = lines[index].split("\t", -1);
                if (kernel.length != 2 || !kernel[0].equals("kernel")) {
                    throw new IllegalArgumentException("Invalid generation kernel registration.");
                }
                int abi = positive(kernel[1]);
                if (abi <= previous) {
                    throw new IllegalArgumentException("Generation kernel registrations must be unique and sorted.");
                }
                previous = abi;
                abis.add(abi);
            }
            if (!abis.contains(version.generatorAbi())) {
                throw new IllegalArgumentException("Current generation ABI is not registered.");
            }
            return new Catalog(version, List.copyOf(abis));
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

    record Catalog(GenerationKernelRegistry.Version current, List<Integer> abis) {
    }
}
