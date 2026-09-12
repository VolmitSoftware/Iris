package art.arcane.iris.generation.hydrology;

import art.arcane.iris.generation.hydrology.surface.SurfaceFootprint;
import art.arcane.iris.generation.hydrology.surface.SurfaceBounds;
import art.arcane.iris.generation.hydrology.surface.SurfaceFootprintCompiler;
import art.arcane.iris.generation.hydrology.surface.SurfaceLayerColumn;
import art.arcane.iris.generation.hydrology.surface.SurfaceNoise;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

final class HydrologyFootprintCompiler {
    static final int FEATURE_ROLE_COUNT = 7;
    static final int COURSE_FOOTPRINT_CACHE_SIZE = 32;
    static final int VALIDATION_RASTER_CACHE_SIZE = 32;

    static final int[][] HORIZONTAL_NEIGHBORS = {
            {1, 0}, {-1, 0}, {0, 1}, {0, -1}
    }

;

    final HydrologyPlannerSettings settings;
    final HydrologyTerrainSampler sampler;
    final HydrologyNaturalTerrainSampler naturalSampler;
    final HydrologyGeometrySampler geometrySampler;
    final Map<String, HydrologyPlannerSettings.DeepFluid> deepFluids;
    final Long2ObjectOpenHashMap<HydrologyTerrainSample> terrainSamples;
    final Long2ObjectOpenHashMap<HydrologyTerrainSample> terrainBases;
    final Long2ObjectOpenHashMap<HydrologyRoutingTerrainSampler.NaturalClassification> naturalClassifications;
    final Map<FootprintFeatureKey, HydrologyFeatureRef> features;
    final LinkedHashMap<FootprintCourseRasterKey, RiverFootprint> courseFootprints;
    final LinkedHashMap<FootprintCourseRasterKey, FootprintValidationCourseRaster> validationCourseRasters;
    final Map<FootprintRasterStencilKey, FootprintRasterStencil> rasterStencils;
    final SurfaceFootprintCompiler surfaceCompiler;
    final LinkedHashMap<FootprintCourseRasterKey, SurfaceFootprint> surfaceFootprints;
    final Map<Long, SurfaceFootprint> regionalSurfaceFootprints;
    final Map<Long, HydrologySurfaceDropRaster> regionalDropRasters;
    HydrologyRegionalNetwork regionalNetwork;

    int fullMaterializationCount;
    final HydrologyFootprintRasterizer rasterizer;
    final HydrologyChannelGeometry channelGeometry;

    HydrologyFootprintCompiler(
            HydrologyPlannerSettings settings,
            HydrologyTerrainSampler sampler
    ) {
        this(settings, sampler, HydrologyGeometrySampler.deterministic(sampler));
    }

    HydrologyFootprintCompiler(
            HydrologyPlannerSettings settings,
            HydrologyTerrainSampler sampler,
            HydrologyGeometrySampler geometrySampler
    ) {
        this(settings, new Sampling(sampler, geometrySampler, null));
    }

    HydrologyFootprintCompiler(
            HydrologyPlannerSettings settings,
            Sampling sampling
    ) {
        this.settings = settings;
        this.sampler = sampling.sampler();
        this.naturalSampler = sampling.naturalSampler();
        this.geometrySampler = sampling.geometrySampler();
        HashMap<String, HydrologyPlannerSettings.DeepFluid> indexed = new HashMap<>();
        for (HydrologyPlannerSettings.DeepFluid deepFluid : settings.deepFluids()) {
            indexed.put(deepFluid.id(), deepFluid);
        }
        this.deepFluids = Map.copyOf(indexed);
        this.terrainSamples = new Long2ObjectOpenHashMap<>();
        this.terrainBases = new Long2ObjectOpenHashMap<>();
        this.naturalClassifications = new Long2ObjectOpenHashMap<>();
        this.features = new HashMap<>();
        this.courseFootprints = new LinkedHashMap<>(COURSE_FOOTPRINT_CACHE_SIZE, 1F, true);
        this.validationCourseRasters = new LinkedHashMap<>(VALIDATION_RASTER_CACHE_SIZE, 1F, true);
        this.rasterStencils = new HashMap<>();
        this.surfaceCompiler = new SurfaceFootprintCompiler(settings, this::sampleTerrainBasis, this.geometrySampler);
        this.surfaceFootprints = new LinkedHashMap<>(COURSE_FOOTPRINT_CACHE_SIZE, 1F, true);
        this.regionalSurfaceFootprints = new LinkedHashMap<>();
        this.regionalDropRasters = new LinkedHashMap<>();
        this.regionalNetwork = HydrologyRegionalNetwork.EMPTY;
        this.fullMaterializationCount = 0;
        this.rasterizer = new HydrologyFootprintRasterizer(this);
        this.channelGeometry = new HydrologyChannelGeometry(this);
    }

    SurfaceRasterIndex emptySurfaceRaster() {
        return new SurfaceRasterIndex(List.<SurfaceFootprint>of(), new Long2ObjectOpenHashMap<>());
    }

    SurfaceFootprint surfaceFootprint(RiverCourse course) {
        SurfaceFootprint regional = regionalSurfaceFootprints.get(course.id());
        if (regional != null) {
            return regional;
        }
        FootprintCourseRasterKey rasterKey = new FootprintCourseRasterKey(
                course.id(),
                course.type(),
                course.profileKey(),
                course.segments()
        );
        SurfaceFootprint cached = surfaceFootprints.get(rasterKey);
        if (cached != null) {
            return cached;
        }
        SurfaceFootprint compiled = surfaceCompiler.compile(course);
        surfaceFootprints.put(rasterKey, compiled);
        if (surfaceFootprints.size() > COURSE_FOOTPRINT_CACHE_SIZE) {
            surfaceFootprints.remove(surfaceFootprints.sequencedKeySet().getFirst());
        }
        return compiled;
    }

    void seedRegionalSurface(HydrologyRegionalNetwork network, SurfaceBounds bounds) {
        regionalNetwork = Objects.requireNonNull(network);
        regionalSurfaceFootprints.clear();
        regionalDropRasters.clear();
        for (RiverCourse course : network.courses()) {
            SurfaceFootprint footprint = surfaceCompiler.compile(course, bounds);
            if (!footprint.accepted()) {
                throw new IllegalStateException("Accepted regional river changed during bounded footprint compilation: " + course.id());
            }
            regionalSurfaceFootprints.put(course.id(), footprint);
            regionalDropRasters.put(course.id(), HydrologySurfaceDropRaster.compile(settings,
                    HydrologyOceanReceiver.forCourse(settings, this::sampleTerrainBasis, course), geometrySampler, course, bounds));
        }
    }

    RiverFootprint compile(List<RiverCourse> courses) {
        fullMaterializationCount++;
        if (courses.isEmpty()) {
            return RiverFootprint.empty();
        }
        if (courses.size() == 1) {
            return compileCourse(courses.getFirst());
        }
        Long2ObjectLinkedOpenHashMap<FootprintMutableColumn> columns = new Long2ObjectLinkedOpenHashMap<>();
        for (RiverCourse course : courses) {
            RiverFootprint footprint = compileCourse(course);
            for (Map.Entry<Long, HydrologyColumnSample> entry : footprint.columns().entrySet()) {
                HydrologyColumnSample sample = entry.getValue();
                FootprintMutableColumn column = columns.get(entry.getKey());
                if (column == null) {
                    columns.put(entry.getKey(), new FootprintMutableColumn(sample));
                    continue;
                }
                column.merge(sample);
            }
        }
        return build(columns);
    }

    ValidationRaster compileValidation(List<RiverCourse> courses) {
        boolean caveCandidatePresent = false;
        for (RiverCourse course : courses) {
            if (hasCaveSegment(course)) {
                caveCandidatePresent = true;
                break;
            }
        }
        if (!caveCandidatePresent) {
            return new ValidationRaster(
                    List.of(),
                    new SurfaceRasterIndex(List.<SurfaceFootprint>of(), new Long2ObjectOpenHashMap<>())
            );
        }

        ArrayList<FootprintValidationCourseRaster> courseRasters = new ArrayList<>(courses.size());
        ArrayList<SurfaceFootprint> surfaces = new ArrayList<>();
        Long2ObjectOpenHashMap<HydrologyColumnSample> firstSamples = new Long2ObjectOpenHashMap<>();
        for (RiverCourse course : courses) {
            FootprintValidationCourseRaster raster = compileValidationCourse(course);
            courseRasters.add(raster);
            surfaces.add(raster.surface());
            for (HydrologyColumnSample sample : raster.columns()) {
                long packed = RiverFootprint.pack(sample.x(), sample.z());
                HydrologyColumnSample first = firstSamples.putIfAbsent(packed, sample);
                if (first != null) {
                    validateMatchingTerrainMetadata(first, sample);
                    FootprintMutableColumn merged = new FootprintMutableColumn(first);
                    merged.merge(sample);
                    firstSamples.put(packed, merged.build());
                }
            }
        }
        return new ValidationRaster(
                List.copyOf(courseRasters),
                new SurfaceRasterIndex(surfaces, firstSamples)
        );
    }

    ValidationRaster compileSurfaceDropValidation(RiverCourse course, SurfaceFootprint surface,
                                                   HydrologySurfaceDropRaster drops) {
        Objects.requireNonNull(course, "course");
        Objects.requireNonNull(surface, "surface");
        Objects.requireNonNull(drops, "drops");
        Long2ObjectOpenHashMap<HydrologyColumnSample> samples = new Long2ObjectOpenHashMap<>();
        for (HydrologyColumnSample sample : drops.columns()) {
            samples.put(RiverFootprint.pack(sample.x(), sample.z()), sample);
        }
        FootprintValidationCourseRaster raster = new FootprintValidationCourseRaster(
                course.id(), drops.columns(), surface);
        return new ValidationRaster(List.of(raster), new SurfaceRasterIndex(List.of(surface), samples));
    }

    int fullMaterializationCount() {
        return fullMaterializationCount;
    }

    FootprintValidationCourseRaster compileValidationCourse(RiverCourse course) {
        FootprintCourseRasterKey rasterKey = new FootprintCourseRasterKey(
                course.id(),
                course.type(),
                course.profileKey(),
                course.segments()
        );
        FootprintValidationCourseRaster cached = validationCourseRasters.get(rasterKey);
        if (cached != null) {
            return cached;
        }

        boolean caveCourse = hasCaveSegment(course);
        features.clear();
        Long2ObjectLinkedOpenHashMap<FootprintMutableColumn> columns = new Long2ObjectLinkedOpenHashMap<>();
        SurfaceFootprint surface = surfaceFootprint(course);
        for (SurfaceLayerColumn column : surface.columns()) {
            addLayer(columns, column.x(), column.z(), column.terrain(), column.layer());
        }
        boolean boundedRegionalDrops = addRegionalDrops(columns, course.id());
        SurfaceRasterIndex courseSurface = new SurfaceRasterIndex(List.of(surface), new Long2ObjectOpenHashMap<>());
        for (int segmentIndex = 0; segmentIndex < course.segments().size(); segmentIndex++) {
            HydraulicSegment segment = course.segments().get(segmentIndex);
            if (boundedRegionalDrops && segment.type().isSurface() && segment.fallingFluid()) {
                continue;
            }
            boolean firstSegment = segmentIndex == 0;
            boolean clipStart = segmentIndex > 0
                    && rasterizer.segmentsJoin(course.segments().get(segmentIndex - 1), segment);
            boolean clipEnd = segmentIndex + 1 < course.segments().size()
                    && rasterizer.segmentsJoin(segment, course.segments().get(segmentIndex + 1));
            if (caveCourse && (segment.type().isUnderground()
                    || segment.type().isDeepFluid() || segment.type().isSurface() && segment.fallingFluid())) {
                rasterizer.rasterizeSegment(
                        columns,
                        course,
                        segment,
                        firstSegment,
                        clipStart,
                        clipEnd,
                        true,
                        courseSurface
                );
            }
        }
        FootprintValidationCourseRaster raster = new FootprintValidationCourseRaster(
                course.id(),
                buildValidationColumns(columns),
                surface
        );
        validationCourseRasters.put(rasterKey, raster);
        if (validationCourseRasters.size() > VALIDATION_RASTER_CACHE_SIZE) {
            validationCourseRasters.remove(validationCourseRasters.sequencedKeySet().getFirst());
        }
        return raster;
    }

    boolean hasCaveSegment(RiverCourse course) {
        for (HydraulicSegment segment : course.segments()) {
            if (segment.type().isUnderground() || segment.type().isDeepFluid()
                    || segment.type().isSurface() && segment.fallingFluid()) {
                return true;
            }
        }
        return false;
    }

    List<HydrologyColumnSample> buildValidationColumns(
            Long2ObjectLinkedOpenHashMap<FootprintMutableColumn> columns
    ) {
        ArrayList<HydrologyColumnSample> built = new ArrayList<>(columns.size());
        for (FootprintMutableColumn column : columns.values()) {
            built.add(column.build());
        }
        built.sort(Comparator.comparingLong(
                (HydrologyColumnSample sample) -> RiverFootprint.pack(sample.x(), sample.z())
        ));
        return List.copyOf(built);
    }

    void mergeSurfaceDrop(Long2ObjectLinkedOpenHashMap<FootprintMutableColumn> columns,
                          Long2ObjectLinkedOpenHashMap<FootprintMutableColumn> dropColumns) {
        List<HydrologyColumnSample> constrained = HydrologySurfaceDropBankBounds.constrain(
                buildValidationColumns(dropColumns), settings.surface().banks().erosion().excavation().maximumWidth());
        for (HydrologyColumnSample sample : constrained) {
            long key = RiverFootprint.pack(sample.x(), sample.z());
            FootprintMutableColumn column = columns.get(key);
            if (column == null) {
                columns.put(key, new FootprintMutableColumn(sample));
            } else {
                column.merge(sample);
            }
        }
    }

    static void validateMatchingTerrainMetadata(
            HydrologyColumnSample first,
            HydrologyColumnSample second
    ) {
        if (first.x() != second.x()
                || first.z() != second.z()
                || first.naturalHeight() != second.naturalHeight()
                || first.seaLevel() != second.seaLevel()
                || first.ocean() != second.ocean()
                || !first.parentBiomeKey().equals(second.parentBiomeKey())) {
            throw new IllegalStateException("Hydrology course footprints disagree on terrain metadata at "
                    + first.x() + "," + first.z() + ".");
        }
    }

    RiverFootprint compileCourse(RiverCourse course) {
        FootprintCourseRasterKey rasterKey = new FootprintCourseRasterKey(
                course.id(),
                course.type(),
                course.profileKey(),
                course.segments()
        );
        RiverFootprint cached = courseFootprints.get(rasterKey);
        if (cached != null) {
            return cached;
        }
        features.clear();
        Long2ObjectLinkedOpenHashMap<FootprintMutableColumn> columns = new Long2ObjectLinkedOpenHashMap<>();
        SurfaceFootprint surface = surfaceFootprint(course);
        for (SurfaceLayerColumn column : surface.columns()) {
            addLayer(columns, column.x(), column.z(), column.terrain(), column.layer());
        }
        boolean boundedRegionalDrops = addRegionalDrops(columns, course.id());
        SurfaceRasterIndex courseSurface = new SurfaceRasterIndex(List.of(surface), new Long2ObjectOpenHashMap<>());
        for (int segmentIndex = 0; segmentIndex < course.segments().size(); segmentIndex++) {
            HydraulicSegment segment = course.segments().get(segmentIndex);
            if (SurfaceFootprintCompiler.exposedSegment(segment)
                    || boundedRegionalDrops && segment.type().isSurface() && segment.fallingFluid()) {
                continue;
            }
            boolean clipStart = segmentIndex > 0
                    && rasterizer.segmentsJoin(course.segments().get(segmentIndex - 1), segment);
            boolean clipEnd = segmentIndex + 1 < course.segments().size()
                    && rasterizer.segmentsJoin(segment, course.segments().get(segmentIndex + 1));
            rasterizer.rasterizeSegment(
                    columns,
                    course,
                    segment,
                    segmentIndex == 0,
                    clipStart,
                    clipEnd,
                    false,
                    courseSurface
            );
        }
        RiverFootprint footprint = build(columns);
        courseFootprints.put(rasterKey, footprint);
        if (courseFootprints.size() > COURSE_FOOTPRINT_CACHE_SIZE) {
            courseFootprints.remove(courseFootprints.sequencedKeySet().getFirst());
        }
        return footprint;
    }

    private boolean addRegionalDrops(Long2ObjectLinkedOpenHashMap<FootprintMutableColumn> columns, long courseId) {
        HydrologySurfaceDropRaster drops = regionalDropRasters.get(courseId);
        if (drops == null) {
            return false;
        }
        for (HydrologyColumnSample sample : drops.columns()) {
            for (HydrologyColumnLayer layer : sample.layers()) {
                addLayer(columns, sample.x(), sample.z(), sampleTerrainBasis(sample.x(), sample.z()), layer);
            }
        }
        return true;
    }

    RiverFootprint build(Long2ObjectLinkedOpenHashMap<FootprintMutableColumn> columns) {
        LinkedHashMap<Long, HydrologyColumnSample> immutable = new LinkedHashMap<>();
        for (Long2ObjectMap.Entry<FootprintMutableColumn> entry : columns.long2ObjectEntrySet()) {
            immutable.put(entry.getLongKey(), entry.getValue().build());
        }
        return new RiverFootprint(immutable);
    }

    HydrologyColumnLayer terrainContainedCaveLayer(
            RiverCourse course,
            HydraulicSegment segment,
            HydrologyTerrainSample terrain,
            HydrologyColumnLayer layer,
            int x,
            int z,
            SurfaceRasterIndex plannedSurface
    ) {
        if (!terrainRoofedLayer(course, segment)) {
            return layer;
        }
        boolean seaCave = course.type() == RiverCourseType.SEA_CAVE;
        int plannedTerrainHeight = plannedSurface.resolve(
                x,
                z,
                terrain.naturalHeight()
        );
        boolean surfaceChannel = plannedSurface.ownsSurfaceChannelAt(
                x,
                z,
                course.id()
        );
        int maximumCeiling = surfaceChannel ? Integer.MAX_VALUE : plannedTerrainHeight - 1;
        for (int[] offset : HORIZONTAL_NEIGHBORS) {
            int neighborX = x + offset[0];
            int neighborZ = z + offset[1];
            HydrologyTerrainSample neighborTerrain = sampleTerrainBasis(neighborX, neighborZ);
            if (neighborTerrain == null) {
                continue;
            }
            if (seaCave && neighborTerrain.ocean()) {
                // The sea face is the intended opening; the seabed never lowers the chamber roof.
                continue;
            }
            if (plannedSurface.ownsSurfaceChannelAt(neighborX, neighborZ, course.id())) {
                continue;
            }
            int neighborSurface = plannedSurface.resolve(
                    neighborX,
                    neighborZ,
                    neighborTerrain.naturalHeight()
            );
            maximumCeiling = Math.min(maximumCeiling, neighborSurface - 1);
        }
        if (seaCave && Math.min(layer.ceilingY(), maximumCeiling) <= layer.fluidHeadY()) {
            // A sea-cave column needs air between the sea and its roof; a roof at or below sea level is not carved.
            return null;
        }
        if (maximumCeiling == Integer.MAX_VALUE || layer.ceilingY() <= maximumCeiling) {
            return layer;
        }
        if (maximumCeiling < layer.fluidHeadY()) {
            return null;
        }
        return withCeiling(layer, maximumCeiling);
    }

    HydrologyColumnLayer withCeiling(HydrologyColumnLayer layer, int ceiling) {
        return new HydrologyColumnLayer(
                layer.feature(),
                layer.bedY(),
                layer.fluidHeadY(),
                Math.min(layer.ceilingY(), ceiling),
                layer.channel(),
                layer.shore(),
                layer.grading(),
                layer.connectedFluid(),
                layer.fallingFluid(),
                layer.receivingPool(),
                layer.terrainOwned(),
                layer.fluidOwned(),
                layer.oceanApron(),
                layer.profileKey(),
                layer.surfaceBiomeKey(),
                layer.mouthBiomeKey(),
                layer.shoreBiomeKey(),
                layer.bankBiomeKey(),
                layer.floodedCaveBiomeKey()
        );
    }

    HydrologyColumnLayer regularLayer(
            RiverCourse course,
            HydraulicSegment segment,
            HydrologyPoint point,
            FootprintLayerShape shape,
            HydrologyTerrainSample terrain,
            double distance,
            int deltaX,
            int deltaZ,
            int flowX,
            int flowZ,
            boolean source,
            boolean channel,
            boolean shore,
            boolean grading,
            boolean falling,
            boolean receiving,
            HydrologyFeatureRef[] pointFeatures
    ) {
        HydrologyFeatureRef feature = feature(
                course,
                segment,
                point.x() + deltaX,
                shape.fluidHead(),
                point.z() + deltaZ,
                flowX,
                flowZ,
                source && deltaX == 0 && deltaZ == 0,
                channel,
                shore,
                grading,
                channel && falling,
                channel && receiving,
                pointFeatures
        );
        return regularLayer(
                course,
                segment,
                shape,
                terrain,
                distance,
                point.x() + deltaX,
                point.z() + deltaZ,
                channel,
                shore,
                grading,
                falling,
                receiving,
                feature
        );
    }

    HydrologyColumnLayer regularLayer(
            RiverCourse course,
            HydraulicSegment segment,
            FootprintLayerShape shape,
            HydrologyTerrainSample terrain,
            double distance,
            int worldX,
            int worldZ,
            boolean channel,
            boolean shore,
            boolean grading,
            boolean falling,
            boolean receiving,
            HydrologyFeatureRef feature
    ) {
        HydrologyPlannerSettings.ChannelShape channelShape = channelGeometry.channelShape(segment.type());
        double ellipsoidScale = channel && (shape.ellipsoid() || shape.archedChannel())
                ? channelGeometry.ellipsoidScale(shape.channelRadius(), distance, channelShape.bedRoundness())
                : 0D;
        int bed = resolvedBed(
                segment,
                shape,
                channelShape,
                terrain,
                distance,
                worldX,
                worldZ,
                channel,
                ellipsoidScale
        );
        boolean surfaceDrop = segment.type().isSurface() && segment.fallingFluid();
        if (surfaceDrop && channel && !falling) {
            int maximumIncision = terrain.surfacePolicy().maximumIncision(settings.surface().maximumIncision());
            int allowedIncision = Math.min(maximumIncision,
                    (int) StrictMath.floor(maximumIncision * terrain.incisionMultiplier()));
            bed = Math.max(bed, Math.min(shape.fluidHead(), terrain.naturalHeight() - allowedIncision));
            if (bed == shape.fluidHead()) {
                channel = false;
                shore = true;
                grading = true;
            }
        }
        if (surfaceDrop && !channel) {
            HydrologyPlannerSettings.Excavation excavation = settings.surface().banks().erosion().excavation();
            double bankDistance = Math.max(0D, distance - shape.channelRadius());
            bed = bankDistance > excavation.maximumWidth() ? terrain.naturalHeight()
                    : Math.max(bed, terrain.naturalHeight() - excavation.maximumDepth());
        }
        int fluidHead = channel ? shape.fluidHead() : bed;
        int ceiling = channel ? localCeiling(shape, ellipsoidScale, segment, channelShape, worldX, worldZ) : fluidHead;
        if (channel && terrainRoofedLayer(course, segment)) {
            ceiling = Math.max(fluidHead, Math.min(ceiling, terrain.naturalHeight() - 1));
        }
        boolean terrainOwned = !falling && (!surfaceDrop || channel || bed < terrain.naturalHeight());
        return new HydrologyColumnLayer(
                feature,
                bed,
                fluidHead,
                ceiling,
                channel,
                !falling && shore,
                terrainOwned && (grading || shore),
                channel,
                channel && falling,
                channel && receiving,
                terrainOwned,
                channel,
                false,
                course.profileKey(),
                terrain.surfaceBiomeKey(),
                terrain.mouthBiomeKey(),
                terrain.shoreBiomeKey(),
                terrain.bankBiomeKey(),
                terrain.floodedCaveBiomeKey()
        );
    }

    FootprintRasterStencil rasterStencil(FootprintLayerShape shape) {
        FootprintRasterStencilKey key = new FootprintRasterStencilKey(
                shape.channelRadius(),
                shape.shoreWidth(),
                shape.gradingWidth()
        );
        FootprintRasterStencil cached = rasterStencils.get(key);
        if (cached != null) {
            return cached;
        }
        FootprintRasterStencil stencil = buildRasterStencil(key);
        rasterStencils.put(key, stencil);
        return stencil;
    }

    FootprintRasterStencil buildRasterStencil(FootprintRasterStencilKey key) {
        double totalRadius = key.channelRadius() + key.shoreWidth() + key.gradingWidth();
        int blockRadius = (int) StrictMath.ceil(totalRadius);
        FootprintRasterStencilBuilder builder = new FootprintRasterStencilBuilder();
        for (int deltaZ = -blockRadius; deltaZ <= blockRadius; deltaZ++) {
            for (int deltaX = -blockRadius; deltaX <= blockRadius; deltaX++) {
                double distance = StrictMath.hypot(deltaX, deltaZ);
                if (distance > totalRadius + 0.25D) {
                    continue;
                }
                boolean channel = distance <= key.channelRadius() + 0.25D;
                boolean shore = !channel && distance <= key.channelRadius() + key.shoreWidth() + 0.25D;
                boolean grading = !channel && !shore && distance <= totalRadius + 0.25D;
                if (!channel && !shore && !grading) {
                    continue;
                }
                builder.add(deltaX, deltaZ, distance);
            }
        }
        return builder.build();
    }

    void addOceanApron(
            Long2ObjectLinkedOpenHashMap<FootprintMutableColumn> columns,
            RiverCourse course,
            HydraulicSegment segment,
            HydrologyPoint point,
            int x,
            int z,
            double distance,
            int flowX,
            int flowZ,
            HydrologyTerrainSample terrain,
            HydrologyFeatureRef[] pointFeatures
    ) {
        if (!oceanApronEligible(segment, distance)) {
            return;
        }
        addOceanApronLayer(columns, course, segment, point, x, z, flowX, flowZ, terrain, pointFeatures);
    }

    void addOceanApronLayer(
            Long2ObjectLinkedOpenHashMap<FootprintMutableColumn> columns,
            RiverCourse course,
            HydraulicSegment segment,
            HydrologyPoint point,
            int x,
            int z,
            int flowX,
            int flowZ,
            HydrologyTerrainSample terrain,
            HydrologyFeatureRef[] pointFeatures
    ) {
        int fluidHead = Math.min(settings.seaLevel(), point.y());
        HydrologyFeatureRef feature = feature(
                course,
                segment,
                x,
                fluidHead,
                z,
                flowX,
                flowZ,
                false,
                true,
                false,
                false,
                false,
                false,
                pointFeatures
        );
        HydrologyColumnLayer layer = new HydrologyColumnLayer(
                feature,
                fluidHead,
                fluidHead,
                segment.type() == HydrologyFeatureType.COASTAL_GROTTO
                        ? fluidHead + settings.outlets().coastalGrotto().headroom()
                        : fluidHead,
                true,
                false,
                false,
                true,
                false,
                false,
                false,
                false,
                true,
                course.profileKey(),
                terrain.surfaceBiomeKey(),
                terrain.mouthBiomeKey(),
                terrain.shoreBiomeKey(),
                terrain.bankBiomeKey(),
                terrain.floodedCaveBiomeKey()
        );
        addLayer(columns, x, z, terrain, layer);
    }

    int gradedBed(
            FootprintLayerShape shape,
            HydrologyTerrainSample terrain,
            double distance
    ) {
        double transitionWidth = shape.shoreWidth() + shape.gradingWidth();
        if (transitionWidth <= 0D) {
            return terrain.naturalHeight();
        }
        double bankDistance = Math.max(0D, distance - shape.channelRadius());
        int targetBed = Math.addExact(shape.fluidHead(), 1);
        double progress = channelGeometry.smoothStep(Math.min(1D, bankDistance / transitionWidth));
        int blendedBed = (int) StrictMath.round(
                targetBed + (terrain.naturalHeight() - targetBed) * progress
        );
        return Math.min(terrain.naturalHeight(), blendedBed);
    }

    int resolvedBed(
            HydraulicSegment segment,
            FootprintLayerShape shape,
            HydrologyPlannerSettings.ChannelShape channelShape,
            HydrologyTerrainSample terrain,
            double distance,
            int worldX,
            int worldZ,
            boolean channel,
            double ellipsoidScale
    ) {
        int resolved;
        if (shape.ellipsoid() || shape.archedChannel()) {
            resolved = channel
                    ? localBed(segment, shape, channelShape, ellipsoidScale, worldX, worldZ)
                    : terrain.naturalHeight();
        } else if (!shape.roundedSurfaceBed()) {
            resolved = channel ? shape.bed() : gradedBed(shape, terrain, distance);
        } else if (channel) {
            double normalized = Math.min(1D, distance / Math.max(1D, shape.channelRadius()));
            double thalwegFraction = settings.surface().banks().flow().waterfallThalwegFraction();
            double shoulder = Math.max(
                    0D,
                    (normalized - thalwegFraction) / (1D - thalwegFraction)
            );
            int maximumDepth = Math.max(1, shape.fluidHead() - shape.bed());
            double roughness = SurfaceNoise.signed(
                    HydrologyHash.mix(segment.id(), HydrologyChannelGeometry.ORGANIC_BED_VARIATION_SALT),
                    worldX,
                    worldZ,
                    channelShape.roughnessWavelength()
            ) * channelShape.bedRoughness();
            double depthScale = Math.max(0.55D, 1D + roughness);
            int localDepth = 1 + (int) StrictMath.floor(
                    (maximumDepth - 1D)
                            * (1D - StrictMath.pow(shoulder, channelShape.bedRoundness()))
                            * depthScale
                            + 1.0E-9D
            );
            localDepth = Math.min(maximumDepth, localDepth);
            if (segment.type().isDrop()) {
                localDepth = Math.min(
                        maximumDepth,
                        Math.max(localDepth, settings.geometry().drops().stepLimit(segment.type()) + 1)
                );
            }
            resolved = shape.fluidHead() - localDepth;
        } else {
            resolved = gradedBed(shape, terrain, distance);
        }
        return Math.min(resolved, terrain.naturalHeight());
    }

    int localBed(
            HydraulicSegment segment,
            FootprintLayerShape shape,
            HydrologyPlannerSettings.ChannelShape channelShape,
            double scale,
            int worldX,
            int worldZ
    ) {
        if (!shape.ellipsoid() && !shape.archedChannel()) {
            return shape.bed();
        }
        int lowerExtent = shape.fluidHead() - shape.bed();
        double roughness = SurfaceNoise.signed(
                HydrologyHash.mix(segment.id(), HydrologyChannelGeometry.ORGANIC_BED_VARIATION_SALT),
                worldX,
                worldZ,
                channelShape.roughnessWavelength()
        ) * channelShape.bedRoughness();
        int localExtent = (int) StrictMath.ceil(lowerExtent * scale * Math.max(0.55D, 1D + roughness));
        localExtent = Math.min(lowerExtent, localExtent);
        if (shape.archedChannel()) {
            localExtent = Math.max(1, localExtent);
        }
        if (segment.type().isDrop()) {
            localExtent = Math.max(localExtent, settings.geometry().drops().stepLimit(segment.type()) + 1);
        }
        return shape.fluidHead() - localExtent;
    }

    int localCeiling(
            FootprintLayerShape shape,
            double scale,
            HydraulicSegment segment,
            HydrologyPlannerSettings.ChannelShape channelShape,
            int worldX,
            int worldZ
    ) {
        if (!shape.ellipsoid() && !shape.archedChannel()) {
            return shape.ceiling();
        }
        int upperExtent = shape.ceiling() - shape.fluidHead();
        double extent = upperExtent * scale;
        if (channelShape.ceilingRoughness() > 0D) {
            double roughness = SurfaceNoise.signed(
                    HydrologyHash.mix(segment.id(), HydrologyChannelGeometry.ORGANIC_CEILING_VARIATION_SALT),
                    worldX,
                    worldZ,
                    channelShape.roughnessWavelength()
            ) * channelShape.ceilingRoughness();
            extent *= Math.max(0.55D, 1D + roughness);
        }
        int localExtent = (int) StrictMath.floor(extent);
        if (shape.archedChannel()) {
            localExtent = Math.max(1, localExtent);
        }
        return shape.fluidHead() + localExtent;
    }

    /**
     * Layers whose ceiling follows the terrain: the underground transition of a surface river, and the
     * chamber of a standalone sea cave, which must never break the surface above it.
     */
    boolean terrainRoofedLayer(RiverCourse course, HydraulicSegment segment) {
        if (course.type() == RiverCourseType.SEA_CAVE) {
            return segment.type() == HydrologyFeatureType.COASTAL_GROTTO;
        }
        return course.type() == RiverCourseType.SURFACE
                && (segment.type() == HydrologyFeatureType.UNDERGROUND_POOL
                || segment.type() == HydrologyFeatureType.UNDERGROUND_DROP);
    }

    void addLayer(
            Long2ObjectLinkedOpenHashMap<FootprintMutableColumn> columns,
            int x,
            int z,
            HydrologyTerrainSample terrain,
            HydrologyColumnLayer layer
    ) {
        long packed = RiverFootprint.pack(x, z);
        FootprintMutableColumn column = columns.get(packed);
        if (column == null) {
            column = new FootprintMutableColumn(x, z, terrain, settings.seaLevel());
            columns.put(packed, column);
        }
        column.add(layer);
    }

    HydrologyTerrainSample sampleTerrain(int x, int z) {
        long packed = RiverFootprint.pack(x, z);
        HydrologyTerrainSample cached = terrainSamples.get(packed);
        if (cached != null) {
            return cached;
        }
        HydrologyTerrainSample sampled = sampler.sample(x, z);
        if (sampled != null) {
            terrainSamples.put(packed, sampled);
            if (naturalSampler == null) {
                terrainBases.put(packed, sampled);
            }
        }
        return sampled;
    }

    HydrologyTerrainSample sampleTerrainBasis(int x, int z) {
        if (naturalSampler == null) {
            return sampleTerrain(x, z);
        }
        long packed = RiverFootprint.pack(x, z);
        HydrologyTerrainSample cached = terrainBases.get(packed);
        if (cached != null) {
            return cached;
        }
        HydrologyTerrainSample sampled = naturalSampler.sampleBasisWithoutSlope(x, z);
        if (sampled != null) {
            terrainBases.put(packed, sampled);
        }
        return sampled;
    }

    HydrologyRoutingTerrainSampler.NaturalClassification classifyNatural(int x, int z) {
        if (naturalSampler == null) {
            return HydrologyRoutingTerrainSampler.NaturalClassification.UNAVAILABLE;
        }
        long packed = RiverFootprint.pack(x, z);
        HydrologyRoutingTerrainSampler.NaturalClassification cached = naturalClassifications.get(packed);
        if (cached != null) {
            return cached;
        }
        HydrologyRoutingTerrainSampler.NaturalClassification sampled = Objects.requireNonNull(
                naturalSampler.classifyNatural(x, z),
                "Hydrology natural terrain classifier returned null at " + x + "," + z
        );
        naturalClassifications.put(packed, sampled);
        return sampled;
    }

    boolean elevatedSeaLevelSurfaceColumn(HydraulicSegment segment, HydrologyTerrainSample terrain, int fluidHead) {
        return segment.type().isSurface() && terrain.naturalHeight() <= settings.seaLevel()
                && fluidHead > settings.seaLevel();
    }

    boolean naturallySubmergedSurfaceColumn(
            HydraulicSegment segment,
            HydrologyTerrainSample terrain
    ) {
        return segment.type().isSurface() && terrain.naturalHeight() < settings.seaLevel();
    }

    boolean oceanApronEligible(HydraulicSegment segment, double distance) {
        return (segment.type() == HydrologyFeatureType.MOUTH
                || segment.type() == HydrologyFeatureType.COASTAL_GROTTO)
                && distance <= apronReach(segment) + 0.25D;
    }

    /**
     * How far from the centerline the ocean apron reaches. A coastal grotto chamber is as wide as its
     * horizontal radius, so its apron reaches at least that far: every sea column the chamber touches
     * must carry the apron that declares the sea face an intentional opening.
     */
    int apronReach(HydraulicSegment segment) {
        int reach = settings.outlets().maximumOceanApron();
        if (segment.type() == HydrologyFeatureType.COASTAL_GROTTO) {
            return Math.max(reach, settings.outlets().coastalGrotto().horizontalRadius());
        }
        return reach;
    }

    /**
     * The organic chamber boundary can put a sea column one block past the apron reach while the land
     * column beside it is still carved; the apron follows the chamber there so the face stays open.
     */
    void addAdjacentSeaApron(
            Long2ObjectLinkedOpenHashMap<FootprintMutableColumn> columns,
            RiverCourse course,
            HydraulicSegment segment,
            HydrologyPoint point,
            int x,
            int z,
            int flowX,
            int flowZ,
            HydrologyFeatureRef[] pointFeatures
    ) {
        for (int[] offset : HORIZONTAL_NEIGHBORS) {
            int neighborX = x + offset[0];
            int neighborZ = z + offset[1];
            HydrologyTerrainSample neighborTerrain = sampleTerrainBasis(neighborX, neighborZ);
            if (neighborTerrain == null) {
                continue;
            }
            if (classifyNatural(neighborX, neighborZ) != HydrologyRoutingTerrainSampler.NaturalClassification.OCEAN
                    && !neighborTerrain.ocean()) {
                continue;
            }
            addOceanApronLayer(
                    columns,
                    course,
                    segment,
                    point,
                    neighborX,
                    neighborZ,
                    flowX,
                    flowZ,
                    neighborTerrain,
                    pointFeatures
            );
        }
    }

    HydrologyFeatureRef feature(
            RiverCourse course,
            HydraulicSegment segment,
            int x,
            int y,
            int z,
            int flowX,
            int flowZ,
            boolean source,
            boolean channel,
            boolean shore,
            boolean grading,
            boolean falling,
            boolean receiving,
            HydrologyFeatureRef[] pointFeatures
    ) {
        int role = source ? 6 : falling ? 4 : receiving ? 5 : channel ? 1 : shore ? 2 : grading ? 3 : 0;
        HydrologyFeatureRef cached = pointFeatures[role];
        if (cached != null) {
            return cached;
        }
        int featureFlowX = Integer.compare(flowX, 0);
        int featureFlowZ = Integer.compare(flowZ, 0);
        FootprintFeatureKey key = new FootprintFeatureKey(
                course.id(),
                segment.id(),
                segment.type(),
                role,
                featureFlowX,
                featureFlowZ
        );
        HydrologyFeatureRef feature = features.get(key);
        if (feature == null) {
            feature = new HydrologyFeatureRef(
                    HydrologyHash.mix(
                            course.id(),
                            segment.id(),
                            segment.type().ordinal(),
                            role,
                            featureFlowX,
                            featureFlowZ
                    ),
                    segment.type(),
                    course.id(),
                    segment.id(),
                    x,
                    y,
                    z,
                    featureFlowX,
                    featureFlowZ,
                    source
            );
            features.put(key, feature);
        }
        pointFeatures[role] = feature;
        return feature;
    }

    int flowDelta(List<HydrologyPoint> centerline, int pointIndex, boolean xAxis) {
        HydrologyPoint from;
        HydrologyPoint to;
        if (centerline.size() == 1) {
            return 0;
        }
        if (pointIndex < centerline.size() - 1) {
            from = centerline.get(pointIndex);
            to = centerline.get(pointIndex + 1);
        } else {
            from = centerline.get(pointIndex - 1);
            to = centerline.get(pointIndex);
        }
        return Integer.compare(xAxis ? to.x() : to.z(), xAxis ? from.x() : from.z());
    }

    record Sampling(
            HydrologyTerrainSampler sampler,
            HydrologyGeometrySampler geometrySampler,
            HydrologyNaturalTerrainSampler naturalSampler
    ) {
        Sampling {
            Objects.requireNonNull(sampler, "sampler");
            Objects.requireNonNull(geometrySampler, "geometrySampler");
        }
    }

    final class ValidationRaster {
        private final List<FootprintValidationCourseRaster> courseRasters;
        private final Long2ObjectOpenHashMap<List<HydrologyColumnSample>> columnsByCourse;
        private final SurfaceRasterIndex surfaceRaster;
        private final RiverFootprint materializedSurface;
        private final int columnReferenceCount;
        private List<HydrologyColumnSample> mergedColumns;

        private ValidationRaster(
                List<FootprintValidationCourseRaster> courseRasters,
                SurfaceRasterIndex surfaceRaster
        ) {
            this(courseRasters, surfaceRaster, null);
        }

        private ValidationRaster(
                List<FootprintValidationCourseRaster> courseRasters,
                SurfaceRasterIndex surfaceRaster,
                RiverFootprint materializedSurface
        ) {
            this.courseRasters = List.copyOf(Objects.requireNonNull(courseRasters, "courseRasters"));
            this.surfaceRaster = Objects.requireNonNull(surfaceRaster, "surfaceRaster");
            this.materializedSurface = materializedSurface;
            this.columnsByCourse = new Long2ObjectOpenHashMap<>(courseRasters.size());
            int references = 0;
            for (FootprintValidationCourseRaster raster : courseRasters) {
                if (columnsByCourse.put(raster.courseId(), raster.columns()) != null) {
                    throw new IllegalStateException("Duplicate hydrology validation course " + raster.courseId());
                }
                references += raster.columns().size();
            }
            this.columnReferenceCount = references;
        }

        List<HydrologyColumnSample> columnsForCourse(long courseId) {
            List<HydrologyColumnSample> columns = columnsByCourse.get(courseId);
            return columns == null ? List.of() : columns;
        }

        List<HydrologyColumnSample> columns() {
            List<HydrologyColumnSample> cached = mergedColumns;
            if (cached != null) {
                return cached;
            }
            Long2ObjectLinkedOpenHashMap<FootprintMutableColumn> merged = new Long2ObjectLinkedOpenHashMap<>();
            for (FootprintValidationCourseRaster raster : courseRasters) {
                for (HydrologyColumnSample sample : raster.columns()) {
                    long packed = RiverFootprint.pack(sample.x(), sample.z());
                    FootprintMutableColumn column = merged.get(packed);
                    if (column == null) {
                        merged.put(packed, new FootprintMutableColumn(sample));
                    } else {
                        column.merge(sample);
                    }
                }
            }
            cached = buildValidationColumns(merged);
            mergedColumns = cached;
            return cached;
        }

        HydrologyCaveVoxelViewFactory.PlannedSurface plannedSurface() {
            if (materializedSurface == null) {
                return surfaceRaster;
            }
            return new MaterializedSurface(materializedSurface);
        }

        HydrologyColumnSample surfaceColumnAt(int x, int z) {
            return materializedSurface == null ? surfaceRaster.fluidColumns.get(RiverFootprint.pack(x, z))
                    : materializedSurface.sample(x, z).orElse(null);
        }

        HydrologyColumnSample surfaceColumnAt(int x, int z, int naturalHeight) {
            if (materializedSurface != null) {
                HydrologyColumnSample sample = materializedSurface.sample(x, z).orElse(null);
                if (sample != null && sample.naturalHeight() != naturalHeight) {
                    throw new IllegalStateException("Hydrology surface and cave rasters disagree on natural terrain at "
                            + x + "," + z + ".");
                }
                return sample;
            }
            return surfaceRaster.surfaceColumnAt(x, z, naturalHeight);
        }

        boolean ownsSurfaceChannelAt(int x, int z, int naturalHeight, long courseId) {
            if (materializedSurface != null) {
                HydrologyColumnSample sample = materializedSurface.sample(x, z).orElse(null);
                if (sample == null) {
                    return false;
                }
                if (sample.naturalHeight() != naturalHeight) {
                    throw new IllegalStateException("Hydrology surface and cave rasters disagree on natural terrain at "
                            + x + "," + z + ".");
                }
                return ownsSurfaceChannel(sample, courseId);
            }
            return surfaceRaster.ownsSurfaceChannelAt(x, z, courseId);
        }

        boolean ownsSurfaceChannelAt(int x, int z, long courseId) {
            if (materializedSurface == null) {
                return surfaceRaster.ownsSurfaceChannelAt(x, z, courseId);
            }
            HydrologyColumnSample sample = materializedSurface.sample(x, z).orElse(null);
            return sample != null && ownsSurfaceChannel(sample, courseId);
        }

        private boolean ownsSurfaceChannel(HydrologyColumnSample sample, long courseId) {
            for (HydrologyColumnLayer layer : sample.layers()) {
                if (layer.feature().courseId() == courseId
                        && layer.feature().type().isSurface()
                        && layer.channel()
                        && layer.terrainOwned()) {
                    return true;
                }
            }
            return false;
        }

        ValidationRaster withMaterializedSurface(RiverFootprint footprint) {
            return new ValidationRaster(courseRasters, surfaceRaster, Objects.requireNonNull(footprint));
        }

        int columnReferenceCount() {
            return columnReferenceCount;
        }
    }

    private record MaterializedSurface(RiverFootprint footprint) implements HydrologyCaveVoxelViewFactory.PlannedSurface {
        @Override
        public int resolve(int x, int z, int naturalHeight) {
            return footprint.sample(x, z).map(HydrologyColumnSample::terrainHeight).orElse(naturalHeight);
        }

        @Override
        public boolean ownsTerrain(int x, int z) {
            HydrologyColumnSample sample = footprint.sample(x, z).orElse(null);
            return sample != null && sample.primarySurfaceLayer().filter(HydrologyColumnLayer::terrainOwned).isPresent();
        }
    }

    final class SurfaceRasterIndex implements HydrologyCaveVoxelViewFactory.PlannedSurface {
        private final Long2ObjectOpenHashMap<HydrologyColumnSample> surfaceColumns;
        private final Long2ObjectOpenHashMap<HydrologyColumnSample> fluidColumns;
        private final Long2IntOpenHashMap validationNaturalHeights;

        private SurfaceRasterIndex(
                List<SurfaceFootprint> footprints,
                Long2ObjectOpenHashMap<HydrologyColumnSample> validationSamples
        ) {
            Long2ObjectLinkedOpenHashMap<FootprintMutableColumn> merged = new Long2ObjectLinkedOpenHashMap<>();
            ArrayList<SurfaceFootprint> allFootprints = new ArrayList<>(footprints);
            for (SurfaceFootprint regional : regionalSurfaceFootprints.values()) {
                if (!allFootprints.contains(regional)) {
                    allFootprints.add(regional);
                }
            }
            for (SurfaceFootprint footprint : allFootprints) {
                for (SurfaceLayerColumn column : footprint.columns()) {
                    addLayer(merged, column.x(), column.z(), column.terrain(), column.layer());
                }
            }
            this.surfaceColumns = new Long2ObjectOpenHashMap<>(merged.size());
            for (Long2ObjectMap.Entry<FootprintMutableColumn> entry : merged.long2ObjectEntrySet()) {
                surfaceColumns.put(entry.getLongKey(), entry.getValue().build());
            }
            for (HydrologySurfaceDropRaster drops : regionalDropRasters.values()) {
                for (HydrologyColumnSample sample : drops.columns()) {
                    addSurfaceFluid(merged, sample);
                }
            }
            for (HydrologyColumnSample sample : validationSamples.values()) {
                addSurfaceFluid(merged, sample);
            }
            this.fluidColumns = new Long2ObjectOpenHashMap<>(merged.size());
            for (Long2ObjectMap.Entry<FootprintMutableColumn> entry : merged.long2ObjectEntrySet()) {
                fluidColumns.put(entry.getLongKey(), entry.getValue().build());
            }
            this.validationNaturalHeights = new Long2IntOpenHashMap(validationSamples.size());
            for (HydrologyColumnSample sample : validationSamples.values()) {
                validationNaturalHeights.put(
                        RiverFootprint.pack(sample.x(), sample.z()),
                        sample.naturalHeight()
                );
            }
        }

        private void addSurfaceFluid(Long2ObjectLinkedOpenHashMap<FootprintMutableColumn> columns, HydrologyColumnSample sample) {
            for (HydrologyColumnLayer layer : sample.layers()) {
                if (layer.publishesSurfaceFluid()) {
                    addLayer(columns, sample.x(), sample.z(), sampleTerrainBasis(sample.x(), sample.z()), layer);
                }
            }
        }

        @Override
        public int resolve(int x, int z, int naturalHeight) {
            long packed = RiverFootprint.pack(x, z);
            HydrologyColumnSample sample = surfaceColumns.get(packed);
            if (sample != null) {
                return sample.terrainHeight();
            }
            if (validationNaturalHeights.containsKey(packed)) {
                return validationNaturalHeights.get(packed);
            }
            return naturalHeight;
        }

        @Override
        public boolean ownsTerrain(int x, int z) {
            HydrologyColumnSample sample = surfaceColumns.get(RiverFootprint.pack(x, z));
            return sample != null && sample.primarySurfaceLayer().filter(HydrologyColumnLayer::terrainOwned).isPresent();
        }

        private HydrologyColumnSample surfaceColumnAt(int x, int z, int naturalHeight) {
            HydrologyColumnSample sample = surfaceColumns.get(RiverFootprint.pack(x, z));
            if (sample != null && sample.naturalHeight() != naturalHeight) {
                throw new IllegalStateException("Hydrology surface and cave rasters disagree on natural terrain at "
                        + x + "," + z + ".");
            }
            return sample;
        }

        private boolean ownsSurfaceChannelAt(int x, int z, long courseId) {
            HydrologyColumnSample sample = surfaceColumns.get(RiverFootprint.pack(x, z));
            if (sample == null) {
                return false;
            }
            for (HydrologyColumnLayer layer : sample.layers()) {
                if (layer.feature().courseId() == courseId
                        && layer.feature().type().isSurface()
                        && layer.channel()
                        && layer.terrainOwned()) {
                    return true;
                }
            }
            return false;
        }
    }
}
