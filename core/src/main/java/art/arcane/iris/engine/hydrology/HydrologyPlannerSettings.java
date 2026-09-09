package art.arcane.iris.engine.hydrology;

import art.arcane.iris.engine.object.IrisRiverBedProfile;
import art.arcane.iris.engine.object.IrisRiverBlendStyle;

import java.util.List;
import java.util.Objects;

public record HydrologyPlannerSettings(
        int seaLevel,
        Routing routing,
        Surface surface,
        Hydraulics hydraulics,
        Underground underground,
        Outlets outlets,
        Geometry geometry,
        List<DeepFluid> deepFluids,
        List<SurfacePool> surfacePools,
        double widestShoreBiomeWidth,
        SeaCaves seaCaves,
        SurfacePolicyBounds surfacePolicyBounds
) {
    private static final long PLAN_FORMAT_REVISION = 6L;
    private static final int MAXIMUM_CROSS_TILE_COLOR_PERIOD = 4;

    public HydrologyPlannerSettings {
        if (seaLevel < -2048 || seaLevel > 2048) {
            throw new IllegalArgumentException("seaLevel is outside the supported planning range.");
        }
        if (routing == null || surface == null || hydraulics == null || underground == null
                || outlets == null || geometry == null) {
            throw new IllegalArgumentException("Hydrology planner settings cannot contain null sections.");
        }
        deepFluids = deepFluids == null ? List.of() : List.copyOf(deepFluids);
        surfacePools = surfacePools == null ? List.of() : List.copyOf(surfacePools);
        if (!Double.isFinite(widestShoreBiomeWidth) || widestShoreBiomeWidth < 0D) {
            throw new IllegalArgumentException("widestShoreBiomeWidth must be finite and non-negative.");
        }
        if (seaCaves == null) {
            throw new IllegalArgumentException("Sea cave settings are required.");
        }
        surfacePolicyBounds = Objects.requireNonNull(surfacePolicyBounds);
        int publicationRadius = Math.max(surfacePolicyBounds.maximumSourceSpacing(),
                publicationRadius(routing, surface, underground, outlets, geometry, deepFluids, surfacePools, widestShoreBiomeWidth, seaCaves));
        if (crossTileColorPeriod(publicationRadius, routing.tileSize()) > MAXIMUM_CROSS_TILE_COLOR_PERIOD) {
            throw new IllegalArgumentException("Hydrology publication envelope exceeds the bounded cross-tile admission period.");
        }
    }

    public static HydrologyPlannerSettings defaults() {
        Source surfaceSources = new Source(true, 0.5D, 88, 0, 1, 384);
        Source undergroundSources = new Source(true, 0.25D, Integer.MIN_VALUE, 0, 1, 512);
        return new HydrologyPlannerSettings(
                63,
                new Routing(2048, 64, 8192, 8192, 384, 192, 1.5D, 24D, 2D, 0.2D, 1D, 0),
                new Surface(true, surfaceSources, 4, 8, 2, 4, 10, 1.5D, Banks.defaults()),
                new Hydraulics(8),
                Underground.of(true, undergroundSources, -48, 72, 3, 8, 1, 3, 6, 14, true, 1),
                Outlets.of(
                        true,
                        new Grotto(true, 18, 8, 8, 32768),
                        new Grotto(true, 18, 8, 8, 32768),
                        false,
                        12,
                        64,
                        8,
                        12,
                        2
                ),
                Geometry.defaults(),
                List.of(),
                List.of(),
                0D,
                SeaCaves.disabled(),
                HydrologyPlannerSettings.SurfacePolicyBounds.NONE
        );
    }

    public long fingerprint() {
        long hash = HydrologyHash.mix(
                0x485944524f4c4f47L,
                PLAN_FORMAT_REVISION,
                routing.hashCode(),
                surface.hashCode(),
                hydraulics.hashCode(),
                underground.hashCode(),
                outlets.hashCode(),
                geometry.hashCode(),
                Double.doubleToLongBits(widestShoreBiomeWidth),
                seaCaves.hashCode()
        );
        for (DeepFluid deepFluid : deepFluids) {
            hash = HydrologyHash.mix(hash, deepFluid.hashCode(), HydrologyHash.text(deepFluid.id()));
        }
        for (SurfacePool pool : surfacePools) {
            hash = HydrologyHash.mix(hash, pool.hashCode(), HydrologyHash.text(pool.id()));
        }
        if (surfacePolicyBounds.fingerprint() != 0L) {
            hash = HydrologyHash.mix(hash, surfacePolicyBounds.fingerprint(), surfacePolicyBounds.maximumSourceSpacing());
        }
        return hash;
    }

    public int publicationRadius() {
        return Math.max(surfacePolicyBounds.maximumSourceSpacing(),
                publicationRadius(routing, surface, underground, outlets, geometry, deepFluids, surfacePools, widestShoreBiomeWidth, seaCaves));
    }

    public int maximumSurfaceSourceSpacing() {
        return Math.max(surface.sources().minimumSpacing(), surfacePolicyBounds.maximumSourceSpacing());
    }

    public record SurfacePolicyBounds(long fingerprint, int maximumSourceSpacing) {
        public static final SurfacePolicyBounds NONE = new SurfacePolicyBounds(0L, 0);

        public SurfacePolicyBounds {
            if (maximumSourceSpacing < 0 || maximumSourceSpacing > 8192) {
                throw new IllegalArgumentException("Maximum surface policy source spacing must be between 0 and 8192.");
            }
        }
    }

    int crossTileColorPeriod() {
        return crossTileColorPeriod(publicationRadius(), routing.tileSize());
    }

    private static int publicationRadius(
            Routing routing,
            Surface surface,
            Underground underground,
            Outlets outlets,
            Geometry geometry,
            List<DeepFluid> deepFluids,
            List<SurfacePool> surfacePools,
            double widestShoreBiomeWidth,
            SeaCaves seaCaves
    ) {
        double shoreReach = Math.max(surface.shoreWidth(), widestShoreBiomeWidth);
        int alignedHalo = Math.floorDiv(
                Math.min(
                        routing.maximumRouteLength(),
                        Math.multiplyExact(routing.sampleSpacing(), 2)
                ),
                routing.sampleSpacing()
        ) * routing.sampleSpacing();
        int radius = 0;
        int routeDisplacement = Math.multiplyExact(routing.sampleSpacing(), 3);
        if (surface.enabled() && surface.sources().enabled()) {
            int blendWidth = surface.banks().maximumBlendWidth();
            int surfaceRadius = (int) StrictMath.ceil(
                    surface.maximumWidth() * surface.banks().mouthFlareRatio() / 2D + shoreReach + blendWidth
            );
            surfaceRadius = Math.max(
                    surfaceRadius,
                    (int) StrictMath.ceil(geometry.drops().basinWidth(
                            geometry.drops().flowWidth(surface.maximumWidth())
                    ) / 2D + shoreReach + blendWidth)
            );
            surfaceRadius = Math.max(surfaceRadius, outlets.coastalGrotto().horizontalRadius());
            surfaceRadius = Math.max(surfaceRadius, outlets.inlandGrotto().horizontalRadius());
            radius = Math.max(radius, Math.addExact(alignedHalo, Math.addExact(surfaceRadius, routeDisplacement)));
        }
        if (underground.enabled() && underground.sources().enabled()) {
            int undergroundRadius = (int) StrictMath.ceil(underground.maximumWidth() / 2D);
            undergroundRadius = Math.max(
                    undergroundRadius,
                    (int) StrictMath.ceil(geometry.drops().basinWidth(
                            geometry.drops().flowWidth(underground.maximumWidth())
                    ) / 2D)
            );
            undergroundRadius = Math.max(undergroundRadius, outlets.coastalGrotto().horizontalRadius());
            undergroundRadius = Math.max(undergroundRadius, outlets.inlandGrotto().horizontalRadius());
            radius = Math.max(radius, Math.addExact(alignedHalo, Math.addExact(undergroundRadius, routeDisplacement)));
        }
        for (DeepFluid deepFluid : deepFluids) {
            if (!deepFluid.enabled() || deepFluid.maximumPerTile() == 0) {
                continue;
            }
            int poolReach = deepFluid.containedPools() ? deepFluid.maximumHorizontalRadius() : 0;
            int channelReach = deepFluid.shortChannels()
                    ? Math.addExact(deepFluid.maximumChannelLength(), (int) StrictMath.ceil(deepFluid.channelWidth() / 2D))
                    : 0;
            radius = Math.max(radius, Math.max(poolReach, channelReach));
        }
        for (SurfacePool pool : surfacePools) {
            if (!pool.enabled()) {
                continue;
            }
            radius = Math.max(radius, pool.maximumRadius() + (int) StrictMath.ceil(shoreReach)
                    + surface.banks().maximumBlendWidth());
        }
        if (seaCaves.enabled() && seaCaves.maximumPerTile() > 0) {
            // A chamber hangs off a shoreline that may lie a halo away, swept inland by its depth.
            radius = Math.max(radius, Math.addExact(alignedHalo, Math.addExact(
                    outlets.coastalGrotto().horizontalRadius(),
                    Math.addExact(seaCaves.depth(), 1)
            )));
        }
        return radius;
    }

    private static int crossTileColorPeriod(int publicationRadius, int tileSize) {
        long actionReach = Math.addExact((long) publicationRadius, 1L);
        return Math.toIntExact(Math.addExact(Math.floorDiv(Math.multiplyExact(2L, actionReach), tileSize), 2L));
    }

    public record Routing(
            int tileSize,
            int sampleSpacing,
            int maximumRouteNodes,
            int maximumRouteLength,
            int minimumSurfaceCourseLength,
            int minimumUndergroundCourseLength,
            double valleyPreference,
            double uphillPenalty,
            double slopePenalty,
            double confluenceAttraction,
            double lengthPreference,
            int tributaries
    ) {
        public Routing {
            if (tileSize < 32 || sampleSpacing < 4 || tileSize % sampleSpacing != 0) {
                throw new IllegalArgumentException("Routing sizes must form an exact bounded tile lattice.");
            }
            int latticeWidth = tileSize / sampleSpacing + 1;
            int halo = Math.min(maximumRouteLength, Math.multiplyExact(sampleSpacing, 2));
            int alignedHalo = Math.floorDiv(halo, sampleSpacing) * sampleSpacing;
            int expandedWidth = latticeWidth + alignedHalo * 2 / sampleSpacing;
            long latticeNodes = (long) expandedWidth * expandedWidth;
            if (maximumRouteNodes < latticeNodes || maximumRouteNodes > 1_000_000 || maximumRouteLength < 1) {
                throw new IllegalArgumentException("maximumRouteNodes must contain the complete lattice and remain bounded.");
            }
            if (minimumSurfaceCourseLength < 0 || minimumSurfaceCourseLength > 32_768
                    || minimumUndergroundCourseLength < 0 || minimumUndergroundCourseLength > 32_768) {
                throw new IllegalArgumentException("Hydrology course lengths are invalid.");
            }
            if (minimumSurfaceCourseLength > maximumRouteLength
                    || minimumUndergroundCourseLength > maximumRouteLength) {
                throw new IllegalArgumentException("Minimum course lengths cannot exceed maximum route length.");
            }
            requireFiniteNonNegative(valleyPreference, "valleyPreference");
            requireFiniteNonNegative(uphillPenalty, "uphillPenalty");
            requireFiniteNonNegative(slopePenalty, "slopePenalty");
            requireFiniteNonNegative(confluenceAttraction, "confluenceAttraction");
            requireFiniteNonNegative(lengthPreference, "lengthPreference");
            if (tributaries < 0) {
                throw new IllegalArgumentException("tributaries must not be negative.");
            }
        }

        // Route refinement is derived from the lattice, never authored.
        public static int refinementSpacing(int sampleSpacing) {
            if (sampleSpacing % 4 == 0) {
                return 4;
            }
            return sampleSpacing % 2 == 0 ? 2 : 1;
        }

        public int refinementSpacing() {
            return refinementSpacing(sampleSpacing);
        }

        public int minimumCourseLength(boolean surface) {
            return surface ? minimumSurfaceCourseLength : minimumUndergroundCourseLength;
        }
    }

    public record Source(
            boolean enabled,
            double density,
            int minimumElevation,
            int minimumPerTile,
            int maximumPerTile,
            int minimumSpacing
    ) {
        public Source {
            if (!Double.isFinite(density) || density < 0D || density > 64D) {
                throw new IllegalArgumentException("Source density must be between 0 and 64 expected sources per tile.");
            }
            if (minimumPerTile < 0 || maximumPerTile < minimumPerTile || maximumPerTile > 4096) {
                throw new IllegalArgumentException("Source tile quotas are invalid.");
            }
            if (minimumSpacing < 0) {
                throw new IllegalArgumentException("Source spacing cannot be negative.");
            }
        }
    }

    public record Surface(
            boolean enabled,
            Source sources,
            int minimumWidth,
            int maximumWidth,
            int minimumDepth,
            int maximumDepth,
            int maximumIncision,
            double shoreWidth,
            Banks banks
    ) {
        public Surface {
            if (sources == null || banks == null) {
                throw new IllegalArgumentException("Surface source and bank settings are required.");
            }
            requireRange(minimumWidth, maximumWidth, 1, 256, "surface width");
            requireRange(minimumDepth, maximumDepth, 1, 64, "surface depth");
            if (maximumIncision < 0 || !Double.isFinite(shoreWidth) || shoreWidth < 0D || shoreWidth > 32D) {
                throw new IllegalArgumentException("Surface incision and shore width are invalid.");
            }
        }
    }

    /**
     * {@code sink} is how many blocks the water surface sits below the lowest natural ground beside
     * the channel; zero keeps the water flush with the bank and the bank top always meets the water.
     * {@code channel} shapes the wet outline and {@code flow} the waterfalls and plunge basins.
     */
    public record Banks(
            int sink,
            double blendSlope,
            int minimumBlendWidth,
            int maximumBlendWidth,
            double roughness,
            int roughnessWavelength,
            int cascadeRun,
            int waterfallMinimumDrop,
            double mouthFlareRatio,
            Inlet inlet,
            double springWidthRatio,
            int springLength,
            boolean exposeCutStrata,
            Erosion erosion,
            Ponds ponds,
            Channel channel,
            Flow flow
    ) {
        // Structural invariants only; authoring bounds live in the pack validator.
        public Banks {
            if (erosion == null || ponds == null || inlet == null || channel == null || flow == null) {
                throw new IllegalArgumentException("Surface erosion, inlet, pond, channel and flow settings are required.");
            }
            if (sink < 0
                    || !Double.isFinite(blendSlope) || blendSlope <= 0D
                    || minimumBlendWidth < 1 || maximumBlendWidth < minimumBlendWidth
                    || !Double.isFinite(roughness) || roughness < 0D || roughness > 1D
                    || roughnessWavelength < 1
                    || cascadeRun < 1
                    || waterfallMinimumDrop < 1
                    || !Double.isFinite(mouthFlareRatio) || mouthFlareRatio < 1D
                    || !Double.isFinite(springWidthRatio) || springWidthRatio < 1D
                    || springLength < 1) {
                throw new IllegalArgumentException("Surface bank settings are invalid.");
            }
        }

        public static Banks defaults() {
            return new Banks(0, 3D, 4, 32, 0.25D, 16, 2, 6, 1.6D, Inlet.defaults(), 2.5D, 24, true, Erosion.defaults(),
                    Ponds.defaults(), Channel.defaults(), Flow.defaults());
        }

        /** The bank settings before channel and flow shaping existed; both take their defaults. */
        public static Banks of(
                int sink,
                double blendSlope,
                int minimumBlendWidth,
                int maximumBlendWidth,
                double roughness,
                int roughnessWavelength,
                int cascadeRun,
                int waterfallMinimumDrop,
                double mouthFlareRatio,
                Inlet inlet,
                double springWidthRatio,
                int springLength,
                boolean exposeCutStrata,
                Erosion erosion,
                Ponds ponds
        ) {
            return new Banks(sink, blendSlope, minimumBlendWidth, maximumBlendWidth, roughness, roughnessWavelength,
                    cascadeRun, waterfallMinimumDrop, mouthFlareRatio, inlet, springWidthRatio, springLength,
                    exposeCutStrata, erosion, ponds, Channel.defaults(), Flow.defaults());
        }

        public Banks withInlet(Inlet inlet) {
            return new Banks(sink, blendSlope, minimumBlendWidth, maximumBlendWidth, roughness, roughnessWavelength,
                    cascadeRun, waterfallMinimumDrop, mouthFlareRatio, inlet, springWidthRatio, springLength,
                    exposeCutStrata, erosion, ponds, channel, flow);
        }
    }

    /**
     * The drowned reach where a surface river meets the sea. Over the last {@code length} blocks before
     * the coast the water is held at sea level, the channel widens toward the mouth flare and its bed
     * deepens by {@code depth}; the stations above it grade down {@code rampSlope} blocks per station into
     * the inlet. The inlet and its approach may be cut up to {@code maximumIncision} deep instead of the
     * channel cap, so a coastal rise no longer rejects the course; a rise the cap cannot pass ends the
     * inlet there, and the inlet never takes more than {@code courseFraction} of the exposed course. A
     * zero length is the plain crossing: the head drops to sea level at the coast, the channel neither
     * widens nor deepens.
     */
    public record Inlet(
            int length,
            int depth,
            int maximumIncision,
            double courseFraction,
            double rampSlope
    ) {
        public Inlet {
            if (length < 0 || length > 1024 || depth < 0 || depth > 64 || maximumIncision < 0 || maximumIncision > 512) {
                throw new IllegalArgumentException("Surface inlet settings are invalid.");
            }
            if (!Double.isFinite(courseFraction) || courseFraction <= 0D || courseFraction > 1D
                    || !Double.isFinite(rampSlope) || rampSlope <= 0D) {
                throw new IllegalArgumentException("Surface inlet course fraction and ramp slope are invalid.");
            }
        }

        public static Inlet none() {
            return new Inlet(0, 0, 0, 0.5D, 1D);
        }

        public static Inlet defaults() {
            return new Inlet(64, 3, 32, 0.5D, 1D);
        }

        /** The inlet reach with the default course fraction and ramp slope. */
        public static Inlet of(int length, int depth, int maximumIncision) {
            return new Inlet(length, depth, maximumIncision, 0.5D, 1D);
        }
    }

    /**
     * How the ground around a channel is eroded into a valley. {@code smoothingRadius} is the run of
     * stations the blend width is averaged over, {@code thalwegFraction} the share of the channel
     * half-width that stays at full bed depth, {@code blendCurve} the exponent on the blend progress
     * (below one hollows the valley sides, above one steepens them near the shore), and
     * {@code bedNoise} the share of the channel roughness applied to the bed. Disabled erosion keeps
     * only the wet channel, the shore band and the containing lip. {@code style} is the curve of the
     * valley side ({@code terraceSteps} and {@code cliffFraction} shape only the terraced and cliff
     * styles), {@code bedProfile} the wet bed cross-section, {@code shoreRise} how many blocks the shore
     * bench climbs from the water to the valley foot, and {@code blendBaseWidth} a width added to every
     * valley blend before its bounds apply.
     */
    public record Erosion(
            boolean enabled,
            int smoothingRadius,
            double thalwegFraction,
            double blendCurve,
            double bedNoise,
            IrisRiverBlendStyle style,
            int terraceSteps,
            double cliffFraction,
            IrisRiverBedProfile bedProfile,
            double shoreRise,
            double blendBaseWidth
    ) {
        public Erosion {
            if (smoothingRadius < 0
                    || !Double.isFinite(thalwegFraction) || thalwegFraction < 0D || thalwegFraction >= 1D
                    || !Double.isFinite(blendCurve) || blendCurve <= 0D
                    || !Double.isFinite(bedNoise) || bedNoise < 0D) {
                throw new IllegalArgumentException("Surface erosion settings are invalid.");
            }
            if (style == null || bedProfile == null
                    || terraceSteps < 2
                    || !Double.isFinite(cliffFraction) || cliffFraction < 0D || cliffFraction > 1D
                    || !Double.isFinite(shoreRise) || shoreRise < 0D
                    || !Double.isFinite(blendBaseWidth) || blendBaseWidth < 0D) {
                throw new IllegalArgumentException("Surface erosion style settings are invalid.");
            }
        }

        public static Erosion defaults() {
            return new Erosion(true, 12, 0.45D, 1D, 0.5D, IrisRiverBlendStyle.SMOOTH, 4, 0.5D, IrisRiverBedProfile.BOWL, 0D, 0D);
        }

        /** The erosion amounts with the smooth valley side, bowl bed and flat shore bench they always had. */
        public static Erosion of(boolean enabled, int smoothingRadius, double thalwegFraction, double blendCurve, double bedNoise) {
            return new Erosion(enabled, smoothingRadius, thalwegFraction, blendCurve, bedNoise,
                    IrisRiverBlendStyle.SMOOTH, 4, 0.5D, IrisRiverBedProfile.BOWL, 0D, 0D);
        }

        // Enum hash codes are identity based and change between runs; the plan fingerprint mixes this hash in.
        @Override
        public int hashCode() {
            return Objects.hash(enabled, smoothingRadius, thalwegFraction, blendCurve, bedNoise, style.name(),
                    terraceSteps, cliffFraction, bedProfile.name(), shoreRise, blendBaseWidth);
        }
    }

    /**
     * The wet outline of a surface channel. {@code smoothingRadius} is the run of stations the profile is
     * averaged over, {@code outlineMinimumRatio} and {@code outlineMaximumRatio} bound the outline against
     * the planned half-width, and {@code springExtraDepth} is how much of the remaining spring taper is
     * added to the bed depth at a spring.
     */
    public record Channel(
            int smoothingRadius,
            double outlineMinimumRatio,
            double outlineMaximumRatio,
            double springExtraDepth
    ) {
        public Channel {
            if (smoothingRadius < 0
                    || !Double.isFinite(outlineMinimumRatio) || outlineMinimumRatio <= 0D || outlineMinimumRatio > 1D
                    || !Double.isFinite(outlineMaximumRatio) || outlineMaximumRatio < 1D
                    || !Double.isFinite(springExtraDepth) || springExtraDepth < 0D) {
                throw new IllegalArgumentException("Surface channel settings are invalid.");
            }
        }

        public static Channel defaults() {
            return new Channel(16, 0.6D, 1.4D, 1D);
        }
    }

    /**
     * Waterfalls and plunge basins on a surface river. {@code waterfallThalwegFraction} is the share of the
     * half-width kept at full depth over a fall; a drop of at least {@code plungeBasinMinimumDrop} blocks
     * scours a basin {@code plungeBasinLengthRatio} half-widths long and {@code plungeBasinDepth} blocks
     * deeper than the bed.
     */
    public record Flow(
            double waterfallThalwegFraction,
            int plungeBasinMinimumDrop,
            double plungeBasinLengthRatio,
            int plungeBasinDepth
    ) {
        public Flow {
            if (!Double.isFinite(waterfallThalwegFraction) || waterfallThalwegFraction < 0D || waterfallThalwegFraction >= 1D
                    || plungeBasinMinimumDrop < 1
                    || !Double.isFinite(plungeBasinLengthRatio) || plungeBasinLengthRatio < 0D
                    || plungeBasinDepth < 0) {
                throw new IllegalArgumentException("Surface flow settings are invalid.");
            }
        }

        public static Flow defaults() {
            return new Flow(0.65D, 2, 2D, 1);
        }
    }

    /** A pond at one end of a surface course: a round bowl holding the course's head at that end. */
    public record Pond(boolean enabled, int minimumRadius, int maximumRadius, int depth) {
        public Pond {
            if (minimumRadius < 1 || maximumRadius < minimumRadius || depth < 1) {
                throw new IllegalArgumentException("Surface pond settings are invalid.");
            }
        }
    }

    /** The ponds at a surface course's source and at an inland terminal. */
    public record Ponds(Pond source, Pond terminal) {
        public Ponds {
            if (source == null || terminal == null) {
                throw new IllegalArgumentException("Surface pond settings are required.");
            }
        }

        public static Ponds defaults() {
            return new Ponds(new Pond(true, 6, 12, 3), new Pond(true, 4, 7, 3));
        }

        /** No pond at either end: what a standing pool or a test channel asks for. */
        public static Ponds none() {
            return new Ponds(new Pond(false, 1, 1, 1), new Pond(false, 1, 1, 1));
        }
    }

    /** A standing surface pool profile: a bowl of the given radius and depth, filled with its own fluid. */
    public record SurfacePool(
            String id,
            boolean enabled,
            double density,
            int spacing,
            int minimumRadius,
            int maximumRadius,
            int depth,
            int maximumPerTile,
            String biomeKey
    ) {
        public SurfacePool {
            if (id == null || id.isBlank()) {
                throw new IllegalArgumentException("Surface pool id must not be blank.");
            }
            id = id.trim();
            if (!Double.isFinite(density) || density < 0D || spacing < 1
                    || minimumRadius < 1 || maximumRadius < minimumRadius || depth < 1 || maximumPerTile < 0) {
                throw new IllegalArgumentException("Surface pool settings are invalid.");
            }
            biomeKey = biomeKey == null || biomeKey.isBlank() ? null : biomeKey.trim();
        }
    }

    /**
     * Sea caves: coastal grottos that open from the ocean into the coast without a river. A tile keeps at
     * most {@code maximumPerTile} of them, the steepest owned coast first, at least {@code minimumSpacing}
     * apart, only where the coast stands {@code minimumCoastHeight} above the sea, each chamber swept
     * {@code depth} blocks inland from the shoreline, its sweep turned up to {@code sweepJitterDegrees}
     * either side of the inland normal. Chamber size and volume come from the coastal grotto.
     */
    public record SeaCaves(
            boolean enabled,
            int maximumPerTile,
            int minimumSpacing,
            int minimumCoastHeight,
            int depth,
            double sweepJitterDegrees
    ) {
        public SeaCaves {
            if (maximumPerTile < 0 || maximumPerTile > 64
                    || minimumSpacing < 16 || minimumSpacing > 8192
                    || minimumCoastHeight < 1 || minimumCoastHeight > 128
                    || depth < 0 || depth > 128
                    || !Double.isFinite(sweepJitterDegrees) || sweepJitterDegrees < 0D || sweepJitterDegrees > 180D) {
                throw new IllegalArgumentException("Sea cave bounds are invalid.");
            }
        }

        public static SeaCaves disabled() {
            return new SeaCaves(false, 0, 16, 1, 0, 25D);
        }

        /** The chamber budget with the default sweep jitter. */
        public static SeaCaves of(boolean enabled, int maximumPerTile, int minimumSpacing, int minimumCoastHeight, int depth) {
            return new SeaCaves(enabled, maximumPerTile, minimumSpacing, minimumCoastHeight, depth, 25D);
        }
    }

    // The routing gates read one hydraulic threshold: the drop that makes a reach a waterfall.
    public record Hydraulics(int waterfallMinimumDrop) {
        public Hydraulics {
            if (waterfallMinimumDrop < 1) {
                throw new IllegalArgumentException("waterfallMinimumDrop must be at least one block.");
            }
        }
    }

    public record Underground(
            boolean enabled,
            Source sources,
            int minimumFluidY,
            int maximumFluidY,
            int minimumWidth,
            int maximumWidth,
            int minimumDepth,
            int maximumDepth,
            int minimumHeadroom,
            int maximumHeadroom,
            boolean connectToExistingCaves,
            int tributaries,
            int minimumRockCover,
            int minimumFloorCover,
            int wideningSources
    ) {
        public Underground {
            if (sources == null || minimumFluidY > maximumFluidY) {
                throw new IllegalArgumentException("Underground source and height settings are invalid.");
            }
            requireRange(minimumWidth, maximumWidth, 1, 256, "underground width");
            requireRange(minimumDepth, maximumDepth, 1, 64, "underground depth");
            requireRange(minimumHeadroom, maximumHeadroom, 1, 128, "underground headroom");
            if (tributaries < 0 || tributaries > 4) {
                throw new IllegalArgumentException("underground tributaries must be between 0 and 4.");
            }
            if (minimumRockCover < 1 || minimumRockCover > 256
                    || minimumFloorCover < 1 || minimumFloorCover > 256
                    || wideningSources < 1 || wideningSources > 1024) {
                throw new IllegalArgumentException("Underground rock cover and widening settings are invalid.");
            }
        }

        /** One block of rock above and below a tunnel, and widening that saturates at eight sources. */
        public static Underground of(
                boolean enabled,
                Source sources,
                int minimumFluidY,
                int maximumFluidY,
                int minimumWidth,
                int maximumWidth,
                int minimumDepth,
                int maximumDepth,
                int minimumHeadroom,
                int maximumHeadroom,
                boolean connectToExistingCaves,
                int tributaries
        ) {
            return new Underground(enabled, sources, minimumFluidY, maximumFluidY, minimumWidth, maximumWidth, minimumDepth,
                    maximumDepth, minimumHeadroom, maximumHeadroom, connectToExistingCaves, tributaries, 1, 1, 8);
        }
    }

    public record Outlets(
            boolean oceanEnabled,
            Grotto coastalGrotto,
            Grotto inlandGrotto,
            boolean surfaceSinkholesEnabled,
            int coastalCliffMinimumHeight,
            int mouthLevelingDistance,
            int maximumOceanApron,
            int maximumPerTile,
            int maximumCoastalPerTile,
            double coastalCliffSlopeFactor
    ) {
        public Outlets {
            if (coastalGrotto == null || inlandGrotto == null) {
                throw new IllegalArgumentException("Coastal and inland grotto settings are required.");
            }
            if (surfaceSinkholesEnabled && !inlandGrotto.enabled()) {
                throw new IllegalArgumentException("Surface sinkholes require inland grotto outlets.");
            }
            if (coastalCliffMinimumHeight < 0 || maximumOceanApron < 0 || maximumOceanApron > 64
                    || mouthLevelingDistance < 0 || maximumPerTile < 1 || maximumPerTile > 256
                    || maximumCoastalPerTile < 0 || maximumCoastalPerTile > 256) {
                throw new IllegalArgumentException("Outlet bounds are invalid.");
            }
            requireFiniteNonNegative(coastalCliffSlopeFactor, "coastalCliffSlopeFactor");
        }

        /** The outlet budget with the default coastal cliff slope factor. */
        public static Outlets of(
                boolean oceanEnabled,
                Grotto coastalGrotto,
                Grotto inlandGrotto,
                boolean surfaceSinkholesEnabled,
                int coastalCliffMinimumHeight,
                int mouthLevelingDistance,
                int maximumOceanApron,
                int maximumPerTile,
                int maximumCoastalPerTile
        ) {
            return new Outlets(oceanEnabled, coastalGrotto, inlandGrotto, surfaceSinkholesEnabled, coastalCliffMinimumHeight,
                    mouthLevelingDistance, maximumOceanApron, maximumPerTile, maximumCoastalPerTile, 0.5D);
        }
    }

    public record Grotto(
            boolean enabled,
            int horizontalRadius,
            int verticalRadius,
            int headroom,
            int maximumVolume
    ) {
        public Grotto {
            if (horizontalRadius < 1 || horizontalRadius > 128 || verticalRadius < 1 || verticalRadius > 128
                    || headroom < 1 || headroom > 128 || maximumVolume < 1 || maximumVolume > 1_048_576) {
                throw new IllegalArgumentException("Grotto bounds are invalid.");
            }
        }
    }

    public record Geometry(
            Meanders meanders,
            ChannelShape surface,
            ChannelShape underground,
            ChannelShape grottos,
            Drops drops
    ) {
        public Geometry {
            if (meanders == null || surface == null || underground == null || grottos == null || drops == null) {
                throw new IllegalArgumentException("Hydrology geometry settings cannot contain null sections.");
            }
        }

        public static Geometry defaults() {
            ChannelShape channel = ChannelShape.of(2.4D, 0.28D, 0.24D, 11);
            return new Geometry(
                    new Meanders(64, 12, 0.34D, 0.42D, 0.48D, 1, 82D),
                    channel,
                    channel,
                    channel,
                    Drops.of(2, 1.4D, 2, 0.45D, 2, 1.8D, 8)
            );
        }
    }

    public record Meanders(
            int primaryWavelength,
            int detailWavelength,
            double primaryStrength,
            double detailStrength,
            double maximumOffsetRatio,
            int smoothingPasses,
            double maximumTurnDegrees
    ) {
        public Meanders {
            if (primaryWavelength < 8 || primaryWavelength > 512
                    || detailWavelength < 4 || detailWavelength > 128
                    || detailWavelength > primaryWavelength
                    || smoothingPasses < 0 || smoothingPasses > 4
                    || !Double.isFinite(primaryStrength) || primaryStrength < 0D || primaryStrength > 2D
                    || !Double.isFinite(detailStrength) || detailStrength < 0D || detailStrength > 2D
                    || !Double.isFinite(maximumOffsetRatio) || maximumOffsetRatio < 0D || maximumOffsetRatio > 1D
                    || !Double.isFinite(maximumTurnDegrees)
                    || maximumTurnDegrees < 10D || maximumTurnDegrees > 150D) {
                throw new IllegalArgumentException("Hydrology meander geometry is invalid.");
            }
        }
    }

    /**
     * The cross-section of a carved channel. The bed and wall roughness shape every channel; the radial
     * and lobe terms shape the organic wall outline of an underground channel or grotto around
     * {@code radialBase}, clamped to {@code [radialMinimum, radialMaximum]}; {@code ceilingRoughness}
     * roughens a tunnel ceiling; {@code aspectMinimum} and {@code aspectRange} bound a grotto's plan aspect.
     */
    public record ChannelShape(
            double bedRoundness,
            double bedRoughness,
            double wallRoughness,
            int roughnessWavelength,
            double radialBase,
            double radialMinimum,
            double radialMaximum,
            double primaryLobeStrength,
            double detailLobeStrength,
            double ceilingRoughness,
            double aspectMinimum,
            double aspectRange
    ) {
        public ChannelShape {
            if (!Double.isFinite(bedRoundness) || bedRoundness < 1D || bedRoundness > 6D
                    || !Double.isFinite(bedRoughness) || bedRoughness < 0D || bedRoughness > 1D
                    || !Double.isFinite(wallRoughness) || wallRoughness < 0D || wallRoughness > 1D
                    || roughnessWavelength < 3 || roughnessWavelength > 128) {
                throw new IllegalArgumentException("Hydrology channel shape is invalid.");
            }
            if (!Double.isFinite(radialBase) || radialBase < 0.1D || radialBase > 4D
                    || !Double.isFinite(radialMinimum) || radialMinimum <= 0D
                    || !Double.isFinite(radialMaximum) || radialMaximum < radialMinimum || radialMaximum > 4D
                    || !Double.isFinite(primaryLobeStrength) || primaryLobeStrength < 0D || primaryLobeStrength > 1D
                    || !Double.isFinite(detailLobeStrength) || detailLobeStrength < 0D || detailLobeStrength > 1D
                    || !Double.isFinite(ceilingRoughness) || ceilingRoughness < 0D || ceilingRoughness > 1D
                    || !Double.isFinite(aspectMinimum) || aspectMinimum <= 0D || aspectMinimum > 1D
                    || !Double.isFinite(aspectRange) || aspectRange < 0D || aspectRange > 1D) {
                throw new IllegalArgumentException("Hydrology channel wall shape is invalid.");
            }
        }

        /** The bed and wall roughness with the organic wall outline every channel had before it was configurable. */
        public static ChannelShape of(double bedRoundness, double bedRoughness, double wallRoughness, int roughnessWavelength) {
            return new ChannelShape(bedRoundness, bedRoughness, wallRoughness, roughnessWavelength,
                    0.86D, 0.58D, 1.18D, 0.08D, 0.06D, 0D, 0.62D, 0.2D);
        }
    }

    public record Drops(
            int cascadeRunPerBlock,
            double cascadeExponent,
            int maximumCascadeStep,
            double flowWidthRatio,
            int maximumFlowDepth,
            double basinWidthRatio,
            int maximumBasinDepth,
            int undergroundCascadeRunPerBlock
    ) {
        public Drops {
            if (cascadeRunPerBlock < 1 || cascadeRunPerBlock > 16
                    || !Double.isFinite(cascadeExponent) || cascadeExponent < 0.25D || cascadeExponent > 6D
                    || maximumCascadeStep < 1 || maximumCascadeStep > 4
                    || !Double.isFinite(flowWidthRatio) || flowWidthRatio < 0.25D || flowWidthRatio > 1D
                    || maximumFlowDepth < 1 || maximumFlowDepth > 16
                    || !Double.isFinite(basinWidthRatio) || basinWidthRatio < 1D || basinWidthRatio > 4D
                    || maximumBasinDepth < maximumFlowDepth || maximumBasinDepth > 32) {
                throw new IllegalArgumentException("Hydrology drop geometry is invalid.");
            }
            if (undergroundCascadeRunPerBlock < 0 || undergroundCascadeRunPerBlock > 64) {
                throw new IllegalArgumentException("undergroundCascadeRunPerBlock must be between 0 and 64.");
            }
        }

        /** The drop geometry with the underground cascade run left at the routing minimum. */
        public static Drops of(
                int cascadeRunPerBlock,
                double cascadeExponent,
                int maximumCascadeStep,
                double flowWidthRatio,
                int maximumFlowDepth,
                double basinWidthRatio,
                int maximumBasinDepth
        ) {
            return new Drops(cascadeRunPerBlock, cascadeExponent, maximumCascadeStep, flowWidthRatio, maximumFlowDepth,
                    basinWidthRatio, maximumBasinDepth, 0);
        }

        public int flowWidth(int channelWidth) {
            return Math.max(1, Math.min(channelWidth, (int) StrictMath.ceil(channelWidth * flowWidthRatio)));
        }

        public int stepLimit(HydrologyFeatureType type) {
            return type.isSurface() ? 1 : maximumCascadeStep;
        }

        public int flowDepth(int channelDepth) {
            return Math.max(1, Math.min(channelDepth, maximumFlowDepth));
        }

        public int basinWidth(int flowWidth) {
            return Math.max(flowWidth, (int) StrictMath.ceil(flowWidth * basinWidthRatio));
        }

        public int basinDepth(int flowDepth, int drop) {
            int erodedDepth = Math.addExact(flowDepth, Math.max(1, (int) StrictMath.ceil(StrictMath.sqrt(drop))));
            return Math.max(flowDepth, Math.min(maximumBasinDepth, erodedDepth));
        }
    }

    public record DeepFluid(
            String id,
            boolean enabled,
            double density,
            int spacing,
            int minimumY,
            int maximumY,
            int minimumHorizontalRadius,
            int maximumHorizontalRadius,
            int minimumVerticalRadius,
            int maximumVerticalRadius,
            int minimumChannelLength,
            int maximumChannelLength,
            int channelWidth,
            int channelDepth,
            int headroom,
            int maximumVolume,
            int maximumPerTile,
            boolean containedPools,
            boolean shortChannels
    ) {
        public DeepFluid {
            if (id == null || id.isBlank()) {
                throw new IllegalArgumentException("Deep-fluid id must not be blank.");
            }
            id = id.trim();
            if (!Double.isFinite(density) || density < 0D || density > 64D || spacing < 16 || minimumY > maximumY) {
                throw new IllegalArgumentException("Deep-fluid density or height range is invalid.");
            }
            requireRange(minimumHorizontalRadius, maximumHorizontalRadius, 1, 128, "deep-fluid horizontal radius");
            requireRange(minimumVerticalRadius, maximumVerticalRadius, 1, 128, "deep-fluid vertical radius");
            requireRange(minimumChannelLength, maximumChannelLength, 0, 32_768, "deep-fluid channel length");
            if (channelWidth < 1 || channelWidth > 256 || channelDepth < 1 || channelDepth > 64 || headroom < 1
                    || maximumVolume < 64 || maximumVolume > 1_048_576
                    || maximumPerTile < 0 || maximumPerTile > 1024) {
                throw new IllegalArgumentException("Deep-fluid channel bounds are invalid.");
            }
            long poolEnvelope = ellipsoidVolume(
                    maximumHorizontalRadius,
                    Math.max(channelDepth, maximumVerticalRadius),
                    headroom
            );
            if (enabled && containedPools && poolEnvelope > maximumVolume) {
                throw new IllegalArgumentException("Deep-fluid maximumVolume cannot contain its configured pool ellipsoid.");
            }
            if (enabled && !containedPools && !shortChannels) {
                throw new IllegalArgumentException("An enabled deep fluid requires a pool or channel.");
            }
            if (enabled && shortChannels && minimumChannelLength < 1) {
                throw new IllegalArgumentException("Enabled deep-fluid channels require a positive minimum length.");
            }
        }
    }

    private static void requireRange(int minimum, int maximum, int lower, int upper, String name) {
        if (minimum < lower || maximum < minimum || maximum > upper) {
            throw new IllegalArgumentException(name + " range is invalid.");
        }
    }

    private static void requireFiniteNonNegative(double value, String name) {
        if (!Double.isFinite(value) || value < 0D) {
            throw new IllegalArgumentException(name + " must be finite and non-negative.");
        }
    }

    static long ellipsoidVolume(int radius, int lowerExtent, int upperExtent) {
        long volume = 0L;
        for (int deltaZ = -radius; deltaZ <= radius; deltaZ++) {
            for (int deltaX = -radius; deltaX <= radius; deltaX++) {
                double distance = StrictMath.hypot(deltaX, deltaZ);
                if (distance > radius + 0.25D) {
                    continue;
                }
                double normalized = Math.min(1D, distance / Math.max(1D, radius));
                double scale = StrictMath.sqrt(Math.max(0D, 1D - normalized * normalized));
                int lower = (int) StrictMath.ceil(lowerExtent * scale);
                int upper = (int) StrictMath.floor(upperExtent * scale);
                volume += lower + upper + 1L;
            }
        }
        return volume;
    }
}
