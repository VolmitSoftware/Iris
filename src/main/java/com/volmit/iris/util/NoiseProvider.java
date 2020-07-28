package ninja.bytecode.iris.util;
@FunctionalInterface
public interface NoiseProvider
{
	public double noise(double x, double z);
}