package ninja.bytecode.iris.util;

@FunctionalInterface
public interface BorderCheck<T>
{
	public T get(double x, double z);
}