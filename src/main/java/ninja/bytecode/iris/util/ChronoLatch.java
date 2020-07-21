package ninja.bytecode.iris.util;

public class ChronoLatch
{
	private long interval;
	private long since;
	
	public ChronoLatch(long interval, boolean openedAtStart)
	{
		this.interval = interval;
		since = System.currentTimeMillis() - (openedAtStart ? interval * 2 : 0);
	}
	
	public ChronoLatch(long interval)
	{
		this(interval, true);
	}
	
	public boolean flip()
	{
		if(System.currentTimeMillis() - since > interval)
		{
			since = System.currentTimeMillis();
			return true;
		}
		
		return false;
	}
}
