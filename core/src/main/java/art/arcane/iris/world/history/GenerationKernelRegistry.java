package art.arcane.iris.world.history;

import art.arcane.iris.generation.runtime.IrisComplex;
import art.arcane.iris.generation.runtime.DimensionStackContext;
import art.arcane.iris.generation.runtime.IrisEngine;
import art.arcane.iris.generation.runtime.UpperDimensionContext;
import art.arcane.iris.generation.runtime.EngineMode;
import art.arcane.iris.generation.runtime.SeedManager;
import art.arcane.iris.generation.mantle.EngineMantle;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.regex.Pattern;

public final class GenerationKernelRegistry {
    private static final Pattern SHA_256_PATTERN = Pattern.compile("[0-9a-f]{64}");
    private static final GenerationKernelRegistry STANDARD = GenerationKernelCatalog.load();

    private final Version current;
    private final Map<Integer, Kernel> kernels;

    public GenerationKernelRegistry(Version current, Collection<Kernel> kernels) {
        this.current = Objects.requireNonNull(current, "current");
        Collection<Kernel> requiredKernels = Objects.requireNonNull(kernels, "kernels");
        TreeMap<Integer, Kernel> indexed = new TreeMap<>();
        for (Kernel kernel : requiredKernels) {
            Kernel requiredKernel = Objects.requireNonNull(kernel, "kernel");
            if (indexed.putIfAbsent(requiredKernel.generatorAbi(), requiredKernel) != null) {
                throw new IllegalArgumentException("Duplicate generation ABI "
                        + requiredKernel.generatorAbi() + ".");
            }
        }
        this.kernels = Map.copyOf(indexed);
        if (!supports(current.generatorAbi(), current.rngVersion(), current.seedDerivationVersion())) {
            throw new IllegalArgumentException("Current generation version is not registered.");
        }
    }

    public static GenerationKernelRegistry standard() {
        return STANDARD;
    }

    public Version current() {
        return current;
    }

    public boolean supports(int generatorAbi, int rngVersion, int seedDerivationVersion) {
        Kernel kernel = kernels.get(generatorAbi);
        return kernel != null && kernel.runtimeFactories().containsKey(
                new AlgorithmVersion(rngVersion, seedDerivationVersion)
        );
    }

    public Kernel requireSupported(
            int generatorAbi,
            int rngVersion,
            int seedDerivationVersion
    ) throws IOException {
        Kernel kernel = kernels.get(generatorAbi);
        if (kernel == null) {
            throw new IOException("Unsupported Iris generation ABI " + generatorAbi + ".");
        }
        AlgorithmVersion version = new AlgorithmVersion(rngVersion, seedDerivationVersion);
        if (!kernel.runtimeFactories().containsKey(version)) {
            throw new IOException("Unsupported Iris RNG/seed derivation version "
                    + rngVersion + "/" + seedDerivationVersion
                    + " for generation ABI " + generatorAbi + ".");
        }
        return kernel;
    }

    public Kernel requireSupported(Version version) throws IOException {
        Version requiredVersion = Objects.requireNonNull(version, "version");
        return requireSupported(
                requiredVersion.generatorAbi(),
                requiredVersion.rngVersion(),
                requiredVersion.seedDerivationVersion()
        );
    }

    public Kernel requireSupported(Version version, String implementationFingerprint) throws IOException {
        Version requiredVersion = Objects.requireNonNull(version, "version");
        Kernel kernel = requireSupported(requiredVersion);
        String requiredFingerprint = requireFingerprint(
                implementationFingerprint,
                "Generation kernel implementation fingerprint"
        );
        if (!kernel.implementationFingerprint().equals(requiredFingerprint)) {
            throw new IOException("Iris generation kernel " + requiredVersion
                    + " has implementation fingerprint " + kernel.implementationFingerprint()
                    + " instead of the epoch's required fingerprint " + requiredFingerprint + ".");
        }
        return kernel;
    }

    public RuntimeKernel select(Version version) throws IOException {
        Version requiredVersion = Objects.requireNonNull(version, "version");
        Kernel kernel = requireSupported(requiredVersion);
        AlgorithmVersion algorithmVersion = new AlgorithmVersion(
                requiredVersion.rngVersion(),
                requiredVersion.seedDerivationVersion()
        );
        RuntimeFactory factory = kernel.runtimeFactories().get(algorithmVersion);
        if (factory == null) {
            throw new IOException("No executable Iris generation kernel is registered for "
                    + requiredVersion + ".");
        }
        return new RuntimeKernel(requiredVersion, kernel.implementationFingerprint(), factory);
    }

    public record Version(int generatorAbi, int rngVersion, int seedDerivationVersion) {
        public Version {
            if (generatorAbi < 1) {
                throw new IllegalArgumentException("Generator ABI must be positive.");
            }
            if (rngVersion < 1) {
                throw new IllegalArgumentException("RNG version must be positive.");
            }
            if (seedDerivationVersion < 1) {
                throw new IllegalArgumentException("Seed derivation version must be positive.");
            }
        }
    }

    private static String requireFingerprint(String value, String label) {
        String requiredValue = Objects.requireNonNull(value, label);
        if (!SHA_256_PATTERN.matcher(requiredValue).matches()) {
            throw new IllegalArgumentException(label + " must be a lowercase SHA-256 value.");
        }
        return requiredValue;
    }

    public record Kernel(
            int generatorAbi,
            String implementationFingerprint,
            Map<AlgorithmVersion, RuntimeFactory> runtimeFactories
    ) {
        public Kernel {
            if (generatorAbi < 1) {
                throw new IllegalArgumentException("Generator ABI must be positive.");
            }
            implementationFingerprint = requireFingerprint(
                    implementationFingerprint,
                    "Generation kernel implementation fingerprint"
            );
            Map<AlgorithmVersion, RuntimeFactory> requiredFactories = Map.copyOf(Objects.requireNonNull(
                    runtimeFactories,
                    "runtimeFactories"
            ));
            if (requiredFactories.isEmpty()) {
                throw new IllegalArgumentException("A generation kernel must provide an executable runtime factory.");
            }
            for (Map.Entry<AlgorithmVersion, RuntimeFactory> entry : requiredFactories.entrySet()) {
                Objects.requireNonNull(entry.getKey(), "algorithmVersion");
                Objects.requireNonNull(entry.getValue(), "runtimeFactory");
            }
            runtimeFactories = requiredFactories;
        }
    }

    @FunctionalInterface
    public interface RuntimeFactory {
        IrisComplex create(IrisEngine engine, TransitionGenerationPlan transitionPlan, boolean detached);

        default SeedManager createSeedManager(long worldSeed) {
            throw new UnsupportedOperationException("Generation kernel does not own seed derivation.");
        }

        default EngineMantle createMantle(IrisEngine engine, Path storageDirectory) {
            throw new UnsupportedOperationException("Generation kernel does not own mantle construction.");
        }

        default UpperDimensionContext createUpperContext(IrisEngine engine) {
            throw new UnsupportedOperationException("Generation kernel does not own upper-dimension construction.");
        }

        default DimensionStackContext createDimensionStackContext(IrisEngine engine) {
            throw new UnsupportedOperationException("Generation kernel does not own dimension-stack construction.");
        }

        default EngineMode createMode(IrisEngine engine) {
            throw new UnsupportedOperationException("Generation kernel does not own generation-mode construction.");
        }

        default void registerStaticObjects(IrisEngine engine, EngineMode mode) {
            throw new UnsupportedOperationException("Generation kernel does not own static-object stages.");
        }

        default void registerDimensionStack(IrisEngine engine, EngineMode mode, DimensionStackContext context) {
            throw new UnsupportedOperationException("Generation kernel does not own dimension-stack stages.");
        }
    }

    public record RuntimeKernel(
            Version version,
            String implementationFingerprint,
            RuntimeFactory factory
    ) {
        public RuntimeKernel {
            Objects.requireNonNull(version, "version");
            implementationFingerprint = requireFingerprint(
                    implementationFingerprint,
                    "Generation kernel implementation fingerprint"
            );
            Objects.requireNonNull(factory, "factory");
        }

        public IrisComplex createComplex(IrisEngine engine, TransitionGenerationPlan transitionPlan, boolean detached) {
            return Objects.requireNonNull(
                    factory.create(Objects.requireNonNull(engine, "engine"), transitionPlan, detached),
                    "generation kernel complex"
            );
        }

        public SeedManager createSeedManager(long worldSeed) {
            return Objects.requireNonNull(factory.createSeedManager(worldSeed), "generation kernel seed manager");
        }

        public EngineMantle createMantle(IrisEngine engine, Path storageDirectory) {
            return Objects.requireNonNull(
                    factory.createMantle(
                            Objects.requireNonNull(engine, "engine"),
                            Objects.requireNonNull(storageDirectory, "storageDirectory")
                    ),
                    "generation kernel mantle"
            );
        }

        public UpperDimensionContext createUpperContext(IrisEngine engine) {
            return factory.createUpperContext(Objects.requireNonNull(engine, "engine"));
        }

        public DimensionStackContext createDimensionStackContext(IrisEngine engine) {
            return factory.createDimensionStackContext(Objects.requireNonNull(engine, "engine"));
        }

        public EngineMode createMode(IrisEngine engine) {
            return Objects.requireNonNull(
                    factory.createMode(Objects.requireNonNull(engine, "engine")),
                    "generation kernel mode"
            );
        }

        public void registerStaticObjects(IrisEngine engine, EngineMode mode) {
            factory.registerStaticObjects(
                    Objects.requireNonNull(engine, "engine"),
                    Objects.requireNonNull(mode, "mode")
            );
        }

        public void registerDimensionStack(
                IrisEngine engine,
                EngineMode mode,
                DimensionStackContext context
        ) {
            factory.registerDimensionStack(
                    Objects.requireNonNull(engine, "engine"),
                    Objects.requireNonNull(mode, "mode"),
                    context
            );
        }
    }

    public record AlgorithmVersion(int rngVersion, int seedDerivationVersion) {
        public AlgorithmVersion {
            if (rngVersion < 1) {
                throw new IllegalArgumentException("RNG version must be positive.");
            }
            if (seedDerivationVersion < 1) {
                throw new IllegalArgumentException("Seed derivation version must be positive.");
            }
        }
    }
}
