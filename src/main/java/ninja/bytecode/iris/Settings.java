package ninja.bytecode.iris;

import ninja.bytecode.iris.util.PerformanceMode;
import ninja.bytecode.iris.util.PlacerType;

public class Settings
{
	public PerformanceSettings performance = new PerformanceSettings();
	public GeneratorSettings gen = new GeneratorSettings();

	public static class PerformanceSettings
	{
		public PerformanceMode performanceMode = PerformanceMode.DOUBLE_CPU;
		public PlacerType placerType = PlacerType.BUKKIT_NO_PHYSICS;
		public int threadPriority = Thread.MIN_PRIORITY;
		public int compilerPriority = Thread.MIN_PRIORITY;
		public int threadCount = 1;
		public boolean debugMode = true;
		public int compilerThreads = 12;
		public int decorationAccuracy = 1;
	}

	public static class GeneratorSettings
	{
		public double horizontalZoom = 1; // 0.525
		public double heightFracture = 155;
		public double landScale = 0.205;
		public double landChance = 0.67;
		public double roughness = 1.333;
		public double heightMultiplier = 0.806;
		public double heightExponentBase = 1;
		public double heightExponentMultiplier = 1.41;
		public double heightScale = 0.56;
		public double superHeightScale = 0.95;
		public double baseHeight = 0.165;
		public int seaLevel = 63;
		public double caveDensity = 0;
		public double caveScale = 1.45;
		public double biomeScale = 2;
		public boolean flatBedrock = false;
		public boolean doSchematics = true;
	}
}
