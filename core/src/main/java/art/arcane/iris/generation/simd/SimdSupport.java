package art.arcane.iris.generation.simd;

import art.arcane.iris.spi.IrisLogging;
import art.arcane.iris.configuration.IrisSettings;

import java.util.concurrent.atomic.AtomicReference;

public final class SimdSupport {
    private static final String VECTOR_MODULE = "jdk.incubator.vector";
    private static final boolean MODULE_PRESENT = ModuleLayer.boot().findModule(VECTOR_MODULE).isPresent();
    private static final AtomicReference<Throwable> KERNEL_FAILURE = new AtomicReference<>();
    private static final SimdKernels KERNELS = selectKernels();

    private SimdSupport() {
    }

    public static SimdKernels kernels() {
        return KERNELS;
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

    public static String kernelStatus() {
        return kernelStatus(isVectorized(), KERNELS.describe(), MODULE_PRESENT, KERNEL_FAILURE.get(), simdEnabledInSettings());
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

    public static void install() {
        IrisLogging.info("SIMD: " + kernelStatus());

        Throwable failure = KERNEL_FAILURE.get();
        if (failure != null) {
            IrisLogging.reportError("Iris could not initialize its Vector API kernels; generation runs on the scalar kernels instead.", failure);
        }
    }

    public static SimdKernels createVectorKernels() {
        if (!MODULE_PRESENT) {
            return null;
        }

        try {
            return (SimdKernels) Class.forName("art.arcane.iris.generation.simd.VectorSimdKernels").getDeclaredConstructor().newInstance();
        } catch (Throwable e) {
            KERNEL_FAILURE.set(e);
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
