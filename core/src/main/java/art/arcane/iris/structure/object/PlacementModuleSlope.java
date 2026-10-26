package art.arcane.iris.structure.object;


import art.arcane.iris.generation.runtime.Engine;
import art.arcane.volmlib.util.stream.ProceduralStream;

/** Slope checks precede random rotation and leave the caller's configuration intact. */
final class PlacementModuleSlope implements PlacementModule {
    static final PlacementLifecycleDependency SLOPE = new PlacementLifecycleDependency("prepare.slope");

    @Override
    public void install(PlacementLifecycleBuilder lifecycle) {
        lifecycle.add(SLOPE, PlacementStage.PREPARE, (context, state, ignored) -> {
            IrisObjectPlacement config = state.config;
            boolean evaluate = !config.isForcePlace() && !config.getSlopeCondition().isDefault();
            if (!evaluate && !config.isRotateTowardsSlope()) {
                return;
            }
            Engine engine = state.placer.getEngine();
            if (engine == null) {
                throw new IllegalStateException("Object placement requires an active Iris engine for slope settings.");
            }
            if (evaluate && !config.getSlopeCondition().isValid(
                    engine.getComplex().getSlopeStream().get(context.x, context.z))) {
                state.reject("slope");
                return;
            }
            if (config.isRotateTowardsSlope()) {
                state.config = rotate(context.self, config, engine, context.x, context.z);
            }
        }, PlacementModulePreparation.PLACER);
    }

    private static IrisObjectPlacement rotate(IrisObject self, IrisObjectPlacement config, Engine engine, int x, int z) {
        int slopeRotationY = 0;
        ProceduralStream<Double> heightStream = engine.getComplex().getHeightStream();
        // Whichever side of the rectangle that bounds the object is lowest is the 'direction' of the slope (simply said).
        double hNorth = heightStream.get(x, z + ((float) self.d) / 2);
        double hEast = heightStream.get(x + ((float) self.w) / 2, z);
        double hSouth = heightStream.get(x, z - ((float) self.d) / 2);
        double hWest = heightStream.get(x - ((float) self.w) / 2, z);
        double min = Math.min(Math.min(hNorth, hEast), Math.min(hSouth, hWest));
        if (min == hNorth) {
            slopeRotationY = 0;
        } else if (min == hEast) {
            slopeRotationY = 90;
        } else if (min == hSouth) {
            slopeRotationY = 180;
        } else if (min == hWest) {
            slopeRotationY = 270;
        }

        double newRotation = config.getRotation().getYAxis().getMin() + slopeRotationY;
        IrisObjectRotation originalRotation = config.getRotation();
        IrisObjectRotation slopeRotation = new IrisObjectRotation();
        slopeRotation.setXAxis(originalRotation.getXAxis());
        slopeRotation.setZAxis(originalRotation.getZAxis());
        if (newRotation == 0) {
            slopeRotation.setYAxis(new IrisAxisRotationClamp(false, false, 0, 0, 90));
            slopeRotation.setEnabled(originalRotation.canRotateX() || originalRotation.canRotateZ());
        } else {
            slopeRotation.setYAxis(new IrisAxisRotationClamp(true, false, newRotation, newRotation, 90));
            slopeRotation.setEnabled(true);
        }
        config = config.toPlacement(config.getPlace().toArray(new String[0]));
        config.setRotation(slopeRotation);
        return config;
    }

}
