package ninja.bytecode.iris;

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
		public double horizontalZoom = 2.225; // 1.856 2.556
		public double heightFracture = 155;
		public double heightMultiplier = 1.154;
		public double heightExponentBase = 1;
		public double heightExponentMultiplier = 1.41;
		public double heightScale = 1;
		public double superHeightScale = 0.65;
		public double altBiomeScale = 1;
		public double baseHeight = 0.22;
		public double superSamplerRadius = 32;
		public int superSamplerIterations = 14;
		public int superSamples = 12;
		public double superSamplerMultiplier = 1;
		public double superSampleThreshold = 0.00984;
		public double superSampleOpacity = 1;
		public int seaLevel = 63;
		public double biomeScale = 1.31;
		public double landChance = 0.75;

		public boolean flatBedrock = false;
	}
}
