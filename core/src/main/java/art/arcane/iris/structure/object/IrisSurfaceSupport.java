package art.arcane.iris.structure.object;

import art.arcane.iris.structure.object.IObjectPlacer;
import art.arcane.iris.structure.object.IrisObjectRotation;
import art.arcane.iris.structure.object.IrisObjectTranslate;
import art.arcane.iris.structure.object.IrisPaintSurfaceProjection;

import art.arcane.iris.pack.loading.IrisData;
import art.arcane.iris.generation.runtime.IrisComplex;
import art.arcane.iris.generation.runtime.Engine;
import art.arcane.iris.generation.geometry.IrisBlockVector;

import java.util.Arrays;
import java.util.List;

/**
 * Hard gate for surface-anchored object and tree placement: every column under the object's lowest solid
 * layer, plus a buffer ring, must be solid un-carved ground for the required depth. Anything else floats
 * over or bridges a carved hole, so the placement is skipped.
 */
public final class IrisSurfaceSupport {
    private static final int MAX_STENCIL_CELLS = 1 << 22;
    private static final ThreadLocal<Stencil> STENCIL = ThreadLocal.withInitial(Stencil::new);

    private IrisSurfaceSupport() {
    }

    public static boolean isUnsupported(IObjectPlacer placer, IrisData data, int x, int z,
                                       IrisObjectTranslate translate, IrisObjectRotation rotation,
                                       int spinX, int spinY, int spinZ,
                                       List<IrisBlockVector> supportOffsets, int buffer, int minSolidDepth) {
        return isUnsupported(placer, data, x, z, translate, rotation, spinX, spinY, spinZ,
                supportOffsets, buffer, minSolidDepth, null);
    }

    static boolean isUnsupported(IObjectPlacer placer, IrisData data, int x, int z,
                                 IrisObjectTranslate translate, IrisObjectRotation rotation,
                                 int spinX, int spinY, int spinZ,
                                 List<IrisBlockVector> supportOffsets, int buffer, int minSolidDepth,
                                 IrisPaintSurfaceProjection projection) {
        Stencil stencil = STENCIL.get();
        if (!stencil.build(supportOffsets, translate, rotation, spinX, spinY, spinZ, Math.max(0, buffer))) {
            return true;
        }
        return stencil.anyColumnUnsupported(placer, data, x, z, Math.max(1, minSolidDepth), projection);
    }

    public static boolean intersectsHydrology(IObjectPlacer placer, int x, int z,
                                              IrisObjectTranslate translate, IrisObjectRotation rotation,
                                              int spinX, int spinY, int spinZ,
                                              List<IrisBlockVector> supportOffsets) {
        Engine engine = placer.getEngine();
        if (engine == null || engine.getComplex() == null) {
            return false;
        }
        Stencil stencil = STENCIL.get();
        if (!stencil.build(supportOffsets, translate, rotation, spinX, spinY, spinZ, 0)) {
            return true;
        }
        return stencil.anyColumnIntersectsHydrology(engine.getComplex(), x, z);
    }

    private static IrisBlockVector transform(IrisBlockVector offset, IrisObjectTranslate translate,
                                             IrisObjectRotation rotation, int spinX, int spinY, int spinZ) {
        IrisBlockVector transformed = offset.clone();
        if (rotation != null) {
            transformed = rotation.rotate(transformed, spinX, spinY, spinZ);
        }
        if (translate == null) {
            return transformed;
        }
        return rotation == null
                ? translate.translate(transformed)
                : translate.translate(transformed, rotation, spinX, spinY, spinZ);
    }

    /**
     * Rasterized footprint reused per thread. Overlapping support offsets collapse into one cell here so
     * each unique column is sampled once no matter how large the buffer is.
     */
    private static final class Stencil {
        private int[] offsetX = new int[64];
        private int[] offsetZ = new int[64];
        private boolean[] cells = new boolean[0];
        private boolean[] dilateScratch = new boolean[0];
        private int minX;
        private int minZ;
        private int width;
        private int depth;
        private int centerX;
        private int centerZ;

        private boolean build(List<IrisBlockVector> supportOffsets, IrisObjectTranslate translate,
                              IrisObjectRotation rotation, int spinX, int spinY, int spinZ, int buffer) {
            boolean originOnly = supportOffsets == null || supportOffsets.isEmpty();
            int count = originOnly ? 1 : supportOffsets.size();
            if (offsetX.length < count) {
                offsetX = new int[count];
                offsetZ = new int[count];
            }

            int lowX = Integer.MAX_VALUE;
            int highX = Integer.MIN_VALUE;
            int lowZ = Integer.MAX_VALUE;
            int highZ = Integer.MIN_VALUE;
            for (int i = 0; i < count; i++) {
                IrisBlockVector source = originOnly ? new IrisBlockVector(0, 0, 0) : supportOffsets.get(i);
                IrisBlockVector transformed = transform(source, translate, rotation, spinX, spinY, spinZ);
                int transformedX = transformed.getBlockX();
                int transformedZ = transformed.getBlockZ();
                offsetX[i] = transformedX;
                offsetZ[i] = transformedZ;
                lowX = Math.min(lowX, transformedX);
                highX = Math.max(highX, transformedX);
                lowZ = Math.min(lowZ, transformedZ);
                highZ = Math.max(highZ, transformedZ);
            }

            minX = lowX - buffer;
            minZ = lowZ - buffer;
            width = (highX - lowX + 1) + (buffer * 2);
            depth = (highZ - lowZ + 1) + (buffer * 2);
            long area = (long) width * (long) depth;
            if (area <= 0L || area > MAX_STENCIL_CELLS) {
                return false;
            }

            int size = (int) area;
            if (cells.length < size) {
                cells = new boolean[size];
                dilateScratch = new boolean[size];
            }
            Arrays.fill(cells, 0, size, false);
            for (int i = 0; i < count; i++) {
                cells[((offsetX[i] - minX) * depth) + (offsetZ[i] - minZ)] = true;
            }
            if (buffer > 0) {
                dilate(buffer, size);
            }

            int anchorX = -minX;
            int anchorZ = -minZ;
            centerX = anchorX >= 0 && anchorX < width ? anchorX : width / 2;
            centerZ = anchorZ >= 0 && anchorZ < depth ? anchorZ : depth / 2;
            return true;
        }

        /** Chebyshev dilation, run as two separable passes so a wide buffer stays linear in footprint size. */
        private void dilate(int buffer, int size) {
            Arrays.fill(dilateScratch, 0, size, false);
            for (int sourceX = 0; sourceX < width; sourceX++) {
                int base = sourceX * depth;
                int fromX = Math.max(0, sourceX - buffer);
                int toX = Math.min(width - 1, sourceX + buffer);
                for (int sourceZ = 0; sourceZ < depth; sourceZ++) {
                    if (!cells[base + sourceZ]) {
                        continue;
                    }
                    for (int targetX = fromX; targetX <= toX; targetX++) {
                        dilateScratch[(targetX * depth) + sourceZ] = true;
                    }
                }
            }

            Arrays.fill(cells, 0, size, false);
            for (int sourceX = 0; sourceX < width; sourceX++) {
                int base = sourceX * depth;
                for (int sourceZ = 0; sourceZ < depth; sourceZ++) {
                    if (!dilateScratch[base + sourceZ]) {
                        continue;
                    }
                    int fromZ = Math.max(0, sourceZ - buffer);
                    int toZ = Math.min(depth - 1, sourceZ + buffer);
                    for (int targetZ = fromZ; targetZ <= toZ; targetZ++) {
                        cells[base + targetZ] = true;
                    }
                }
            }
        }

        /** Centre-outward so the common failure, a hole right under the anchor, costs one column. */
        private boolean anyColumnUnsupported(IObjectPlacer placer, IrisData data, int x, int z, int minSolidDepth,
                                             IrisPaintSurfaceProjection projection) {
            int maxRing = Math.max(
                    Math.max(centerX, width - 1 - centerX),
                    Math.max(centerZ, depth - 1 - centerZ));
            for (int ring = 0; ring <= maxRing; ring++) {
                if (ring == 0) {
                    if (isColumnUnsupported(placer, data, x, z, centerX, centerZ, minSolidDepth, projection)) {
                        return true;
                    }
                    continue;
                }

                int lowX = centerX - ring;
                int highX = centerX + ring;
                int lowZ = centerZ - ring;
                int highZ = centerZ + ring;
                for (int sx = lowX; sx <= highX; sx++) {
                    if (isColumnUnsupported(placer, data, x, z, sx, lowZ, minSolidDepth, projection)
                            || isColumnUnsupported(placer, data, x, z, sx, highZ, minSolidDepth, projection)) {
                        return true;
                    }
                }
                for (int sz = lowZ + 1; sz < highZ; sz++) {
                    if (isColumnUnsupported(placer, data, x, z, lowX, sz, minSolidDepth, projection)
                            || isColumnUnsupported(placer, data, x, z, highX, sz, minSolidDepth, projection)) {
                        return true;
                    }
                }
            }
            return false;
        }

        private boolean anyColumnIntersectsHydrology(IrisComplex complex, int x, int z) {
            for (int stencilX = 0; stencilX < width; stencilX++) {
                for (int stencilZ = 0; stencilZ < depth; stencilZ++) {
                    if (!cells[(stencilX * depth) + stencilZ]) {
                        continue;
                    }
                    int columnX = x + minX + stencilX;
                    int columnZ = z + minZ + stencilZ;
                    if (complex.hasHydrologyChannelOrShore(columnX, columnZ)) {
                        return true;
                    }
                }
            }
            return false;
        }

        private boolean isColumnUnsupported(IObjectPlacer placer, IrisData data, int x, int z,
                                            int stencilX, int stencilZ, int minSolidDepth,
                                            IrisPaintSurfaceProjection projection) {
            if (stencilX < 0 || stencilX >= width || stencilZ < 0 || stencilZ >= depth
                    || !cells[(stencilX * depth) + stencilZ]) {
                return false;
            }

            int columnX = x + minX + stencilX;
            int columnZ = z + minZ + stencilZ;
            int surfaceY = projection == null ? placer.getHighest(columnX, columnZ, data, true)
                    : projection.surfaceY(columnX, columnZ);
            if (surfaceY == IrisPaintSurfaceProjection.MISSING) {
                return false;
            }
            for (int below = 0; below < minSolidDepth; below++) {
                if (placer.isCarved(columnX, surfaceY - below, columnZ)
                        || projection != null && !placer.isSurfaceSolid(columnX, surfaceY - below, columnZ)) {
                    return true;
                }
            }
            return !placer.isSurfaceSolid(columnX, surfaceY, columnZ);
        }
    }
}
