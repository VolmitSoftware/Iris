package ninja.bytecode.iris;

public class Settings
{
	public PerformanceSettings performance = new PerformanceSettings();
	public GeneratorSettings gen = new GeneratorSettings();

	public static class PerformanceSettings
	{
		public PerformanceMode performanceMode = PerformanceMode.UNLIMITED;
		public int threadCount = 4;
		public int threadPriority = Thread.MAX_PRIORITY;
	}

	public static class GeneratorSettings
	{
		public double horizontalZoom = 2.125; //   1.856 2.556
		public double heightFracture = 155;
		public double heightMultiplier = 1.154;
		public double heightExponentBase = 1;
		public double heightExponentMultiplier = 1.41;
		public double humidityByHeightInfluence = 0.1;
		public double temperatureByHeightInfluence = 0.19;
		public double temperatureByHeightOffset = 0.25;
		public double biomeSoftFracture = 66;
		public double biomeSharpFracture = 2;
		public double temperatureScale = 1.65;
		public double humidityScale = 1.4;
		public double heightScale = 1;
		public double superHeightScale = 0.65;
		public double altBiomeScale = 1;
		public double baseHeight = 0.3415;
		public double temperatureIgnorance = 1.55;
		public double humidityIgnorance = 1.55;
		public double heightIgnorance = 1;
		public double mountainMultiplier = 1.65;
		public double mountainHorizontalZoom = 3.15;
		public double mountainSink = 0.0445;
		public double superSamplerRadius = 32;
		public double superSamplerMultiplier = 1;
		public double superSampleOpacity = 1;
		public int superSamplerIterations = 9;
		public double caveSpread = 3.466;
		public double caveChance = 0.03;

		public boolean flatBedrock = false;
	}
}
