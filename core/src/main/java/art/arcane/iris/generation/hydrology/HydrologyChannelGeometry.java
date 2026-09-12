package art.arcane.iris.generation.hydrology;

import art.arcane.iris.generation.hydrology.surface.SurfaceNoise;

final class HydrologyChannelGeometry {
    private final HydrologyFootprintCompiler compiler;

    HydrologyChannelGeometry(HydrologyFootprintCompiler compiler) {
        this.compiler = compiler;
    }

    static final long ORGANIC_SHAPE_FIRST_PHASE_SALT = 0x4f5247414e31L;
    static final long ORGANIC_SHAPE_SECOND_PHASE_SALT = 0x4f5247414e32L;
    static final long ORGANIC_BED_VARIATION_SALT = 0x424544564152L;
    static final long ORGANIC_CEILING_VARIATION_SALT = 0x4345494c564152L;

    double smoothStep(double progress) {
        return progress * progress * (3D - 2D * progress);
    }

    FootprintLayerShape shape(
            RiverCourse course,
            HydraulicSegment segment,
            HydrologyPoint point,
            int bed,
            int fluidHead,
            boolean falling,
            boolean receiving
    ) {
        HydrologyFeatureType type = segment.type();
        int channelRadius = Math.max(1, segment.width() / 2);
        double shoreWidth = 0D;
        double gradingWidth = 0D;
        int ceiling = fluidHead;
        boolean ellipsoid = false;
        boolean archedChannel = false;
        boolean roundedSurfaceBed = false;
        boolean organicBoundary = type.isSurface() || type.isUnderground() || type.isDeepFluid() || falling || receiving;
        if (course.type() == RiverCourseType.SURFACE && type.isSurface()) {
            roundedSurfaceBed = !falling;
            shoreWidth = compiler.settings.surface().shoreWidth();
            gradingWidth = compiler.geometrySampler.sample(
                    HydrologyGeometrySampler.Field.SURFACE_BLEND_WIDTH,
                    course.profileKey(),
                    point.x(),
                    point.z(),
                    segment.id(),
                    compiler.settings.surface().banks().minimumBlendWidth(),
                    compiler.settings.surface().banks().maximumBlendWidth()
            );
            if (type == HydrologyFeatureType.WATERFALL) {
                shoreWidth = Math.min(2.5D, shoreWidth);
                gradingWidth = Math.min(gradingWidth, Math.max(8D, channelRadius * 4D));
            }
        }
        if (type == HydrologyFeatureType.UNDERGROUND_POOL
                || type == HydrologyFeatureType.UNDERGROUND_DROP
                || type == HydrologyFeatureType.SINKHOLE) {
            ceiling = fluidHead + compiler.geometrySampler.sample(
                    HydrologyGeometrySampler.Field.UNDERGROUND_HEADROOM,
                    course.profileKey(),
                    point.x(),
                    point.z(),
                    course.id(),
                    compiler.settings.underground().minimumHeadroom(),
                    compiler.settings.underground().maximumHeadroom()
            );
            archedChannel = !falling;
        } else if (type == HydrologyFeatureType.COASTAL_GROTTO) {
            HydrologyPlannerSettings.Grotto grotto = compiler.settings.outlets().coastalGrotto();
            channelRadius = grotto.horizontalRadius();
            bed = fluidHead - grotto.verticalRadius();
            ceiling = fluidHead + grotto.headroom();
            ellipsoid = true;
            organicBoundary = true;
        } else if (type == HydrologyFeatureType.INLAND_GROTTO) {
            HydrologyPlannerSettings.Grotto grotto = compiler.settings.outlets().inlandGrotto();
            channelRadius = grotto.horizontalRadius();
            bed = fluidHead - grotto.verticalRadius();
            ceiling = fluidHead + grotto.headroom();
            ellipsoid = true;
            organicBoundary = true;
        } else if (type.isDeepFluid()) {
            HydrologyPlannerSettings.DeepFluid deepFluid = compiler.deepFluids.get(course.profileKey());
            if (deepFluid == null) {
                throw new IllegalStateException("Missing deep-fluid planner settings for " + course.profileKey() + ".");
            }
            if (type == HydrologyFeatureType.DEEP_POOL) {
                channelRadius = Math.max(1, segment.width() / 2);
                bed = fluidHead - Math.max(segment.depth(), deepFluid.minimumVerticalRadius());
                ellipsoid = true;
            } else {
                archedChannel = !falling;
            }
            ceiling = fluidHead + deepFluid.headroom();
            organicBoundary = true;
        }
        if (receiving) {
            HydrologyPlannerSettings.Drops drops = compiler.settings.geometry().drops();
            if (course.type() != RiverCourseType.SURFACE || !type.isSurface()) {
                channelRadius = Math.max(
                        channelRadius,
                        (int) StrictMath.ceil(drops.basinWidth(segment.width()) / 2D)
                );
            }
            bed = Math.min(bed, fluidHead - drops.basinDepth(segment.depth(), segment.drop()));
            if (course.type() == RiverCourseType.SURFACE && type.isSurface()) {
                gradingWidth = Math.max(
                        gradingWidth,
                        Math.min(compiler.settings.surface().banks().maximumBlendWidth(), channelRadius * 2D)
                );
            }
        }
        if (type == HydrologyFeatureType.UNDERGROUND_POOL && segment.centerline().size() > 1) {
            int depthVariation = Math.min(
                    2,
                    Math.max(0, compiler.settings.underground().maximumDepth() - (fluidHead - bed))
            );
            bed -= organicVerticalVariation(
                    HydrologyHash.mix(segment.id(), ORGANIC_BED_VARIATION_SALT),
                    point.x(),
                    point.z(),
                    depthVariation
            );
        }
        if (falling) {
            bed = Math.min(bed, segment.downstreamHeadY() - 1);
            ceiling = Math.max(ceiling, segment.upstreamHeadY());
        }
        return new FootprintLayerShape(
                channelRadius,
                shoreWidth,
                gradingWidth,
                bed,
                fluidHead,
                ceiling,
                ellipsoid,
                archedChannel,
                roundedSurfaceBed,
                organicBoundary,
                falling
        );
    }

    int organicVerticalVariation(
            long seed,
            int x,
            int z,
            int maximum
    ) {
        if (maximum == 0) {
            return 0;
        }
        double sampled = SurfaceNoise.value(seed, x, z, 12);
        return (int) StrictMath.round(sampled * maximum);
    }

    double ellipsoidScale(int radius, double distance, double roundness) {
        double normalized = Math.min(1D, distance / Math.max(1D, radius));
        return StrictMath.pow(
                Math.max(0D, 1D - StrictMath.pow(normalized, roundness)),
                1D / roundness
        );
    }

    double shapedDistance(
            FootprintLayerShape shape,
            HydraulicSegment segment,
            int worldX,
            int worldZ,
            double deltaX,
            double deltaZ,
            int flowX,
            int flowZ,
            double rawDistance
    ) {
        if (segment.type() == HydrologyFeatureType.DEEP_POOL) {
            return deepPoolDistance(shape, segment, worldX, worldZ, deltaX, deltaZ);
        }
        if (segment.type().isSurface() && !shape.fallingThroat()) {
            return surfaceChannelDistance(shape, segment, worldX, worldZ, deltaX, deltaZ, flowX, flowZ);
        }
        if (rawDistance == 0D) {
            return 0D;
        }
        double distance = rawDistance;
        if (shape.fallingThroat() && (flowX != 0 || flowZ != 0)) {
            double flowLength = StrictMath.hypot(flowX, flowZ);
            double along = (deltaX * flowX + deltaZ * flowZ) / flowLength;
            double across = StrictMath.abs(deltaX * -flowZ + deltaZ * flowX) / flowLength;
            if (StrictMath.abs(along) > 0.75D || segment.type().isSurface() && along < 0D) {
                return shape.totalRadius() + 1D;
            }
            distance = across;
        }
        if (!shape.organicBoundary()) {
            return distance;
        }
        HydrologyPlannerSettings.ChannelShape channelShape = channelShape(segment.type());
        if (segment.type() == HydrologyFeatureType.COASTAL_GROTTO
                || segment.type() == HydrologyFeatureType.INLAND_GROTTO) {
            double orientation = HydrologyHash.unit(HydrologyHash.mix(
                    segment.courseId(),
                    ORGANIC_SHAPE_FIRST_PHASE_SALT
            )) * StrictMath.PI * 2D;
            double cosine = StrictMath.cos(orientation);
            double sine = StrictMath.sin(orientation);
            double rotatedX = deltaX * cosine + deltaZ * sine;
            double rotatedZ = -deltaX * sine + deltaZ * cosine;
            double aspect = channelShape.aspectMinimum() + HydrologyHash.unit(HydrologyHash.mix(
                    segment.courseId(),
                    ORGANIC_SHAPE_SECOND_PHASE_SALT
            )) * channelShape.aspectRange();
            distance = StrictMath.hypot(rotatedX, rotatedZ / aspect);
        }
        double angle = StrictMath.atan2(deltaZ, deltaX);
        double firstPhase = HydrologyHash.unit(HydrologyHash.mix(
                segment.courseId(),
                ORGANIC_SHAPE_FIRST_PHASE_SALT
        )) * StrictMath.PI * 2D;
        double secondPhase = HydrologyHash.unit(HydrologyHash.mix(
                segment.courseId(),
                ORGANIC_SHAPE_SECOND_PHASE_SALT
        )) * StrictMath.PI * 2D;
        double firstLobe = 0.5D + 0.5D * StrictMath.sin(angle * 3D + firstPhase);
        double secondLobe = 0.5D + 0.5D * StrictMath.sin(angle * 5D + secondPhase);
        double coherent = SurfaceNoise.signed(
                segment.courseId(),
                worldX,
                worldZ,
                channelShape.roughnessWavelength()
        );
        double detail = SurfaceNoise.signed(
                HydrologyHash.mix(segment.courseId(), ORGANIC_SHAPE_SECOND_PHASE_SALT),
                worldX,
                worldZ,
                Math.max(3, channelShape.roughnessWavelength() / 2)
        );
        double radialScale = channelShape.radialBase()
                + (firstLobe - 0.5D) * channelShape.primaryLobeStrength()
                + (secondLobe - 0.5D) * channelShape.detailLobeStrength()
                + coherent * channelShape.wallRoughness() * 0.7D
                + detail * channelShape.wallRoughness() * 0.3D;
        radialScale = Math.max(channelShape.radialMinimum(), Math.min(channelShape.radialMaximum(), radialScale));
        if (segment.type().isSurface() && shape.fallingThroat()) {
            radialScale = Math.max(1D, radialScale);
        }
        return distance / radialScale;
    }

    double surfaceChannelDistance(
            FootprintLayerShape shape,
            HydraulicSegment segment,
            int worldX,
            int worldZ,
            double deltaX,
            double deltaZ,
            int flowX,
            int flowZ
    ) {
        double flowLength = StrictMath.hypot(flowX, flowZ);
        if (flowLength == 0D) {
            return deepPoolDistance(shape, segment, worldX, worldZ, deltaX, deltaZ);
        }
        double signedCross = (deltaX * -flowZ + deltaZ * flowX) / flowLength;
        HydrologyPlannerSettings.ChannelShape channelShape = compiler.settings.geometry().surface();
        double thalweg = SurfaceNoise.signed(
                HydrologyHash.mix(segment.courseId(), ORGANIC_BED_VARIATION_SALT),
                worldX,
                worldZ,
                channelShape.roughnessWavelength()
        ) * shape.channelRadius() * channelShape.wallRoughness();
        long bankSeed = HydrologyHash.mix(segment.courseId(), ORGANIC_SHAPE_SECOND_PHASE_SALT);
        double bankNoise = SurfaceNoise.signed(
                bankSeed,
                worldX,
                worldZ,
                channelShape.roughnessWavelength()
        );
        double sideBias = signedCross < thalweg
                ? HydrologyHash.unit(HydrologyHash.mix(bankSeed, 1L))
                : HydrologyHash.unit(HydrologyHash.mix(bankSeed, 2L));
        double widthScale = 0.86D
                + bankNoise * channelShape.wallRoughness()
                + (sideBias - 0.5D) * 0.08D;
        widthScale = Math.max(1D, Math.min(1.18D, widthScale));
        double organicDistance = StrictMath.abs(signedCross - thalweg) / widthScale;
        double along = (deltaX * flowX + deltaZ * flowZ) / flowLength;
        return StrictMath.hypot(along, Math.min(StrictMath.abs(signedCross), organicDistance));
    }

    HydrologyPlannerSettings.ChannelShape channelShape(HydrologyFeatureType type) {
        if (type.isGrotto() || type == HydrologyFeatureType.DEEP_POOL) {
            return compiler.settings.geometry().grottos();
        }
        if (type.isUnderground() || type == HydrologyFeatureType.DEEP_CHANNEL) {
            return compiler.settings.geometry().underground();
        }
        return compiler.settings.geometry().surface();
    }

    double deepPoolDistance(
            FootprintLayerShape shape,
            HydraulicSegment segment,
            int worldX,
            int worldZ,
            double deltaX,
            double deltaZ
    ) {
        double radius = Math.max(1D, shape.channelRadius());
        long seed = HydrologyHash.mix(segment.courseId(), ORGANIC_SHAPE_FIRST_PHASE_SALT);
        double orientation = HydrologyHash.unit(seed) * StrictMath.PI * 2D;
        double cosine = StrictMath.cos(orientation);
        double sine = StrictMath.sin(orientation);
        double rotatedX = deltaX * cosine + deltaZ * sine;
        double rotatedZ = -deltaX * sine + deltaZ * cosine;
        double angle = StrictMath.atan2(rotatedZ, rotatedX);
        double secondPhase = HydrologyHash.unit(HydrologyHash.mix(seed, 2L)) * StrictMath.PI * 2D;
        double thirdPhase = HydrologyHash.unit(HydrologyHash.mix(seed, 3L)) * StrictMath.PI * 2D;
        double fifthPhase = HydrologyHash.unit(HydrologyHash.mix(seed, 5L)) * StrictMath.PI * 2D;
        double radialScale = 0.84D
                + StrictMath.sin(angle * 2D + secondPhase) * 0.07D
                + StrictMath.sin(angle * 3D + thirdPhase) * 0.05D
                + StrictMath.sin(angle * 5D + fifthPhase) * 0.035D;
        radialScale = Math.max(0.68D, Math.min(1D, radialScale));
        double normalizedDistance = StrictMath.hypot(
                rotatedX / (radius * 0.92D),
                rotatedZ / (radius * 0.7D)
        );
        return normalizedDistance * radius / radialScale;
    }
}
