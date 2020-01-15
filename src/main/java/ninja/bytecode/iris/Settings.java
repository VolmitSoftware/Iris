package ninja.bytecode.iris;

import ninja.bytecode.iris.util.InterpolationType;
import ninja.bytecode.iris.util.PerformanceMode;

public class Settings
{
	public PerformanceSettings performance = new PerformanceSettings();
	public GeneratorSettings gen = new GeneratorSettings();

	public static class PerformanceSettings
	{
		public PerformanceMode performanceMode = PerformanceMode.MATCH_CPU;
		public boolean fastDecoration = true;
		public int threadPriority = Thread.MIN_PRIORITY;
		public int compilerPriority = Thread.MIN_PRIORITY;
		public int threadCount = 1;
		public boolean debugMode = true;
		public int compilerThreads = 12;
		public int decorationAccuracy = 1;
		public int cascadeLimit = 14;
	}

	public static class GeneratorSettings
	{
		public InterpolationType linearFunction = InterpolationType.PARAMETRIC_2;
		public int linearSampleRadius = 2;
		public int interpolationIntervals = 1;
		public double horizontalZoom = 1;
		public double heightFracture = 155;
		public double beachScale = 76;
		public double landScale = 0.325;
		public double landChance = 0.62;
		public double biomeEdgeScramble = 0D; // 1550D
		public double roughness = 1.25;
		public double heightMultiplier = 0.806;
		public double heightExponentBase = 1;
		public double heightExponentMultiplier = 1.41;
		public double heightScale = 0.56;
		public double superHeightScale = 0.95;
		public double baseHeight = 0.165;
		public int seaLevel = 63;
		public double caveDensity = 4;
		public double caveScale = 1.45;
		public double biomeScale = 2;
		public boolean flatBedrock = false;
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
