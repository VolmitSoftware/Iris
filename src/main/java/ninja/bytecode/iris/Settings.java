package ninja.bytecode.iris;

import ninja.bytecode.iris.util.PerformanceMode;

public class Settings
{
	public PerformanceSettings performance = new PerformanceSettings();
	public GeneratorSettings gen = new GeneratorSettings();

	public static class PerformanceSettings
	{
		public PerformanceMode performanceMode = PerformanceMode.HALF_CPU;
		public int threadCount = 4;
		public int threadPriority = Thread.MAX_PRIORITY;
	}

	public static class GeneratorSettings
	{ 
		public double horizontalZoom = 0.525; // 2.225 - 1.625 (big)
		public double heightFracture = 155;
		public double heightMultiplier = 0.806;
		public double heightExponentBase = 1;
		public double heightExponentMultiplier = 1.41;
		public double heightScale = 1;
		public double superHeightScale = 0.65;
		public double baseHeight = 0.165;
		public int seaLevel = 63;
		public double biomeScale = 2.46;
		public boolean flatBedrock = false;
		public boolean doTrees = false;
	}
}
