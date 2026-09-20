package art.arcane.iris.modded;

import art.arcane.iris.generation.geometry.IrisBlockVector;
import art.arcane.iris.spi.IrisLogging;
import art.arcane.iris.structure.object.IrisObjectRotation;
import art.arcane.volmlib.nativelib.minecraft26_2.modded.NativeStateRotator;
import art.arcane.volmlib.nativelib.terrain.NativeBlockState;

public final class ModdedStateRotator implements IrisObjectRotation.StateRotator {
    @Override
    public NativeBlockState rotate(IrisObjectRotation rotation, NativeBlockState state, int spinxx, int spinyy, int spinzz) {
        if (!rotation.canRotate()) {
            return state;
        }
        int spinx = (int) (90D * Math.ceil(Math.abs((spinxx % 360D) / 90D)));
        int spiny = (int) (90D * Math.ceil(Math.abs((spinyy % 360D) / 90D)));
        int spinz = (int) (90D * Math.ceil(Math.abs((spinzz % 360D) / 90D)));
        return NativeStateRotator.rotate(state, (x, y, z) -> {
            IrisBlockVector rotated = rotation.rotate(new IrisBlockVector(x, y, z), spinx, spiny, spinz);
            return new NativeStateRotator.Vector(rotated.getX(), rotated.getY(), rotated.getZ());
        }, IrisLogging::reportError);
    }
}
