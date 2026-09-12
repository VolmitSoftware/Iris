package art.arcane.iris.generation.geometry;

public class IrisVector implements Cloneable {
    private static final double EPSILON = 0.000001;
    protected double x;
    protected double y;
    protected double z;

    public IrisVector() {
        this.x = 0;
        this.y = 0;
        this.z = 0;
    }

    public IrisVector(int x, int y, int z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }

    public IrisVector(double x, double y, double z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }

    public double getX() {
        return x;
    }

    public double getY() {
        return y;
    }

    public double getZ() {
        return z;
    }

    public int getBlockX() {
        return floor(x);
    }

    public int getBlockY() {
        return floor(y);
    }

    public int getBlockZ() {
        return floor(z);
    }

    public IrisVector setX(double x) {
        this.x = x;
        return this;
    }

    public IrisVector setY(double y) {
        this.y = y;
        return this;
    }

    public IrisVector setZ(double z) {
        this.z = z;
        return this;
    }

    public IrisVector add(IrisVector vec) {
        x += vec.x;
        y += vec.y;
        z += vec.z;
        return this;
    }

    public IrisVector subtract(IrisVector vec) {
        x -= vec.x;
        y -= vec.y;
        z -= vec.z;
        return this;
    }

    public IrisVector multiply(double m) {
        x *= m;
        y *= m;
        z *= m;
        return this;
    }

    public double distance(IrisVector o) {
        return Math.sqrt(square(x - o.x) + square(y - o.y) + square(z - o.z));
    }

    public double distanceSquared(IrisVector o) {
        return square(x - o.x) + square(y - o.y) + square(z - o.z);
    }

    public IrisVector rotateAroundX(double angle) {
        double angleCos = Math.cos(angle);
        double angleSin = Math.sin(angle);
        double y = angleCos * this.y - angleSin * this.z;
        double z = angleSin * this.y + angleCos * this.z;
        return setY(y).setZ(z);
    }

    public IrisVector rotateAroundY(double angle) {
        double angleCos = Math.cos(angle);
        double angleSin = Math.sin(angle);
        double x = angleCos * this.x + angleSin * this.z;
        double z = -angleSin * this.x + angleCos * this.z;
        return setX(x).setZ(z);
    }

    public IrisVector rotateAroundZ(double angle) {
        double angleCos = Math.cos(angle);
        double angleSin = Math.sin(angle);
        double x = angleCos * this.x - angleSin * this.y;
        double y = angleSin * this.x + angleCos * this.y;
        return setX(x).setY(y);
    }

    public IrisBlockVector toBlockVector() {
        return new IrisBlockVector(x, y, z);
    }

    @Override
    public IrisVector clone() {
        try {
            return (IrisVector) super.clone();
        } catch (CloneNotSupportedException e) {
            throw new Error(e);
        }
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof IrisVector other)) {
            return false;
        }

        return Math.abs(x - other.x) < EPSILON && Math.abs(y - other.y) < EPSILON && Math.abs(z - other.z) < EPSILON && this.getClass().equals(obj.getClass());
    }

    @Override
    public int hashCode() {
        int hash = 7;
        hash = 79 * hash + (int) (Double.doubleToLongBits(this.x) ^ (Double.doubleToLongBits(this.x) >>> 32));
        hash = 79 * hash + (int) (Double.doubleToLongBits(this.y) ^ (Double.doubleToLongBits(this.y) >>> 32));
        hash = 79 * hash + (int) (Double.doubleToLongBits(this.z) ^ (Double.doubleToLongBits(this.z) >>> 32));
        return hash;
    }

    @Override
    public String toString() {
        return x + "," + y + "," + z;
    }

    protected static int floor(double num) {
        int floor = (int) num;
        return floor == num ? floor : floor - (int) (Double.doubleToRawLongBits(num) >>> 63);
    }

    private static double square(double num) {
        return num * num;
    }
}
