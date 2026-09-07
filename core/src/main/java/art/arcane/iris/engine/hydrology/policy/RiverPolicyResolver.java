package art.arcane.iris.engine.hydrology.policy;

import art.arcane.iris.core.loader.IrisData;
import art.arcane.iris.core.loader.IrisRegistrant;
import art.arcane.iris.engine.object.IrisBiome;
import art.arcane.iris.engine.object.IrisDimension;
import art.arcane.iris.engine.object.IrisRegion;
import art.arcane.iris.engine.object.IrisRiverPlacementMode;
import art.arcane.iris.engine.object.IrisRiverPolicy;
import art.arcane.iris.engine.object.IrisRiverRoutingMode;

import java.util.List;

public final class RiverPolicyResolver {
    private RiverPolicyResolver() {
    }

    public static EffectiveRiverPolicy resolve(IrisDimension dimension, IrisRegion region, IrisBiome biome) {
        return resolve(dimension == null ? null : dimension.getRiverPolicy(),
                region == null ? null : region.getRiverPolicy(),
                biome == null ? null : biome.getRiverPolicy(), loaderOf(biome, region, dimension));
    }

    public static Resolution resolveWithStatus(IrisDimension dimension, IrisRegion region, IrisBiome biome) {
        State state = resolveState(dimension == null ? null : dimension.getRiverPolicy(),
                region == null ? null : region.getRiverPolicy(),
                biome == null ? null : biome.getRiverPolicy(), loaderOf(biome, region, dimension), true);
        return new Resolution(state.build(), state.complete);
    }

    public static EffectiveRiverPolicy resolve(
            IrisRiverPolicy dimensionPolicy,
            IrisRiverPolicy regionPolicy,
            IrisRiverPolicy biomePolicy
    ) {
        return resolve(dimensionPolicy, regionPolicy, biomePolicy, null);
    }

    /**
     * @param data pack the policies came from, used to drop river biome references the version-content gate excluded.
     *             Null skips that filtering.
     */
    public static EffectiveRiverPolicy resolve(
            IrisRiverPolicy dimensionPolicy,
            IrisRiverPolicy regionPolicy,
            IrisRiverPolicy biomePolicy,
            IrisData data
    ) {
        return resolveState(dimensionPolicy, regionPolicy, biomePolicy, data, false).build();
    }

    private static State resolveState(IrisRiverPolicy dimensionPolicy, IrisRiverPolicy regionPolicy,
                                      IrisRiverPolicy biomePolicy, IrisData data, boolean trackCompleteness) {
        State state = new State(trackCompleteness);
        state.apply(dimensionPolicy, RiverConfinement.REGION, data);
        state.apply(regionPolicy, RiverConfinement.REGION, data);
        state.apply(biomePolicy, RiverConfinement.BIOME, data);
        return state;
    }

    private static IrisData loaderOf(IrisRegistrant... registrants) {
        for (IrisRegistrant registrant : registrants) {
            if (registrant != null && registrant.getLoader() != null) {
                return registrant.getLoader();
            }
        }

        return null;
    }

    public record Resolution(EffectiveRiverPolicy policy, boolean complete) {
    }

    private static final class State {
        private final Runnable unresolvedReference;
        private boolean complete = true;
        private IrisRiverPlacementMode placement = IrisRiverPlacementMode.NATURAL;
        private IrisRiverRoutingMode routing = IrisRiverRoutingMode.ALLOW;
        private boolean outletAdmission = true;
        private List<String> profiles = List.of();
        private List<String> surfaceBiomes = List.of();
        private List<String> mouthBiomes = List.of();
        private List<String> shoreBiomes = List.of();
        private List<String> bankBiomes = List.of();
        private List<String> floodedCaveBiomes = List.of();
        private List<String> surfacePools = List.of();
        private double widthMultiplier = 1D;
        private double depthMultiplier = 1D;
        private double incisionMultiplier = 1D;
        private double routingMultiplier = 1D;
        private double bankMultiplier = 1D;
        private Double shoreBiomeWidth = null;
        private RiverConfinement confinement = RiverConfinement.NONE;
        private Double shoreWidth = null;
        private Boolean erosion = null;

        private State(boolean trackCompleteness) {
            unresolvedReference = trackCompleteness ? this::unresolvedReference : null;
        }

        /**
         * {@code scope} is the confinement a {@code confined: true} at this level binds courses to; {@code data} is the
         * pack whose version-content gate filters the biome selections (null skips filtering).
         */
        private void apply(IrisRiverPolicy policy, RiverConfinement scope, IrisData data) {
            if (policy == null) {
                return;
            }
            if (policy.getShoreBiomeWidth() != null) {
                shoreBiomeWidth = policy.getShoreBiomeWidth();
            }
            if (policy.getShoreWidth() != null) {
                shoreWidth = policy.getShoreWidth();
            }
            if (policy.getErosion() != null) {
                erosion = policy.getErosion();
            }
            if (policy.getConfined() != null) {
                confinement = policy.getConfined() ? scope : RiverConfinement.NONE;
            }
            if (policy.getPlacement() != null) {
                placement = policy.getPlacement();
            }
            if (policy.getRouting() != null) {
                routing = policy.getRouting();
            }
            if (policy.getOutletAdmission() != null) {
                outletAdmission = policy.getOutletAdmission();
            }
            if (policy.getProfiles() != null) {
                profiles = List.copyOf(policy.getProfiles());
            }
            if (policy.getSurfaceBiomes() != null) {
                surfaceBiomes = List.copyOf(policy.compatBiomes(policy.getSurfaceBiomes(), data, "surfaceBiomes", unresolvedReference));
            }
            if (policy.getMouthBiomes() != null) {
                mouthBiomes = List.copyOf(policy.compatBiomes(policy.getMouthBiomes(), data, "mouthBiomes", unresolvedReference));
            }
            if (policy.getShoreBiomes() != null) {
                shoreBiomes = List.copyOf(policy.compatBiomes(policy.getShoreBiomes(), data, "shoreBiomes", unresolvedReference));
            }
            if (policy.getBankBiomes() != null) {
                bankBiomes = List.copyOf(policy.compatBiomes(policy.getBankBiomes(), data, "bankBiomes", unresolvedReference));
            }
            if (policy.getFloodedCaveBiomes() != null) {
                floodedCaveBiomes = List.copyOf(policy.compatBiomes(policy.getFloodedCaveBiomes(), data, "floodedCaveBiomes", unresolvedReference));
            }
            if (policy.getSurfacePools() != null) {
                surfacePools = List.copyOf(policy.getSurfacePools());
            }
            if (policy.getWidthMultiplier() != null) {
                widthMultiplier = policy.getWidthMultiplier();
            }
            if (policy.getDepthMultiplier() != null) {
                depthMultiplier = policy.getDepthMultiplier();
            }
            if (policy.getIncisionMultiplier() != null) {
                incisionMultiplier = policy.getIncisionMultiplier();
            }
            if (policy.getRoutingMultiplier() != null) {
                routingMultiplier = policy.getRoutingMultiplier();
            }
            if (policy.getBankMultiplier() != null) {
                bankMultiplier = policy.getBankMultiplier();
            }
        }

        private void unresolvedReference() {
            complete = false;
        }

        private EffectiveRiverPolicy build() {
            return new EffectiveRiverPolicy(
                    placement,
                    routing,
                    outletAdmission,
                    profiles,
                    surfaceBiomes,
                    mouthBiomes,
                    shoreBiomes,
                    bankBiomes,
                    floodedCaveBiomes,
                    surfacePools,
                    widthMultiplier,
                    depthMultiplier,
                    incisionMultiplier,
                    routingMultiplier,
                    bankMultiplier,
                    shoreBiomeWidth,
                    confinement,
                    shoreWidth,
                    erosion
            );
        }
    }
}
