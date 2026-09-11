package art.arcane.iris.engine.hydrology.runtime;

import art.arcane.iris.engine.hydrology.HydrologyHash;
import art.arcane.iris.engine.hydrology.HydrologyPlannerSettings;
import art.arcane.iris.engine.hydrology.policy.SurfaceRiverPolicy;
import art.arcane.iris.engine.object.IrisBiome;
import art.arcane.iris.engine.object.IrisCoastalRiverGrottoConfig;
import art.arcane.iris.engine.object.IrisDeepFluidConfig;
import art.arcane.iris.engine.object.IrisDimension;
import art.arcane.iris.engine.object.IrisHydrology;
import art.arcane.iris.engine.object.IrisInlandRiverGrottoConfig;
import art.arcane.iris.engine.object.IrisRegion;
import art.arcane.iris.engine.object.IrisRiverChannelShapeConfig;
import art.arcane.iris.engine.object.IrisRiverDropShapeConfig;
import art.arcane.iris.engine.object.IrisRiverGeometryConfig;
import art.arcane.iris.engine.object.IrisRiverGrottoConfig;
import art.arcane.iris.engine.object.IrisRiverHydrology;
import art.arcane.iris.engine.object.IrisRiverInlandOutlet;
import art.arcane.iris.engine.object.IrisRiverMeanderConfig;
import art.arcane.iris.engine.object.IrisRiverMouthConfig;
import art.arcane.iris.engine.object.IrisRiverPolicy;
import art.arcane.iris.engine.object.IrisRiverRoutingConfig;
import art.arcane.iris.engine.object.IrisSeaCaveConfig;
import art.arcane.iris.engine.object.IrisStyledRange;
import art.arcane.iris.engine.object.IrisSurfacePoolConfig;
import art.arcane.iris.engine.object.IrisSurfaceRiverBankConfig;
import art.arcane.iris.engine.object.IrisSurfaceRiverChannelConfig;
import art.arcane.iris.engine.object.IrisSurfaceRiverConfig;
import art.arcane.iris.engine.object.IrisSurfaceRiverErosionConfig;
import art.arcane.iris.engine.object.IrisSurfaceRiverFlowConfig;
import art.arcane.iris.engine.object.IrisSurfaceRiverPondConfig;
import art.arcane.iris.engine.object.IrisSurfaceRiverShapeConfig;
import art.arcane.iris.engine.object.IrisSurfaceRiverSourceConfig;
import art.arcane.iris.engine.object.IrisUndergroundRiverConfig;
import art.arcane.iris.engine.object.IrisUndergroundRiverSourceConfig;
import art.arcane.iris.util.common.data.DataProvider;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

final class IrisHydrologySettingsCompiler {
    private IrisHydrologySettingsCompiler() {
    }

    static HydrologyPlannerSettings compile(
            IrisDimension dimension,
            IrisHydrology hydrology,
            DataProvider data
    ) {
        IrisRiverHydrology rivers = hydrology.getRivers();
        IrisRiverRoutingConfig routing = rivers.getRouting();
        IrisSurfaceRiverConfig surface = rivers.getSurface();
        IrisSurfaceRiverSourceConfig surfaceSources = surface.getSources();
        IrisSurfaceRiverChannelConfig channel = surface.getChannel();
        IrisSurfaceRiverBankConfig banks = surface.getBanks();
        IrisSurfaceRiverErosionConfig erosion = surface.getErosion();
        IrisSurfaceRiverFlowConfig flow = surface.getFlow();
        IrisRiverMouthConfig mouths = surface.getMouths();
        IrisUndergroundRiverConfig underground = rivers.getUnderground();
        IrisUndergroundRiverSourceConfig undergroundSources = underground.getSources();
        int routeNodes = maximumRouteNodes(routing);
        int minimumWorldY = dimension.getMinHeight();
        boolean riversEnabled = rivers.isEnabled();
        boolean surfaceEnabled = riversEnabled && surface.isEnabled();
        boolean undergroundEnabled = riversEnabled && underground.isEnabled();
        HydrologyPlannerSettings.Routing plannerRouting = new HydrologyPlannerSettings.Routing(
                routing.getTileSize(),
                routing.getSampleSpacing(),
                routeNodes,
                routing.getMaximumRouteLength(),
                routing.getMinimumSurfaceCourseLength(),
                routing.getMinimumUndergroundCourseLength(),
                routing.getValleyPreference(),
                routing.getUphillPenalty(),
                routing.getSlopePenalty(),
                routing.getConfluenceAttraction(),
                routing.getLengthPreference(),
                routing.getTributaries(),
                new HydrologyPlannerSettings.Regional(
                        routing.getRegional().isEnabled(),
                        routing.getRegional().getSampleSpacing(),
                        routing.getRegional().getMinimumLength(),
                        routing.getRegional().getMaximumTrunks(),
                        routing.getRegional().getMaximumCachedBasins(),
                        routing.getRegional().getMaximumCachedStations(),
                        routing.getRegional().isCoastalChannels(),
                        routing.getRegional().getCoastalChannelChance(),
                        routing.getRegional().getMaximumCoastalIncision()
                )
        );
        HydrologyPlannerSettings.Source plannerSurfaceSources = new HydrologyPlannerSettings.Source(
                surfaceEnabled,
                surfaceSources.getDensity(),
                surfaceSources.getMinimumElevation() - minimumWorldY,
                surfaceSources.getMinimumPerTile(),
                maximumSources(surfaceSources.getDensity(), surfaceSources.getMinimumPerTile()),
                surfaceSources.getMinimumSpacing()
        );
        HydrologyPlannerSettings.Surface plannerSurface = new HydrologyPlannerSettings.Surface(
                surfaceEnabled,
                plannerSurfaceSources,
                minimumInt(channel.getWidth()),
                maximumInt(channel.getWidth()),
                minimumInt(channel.getDepth()),
                maximumInt(channel.getDepth()),
                channel.getMaximumIncision(),
                banks.getShoreWidth(),
                new HydrologyPlannerSettings.Banks(
                        channel.getSink(),
                        banks.getBlendSlope(),
                        banks.getMinimumBlendWidth(),
                        banks.getMaximumBlendWidth(),
                        channel.getRoughness(),
                        channel.getRoughnessWavelength(),
                        flow.getCascadeRun(),
                        flow.getWaterfallMinimumDrop(),
                        mouths.getFlareRatio(),
                        new HydrologyPlannerSettings.Inlet(
                                mouths.getInletLength(),
                                mouths.getInletDepth(),
                                mouths.getMaximumIncision(),
                                mouths.getInletCourseFraction(),
                                mouths.getInletRampSlope()
                        ),
                        channel.getSpringWidthRatio(),
                        channel.getSpringLength(),
                        banks.isExposeCutStrata(),
                        new HydrologyPlannerSettings.Erosion(
                                erosion.isEnabled(),
                                erosion.getSmoothingRadius(),
                                erosion.getThalwegFraction(),
                                erosion.getBlendCurve(),
                                erosion.getBedNoise(),
                                erosion.getStyle(),
                                erosion.getTerraceSteps(),
                                erosion.getCliffFraction(),
                                erosion.getBedProfile(),
                                banks.getShoreRise(),
                                banks.getBlendBaseWidth(),
                                new HydrologyPlannerSettings.Excavation(
                                        banks.getExcavation().getMaximumDepth(),
                                        banks.getExcavation().getMaximumWidth(),
                                        banks.getExcavation().getMaximumVolumePerBlock()
                                )
                        ),
                        new HydrologyPlannerSettings.Ponds(
                                pond(surface.getPonds().getSource()),
                                pond(surface.getPonds().getTerminal())
                        ),
                        new HydrologyPlannerSettings.Channel(
                                channel.getSmoothingRadius(),
                                channel.getOutlineMinimumRatio(),
                                channel.getOutlineMaximumRatio(),
                                channel.getSpringExtraDepth()
                        ),
                        new HydrologyPlannerSettings.Flow(
                                flow.getWaterfallThalwegFraction(),
                                flow.getPlungeBasinMinimumDrop(),
                                flow.getPlungeBasinLengthRatio(),
                                flow.getPlungeBasinDepth()
                        )
                )
        );
        HydrologyPlannerSettings.Hydraulics plannerHydraulics =
                new HydrologyPlannerSettings.Hydraulics(flow.getWaterfallMinimumDrop());
        HydrologyPlannerSettings.Source plannerUndergroundSources = new HydrologyPlannerSettings.Source(
                undergroundEnabled,
                undergroundSources.getDensity(),
                Integer.MIN_VALUE,
                undergroundSources.getMinimumPerTile(),
                maximumSources(undergroundSources.getDensity(), undergroundSources.getMinimumPerTile()),
                undergroundSources.getMinimumSpacing()
        );
        HydrologyPlannerSettings.Underground plannerUnderground = new HydrologyPlannerSettings.Underground(
                undergroundEnabled,
                plannerUndergroundSources,
                minimumInt(underground.getFluidLevel()) - minimumWorldY,
                maximumInt(underground.getFluidLevel()) - minimumWorldY,
                minimumInt(underground.getChannelWidth()),
                maximumInt(underground.getChannelWidth()),
                minimumInt(underground.getDepth()),
                maximumInt(underground.getDepth()),
                minimumInt(underground.getHeadroom()),
                maximumInt(underground.getHeadroom()),
                underground.isConnectToExistingCaves(),
                underground.getTributaries(),
                underground.getMinimumRockCover(),
                underground.getMinimumFloorCover(),
                underground.getWideningSources()
        );
        IrisRiverGrottoConfig grottos = rivers.getGrottos();
        IrisCoastalRiverGrottoConfig coastal = grottos.getCoastal();
        IrisInlandRiverGrottoConfig inland = grottos.getInland();
        boolean inlandEnabled = inland.isEnabled()
                && routing.getInlandOutlets().contains(IrisRiverInlandOutlet.SINKHOLE_GROTTO);
        HydrologyPlannerSettings.Outlets plannerOutlets = new HydrologyPlannerSettings.Outlets(
                routing.isOceanOutlets(),
                grotto(coastal.isEnabled(), coastal.getHorizontalRadius(), coastal.getVerticalRadius(),
                        coastal.getHeadroom(), coastal.getMaximumVolume()),
                grotto(inlandEnabled, inland.getHorizontalRadius(), inland.getVerticalRadius(),
                        inland.getHeadroom(), inland.getMaximumVolume()),
                inlandEnabled && inland.isConnectSurfaceRivers(),
                coastal.getCliffMinimumHeight() == null
                        ? Math.max(4, coastal.getVerticalRadius())
                        : coastal.getCliffMinimumHeight(),
                underground.getMouthLevelingDistance(),
                mouths.getMaximumOceanApron(),
                routing.getMaximumOutletsPerTile(),
                routing.getMaximumCoastalOutletsPerTile(),
                coastal.getCliffSlopeFactor()
        );
        HydrologyPlannerSettings.Geometry plannerGeometry = geometry(
                rivers.getGeometry(),
                surfaceShape(rivers.getGeometry().getSurface(), channel)
        );
        return new HydrologyPlannerSettings(
                dimension.getFluidHeight(),
                plannerRouting,
                plannerSurface,
                plannerHydraulics,
                plannerUnderground,
                plannerOutlets,
                plannerGeometry,
                deepFluids(hydrology.getDeepFluids(), minimumWorldY, routing, plannerRouting.refinementSpacing()),
                surfacePools(hydrology.getSurfacePools()),
                widestShoreBiomeWidth(dimension, data, banks.getShoreWidth()),
                seaCaves(coastal),
                surfacePolicyBounds(dimension, data)
        );
    }

    static HydrologyPlannerSettings.SurfacePolicyBounds surfacePolicyBounds(IrisDimension dimension, DataProvider data) {
        TreeMap<String, SurfaceRiverPolicy> policies = new TreeMap<>();
        addSurfacePolicy(policies, "dimension", dimension.getRiverPolicy());
        for (IrisRegion region : dimension.getAllRegions(data)) {
            addSurfacePolicy(policies, "region:" + region.getLoadKey(), region.getRiverPolicy());
        }
        for (IrisBiome biome : dimension.getAllBiomes(data)) {
            addSurfacePolicy(policies, "biome:" + biome.getLoadKey(), biome.getRiverPolicy());
        }
        long fingerprint = 0L;
        int maximumSpacing = 0;
        for (SurfaceRiverPolicy policy : policies.values()) {
            fingerprint = HydrologyHash.mix(fingerprint, policy.hashCode(), HydrologyHash.text(policy.areaKey()));
            maximumSpacing = Math.max(maximumSpacing, policy.sourceSpacing(0));
        }
        return new HydrologyPlannerSettings.SurfacePolicyBounds(fingerprint, maximumSpacing);
    }

    private static void addSurfacePolicy(Map<String, SurfaceRiverPolicy> policies, String key, IrisRiverPolicy policy) {
        if (policy == null) {
            return;
        }
        SurfaceRiverPolicy surface = policy.surfacePolicy(key);
        if (surface.configured()) {
            policies.put(key, surface);
        }
    }

    /**
     * The widest shore band any policy of the dimension asks for, whether as a shore biome band or as a
     * geometric shore width, never below the dimension's geometric shore width, so the publication
     * envelope reaches every column a river can give shore content to.
     */
    static double widestShoreBiomeWidth(IrisDimension dimension, DataProvider data, double shoreWidth) {
        double widest = Math.max(0D, shoreWidth);
        widest = Math.max(widest, policyShoreReach(dimension.getRiverPolicy()));
        for (IrisRegion region : dimension.getAllRegions(data)) {
            widest = Math.max(widest, policyShoreReach(region.getRiverPolicy()));
        }
        for (IrisBiome biome : dimension.getAllBiomes(data)) {
            widest = Math.max(widest, policyShoreReach(biome.getRiverPolicy()));
        }
        return widest;
    }

    private static double policyShoreReach(IrisRiverPolicy policy) {
        if (policy == null) {
            return 0D;
        }
        double shoreBiomeWidth = policy.getShoreBiomeWidth() == null ? 0D : policy.getShoreBiomeWidth();
        double shoreWidth = policy.getShoreWidth() == null ? 0D : policy.getShoreWidth();
        return Math.max(shoreBiomeWidth, shoreWidth);
    }

    private static List<HydrologyPlannerSettings.SurfacePool> surfacePools(List<IrisSurfacePoolConfig> configurations) {
        ArrayList<HydrologyPlannerSettings.SurfacePool> pools = new ArrayList<>();
        for (IrisSurfacePoolConfig configuration : configurations) {
            pools.add(new HydrologyPlannerSettings.SurfacePool(
                    configuration.getId(),
                    configuration.getDensity() > 0D,
                    configuration.getDensity(),
                    configuration.getSpacing(),
                    configuration.getMinimumRadius(),
                    configuration.getMaximumRadius(),
                    configuration.getDepth(),
                    Math.min(64, maximumSources(configuration.getDensity(), 0)),
                    configuration.getBiome()
            ));
        }
        return List.copyOf(pools);
    }

    private static HydrologyPlannerSettings.Geometry geometry(
            IrisRiverGeometryConfig geometry,
            HydrologyPlannerSettings.ChannelShape surfaceShape
    ) {
        IrisRiverMeanderConfig meanders = geometry.getMeanders();
        IrisRiverDropShapeConfig drops = geometry.getDrops();
        return new HydrologyPlannerSettings.Geometry(
                new HydrologyPlannerSettings.Meanders(
                        meanders.getPrimaryWavelength(),
                        meanders.getDetailWavelength(),
                        meanders.getPrimaryStrength(),
                        meanders.getDetailStrength(),
                        meanders.getMaximumOffsetRatio(),
                        meanders.getSmoothingPasses(),
                        meanders.getMaximumTurnDegrees()
                ),
                surfaceShape,
                channelShape(geometry.getUnderground()),
                channelShape(geometry.getGrottos()),
                new HydrologyPlannerSettings.Drops(
                        drops.getCascadeRunPerBlock(),
                        drops.getCascadeExponent(),
                        drops.getMaximumCascadeStep(),
                        drops.getFlowWidthRatio(),
                        drops.getMaximumFlowDepth(),
                        drops.getBasinWidthRatio(),
                        drops.getMaximumBasinDepth(),
                        drops.getUndergroundCascadeRunPerBlock()
                )
        );
    }

    private static HydrologyPlannerSettings.ChannelShape channelShape(IrisRiverChannelShapeConfig shape) {
        return new HydrologyPlannerSettings.ChannelShape(
                shape.getBedRoundness(),
                shape.getBedRoughness(),
                shape.getWallRoughness(),
                shape.getRoughnessWavelength(),
                shape.getRadialBase(),
                shape.getRadialMinimum(),
                shape.getRadialMaximum(),
                shape.getPrimaryLobeStrength(),
                shape.getDetailLobeStrength(),
                shape.getCeilingRoughness(),
                shape.getAspectMinimum(),
                shape.getAspectRange()
        );
    }

    // The surface shape's roughness fields are optional and fall back to the channel roughness.
    private static HydrologyPlannerSettings.ChannelShape surfaceShape(
            IrisSurfaceRiverShapeConfig shape,
            IrisSurfaceRiverChannelConfig channel
    ) {
        return new HydrologyPlannerSettings.ChannelShape(
                shape.getBedRoundness(),
                shape.getBedRoughness() == null ? channel.getRoughness() : shape.getBedRoughness(),
                shape.getWallRoughness() == null ? channel.getRoughness() : shape.getWallRoughness(),
                shape.getRoughnessWavelength() == null ? channel.getRoughnessWavelength() : shape.getRoughnessWavelength(),
                shape.getRadialBase(),
                shape.getRadialMinimum(),
                shape.getRadialMaximum(),
                shape.getPrimaryLobeStrength(),
                shape.getDetailLobeStrength(),
                shape.getCeilingRoughness(),
                shape.getAspectMinimum(),
                shape.getAspectRange()
        );
    }

    private static HydrologyPlannerSettings.Grotto grotto(
            boolean enabled,
            int horizontalRadius,
            int verticalRadius,
            int headroom,
            int maximumVolume
    ) {
        return new HydrologyPlannerSettings.Grotto(
                enabled,
                horizontalRadius,
                verticalRadius,
                headroom,
                maximumVolume
        );
    }

    // A sea cave is a coastal grotto without a river, so it needs the coastal grotto itself enabled.
    private static HydrologyPlannerSettings.SeaCaves seaCaves(IrisCoastalRiverGrottoConfig coastal) {
        IrisSeaCaveConfig seaCaves = coastal.getSeaCaves();
        return new HydrologyPlannerSettings.SeaCaves(
                coastal.isEnabled() && seaCaves.isEnabled(),
                seaCaves.getMaximumPerTile(),
                seaCaves.getMinimumSpacing(),
                seaCaves.getMinimumCoastHeight(),
                seaCaves.getDepth(),
                seaCaves.getSweepJitterDegrees()
        );
    }

    private static List<HydrologyPlannerSettings.DeepFluid> deepFluids(
            List<IrisDeepFluidConfig> configurations,
            int minimumWorldY,
            IrisRiverRoutingConfig routing,
            int refinementSpacing
    ) {
        ArrayList<HydrologyPlannerSettings.DeepFluid> deepFluids = new ArrayList<>();
        for (IrisDeepFluidConfig configuration : configurations) {
            int radius = configuration.getHorizontalRadius();
            int verticalRadius = configuration.getVerticalRadius();
            int maximumVolume = boundedVolume(radius, verticalRadius, configuration.getHeadroom());
            int maximumPerTile = Math.min(64, maximumSources(configuration.getDensity(), 0));
            int maximumChannelLength = maximumDeepChannelLength(configuration, routing);
            int minimumChannelLength = maximumChannelLength == 0
                    ? 0
                    : Math.min(maximumChannelLength, Math.max(refinementSpacing, radius));
            deepFluids.add(new HydrologyPlannerSettings.DeepFluid(
                    configuration.getId(),
                    configuration.getDensity() > 0D
                            && (configuration.isContainedPools() || configuration.isShortChannels()),
                    configuration.getDensity(),
                    configuration.getSpacing(),
                    minimumInt(configuration.getHeight()) - minimumWorldY,
                    maximumInt(configuration.getHeight()) - minimumWorldY,
                    radius,
                    radius,
                    verticalRadius,
                    verticalRadius,
                    minimumChannelLength,
                    maximumChannelLength,
                    configuration.getChannelWidth(),
                    configuration.getDepth(),
                    configuration.getHeadroom(),
                    maximumVolume,
                    maximumPerTile,
                    configuration.isContainedPools(),
                    configuration.isShortChannels()
            ));
        }
        return List.copyOf(deepFluids);
    }

    static int maximumDeepChannelLength(
            IrisDeepFluidConfig configuration,
            IrisRiverRoutingConfig routing
    ) {
        if (!configuration.isShortChannels()) {
            return 0;
        }
        int configuredLength = Math.max(
                HydrologyPlannerSettings.Routing.refinementSpacing(routing.getSampleSpacing()),
                configuration.getSpacing() / 3);
        return Math.min(configuredLength, routing.getTileSize() / 2);
    }

    private static int maximumRouteNodes(IrisRiverRoutingConfig routing) {
        int halo = Math.min(routing.getMaximumRouteLength(), routing.getTileSize() / 2);
        int alignedHalo = Math.floorDiv(halo, routing.getSampleSpacing()) * routing.getSampleSpacing();
        int width = routing.getTileSize() / routing.getSampleSpacing() + 1
                + alignedHalo * 2 / routing.getSampleSpacing();
        long nodes = (long) width * width;
        if (nodes > 1_000_000L) {
            throw new IllegalArgumentException("Hydrology routing lattice exceeds one million nodes.");
        }
        return (int) nodes;
    }

    private static int maximumSources(double density, int minimumPerTile) {
        int expected = (int) Math.ceil(Math.max(0D, density));
        return Math.min(64, Math.max(minimumPerTile, expected));
    }

    private static int boundedVolume(int radius, int verticalRadius, int headroom) {
        long diameter = radius * 2L + 1L;
        long volume = diameter * diameter * (verticalRadius + headroom + 1L);
        return (int) Math.min(1_048_576L, Math.max(64L, volume));
    }

    private static HydrologyPlannerSettings.Pond pond(IrisSurfaceRiverPondConfig config) {
        return new HydrologyPlannerSettings.Pond(
                config.isEnabled(),
                config.getMinimumRadius(),
                config.getMaximumRadius(),
                config.getDepth()
        );
    }

    private static int minimumInt(IrisStyledRange range) {
        return (int) StrictMath.floor(Math.min(range.getMin(), range.getMax()));
    }

    private static int maximumInt(IrisStyledRange range) {
        return (int) StrictMath.ceil(Math.max(range.getMin(), range.getMax()));
    }
}
