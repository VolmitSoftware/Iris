package art.arcane.iris.structure.object;


import art.arcane.iris.structure.object.HeightmapObjectPlacer;
import art.arcane.iris.generation.geometry.IrisBlockVector;

/** Resolves the placer and transforms once, preserving the caller's RNG draw order. */
final class PlacementModulePreparation implements PlacementModule {
    static final PlacementLifecycleDependency PLACER = new PlacementLifecycleDependency("prepare.placer");
    static final PlacementLifecycleDependency TRANSFORMS = new PlacementLifecycleDependency("prepare.transforms");

    @Override
    public void install(PlacementLifecycleBuilder lifecycle) {
        lifecycle.add(PLACER, PlacementStage.PREPARE, (context, state, ignored) -> {
            if (state.config.getHeightmap() != null) {
                state.placer = new HeightmapObjectPlacer(context.rng, context.x, context.yv, context.z,
                        state.config, context.placer);
            }
        });
        lifecycle.add(TRANSFORMS, PlacementStage.PREPARE, PlacementModulePreparation::prepare,
                PlacementModuleSlope.SLOPE);
    }

    private static void prepare(PlacementContext context, PlacementState state, Void ignored) {
        IrisObjectPlacement config = state.config;
        IrisObject self = context.self;
        int spinx = context.rng.imax() / 1000;
        int spiny = context.rng.imax() / 1000;
        int spinz = context.rng.imax() / 1000;
        state.spinX = spinx;
        state.spinY = spiny;
        state.spinZ = spinz;
        state.rty = config.getRotation().rotate(new IrisBlockVector(0, self.getCenter().getBlockY(), 0), spinx, spiny, spinz).getBlockY();
        state.ty = config.getTranslate().translate(new IrisBlockVector(0, self.getCenter().getBlockY(), 0), config.getRotation(), spinx, spiny, spinz).getBlockY();
        state.spin = new SpinKernel(config.getRotation(), spinx, spiny, spinz);
        state.ceilingHang = config.getMode() == ObjectPlaceMode.CEILING_HANG;
        IrisObjectTranslate translate = config.getTranslate();
        if (translate.canTranslate()) {
            state.translateOffset = config.getRotation().rotate(
                    new IrisBlockVector(translate.getX(), translate.getY(), translate.getZ()), spinx, spiny, spinz);
        }
        int yrand = translate.getYRandom();
        state.yrand = yrand > 0 ? context.rng.i(0, yrand) : yrand < 0 ? context.rng.i(yrand, 0) : yrand;
        state.warpMargin = !config.getWarp().isFlat() ? (int) Math.ceil(Math.abs(config.getWarp().getMultiplier()) / 2D) : 0;
        state.bounds = transformedBounds(self, state.spin, state.translateOffset != null, state.translateOffset,
                state.ceilingHang, state.warpMargin);
    }

    private static PlacementContext.TransformedBounds transformedBounds(IrisObject self, SpinKernel spin, boolean translating, IrisBlockVector translateOffset,
                                                boolean ceilingHang, int margin) {
        int sourceMinX = -self.getCenter().getBlockX();
        int sourceMaxX = self.getW() - self.getCenter().getBlockX() - 1;
        int sourceMinY = -self.getCenter().getBlockY();
        int sourceMaxY = self.getH() - self.getCenter().getBlockY() - 1;
        int sourceMinZ = -self.getCenter().getBlockZ();
        int sourceMaxZ = self.getD() - self.getCenter().getBlockZ() - 1;
        int minX = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int minY = Integer.MAX_VALUE;
        int maxY = Integer.MIN_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxZ = Integer.MIN_VALUE;

        for (int xCorner = 0; xCorner < 2; xCorner++) {
            int sourceX = xCorner == 0 ? sourceMinX : sourceMaxX;
            for (int yCorner = 0; yCorner < 2; yCorner++) {
                int sourceY = yCorner == 0 ? sourceMinY : sourceMaxY;
                for (int zCorner = 0; zCorner < 2; zCorner++) {
                    int sourceZ = zCorner == 0 ? sourceMinZ : sourceMaxZ;
                    IrisBlockVector corner = new IrisBlockVector(sourceX, sourceY, sourceZ);
                    spin.rotate(corner);
                    if (ceilingHang) {
                        corner.setY(-corner.getBlockY());
                    }
                    if (translating) {
                        corner.add(translateOffset);
                    }
                    int transformedX = (int) Math.round(corner.getX());
                    int transformedY = (int) Math.round(corner.getY());
                    int transformedZ = (int) Math.round(corner.getZ());
                    minX = Math.min(minX, transformedX);
                    maxX = Math.max(maxX, transformedX);
                    minY = Math.min(minY, transformedY);
                    maxY = Math.max(maxY, transformedY);
                    minZ = Math.min(minZ, transformedZ);
                    maxZ = Math.max(maxZ, transformedZ);
                }
            }
        }

        return new PlacementContext.TransformedBounds(minX - margin, maxX + margin, minY, maxY, minZ - margin, maxZ + margin);
    }

}
