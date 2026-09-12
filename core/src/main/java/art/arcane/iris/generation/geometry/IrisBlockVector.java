package art.arcane.iris.generation.geometry;

public class IrisBlockVector extends IrisVector {
    public IrisBlockVector() {
        super();
    }

    public IrisBlockVector(int x, int y, int z) {
        super(x, y, z);
    }

    public IrisBlockVector(double x, double y, double z) {
        super(x, y, z);
    }

    @Override
    public IrisBlockVector clone() {
        return (IrisBlockVector) super.clone();
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof IrisBlockVector other)) {
            return false;
        }

        return (int) other.getX() == (int) this.x && (int) other.getY() == (int) this.y && (int) other.getZ() == (int) this.z;
    }

    @Override
    public int hashCode() {
        return (Integer.hashCode((int) x) >> 13) ^ (Integer.hashCode((int) y) >> 7) ^ Integer.hashCode((int) z);
    }
}
