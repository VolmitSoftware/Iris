package art.arcane.iris.util.simd;

import art.arcane.iris.spi.IrisLogging;
import art.arcane.iris.core.IrisSettings;

public final class SimdSupport {
    private static final String VECTOR_MODULE = "jdk.incubator.vector";
    private static final boolean MODULE_PRESENT = ModuleLayer.boot().findModule(VECTOR_MODULE).isPresent();
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

    public static void install() {
        if (isVectorized()) {
            IrisLogging.info("SIMD: vector kernels enabled (" + KERNELS.describe() + ")");
        } else if (!MODULE_PRESENT) {
            IrisLogging.info("SIMD: scalar kernels active; add --add-modules " + VECTOR_MODULE + " to JVM flags to enable vectorized generation kernels");
        } else {
            IrisLogging.info("SIMD: vector kernels disabled (performance.simdKernels=false)");
        }
    }

    public static SimdKernels createVectorKernels() {
        if (!MODULE_PRESENT) {
            return null;
        }

        try {
            return (SimdKernels) Class.forName("art.arcane.iris.util.simd.VectorSimdKernels").getDeclaredConstructor().newInstance();
        } catch (Throwable e) {
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

    private static boolean simdEnabledInSettings() {
        try {
            return IrisSettings.get().getPerformance().isSimdKernels();
        } catch (Throwable e) {
            return true;
        }
    }
}
