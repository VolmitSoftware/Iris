package art.arcane.iris.engine.hydrology.surface;

import art.arcane.iris.engine.hydrology.HydrologyCandidateRejection;
import art.arcane.iris.engine.hydrology.HydrologyPlannerSettings;
import art.arcane.iris.engine.hydrology.HydrologyTerrainSample;
import art.arcane.iris.engine.hydrology.HydrologyTerrainSampler;

import java.util.Objects;

public final class ValleyProfileSolver {
    private static final int BANK_LOOKAHEAD = SurfaceBankSupport.BANK_LOOKAHEAD;
    private final HydrologyPlannerSettings.Surface surface;
    private final HydrologyTerrainSampler sampler;
    private final int seaLevel;
    private final int minimumCourseLength;
    private final SurfaceBankSupport bankSupport;

    public ValleyProfileSolver(
            HydrologyPlannerSettings.Surface surface,
            HydrologyTerrainSampler sampler,
            int seaLevel,
            int minimumCourseLength
    ) {
        this.surface = Objects.requireNonNull(surface, "surface");
        this.sampler = Objects.requireNonNull(sampler, "sampler");
        this.seaLevel = seaLevel;
        this.minimumCourseLength = minimumCourseLength;
        this.bankSupport = new SurfaceBankSupport(surface, seaLevel);
    }

    public ValleyProfile solve(
            SurfaceCenterline centerline,
            ChannelProfile channel,
            SurfaceTerminal terminal,
            int terminalHead
    ) {
        int count = centerline.size();
        int[] crossMin = new int[count];
        int[] crossMax = new int[count];
        int[] centerNatural = new int[count];
        int[] channelIncision = new int[count];
        double[] incisionMultiplier = new double[count];
        double roughness = surface.banks().roughness();
        int exposed = count;
        for (int station = 0; station < count; station++) {
            int stationX = centerline.x()[station];
            int stationZ = centerline.z()[station];
            HydrologyTerrainSample center = sampler.sample(stationX, stationZ);
            if (!writable(center, terminal)) {
                exposed = station;
                break;
            }
            centerNatural[station] = center.naturalHeight();
            channelIncision[station] = center.surfacePolicy().maximumIncision(surface.maximumIncision());
            incisionMultiplier[station] = center.incisionMultiplier();
            SurfaceBankSupport.CrossSection section = bankSupport.crossSection(sampler,
                    station(centerline, channel, station), terminal == SurfaceTerminal.OCEAN_MOUTH);
            if (section.blocked()) {
                exposed = station;
                break;
            }
            crossMin[station] = section.minimum();
            crossMax[station] = section.maximum();
        }
        if (terminal == SurfaceTerminal.COASTAL_GROTTO) {
            while (exposed > 0 && !terminalCapSupported(centerline, channel, exposed - 1)) {
                exposed--;
            }
        }
        double exposedLength = exposedLength(centerline, exposed);
        if (exposed < 2 || exposedLength < minimumCourseLength) {
            return ValleyProfile.rejected(HydrologyCandidateRejection.COURSE_TOO_SHORT,
                    (int) StrictMath.floor(exposedLength));
        }
        if (exposed < count && terminal != SurfaceTerminal.OCEAN_MOUTH
                && terminal != SurfaceTerminal.COASTAL_GROTTO) {
            return ValleyProfile.rejected(HydrologyCandidateRejection.SURFACE_EXPOSURE, exposed);
        }
        // The channel's rounded end caps reach past the first and last stations; the ground under
        // them bounds those stations' water like any other bank cell.
        crossMin[0] = Math.min(crossMin[0], capMinimum(centerline, channel, 0, -1D, roughness));
        if (exposed == count) {
            crossMin[exposed - 1] = Math.min(crossMin[exposed - 1], capMinimum(centerline, channel, exposed - 1, 1D, roughness));
        }
        int sink = surface.banks().sink();
        int[] head = new int[count];
        for (int station = 0; station < exposed; station++) {
            int incomingCeiling = station == 0 ? crossMin[station]
                    : Math.min(crossMin[station], head[station - 1] + sink);
            crossMin[station] = bankSupport.perimeter(sampler, station(centerline, channel, station), incomingCeiling).minimum();
            // The cells beside a station's water include cells the next stations own, so on ground
            // that falls gently along the course the head steps down a station early rather than
            // onto a bank the lip cannot raise above its natural height. A larger drop is a step or
            // a fall and keeps its edge where the ground breaks.
            int ceiling = crossMin[station] - sink;
            int lookahead = ceiling;
            for (int ahead = 1; ahead <= BANK_LOOKAHEAD && station + ahead < exposed; ahead++) {
                int next = crossMin[station + ahead] - sink;
                if (next < lookahead && next >= ceiling - 1) {
                    lookahead = next;
                }
            }
            head[station] = station == 0 ? lookahead : Math.min(head[station - 1], lookahead);
        }
        if (terminal == SurfaceTerminal.OCEAN_MOUTH || terminal == SurfaceTerminal.COASTAL_GROTTO) {
            for (int station = 0; station < exposed; station++) {
                head[station] = Math.max(head[station], seaLevel);
            }
        }
        if (terminal == SurfaceTerminal.OCEAN_MOUTH) {
            for (int station = exposed; station < count; station++) {
                head[station] = seaLevel;
            }
        }
        // The inlet: the drowned reach runs inland from the coast, holding the water at sea level, as
        // far as the inlet length allows and the ground can be cut to sea level within the inlet cap;
        // a rise the cap cannot pass ends it there. Above it the approach grades down the inlet ramp
        // slope per station into the estuary wherever that cut fits, instead of dropping in a wall at
        // the coast. The drowned reach is the end of a river, never most of one: it takes at most the
        // inlet course fraction of the exposed course and the approach half of that again, so the
        // headwater keeps its natural head.
        // Every head set here is at or below the terrain-supported head, so the course stays non-rising.
        HydrologyPlannerSettings.Inlet inlet = surface.banks().inlet();
        int rampStart = exposed;
        if (terminal == SurfaceTerminal.OCEAN_MOUTH && inlet.length() > 0) {
            int reach = Math.min(inlet.length(), (int) StrictMath.floor(exposed * inlet.courseFraction()));
            int inletStart = exposed;
            while (inletStart > 0 && exposed - inletStart < reach
                    && cutFits(inletStart - 1, seaLevel, channel, centerNatural, incisionMultiplier,
                    Math.max(channelIncision[inletStart - 1], inlet.maximumIncision()))) {
                inletStart--;
            }
            for (int station = inletStart; station < exposed; station++) {
                head[station] = seaLevel;
            }
            rampStart = Math.max(0, inletStart - reach / 2);
            // The ramp is contiguous with the inlet: the first station the cap cannot lower ends it,
            // otherwise a station lowered further upstream would sit below one left at its natural head.
            for (int station = inletStart - 1; station >= rampStart; station--) {
                int target = seaLevel + (int) StrictMath.round((inletStart - station) * inlet.rampSlope());
                if (head[station] <= target) {
                    continue;
                }
                if (!cutFits(station, target, channel, centerNatural, incisionMultiplier,
                        Math.max(channelIncision[station], inlet.maximumIncision()))) {
                    break;
                }
                head[station] = target;
            }
        }
        if (terminal == SurfaceTerminal.SINKHOLE && head[exposed - 1] < terminalHead) {
            return ValleyProfile.rejected(HydrologyCandidateRejection.SURFACE_SINKHOLE_CLEARANCE, terminalHead - head[exposed - 1]);
        }
        if (terminal == SurfaceTerminal.COASTAL_GROTTO && head[exposed - 1] < terminalHead) {
            return ValleyProfile.rejected(HydrologyCandidateRejection.SURFACE_HEAD_RANGE, terminalHead - head[exposed - 1]);
        }
        int deepestCut = 0;
        boolean rejected = false;
        for (int station = 0; station < exposed; station++) {
            int maximumIncision = station >= rampStart
                    ? Math.max(channelIncision[station], inlet.maximumIncision()) : channelIncision[station];
            int cut = cut(station, head[station], channel, centerNatural);
            deepestCut = Math.max(deepestCut, cut);
            rejected |= cut > permitted(station, incisionMultiplier, maximumIncision);
        }
        if (rejected) {
            return ValleyProfile.rejected(HydrologyCandidateRejection.SURFACE_CORRIDOR_UNSUPPORTED, deepestCut);
        }
        return new ValleyProfile(head, crossMin, crossMax, centerNatural, exposed, null, 0);
    }

    private static SurfaceBankSupport.Station station(SurfaceCenterline centerline, ChannelProfile channel, int station) {
        return new SurfaceBankSupport.Station(centerline.x()[station], centerline.z()[station],
                centerline.tangentX()[station], centerline.tangentZ()[station], channel.halfWidth()[station]);
    }

    private static double exposedLength(SurfaceCenterline centerline, int exposed) {
        double length = 0D;
        for (int station = 1; station < exposed; station++) {
            length += StrictMath.hypot((double) centerline.x()[station] - centerline.x()[station - 1],
                    (double) centerline.z()[station] - centerline.z()[station - 1]);
        }
        return length;
    }

    private boolean terminalCapSupported(SurfaceCenterline centerline, ChannelProfile channel, int station) {
        double reach = channel.halfWidth()[station] * (1D + surface.banks().roughness()) + 2D;
        int radius = (int) StrictMath.ceil(reach);
        double reachSquared = reach * reach;
        double tangentX = centerline.tangentX()[station];
        double tangentZ = centerline.tangentZ()[station];
        for (int dz = -radius; dz <= radius; dz++) {
            for (int dx = -radius; dx <= radius; dx++) {
                if ((double) dx * dx + (double) dz * dz > reachSquared
                        || dx * tangentX + dz * tangentZ < 0D) {
                    continue;
                }
                HydrologyTerrainSample terrain = sampler.sample(centerline.x()[station] + dx, centerline.z()[station] + dz);
                if (!SurfaceCellAdmission.writable(terrain, seaLevel)) {
                    return false;
                }
            }
        }
        return true;
    }

    private static boolean cutFits(
            int station,
            int head,
            ChannelProfile channel,
            int[] centerNatural,
            double[] incisionMultiplier,
            int maximumIncision
    ) {
        return cut(station, head, channel, centerNatural) <= permitted(station, incisionMultiplier, maximumIncision);
    }

    private boolean writable(HydrologyTerrainSample terrain, SurfaceTerminal terminal) {
        return terminal == SurfaceTerminal.OCEAN_MOUTH
                ? SurfaceCellAdmission.mouthLand(terrain, seaLevel)
                : SurfaceCellAdmission.writable(terrain, seaLevel);
    }

    /** How far below the natural ground the bed under a head sits at a station. */
    private static int cut(int station, int head, ChannelProfile channel, int[] centerNatural) {
        return centerNatural[station] - (head - (int) StrictMath.round(channel.depth()[station]));
    }

    private static int permitted(int station, double[] incisionMultiplier, int maximumIncision) {
        return Math.min(maximumIncision, (int) StrictMath.floor(maximumIncision * incisionMultiplier[station]));
    }

    /**
     * Lowest natural ground under the rounded cap the channel outline draws past an end station, in
     * the given direction along the tangent; cells the channel cannot write are ignored.
     */
    private int capMinimum(SurfaceCenterline centerline, ChannelProfile channel, int station, double direction, double roughness) {
        double reach = channel.halfWidth()[station] * (1D + roughness) + 2D;
        double tangentX = centerline.tangentX()[station] * direction;
        double tangentZ = centerline.tangentZ()[station] * direction;
        double normalX = centerline.normalX(station);
        double normalZ = centerline.normalZ(station);
        int stationX = centerline.x()[station];
        int stationZ = centerline.z()[station];
        int minimum = Integer.MAX_VALUE;
        for (double along = 0.5D; along <= reach; along += 0.5D) {
            for (double across = -reach; across <= reach; across += 0.5D) {
                if (along * along + across * across > reach * reach) {
                    continue;
                }
                int cellX = (int) StrictMath.round(stationX + tangentX * along + normalX * across);
                int cellZ = (int) StrictMath.round(stationZ + tangentZ * along + normalZ * across);
                HydrologyTerrainSample terrain = sampler.sample(cellX, cellZ);
                if (!SurfaceCellAdmission.writable(terrain, seaLevel)) {
                    continue;
                }
                minimum = Math.min(minimum, terrain.naturalHeight());
            }
        }
        return minimum;
    }
}
