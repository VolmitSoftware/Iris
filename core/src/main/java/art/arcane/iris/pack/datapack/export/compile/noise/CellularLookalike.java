package art.arcane.iris.pack.datapack.export.compile.noise;

import art.arcane.iris.generation.noise.NoiseStyle;
import art.arcane.iris.pack.datapack.DataVersion;
import art.arcane.iris.pack.datapack.export.ir.Field;
import art.arcane.iris.pack.datapack.export.VanillaResourceIdentifier;

public final class CellularLookalike {
    private static final String NAMESPACE_PREFIX = "cell/";

    private CellularLookalike() {
    }

    public static Field smoothStyle(String instanceId, NoiseStyle style, double zoom, Field warpX, Field warpZ, NoiseRegistry registry, DataVersion target) {
        String prefix = prefix(instanceId);
        if (!(zoom > 0.0D) || !Double.isFinite(zoom)) {
            throw new IllegalArgumentException("Smooth look-alike zoom must be positive and finite, got " + zoom);
        }
        IrisSampleFit.Profile iris = IrisSampleFit.style(style);
        IrisSampleFit.Profile vanilla = IrisSampleFit.vanilla(target);
        double xzScale = LeafNoise.xzScale(vanilla, iris.correlationLength() * zoom);
        Field noise = LeafNoise.sample(LeafNoise.register(registry), xzScale, prefix + "smooth", warpX, warpZ);
        return new Field.Shared(prefix + "smooth", LeafNoise.toMarginal(noise, vanilla, iris));
    }

    private static String prefix(String instanceId) {
        return NAMESPACE_PREFIX + VanillaResourceIdentifier.requirePath(instanceId, "cellular look-alike instance id") + "/";
    }
}
