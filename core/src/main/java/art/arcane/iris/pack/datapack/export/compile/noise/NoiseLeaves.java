package art.arcane.iris.pack.datapack.export.compile.noise;

import art.arcane.volmlib.util.noise.InterpolatedNoise;
import art.arcane.volmlib.util.noise.NoiseGenerator;
import art.arcane.volmlib.util.noise.NoiseType;
import art.arcane.volmlib.util.noise.OffsetNoiseGenerator;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

final class NoiseLeaves {
    private static final int MAXIMUM_OCTAVES = 16;
    private static final Set<NoiseType> CELLULAR_BASES = EnumSet.of(
            NoiseType.WHITE, NoiseType.CELLULAR, NoiseType.GLOB, NoiseType.CELLULAR_HEIGHT, NoiseType.VASCULAR,
            NoiseType.HEXAGON, NoiseType.HEX_JAMES, NoiseType.HEX_SIMPLEX, NoiseType.HEX_RANDOM_SIZE);
    private static final Set<NoiseType> OCTAVE_SUMS = EnumSet.of(NoiseType.SIMPLEX, NoiseType.PERLIN, NoiseType.FRACTAL_FBM_SIMPLEX);
    private static final Set<NoiseType> OCTAVE_TYPES = EnumSet.of(
            NoiseType.SIMPLEX, NoiseType.PERLIN, NoiseType.FRACTAL_FBM_SIMPLEX, NoiseType.FRACTAL_BILLOW_SIMPLEX,
            NoiseType.FRACTAL_BILLOW_PERLIN, NoiseType.FRACTAL_RIGID_MULTI_SIMPLEX, NoiseType.FRACTAL_CUBIC);

    private NoiseLeaves() {
    }

    static NoiseType typeOf(NoiseGenerator generator) {
        NoiseGenerator base = unwrap(generator);
        if (base instanceof InterpolatedNoise interpolated) {
            String name = typeOf(interpolated.getGenerator()).name() + "_" + interpolated.getMethod().name();
            return NoiseType.valueOf(name);
        }
        NoiseType type = Classes.TYPES.get(base.getClass());
        if (type == null) {
            throw new IllegalArgumentException("Noise generator " + base.getClass().getName() + " has no exportable noise type");
        }
        return type;
    }

    static boolean isCellular(NoiseType type) {
        return CELLULAR_BASES.contains(baseOf(type));
    }

    static boolean isOctaveSum(NoiseType type) {
        return OCTAVE_SUMS.contains(type);
    }

    static Fold fold(NoiseType type) {
        return switch (type) {
            case FRACTAL_BILLOW_SIMPLEX -> new Fold(FoldKind.BILLOW, NoiseType.SIMPLEX);
            case FRACTAL_BILLOW_PERLIN -> new Fold(FoldKind.BILLOW, NoiseType.PERLIN);
            case FRACTAL_CUBIC -> new Fold(FoldKind.BILLOW, NoiseType.CUBIC);
            case FRACTAL_RIGID_MULTI_SIMPLEX -> new Fold(FoldKind.RIGID, NoiseType.SIMPLEX);
            default -> new Fold(FoldKind.NONE, type);
        };
    }

    static int effectiveOctaves(NoiseType type, int octaves) {
        return OCTAVE_TYPES.contains(type) ? Math.clamp(octaves, 1, MAXIMUM_OCTAVES) : 1;
    }

    static NoiseGenerator unwrap(NoiseGenerator generator) {
        NoiseGenerator current = generator;
        while (current instanceof OffsetNoiseGenerator offset) {
            current = offset.getBase();
        }
        return current;
    }

    private static NoiseType baseOf(NoiseType type) {
        for (NoiseType candidate : CELLULAR_BASES) {
            if (type == candidate || type.name().startsWith(candidate.name() + "_")) {
                return candidate;
            }
        }
        return type;
    }

    enum FoldKind {
        NONE,
        BILLOW,
        RIGID
    }

    record Fold(FoldKind kind, NoiseType base) {
    }

    private static final class Classes {
        private static final Map<Class<?>, NoiseType> TYPES = scan();

        private static Map<Class<?>, NoiseType> scan() {
            Map<Class<?>, NoiseType> types = new HashMap<>();
            for (NoiseType type : NoiseType.values()) {
                NoiseGenerator base = unwrap(type.create(0L));
                if (!(base instanceof InterpolatedNoise)) {
                    types.putIfAbsent(base.getClass(), type);
                }
            }
            return Map.copyOf(types);
        }
    }
}
