package art.arcane.iris.generation.hydrology.policy;

import art.arcane.iris.generation.hydrology.IrisRiverPlacementMode;
import art.arcane.iris.generation.hydrology.IrisRiverRoutingMode;

import java.util.List;
import java.util.Objects;

public record EffectiveRiverPolicy(
        IrisRiverPlacementMode placement,
        IrisRiverRoutingMode routing,
        boolean outletAdmission,
        List<String> profiles,
        List<String> surfaceBiomes,
        List<String> mouthBiomes,
        List<String> shoreBiomes,
        List<String> bankBiomes,
        List<String> floodedCaveBiomes,
        List<String> surfacePools,
        double widthMultiplier,
        double depthMultiplier,
        double incisionMultiplier,
        double routingMultiplier,
        double bankMultiplier,
        Double shoreBiomeWidth,
        RiverConfinement confinement,
        Double shoreWidth,
        Boolean erosion,
        SurfaceRiverPolicy surfacePolicy
) {
    public EffectiveRiverPolicy {
        placement = Objects.requireNonNull(placement);
        routing = Objects.requireNonNull(routing);
        confinement = Objects.requireNonNull(confinement);
        surfacePolicy = Objects.requireNonNull(surfacePolicy);
        if (shoreBiomeWidth != null && (!Double.isFinite(shoreBiomeWidth) || shoreBiomeWidth < 0D)) {
            throw new IllegalArgumentException("shoreBiomeWidth must be finite and non-negative.");
        }
        if (shoreWidth != null && (!Double.isFinite(shoreWidth) || shoreWidth < 0D)) {
            throw new IllegalArgumentException("shoreWidth must be finite and non-negative.");
        }
        profiles = List.copyOf(profiles);
        surfaceBiomes = List.copyOf(surfaceBiomes);
        mouthBiomes = List.copyOf(mouthBiomes);
        shoreBiomes = List.copyOf(shoreBiomes);
        bankBiomes = List.copyOf(bankBiomes);
        floodedCaveBiomes = List.copyOf(floodedCaveBiomes);
        surfacePools = List.copyOf(surfacePools);
    }

    public boolean allowsSources() {
        return switch (placement) {
            case DISABLED, TRANSIT_ONLY -> false;
            case NATURAL, PREFERRED_HEADWATER, REQUIRED_HEADWATER -> true;
        };
    }

    public boolean allowsTransit() {
        return placement != IrisRiverPlacementMode.DISABLED;
    }

    public boolean prefersHeadwaters() {
        return placement == IrisRiverPlacementMode.PREFERRED_HEADWATER
                || placement == IrisRiverPlacementMode.REQUIRED_HEADWATER;
    }

    public boolean requiresHeadwaters() {
        return placement == IrisRiverPlacementMode.REQUIRED_HEADWATER;
    }

    public boolean allowsRouting() {
        return routing != IrisRiverRoutingMode.BLOCK;
    }
}
