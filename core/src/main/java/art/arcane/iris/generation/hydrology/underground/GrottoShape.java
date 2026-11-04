package art.arcane.iris.generation.hydrology.underground;

import art.arcane.iris.generation.hydrology.grid.CellHash;

import java.util.ArrayList;
import java.util.List;

public final class GrottoShape {
    private static final double WARP = 3.6D;
    private static final double WARP_WAVE = 21D;
    private static final double WARP_WAVE_Y = 11D;
    private static final double WARP_Y = 1.1D;
    private static final double LOBE_WIDE = 0.34D;
    private static final double LOBE_MID = 0.26D;
    private static final double LOBE_FINE = 0.12D;
    private static final double TURN_WIDE = 1.5D;
    private static final double TURN_MID = 3.1D;
    private static final double TURN_FINE = 6.5D;
    private static final double ALONG_WIDE = 19D;
    private static final double ALONG_MID = 8.5D;
    private static final double ALONG_FINE = 3.7D;
    private static final double HANG = 6D;
    private static final double HANG_WAVE = 3.1D;
    private static final double HANG_SPARSE = 0.42D;
    private static final double HANG_BROAD = 9D;
    private static final double HANG_LOW = 1.6D;
    private static final double MOUND = 2.2D;
    private static final double MOUND_WAVE = 6.1D;
    private static final double HANG_BAND = 0.45D;
    private static final double MIN_RADIAL = 0.62D;
    private static final double CORE = 0.5D;
    private static final double HOLE_WOBBLE = 0.45D;
    private static final double HOLE_WAVE = 3.2D;
    private static final long SALT_WARP_X = 0x4752545758L;
    private static final long SALT_WARP_Z = 0x47525457ACL;
    private static final long SALT_WARP_Y = 0x4752545759L;
    private static final long SALT_LOBE = 0x4752544C42L;
    private static final long SALT_HANG = 0x4752544847L;
    private static final long SALT_MOUND = 0x4752544D44L;
    private static final long SALT_HOLE = 0x4752544F4CL;

    private final Grotto grotto;
    private final float[] xs;
    private final float[] zs;
    private final float[] along;
    private final int[] segments;
    private final double warp;
    private final double reachFactor;
    private final int minY;
    private final int maxY;

    public GrottoShape(Grotto grotto) {
        this(grotto, allSegments(grotto));
    }

    private GrottoShape(Grotto grotto, int[] segments) {
        this.grotto = grotto;
        this.xs = grotto.xs();
        this.zs = grotto.zs();
        this.along = distances(xs, zs);
        this.segments = segments;
        this.warp = warp(grotto.roughness());
        this.reachFactor = radialMax(grotto.roughness());
        this.minY = verticalMin(grotto);
        this.maxY = verticalMax(grotto);
    }

    public GrottoShape scoped(int minX, int minZ, int maxX, int maxZ) {
        List<Integer> kept = new ArrayList<>();
        for (int segment = 0; segment < xs.length - 1; segment++) {
            float out = segmentReach(segment);
            float fromX = Math.min(xs[segment], xs[segment + 1]) - out;
            float toX = Math.max(xs[segment], xs[segment + 1]) + out;
            float fromZ = Math.min(zs[segment], zs[segment + 1]) - out;
            float toZ = Math.max(zs[segment], zs[segment + 1]) + out;
            if (fromX <= maxX && toX >= minX && fromZ <= maxZ && toZ >= minZ) {
                kept.add(segment);
            }
        }
        int[] scoped = new int[kept.size()];
        for (int index = 0; index < scoped.length; index++) {
            scoped[index] = kept.get(index);
        }
        return new GrottoShape(grotto, scoped);
    }

    public boolean empty() {
        return segments.length == 0 && grotto.holeX().length == 0;
    }

    public int minY() {
        return minY;
    }

    public int maxY() {
        return maxY;
    }

    public Grotto grotto() {
        return grotto;
    }

    public boolean near(int x, int z) {
        for (int segment : segments) {
            float out = segmentReach(segment);
            if (squared(x, z, segment) <= out * out) {
                return true;
            }
        }
        float[] holeX = grotto.holeX();
        for (int hole = 0; hole < holeX.length; hole++) {
            double out = grotto.holeRadius()[hole] * (1D + HOLE_WOBBLE) + warp + 1D;
            double dx = x - holeX[hole];
            double dz = z - grotto.holeZ()[hole];
            if (dx * dx + dz * dz <= out * out) {
                return true;
            }
        }
        return false;
    }

    public boolean inside(int x, int y, int z) {
        if (y < minY || y > maxY || grotto.plugged(x, y, z)) {
            return false;
        }
        double noiseX = x / WARP_WAVE;
        double noiseY = y / WARP_WAVE_Y;
        double noiseZ = z / WARP_WAVE;
        double px = x + warp * noise(SALT_WARP_X, noiseX, noiseY, noiseZ);
        double pz = z + warp * noise(SALT_WARP_Z, noiseX + 37.1D, noiseY, noiseZ);
        double py = y + WARP_Y * noise(SALT_WARP_Y, noiseX, noiseY + 11.3D, noiseZ);
        return inPassage(x, z, px, py, pz) || y > grotto.seaLevel() && inHole(y, px, pz);
    }

    private boolean inPassage(int x, int z, double px, double py, double pz) {
        int best = -1;
        double bestSquared = Double.POSITIVE_INFINITY;
        double bestShare = 0D;
        for (int segment : segments) {
            double ax = xs[segment];
            double az = zs[segment];
            double dx = xs[segment + 1] - ax;
            double dz = zs[segment + 1] - az;
            double length = dx * dx + dz * dz;
            double share = length <= 0D ? 0D : Math.clamp(((px - ax) * dx + (pz - az) * dz) / length, 0D, 1D);
            double ox = px - ax - share * dx;
            double oz = pz - az - share * dz;
            double distance = ox * ox + oz * oz;
            if (distance < bestSquared) {
                bestSquared = distance;
                best = segment;
                bestShare = share;
            }
        }
        if (best < 0) {
            return false;
        }
        double width = lerp(grotto.halfWidth(), best, bestShare);
        double limit = width * reachFactor;
        if (bestSquared > limit * limit) {
            return false;
        }
        double s = along[best] + bestShare * (along[best + 1] - along[best]);
        double centre = grotto.seaLevel() + lerp(grotto.lift(), best, bestShare);
        double dy = py - centre;
        double roughness = grotto.roughness();
        double height;
        if (dy > 0D) {
            height = lerp(grotto.roof(), best, bestShare);
            double hang = noise(SALT_HANG, x / HANG_WAVE, 0D, z / HANG_WAVE);
            double broad = noise(SALT_HANG + 1L, x / HANG_BROAD, 0D, z / HANG_BROAD);
            dy += roughness * (HANG * Math.max(0D, hang - HANG_SPARSE) + HANG_LOW * broad) * Math.min(1D, dy / (HANG_BAND * height));
        } else {
            height = lerp(grotto.floor(), best, bestShare);
            double mound = noise(SALT_MOUND, x / MOUND_WAVE, 0D, z / MOUND_WAVE);
            dy += roughness * MOUND * mound * Math.min(1D, -dy / (HANG_BAND * height));
            dy = Math.min(dy, 0D);
        }
        double distance = Math.sqrt(bestSquared);
        double dirX = xs[best + 1] - xs[best];
        double dirZ = zs[best + 1] - zs[best];
        double side = dirX * (pz - zs[best]) - dirZ * (px - xs[best]) >= 0D ? 1D : -1D;
        double u = side * distance / width;
        double v = dy / height;
        double rho = Math.sqrt(u * u + v * v);
        if (rho < CORE) {
            return true;
        }
        double cos = u / rho;
        double sin = v / rho;
        double lobes = LOBE_WIDE * noise(SALT_LOBE, cos * TURN_WIDE, sin * TURN_WIDE, s / ALONG_WIDE)
                + LOBE_MID * noise(SALT_LOBE + 1L, cos * TURN_MID, sin * TURN_MID, s / ALONG_MID)
                + LOBE_FINE * noise(SALT_LOBE + 2L, cos * TURN_FINE, sin * TURN_FINE, s / ALONG_FINE);
        double radial = Math.max(MIN_RADIAL, 1D + 2D * roughness * lobes);
        return rho < radial;
    }

    private boolean inHole(int y, double px, double pz) {
        float[] holeX = grotto.holeX();
        for (int hole = 0; hole < holeX.length; hole++) {
            if (y > grotto.holeTop()[hole]) {
                continue;
            }
            double dx = px - holeX[hole];
            double dz = pz - grotto.holeZ()[hole];
            double radius = grotto.holeRadius()[hole] * (1D + HOLE_WOBBLE * noise(SALT_HOLE + hole, dx / HOLE_WAVE, y / HOLE_WAVE, dz / HOLE_WAVE));
            if (dx * dx + dz * dz < radius * radius) {
                return true;
            }
        }
        return false;
    }

    static float horizontalReach(Grotto grotto) {
        float widest = 0f;
        for (float width : grotto.halfWidth()) {
            widest = Math.max(widest, width);
        }
        return (float) (widest * radialMax(grotto.roughness()) + warp(grotto.roughness()) + 1D);
    }

    public static double radialMax(double roughness) {
        return 1D + 2D * roughness * (LOBE_WIDE + LOBE_MID + LOBE_FINE);
    }

    public static double warp(double roughness) {
        return WARP * (0.4D + 0.6D * roughness);
    }

    private static int verticalMin(Grotto grotto) {
        float deepest = 0f;
        for (float floor : grotto.floor()) {
            deepest = Math.max(deepest, floor);
        }
        return grotto.seaLevel() - (int) Math.ceil(deepest * radialMax(grotto.roughness()) + WARP_Y + 1D);
    }

    private static int verticalMax(Grotto grotto) {
        float highest = 0f;
        for (int point = 0; point < grotto.roof().length; point++) {
            highest = Math.max(highest, grotto.roof()[point] + grotto.lift()[point]);
        }
        int top = grotto.seaLevel() + (int) Math.ceil(highest * radialMax(grotto.roughness()) + grotto.roughness() * HANG_LOW + WARP_Y + 1D);
        for (int holeTop : grotto.holeTop()) {
            top = Math.max(top, holeTop);
        }
        return top;
    }

    private float segmentReach(int segment) {
        return (float) (Math.max(grotto.halfWidth()[segment], grotto.halfWidth()[segment + 1]) * reachFactor + warp + 1D);
    }

    private double squared(int x, int z, int segment) {
        double ax = xs[segment];
        double az = zs[segment];
        double dx = xs[segment + 1] - ax;
        double dz = zs[segment + 1] - az;
        double length = dx * dx + dz * dz;
        double share = length <= 0D ? 0D : Math.clamp(((x - ax) * dx + (z - az) * dz) / length, 0D, 1D);
        double ox = x - ax - share * dx;
        double oz = z - az - share * dz;
        return ox * ox + oz * oz;
    }

    private static double lerp(float[] values, int segment, double share) {
        return values[segment] + share * (values[segment + 1] - values[segment]);
    }

    private static float[] distances(float[] xs, float[] zs) {
        float[] along = new float[xs.length];
        for (int point = 1; point < xs.length; point++) {
            along[point] = along[point - 1] + (float) Math.hypot(xs[point] - xs[point - 1], zs[point] - zs[point - 1]);
        }
        return along;
    }

    private static int[] allSegments(Grotto grotto) {
        int[] all = new int[grotto.xs().length - 1];
        for (int segment = 0; segment < all.length; segment++) {
            all[segment] = segment;
        }
        return all;
    }

    private double noise(long salt, double x, double y, double z) {
        int ix = (int) Math.floor(x);
        int iy = (int) Math.floor(y);
        int iz = (int) Math.floor(z);
        double fx = fade(x - ix);
        double fy = fade(y - iy);
        double fz = fade(z - iz);
        long id = grotto.id();
        double c000 = lattice(id, salt + iy, ix, iz);
        double c100 = lattice(id, salt + iy, ix + 1, iz);
        double c010 = lattice(id, salt + iy + 1L, ix, iz);
        double c110 = lattice(id, salt + iy + 1L, ix + 1, iz);
        double c001 = lattice(id, salt + iy, ix, iz + 1);
        double c101 = lattice(id, salt + iy, ix + 1, iz + 1);
        double c011 = lattice(id, salt + iy + 1L, ix, iz + 1);
        double c111 = lattice(id, salt + iy + 1L, ix + 1, iz + 1);
        double near = c000 + fx * (c100 - c000) + fy * (c010 + fx * (c110 - c010) - c000 - fx * (c100 - c000));
        double far = c001 + fx * (c101 - c001) + fy * (c011 + fx * (c111 - c011) - c001 - fx * (c101 - c001));
        return near + fz * (far - near);
    }

    private static double lattice(long id, long salt, int x, int z) {
        return 2D * CellHash.unit(CellHash.mix(id, salt, x, z)) - 1D;
    }

    private static double fade(double t) {
        return t * t * (3D - 2D * t);
    }
}
