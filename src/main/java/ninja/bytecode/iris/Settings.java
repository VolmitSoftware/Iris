package ninja.bytecode.iris;

import ninja.bytecode.iris.util.PerformanceMode;

public class Settings
{
	public PerformanceSettings performance = new PerformanceSettings();
	public GeneratorSettings gen = new GeneratorSettings();

	public static class PerformanceSettings
	{
		public PerformanceMode performanceMode = PerformanceMode.UNLIMITED;
		public boolean fastDecoration = true;
		public int threadPriority = Thread.MAX_PRIORITY;
		public int threadCount = 4;
		public boolean debugMode = true;
		public int decorationAccuracy = 1;
		public int cascadeLimit = 14;
		public boolean interpolation = true;
		public boolean surfaceNoise = true;
		public boolean baseNoise = true;
	}

	public static class GeneratorSettings
	{
		public int hermiteSampleRadius = 6;
		public double horizontalZoom = 2;
		public double heightFracture = 155;
		public double landScale = 0.5;
		public double landChance = 0.5;
		public double biomeEdgeScramble = 1550D; // 1550D
		public double roughness = 1.55;
		public double heightMultiplier = 0.806;
		public double heightExponentBase = 1;
		public double heightExponentMultiplier = 1.41;
		public double heightScale = 0.56;
		public double baseHeight = 0.165;
		public int seaLevel = 63;
		public double caveDensity = 4;
		public double caveScale = 1.45;
		public double biomeScale = 1.65;
		public boolean flatBedrock = true;
		public boolean genObjects = false;
		public boolean genCarving = false;
		public boolean genCaverns = false;
		public boolean genCaves = false;
		public double carvingChance = 0.352;
		public double cavernChance = 0.321;
		public int minCarvingHeight = 75;
		public int maxCarvingHeight = 155;
		public int minCavernHeight = 14;
		public int maxCavernHeight = 54;
	}
}
