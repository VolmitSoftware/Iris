package art.arcane.iris.generation.hydrology;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class HydrologyPlannerSettingsTest {
    @Test
    public void routingUsesIndependentSurfaceAndUndergroundCourseFloors() {
        HydrologyPlannerSettings.Routing routing = HydrologyPlannerSettings.defaults().routing();

        assertEquals(384, routing.minimumCourseLength(true));
        assertEquals(192, routing.minimumCourseLength(false));
    }

    @Test
    public void refinementSpacingIsDerivedFromTheSampleLattice() {
        assertEquals(4, HydrologyPlannerSettings.Routing.refinementSpacing(64));
        assertEquals(2, HydrologyPlannerSettings.Routing.refinementSpacing(50));
        assertEquals(1, HydrologyPlannerSettings.Routing.refinementSpacing(45));
        assertEquals(4, HydrologyPlannerSettings.defaults().routing().refinementSpacing());
    }

    @Test
    public void crossTileAdmissionRejectsPublicationEnvelopesBeyondFourColorsPerAxis() {
        HydrologyPlannerSettings base = HydrologyPlannerSettings.defaults();
        assertEquals(0.5D, base.surface().sources().density(), 0D);
        assertEquals(0.25D, base.underground().sources().density(), 0D);
        HydrologyPlannerSettings.DeepFluid overlongChannel = new HydrologyPlannerSettings.DeepFluid(
                "deep",
                true,
                1D,
                64,
                -64,
                -32,
                1,
                1,
                1,
                1,
                1,
                3072,
                1,
                1,
                1,
                64,
                1,
                false,
                true
        );

        assertEquals(2, base.crossTileColorPeriod());
        assertThrows(IllegalArgumentException.class, () -> new HydrologyPlannerSettings(
                base.seaLevel(),
                base.routing(),
                base.surface(),
                base.hydraulics(),
                base.underground(),
                base.outlets(),
                HydrologyPlannerSettings.Geometry.defaults(),
                List.of(overlongChannel), List.of(),
                0D,
                HydrologyPlannerSettings.SeaCaves.disabled(),
                HydrologyPlannerSettings.SurfacePolicyBounds.NONE
        )
        );
    }

    @Test
    public void canonicalBoundaryValuesAreAccepted() {
        HydrologyPlannerSettings.Routing routing = new HydrologyPlannerSettings.Routing(
                512,
                512,
                4,
                256,
                0,
                0,
                0D,
                0D,
                0D,
                0D,
                1D,
                0, HydrologyPlannerSettings.Regional.disabled()
        );
        HydrologyPlannerSettings.Source sources = new HydrologyPlannerSettings.Source(true, 1D, 0, 0, 1, 0);
        HydrologyPlannerSettings.Surface surface = new HydrologyPlannerSettings.Surface(
                true,
                sources,
                1,
                1,
                1,
                1,
                0,
                1D,
                HydrologyPlannerSettings.Banks.defaults());
        HydrologyPlannerSettings.Hydraulics hydraulics = new HydrologyPlannerSettings.Hydraulics(1);
        HydrologyPlannerSettings.Grotto grotto = new HydrologyPlannerSettings.Grotto(true, 1, 1, 1, 1);
        HydrologyPlannerSettings.Outlets outlets = HydrologyPlannerSettings.Outlets.of(
                true,
                grotto,
                grotto,
                true,
                0,
                0,
                64,
                1,
                1
        );
        HydrologyPlannerSettings.DeepFluid deepFluid = new HydrologyPlannerSettings.DeepFluid(
                "deep",
                true,
                1D,
                8192,
                -64,
                -32,
                1,
                1,
                1,
                1,
                1,
                2730,
                1,
                1,
                1,
                64,
                1,
                false,
                true
        );

        assertEquals(256, routing.maximumRouteLength());
        assertEquals(0, surface.maximumIncision());
        assertEquals(1, hydraulics.waterfallMinimumDrop());
        assertEquals(0, outlets.mouthLevelingDistance());
        assertEquals(1, grotto.maximumVolume());
        assertEquals(2730, deepFluid.maximumChannelLength());
    }

    @Test
    public void bankSettingsRejectValuesOutsideTheirBounds() {
        HydrologyPlannerSettings.Banks banks = HydrologyPlannerSettings.Banks.defaults();

        assertEquals(0, banks.sink());
        assertTrue(banks.erosion().enabled());
        assertEquals(12, banks.erosion().smoothingRadius());
        assertTrue(banks.ponds().source().enabled());
        assertTrue(banks.ponds().terminal().enabled());
        assertEquals(3D, banks.blendSlope(), 0D);
        assertEquals(4, banks.minimumBlendWidth());
        assertEquals(32, banks.maximumBlendWidth());
        assertEquals(6, banks.waterfallMinimumDrop());
        assertTrue(banks.exposeCutStrata());
        assertThrows(IllegalArgumentException.class, () -> HydrologyPlannerSettings.Banks.of(-1, 3D, 4, 32, 0.25D, 16, 2, 6, 1.6D, HydrologyPlannerSettings.Inlet.none(), 2.5D, 24, true, HydrologyPlannerSettings.Erosion.defaults(), HydrologyPlannerSettings.Ponds.defaults()));
        assertThrows(IllegalArgumentException.class, () -> HydrologyPlannerSettings.Banks.of(1, 0D, 4, 32, 0.25D, 16, 2, 6, 1.6D, HydrologyPlannerSettings.Inlet.none(), 2.5D, 24, true, HydrologyPlannerSettings.Erosion.defaults(), HydrologyPlannerSettings.Ponds.defaults()));
        assertThrows(IllegalArgumentException.class, () -> HydrologyPlannerSettings.Banks.of(1, 3D, 40, 32, 0.25D, 16, 2, 6, 1.6D, HydrologyPlannerSettings.Inlet.none(), 2.5D, 24, true, HydrologyPlannerSettings.Erosion.defaults(), HydrologyPlannerSettings.Ponds.defaults()));
        assertThrows(IllegalArgumentException.class, () -> HydrologyPlannerSettings.Banks.of(1, 3D, 4, 32, 0.25D, 16, 2, 0, 1.6D, HydrologyPlannerSettings.Inlet.none(), 2.5D, 24, true, HydrologyPlannerSettings.Erosion.defaults(), HydrologyPlannerSettings.Ponds.defaults()));
        assertThrows(IllegalArgumentException.class, () -> HydrologyPlannerSettings.Banks.of(1, 3D, 4, 32, 0.25D, 16, 0, 6, 1.6D, HydrologyPlannerSettings.Inlet.none(), 2.5D, 24, true, HydrologyPlannerSettings.Erosion.defaults(), HydrologyPlannerSettings.Ponds.defaults()));
        assertThrows(IllegalArgumentException.class, () -> HydrologyPlannerSettings.Banks.of(1, 3D, 4, 32, 1.5D, 16, 2, 6, 1.6D, HydrologyPlannerSettings.Inlet.none(), 2.5D, 24, true, HydrologyPlannerSettings.Erosion.defaults(), HydrologyPlannerSettings.Ponds.defaults()));
        assertThrows(IllegalArgumentException.class, () -> new HydrologyPlannerSettings.Surface(
                true,
                new HydrologyPlannerSettings.Source(true, 1D, 0, 0, 1, 0),
                1, 1, 1, 1, 0, 1D,
                null));
        assertThrows(IllegalArgumentException.class, () -> HydrologyPlannerSettings.Erosion.of(true, -1, 0.45D, 1D, 0.5D));
        assertThrows(IllegalArgumentException.class, () -> HydrologyPlannerSettings.Erosion.of(true, 12, 1D, 1D, 0.5D));
        assertThrows(IllegalArgumentException.class, () -> HydrologyPlannerSettings.Erosion.of(true, 12, 0.45D, 0D, 0.5D));
        assertThrows(IllegalArgumentException.class, () -> HydrologyPlannerSettings.Erosion.of(true, 12, 0.45D, 1D, -0.1D));
        assertThrows(IllegalArgumentException.class, () -> new HydrologyPlannerSettings.Pond(true, 0, 4, 3));
        assertThrows(IllegalArgumentException.class, () -> new HydrologyPlannerSettings.Pond(true, 5, 4, 3));
        assertThrows(IllegalArgumentException.class, () -> new HydrologyPlannerSettings.Pond(true, 4, 6, 0));
        assertThrows(IllegalArgumentException.class, () -> new HydrologyPlannerSettings.Ponds(null, new HydrologyPlannerSettings.Pond(true, 4, 6, 3)));
    }

    @Test
    public void inletSettingsRejectValuesOutsideTheirBounds() {
        HydrologyPlannerSettings.Inlet inlet = HydrologyPlannerSettings.defaults().surface().banks().inlet();

        assertEquals(64, inlet.length());
        assertEquals(3, inlet.depth());
        assertEquals(32, inlet.maximumIncision());
        assertEquals(HydrologyPlannerSettings.Inlet.of(0, 0, 0), HydrologyPlannerSettings.Inlet.none());
        assertThrows(IllegalArgumentException.class, () -> HydrologyPlannerSettings.Inlet.of(-1, 3, 32));
        assertThrows(IllegalArgumentException.class, () -> HydrologyPlannerSettings.Inlet.of(1025, 3, 32));
        assertThrows(IllegalArgumentException.class, () -> HydrologyPlannerSettings.Inlet.of(64, -1, 32));
        assertThrows(IllegalArgumentException.class, () -> HydrologyPlannerSettings.Inlet.of(64, 65, 32));
        assertThrows(IllegalArgumentException.class, () -> HydrologyPlannerSettings.Inlet.of(64, 3, -1));
        assertThrows(IllegalArgumentException.class, () -> HydrologyPlannerSettings.Inlet.of(64, 3, 513));
        assertThrows(IllegalArgumentException.class, () -> HydrologyPlannerSettings.Banks.of(
                0, 3D, 4, 32, 0.25D, 16, 2, 6, 1.6D, null, 2.5D, 24, true,
                HydrologyPlannerSettings.Erosion.defaults(), HydrologyPlannerSettings.Ponds.defaults()));
        HydrologyPlannerSettings.Inlet.of(1024, 64, 512);
    }

    @Test
    public void publicationRadiusGrowsWithTheMouthFlare() {
        HydrologyPlannerSettings base = HydrologyPlannerSettings.defaults();
        HydrologyPlannerSettings.Banks banks = base.surface().banks();
        HydrologyPlannerSettings.Banks flared = HydrologyPlannerSettings.Banks.of(
                banks.sink(), banks.blendSlope(), banks.minimumBlendWidth(), banks.maximumBlendWidth(), banks.roughness(),
                banks.roughnessWavelength(), banks.cascadeRun(), banks.waterfallMinimumDrop(), 4D, banks.inlet(),
                banks.springWidthRatio(), banks.springLength(), banks.exposeCutStrata(), banks.erosion(), banks.ponds());
        HydrologyPlannerSettings.Surface surface = base.surface();
        HydrologyPlannerSettings wide = new HydrologyPlannerSettings(
                base.seaLevel(), base.routing(),
                new HydrologyPlannerSettings.Surface(surface.enabled(), surface.sources(), surface.minimumWidth(),
                        surface.maximumWidth(), surface.minimumDepth(), surface.maximumDepth(), surface.maximumIncision(),
                        surface.shoreWidth(), flared),
                base.hydraulics(), base.underground(), base.outlets(), base.geometry(), base.deepFluids(), base.surfacePools(), 0D, HydrologyPlannerSettings.SeaCaves.disabled(),
                HydrologyPlannerSettings.SurfacePolicyBounds.NONE
        );

        assertEquals(1.6D, banks.mouthFlareRatio(), 0D);
        int widened = (int) StrictMath.ceil(surface.maximumWidth() * (4D - banks.mouthFlareRatio()) / 2D);
        assertTrue(wide.publicationRadius() - base.publicationRadius() >= widened - 1);
        assertTrue(wide.publicationRadius() > base.publicationRadius());
    }

    @Test
    public void nonPositiveWaterfallThresholdsAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> new HydrologyPlannerSettings.Hydraulics(0));
    }

    @Test
    public void valuesOutsideCanonicalBoundsAreRejected() {
        HydrologyPlannerSettings.Grotto accepted = new HydrologyPlannerSettings.Grotto(true, 1, 1, 1, 1);
        assertEquals(1, accepted.maximumVolume());
        assertThrows(IllegalArgumentException.class,
                () -> new HydrologyPlannerSettings.Grotto(true, 1, 1, 1, 0));
        HydrologyPlannerSettings.Grotto disabled = new HydrologyPlannerSettings.Grotto(false, 1, 1, 1, 1);
        assertThrows(IllegalArgumentException.class, () -> HydrologyPlannerSettings.Outlets.of(
                true,
                accepted,
                disabled,
                true,
                0,
                0,
                1,
                1,
                1)
        );
        assertThrows(IllegalArgumentException.class, () -> HydrologyPlannerSettings.Outlets.of(
                true,
                accepted,
                accepted,
                true,
                0,
                0,
                65,
                1,
                1)
        );
    }

    @Test
    public void coastalOutletBudgetIsBounded() {
        HydrologyPlannerSettings.Grotto grotto = new HydrologyPlannerSettings.Grotto(true, 1, 1, 1, 1);
        HydrologyPlannerSettings.Outlets separate = HydrologyPlannerSettings.Outlets.of(
                true,
                grotto,
                grotto,
                true,
                0,
                0,
                1,
                3,
                0
        );

        assertEquals(0, separate.maximumCoastalPerTile());
        assertEquals(256, HydrologyPlannerSettings.Outlets.of(true, grotto, grotto, true, 0, 0, 1, 3, 256)
                .maximumCoastalPerTile());
        assertThrows(IllegalArgumentException.class, () -> HydrologyPlannerSettings.Outlets.of(
                true, grotto, grotto, true, 0, 0, 1, 3, -1));
        assertThrows(IllegalArgumentException.class, () -> HydrologyPlannerSettings.Outlets.of(
                true, grotto, grotto, true, 0, 0, 1, 3, 257));
    }

    @Test
    public void dropGeometryNarrowsFlowAndExpandsItsReceivingBasin() {
        HydrologyPlannerSettings.Drops drops = HydrologyPlannerSettings.Drops.of(
                2,
                1.4D,
                2,
                0.45D,
                2,
                1.8D,
                8
        );

        assertEquals(5, drops.flowWidth(10));
        assertEquals(1, drops.stepLimit(HydrologyFeatureType.CASCADE));
        assertEquals(2, drops.stepLimit(HydrologyFeatureType.UNDERGROUND_DROP));
        assertEquals(2, drops.flowDepth(6));
        assertEquals(9, drops.basinWidth(5));
        assertEquals(5, drops.basinDepth(2, 8));
        assertThrows(IllegalArgumentException.class, () -> HydrologyPlannerSettings.Drops.of(
                2,
                1.4D,
                2,
                0.45D,
                4,
                1.8D,
                3
        ));
    }

    @Test
    public void deepSiteAdmissionUsesItsOwnHeadAndEnvelope() {
        HydrologyPlannerSettings.DeepFluid deepFluid = new HydrologyPlannerSettings.DeepFluid(
                "deep",
                true,
                1D,
                1024,
                16,
                96,
                4,
                4,
                3,
                3,
                0,
                0,
                2,
                2,
                4,
                4096,
                1,
                true,
                false
        );
        HydrologyTerrainSample terrainWithoutUndergroundRiverCave = HydrologyTerrainSample.openLand(100, 1D, "parent");

        assertFalse(HydrologyFeatureSitePlanner.deepSiteFits(terrainWithoutUndergroundRiverCave, deepFluid, 3, 3, 0));
        assertTrue(HydrologyFeatureSitePlanner.deepSiteFits(terrainWithoutUndergroundRiverCave, deepFluid, 3, 3, -64));
        assertTrue(HydrologyFeatureSitePlanner.deepSiteFits(terrainWithoutUndergroundRiverCave, deepFluid, 4, 3, 0));
        assertTrue(HydrologyFeatureSitePlanner.deepSiteFits(terrainWithoutUndergroundRiverCave, deepFluid, 80, 3, 0));
        assertFalse(HydrologyFeatureSitePlanner.deepSiteFits(terrainWithoutUndergroundRiverCave, deepFluid, 96, 3, 0));
        assertFalse(HydrologyFeatureSitePlanner.deepSiteFits(
                HydrologyTerrainSample.ocean(50, "ocean"),
                deepFluid,
                40,
                3,
                0
        ));
    }

    @Test
    public void seaCaveBoundsAreEnforcedAndTheDefaultsPlanNone() {
        HydrologyPlannerSettings.SeaCaves seaCaves = HydrologyPlannerSettings.SeaCaves.of(true, 3, 160, 8, 12);
        assertEquals(3, seaCaves.maximumPerTile());
        assertFalse(HydrologyPlannerSettings.SeaCaves.disabled().enabled());
        assertThrows(IllegalArgumentException.class, () -> HydrologyPlannerSettings.SeaCaves.of(true, -1, 160, 8, 12));
        assertThrows(IllegalArgumentException.class, () -> HydrologyPlannerSettings.SeaCaves.of(true, 65, 160, 8, 12));
        assertThrows(IllegalArgumentException.class, () -> HydrologyPlannerSettings.SeaCaves.of(true, 3, 15, 8, 12));
        assertThrows(IllegalArgumentException.class, () -> HydrologyPlannerSettings.SeaCaves.of(true, 3, 8193, 8, 12));
        assertThrows(IllegalArgumentException.class, () -> HydrologyPlannerSettings.SeaCaves.of(true, 3, 160, 0, 12));
        assertThrows(IllegalArgumentException.class, () -> HydrologyPlannerSettings.SeaCaves.of(true, 3, 160, 129, 12));
        assertThrows(IllegalArgumentException.class, () -> HydrologyPlannerSettings.SeaCaves.of(true, 3, 160, 8, -1));
        assertThrows(IllegalArgumentException.class, () -> HydrologyPlannerSettings.SeaCaves.of(true, 3, 160, 8, 129));

        HydrologyPlannerSettings base = HydrologyPlannerSettings.defaults();
        assertEquals(HydrologyPlannerSettings.SeaCaves.disabled(), base.seaCaves());
        HydrologyPlannerSettings withCaves = new HydrologyPlannerSettings(
                base.seaLevel(),
                base.routing(),
                base.surface(),
                base.hydraulics(),
                base.underground(),
                base.outlets(),
                base.geometry(),
                base.deepFluids(),
                base.surfacePools(),
                base.widestShoreBiomeWidth(),
                seaCaves,
                HydrologyPlannerSettings.SurfacePolicyBounds.NONE
        );
        assertEquals(seaCaves, withCaves.seaCaves());
        assertTrue(base.fingerprint() != withCaves.fingerprint());
    }

    @Test
    public void seaCavesAloneReachTheChamberAndItsSweepBeyondTheHalo() {
        HydrologyPlannerSettings base = HydrologyPlannerSettings.defaults();
        HydrologyPlannerSettings.Source noSources = new HydrologyPlannerSettings.Source(false, 0D, 0, 0, 0, 0);
        HydrologyPlannerSettings.Surface surface = new HydrologyPlannerSettings.Surface(
                false, noSources, 4, 8, 2, 4, 10, 1.5D, HydrologyPlannerSettings.Banks.defaults());
        HydrologyPlannerSettings.Underground underground = HydrologyPlannerSettings.Underground.of(
                false, noSources, -48, 72, 3, 8, 1, 3, 6, 14, true, 0);
        HydrologyPlannerSettings quiet = new HydrologyPlannerSettings(
                base.seaLevel(),
                base.routing(),
                surface,
                base.hydraulics(),
                underground,
                base.outlets(),
                base.geometry(),
                List.of(),
                List.of(),
                0D,
                HydrologyPlannerSettings.SeaCaves.disabled(),
                HydrologyPlannerSettings.SurfacePolicyBounds.NONE
        );
        HydrologyPlannerSettings withCaves = new HydrologyPlannerSettings(
                base.seaLevel(),
                base.routing(),
                surface,
                base.hydraulics(),
                underground,
                base.outlets(),
                base.geometry(),
                List.of(),
                List.of(),
                surface.shoreWidth(),
                HydrologyPlannerSettings.SeaCaves.of(true, 1, 64, 8, 12),
                HydrologyPlannerSettings.SurfacePolicyBounds.NONE
        );

        int alignedHalo = Math.min(base.routing().maximumRouteLength(), base.routing().sampleSpacing() * 2)
                / base.routing().sampleSpacing() * base.routing().sampleSpacing();
        assertEquals(0, quiet.publicationRadius());
        assertEquals(alignedHalo + base.outlets().coastalGrotto().horizontalRadius() + 12 + 1, withCaves.publicationRadius());
    }


    @Test
    public void channelSettingsPinTheirDefaultsAndRejectValuesOutsideTheirBounds() {
        HydrologyPlannerSettings.Channel channel = HydrologyPlannerSettings.Channel.defaults();

        assertEquals(new HydrologyPlannerSettings.Channel(16, 0.6D, 1.4D, 1D), channel);
        assertEquals(channel, HydrologyPlannerSettings.Banks.defaults().channel());
        assertEquals(channel, HydrologyPlannerSettings.defaults().surface().banks().channel());
        new HydrologyPlannerSettings.Channel(0, 1D, 1D, 0D);
        assertThrows(IllegalArgumentException.class, () -> new HydrologyPlannerSettings.Channel(-1, 0.6D, 1.4D, 1D));
        assertThrows(IllegalArgumentException.class, () -> new HydrologyPlannerSettings.Channel(16, 0D, 1.4D, 1D));
        assertThrows(IllegalArgumentException.class, () -> new HydrologyPlannerSettings.Channel(16, 1.01D, 1.4D, 1D));
        assertThrows(IllegalArgumentException.class, () -> new HydrologyPlannerSettings.Channel(16, Double.NaN, 1.4D, 1D));
        assertThrows(IllegalArgumentException.class, () -> new HydrologyPlannerSettings.Channel(16, 0.6D, 0.99D, 1D));
        assertThrows(IllegalArgumentException.class, () -> new HydrologyPlannerSettings.Channel(16, 0.6D, Double.POSITIVE_INFINITY, 1D));
        assertThrows(IllegalArgumentException.class, () -> new HydrologyPlannerSettings.Channel(16, 0.6D, 1.4D, -0.1D));
        assertThrows(IllegalArgumentException.class, () -> new HydrologyPlannerSettings.Channel(16, 0.6D, 1.4D, Double.NaN));

        HydrologyPlannerSettings base = HydrologyPlannerSettings.defaults();
        HydrologyPlannerSettings.Banks banks = base.surface().banks();
        HydrologyPlannerSettings smoothed = withBanks(base, banksWith(banks, banks.erosion(),
                new HydrologyPlannerSettings.Channel(8, 0.6D, 1.4D, 1D), banks.flow()));
        assertTrue(base.fingerprint() != smoothed.fingerprint());
        assertEquals(base.publicationRadius(), smoothed.publicationRadius());
    }

    @Test
    public void flowSettingsPinTheirDefaultsAndRejectValuesOutsideTheirBounds() {
        HydrologyPlannerSettings.Flow flow = HydrologyPlannerSettings.Flow.defaults();

        assertEquals(new HydrologyPlannerSettings.Flow(0.65D, 2, 2D, 1), flow);
        assertEquals(flow, HydrologyPlannerSettings.Banks.defaults().flow());
        assertEquals(flow, HydrologyPlannerSettings.defaults().surface().banks().flow());
        new HydrologyPlannerSettings.Flow(0D, 1, 0D, 0);
        assertThrows(IllegalArgumentException.class, () -> new HydrologyPlannerSettings.Flow(-0.01D, 2, 2D, 1));
        assertThrows(IllegalArgumentException.class, () -> new HydrologyPlannerSettings.Flow(1D, 2, 2D, 1));
        assertThrows(IllegalArgumentException.class, () -> new HydrologyPlannerSettings.Flow(Double.NaN, 2, 2D, 1));
        assertThrows(IllegalArgumentException.class, () -> new HydrologyPlannerSettings.Flow(0.65D, 0, 2D, 1));
        assertThrows(IllegalArgumentException.class, () -> new HydrologyPlannerSettings.Flow(0.65D, 2, -0.1D, 1));
        assertThrows(IllegalArgumentException.class, () -> new HydrologyPlannerSettings.Flow(0.65D, 2, Double.POSITIVE_INFINITY, 1));
        assertThrows(IllegalArgumentException.class, () -> new HydrologyPlannerSettings.Flow(0.65D, 2, 2D, -1));

        HydrologyPlannerSettings base = HydrologyPlannerSettings.defaults();
        HydrologyPlannerSettings.Banks banks = base.surface().banks();
        HydrologyPlannerSettings plunging = withBanks(base, banksWith(banks, banks.erosion(), banks.channel(),
                new HydrologyPlannerSettings.Flow(0.65D, 3, 2D, 1)));
        assertTrue(base.fingerprint() != plunging.fingerprint());
        assertEquals(base.publicationRadius(), plunging.publicationRadius());
    }

    @Test
    public void inletSettingsCarryTheEstuaryRampAndRejectValuesOutsideTheirBounds() {
        assertEquals(new HydrologyPlannerSettings.Inlet(64, 3, 32, 0.5D, 1D), HydrologyPlannerSettings.Inlet.defaults());
        assertEquals(new HydrologyPlannerSettings.Inlet(0, 0, 0, 0.5D, 1D), HydrologyPlannerSettings.Inlet.none());
        assertEquals(HydrologyPlannerSettings.Inlet.defaults(), HydrologyPlannerSettings.Inlet.of(64, 3, 32));
        assertEquals(0.5D, HydrologyPlannerSettings.defaults().surface().banks().inlet().courseFraction(), 0D);
        assertEquals(1D, HydrologyPlannerSettings.defaults().surface().banks().inlet().rampSlope(), 0D);
        new HydrologyPlannerSettings.Inlet(64, 3, 32, 1D, 0.01D);
        new HydrologyPlannerSettings.Inlet(64, 3, 32, 0.01D, 4D);
        assertThrows(IllegalArgumentException.class, () -> new HydrologyPlannerSettings.Inlet(64, 3, 32, 0D, 1D));
        assertThrows(IllegalArgumentException.class, () -> new HydrologyPlannerSettings.Inlet(64, 3, 32, 1.01D, 1D));
        assertThrows(IllegalArgumentException.class, () -> new HydrologyPlannerSettings.Inlet(64, 3, 32, Double.NaN, 1D));
        assertThrows(IllegalArgumentException.class, () -> new HydrologyPlannerSettings.Inlet(64, 3, 32, 0.5D, 0D));
        assertThrows(IllegalArgumentException.class, () -> new HydrologyPlannerSettings.Inlet(64, 3, 32, 0.5D, -1D));
        assertThrows(IllegalArgumentException.class, () -> new HydrologyPlannerSettings.Inlet(64, 3, 32, 0.5D, Double.POSITIVE_INFINITY));

        HydrologyPlannerSettings base = HydrologyPlannerSettings.defaults();
        HydrologyPlannerSettings ramped = withBanks(base, base.surface().banks()
                .withInlet(new HydrologyPlannerSettings.Inlet(64, 3, 32, 0.25D, 1D)));
        assertTrue(base.fingerprint() != ramped.fingerprint());
        assertEquals(base.publicationRadius(), ramped.publicationRadius());
    }

    @Test
    public void erosionSettingsPinTheirDefaultsAndRejectValuesOutsideTheirBounds() {
        HydrologyPlannerSettings.Erosion erosion = HydrologyPlannerSettings.Erosion.defaults();

        assertEquals(new HydrologyPlannerSettings.Erosion(true, 12, 0.45D, 1D, 0.5D,
                IrisRiverBlendStyle.SMOOTH, 4, 0.5D, IrisRiverBedProfile.BOWL, 0D, 0D, HydrologyPlannerSettings.Excavation.defaults()), erosion);
        assertEquals(erosion, HydrologyPlannerSettings.Erosion.of(true, 12, 0.45D, 1D, 0.5D));
        assertEquals(IrisRiverBlendStyle.SMOOTH, erosion.style());
        assertEquals(4, erosion.terraceSteps());
        assertEquals(0.5D, erosion.cliffFraction(), 0D);
        assertEquals(IrisRiverBedProfile.BOWL, erosion.bedProfile());
        assertEquals(0D, erosion.shoreRise(), 0D);
        assertEquals(0D, erosion.blendBaseWidth(), 0D);
        new HydrologyPlannerSettings.Erosion(true, 12, 0.45D, 1D, 0.5D, IrisRiverBlendStyle.TERRACED, 2, 0D, IrisRiverBedProfile.V, 0D, 0D, HydrologyPlannerSettings.Excavation.defaults());
        new HydrologyPlannerSettings.Erosion(true, 12, 0.45D, 1D, 0.5D, IrisRiverBlendStyle.CLIFF, 16, 1D, IrisRiverBedProfile.U, 4D, 32D, HydrologyPlannerSettings.Excavation.defaults());
        assertThrows(IllegalArgumentException.class, () -> new HydrologyPlannerSettings.Erosion(true, 12, 0.45D, 1D, 0.5D, null, 4, 0.5D, IrisRiverBedProfile.BOWL, 0D, 0D, HydrologyPlannerSettings.Excavation.defaults()));
        assertThrows(IllegalArgumentException.class, () -> new HydrologyPlannerSettings.Erosion(true, 12, 0.45D, 1D, 0.5D, IrisRiverBlendStyle.SMOOTH, 1, 0.5D, IrisRiverBedProfile.BOWL, 0D, 0D, HydrologyPlannerSettings.Excavation.defaults()));
        assertThrows(IllegalArgumentException.class, () -> new HydrologyPlannerSettings.Erosion(true, 12, 0.45D, 1D, 0.5D, IrisRiverBlendStyle.SMOOTH, 4, -0.01D, IrisRiverBedProfile.BOWL, 0D, 0D, HydrologyPlannerSettings.Excavation.defaults()));
        assertThrows(IllegalArgumentException.class, () -> new HydrologyPlannerSettings.Erosion(true, 12, 0.45D, 1D, 0.5D, IrisRiverBlendStyle.SMOOTH, 4, 1.01D, IrisRiverBedProfile.BOWL, 0D, 0D, HydrologyPlannerSettings.Excavation.defaults()));
        assertThrows(IllegalArgumentException.class, () -> new HydrologyPlannerSettings.Erosion(true, 12, 0.45D, 1D, 0.5D, IrisRiverBlendStyle.SMOOTH, 4, Double.NaN, IrisRiverBedProfile.BOWL, 0D, 0D, HydrologyPlannerSettings.Excavation.defaults()));
        assertThrows(IllegalArgumentException.class, () -> new HydrologyPlannerSettings.Erosion(true, 12, 0.45D, 1D, 0.5D, IrisRiverBlendStyle.SMOOTH, 4, 0.5D, null, 0D, 0D, HydrologyPlannerSettings.Excavation.defaults()));
        assertThrows(IllegalArgumentException.class, () -> new HydrologyPlannerSettings.Erosion(true, 12, 0.45D, 1D, 0.5D, IrisRiverBlendStyle.SMOOTH, 4, 0.5D, IrisRiverBedProfile.BOWL, -0.1D, 0D, HydrologyPlannerSettings.Excavation.defaults()));
        assertThrows(IllegalArgumentException.class, () -> new HydrologyPlannerSettings.Erosion(true, 12, 0.45D, 1D, 0.5D, IrisRiverBlendStyle.SMOOTH, 4, 0.5D, IrisRiverBedProfile.BOWL, Double.POSITIVE_INFINITY, 0D, HydrologyPlannerSettings.Excavation.defaults()));
        assertThrows(IllegalArgumentException.class, () -> new HydrologyPlannerSettings.Erosion(true, 12, 0.45D, 1D, 0.5D, IrisRiverBlendStyle.SMOOTH, 4, 0.5D, IrisRiverBedProfile.BOWL, 0D, -0.1D, HydrologyPlannerSettings.Excavation.defaults()));
        assertThrows(IllegalArgumentException.class, () -> new HydrologyPlannerSettings.Erosion(true, 12, 0.45D, 1D, 0.5D, IrisRiverBlendStyle.SMOOTH, 4, 0.5D, IrisRiverBedProfile.BOWL, 0D, Double.NaN, HydrologyPlannerSettings.Excavation.defaults()));

        HydrologyPlannerSettings base = HydrologyPlannerSettings.defaults();
        HydrologyPlannerSettings.Banks banks = base.surface().banks();
        HydrologyPlannerSettings linear = withBanks(base, banksWith(banks,
                new HydrologyPlannerSettings.Erosion(true, 12, 0.45D, 1D, 0.5D, IrisRiverBlendStyle.LINEAR, 4, 0.5D, IrisRiverBedProfile.BOWL, 0D, 0D, HydrologyPlannerSettings.Excavation.defaults()),
                banks.channel(), banks.flow()));
        assertTrue(base.fingerprint() != linear.fingerprint());
        assertEquals(base.publicationRadius(), linear.publicationRadius());
    }

    @Test
    public void channelShapePinsTheOrganicWallDefaultsAndRejectsValuesOutsideTheirBounds() {
        HydrologyPlannerSettings.ChannelShape shape = HydrologyPlannerSettings.ChannelShape.of(2.4D, 0.28D, 0.24D, 11);

        assertEquals(new HydrologyPlannerSettings.ChannelShape(2.4D, 0.28D, 0.24D, 11, 0.86D, 0.58D, 1.18D, 0.08D, 0.06D, 0D, 0.62D, 0.2D), shape);
        assertEquals(shape, HydrologyPlannerSettings.Geometry.defaults().surface());
        assertEquals(shape, HydrologyPlannerSettings.Geometry.defaults().underground());
        assertEquals(shape, HydrologyPlannerSettings.Geometry.defaults().grottos());
        new HydrologyPlannerSettings.ChannelShape(2.4D, 0.28D, 0.24D, 11, 0.1D, 0.01D, 0.01D, 0D, 0D, 0D, 0.01D, 0D);
        new HydrologyPlannerSettings.ChannelShape(2.4D, 0.28D, 0.24D, 11, 4D, 4D, 4D, 1D, 1D, 1D, 1D, 1D);
        assertThrows(IllegalArgumentException.class, () -> new HydrologyPlannerSettings.ChannelShape(2.4D, 0.28D, 0.24D, 11, 0.09D, 0.58D, 1.18D, 0.08D, 0.06D, 0D, 0.62D, 0.2D));
        assertThrows(IllegalArgumentException.class, () -> new HydrologyPlannerSettings.ChannelShape(2.4D, 0.28D, 0.24D, 11, 4.01D, 0.58D, 1.18D, 0.08D, 0.06D, 0D, 0.62D, 0.2D));
        assertThrows(IllegalArgumentException.class, () -> new HydrologyPlannerSettings.ChannelShape(2.4D, 0.28D, 0.24D, 11, Double.NaN, 0.58D, 1.18D, 0.08D, 0.06D, 0D, 0.62D, 0.2D));
        assertThrows(IllegalArgumentException.class, () -> new HydrologyPlannerSettings.ChannelShape(2.4D, 0.28D, 0.24D, 11, 0.86D, 0D, 1.18D, 0.08D, 0.06D, 0D, 0.62D, 0.2D));
        assertThrows(IllegalArgumentException.class, () -> new HydrologyPlannerSettings.ChannelShape(2.4D, 0.28D, 0.24D, 11, 0.86D, 0.7D, 0.6D, 0.08D, 0.06D, 0D, 0.62D, 0.2D));
        assertThrows(IllegalArgumentException.class, () -> new HydrologyPlannerSettings.ChannelShape(2.4D, 0.28D, 0.24D, 11, 0.86D, 0.58D, 4.01D, 0.08D, 0.06D, 0D, 0.62D, 0.2D));
        assertThrows(IllegalArgumentException.class, () -> new HydrologyPlannerSettings.ChannelShape(2.4D, 0.28D, 0.24D, 11, 0.86D, 0.58D, Double.POSITIVE_INFINITY, 0.08D, 0.06D, 0D, 0.62D, 0.2D));
        assertThrows(IllegalArgumentException.class, () -> new HydrologyPlannerSettings.ChannelShape(2.4D, 0.28D, 0.24D, 11, 0.86D, 0.58D, 1.18D, -0.01D, 0.06D, 0D, 0.62D, 0.2D));
        assertThrows(IllegalArgumentException.class, () -> new HydrologyPlannerSettings.ChannelShape(2.4D, 0.28D, 0.24D, 11, 0.86D, 0.58D, 1.18D, 1.01D, 0.06D, 0D, 0.62D, 0.2D));
        assertThrows(IllegalArgumentException.class, () -> new HydrologyPlannerSettings.ChannelShape(2.4D, 0.28D, 0.24D, 11, 0.86D, 0.58D, 1.18D, 0.08D, -0.01D, 0D, 0.62D, 0.2D));
        assertThrows(IllegalArgumentException.class, () -> new HydrologyPlannerSettings.ChannelShape(2.4D, 0.28D, 0.24D, 11, 0.86D, 0.58D, 1.18D, 0.08D, 1.01D, 0D, 0.62D, 0.2D));
        assertThrows(IllegalArgumentException.class, () -> new HydrologyPlannerSettings.ChannelShape(2.4D, 0.28D, 0.24D, 11, 0.86D, 0.58D, 1.18D, 0.08D, 0.06D, -0.01D, 0.62D, 0.2D));
        assertThrows(IllegalArgumentException.class, () -> new HydrologyPlannerSettings.ChannelShape(2.4D, 0.28D, 0.24D, 11, 0.86D, 0.58D, 1.18D, 0.08D, 0.06D, 1.01D, 0.62D, 0.2D));
        assertThrows(IllegalArgumentException.class, () -> new HydrologyPlannerSettings.ChannelShape(2.4D, 0.28D, 0.24D, 11, 0.86D, 0.58D, 1.18D, 0.08D, 0.06D, 0D, 0D, 0.2D));
        assertThrows(IllegalArgumentException.class, () -> new HydrologyPlannerSettings.ChannelShape(2.4D, 0.28D, 0.24D, 11, 0.86D, 0.58D, 1.18D, 0.08D, 0.06D, 0D, 1.01D, 0.2D));
        assertThrows(IllegalArgumentException.class, () -> new HydrologyPlannerSettings.ChannelShape(2.4D, 0.28D, 0.24D, 11, 0.86D, 0.58D, 1.18D, 0.08D, 0.06D, 0D, 0.62D, -0.01D));
        assertThrows(IllegalArgumentException.class, () -> new HydrologyPlannerSettings.ChannelShape(2.4D, 0.28D, 0.24D, 11, 0.86D, 0.58D, 1.18D, 0.08D, 0.06D, 0D, 0.62D, 1.01D));

        HydrologyPlannerSettings base = HydrologyPlannerSettings.defaults();
        HydrologyPlannerSettings.Geometry geometry = base.geometry();
        HydrologyPlannerSettings rounder = rebuild(base, base.surface(), base.underground(), base.outlets(),
                new HydrologyPlannerSettings.Geometry(geometry.meanders(), geometry.surface(),
                        shapeWithRadialBase(geometry.underground(), 0.9D), geometry.grottos(), geometry.drops()),
                base.seaCaves());
        assertTrue(base.fingerprint() != rounder.fingerprint());
        assertEquals(base.publicationRadius(), rounder.publicationRadius());
    }

    @Test
    public void undergroundSettingsPinRockCoverAndRejectValuesOutsideTheirBounds() {
        HydrologyPlannerSettings.Source sources = HydrologyPlannerSettings.defaults().underground().sources();
        HydrologyPlannerSettings.Underground underground = HydrologyPlannerSettings.Underground.of(
                true, sources, -48, 72, 3, 8, 1, 3, 6, 14, true, 1);

        assertEquals(new HydrologyPlannerSettings.Underground(true, sources, -48, 72, 3, 8, 1, 3, 6, 14, true, 1, 1, 1, 8), underground);
        assertEquals(underground, HydrologyPlannerSettings.defaults().underground());
        assertEquals(1, underground.minimumRockCover());
        assertEquals(1, underground.minimumFloorCover());
        assertEquals(8, underground.wideningSources());
        new HydrologyPlannerSettings.Underground(true, sources, -48, 72, 3, 8, 1, 3, 6, 14, true, 1, 256, 256, 1024);
        assertThrows(IllegalArgumentException.class, () -> new HydrologyPlannerSettings.Underground(true, sources, -48, 72, 3, 8, 1, 3, 6, 14, true, 1, 0, 1, 8));
        assertThrows(IllegalArgumentException.class, () -> new HydrologyPlannerSettings.Underground(true, sources, -48, 72, 3, 8, 1, 3, 6, 14, true, 1, 257, 1, 8));
        assertThrows(IllegalArgumentException.class, () -> new HydrologyPlannerSettings.Underground(true, sources, -48, 72, 3, 8, 1, 3, 6, 14, true, 1, 1, 0, 8));
        assertThrows(IllegalArgumentException.class, () -> new HydrologyPlannerSettings.Underground(true, sources, -48, 72, 3, 8, 1, 3, 6, 14, true, 1, 1, 257, 8));
        assertThrows(IllegalArgumentException.class, () -> new HydrologyPlannerSettings.Underground(true, sources, -48, 72, 3, 8, 1, 3, 6, 14, true, 1, 1, 1, 0));
        assertThrows(IllegalArgumentException.class, () -> new HydrologyPlannerSettings.Underground(true, sources, -48, 72, 3, 8, 1, 3, 6, 14, true, 1, 1, 1, 1025));

        HydrologyPlannerSettings base = HydrologyPlannerSettings.defaults();
        HydrologyPlannerSettings covered = rebuild(base, base.surface(),
                new HydrologyPlannerSettings.Underground(true, sources, -48, 72, 3, 8, 1, 3, 6, 14, true, 1, 2, 1, 8),
                base.outlets(), base.geometry(), base.seaCaves());
        assertTrue(base.fingerprint() != covered.fingerprint());
        assertEquals(base.publicationRadius(), covered.publicationRadius());
    }

    @Test
    public void outletSettingsPinTheCliffSlopeFactorAndRejectValuesOutsideTheirBounds() {
        HydrologyPlannerSettings.Grotto grotto = new HydrologyPlannerSettings.Grotto(true, 1, 1, 1, 1);
        HydrologyPlannerSettings.Outlets outlets = HydrologyPlannerSettings.Outlets.of(true, grotto, grotto, true, 0, 0, 1, 3, 0);

        assertEquals(new HydrologyPlannerSettings.Outlets(true, grotto, grotto, true, 0, 0, 1, 3, 0, 0.5D), outlets);
        assertEquals(0.5D, HydrologyPlannerSettings.defaults().outlets().coastalCliffSlopeFactor(), 0D);
        new HydrologyPlannerSettings.Outlets(true, grotto, grotto, true, 0, 0, 1, 3, 0, 0D);
        new HydrologyPlannerSettings.Outlets(true, grotto, grotto, true, 0, 0, 1, 3, 0, 4D);
        assertThrows(IllegalArgumentException.class, () -> new HydrologyPlannerSettings.Outlets(true, grotto, grotto, true, 0, 0, 1, 3, 0, -0.1D));
        assertThrows(IllegalArgumentException.class, () -> new HydrologyPlannerSettings.Outlets(true, grotto, grotto, true, 0, 0, 1, 3, 0, Double.NaN));
        assertThrows(IllegalArgumentException.class, () -> new HydrologyPlannerSettings.Outlets(true, grotto, grotto, true, 0, 0, 1, 3, 0, Double.POSITIVE_INFINITY));

        HydrologyPlannerSettings base = HydrologyPlannerSettings.defaults();
        HydrologyPlannerSettings.Outlets o = base.outlets();
        HydrologyPlannerSettings steeper = rebuild(base, base.surface(), base.underground(),
                new HydrologyPlannerSettings.Outlets(o.oceanEnabled(), o.coastalGrotto(), o.inlandGrotto(), o.surfaceSinkholesEnabled(),
                        o.coastalCliffMinimumHeight(), o.mouthLevelingDistance(), o.maximumOceanApron(), o.maximumPerTile(),
                        o.maximumCoastalPerTile(), 1D),
                base.geometry(), base.seaCaves());
        assertTrue(base.fingerprint() != steeper.fingerprint());
        assertEquals(base.publicationRadius(), steeper.publicationRadius());
    }

    @Test
    public void seaCaveSettingsPinTheSweepJitterAndRejectValuesOutsideTheirBounds() {
        assertEquals(new HydrologyPlannerSettings.SeaCaves(false, 0, 16, 1, 0, 25D), HydrologyPlannerSettings.SeaCaves.disabled());
        assertEquals(new HydrologyPlannerSettings.SeaCaves(true, 3, 160, 8, 12, 25D), HydrologyPlannerSettings.SeaCaves.of(true, 3, 160, 8, 12));
        assertEquals(25D, HydrologyPlannerSettings.defaults().seaCaves().sweepJitterDegrees(), 0D);
        new HydrologyPlannerSettings.SeaCaves(true, 3, 160, 8, 12, 0D);
        new HydrologyPlannerSettings.SeaCaves(true, 3, 160, 8, 12, 180D);
        assertThrows(IllegalArgumentException.class, () -> new HydrologyPlannerSettings.SeaCaves(true, 3, 160, 8, 12, -0.1D));
        assertThrows(IllegalArgumentException.class, () -> new HydrologyPlannerSettings.SeaCaves(true, 3, 160, 8, 12, 180.1D));
        assertThrows(IllegalArgumentException.class, () -> new HydrologyPlannerSettings.SeaCaves(true, 3, 160, 8, 12, Double.NaN));

        HydrologyPlannerSettings base = HydrologyPlannerSettings.defaults();
        HydrologyPlannerSettings straight = rebuild(base, base.surface(), base.underground(), base.outlets(), base.geometry(),
                new HydrologyPlannerSettings.SeaCaves(true, 1, 64, 8, 12, 25D));
        HydrologyPlannerSettings jittered = rebuild(base, base.surface(), base.underground(), base.outlets(), base.geometry(),
                new HydrologyPlannerSettings.SeaCaves(true, 1, 64, 8, 12, 40D));
        assertTrue(straight.fingerprint() != jittered.fingerprint());
        assertEquals(straight.publicationRadius(), jittered.publicationRadius());
    }

    @Test
    public void dropSettingsPinTheUndergroundCascadeRunAndRejectValuesOutsideTheirBounds() {
        HydrologyPlannerSettings.Drops drops = HydrologyPlannerSettings.Drops.of(2, 1.4D, 2, 0.45D, 2, 1.8D, 8);

        assertEquals(new HydrologyPlannerSettings.Drops(2, 1.4D, 2, 0.45D, 2, 1.8D, 8, 0), drops);
        assertEquals(drops, HydrologyPlannerSettings.Geometry.defaults().drops());
        assertEquals(0, drops.undergroundCascadeRunPerBlock());
        new HydrologyPlannerSettings.Drops(2, 1.4D, 2, 0.45D, 2, 1.8D, 8, 64);
        assertThrows(IllegalArgumentException.class, () -> new HydrologyPlannerSettings.Drops(2, 1.4D, 2, 0.45D, 2, 1.8D, 8, -1));
        assertThrows(IllegalArgumentException.class, () -> new HydrologyPlannerSettings.Drops(2, 1.4D, 2, 0.45D, 2, 1.8D, 8, 65));

        HydrologyPlannerSettings base = HydrologyPlannerSettings.defaults();
        HydrologyPlannerSettings.Geometry geometry = base.geometry();
        HydrologyPlannerSettings longer = rebuild(base, base.surface(), base.underground(), base.outlets(),
                new HydrologyPlannerSettings.Geometry(geometry.meanders(), geometry.surface(), geometry.underground(), geometry.grottos(),
                        new HydrologyPlannerSettings.Drops(2, 1.4D, 2, 0.45D, 2, 1.8D, 8, 3)),
                base.seaCaves());
        assertTrue(base.fingerprint() != longer.fingerprint());
        assertEquals(base.publicationRadius(), longer.publicationRadius());
    }

    @Test
    public void bankSettingsCarryChannelAndFlowAndTheNewKnobsLeaveTheEnvelopeAlone() {
        HydrologyPlannerSettings.Banks banks = HydrologyPlannerSettings.Banks.defaults();

        assertEquals(HydrologyPlannerSettings.Banks.of(0, 3D, 4, 32, 0.25D, 16, 2, 6, 1.6D, HydrologyPlannerSettings.Inlet.defaults(),
                2.5D, 24, true, HydrologyPlannerSettings.Erosion.defaults(), HydrologyPlannerSettings.Ponds.defaults()), banks);
        assertEquals(HydrologyPlannerSettings.Channel.defaults(), banks.channel());
        assertEquals(HydrologyPlannerSettings.Flow.defaults(), banks.flow());
        assertEquals(HydrologyPlannerSettings.Channel.defaults(), banks.withInlet(HydrologyPlannerSettings.Inlet.none()).channel());
        assertEquals(HydrologyPlannerSettings.Flow.defaults(), banks.withInlet(HydrologyPlannerSettings.Inlet.none()).flow());
        assertEquals(HydrologyPlannerSettings.Inlet.none(), banks.withInlet(HydrologyPlannerSettings.Inlet.none()).inlet());
        assertThrows(IllegalArgumentException.class, () -> banksWith(banks, banks.erosion(), null, banks.flow()));
        assertThrows(IllegalArgumentException.class, () -> banksWith(banks, banks.erosion(), banks.channel(), null));

        HydrologyPlannerSettings base = HydrologyPlannerSettings.defaults();
        HydrologyPlannerSettings.Underground u = base.underground();
        HydrologyPlannerSettings.Outlets o = base.outlets();
        HydrologyPlannerSettings.Geometry geometry = base.geometry();
        HydrologyPlannerSettings reshaped = rebuild(base,
                surfaceWith(base.surface(), banksWith(banks,
                        new HydrologyPlannerSettings.Erosion(true, 12, 0.45D, 1D, 0.5D, IrisRiverBlendStyle.TERRACED, 8, 0.25D, IrisRiverBedProfile.U, 1D, 4D, HydrologyPlannerSettings.Excavation.defaults()),
                        new HydrologyPlannerSettings.Channel(8, 0.5D, 2D, 2D),
                        new HydrologyPlannerSettings.Flow(0.5D, 3, 3D, 2))
                        .withInlet(new HydrologyPlannerSettings.Inlet(64, 3, 32, 0.25D, 2D))),
                new HydrologyPlannerSettings.Underground(u.enabled(), u.sources(), u.minimumFluidY(), u.maximumFluidY(), u.minimumWidth(),
                        u.maximumWidth(), u.minimumDepth(), u.maximumDepth(), u.minimumHeadroom(), u.maximumHeadroom(),
                        u.connectToExistingCaves(), u.tributaries(), 4, 3, 16),
                new HydrologyPlannerSettings.Outlets(o.oceanEnabled(), o.coastalGrotto(), o.inlandGrotto(), o.surfaceSinkholesEnabled(),
                        o.coastalCliffMinimumHeight(), o.mouthLevelingDistance(), o.maximumOceanApron(), o.maximumPerTile(),
                        o.maximumCoastalPerTile(), 1D),
                new HydrologyPlannerSettings.Geometry(geometry.meanders(), shapeWithRadialBase(geometry.surface(), 0.9D),
                        shapeWithRadialBase(geometry.underground(), 0.7D), shapeWithRadialBase(geometry.grottos(), 1.1D),
                        new HydrologyPlannerSettings.Drops(2, 1.4D, 2, 0.45D, 2, 1.8D, 8, 3)),
                new HydrologyPlannerSettings.SeaCaves(false, 0, 16, 1, 0, 40D));
        assertEquals(base.publicationRadius(), reshaped.publicationRadius());
        assertEquals(base.crossTileColorPeriod(), reshaped.crossTileColorPeriod());
        assertTrue(base.fingerprint() != reshaped.fingerprint());
    }

    private static HydrologyPlannerSettings withBanks(HydrologyPlannerSettings base, HydrologyPlannerSettings.Banks banks) {
        return rebuild(base, surfaceWith(base.surface(), banks), base.underground(), base.outlets(), base.geometry(), base.seaCaves());
    }

    private static HydrologyPlannerSettings rebuild(
            HydrologyPlannerSettings base,
            HydrologyPlannerSettings.Surface surface,
            HydrologyPlannerSettings.Underground underground,
            HydrologyPlannerSettings.Outlets outlets,
            HydrologyPlannerSettings.Geometry geometry,
            HydrologyPlannerSettings.SeaCaves seaCaves
    ) {
        return new HydrologyPlannerSettings(base.seaLevel(), base.routing(), surface, base.hydraulics(), underground, outlets,
                geometry, base.deepFluids(), base.surfacePools(), base.widestShoreBiomeWidth(), seaCaves,
                HydrologyPlannerSettings.SurfacePolicyBounds.NONE
        );
    }

    private static HydrologyPlannerSettings.Surface surfaceWith(HydrologyPlannerSettings.Surface surface, HydrologyPlannerSettings.Banks banks) {
        return new HydrologyPlannerSettings.Surface(surface.enabled(), surface.sources(), surface.minimumWidth(), surface.maximumWidth(),
                surface.minimumDepth(), surface.maximumDepth(), surface.maximumIncision(), surface.shoreWidth(), banks);
    }

    private static HydrologyPlannerSettings.Banks banksWith(
            HydrologyPlannerSettings.Banks banks,
            HydrologyPlannerSettings.Erosion erosion,
            HydrologyPlannerSettings.Channel channel,
            HydrologyPlannerSettings.Flow flow
    ) {
        return new HydrologyPlannerSettings.Banks(banks.sink(), banks.blendSlope(), banks.minimumBlendWidth(), banks.maximumBlendWidth(),
                banks.roughness(), banks.roughnessWavelength(), banks.cascadeRun(), banks.waterfallMinimumDrop(), banks.mouthFlareRatio(),
                banks.inlet(), banks.springWidthRatio(), banks.springLength(), banks.exposeCutStrata(), erosion, banks.ponds(), channel, flow);
    }

    private static HydrologyPlannerSettings.ChannelShape shapeWithRadialBase(HydrologyPlannerSettings.ChannelShape shape, double radialBase) {
        return new HydrologyPlannerSettings.ChannelShape(shape.bedRoundness(), shape.bedRoughness(), shape.wallRoughness(), shape.roughnessWavelength(),
                radialBase, shape.radialMinimum(), shape.radialMaximum(), shape.primaryLobeStrength(), shape.detailLobeStrength(),
                shape.ceilingRoughness(), shape.aspectMinimum(), shape.aspectRange());
    }
}
