package ninja.bytecode.iris;

import ninja.bytecode.iris.util.PerformanceMode;

public class Settings
{
	public PerformanceSettings performance = new PerformanceSettings();
	public GeneratorSettings gen = new GeneratorSettings();

	public static class PerformanceSettings
	{
		public PerformanceMode performanceMode = PerformanceMode.DOUBLE_CPU;
		public int threadCount = 1;
		public int threadPriority = Thread.MIN_PRIORITY;
		public boolean loadonstart = true;
		public int compilerThreads = 4;
		public int compilerPriority = Thread.MAX_PRIORITY;
		public int decorationAccuracy = 3;
	}

	public static class GeneratorSettings
	{
		public double horizontalZoom = 1; // 0.525
		public double heightFracture = 155;
		public double landScale = 0.205;
		public double landChance = 0.6;
		public double roughness = 1.333;
		public double heightMultiplier = 0.806;
		public double heightExponentBase = 1;
		public double heightExponentMultiplier = 1.41;
		public double heightScale = 0.56;
		public double superHeightScale = 0.95;
		public double baseHeight = 0.165;
		public int seaLevel = 63;
		public double caveDensity = 1;
		public double biomeScale = 2;
		public boolean flatBedrock = false;
		public boolean doSchematics = true;
	}
}
