package ninja.bytecode.iris;

public class Settings
{
	public PerformanceSettings performance = new PerformanceSettings();
	public GeneratorSettings gen = new GeneratorSettings();
	
	public static class PerformanceSettings
	{
		public int threadCount = -1;
		public int threadPriority = Thread.MAX_PRIORITY;
	}
	
	public static class GeneratorSettings
	{
		public boolean flatBedrock = false;
	}
}
