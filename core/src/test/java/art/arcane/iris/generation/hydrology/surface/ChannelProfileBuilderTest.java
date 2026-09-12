package art.arcane.iris.generation.hydrology.surface;

import art.arcane.iris.generation.hydrology.policy.SurfaceRiverPolicy;

import art.arcane.iris.generation.hydrology.HydrologyGeometrySampler;
import art.arcane.iris.generation.hydrology.HydrologyPlannerSettings;
import art.arcane.iris.generation.hydrology.HydrologyPoint;
import art.arcane.iris.generation.hydrology.HydrologyTerrainSample;
import art.arcane.iris.generation.hydrology.HydrologyTerrainSampler;
import org.junit.Test;

import java.util.List;
import java.util.function.IntBinaryOperator;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class ChannelProfileBuilderTest {
    private static final HydrologyGeometrySampler CONSTANT_GEOMETRY = request -> switch (request.field()) {
        case SURFACE_WIDTH -> 6;
        case SURFACE_DEPTH -> 3;
        default -> request.minimum();
    };

    @Test
    public void headwaterOpensAsASpringPoolThatNarrowsToTheCruiseWidth() {
        SurfaceCenterline centerline = straight(400);
        ChannelProfile profile = builder(1D, 1D).build(centerline, "water", false);

        assertEquals(400, profile.size());
        assertEquals(3D, profile.halfWidth()[200], 1.0E-9D);
        assertEquals(3D, profile.halfWidth()[399], 1.0E-9D);
        assertEquals(3D, profile.depth()[200], 1.0E-9D);
        assertEquals(3D * 2.5D, profile.halfWidth()[0], 1.0E-9D);
        assertEquals(4D, profile.depth()[0], 1.0E-9D);
        for (int station = 1; station <= 24; station++) {
            assertTrue(profile.halfWidth()[station] <= profile.halfWidth()[station - 1]);
            assertTrue(profile.depth()[station] <= profile.depth()[station - 1]);
        }
        assertEquals(3D, profile.halfWidth()[24], 1.0E-9D);
        assertEquals(3D, profile.depth()[24], 1.0E-9D);
    }

    @Test
    public void theInletWidensAndDeepensTowardTheCoastOverItsLength() {
        SurfaceCenterline centerline = straight(400);
        HydrologyPlannerSettings.Surface defaults = HydrologyPlannerSettings.defaults().surface();
        HydrologyPlannerSettings.Inlet inlet = defaults.banks().inlet();
        ChannelProfile profile = builder(1D, 1D).build(centerline, "water", true);
        ChannelProfile plain = builder(defaults.banks().withInlet(HydrologyPlannerSettings.Inlet.none()))
                .build(centerline, "water", true);

        assertEquals(64, inlet.length());
        int flareStart = 400 - inlet.length();
        assertEquals(3D * defaults.banks().mouthFlareRatio(), profile.halfWidth()[399], 1.0E-9D);
        assertEquals(3D + inlet.depth(), profile.depth()[399], 1.0E-9D);
        assertEquals(3D, profile.halfWidth()[flareStart], 1.0E-9D);
        assertEquals(3D, profile.depth()[flareStart], 1.0E-9D);
        assertTrue(profile.halfWidth()[flareStart + 1] > 3D);
        assertTrue(profile.depth()[flareStart + 1] > 3D);
        assertEquals(3D, profile.halfWidth()[300], 1.0E-9D);
        for (int station = flareStart + 1; station < 400; station++) {
            assertTrue(profile.halfWidth()[station] >= profile.halfWidth()[station - 1]);
            assertTrue(profile.depth()[station] >= profile.depth()[station - 1]);
        }
        assertEquals(3D, plain.halfWidth()[399], 1.0E-9D);
        assertEquals(3D, plain.depth()[399], 1.0E-9D);
    }

    @Test
    public void theFlareCompletesAtTheShorelineWhenTheCenterlineRunsIntoTheSea() {
        SurfaceCenterline centerline = straight(400);
        HydrologyPlannerSettings.Surface defaults = HydrologyPlannerSettings.defaults().surface();
        HydrologyPlannerSettings.Inlet inlet = defaults.banks().inlet();
        HydrologyTerrainSampler coast = (int x, int z) -> x >= 380
                ? HydrologyTerrainSample.ocean(50, "ocean")
                : HydrologyTerrainSample.openLand(80, 0D, "land");
        ChannelProfile profile = new ChannelProfileBuilder(defaults, coast, CONSTANT_GEOMETRY).build(centerline, "water", true);

        int flareStart = 380 - inlet.length();
        assertEquals(3D, profile.halfWidth()[flareStart], 1.0E-9D);
        assertTrue(profile.halfWidth()[flareStart + 1] > 3D);
        assertEquals(3D * defaults.banks().mouthFlareRatio(), profile.halfWidth()[379], 1.0E-9D);
        assertEquals(3D + inlet.depth(), profile.depth()[379], 1.0E-9D);
        assertEquals(3D * defaults.banks().mouthFlareRatio(), profile.halfWidth()[399], 1.0E-9D);
        assertEquals(3D, profile.halfWidth()[300], 1.0E-9D);
    }

    @Test
    public void policyMultipliersScaleWidthDepthAndBank() {
        SurfaceCenterline centerline = straight(400);
        ChannelProfile profile = builder(2D, 0.5D).build(centerline, "water", false);

        assertEquals(6D, profile.halfWidth()[200], 1.0E-9D);
        assertEquals(1.5D, profile.depth()[200], 1.0E-9D);
        assertEquals(1.25D, profile.bankMultiplier()[200], 1.0E-9D);
        assertEquals(6D * 1.25D + 2D, profile.collar(200, 0.25D), 1.0E-9D);
    }

    @Test
    public void groundFallingAwayAcrossThePoolShrinksTheSpringPool() {
        SurfaceCenterline centerline = straight(400);
        ChannelProfile sloped = builder(1D, 1D, (int x, int z) -> 80 - Math.abs(z) / 2).build(centerline, "water", false);
        ChannelProfile cliff = builder(1D, 1D, (int x, int z) -> 80 - Math.abs(z) * 8).build(centerline, "water", false);

        assertTrue(sloped.halfWidth()[0] > 3D);
        assertTrue(sloped.halfWidth()[0] < 3D * 2.5D);
        assertEquals(3D, cliff.halfWidth()[0], 1.0E-9D);
        assertEquals(4D, cliff.depth()[0], 1.0E-9D);
        assertEquals(3D, sloped.halfWidth()[24], 1.0E-9D);
    }

    @Test
    public void smoothingRadiusZeroFollowsTheSampledWidthExactly() {
        HydrologyGeometrySampler stepped = request -> switch (request.field()) {
            case SURFACE_WIDTH -> request.x() < 200 ? 4 : 8;
            case SURFACE_DEPTH -> 3;
            default -> request.minimum();
        };
        SurfaceCenterline centerline = straight(400);
        ChannelProfile sharp = builder(new HydrologyPlannerSettings.Channel(0, 0.6D, 1.4D, 1D), stepped)
                .build(centerline, "water", false);
        ChannelProfile smoothed = builder(HydrologyPlannerSettings.Channel.defaults(), stepped)
                .build(centerline, "water", false);

        assertEquals(2D, sharp.halfWidth()[199], 1.0E-9D);
        assertEquals(4D, sharp.halfWidth()[200], 1.0E-9D);
        assertEquals(2D, sharp.halfWidth()[100], 1.0E-9D);
        assertEquals(4D, sharp.halfWidth()[300], 1.0E-9D);
        assertTrue(smoothed.halfWidth()[199] > 2D);
        assertTrue(smoothed.halfWidth()[200] < 4D);
        assertEquals(2D, smoothed.halfWidth()[100], 1.0E-9D);
    }

    @Test
    public void springExtraDepthDeepensTheSpringPool() {
        SurfaceCenterline centerline = straight(400);
        ChannelProfile flat = builder(new HydrologyPlannerSettings.Channel(16, 0.6D, 1.4D, 0D), CONSTANT_GEOMETRY)
                .build(centerline, "water", false);
        ChannelProfile deep = builder(new HydrologyPlannerSettings.Channel(16, 0.6D, 1.4D, 3D), CONSTANT_GEOMETRY)
                .build(centerline, "water", false);

        assertEquals(3D, flat.depth()[0], 1.0E-9D);
        assertEquals(3D, flat.depth()[200], 1.0E-9D);
        assertEquals(6D, deep.depth()[0], 1.0E-9D);
        assertEquals(3D, deep.depth()[24], 1.0E-9D);
        assertEquals(3D, deep.depth()[200], 1.0E-9D);
    }

    private static ChannelProfileBuilder builder(
            HydrologyPlannerSettings.Channel channel,
            HydrologyGeometrySampler geometry
    ) {
        HydrologyPlannerSettings.Banks banks = HydrologyPlannerSettings.defaults().surface().banks();
        HydrologyPlannerSettings.Banks tuned = new HydrologyPlannerSettings.Banks(
                banks.sink(), banks.blendSlope(), banks.minimumBlendWidth(), banks.maximumBlendWidth(),
                banks.roughness(), banks.roughnessWavelength(), banks.cascadeRun(), banks.waterfallMinimumDrop(),
                banks.mouthFlareRatio(), banks.inlet(), banks.springWidthRatio(), banks.springLength(),
                banks.exposeCutStrata(), banks.erosion(), banks.ponds(), channel, banks.flow());
        HydrologyPlannerSettings.Surface defaults = HydrologyPlannerSettings.defaults().surface();
        HydrologyPlannerSettings.Surface surface = new HydrologyPlannerSettings.Surface(
                defaults.enabled(), defaults.sources(), defaults.minimumWidth(), defaults.maximumWidth(),
                defaults.minimumDepth(), defaults.maximumDepth(), defaults.maximumIncision(), defaults.shoreWidth(), tuned);
        return new ChannelProfileBuilder(surface, (int x, int z) -> HydrologyTerrainSample.openLand(80, 0D, "land"), geometry);
    }

    private static ChannelProfileBuilder builder(double widthMultiplier, double depthMultiplier) {
        return builder(widthMultiplier, depthMultiplier, (int x, int z) -> 80);
    }

    private static ChannelProfileBuilder builder(HydrologyPlannerSettings.Banks banks) {
        HydrologyPlannerSettings.Surface defaults = HydrologyPlannerSettings.defaults().surface();
        HydrologyPlannerSettings.Surface surface = new HydrologyPlannerSettings.Surface(
                defaults.enabled(), defaults.sources(), defaults.minimumWidth(), defaults.maximumWidth(),
                defaults.minimumDepth(), defaults.maximumDepth(), defaults.maximumIncision(), defaults.shoreWidth(), banks);
        return new ChannelProfileBuilder(surface, (int x, int z) -> HydrologyTerrainSample.openLand(80, 0D, "land"), CONSTANT_GEOMETRY);
    }

    private static ChannelProfileBuilder builder(double widthMultiplier, double depthMultiplier, IntBinaryOperator height) {
        HydrologyTerrainSampler sampler = (int x, int z) -> new HydrologyTerrainSample(
                height.applyAsInt(x, z),
                0D,
                false,
                false,
                48,
                50,
                true,
                true,
                true,
                false,
                false,
                false,
                0D,
                1D,
                1D,
                widthMultiplier,
                depthMultiplier,
                1D,
                1D,
                1.25D,
                "parent",
                "parent",
                "parent",
                "parent",
                "parent",
                "parent",
                List.of("water"), List.of(),
                Double.NaN,
                null,
                Double.NaN,
                true,
                SurfaceRiverPolicy.INHERIT
        );
        return new ChannelProfileBuilder(HydrologyPlannerSettings.defaults().surface(), sampler, CONSTANT_GEOMETRY);
    }

    private static SurfaceCenterline straight(int stations) {
        return SurfaceCenterline.densify(path(stations));
    }

    private static List<HydrologyPoint> path(int stations) {
        return List.of(new HydrologyPoint(0, 0, 0), new HydrologyPoint(stations - 1, 0, 0));
    }
}
