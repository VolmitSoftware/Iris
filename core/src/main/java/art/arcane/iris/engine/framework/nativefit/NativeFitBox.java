package art.arcane.iris.engine.framework.nativefit;

/** A box of world blocks as the platform measures it: world heights, every bound inclusive. */
public record NativeFitBox(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
}
