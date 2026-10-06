package art.arcane.iris.engine.object.placement;

import art.arcane.iris.engine.object.prefab.IrisObject;
import art.arcane.iris.engine.object.prefab.IrisObjectShaping;
import art.arcane.iris.engine.framework.Engine;
import art.arcane.iris.engine.framework.NativeStructureVolume;
import art.arcane.iris.spi.PlatformBlockState;
import art.arcane.iris.util.common.data.VectorMap;
import art.arcane.iris.util.common.math.IrisBlockVector;
import art.arcane.volmlib.util.collection.KList;

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
        ObjectSpinKernel spin = state.spin;
        IrisBlockVector translateOffset = state.translateOffset;
        boolean translating = translateOffset != null;
        boolean ceilingHang = config.getMode() == ObjectPlaceMode.CEILING_HANG;
        boolean paint = context.yv < 0 && config.getMode() == ObjectPlaceMode.PAINT;
        int warpMargin = state.warpMargin;
        int x = context.x;
        int y = state.y + state.yrand;
        int z = context.z;
        Engine engine = placer.getEngine();
        if (engine == null) {
            return false;
        }

        int margin = (Math.max(self.getW(), Math.max(self.getH(), self.getD())) / 2) + 1 + warpMargin;
        if (translating) {
            margin += Math.max(Math.abs(translateOffset.getBlockX()),
                    Math.max(Math.abs(translateOffset.getBlockY()), Math.abs(translateOffset.getBlockZ())));
        }

        KList<NativeStructureVolume> volumes = engine.getNativeStructureVolumes(x - margin, z - margin, x + margin, z + margin);
        if (volumes == null || volumes.isEmpty()) {
            return false;
        }

        int worldY = y + engine.getMinHeight();
        int envelopeMinY = paint ? Integer.MIN_VALUE : worldY - margin;
        int envelopeMaxY = paint ? Integer.MAX_VALUE : worldY + margin;
        boolean envelopeMeetsPiece = false;
        for (NativeStructureVolume volume : volumes) {
            if (volume.intersects(x - margin, envelopeMinY, z - margin, x + margin, envelopeMaxY, z + margin)) {
                envelopeMeetsPiece = true;
                break;
            }
        }

        if (!envelopeMeetsPiece) {
            return false;
        }

        self.readLock.lock();
        try {
            VectorMap<PlatformBlockState>.Cursor cursor = self.blocks.cursor();
            while (cursor.next()) {
                PlatformBlockState source = cursor.value();
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
                int yy = paint
                        ? (int) Math.round(i.getY()) + Math.floorDiv(self.h, 2)
                                + placer.getHighest(xx, zz, self.getLoader(), config.isUnderwater())
                        : y + (int) Math.round(i.getY());
                int worldBlockY = yy + engine.getMinHeight();

                for (NativeStructureVolume volume : volumes) {
                    if (volume.containsWithin(xx, worldBlockY, zz, warpMargin)) {
                        return true;
                    }
                }
            }
        } finally {
            self.readLock.unlock();
        }

        return false;
    }

    private static boolean isAirBlock(PlatformBlockState state) {
        String material = IrisObjectShaping.materialKey(state);
        return material.equals("minecraft:air") || material.equals("minecraft:cave_air");
    }

}
