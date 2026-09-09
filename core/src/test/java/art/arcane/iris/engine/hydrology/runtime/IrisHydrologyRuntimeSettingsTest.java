package art.arcane.iris.engine.hydrology.runtime;

import art.arcane.iris.core.loader.IrisData;
import art.arcane.iris.core.loader.ResourceLoader;
import art.arcane.iris.engine.hydrology.HydrologyPlannerSettings;
import art.arcane.iris.engine.object.IrisBiome;
import art.arcane.iris.engine.object.IrisCoastalRiverGrottoConfig;
import art.arcane.iris.engine.object.IrisDeepFluidConfig;
import art.arcane.iris.engine.object.IrisDimension;
import art.arcane.iris.engine.object.IrisRegion;
import art.arcane.iris.engine.object.IrisRiverBedProfile;
import art.arcane.iris.engine.object.IrisRiverBlendStyle;
import art.arcane.iris.engine.object.IrisRiverGeometryConfig;
import art.arcane.iris.engine.object.IrisRiverHydrology;
import art.arcane.iris.engine.object.IrisRiverInlandOutlet;
import art.arcane.iris.engine.object.IrisRiverPolicy;
import art.arcane.iris.engine.object.IrisRiverRoutingConfig;
import art.arcane.iris.engine.object.IrisSurfaceRiverShapeConfig;
import art.arcane.volmlib.util.collection.KList;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class IrisHydrologyRuntimeSettingsTest {
    @Test
    @SuppressWarnings("unchecked")
    public void regionalSurfaceControlsFingerprintAllScopesAndExpandSpacingReach() {
        IrisDimension dimension = new IrisDimension().setRegions(new KList<>("tropical"));
        IrisRegion tropical = new IrisRegion().setRiverPolicy(new IrisRiverPolicy().setSurfaceSourceDensity(8D)
                .setSurfaceSourceSpacing(160).setSurfaceTributaries(3).setSurfaceInlandOutlets(3).setSurfaceCoastalOutlets(4));
        tropical.setLoadKey("tropical");
        IrisBiome volcanic = new IrisBiome().setRiverPolicy(new IrisRiverPolicy().setSurfaceCoastalOutlets(0));
        volcanic.setLoadKey("volcanic");
        IrisData data = mock(IrisData.class);
        ResourceLoader<IrisRegion> regions = mock(ResourceLoader.class);
        ResourceLoader<IrisBiome> biomes = mock(ResourceLoader.class);
        when(data.getRegionLoader()).thenReturn(regions);
        when(data.getBiomeLoader()).thenReturn(biomes);
        when(regions.load("tropical")).thenReturn(tropical);
        when(biomes.getPossibleKeys()).thenReturn(new String[]{"volcanic"});
        when(biomes.loadAll(any(String[].class))).thenReturn(new KList<>(volcanic));

        HydrologyPlannerSettings baseline = settings(dimension);
        HydrologyPlannerSettings regional = IrisHydrologyRuntime.createSettings(dimension, dimension.getHydrology(), () -> data);
        assertEquals(HydrologyPlannerSettings.SurfacePolicyBounds.NONE, baseline.surfacePolicyBounds());
        assertNotEquals(baseline.fingerprint(), regional.fingerprint());
        assertEquals(384, regional.maximumSurfaceSourceSpacing());

        volcanic.getRiverPolicy().setSurfaceSourceSpacing(1600);
        HydrologyPlannerSettings widened = IrisHydrologyRuntime.createSettings(dimension, dimension.getHydrology(), () -> data);
        assertNotEquals(regional.fingerprint(), widened.fingerprint());
        assertEquals(1600, widened.maximumSurfaceSourceSpacing());
        assertTrue(widened.publicationRadius() >= 1600);
        assertEquals(widened.fingerprint(), IrisHydrologyRuntime.createSettings(dimension, dimension.getHydrology(), () -> data).fingerprint());

        dimension.getRiverPolicy().setSurfaceInlandOutlets(7);
        assertNotEquals(widened.fingerprint(), IrisHydrologyRuntime.createSettings(dimension, dimension.getHydrology(), () -> data).fingerprint());
    }

    @Test
    public void geometryOnlyPoliciesInvalidateThePlanWithoutChangingDimensionLimits() {
        IrisDimension dimension = new IrisDimension();
        HydrologyPlannerSettings baseline = settings(dimension);
        dimension.getRiverPolicy().setSurfaceMinimumCourseLength(128);
        HydrologyPlannerSettings shortened = settings(dimension);
        assertNotEquals(baseline.fingerprint(), shortened.fingerprint());
        assertEquals(baseline.routing().minimumSurfaceCourseLength(), shortened.routing().minimumSurfaceCourseLength());
        assertEquals(baseline.maximumSurfaceSourceSpacing(), shortened.maximumSurfaceSourceSpacing());

        dimension.getRiverPolicy().setSurfaceMaximumIncision(24);
        HydrologyPlannerSettings deeper = settings(dimension);
        assertNotEquals(shortened.fingerprint(), deeper.fingerprint());
        assertEquals(baseline.surface().maximumIncision(), deeper.surface().maximumIncision());
        assertEquals(deeper.fingerprint(), settings(dimension).fingerprint());
        dimension.getRiverPolicy().setSurfaceMinimumCourseLength(null).setSurfaceMaximumIncision(null);
        assertEquals(baseline.fingerprint(), settings(dimension).fingerprint());
    }

    @Test
    public void deepChannelsRemainLocalWhenConfiguredSpacingIsLarge() {
        IrisDeepFluidConfig deepFluid = new IrisDeepFluidConfig()
                .setShortChannels(true)
                .setSpacing(8192);
        IrisRiverRoutingConfig routing = new IrisRiverRoutingConfig()
                .setTileSize(256);

        assertEquals(128, IrisHydrologyRuntime.maximumDeepChannelLength(deepFluid, routing));
    }

    @Test
    public void disabledDeepChannelsHaveNoPublicationReach() {
        IrisDeepFluidConfig deepFluid = new IrisDeepFluidConfig()
                .setShortChannels(false)
                .setSpacing(8192);
        IrisRiverRoutingConfig routing = new IrisRiverRoutingConfig()
                .setTileSize(256);

        assertEquals(0, IrisHydrologyRuntime.maximumDeepChannelLength(deepFluid, routing));
    }

    @Test
    public void surfaceSinkholeSettingRequiresTheCanonicalEnabledInlandOutlet() {
        IrisDimension dimension = new IrisDimension();
        IrisRiverHydrology rivers = dimension.getHydrology().getRivers();
        rivers.setEnabled(true);
        rivers.getGrottos().getInland().setEnabled(true).setConnectSurfaceRivers(true);
        rivers.getRouting().getInlandOutlets().add(IrisRiverInlandOutlet.SINKHOLE_GROTTO);

        assertTrue(IrisHydrologyRuntime.createSettings(dimension, dimension.getHydrology(), () -> null)
                .outlets().surfaceSinkholesEnabled());

        rivers.getRouting().getInlandOutlets().clear();

        assertFalse(IrisHydrologyRuntime.createSettings(dimension, dimension.getHydrology(), () -> null)
                .outlets().surfaceSinkholesEnabled());
    }

    @Test
    public void denseRiverSourcesRemainDistinctAndCanFillTheirTileQuota() {
        IrisDimension dimension = new IrisDimension();
        IrisRiverHydrology rivers = dimension.getHydrology().getRivers();
        rivers.setEnabled(true);
        rivers.getSurface().setEnabled(true);
        rivers.getSurface().getSources().setDensity(4.5D);
        rivers.getUnderground().setEnabled(true);
        rivers.getUnderground().getSources().setDensity(4.5D);
        rivers.getRouting().setSampleSpacing(64);

        assertEquals(5, IrisHydrologyRuntime.createSettings(dimension, dimension.getHydrology(), () -> null)
                .surface().sources().maximumPerTile());
        assertEquals(384, IrisHydrologyRuntime.createSettings(dimension, dimension.getHydrology(), () -> null)
                .surface().sources().minimumSpacing());
        assertEquals(5, IrisHydrologyRuntime.createSettings(dimension, dimension.getHydrology(), () -> null)
                .underground().sources().maximumPerTile());
        assertEquals(512, IrisHydrologyRuntime.createSettings(dimension, dimension.getHydrology(), () -> null)
                .underground().sources().minimumSpacing());
    }

    @Test
    public void surfaceBankAndFlowConfigurationMapsIntoPlannerSettings() {
        IrisDimension dimension = new IrisDimension();
        IrisRiverHydrology rivers = dimension.getHydrology().getRivers();
        rivers.getSurface().getChannel().setSink(2).setMaximumIncision(12);
        rivers.getSurface().getErosion()
                .setEnabled(false)
                .setSmoothingRadius(6)
                .setThalwegFraction(0.6D)
                .setBlendCurve(1.5D)
                .setBedNoise(0.25D);
        rivers.getSurface().getPonds().getSource().setEnabled(false).setMinimumRadius(3).setMaximumRadius(4).setDepth(2);
        rivers.getSurface().getPonds().getTerminal().setMinimumRadius(7).setMaximumRadius(11).setDepth(5);
        rivers.getSurface().getBanks()
                .setShoreWidth(2.5D)
                .setBlendSlope(4D)
                .setMinimumBlendWidth(6)
                .setMaximumBlendWidth(48)
                .setExposeCutStrata(false);
        rivers.getSurface().getFlow().setCascadeRun(3).setWaterfallMinimumDrop(8);
        rivers.getSurface().getMouths().setFlareRatio(2.2D).setInletLength(96).setInletDepth(5).setMaximumIncision(40);
        rivers.getUnderground().setMouthLevelingDistance(96);
        rivers.getRouting()
                .setSampleSpacing(32)
                .setMinimumSurfaceCourseLength(500)
                .setMinimumUndergroundCourseLength(250)
                .setValleyPreference(2.5D)
                .setUphillPenalty(30D)
                .setSlopePenalty(3D)
                .setConfluenceAttraction(0.4D);

        HydrologyPlannerSettings settings = IrisHydrologyRuntime.createSettings(dimension, dimension.getHydrology(), () -> null);
        HydrologyPlannerSettings.Banks banks = settings.surface().banks();

        assertEquals(2, banks.sink());
        assertFalse(banks.erosion().enabled());
        assertEquals(6, banks.erosion().smoothingRadius());
        assertEquals(0.6D, banks.erosion().thalwegFraction(), 0D);
        assertEquals(1.5D, banks.erosion().blendCurve(), 0D);
        assertEquals(0.25D, banks.erosion().bedNoise(), 0D);
        assertFalse(banks.ponds().source().enabled());
        assertEquals(3, banks.ponds().source().minimumRadius());
        assertEquals(4, banks.ponds().source().maximumRadius());
        assertEquals(2, banks.ponds().source().depth());
        assertTrue(banks.ponds().terminal().enabled());
        assertEquals(7, banks.ponds().terminal().minimumRadius());
        assertEquals(11, banks.ponds().terminal().maximumRadius());
        assertEquals(5, banks.ponds().terminal().depth());
        assertEquals(4D, banks.blendSlope(), 0D);
        assertEquals(6, banks.minimumBlendWidth());
        assertEquals(48, banks.maximumBlendWidth());
        assertEquals(3, banks.cascadeRun());
        assertEquals(8, banks.waterfallMinimumDrop());
        assertEquals(2.2D, banks.mouthFlareRatio(), 0D);
        assertEquals(HydrologyPlannerSettings.Inlet.of(96, 5, 40), banks.inlet());
        assertFalse(banks.exposeCutStrata());
        assertEquals(12, settings.surface().maximumIncision());
        assertEquals(2.5D, settings.surface().shoreWidth(), 0D);
        assertEquals(8, settings.hydraulics().waterfallMinimumDrop());
        assertEquals(96, settings.outlets().mouthLevelingDistance());
        assertEquals(4, settings.routing().refinementSpacing());
        assertEquals(500, settings.routing().minimumSurfaceCourseLength());
        assertEquals(250, settings.routing().minimumUndergroundCourseLength());
        assertEquals(2.5D, settings.routing().valleyPreference(), 0D);
        assertEquals(30D, settings.routing().uphillPenalty(), 0D);
        assertEquals(3D, settings.routing().slopePenalty(), 0D);
        assertEquals(0.4D, settings.routing().confluenceAttraction(), 0D);
        assertEquals(2, HydrologyPlannerSettings.Routing.refinementSpacing(50));
        assertEquals(1, HydrologyPlannerSettings.Routing.refinementSpacing(45));
    }

    @Test
    public void routingOutletLimitControlsDrainageRootCount() {
        IrisDimension dimension = new IrisDimension();
        dimension.getHydrology().getRivers().getRouting()
                .setMaximumOutletsPerTile(2)
                .setMaximumCoastalOutletsPerTile(5);

        HydrologyPlannerSettings.Outlets outlets = IrisHydrologyRuntime
                .createSettings(dimension, dimension.getHydrology(), () -> null)
                .outlets();

        assertEquals(2, outlets.maximumPerTile());
        assertEquals(5, outlets.maximumCoastalPerTile());
    }

    @Test
    public void organicGeometryConfigurationMapsWithoutLosingPrecision() {
        IrisDimension dimension = new IrisDimension();
        IrisRiverHydrology rivers = dimension.getHydrology().getRivers();
        rivers.getGeometry().getMeanders()
                .setPrimaryWavelength(47)
                .setDetailWavelength(9)
                .setPrimaryStrength(0.31D)
                .setDetailStrength(0.53D)
                .setMaximumOffsetRatio(0.44D)
                .setSmoothingPasses(2)
                .setMaximumTurnDegrees(76D);
        rivers.getSurface().getChannel()
                .setRoughness(0.37D)
                .setRoughnessWavelength(13);
        rivers.getGeometry().getDrops()
                .setCascadeRunPerBlock(4)
                .setCascadeExponent(2.2D)
                .setMaximumCascadeStep(1)
                .setFlowWidthRatio(0.81D)
                .setMaximumFlowDepth(3)
                .setBasinWidthRatio(2.1D)
                .setMaximumBasinDepth(9);

        HydrologyPlannerSettings.Geometry geometry = IrisHydrologyRuntime
                .createSettings(dimension, dimension.getHydrology(), () -> null)
                .geometry();

        assertEquals(47, geometry.meanders().primaryWavelength());
        assertEquals(9, geometry.meanders().detailWavelength());
        assertEquals(0.31D, geometry.meanders().primaryStrength(), 0D);
        assertEquals(0.53D, geometry.meanders().detailStrength(), 0D);
        assertEquals(0.44D, geometry.meanders().maximumOffsetRatio(), 0D);
        assertEquals(2, geometry.meanders().smoothingPasses());
        assertEquals(76D, geometry.meanders().maximumTurnDegrees(), 0D);
        assertEquals(0.37D, geometry.surface().bedRoughness(), 0D);
        assertEquals(0.37D, geometry.surface().wallRoughness(), 0D);
        assertEquals(13, geometry.surface().roughnessWavelength());
        assertEquals(4, geometry.drops().cascadeRunPerBlock());
        assertEquals(2.2D, geometry.drops().cascadeExponent(), 0D);
        assertEquals(1, geometry.drops().maximumCascadeStep());
        assertEquals(0.81D, geometry.drops().flowWidthRatio(), 0D);
        assertEquals(3, geometry.drops().maximumFlowDepth());
        assertEquals(2.1D, geometry.drops().basinWidthRatio(), 0D);
        assertEquals(9, geometry.drops().maximumBasinDepth());
    }

    @Test
    public void seaCaveConfigurationMapsIntoPlannerSettings() {
        IrisDimension dimension = new IrisDimension();
        IrisRiverHydrology rivers = dimension.getHydrology().getRivers();
        rivers.setEnabled(true);
        IrisCoastalRiverGrottoConfig coastal = rivers.getGrottos().getCoastal();
        coastal.setEnabled(true);
        coastal.getSeaCaves().setEnabled(true).setMaximumPerTile(5).setMinimumSpacing(200).setMinimumCoastHeight(9).setDepth(20);

        HydrologyPlannerSettings.SeaCaves mapped = IrisHydrologyRuntime.createSettings(dimension, dimension.getHydrology(), () -> null)
                .seaCaves();
        assertEquals(HydrologyPlannerSettings.SeaCaves.of(true, 5, 200, 9, 20), mapped);

        coastal.setEnabled(false);
        HydrologyPlannerSettings.SeaCaves withoutCoastalGrottos = IrisHydrologyRuntime.createSettings(dimension, dimension.getHydrology(), () -> null)
                .seaCaves();
        assertFalse(withoutCoastalGrottos.enabled());
        assertEquals(5, withoutCoastalGrottos.maximumPerTile());
        assertEquals(20, withoutCoastalGrottos.depth());

        coastal.setEnabled(true);
        coastal.getSeaCaves().setEnabled(false);
        assertFalse(IrisHydrologyRuntime.createSettings(dimension, dimension.getHydrology(), () -> null).seaCaves().enabled());
    }

    @Test
    public void surfaceShapingKnobsMapIntoErosionChannelFlowAndInlet() {
        IrisDimension dimension = new IrisDimension();
        IrisRiverHydrology rivers = dimension.getHydrology().getRivers();
        rivers.getSurface().getBanks().setShoreRise(1.5D).setBlendBaseWidth(3D);
        rivers.getSurface().getErosion()
                .setEnabled(true)
                .setSmoothingRadius(7)
                .setThalwegFraction(0.35D)
                .setBlendCurve(1.25D)
                .setBedNoise(0.4D)
                .setStyle(IrisRiverBlendStyle.TERRACED)
                .setTerraceSteps(6)
                .setCliffFraction(0.3D)
                .setBedProfile(IrisRiverBedProfile.V);
        rivers.getSurface().getChannel()
                .setSmoothingRadius(9)
                .setOutlineMinimumRatio(0.5D)
                .setOutlineMaximumRatio(1.9D)
                .setSpringExtraDepth(2.5D);
        rivers.getSurface().getFlow()
                .setWaterfallThalwegFraction(0.4D)
                .setPlungeBasinMinimumDrop(3)
                .setPlungeBasinLengthRatio(1.5D)
                .setPlungeBasinDepth(2);
        rivers.getSurface().getMouths()
                .setInletLength(80)
                .setInletDepth(4)
                .setMaximumIncision(36)
                .setInletCourseFraction(0.3D)
                .setInletRampSlope(1.75D);

        HydrologyPlannerSettings.Banks banks = settings(dimension).surface().banks();

        assertEquals(new HydrologyPlannerSettings.Erosion(true, 7, 0.35D, 1.25D, 0.4D,
                IrisRiverBlendStyle.TERRACED, 6, 0.3D, IrisRiverBedProfile.V, 1.5D, 3D), banks.erosion());
        assertEquals(new HydrologyPlannerSettings.Channel(9, 0.5D, 1.9D, 2.5D), banks.channel());
        assertEquals(new HydrologyPlannerSettings.Flow(0.4D, 3, 1.5D, 2), banks.flow());
        assertEquals(new HydrologyPlannerSettings.Inlet(80, 4, 36, 0.3D, 1.75D), banks.inlet());
    }

    @Test
    public void surfaceShapeRoughnessFallsBackToTheChannelRoughnessWhenUnset() {
        IrisDimension dimension = new IrisDimension();
        IrisRiverHydrology rivers = dimension.getHydrology().getRivers();
        rivers.getSurface().getChannel().setRoughness(0.37D).setRoughnessWavelength(13);
        IrisSurfaceRiverShapeConfig shape = rivers.getGeometry().getSurface();
        shape.setBedRoughness(null).setWallRoughness(null).setRoughnessWavelength(null);

        assertEquals(
                new HydrologyPlannerSettings.ChannelShape(2D, 0.37D, 0.37D, 13, 0.86D, 0.58D, 1.18D, 0.08D, 0.06D, 0D, 0.62D, 0.2D),
                settings(dimension).geometry().surface());

        shape.setBedRoughness(0.11D);
        assertEquals(
                new HydrologyPlannerSettings.ChannelShape(2D, 0.11D, 0.37D, 13, 0.86D, 0.58D, 1.18D, 0.08D, 0.06D, 0D, 0.62D, 0.2D),
                settings(dimension).geometry().surface());

        shape.setBedRoundness(3.5D)
                .setWallRoughness(0.22D)
                .setRoughnessWavelength(21)
                .setRadialBase(0.9D)
                .setRadialMinimum(0.5D)
                .setRadialMaximum(1.3D)
                .setPrimaryLobeStrength(0.12D)
                .setDetailLobeStrength(0.04D)
                .setCeilingRoughness(0.3D)
                .setAspectMinimum(0.7D)
                .setAspectRange(0.25D);
        assertEquals(
                new HydrologyPlannerSettings.ChannelShape(3.5D, 0.11D, 0.22D, 21, 0.9D, 0.5D, 1.3D, 0.12D, 0.04D, 0.3D, 0.7D, 0.25D),
                settings(dimension).geometry().surface());
    }

    @Test
    public void undergroundAndGrottoShapeKnobsMapIntoTheirChannelShapes() {
        IrisDimension dimension = new IrisDimension();
        IrisRiverGeometryConfig geometry = dimension.getHydrology().getRivers().getGeometry();
        geometry.getUnderground()
                .setBedRoundness(3.1D)
                .setBedRoughness(0.31D)
                .setWallRoughness(0.29D)
                .setRoughnessWavelength(17)
                .setRadialBase(0.7D)
                .setRadialMinimum(0.4D)
                .setRadialMaximum(1.5D)
                .setPrimaryLobeStrength(0.2D)
                .setDetailLobeStrength(0.1D)
                .setCeilingRoughness(0.45D)
                .setAspectMinimum(0.8D)
                .setAspectRange(0.1D);
        geometry.getGrottos()
                .setBedRoundness(1.5D)
                .setBedRoughness(0.15D)
                .setWallRoughness(0.35D)
                .setRoughnessWavelength(7)
                .setRadialBase(1.1D)
                .setRadialMinimum(0.9D)
                .setRadialMaximum(1.9D)
                .setPrimaryLobeStrength(0.3D)
                .setDetailLobeStrength(0.25D)
                .setCeilingRoughness(0.6D)
                .setAspectMinimum(0.5D)
                .setAspectRange(0.4D);

        HydrologyPlannerSettings.Geometry mapped = settings(dimension).geometry();

        assertEquals(new HydrologyPlannerSettings.ChannelShape(3.1D, 0.31D, 0.29D, 17, 0.7D, 0.4D, 1.5D, 0.2D, 0.1D, 0.45D, 0.8D, 0.1D),
                mapped.underground());
        assertEquals(new HydrologyPlannerSettings.ChannelShape(1.5D, 0.15D, 0.35D, 7, 1.1D, 0.9D, 1.9D, 0.3D, 0.25D, 0.6D, 0.5D, 0.4D),
                mapped.grottos());
    }

    @Test
    public void undergroundRockCoverFloorCoverAndWideningSourcesMap() {
        IrisDimension dimension = new IrisDimension();
        dimension.getHydrology().getRivers().getUnderground()
                .setMinimumRockCover(3)
                .setMinimumFloorCover(2)
                .setWideningSources(20);

        HydrologyPlannerSettings.Underground mapped = settings(dimension).underground();

        assertEquals(3, mapped.minimumRockCover());
        assertEquals(2, mapped.minimumFloorCover());
        assertEquals(20, mapped.wideningSources());
    }

    @Test
    public void coastalCliffKnobsMapWithTheVerticalRadiusFallback() {
        IrisDimension dimension = new IrisDimension();
        IrisCoastalRiverGrottoConfig coastal = dimension.getHydrology().getRivers().getGrottos().getCoastal();
        coastal.setVerticalRadius(7).setCliffMinimumHeight(null).setCliffSlopeFactor(1.25D);

        HydrologyPlannerSettings.Outlets fallback = settings(dimension).outlets();
        assertEquals(7, fallback.coastalCliffMinimumHeight());
        assertEquals(1.25D, fallback.coastalCliffSlopeFactor(), 0D);

        coastal.setVerticalRadius(2);
        assertEquals(4, settings(dimension).outlets().coastalCliffMinimumHeight());

        coastal.setCliffMinimumHeight(2);
        assertEquals(2, settings(dimension).outlets().coastalCliffMinimumHeight());
    }

    @Test
    public void seaCaveSweepJitterMapsIntoPlannerSettings() {
        IrisDimension dimension = new IrisDimension();
        IrisRiverHydrology rivers = dimension.getHydrology().getRivers();
        rivers.setEnabled(true);
        IrisCoastalRiverGrottoConfig coastal = rivers.getGrottos().getCoastal();
        coastal.setEnabled(true);
        coastal.getSeaCaves()
                .setEnabled(true)
                .setMaximumPerTile(5)
                .setMinimumSpacing(200)
                .setMinimumCoastHeight(9)
                .setDepth(20)
                .setSweepJitterDegrees(40D);

        assertEquals(new HydrologyPlannerSettings.SeaCaves(true, 5, 200, 9, 20, 40D), settings(dimension).seaCaves());

        coastal.setEnabled(false);
        assertEquals(new HydrologyPlannerSettings.SeaCaves(false, 5, 200, 9, 20, 40D), settings(dimension).seaCaves());
    }

    @Test
    public void undergroundCascadeRunPerBlockMapsIntoDrops() {
        IrisDimension dimension = new IrisDimension();
        dimension.getHydrology().getRivers().getGeometry().getDrops()
                .setCascadeRunPerBlock(4)
                .setCascadeExponent(2.2D)
                .setMaximumCascadeStep(1)
                .setFlowWidthRatio(0.81D)
                .setMaximumFlowDepth(3)
                .setBasinWidthRatio(2.1D)
                .setMaximumBasinDepth(9)
                .setUndergroundCascadeRunPerBlock(3);

        assertEquals(new HydrologyPlannerSettings.Drops(4, 2.2D, 1, 0.81D, 3, 2.1D, 9, 3), settings(dimension).geometry().drops());
    }

    @Test
    @SuppressWarnings("unchecked")
    public void widestShoreBiomeWidthCoversPolicyShoreWidthAcrossDimensionRegionsAndBiomes() {
        IrisDimension dimension = new IrisDimension().setRegions(new KList<>("valley"));
        dimension.getRiverPolicy().setShoreWidth(4D);

        assertEquals(4D, IrisHydrologyRuntime.widestShoreBiomeWidth(dimension, () -> null, 1.5D), 0D);
        assertEquals(4D, settings(dimension).widestShoreBiomeWidth(), 0D);

        IrisRegion valley = new IrisRegion().setRiverPolicy(new IrisRiverPolicy().setShoreBiomeWidth(6D));
        IrisBiome beach = new IrisBiome().setRiverPolicy(new IrisRiverPolicy().setShoreWidth(9D));
        IrisBiome plain = new IrisBiome();
        IrisData data = mock(IrisData.class);
        ResourceLoader<IrisRegion> regionLoader = mock(ResourceLoader.class);
        ResourceLoader<IrisBiome> biomeLoader = mock(ResourceLoader.class);
        when(data.getRegionLoader()).thenReturn(regionLoader);
        when(data.getBiomeLoader()).thenReturn(biomeLoader);
        when(regionLoader.load("valley")).thenReturn(valley);
        when(biomeLoader.getPossibleKeys()).thenReturn(new String[]{"beach", "plain"});
        when(biomeLoader.loadAll(any(String[].class))).thenReturn(new KList<>(beach, plain));

        assertEquals(9D, IrisHydrologyRuntime.widestShoreBiomeWidth(dimension, () -> data, 1.5D), 0D);

        beach.getRiverPolicy().setShoreWidth(null).setShoreBiomeWidth(11D);
        assertEquals(11D, IrisHydrologyRuntime.widestShoreBiomeWidth(dimension, () -> data, 1.5D), 0D);

        valley.getRiverPolicy().setShoreWidth(12D);
        assertEquals(12D, IrisHydrologyRuntime.widestShoreBiomeWidth(dimension, () -> data, 1.5D), 0D);

        dimension.getRiverPolicy().setShoreWidth(null);
        valley.getRiverPolicy().setShoreWidth(null).setShoreBiomeWidth(null);
        beach.getRiverPolicy().setShoreBiomeWidth(null);
        assertEquals(1.5D, IrisHydrologyRuntime.widestShoreBiomeWidth(dimension, () -> data, 1.5D), 0D);
    }

    @Test
    public void defaultConfigurationMapsToTheDefaultShapingRecords() {
        IrisDimension dimension = new IrisDimension();
        HydrologyPlannerSettings settings = settings(dimension);

        assertEquals(HydrologyPlannerSettings.Banks.defaults(), settings.surface().banks());
        assertEquals(HydrologyPlannerSettings.ChannelShape.of(2D, 0.25D, 0.25D, 16), settings.geometry().surface());
        assertEquals(HydrologyPlannerSettings.ChannelShape.of(2.4D, 0.28D, 0.24D, 11), settings.geometry().underground());
        assertEquals(HydrologyPlannerSettings.ChannelShape.of(2.4D, 0.28D, 0.24D, 11), settings.geometry().grottos());
        assertEquals(HydrologyPlannerSettings.Drops.of(2, 1.4D, 2, 0.45D, 2, 1.8D, 8), settings.geometry().drops());
        assertEquals(1, settings.underground().minimumRockCover());
        assertEquals(1, settings.underground().minimumFloorCover());
        assertEquals(8, settings.underground().wideningSources());
        assertEquals(7, settings.outlets().coastalCliffMinimumHeight());
        assertEquals(0.5D, settings.outlets().coastalCliffSlopeFactor(), 0D);
        assertEquals(25D, settings.seaCaves().sweepJitterDegrees(), 0D);
        assertEquals(1.5D, settings.widestShoreBiomeWidth(), 0D);
    }

    private static HydrologyPlannerSettings settings(IrisDimension dimension) {
        return IrisHydrologyRuntime.createSettings(dimension, dimension.getHydrology(), () -> null);
    }
}
