package art.arcane.iris.util.project.noise;


import art.arcane.volmlib.util.math.RNG;
import org.jetbrains.annotations.NotNull;


public class OffsetNoiseGenerator implements NoiseGenerator, OctaveNoise {
    private static final int GENERIC = 0;
    private static final int SIMPLEX = 1;
    private static final int PERLIN = 2;
    private static final int FRACTAL_FBM_SIMPLEX = 3;
    private static final int VASCULAR = 4;
    private final NoiseGenerator base;
    private final SimplexNoise simplexBase;
    private final PerlinNoise perlinBase;
    private final FractalFBMSimplexNoise fractalFbmSimplexBase;
    private final VascularNoise vascularBase;
    private final int dispatchKind;
    private final double ox;
    private final double oz;

    public OffsetNoiseGenerator(NoiseGenerator base, long seed) {
        this.base = base;
        this.simplexBase = base instanceof SimplexNoise simplex ? simplex : null;
        this.perlinBase = base instanceof PerlinNoise perlin ? perlin : null;
        this.fractalFbmSimplexBase = base instanceof FractalFBMSimplexNoise fractal ? fractal : null;
        this.vascularBase = base instanceof VascularNoise vascular ? vascular : null;
        this.dispatchKind = simplexBase != null ? SIMPLEX
                : perlinBase != null ? PERLIN
                : fractalFbmSimplexBase != null ? FRACTAL_FBM_SIMPLEX
                : vascularBase != null ? VASCULAR
                : GENERIC;
        RNG rng = new RNG(seed);
        ox = rng.nextInt(Short.MIN_VALUE, Short.MAX_VALUE);
        oz = rng.nextInt(Short.MIN_VALUE, Short.MAX_VALUE);
    }

    @Override
    public void setOctaves(int octaves) {
        if (base instanceof OctaveNoise octaveBase) {
            octaveBase.setOctaves(octaves);
        }
    }

    @Override
    public double noise(double x) {
        return switch (dispatchKind) {
            case SIMPLEX -> simplexBase.noise(x + ox);
            case PERLIN -> perlinBase.noise(x + ox);
            case FRACTAL_FBM_SIMPLEX -> fractalFbmSimplexBase.noise(x + ox);
            case VASCULAR -> vascularBase.noise(x + ox);
            default -> base.noise(x + ox);
        };
    }

    @Override
    public double noiseSigned(double x) {
        return switch (dispatchKind) {
            case SIMPLEX -> simplexBase.noiseSigned(x + ox);
            case PERLIN -> perlinBase.noiseSigned(x + ox);
            case FRACTAL_FBM_SIMPLEX -> fractalFbmSimplexBase.noiseSigned(x + ox);
            case VASCULAR -> vascularBase.noiseSigned(x + ox);
            default -> base.noiseSigned(x + ox);
        };
    }

    @Override
    public double noise(double x, double z) {
        return switch (dispatchKind) {
            case SIMPLEX -> simplexBase.noise(x + ox, z + oz);
            case PERLIN -> perlinBase.noise(x + ox, z + oz);
            case FRACTAL_FBM_SIMPLEX -> fractalFbmSimplexBase.noise(x + ox, z + oz);
            case VASCULAR -> vascularBase.noise(x + ox, z + oz);
            default -> base.noise(x + ox, z + oz);
        };
    }

    @Override
    public double noiseSigned(double x, double z) {
        return switch (dispatchKind) {
            case SIMPLEX -> simplexBase.noiseSigned(x + ox, z + oz);
            case PERLIN -> perlinBase.noiseSigned(x + ox, z + oz);
            case FRACTAL_FBM_SIMPLEX -> fractalFbmSimplexBase.noiseSigned(x + ox, z + oz);
            case VASCULAR -> vascularBase.noiseSigned(x + ox, z + oz);
            default -> base.noiseSigned(x + ox, z + oz);
        };
    }

    @Override
    public double noise(double x, double y, double z) {
        return switch (dispatchKind) {
            case SIMPLEX -> simplexBase.noise(x + ox, y, z + oz);
            case PERLIN -> perlinBase.noise(x + ox, y, z + oz);
            case FRACTAL_FBM_SIMPLEX -> fractalFbmSimplexBase.noise(x + ox, y, z + oz);
            case VASCULAR -> vascularBase.noise(x + ox, y, z + oz);
            default -> base.noise(x + ox, y, z + oz);
        };
    }

    @Override
    public double noiseSigned(double x, double y, double z) {
        return switch (dispatchKind) {
            case SIMPLEX -> simplexBase.noiseSigned(x + ox, y, z + oz);
            case PERLIN -> perlinBase.noiseSigned(x + ox, y, z + oz);
            case FRACTAL_FBM_SIMPLEX -> fractalFbmSimplexBase.noiseSigned(x + ox, y, z + oz);
            case VASCULAR -> vascularBase.noiseSigned(x + ox, y, z + oz);
            default -> base.noiseSigned(x + ox, y, z + oz);
        };
    }

    @Override
    public boolean isNoScale() {
        return base.isNoScale();
    }

    @Override
    public boolean isStatic() {
        return base.isStatic();
    }

    @NotNull
    public NoiseGenerator getBase() {
        return base;
    }
}
