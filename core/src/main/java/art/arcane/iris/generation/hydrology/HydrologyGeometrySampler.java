package art.arcane.iris.generation.hydrology;

import java.util.Objects;

@FunctionalInterface
public interface HydrologyGeometrySampler {
    int sample(Request request);

    default int sample(
            Field field,
            String profileKey,
            int x,
            int z,
            int minimum,
            int maximum
    ) {
        return sample(field, profileKey, x, z, 0L, minimum, maximum);
    }

    default int sample(
            Field field,
            String profileKey,
            int x,
            int z,
            long stableId,
            int minimum,
            int maximum
    ) {
        Request request = new Request(field, profileKey, x, z, stableId, minimum, maximum);
        int value = sample(request);
        if (value < minimum || value > maximum) {
            throw new IllegalStateException(
                    "Hydrology geometry sampler returned " + value + " outside " + minimum + ".." + maximum
                            + " for " + field + " at " + x + "," + z + "."
            );
        }
        return value;
    }

    static HydrologyGeometrySampler deterministic(HydrologyTerrainSampler terrainSampler) {
        Objects.requireNonNull(terrainSampler, "terrainSampler");
        return request -> {
            if (request.field() == Field.UNDERGROUND_FLUID_LEVEL) {
                HydrologyTerrainSample terrain = Objects.requireNonNull(
                        terrainSampler.sample(request.x(), request.z()),
                        "Hydrology terrain sampler returned null at " + request.x() + "," + request.z()
                );
                return Math.max(request.minimum(), Math.min(request.maximum(), terrain.caveFluidY()));
            }
            if (request.field() == Field.SURFACE_WIDTH
                    || request.field() == Field.SURFACE_DEPTH
                    || request.field() == Field.UNDERGROUND_WIDTH
                    || request.field() == Field.UNDERGROUND_DEPTH) {
                return request.minimum();
            }
            long stable = switch (request.field()) {
                case SURFACE_BLEND_WIDTH -> HydrologyHash.mix(
                        request.stableId(),
                        request.x(),
                        request.z(),
                        0x424c454e44L
                );
                case UNDERGROUND_HEADROOM -> HydrologyHash.mix(
                        request.stableId(),
                        request.x(),
                        request.z(),
                        0x48454144L
                );
                case DEEP_FLUID_HEIGHT -> HydrologyHash.mix(request.stableId(), 0x484549474854L);
                default -> throw new IllegalStateException("Missing deterministic geometry rule for " + request.field() + ".");
            };
            return HydrologyHash.between(stable, request.minimum(), request.maximum());
        };
    }

    enum Field {
        SURFACE_WIDTH,
        SURFACE_DEPTH,
        SURFACE_BLEND_WIDTH,
        UNDERGROUND_FLUID_LEVEL,
        UNDERGROUND_WIDTH,
        UNDERGROUND_DEPTH,
        UNDERGROUND_HEADROOM,
        DEEP_FLUID_HEIGHT
    }

    record Request(
            Field field,
            String profileKey,
            int x,
            int z,
            long stableId,
            int minimum,
            int maximum
    ) {
        public Request {
            field = Objects.requireNonNull(field, "field");
            profileKey = profileKey == null ? "" : profileKey;
            if (minimum > maximum) {
                throw new IllegalArgumentException("Hydrology geometry sample bounds are reversed.");
            }
        }
    }
}
