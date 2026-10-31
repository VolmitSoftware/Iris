package art.arcane.iris.generation.hydrology.raster;

import art.arcane.iris.generation.hydrology.config.IrisBankStyle;
import art.arcane.iris.generation.hydrology.cave.HydrologyCaveCell;
import art.arcane.iris.generation.hydrology.plan.HydrologySettings;
import art.arcane.iris.generation.hydrology.underground.SeaInlet;
import art.arcane.iris.generation.hydrology.water.HydrologyTier;
import art.arcane.iris.generation.hydrology.water.Pond;
import art.arcane.iris.generation.hydrology.water.Reach;
import art.arcane.iris.generation.hydrology.water.ReachStyle;

import java.util.Arrays;

final class RasterComposer {
    private static final int CHUNK = 16;
    private static final int GRID = CHUNK + 2;
    private static final int CELLS = GRID * GRID;
    private static final int[] SIDES = {1, -1, GRID, -GRID};
    private static final int SHORE_WIDTH = 2;
    private static final double MAX_CONE_SLOPE = 2D;
    private static final double CONE_EXPONENT = 0.8D;
    private static final double ROUNDING = 1e-6D;
    private static final int SLOPE_REACH = 2;
    private static final int RAY_STEP = 4;

    private final HydrologySettings settings;
    private final ChunkFeatures features;
    private final ChannelFluids channelFluids;
    private final int originX;
    private final int originZ;
    private final double[] exact = new double[CELLS];
    private final int[] natural = new int[CELLS];
    private final int[] terrain = new int[CELLS];
    private final int[] head = new int[CELLS];
    private final WaterRole[] role = new WaterRole[CELLS];
    private final long[] feature = new long[CELLS];
    private final HydrologyTier[] tier = new HydrologyTier[CELLS];
    private final String[] fluid = new String[CELLS];
    private final int[] pond = new int[CELLS];
    private final double[] distance = new double[CELLS];
    private final long[] nearFeature = new long[CELLS];
    private final HydrologyTier[] nearTier = new HydrologyTier[CELLS];
    private final ReachCursor cursor = new ReachCursor();
    private final int[] inletColumn = new int[2];
    private NaturalHeights naturalHeight;

    RasterComposer(HydrologySettings settings, ChunkFeatures features, ChannelFluids channelFluids, int chunkX, int chunkZ) {
        this.settings = settings;
        this.features = features;
        this.channelFluids = channelFluids;
        this.originX = chunkX * CHUNK - 1;
        this.originZ = chunkZ * CHUNK - 1;
        Arrays.fill(head, Integer.MIN_VALUE);
        Arrays.fill(role, WaterRole.NONE);
        Arrays.fill(pond, -1);
        Arrays.fill(distance, Double.POSITIVE_INFINITY);
    }

    ChunkWaterRaster compose(NaturalHeights naturalHeight) {
        this.naturalHeight = naturalHeight;
        for (int cell = 0; cell < CELLS; cell++) {
            if (!corner(cell)) {
                exact[cell] = naturalHeight.height(originX + cell % GRID, originZ + cell / GRID);
                natural[cell] = (int) Math.round(exact[cell]);
                terrain[cell] = natural[cell];
            }
        }
        for (int cell = 0; cell < CELLS; cell++) {
            if (!corner(cell)) {
                column(cell);
            }
        }
        gradeRiffles();
        raiseDams();
        bermEdges();
        measureLakeShores();
        nameShores();
        return pack();
    }

    private void column(int cell) {
        int x = originX + cell % GRID;
        int z = originZ + cell / GRID;
        int ground = natural[cell];
        SeaInlet inlet = features.openInletAt(x, z, inletColumn);
        if (inlet != null) {
            head[cell] = level(inlet.waterLevel());
            terrain[cell] = Math.min(Math.min(ground, inletColumn[0]), head[cell] - 1);
            role[cell] = WaterRole.INLET;
            feature[cell] = inlet.id();
            fluid[cell] = HydrologyCaveCell.SEA_FLUID;
            return;
        }
        int lake = features.lakeAt(x, z, ground);
        int pondIndex = lake >= 0 ? -1 : features.pondAt(x, z);
        boolean shore = interior(cell) && lake < 0 && pondIndex < 0;
        features.segments().nearest(x, z, shore ? ReachSegments.Scope.SHORE : ReachSegments.Scope.CHANNEL, cursor);
        double variation = BedProfile.variation(settings.seed(), x, z);
        if (lake >= 0) {
            Pond water = features.lake(lake);
            standing(cell, water, trench(ground, level(water.level()), ground, variation, BedProfile.detail(settings.seed(), x, z)));
            return;
        }
        if (pondIndex >= 0) {
            Pond water = features.pond(pondIndex);
            pond[cell] = pondIndex;
            double detail = BedProfile.detail(settings.seed(), x, z);
            int floor = interior(cell) ? pondFloor(pondIndex, level(water.level()), x, z, ground, variation, detail) : ground;
            standing(cell, water, trench(floor, level(water.level()), ground, variation, detail));
            return;
        }
        if (cursor.channel.reach != null) {
            channel(cell, x, z, ground, variation);
        } else if (interior(cell)) {
            dry(cell, x, z, ground, variation);
        }
    }

    private void standing(int cell, Pond water, int floor) {
        head[cell] = level(water.level());
        terrain[cell] = Math.min(floor, head[cell] - 1);
        role[cell] = WaterRole.POND;
        feature[cell] = water.id();
        fluid[cell] = water.fluid();
    }

    private int trench(int floor, int level, int ground, double variation, double detail) {
        ReachCursor.Choice hit = cursor.channel;
        if (hit.reach == null) {
            return floor;
        }
        return Math.min(floor, bed(hit, Math.min(level, level(hit.reach.grade()[hit.point()])), ground, variation, detail));
    }

    private void channel(int cell, int x, int z, int ground, double variation) {
        ReachCursor.Choice hit = cursor.channel;
        Reach reach = hit.reach;
        int point = hit.point();
        String area = channelFluids.channelFluid(x, z);
        head[cell] = level(reach.grade()[point]);
        terrain[cell] = bed(hit, head[cell], ground, variation, BedProfile.detail(settings.seed(), x, z));
        role[cell] = role(reach.style()[point]);
        feature[cell] = reach.id();
        tier[cell] = reach.tier();
        fluid[cell] = area != null ? area : reach.tier().fluid();
    }

    private static int bed(ReachCursor.Choice hit, int surface, int ground, double variation, double detail) {
        double width = hit.halfWidth();
        double ratio = width > 0D ? hit.distance / width : 0D;
        long cut = BedProfile.channelDepth(hit.depth(), ratio, variation, detail);
        return (int) Math.min(ground, surface - cut);
    }

    private void dry(int cell, int x, int z, int ground, double variation) {
        int cut = ground;
        int raise = Integer.MIN_VALUE;
        double bankEdge = Double.POSITIVE_INFINITY;
        ReachCursor.Choice bank = cursor.bank;
        if (bank.reach != null) {
            HydrologyTier bankTier = bank.reach.tier();
            int surface = level(bank.reach.grade()[bank.point()]);
            bankEdge = bank.distance - bank.halfWidth();
            double u = bankEdge / bankTier.bankBand();
            if (bankTier.bankStyle() != IrisBankStyle.CLIFF && bankTier.bankStyle() != IrisBankStyle.TERRACED) {
                u = BedProfile.position(u, -variation);
            }
            if (ground >= surface) {
                double cone = coneSlope(steepestToWater(x, z, bank, bankEdge)) * u * bankTier.bankBand();
                cut = BankProfile.cut(bankTier.bankStyle(), surface, ground, u, cone, noise(x, z));
            } else {
                raise = BankProfile.levee(bankTier.bankStyle(), surface, ground, u, noise(x, z), settings.deformation().maxFill());
            }
        }
        int nearPond = -1;
        double pondEdge = Double.POSITIVE_INFINITY;
        for (int index = 0; index < features.ponds(); index++) {
            double edge = features.pondShoreDistance(index, x, z);
            if (edge < pondEdge || edge == pondEdge && nearPond >= 0 && features.pond(index).id() < features.pond(nearPond).id()) {
                pondEdge = edge;
                nearPond = index;
            }
        }
        int taper = settings.ponds().shore().taper();
        boolean tapered = nearPond >= 0 && pondEdge <= taper;
        if (tapered && ground > level(features.pond(nearPond).level())) {
            cut = Math.min(cut, taperCut(level(features.pond(nearPond).level()), ground, BedProfile.position(pondEdge / taper, -variation)));
        }
        terrain[cell] = raise == Integer.MIN_VALUE ? cut : Math.max(cut, raise);
        if (bank.reach != null && (!tapered || bankEdge <= pondEdge)) {
            mark(cell, WaterRole.BANK, bank.reach.id(), bank.reach.tier());
        } else if (tapered) {
            mark(cell, WaterRole.BANK, features.pond(nearPond).id(), null);
        }
        nearestWater(cell, nearPond, pondEdge);
    }

    private void nearestWater(int cell, int nearPond, double pondEdge) {
        double best = Double.POSITIVE_INFINITY;
        if (cursor.edge.reach != null) {
            best = cursor.edge.distance;
            nearFeature[cell] = cursor.edge.reach.id();
            nearTier[cell] = cursor.edge.reach.tier();
        }
        if (nearPond >= 0 && pondEdge < best) {
            best = pondEdge;
            nearFeature[cell] = features.pond(nearPond).id();
            nearTier[cell] = null;
        }
        distance[cell] = best;
    }

    private double coneSlope(double slope) {
        HydrologySettings.Deformation deformation = settings.deformation();
        return Math.min(MAX_CONE_SLOPE, deformation.bankSlope() + deformation.bankSteepFactor() * Math.pow(slope, CONE_EXPONENT));
    }

    private double steepestToWater(int x, int z, ReachCursor.Choice bank, double bankEdge) {
        Reach reach = bank.reach;
        int next = Math.min(bank.segment + 1, reach.xs().length - 1);
        double centreX = reach.xs()[bank.segment] + (reach.xs()[next] - reach.xs()[bank.segment]) * bank.t;
        double centreZ = reach.zs()[bank.segment] + (reach.zs()[next] - reach.zs()[bank.segment]) * bank.t;
        double steepest = slopeAt(x, z);
        double length = Math.hypot(centreX - x, centreZ - z);
        if (length <= 0D) {
            return steepest;
        }
        double towardX = (centreX - x) / length;
        double towardZ = (centreZ - z) / length;
        for (double edge = 0D; edge < bankEdge; edge += RAY_STEP) {
            double back = bankEdge - edge;
            steepest = Math.max(steepest, slopeAt((int) Math.floor(x + towardX * back + 0.5D), (int) Math.floor(z + towardZ * back + 0.5D)));
        }
        return steepest;
    }

    private double slopeAt(int x, int z) {
        return Math.max(Math.abs(exactAt(x + SLOPE_REACH, z) - exactAt(x - SLOPE_REACH, z)),
                Math.abs(exactAt(x, z + SLOPE_REACH) - exactAt(x, z - SLOPE_REACH))) / (2D * SLOPE_REACH);
    }

    private double exactAt(int x, int z) {
        int gx = x - originX;
        int gz = z - originZ;
        if (gx >= 0 && gz >= 0 && gx < GRID && gz < GRID && !corner(gz * GRID + gx)) {
            return exact[gz * GRID + gx];
        }
        return naturalHeight.height(x, z);
    }

    private double noise(int x, int z) {
        float strength = settings.deformation().bankNoise();
        return strength == 0f ? 0D : BedProfile.roughness(settings.seed(), x, z) * strength;
    }

    private static int taperCut(int level, int ground, double u) {
        double rise = (ground - level) * BankProfile.shape(IrisBankStyle.SMOOTH, u);
        return (int) Math.clamp((long) Math.floor(level + rise), level, ground);
    }

    private int level(int planned) {
        return planned - Math.floorMod(planned - settings.seaLevel(), 2);
    }

    private void gradeRiffles() {
        int surface = Integer.MIN_VALUE;
        for (int cell = 0; cell < CELLS && surface == Integer.MIN_VALUE; cell++) {
            if (interior(cell)) {
                surface = head[cell];
            }
        }
        if (surface == Integer.MIN_VALUE || features.hasOnlyLevel(surface, settings.seaLevel())) {
            return;
        }
        RiffleProfile riffles = new RiffleProfile((originX + 1) >> 4, (originZ + 1) >> 4, this::headAt);
        for (int cell = 0; cell < CELLS; cell++) {
            if (interior(cell) && head[cell] != Integer.MIN_VALUE) {
                int x = originX + cell % GRID;
                int z = originZ + cell / GRID;
                terrain[cell] = riffles.bed(cell % GRID - 1, cell / GRID - 1, terrain[cell], head[cell],
                        BedProfile.variation(settings.seed(), x, z));
            }
        }
    }

    private int headAt(int x, int z) {
        int gx = x - originX;
        int gz = z - originZ;
        if (gx >= 0 && gx < GRID && gz >= 0 && gz < GRID && !corner(gz * GRID + gx)) {
            return head[gz * GRID + gx];
        }
        SeaInlet inlet = features.openInletAt(x, z, inletColumn);
        if (inlet != null) {
            return level(inlet.waterLevel());
        }
        int lake = features.lakeCovers(x, z) ? features.lakeAt(x, z, naturalAt(x, z)) : -1;
        if (lake >= 0) {
            return level(features.lake(lake).level());
        }
        int pond = features.pondAt(x, z);
        if (pond >= 0) {
            return level(features.pond(pond).level());
        }
        features.segments().nearest(x, z, ReachSegments.Scope.CHANNEL, cursor);
        return cursor.channel.reach == null ? Integer.MIN_VALUE : level(cursor.channel.reach.grade()[cursor.channel.point()]);
    }

    private int pondFloor(int index, int level, int x, int z, int ground, double variation, double detail) {
        HydrologySettings.PondShore shore = settings.ponds().shore();
        double bankDistance = features.pondBankDistance(index, x, z);
        double u = shore.shelf() <= 0 ? 1D : Math.clamp((bankDistance - 1D) / shore.shelf(), 0D, 1D);
        long bowl = BedProfile.pondDepth(shore.maxDepth(), u, variation, detail);
        return (int) Math.min(ground, level - bowl);
    }

    private void raiseDams() {
        for (int cell = 0; cell < CELLS; cell++) {
            if (!interior(cell) || head[cell] != Integer.MIN_VALUE) {
                continue;
            }
            int crest = Integer.MIN_VALUE;
            long dam = 0L;
            for (int side : SIDES) {
                int index = pond[cell + side];
                if (index < 0) {
                    continue;
                }
                Pond held = features.pond(index);
                int level = level(held.level());
                if (held.damRaise() > 0 && terrain[cell] < level && level + 1 > crest) {
                    crest = level + 1;
                    dam = held.id();
                }
            }
            if (crest != Integer.MIN_VALUE) {
                terrain[cell] = Math.max(terrain[cell], crest);
                mark(cell, WaterRole.DAM, dam, null);
            }
        }
    }

    private void bermEdges() {
        int sea = settings.seaLevel();
        int maxFill = settings.deformation().maxFill();
        for (int cell = 0; cell < CELLS; cell++) {
            if (!interior(cell) || head[cell] != Integer.MIN_VALUE) {
                continue;
            }
            int surface = Integer.MIN_VALUE;
            for (int side : SIDES) {
                surface = Math.max(surface, head[cell + side]);
            }
            if (terrain[cell] >= surface || surface <= sea && terrain[cell] < sea) {
                continue;
            }
            terrain[cell] = Math.max(terrain[cell], Math.max(surface, Math.min(surface + 1, natural[cell] + maxFill)));
        }
    }

    private void measureLakeShores() {
        if (features.lakes() == 0) {
            return;
        }
        LakeShores shores = new LakeShores(features, this::naturalAt, originX + 1, originZ + 1);
        for (int cell = 0; cell < CELLS; cell++) {
            if (!interior(cell) || head[cell] != Integer.MIN_VALUE) {
                continue;
            }
            int x = originX + cell % GRID;
            int z = originZ + cell / GRID;
            long lower = features.lakeCovers(x, z) ? 0L : Long.MAX_VALUE;
            for (int lake = 0; lake < features.lakes() && lower > 0L; lake++) {
                lower = Math.min(lower, features.lakeReachSquared(lake, x, z));
            }
            if (lower != Long.MAX_VALUE && shores.find(x, z, lower, distance[cell])) {
                distance[cell] = shores.foundDistance();
                nearFeature[cell] = features.lake(shores.foundLake()).id();
                nearTier[cell] = null;
            }
        }
    }

    private int naturalAt(int x, int z) {
        int gx = x - originX;
        int gz = z - originZ;
        if (gx >= 0 && gz >= 0 && gx < GRID && gz < GRID && !corner(gz * GRID + gx)) {
            return natural[gz * GRID + gx];
        }
        return (int) Math.round(naturalHeight.height(x, z));
    }

    private void nameShores() {
        for (int cell = 0; cell < CELLS; cell++) {
            if (!interior(cell) || head[cell] != Integer.MIN_VALUE || role[cell] == WaterRole.DAM || shore(cell) > SHORE_WIDTH) {
                continue;
            }
            int wet = wettestNear(cell);
            long id = wet >= 0 ? feature[wet] : nearFeature[cell];
            HydrologyTier family = wet >= 0 ? tier[wet] : nearTier[cell];
            mark(cell, family != null ? WaterRole.SHORE : WaterRole.POND_SHORE, id, family);
        }
    }

    private int wettestNear(int cell) {
        int gx = cell % GRID;
        int gz = cell / GRID;
        int found = -1;
        int best = Integer.MAX_VALUE;
        for (int dz = -SHORE_WIDTH; dz <= SHORE_WIDTH; dz++) {
            for (int dx = -SHORE_WIDTH; dx <= SHORE_WIDTH; dx++) {
                int nx = gx + dx;
                int nz = gz + dz;
                int squared = dx * dx + dz * dz;
                if (nx >= 0 && nz >= 0 && nx < GRID && nz < GRID && squared < best && head[nz * GRID + nx] != Integer.MIN_VALUE) {
                    best = squared;
                    found = nz * GRID + nx;
                }
            }
        }
        return found;
    }

    private ChunkWaterRaster pack() {
        int columns = ChunkWaterRaster.COLUMNS;
        boolean affected = false;
        for (int lz = 0; lz < CHUNK && !affected; lz++) {
            for (int lx = 0; lx < CHUNK && !affected; lx++) {
                int cell = (lz + 1) * GRID + lx + 1;
                affected = role[cell] != WaterRole.NONE || terrain[cell] != natural[cell] || shore(cell) < ChunkWaterRaster.MAX_SHORE_DISTANCE;
            }
        }
        if (!affected) {
            return ChunkWaterRaster.EMPTY;
        }
        int[] terrainOut = new int[columns];
        int[] headOut = new int[columns];
        byte[] roleOut = new byte[columns];
        ChunkWaterRaster.Palette<HydrologyTier> tierOut = new ChunkWaterRaster.Palette<>(new HydrologyTier[columns]);
        ChunkWaterRaster.LongPalette featureOut = new ChunkWaterRaster.LongPalette();
        ChunkWaterRaster.Palette<String> fluidOut = new ChunkWaterRaster.Palette<>(new String[columns]);
        byte[] shoreOut = new byte[columns];
        for (int lz = 0; lz < CHUNK; lz++) {
            for (int lx = 0; lx < CHUNK; lx++) {
                int cell = (lz + 1) * GRID + lx + 1;
                int index = lz << 4 | lx;
                terrainOut[index] = terrain[cell];
                headOut[index] = head[cell];
                roleOut[index] = (byte) role[cell].ordinal();
                tierOut.set(index, tier[cell]);
                featureOut.set(index, feature[cell]);
                fluidOut.set(index, fluid[cell]);
                shoreOut[index] = (byte) shore(cell);
            }
        }
        return new ChunkWaterRaster(terrainOut, headOut, roleOut, tierOut, featureOut, fluidOut, settings.deformation().caveMask(), shoreOut);
    }

    private int shore(int cell) {
        if (head[cell] != Integer.MIN_VALUE) {
            return 0;
        }
        return (int) Math.clamp((long) Math.ceil(distance[cell] - ROUNDING), 1, ChunkWaterRaster.MAX_SHORE_DISTANCE);
    }

    private void mark(int cell, WaterRole marked, long id, HydrologyTier family) {
        role[cell] = marked;
        feature[cell] = id;
        tier[cell] = family;
    }

    private static WaterRole role(ReachStyle style) {
        return switch (style) {
            case CASCADE -> WaterRole.CASCADE;
            case WATERFALL -> WaterRole.WATERFALL;
            case MOUTH -> WaterRole.MOUTH;
            case RUN, RIFFLE, POND_OUTLET -> WaterRole.CHANNEL;
        };
    }

    private static boolean interior(int cell) {
        int gx = cell % GRID;
        int gz = cell / GRID;
        return gx >= 1 && gx <= CHUNK && gz >= 1 && gz <= CHUNK;
    }

    private static boolean corner(int cell) {
        int gx = cell % GRID;
        int gz = cell / GRID;
        return (gx == 0 || gx == GRID - 1) && (gz == 0 || gz == GRID - 1);
    }
}
