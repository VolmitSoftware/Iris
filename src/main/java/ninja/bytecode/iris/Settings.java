package ninja.bytecode.iris;

import ninja.bytecode.iris.util.PerformanceMode;

public class Settings
{
	public PerformanceSettings performance = new PerformanceSettings();
	public GeneratorSettings gen = new GeneratorSettings();

	public static class PerformanceSettings
	{
		public PerformanceMode performanceMode = PerformanceMode.HALF_CPU;
		public int threadCount = 12;
		public int threadPriority = Thread.MIN_PRIORITY;
		public boolean loadonstart = false;
	}

	public static class GeneratorSettings
	{ 
		public double horizontalZoom = 0.525; // 0.525
		public double heightFracture = 155;
		public double heightMultiplier = 0.806;
		public double heightExponentBase = 1;
		public double heightExponentMultiplier = 1.41;
		public double heightScale = 1;
		public double superHeightScale = 0.95;
		public double baseHeight = 0.165;
		public int seaLevel = 63;
		public double caveDensity = 3;
		public double biomeScale = 2.46;
		public boolean flatBedrock = false;
		public boolean doSchematics = true;
	}
}
