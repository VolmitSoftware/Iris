package ninja.bytecode.iris.util;

public interface NastyFuture<R>
{
	public R run() throws Throwable;
}
