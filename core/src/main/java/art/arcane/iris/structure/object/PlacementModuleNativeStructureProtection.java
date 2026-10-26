package art.arcane.iris.structure.object;

import art.arcane.iris.generation.block.B;

import art.arcane.iris.generation.runtime.Engine;
import art.arcane.volmlib.nativelib.terrain.structure.NativeStructureVolume;
import art.arcane.volmlib.nativelib.terrain.NativeBlockState;
import art.arcane.iris.generation.block.VectorMap;
import art.arcane.iris.generation.geometry.IrisBlockVector;
import art.arcane.volmlib.util.collection.KList;
import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;

/** Native structures remain protected even when forcePlace bypasses optional placement checks. */
final class PlacementModuleNativeStructureProtection implements PlacementModule {
    static final PlacementLifecycleDependency CHECK = new PlacementLifecycleDependency("validate.native-structures");

    @Override
    public void install(PlacementLifecycleBuilder lifecycle) {
        lifecycle.add(CHECK, PlacementStage.VALIDATE, (context, state, ignored) -> {
            if (state.config.getMode() != ObjectPlaceMode.STRUCTURE_PIECE && vetoes(context, state)) {
                state.reject("native-structure");
            }
        }, PlacementModuleLimits.CHECK);
    }

    private static boolean vetoes(PlacementContext context, PlacementState state) {
        IrisObject self = context.self;
        IObjectPlacer placer = state.placer;
        IrisObjectPlacement config = state.config;
        SpinKernel spin = state.spin;
        IrisBlockVector translateOffset = state.translateOffset;
        boolean translating = translateOffset != null;
        boolean ceilingHang = state.ceilingHang;
        boolean paint = context.yv < 0 && config.getMode() == ObjectPlaceMode.PAINT;
        int warpMargin = state.warpMargin;
        int x = context.x;
        int y = state.y + state.yrand;
        int z = context.z;
        var bounds = state.bounds;
        var projection = state.paintProjection;
        var surfaceWarp = config.getWarp().isFlat() ? null : config.getSurfaceWarp(context.rng, self.getLoader());
        double warpHalf = config.getWarp().isFlat() ? 0 : config.getWarp().getMultiplier() / 2D;
        int randomY = state.yrand;
        Engine engine = placer.getEngine();
        if (engine == null) {
            return false;
        }

        int minX = x + bounds.minX();
        int maxX = x + bounds.maxX();
        int minZ = z + bounds.minZ();
        int maxZ = z + bounds.maxZ();
        KList<NativeStructureVolume> volumes = engine.getNativeStructureVolumes(minX, minZ, maxX, maxZ);
        if (volumes == null || volumes.isEmpty()) {
            return false;
        }

        int worldY = y + engine.getMinHeight();
        int envelopeMinY = projection != null ? Integer.MIN_VALUE : worldY + bounds.minY() - warpMargin;
        int envelopeMaxY = projection != null ? Integer.MAX_VALUE : worldY + bounds.maxY() + warpMargin;
        boolean envelopeMeetsPiece = false;
        boolean includesSurfaceCuts = false;
        for (NativeStructureVolume volume : volumes) {
            if (volume.intersects(minX, envelopeMinY, minZ, maxX, envelopeMaxY, maxZ)) {
                envelopeMeetsPiece = true;
            }
            if (volume.surfaceAdjustment() != null && volume.intersectsInfluenceRect(minX, minZ, maxX, maxZ)) {
                envelopeMeetsPiece = true;
                includesSurfaceCuts = true;
            }
        }

        if (!envelopeMeetsPiece) {
            return false;
        }

        int margin = projection == null ? warpMargin : 0;
        SurfaceCuts surfaceCuts = includesSurfaceCuts ? new SurfaceCuts(engine, volumes) : null;
        self.readLock.lock();
        try {
            VectorMap<NativeBlockState>.Cursor cursor = self.blocks.cursor();
            while (cursor.next()) {
                NativeBlockState source = cursor.value();
                if (source == null || isAirBlock(source)) {
                    continue;
                }

                IrisBlockVector i = cursor.key().clone();
                spin.rotate(i);
                if (ceilingHang) {
                    i.setY(-i.getBlockY());
                }
                if (translating) {
                    i.add(translateOffset);
                }

                int xx = x + (int) Math.round(i.getX());
                int zz = z + (int) Math.round(i.getZ());
                int yy = y + (int) Math.round(i.getY());
                if (projection != null) {
                    if (surfaceWarp != null) {
                        xx += surfaceWarp.fitDouble(-warpHalf, warpHalf, i.getX() + x, i.getY() + y, i.getZ() + z);
                        zz += surfaceWarp.fitDouble(-warpHalf, warpHalf, i.getZ() + z, i.getY() + y, i.getX() + x);
                    }
                    int surface = projection.surfaceY(xx, zz);
                    if (surface == IrisPaintSurfaceProjection.MISSING) {
                        continue;
                    }
                    if (!B.isVineBlock(source)) {
                        yy = (int) Math.round(i.getY()) + Math.floorDiv(self.h, 2) + surface + randomY;
                    }
                }
                int worldBlockY = yy + engine.getMinHeight();

                for (NativeStructureVolume volume : volumes) {
                    if (volume.containsWithin(xx, worldBlockY, zz, margin)) {
                        return true;
                    }
                }
                if (surfaceCuts != null && surfaceCuts.reaches(xx, worldBlockY, zz, margin)) {
                    return true;
                }
            }
        } finally {
            self.readLock.unlock();
        }

        return false;
    }

    /** Terrain a native structure lowers is protected between the cut floor and the original surface. */
    private static final class SurfaceCuts {
        private final Engine engine;
        private final KList<NativeStructureVolume> volumes;
        private final Long2LongOpenHashMap columns = new Long2LongOpenHashMap();

        private SurfaceCuts(Engine engine, KList<NativeStructureVolume> volumes) {
            this.engine = engine;
            this.volumes = volumes;
            columns.defaultReturnValue(Long.MIN_VALUE);
        }

        private boolean reaches(int x, int worldY, int z, int margin) {
            long cut = cutOf(x, z);
            int surfaceY = (int) (cut >> 32);
            int targetY = (int) cut;
            return targetY < surfaceY && worldY > (long) targetY - margin && worldY <= (long) surfaceY + 1 + margin;
        }

        private long cutOf(int x, int z) {
            long column = ((long) x << 32) ^ (z & 0xffffffffL);
            long cut = columns.get(column);
            if (cut == Long.MIN_VALUE) {
                int surfaceY = Engine.hostHeight(engine, x, z, true) + engine.getMinHeight();
                int targetY = NativeStructureVolume.surfaceCutY(volumes, x, z, surfaceY);
                cut = ((long) surfaceY << 32) ^ (targetY & 0xffffffffL);
                columns.put(column, cut);
            }
            return cut;
        }
    }

    private static boolean isAirBlock(NativeBlockState state) {
        String material = IrisObjectShaping.materialKey(state);
        return material.equals("minecraft:air") || material.equals("minecraft:cave_air");
    }

}
