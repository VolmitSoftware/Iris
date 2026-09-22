package art.arcane.iris.generation.hydrology.surface;

import art.arcane.iris.generation.hydrology.HydrologyCandidateRejection;
import art.arcane.iris.generation.hydrology.HydrologyPlannerSettings;
import art.arcane.iris.generation.hydrology.HydrologyPoint;
import art.arcane.iris.generation.hydrology.HydrologyTerrainSample;
import art.arcane.iris.generation.hydrology.HydrologyTerrainSampler;

import java.util.List;
import java.util.Objects;

final class RiverProfileSolver {
    private final HydrologyPlannerSettings.Surface surface;
    private final HydrologyTerrainSampler sampler;
    private final int seaLevel;

    RiverProfileSolver(Options options, HydrologyTerrainSampler sampler) {
        this.surface = Objects.requireNonNull(options.surface());
        this.sampler = Objects.requireNonNull(sampler);
        this.seaLevel = options.seaLevel();
    }

    ValleyProfile solve(List<HydrologyPoint> path, SurfaceCenterline centerline, ChannelProfile channel,
                        SurfaceTerminal terminal, int terminalHead, int minimumLength) {
        int count = centerline.size();
        int[] heads = interpolate(path, centerline);
        int[] natural = new int[count];
        int[] incision = new int[count];
        int[] inletIncision = new int[count];
        int exposed = count;
        double length = 0D;
        for (int station = 0; station < count; station++) {
            HydrologyTerrainSample terrain = sampler.sample(centerline.x()[station], centerline.z()[station]);
            boolean writable = terminal == SurfaceTerminal.OCEAN_MOUTH
                    ? SurfaceCellAdmission.mouthLand(terrain, seaLevel)
                    : SurfaceCellAdmission.writable(terrain, seaLevel);
            if (!writable) {
                exposed = station;
                break;
            }
            if (!terrain.transitAllowed()) {
                return ValleyProfile.rejected(HydrologyCandidateRejection.POLICY_EXCLUDED, station);
            }
            natural[station] = terrain.naturalHeight();
            int limit = terrain.surfacePolicy().maximumIncision(surface.maximumIncision());
            incision[station] = Math.min(limit, (int) StrictMath.floor(limit * terrain.incisionMultiplier()));
            int inletLimit = Math.max(limit, surface.banks().inlet().maximumIncision());
            inletIncision[station] = Math.min(inletLimit,
                    (int) StrictMath.floor(inletLimit * terrain.incisionMultiplier()));
            if (station > 0) {
                length += StrictMath.hypot((double) centerline.x()[station] - centerline.x()[station - 1],
                        (double) centerline.z()[station] - centerline.z()[station - 1]);
            }
        }
        if (exposed < 2 || length < minimumLength) {
            return ValleyProfile.rejected(HydrologyCandidateRejection.COURSE_TOO_SHORT, (int) StrictMath.floor(length));
        }
        if (exposed < count && terminal != SurfaceTerminal.OCEAN_MOUTH
                && terminal != SurfaceTerminal.COASTAL_GROTTO) {
            return ValleyProfile.rejected(HydrologyCandidateRejection.SURFACE_EXPOSURE, exposed);
        }
        int floor = terminal == SurfaceTerminal.OCEAN_MOUTH || terminal == SurfaceTerminal.COASTAL_GROTTO
                ? Math.max(seaLevel, terminalHead) : terminalHead;
        for (int station = 0; station < exposed; station++) {
            heads[station] = Math.max(heads[station], floor);
        }
        if (terminal == SurfaceTerminal.TRIBUTARY) {
            gradeTerminal(heads, exposed, terminalHead, surface.banks().cascadeRun());
        }
        int inletStart = exposed;
        if (terminal == SurfaceTerminal.OCEAN_MOUTH) {
            inletStart = gradeInlet(heads, natural, inletIncision, channel, exposed);
            for (int station = exposed; station < count; station++) {
                heads[station] = seaLevel;
            }
        }
        int sourceHead = heads[0];
        int downstream = floor;
        for (int station = exposed - 1; station >= 0; station--) {
            int depth = (int) StrictMath.round(channel.depth()[station]);
            int limit = incision[station];
            if (station >= inletStart) {
                limit = inletIncision[station];
            }
            int required = Math.max(Math.max(heads[station], downstream), natural[station] - limit + depth);
            if (required > sourceHead || terminal == SurfaceTerminal.TRIBUTARY
                    && station == exposed - 1 && required > terminalHead) {
                return ValleyProfile.rejected(HydrologyCandidateRejection.SURFACE_CORRIDOR_UNSUPPORTED,
                        natural[station] - heads[station] + depth);
            }
            heads[station] = required;
            downstream = required;
        }
        for (int station = 1; station < exposed; station++) {
            int drop = heads[station - 1] - heads[station];
            if (drop >= surface.banks().waterfallMinimumDrop()) {
                continue;
            }
            int limit = station - 1 >= inletStart ? inletIncision[station - 1] : incision[station - 1];
            int connectedHead = Math.max(heads[station - 1] - 1, natural[station - 1] - limit + 1);
            if (terminal == SurfaceTerminal.TRIBUTARY && station == exposed - 1 && connectedHead > terminalHead) {
                return ValleyProfile.rejected(HydrologyCandidateRejection.SURFACE_CORRIDOR_UNSUPPORTED,
                        natural[station - 1] - terminalHead + 1);
            }
            heads[station] = Math.max(heads[station], Math.min(heads[station - 1], connectedHead));
        }
        return new ValleyProfile(heads, natural, natural, natural, exposed, null, 0);
    }

    private static int[] interpolate(List<HydrologyPoint> path, SurfaceCenterline centerline) {
        int[] heads = new int[centerline.size()];
        for (int station = 0; station < heads.length; station++) {
            int index = Math.min(centerline.pathIndex()[station], path.size() - 1);
            HydrologyPoint start = path.get(index);
            HydrologyPoint end = path.get(Math.min(index + 1, path.size() - 1));
            double dx = (double) end.x() - start.x();
            double dz = (double) end.z() - start.z();
            double squared = dx * dx + dz * dz;
            double progress = squared == 0D ? 0D : Math.clamp(
                    ((centerline.x()[station] - (double) start.x()) * dx
                            + (centerline.z()[station] - (double) start.z()) * dz) / squared, 0D, 1D);
            int head = (int) StrictMath.round(start.y() + ((double) end.y() - start.y()) * progress);
            heads[station] = station == 0 ? head : Math.min(heads[station - 1], head);
        }
        return heads;
    }

    private static void gradeTerminal(int[] heads, int exposed, int terminalHead, int run) {
        for (int station = exposed - 1; station >= 0; station--) {
            long target = (long) terminalHead + Math.floorDiv(exposed - 1L - station, Math.max(1, run));
            if (heads[station] <= target) {
                break;
            }
            heads[station] = (int) Math.min(Integer.MAX_VALUE, target);
        }
    }

    private int gradeInlet(int[] heads, int[] natural, int[] incision, ChannelProfile channel, int exposed) {
        HydrologyPlannerSettings.Inlet inlet = surface.banks().inlet();
        int reach = Math.min(inlet.length(), (int) StrictMath.floor(exposed * inlet.courseFraction()));
        int start = exposed;
        while (start > 0 && exposed - start < reach) {
            int index = start - 1;
            int cut = natural[index] - seaLevel + (int) StrictMath.round(channel.depth()[index]);
            if (cut > incision[index]) {
                break;
            }
            heads[index] = seaLevel;
            start--;
        }
        int rampStart = start;
        for (int station = start - 1; station >= Math.max(0, start - reach / 2); station--) {
            int target = seaLevel + (int) StrictMath.round((start - station) * inlet.rampSlope());
            if (heads[station] <= target) {
                break;
            }
            int cut = natural[station] - target + (int) StrictMath.round(channel.depth()[station]);
            if (cut > incision[station]) {
                break;
            }
            heads[station] = target;
            rampStart = station;
        }
        return rampStart;
    }

    record Options(HydrologyPlannerSettings.Surface surface, int seaLevel) {
    }
}
