package ninja.bytecode.iris;

import ninja.bytecode.iris.util.InterpolationMode;
import ninja.bytecode.iris.util.ObjectMode;
import ninja.bytecode.iris.util.PerformanceMode;

public class Settings
{
	public PerformanceSettings performance = new PerformanceSettings();
	public GeneratorSettings gen = new GeneratorSettings();
	public OreSettings ore = new OreSettings();

	public static class PerformanceSettings
	{
		public PerformanceMode performanceMode = PerformanceMode.EXPLICIT;
		public ObjectMode objectMode = ObjectMode.PARALLAX;
		public int threadPriority = Thread.MAX_PRIORITY;
		public int threadCount = 16;
		public boolean debugMode = true;
		public int decorationAccuracy = 1;
		public boolean noObjectFail = false;
		public boolean verbose = true;
		public int placeHistoryLimit = 8192;
	}

	public static class GeneratorSettings
	{
		public InterpolationMode interpolationMode = InterpolationMode.BILINEAR;
		public int interpolationRadius = 64;
		public int blockSmoothing = 1;
		public double objectDensity = 1D;
		public double horizontalZoom = 2;
		public double heightFracture = 155;
		public double landScale = 0.5;
		public double landChance = 0.56;
		public double roughness = 1.55;
		public double biomeEdgeFuzzScale = 1;
		public double biomeEdgeScrambleScale = 0.3;
		public double biomeEdgeScrambleRange = 0.9;
		public double heightMultiplier = 0.806;
		public double heightExponentBase = 1;
		public double heightExponentMultiplier = 1.41;
		public double heightScale = 0.56;
		public double baseHeight = 0.065;
		public int seaLevel = 63;
		public double biomeScale = 0.75;
		public boolean flatBedrock = false;
	}

	public static class OreSettings
	{
		public int ironMinHeight = 5;
		public int ironMaxHeight = 65;
		public double ironMaxDispersion = 0.02;
		public double ironMinDispersion = 0.26;

		public int coalMinHeight = 5;
		public int coalMaxHeight = 100;
		public double coalMaxDispersion = 0.02;
		public double coalMinDispersion = 0.29;

		public int goldMinHeight = 5;
		public int goldMaxHeight = 34;
		public double goldMaxDispersion = 0.01;
		public double goldMinDispersion = 0.13;

		public int redstoneMinHeight = 5;
		public int redstoneMaxHeight = 15;
		public double redstoneMaxDispersion = 0.05;
		public double redstoneMinDispersion = 0.17;

		public int lapisMinHeight = 13;
		public int lapisMaxHeight = 33;
		public double lapisMaxDispersion = 0.05;
		public double lapisMinDispersion = 0.12;

		public int diamondMinHeight = 5;
		public int diamondMaxHeight = 16;
		public double diamondMaxDispersion = 0.05;
		public double diamondMinDispersion = 0.1;

		public int emeraldMinHeight = 5;
		public int emeraldMaxHeight = 16;
		public double emeraldMaxDispersion = 0.005;
		public double emeraldMinDispersion = 0.07;
	}
}
