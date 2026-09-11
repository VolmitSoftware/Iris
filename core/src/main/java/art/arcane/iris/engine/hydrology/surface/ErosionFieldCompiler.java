package art.arcane.iris.engine.hydrology.surface;

import art.arcane.iris.engine.hydrology.HydrologyHash;
import art.arcane.iris.engine.hydrology.HydrologyCandidateRejection;
import art.arcane.iris.engine.hydrology.HydrologyPlannerSettings;
import art.arcane.iris.engine.hydrology.HydrologySurfaceDropRaster;
import art.arcane.iris.engine.hydrology.HydrologyTerrainSample;
import art.arcane.iris.engine.hydrology.HydrologyTerrainSampler;
import art.arcane.iris.engine.hydrology.RiverFootprint;
import art.arcane.iris.engine.object.IrisRiverBedProfile;
import art.arcane.iris.engine.object.IrisRiverBlendStyle;
import it.unimi.dsi.fastutil.longs.Long2DoubleOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongArrayFIFOQueue;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;

import java.util.Objects;

public final class ErosionFieldCompiler {
    private static final int[][] CARDINALS = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
    private static final int[][] DIAGONALS = {{1, 1}, {1, -1}, {-1, 1}, {-1, -1}};
    private static final long OUTLINE_SALT = 0x4f55544c494e45L;
    private static final long BED_SALT = 0x424544L;
    private static final long SOURCE_POND_SALT = 0x504f4e4453524345L;
    private static final long TERMINAL_POND_SALT = 0x504f4e4454524dL;
    private static final int POND_RIM_SAMPLES_PER_BLOCK = 8;
    private static final double EPSILON = 1.0E-9D;

    private final HydrologyPlannerSettings.Surface surface;
    private final HydrologyTerrainSampler sampler;
    private final int seaLevel;

    public ErosionFieldCompiler(
            HydrologyPlannerSettings.Surface surface,
            HydrologyTerrainSampler sampler,
            int seaLevel
    ) {
        this.surface = Objects.requireNonNull(surface, "surface");
        this.sampler = Objects.requireNonNull(sampler, "sampler");
        this.seaLevel = seaLevel;
    }

    public ErosionField compile(
            long courseSeed,
            SurfaceCenterline centerline,
            ChannelProfile channel,
            ValleyProfile valley,
            SurfaceTerminal terminal,
            int apronLimit
    ) {
        return compile(courseSeed, centerline, channel, valley, terminal, apronLimit, surface.banks().ponds());
    }

    /**
     * @param ponds the ponds to dig at the course ends; a river passes the configured ones, a standing
     *              pool passes none
     */
    public ErosionField compile(
            long courseSeed,
            SurfaceCenterline centerline,
            ChannelProfile channel,
            ValleyProfile valley,
            SurfaceTerminal terminal,
            int apronLimit,
            HydrologyPlannerSettings.Ponds ponds
    ) {
        return compile(courseSeed, centerline, channel, valley, terminal, apronLimit, ponds, SurfaceRasterContext.none());
    }

    public ErosionField compile(
            long courseSeed,
            SurfaceCenterline centerline,
            ChannelProfile channel,
            ValleyProfile valley,
            SurfaceTerminal terminal,
            int apronLimit,
            HydrologyPlannerSettings.Ponds ponds,
            SurfaceRasterContext context
    ) {
        Objects.requireNonNull(context, "context");
        SurfaceBounds bounds = context.bounds();
        SurfaceBounds samplingBounds = bounds == null ? null : bounds.expand(2);
        SurfaceRasterContext samplingContext = new SurfaceRasterContext(samplingBounds, context.drops(), context.boundary());
        HydrologyPlannerSettings.Banks banks = surface.banks();
        HydrologyPlannerSettings.Erosion erosion = banks.erosion();
        HydrologyPlannerSettings.Channel channelSettings = banks.channel();
        HydrologyPlannerSettings.Flow flow = banks.flow();
        int count = terminal == SurfaceTerminal.OCEAN_MOUTH ? centerline.size() : valley.exposedStations();
        double roughness = banks.roughness();
        double shoreWidth = surface.shoreWidth();
        double shoreRise = erosion.shoreRise();
        int sink = banks.sink();
        boolean[] basin = basins(valley, channel, count);
        double[] bendOffsets = SurfaceBendProfile.offsets(centerline, channel);
        double[][] blendWidth = blendWidths(centerline, channel, valley, count);
        Long2IntOpenHashMap nearest = new Long2IntOpenHashMap();
        nearest.defaultReturnValue(-1);
        Long2DoubleOpenHashMap distance = new Long2DoubleOpenHashMap();
        for (int station = 0; station < count; station++) {
            double widest = Math.max(blendWidth[station][0], blendWidth[station][1]);
            // The shore biome band a policy asks for can be wider than the geometric shore and the
            // eroded valley; the search radius has to reach it so those columns get a shore role.
            double stationBand = stationShoreBiomeBand(centerline, station, shoreWidth);
            double stationShore = stationShoreWidth(centerline, station, shoreWidth);
            int radius = (int) StrictMath.ceil(channel.halfWidth()[station] * (1D + roughness) + Math.max(stationShore + widest, stationBand)) + 1;
            int stationX = centerline.x()[station];
            int stationZ = centerline.z()[station];
            if (samplingBounds != null && !samplingBounds.intersects(stationX, stationZ, radius)) {
                continue;
            }
            int minimumZ = samplingBounds == null ? -radius : Math.max(-radius, samplingBounds.minimumZ() - stationZ);
            int maximumZ = samplingBounds == null ? radius : Math.min(radius, samplingBounds.maximumZ() - stationZ);
            int minimumX = samplingBounds == null ? -radius : Math.max(-radius, samplingBounds.minimumX() - stationX);
            int maximumX = samplingBounds == null ? radius : Math.min(radius, samplingBounds.maximumX() - stationX);
            for (int deltaZ = minimumZ; deltaZ <= maximumZ; deltaZ++) {
                for (int deltaX = minimumX; deltaX <= maximumX; deltaX++) {
                    int cellX = stationX + deltaX;
                    int cellZ = stationZ + deltaZ;
                    double cellDistance = centerline.distanceToSegment(station, cellX, cellZ);
                    if (cellDistance > radius) {
                        continue;
                    }
                    long key = RiverFootprint.pack(cellX, cellZ);
                    int existing = nearest.get(key);
                    // A cell on a station's cross-section row ties between the two segments meeting
                    // there; the later station owns it so the row uses the head its own ring bounded.
                    if (existing < 0
                            || cellDistance < distance.get(key) - EPSILON
                            || Math.abs(cellDistance - distance.get(key)) <= EPSILON && station > existing) {
                        nearest.put(key, station);
                        distance.put(key, cellDistance);
                    }
                }
            }
        }
        int oceanStart = oceanStart(centerline, valley, terminal);
        Long2ObjectOpenHashMap<SurfaceColumn> columns = new Long2ObjectOpenHashMap<>(nearest.size());
        LongArrayList wetKeys = new LongArrayList();
        for (long key : nearest.keySet()) {
            int station = nearest.get(key);
            double cellDistance = distance.get(key);
            int cellX = RiverFootprint.unpackX(key);
            int cellZ = RiverFootprint.unpackZ(key);
            int head = valley.head()[station];
            if (!context.boundary().allows(cellX, cellZ) || context.drops().connects(cellX, cellZ, head)) {
                continue;
            }
            HydrologyTerrainSample terrain = sampler.sample(cellX, cellZ);
            double halfWidth = channel.halfWidth()[station];
            double outline = halfWidth * (1D + roughness * SurfaceNoise.signed(
                    courseSeed ^ OUTLINE_SALT, cellX, cellZ, banks.roughnessWavelength()));
            outline = Math.max(
                    Math.max(0.5D, channelSettings.outlineMinimumRatio() * halfWidth),
                    Math.min(channelSettings.outlineMaximumRatio() * halfWidth, outline));
            boolean wet = cellDistance <= outline + 0.25D;
            boolean mouthLand = terminal == SurfaceTerminal.OCEAN_MOUTH && head == seaLevel
                    && SurfaceCellAdmission.mouthLand(terrain, seaLevel);
            if (!SurfaceCellAdmission.writable(terrain, seaLevel) && !mouthLand) {
                if (sampler.receivingWater(cellX, cellZ, seaLevel) && wet && terminal == SurfaceTerminal.OCEAN_MOUTH
                        && station >= valley.exposedStations() - 1
                        && station - oceanStart < apronLimit
                        && cellDistance <= apronLimit + 0.25D) {
                    columns.put(key, new SurfaceColumn(
                            cellX, cellZ, terrain, station, SurfaceRole.CHANNEL, terrain.naturalHeight(), seaLevel, true));
                }
                continue;
            }
            int natural = terrain.naturalHeight();
            if (wet) {
                double normalized = Math.min(1D, cellDistance / Math.max(0.5D, outline));
                normalized = SurfaceBendProfile.normalizedDistance(normalized,
                        side(centerline, station, cellX, cellZ), bendOffsets[station]);
                double depth = channel.depth()[station];
                double local = 1D + (depth - 1D) * bedFactor(normalized, erosion)
                        + erosion.bedNoise() * roughness * SurfaceNoise.signed(courseSeed ^ BED_SALT, cellX, cellZ, banks.roughnessWavelength())
                        + (basin[station] ? flow.plungeBasinDepth() : 0D);
                int bed = Math.min(natural, head - Math.max(1, (int) StrictMath.round(local)));
                columns.put(key, new SurfaceColumn(cellX, cellZ, terrain, station, SurfaceRole.CHANNEL, bed, head, false));
                wetKeys.add(key);
                continue;
            }
            int bankTop = head + sink;
            // The geometric shore is the column's own policy width where it sets one, so a beach can be
            // wide on one bank and narrow on the other.
            double shore = terrain.shoreWidth(shoreWidth);
            // The SHORE role is the shore biome band: the column's policy width, or the geometric shore
            // width where no policy sets one. It is independent of the flattened geometric shore and of
            // the eroded valley, so a wide band reaches over untouched ground and a zero band leaves the
            // geometric shore with the bank biome.
            boolean shoreBiome = cellDistance <= outline + terrain.shoreBiomeWidth(shoreWidth) + 0.25D;
            SurfaceRole dryRole = shoreBiome ? SurfaceRole.SHORE : SurfaceRole.BANK;
            double bankDistance = cellDistance - outline;
            if (bankDistance > erosion.excavation().maximumWidth()) {
                if (shoreBiome) {
                    columns.put(key, new SurfaceColumn(cellX, cellZ, terrain, station, SurfaceRole.SHORE, natural, natural, false));
                }
                continue;
            }
            if (cellDistance <= outline + shore + 0.25D) {
                int height = Math.min(natural, bankTop + (int) StrictMath.round(shoreRise * benchProgress(cellDistance, outline, shore)));
                height = boundedBankHeight(natural, height, erosion.excavation());
                columns.put(key, new SurfaceColumn(cellX, cellZ, terrain, station, dryRole, height, height, false));
                continue;
            }
            int shoreTop = bankTop + (int) StrictMath.round(shoreRise);
            int cut = natural - shoreTop;
            if (cut <= 0 || !erosion.enabled() || !terrain.erosion()) {
                if (shoreBiome) {
                    columns.put(key, new SurfaceColumn(cellX, cellZ, terrain, station, SurfaceRole.SHORE, natural, natural, false));
                }
                continue;
            }
            double side = side(centerline, station, cellX, cellZ);
            double width = blendWidth[station][side < 0D ? 0 : 1];
            width = Math.min(width, Math.max(0.25D, erosion.excavation().maximumWidth() - shore));
            double progress = (cellDistance - outline - shore) / width;
            if (progress >= 1D) {
                if (shoreBiome) {
                    columns.put(key, new SurfaceColumn(cellX, cellZ, terrain, station, SurfaceRole.SHORE, natural, natural, false));
                }
                continue;
            }
            int height = Math.min(natural, (int) StrictMath.round(shoreTop + cut * blend(progress, erosion)));
            height = boundedBankHeight(natural, height, erosion.excavation());
            columns.put(key, new SurfaceColumn(cellX, cellZ, terrain, station, dryRole, height, height, false));
        }
        if (count > 0) {
            pond(columns, wetKeys, courseSeed ^ SOURCE_POND_SALT, ponds.source(), centerline, 0, valley.head()[0], samplingContext);
            if (terminal == SurfaceTerminal.SINKHOLE) {
                pond(columns, wetKeys, courseSeed ^ TERMINAL_POND_SALT, ponds.terminal(), centerline, count - 1, valley.head()[count - 1], samplingContext);
            }
        }
        int uncontained = contain(columns, wetKeys, sink, bounds, context.drops());
        connectSteps(columns, wetKeys);
        return validate(columns, wetKeys, centerline, terminal, bounds, uncontained);
    }

    /**
     * A round bowl around an end station holding that station's head: wet inside its outline, a shore
     * ring and an eroded rim outside it like the channel's own. Channel water inside the bowl joins
     * the pond at the pond's level; dry channel columns give way to the bowl. The radius is chosen per
     * course within the configured range and shrinks where the ground around the rim falls below the
     * water, so the pond never sits above the bank that has to hold it; no radius fits, no pond.
     */
    private void pond(
            Long2ObjectOpenHashMap<SurfaceColumn> columns,
            LongArrayList wetKeys,
            long seed,
            HydrologyPlannerSettings.Pond pond,
            SurfaceCenterline centerline,
            int station,
            int head,
            SurfaceRasterContext context
    ) {
        SurfaceBounds bounds = context.bounds();
        if (!pond.enabled()) {
            return;
        }
        HydrologyPlannerSettings.Banks banks = surface.banks();
        HydrologyPlannerSettings.Erosion erosion = banks.erosion();
        HydrologyPlannerSettings.Channel channelSettings = banks.channel();
        double roughness = banks.roughness();
        double shoreWidth = surface.shoreWidth();
        double shoreRise = erosion.shoreRise();
        int sink = banks.sink();
        int centreX = centerline.x()[station];
        int centreZ = centerline.z()[station];
        int maximumReach = pond.maximumRadius() * 2 + banks.maximumBlendWidth() + (int) StrictMath.ceil(shoreWidth) + 2;
        if (bounds != null && !bounds.intersects(centreX, centreZ, maximumReach)) {
            return;
        }
        // One bowl, one beach: the pond takes the shore width and the erosion switch its centre asks for.
        HydrologyTerrainSample centre = sampler.sample(centreX, centreZ);
        double shore = centre == null ? shoreWidth : centre.shoreWidth(shoreWidth);
        boolean erode = centre == null || centre.erosion();
        int radius = pondRadius(seed, pond, centreX, centreZ, head + sink, roughness);
        if (radius < 1) {
            return;
        }
        int bankTop = head + sink;
        int shoreTop = bankTop + (int) StrictMath.round(shoreRise);
        int reach = (int) StrictMath.ceil(radius * (1D + roughness) + shore + banks.maximumBlendWidth()) + 1;
        for (int deltaZ = -reach; deltaZ <= reach; deltaZ++) {
            for (int deltaX = -reach; deltaX <= reach; deltaX++) {
                double distance = Math.sqrt((double) deltaX * deltaX + (double) deltaZ * deltaZ);
                if (distance > reach) {
                    continue;
                }
                int cellX = centreX + deltaX;
                int cellZ = centreZ + deltaZ;
                if (bounds != null && !bounds.contains(cellX, cellZ)
                        || !context.boundary().allows(cellX, cellZ)
                        || context.drops().connects(cellX, cellZ, head)) {
                    continue;
                }
                HydrologyTerrainSample terrain = sampler.sample(cellX, cellZ);
                if (!SurfaceCellAdmission.writable(terrain, seaLevel)) {
                    continue;
                }
                int natural = terrain.naturalHeight();
                long key = RiverFootprint.pack(cellX, cellZ);
                SurfaceColumn existing = columns.get(key);
                if (existing != null && existing.apron()) {
                    continue;
                }
                double outline = radius * (1D + roughness * SurfaceNoise.signed(
                        seed ^ OUTLINE_SALT, cellX, cellZ, banks.roughnessWavelength()));
                outline = Math.max(
                        Math.max(0.5D, channelSettings.outlineMinimumRatio() * radius),
                        Math.min(channelSettings.outlineMaximumRatio() * radius, outline));
                if (distance <= outline + 0.25D) {
                    double normalized = Math.min(1D, distance / Math.max(0.5D, outline));
                    double local = 1D + (pond.depth() - 1D) * bedFactor(normalized, erosion)
                            + erosion.bedNoise() * roughness * SurfaceNoise.signed(seed ^ BED_SALT, cellX, cellZ, banks.roughnessWavelength());
                    int bed = Math.min(natural, head - Math.max(1, (int) StrictMath.round(local)));
                    boolean wasWet = existing != null && existing.role() == SurfaceRole.CHANNEL;
                    if (wasWet) {
                        bed = Math.min(bed, existing.height());
                    }
                    columns.put(key, new SurfaceColumn(cellX, cellZ, terrain, station, SurfaceRole.CHANNEL, bed, head, false));
                    if (!wasWet) {
                        wetKeys.add(key);
                    }
                    continue;
                }
                if (existing != null && existing.role() == SurfaceRole.CHANNEL) {
                    continue;
                }
                if (distance - outline > erosion.excavation().maximumWidth()) {
                    continue;
                }
                if (distance <= outline + shore + 0.25D) {
                    int height = Math.min(natural, bankTop + (int) StrictMath.round(shoreRise * benchProgress(distance, outline, shore)));
                    height = boundedBankHeight(natural, height, erosion.excavation());
                    columns.put(key, new SurfaceColumn(cellX, cellZ, terrain, station, SurfaceRole.SHORE, height, height, false));
                    continue;
                }
                int cut = natural - shoreTop;
                if (cut <= 0 || !erosion.enabled() || !erode) {
                    continue;
                }
                double width = Math.max(banks.minimumBlendWidth(),
                        Math.min(banks.maximumBlendWidth(), cut * banks.blendSlope() + erosion.blendBaseWidth()));
                width = Math.min(width, Math.max(0.25D, erosion.excavation().maximumWidth() - shore));
                double progress = (distance - outline - shore) / width;
                if (progress >= 1D) {
                    continue;
                }
                int height = Math.min(natural, (int) StrictMath.round(shoreTop + cut * blend(progress, erosion)));
                height = boundedBankHeight(natural, height, erosion.excavation());
                if (existing != null && existing.height() <= height) {
                    continue;
                }
                columns.put(key, new SurfaceColumn(cellX, cellZ, terrain, station, SurfaceRole.BANK, height, height, false));
            }
        }
    }

    /**
     * The largest radius in the configured range, chosen per course, whose rim ground everywhere
     * reaches the bank top; zero when even the smallest radius sits above the ground beside it.
     */
    private int pondRadius(long seed, HydrologyPlannerSettings.Pond pond, int centreX, int centreZ, int bankTop, double roughness) {
        int chosen = HydrologyHash.between(seed, pond.minimumRadius(), pond.maximumRadius());
        for (int radius = chosen; radius >= pond.minimumRadius(); radius--) {
            if (rimHolds(centreX, centreZ, radius * (1D + roughness) + 1D, bankTop)) {
                return radius;
            }
        }
        return 0;
    }

    private boolean rimHolds(int centreX, int centreZ, double rim, int bankTop) {
        int samples = Math.max(8, (int) StrictMath.ceil(rim * POND_RIM_SAMPLES_PER_BLOCK));
        for (int sample = 0; sample < samples; sample++) {
            double angle = sample * (2D * Math.PI / samples);
            for (double ring = rim; ring <= rim + 1D; ring += 0.5D) {
                int cellX = (int) StrictMath.round(centreX + Math.cos(angle) * ring);
                int cellZ = (int) StrictMath.round(centreZ + Math.sin(angle) * ring);
                HydrologyTerrainSample terrain = sampler.sample(cellX, cellZ);
                if (!SurfaceCellAdmission.writable(terrain, seaLevel) || terrain.naturalHeight() < bankTop) {
                    return false;
                }
            }
        }
        return true;
    }

    /** Eased blend from the bank top (0) to natural terrain (1); the curve exponent reshapes the valley side. */
    static double blend(double progress, double curve) {
        return SurfaceNoise.smoothStep(Math.pow(progress, curve));
    }

    /**
     * The configured valley side, from the shore top (0) to natural terrain (1). The smooth style is the
     * eased blend the valley always had; the others read the same progress through a different curve.
     */
    static double blend(double progress, HydrologyPlannerSettings.Erosion erosion) {
        double curve = erosion.blendCurve();
        IrisRiverBlendStyle style = erosion.style();
        if (style == IrisRiverBlendStyle.SMOOTH) {
            return blend(progress, curve);
        }
        return switch (style) {
            case LINEAR -> Math.pow(progress, curve);
            case CONCAVE -> 1D - Math.pow(1D - progress, curve);
            case TERRACED -> {
                int steps = erosion.terraceSteps();
                yield Math.floor(blend(progress, curve) * steps) / steps;
            }
            case CLIFF -> progress < erosion.cliffFraction() ? 0D : 1D;
            default -> blend(progress, curve);
        };
    }

    /** How far across the shore bench a cell sits, 0 at the waterline and 1 at the valley foot. */
    private static double benchProgress(double cellDistance, double outline, double shore) {
        if (shore <= 0D) {
            return 0D;
        }
        return Math.max(0D, Math.min(1D, (cellDistance - outline) / shore));
    }

    /**
     * The configured bed cross-section factor, 1 at full depth and 0 at the one-block channel edge;
     * the bowl is the broad thalweg the bed always had.
     */
    static double bedFactor(double normalized, HydrologyPlannerSettings.Erosion erosion) {
        double thalweg = erosion.thalwegFraction();
        IrisRiverBedProfile profile = erosion.bedProfile();
        if (profile == IrisRiverBedProfile.BOWL) {
            return bowl(normalized, thalweg);
        }
        return switch (profile) {
            case FLAT -> 1D;
            case V -> 1D - normalized;
            case U -> {
                if (normalized <= thalweg) {
                    yield 1D;
                }
                double u = Math.min(1D, (normalized - thalweg) / (1D - thalweg));
                yield 1D - u * u * u * u;
            }
            default -> bowl(normalized, thalweg);
        };
    }

    /**
     * Where the head steps down between neighbouring wet cells, the upper bed is lowered so its water
     * column reaches the lower head; the water stays one connected body across every step.
     */
    /** The shore biome band the terrain at a station asks for, or the geometric shore width without a policy. */
    private double stationShoreBiomeBand(SurfaceCenterline centerline, int station, double shoreWidth) {
        HydrologyTerrainSample terrain = sampler.sample(centerline.x()[station], centerline.z()[station]);
        return terrain == null ? shoreWidth : terrain.shoreBiomeWidth(shoreWidth);
    }

    /** The geometric shore the terrain under a station asks for, or the dimension width without a policy. */
    private double stationShoreWidth(SurfaceCenterline centerline, int station, double shoreWidth) {
        HydrologyTerrainSample terrain = sampler.sample(centerline.x()[station], centerline.z()[station]);
        return terrain == null ? shoreWidth : terrain.shoreWidth(shoreWidth);
    }

    private static void connectSteps(Long2ObjectOpenHashMap<SurfaceColumn> columns, LongArrayList wetKeys) {
        for (int index = 0; index < wetKeys.size(); index++) {
            long key = wetKeys.getLong(index);
            SurfaceColumn wet = columns.get(key);
            int bed = wet.height();
            for (int[] offset : CARDINALS) {
                SurfaceColumn neighbour = columns.get(RiverFootprint.pack(wet.x() + offset[0], wet.z() + offset[1]));
                if (neighbour == null || neighbour.role() != SurfaceRole.CHANNEL || neighbour.apron()) {
                    continue;
                }
                if (neighbour.headY() < wet.headY()) {
                    bed = Math.min(bed, neighbour.headY() - 1);
                }
            }
            if (bed < wet.height()) {
                columns.put(key, wet.withBed(bed));
            }
        }
    }

    /**
     * Every dry cell touching water, diagonals included, keeps the lip; only cardinal neighbours can
     * spill, so only they count as uncontained.
     */
    private int contain(Long2ObjectOpenHashMap<SurfaceColumn> columns, LongArrayList wetKeys, int sink, SurfaceBounds bounds, HydrologySurfaceDropRaster drops) {
        int uncontained = 0;
        for (int index = 0; index < wetKeys.size(); index++) {
            SurfaceColumn wet = columns.get(wetKeys.getLong(index));
            if (bounds != null && !bounds.contains(wet.x(), wet.z())) {
                continue;
            }
            uncontained += lip(columns, wet, CARDINALS, sink, true, drops);
            lip(columns, wet, DIAGONALS, sink, false, drops);
        }
        return uncontained;
    }

    /**
     * The bank beside water must reach the natural ground the head was taken from: the water's own
     * block level plus the sink. A neighbour lower than that is raised to it. The solver takes the head
     * from the banks it samples at each station. A missed low cell can retain its natural ground,
     * but cannot be raised above it. A shortfall below the water head stays uncontained and is counted.
     */
    private int lip(
            Long2ObjectOpenHashMap<SurfaceColumn> columns,
            SurfaceColumn wet,
            int[][] offsets,
            int sink,
            boolean countSpills,
            HydrologySurfaceDropRaster drops
    ) {
        int uncontained = 0;
        for (int[] offset : offsets) {
            int neighbourX = wet.x() + offset[0];
            int neighbourZ = wet.z() + offset[1];
            if (drops.connects(neighbourX, neighbourZ, wet.headY())) {
                continue;
            }
            long key = RiverFootprint.pack(neighbourX, neighbourZ);
            SurfaceColumn neighbour = columns.get(key);
            if (neighbour == null) {
                // Water that meets the sea at the mouth is not spilling; anything else beyond the field is.
                HydrologyTerrainSample terrain = sampler.sample(wet.x() + offset[0], wet.z() + offset[1]);
                if (countSpills && (terrain == null || terrain.naturalHeight() < wet.headY())
                        && !(wet.headY() == seaLevel && sampler.receivingWater(neighbourX, neighbourZ, seaLevel))) {
                    uncontained++;
                }
                continue;
            }
            if (neighbour.role() == SurfaceRole.CHANNEL) {
                continue;
            }
            int required = wet.headY() + sink;
            if (neighbour.height() < required) {
                int ceiling = neighbour.terrain().naturalHeight();
                int raised = Math.min(ceiling, required);
                neighbour = neighbour.withHeight(raised);
                columns.put(key, neighbour);
            }
            if (countSpills && neighbour.height() < wet.headY()) {
                uncontained++;
            }
        }
        return uncontained;
    }

    /** First mouth station whose center is ocean; the apron is measured from there, not from the mouth segment start. */
    private int oceanStart(SurfaceCenterline centerline, ValleyProfile valley, SurfaceTerminal terminal) {
        int count = centerline.size();
        if (terminal != SurfaceTerminal.OCEAN_MOUTH) {
            return count;
        }
        for (int station = Math.max(0, valley.exposedStations() - 1); station < count; station++) {
            if (sampler.receivingWater(centerline.x()[station], centerline.z()[station], seaLevel)) {
                return station;
            }
        }
        return count;
    }

    private static int boundedBankHeight(int natural, int target, HydrologyPlannerSettings.Excavation limits) {
        return Math.max(natural - limits.maximumDepth(), Math.min(natural, target));
    }

    private ErosionField validate(Long2ObjectOpenHashMap<SurfaceColumn> columns, LongArrayList wetKeys,
                                  SurfaceCenterline centerline, SurfaceTerminal terminal, SurfaceBounds bounds, int uncontained) {
        long excavation = 0L;
        int[] stationVolumes = new int[centerline.size()];
        int deepest = 0;
        for (SurfaceColumn column : columns.values()) {
            if (column.apron() || column.role() == SurfaceRole.CHANNEL
                    || bounds != null && !bounds.contains(column.x(), column.z())) {
                continue;
            }
            int cut = Math.max(0, column.terrain().naturalHeight() - column.height());
            excavation += cut;
            deepest = Math.max(deepest, cut);
            stationVolumes[column.station()] += cut;
        }
        int largestSection = SurfaceExcavationMetrics.maximumVolumePerBlock(centerline, stationVolumes);
        HydrologyPlannerSettings.Excavation limits = surface.banks().erosion().excavation();
        if (deepest > limits.maximumDepth() || largestSection > limits.maximumVolumePerBlock()) {
            return new ErosionField(columns, uncontained, HydrologyCandidateRejection.SURFACE_BANK_BUDGET,
                    largestSection, excavation);
        }
        if (uncontained > 0) {
            return new ErosionField(columns, uncontained, HydrologyCandidateRejection.SURFACE_WATER_CONTAINMENT,
                    uncontained, excavation);
        }
        if (bounds == null && terminal == SurfaceTerminal.OCEAN_MOUTH && !connectedToOcean(columns, wetKeys)) {
            return new ErosionField(columns, 0, HydrologyCandidateRejection.SURFACE_MOUTH_DISCONNECTED, 0, excavation);
        }
        return new ErosionField(columns, uncontained, null, 0, excavation);
    }

    private boolean connectedToOcean(Long2ObjectOpenHashMap<SurfaceColumn> columns, LongArrayList wetKeys) {
        if (wetKeys.isEmpty()) {
            return false;
        }
        long firstKey = wetKeys.getLong(0);
        for (int index = 1; index < wetKeys.size(); index++) {
            long key = wetKeys.getLong(index);
            SurfaceColumn candidate = columns.get(key);
            SurfaceColumn first = columns.get(firstKey);
            if (candidate.station() < first.station() || candidate.station() == first.station() && key < firstKey) {
                firstKey = key;
            }
        }
        LongOpenHashSet visited = new LongOpenHashSet(wetKeys.size());
        LongArrayFIFOQueue queue = new LongArrayFIFOQueue();
        visited.add(firstKey);
        queue.enqueue(firstKey);
        boolean ocean = false;
        while (!queue.isEmpty()) {
            SurfaceColumn current = columns.get(queue.dequeueLong());
            for (int[] offset : CARDINALS) {
                int x = current.x() + offset[0];
                int z = current.z() + offset[1];
                long key = RiverFootprint.pack(x, z);
                SurfaceColumn next = columns.get(key);
                if (current.headY() == seaLevel && current.height() < seaLevel
                        && sampler.receivingWater(x, z, seaLevel)) {
                    ocean = true;
                }
                if (next == null || next.role() != SurfaceRole.CHANNEL || next.height() >= next.headY()
                        || Math.max(current.height(), next.height()) >= Math.min(current.headY(), next.headY())) {
                    continue;
                }
                if (visited.add(key)) {
                    queue.enqueue(key);
                }
            }
        }
        if (!ocean) {
            return false;
        }
        for (int index = 0; index < wetKeys.size(); index++) {
            if (!visited.contains(wetKeys.getLong(index))) {
                return false;
            }
        }
        return true;
    }

    private boolean[] basins(ValleyProfile valley, ChannelProfile channel, int count) {
        HydrologyPlannerSettings.Flow flow = surface.banks().flow();
        boolean[] basin = new boolean[count];
        for (int station = 1; station < count; station++) {
            int drop = valley.head()[station - 1] - valley.head()[station];
            if (drop < flow.plungeBasinMinimumDrop()) {
                continue;
            }
            int length = Math.max(2, (int) StrictMath.round(flow.plungeBasinLengthRatio() * channel.halfWidth()[station]));
            for (int following = station; following < Math.min(count, station + length); following++) {
                basin[following] = true;
            }
        }
        return basin;
    }

    private double[][] blendWidths(SurfaceCenterline centerline, ChannelProfile channel, ValleyProfile valley, int count) {
        HydrologyPlannerSettings.Banks banks = surface.banks();
        HydrologyPlannerSettings.Erosion erosion = banks.erosion();
        double[][] widths = new double[count][2];
        if (!erosion.enabled()) {
            return widths;
        }
        for (int station = 0; station < count; station++) {
            double halfWidth = channel.halfWidth()[station];
            double probe = halfWidth + stationShoreWidth(centerline, station, surface.shoreWidth()) + Math.max(2D, halfWidth);
            int bankTop = valley.head()[station] + banks.sink();
            for (int side = 0; side < 2; side++) {
                double direction = side == 0 ? -1D : 1D;
                int probeX = (int) StrictMath.round(centerline.x()[station] + centerline.normalX(station) * probe * direction);
                int probeZ = (int) StrictMath.round(centerline.z()[station] + centerline.normalZ(station) * probe * direction);
                HydrologyTerrainSample terrain = sampler.sample(probeX, probeZ);
                double cut = terrain == null ? 0D : Math.max(0D, terrain.naturalHeight() - bankTop);
                double width = cut * banks.blendSlope() * channel.bankMultiplier()[station] + erosion.blendBaseWidth();
                widths[station][side] = Math.max(banks.minimumBlendWidth(), Math.min(banks.maximumBlendWidth(), width));
            }
        }
        double[][] smoothed = smoothWidths(widths, erosion.smoothingRadius());
        // A station whose ground asks for no erosion keeps its channel, its bench and its lip; the
        // averaging above may not carry a neighbour's valley into it.
        for (int station = 0; station < count; station++) {
            HydrologyTerrainSample centre = sampler.sample(centerline.x()[station], centerline.z()[station]);
            if (centre != null && !centre.erosion()) {
                smoothed[station][0] = 0D;
                smoothed[station][1] = 0D;
            }
        }
        return smoothed;
    }

    /**
     * Moving average along the course so the valley outline widens and narrows gradually instead of
     * stepping between neighbouring stations.
     */
    static double[][] smoothWidths(double[][] widths, int radius) {
        int count = widths.length;
        double[][] smoothed = new double[count][2];
        for (int side = 0; side < 2; side++) {
            double sum = 0D;
            int window = 0;
            int head = -1;
            int tail = 0;
            for (int station = 0; station < count; station++) {
                while (head + 1 < count && head + 1 <= station + radius) {
                    head++;
                    sum += widths[head][side];
                    window++;
                }
                while (tail < station - radius) {
                    sum -= widths[tail][side];
                    tail++;
                    window--;
                }
                smoothed[station][side] = sum / window;
            }
        }
        return smoothed;
    }

    /**
     * Bed depth factor across the channel: a level thalweg over the inner part of the width that eases
     * up to a one-block edge, so the channel reads as a broad bowl rather than a V-shaped trough.
     */
    static double bowl(double normalized, double thalwegFraction) {
        if (normalized <= thalwegFraction) {
            return 1D;
        }
        double t = Math.min(1D, (normalized - thalwegFraction) / (1D - thalwegFraction));
        return 1D - t * t * (3D - 2D * t);
    }

    private static double side(SurfaceCenterline centerline, int station, int cellX, int cellZ) {
        double deltaX = cellX - centerline.x()[station];
        double deltaZ = cellZ - centerline.z()[station];
        return deltaX * centerline.normalX(station) + deltaZ * centerline.normalZ(station);
    }
}
