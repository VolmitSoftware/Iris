package art.arcane.iris.structure.object;

import art.arcane.iris.pack.loading.IrisData;

import java.util.Arrays;

final class IrisPaintSurfaceProjection {
    static final int MISSING = Integer.MIN_VALUE;
    private static final int MAXIMUM_STEP = 4;
    private static final int MAXIMUM_CELLS = 1 << 22;

    private final Request request;
    private final int depth;
    private final int[] surfaces;

    private IrisPaintSurfaceProjection(Request request, int[] surfaces) {
        this.request = request;
        depth = request.maxZ() - request.minZ() + 1;
        this.surfaces = surfaces;
    }

    static IrisPaintSurfaceProjection create(IObjectPlacer placer, Request request) {
        long width = (long) request.maxX() - request.minX() + 1;
        long depth = (long) request.maxZ() - request.minZ() + 1;
        long cells = width * depth;
        if (width <= 0 || depth <= 0 || width > MAXIMUM_CELLS || depth > MAXIMUM_CELLS
                || cells > MAXIMUM_CELLS || request.anchorX() < request.minX()
                || request.anchorX() > request.maxX() || request.anchorZ() < request.minZ()
                || request.anchorZ() > request.maxZ()) {
            return null;
        }
        int[] surfaces = new int[(int) cells];
        Arrays.fill(surfaces, MISSING);
        IrisPaintSurfaceProjection projection = new IrisPaintSurfaceProjection(request, surfaces);
        return projection.resolve(placer) ? projection : null;
    }

    int surfaceY(int x, int z) {
        if (x < request.minX() || x > request.maxX() || z < request.minZ() || z > request.maxZ()) {
            return MISSING;
        }
        return surfaces[index(x, z)];
    }

    private boolean resolve(IObjectPlacer placer) {
        int[] highest = new int[surfaces.length];
        Arrays.fill(highest, MISSING);
        int[] pending = new int[surfaces.length];
        int anchor = index(request.anchorX(), request.anchorZ());
        highest[anchor] = placer.getHighest(request.anchorX(), request.anchorZ(), request.data(), request.ignoreFluid());
        if (!accepts(placer, request.anchorX(), request.anchorZ(), request.anchorY(), highest[anchor])) {
            return false;
        }
        surfaces[anchor] = request.anchorY();
        pending[0] = anchor;
        int count = 1;
        for (int cursor = 0; cursor < count; cursor++) {
            int cell = pending[cursor];
            int x = request.minX() + cell / depth;
            int z = request.minZ() + cell % depth;
            int reference = surfaces[cell];
            count = visit(placer, (long) x - 1, z, reference, highest, pending, count);
            count = visit(placer, (long) x + 1, z, reference, highest, pending, count);
            count = visit(placer, x, (long) z - 1, reference, highest, pending, count);
            count = visit(placer, x, (long) z + 1, reference, highest, pending, count);
        }
        return true;
    }

    private int visit(IObjectPlacer placer, long candidateX, long candidateZ, int reference,
                      int[] highest, int[] pending, int count) {
        if (candidateX < request.minX() || candidateX > request.maxX()
                || candidateZ < request.minZ() || candidateZ > request.maxZ()) {
            return count;
        }
        int x = (int) candidateX;
        int z = (int) candidateZ;
        int cell = index(x, z);
        if (surfaces[cell] != MISSING) {
            return count;
        }
        if (highest[cell] == MISSING) {
            highest[cell] = placer.getHighest(x, z, request.data(), request.ignoreFluid());
        }
        for (int distance = 0; distance <= MAXIMUM_STEP; distance++) {
            long lower = (long) reference - distance;
            if (accepts(placer, x, z, lower, highest[cell])) {
                surfaces[cell] = (int) lower;
                pending[count++] = cell;
                return count;
            }
            long upper = (long) reference + distance;
            if (distance > 0 && accepts(placer, x, z, upper, highest[cell])) {
                surfaces[cell] = (int) upper;
                pending[count++] = cell;
                return count;
            }
        }
        return count;
    }

    private boolean accepts(IObjectPlacer placer, int x, int z, long y, int highest) {
        if (y <= Integer.MIN_VALUE || y >= Integer.MAX_VALUE || y > highest
                || !matchesNeighbour((long) x - 1, z, y) || !matchesNeighbour((long) x + 1, z, y)
                || !matchesNeighbour(x, (long) z - 1, y) || !matchesNeighbour(x, (long) z + 1, y)) {
            return false;
        }
        if (y == highest) {
            return request.virtualHeightmap()
                    || solid(placer, x, (int) y, z)
                    || !request.ignoreFluid() && y == placer.getFluidHeight(x, z);
        }
        if (request.virtualHeightmap()) {
            return false;
        }
        return solid(placer, x, (int) y, z) && !solid(placer, x, (int) y + 1, z);
    }

    private boolean matchesNeighbour(long x, long z, long y) {
        if (x < request.minX() || x > request.maxX() || z < request.minZ() || z > request.maxZ()) {
            return true;
        }
        int neighbour = surfaceY((int) x, (int) z);
        return neighbour == MISSING || Math.abs(y - neighbour) <= MAXIMUM_STEP;
    }

    private boolean solid(IObjectPlacer placer, int x, int y, int z) {
        return placer.isSurfaceSolid(x, y, z) && !placer.isCarved(x, y, z);
    }

    private int index(int x, int z) {
        return (x - request.minX()) * depth + z - request.minZ();
    }

    record Request(IrisData data, int minX, int maxX, int minZ, int maxZ,
                   int anchorX, int anchorY, int anchorZ, boolean ignoreFluid, boolean virtualHeightmap) {
    }
}
