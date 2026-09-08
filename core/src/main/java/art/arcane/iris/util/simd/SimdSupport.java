package art.arcane.iris.util.simd;

import art.arcane.iris.spi.IrisLogging;
import art.arcane.iris.core.IrisSettings;

import java.util.concurrent.atomic.AtomicReference;

public final class SimdSupport {
    private static final String VECTOR_MODULE = "jdk.incubator.vector";
    private static final boolean MODULE_PRESENT = ModuleLayer.boot().findModule(VECTOR_MODULE).isPresent();
    private static final AtomicReference<Throwable> KERNEL_FAILURE = new AtomicReference<>();
    private static final AtomicReference<Throwable> NOISE_KERNEL_FAILURE = new AtomicReference<>();
    private static final SimdKernels KERNELS = selectKernels();
    private static final NoiseKernels2D NOISE_KERNELS_2D = selectNoiseKernels2D();

    private SimdSupport() {
    }

    public static SimdKernels kernels() {
        return KERNELS;
    }

    public static NoiseKernels2D noiseKernels2D() {
        return NOISE_KERNELS_2D;
    }

    public static boolean isVectorModulePresent() {
        return MODULE_PRESENT;
    }

    public static boolean isVectorized() {
        return !(KERNELS instanceof ScalarSimdKernels);
    }

    public static Throwable kernelInitializationFailure() {
        return KERNEL_FAILURE.get();
    }

    public static Throwable noiseKernelInitializationFailure() {
        return NOISE_KERNEL_FAILURE.get();
    }

    public static String kernelStatus() {
        return kernelStatus(isVectorized(), KERNELS.describe(), MODULE_PRESENT, KERNEL_FAILURE.get(), simdEnabledInSettings());
    }

    public static String noiseKernelStatus() {
        return noiseKernelStatus(!(NOISE_KERNELS_2D instanceof ScalarNoiseKernels2D), NOISE_KERNELS_2D.describe(),
                MODULE_PRESENT, NOISE_KERNEL_FAILURE.get(), simdEnabledInSettings());
    }

    static String kernelStatus(boolean vectorized, String description, boolean modulePresent, Throwable failure, boolean settingEnabled) {
        if (vectorized) {
            return "vector kernels enabled (" + description + ")";
        }

        if (!modulePresent) {
            return "scalar kernels active; add --add-modules " + VECTOR_MODULE + " to JVM flags to enable vectorized generation kernels";
        }

        if (failure != null) {
            return "scalar kernels active; vector kernel initialization failed: " + describe(failure);
        }

        if (!settingEnabled) {
            return "vector kernels disabled (performance.simdKernels=false)";
        }

        return "scalar kernels active; the Vector API reported no usable vector shape on this CPU";
    }

    static String noiseKernelStatus(boolean vectorized, String description, boolean modulePresent, Throwable failure, boolean settingEnabled) {
        if (vectorized) {
            return "2D noise vector kernels enabled (" + description + ")";
        }

        if (!modulePresent) {
            return "2D noise scalar kernels active; the " + VECTOR_MODULE + " module is not on this JVM";
        }

        if (failure != null) {
            return "2D noise scalar kernels active; vector kernel initialization failed: " + describe(failure);
        }

        if (!settingEnabled) {
            return "2D noise vector kernels disabled (performance.simdKernels=false)";
        }

        return "2D noise scalar kernels active; this CPU has fewer than the 4 double lanes the vector kernel needs to pay for itself";
    }

    public static void install() {
        IrisLogging.info("SIMD: " + kernelStatus());
        IrisLogging.info("SIMD: " + noiseKernelStatus());

        Throwable failure = KERNEL_FAILURE.get();
        if (failure != null) {
            IrisLogging.reportError("Iris could not initialize its Vector API kernels; generation runs on the scalar kernels instead.", failure);
        }

        Throwable noiseFailure = NOISE_KERNEL_FAILURE.get();
        if (noiseFailure != null) {
            IrisLogging.reportError("Iris could not initialize its Vector API 2D noise kernels; noise runs on the scalar kernels instead.", noiseFailure);
        }
    }

    public static SimdKernels createVectorKernels() {
        if (!MODULE_PRESENT) {
            return null;
        }

        try {
            return (SimdKernels) Class.forName("art.arcane.iris.util.simd.VectorSimdKernels").getDeclaredConstructor().newInstance();
        } catch (Throwable e) {
            KERNEL_FAILURE.set(e);
            return null;
        }
    }

    public static NoiseKernels2D createVectorNoiseKernels2D() {
        if (!MODULE_PRESENT) {
            return null;
        }

        try {
            Class<?> cls = Class.forName("art.arcane.iris.util.simd.VectorNoiseKernels2D");
            boolean profitable = (boolean) cls.getMethod("profitable").invoke(null);
            if (!profitable) {
                return null;
            }
            return (NoiseKernels2D) cls.getDeclaredConstructor().newInstance();
        } catch (Throwable e) {
            NOISE_KERNEL_FAILURE.set(e);
            return null;
        }
    }

    private static SimdKernels selectKernels() {
        if (!simdEnabledInSettings()) {
            return new ScalarSimdKernels();
        }

        SimdKernels vector = createVectorKernels();
        return vector == null ? new ScalarSimdKernels() : vector;
    }

    private static NoiseKernels2D selectNoiseKernels2D() {
        if (!simdEnabledInSettings()) {
            return new ScalarNoiseKernels2D();
        }

        NoiseKernels2D vector = createVectorNoiseKernels2D();
        return vector == null ? new ScalarNoiseKernels2D() : vector;
    }

    private static String describe(Throwable failure) {
        Throwable root = failure;
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }

        String message = root.getMessage();
        return message == null ? root.getClass().getName() : root.getClass().getName() + ": " + message;
    }

    private static boolean simdEnabledInSettings() {
        try {
            return IrisSettings.get().getPerformance().isSimdKernels();
        } catch (Throwable e) {
            return true;
        }
    }
}
