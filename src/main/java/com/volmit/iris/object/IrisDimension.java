package com.volmit.iris.object;

import com.volmit.iris.generator.noise.CNG;
import com.volmit.iris.manager.IrisDataManager;
import com.volmit.iris.scaffold.cache.AtomicCache;
import com.volmit.iris.scaffold.data.DataProvider;
import com.volmit.iris.util.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import org.bukkit.Material;
import org.bukkit.World.Environment;
import org.bukkit.block.data.BlockData;

@Accessors(chain = true)
@AllArgsConstructor
@NoArgsConstructor
@Desc("Represents a dimension")
@Data
@EqualsAndHashCode(callSuper = false)
public class IrisDimension extends IrisRegistrant
{
	public static final BlockData STONE = Material.STONE.createBlockData();
	public static final BlockData WATER = Material.WATER.createBlockData();

	@MinNumber(2)
	@Required
	@DontObfuscate
	@Desc("The human readable name of this dimension")
	private String name = "A Dimension";

	@Desc("You can create mutliple dimensions on top of each other taking up less height of the same world. Such as the nether with a floor + ceiling.")
	@ArrayType(min = 1,type = IrisDimensionIndex.class)
	private KList<IrisDimensionIndex> dimensionalComposite = new KList<>();

	@DontObfuscate
	@Desc("Create an inverted dimension in the sky (like the nether)")
	private IrisDimension sky = null;

	@RegistryListJigsaw
	@DontObfuscate
	@Desc("If defined, Iris will place the given jigsaw structure where minecraft should place the overworld stronghold.")
	private String stronghold;

	@DontObfuscate
	@Desc("Improves the biome grid variation by shuffling the cell grid more depending on the seed. This makes biomes across multiple seeds look far different than before.")
	private boolean aggressiveBiomeReshuffle = false;

	@DontObfuscate
	@Desc("Instead of a flat bottom, applies a clamp (using this noise style) to the bottom instead of a flat bottom. Useful for carving out center-dimensions in a dimension composite world.")
	private IrisShapedGeneratorStyle undercarriage = null;

	@DontObfuscate
	@Desc("Upon joining this world, Iris will send a resource pack request to the client. If they have previously selected yes, it will auto-switch depending on which dimension they go to.")
	private String resourcePack = "";

	@DontObfuscate
	@Desc("Entity spawns to override or add to this dimension")
	@ArrayType(min = 1, type = IrisEntitySpawnOverride.class)
	private KList<IrisEntitySpawnOverride> entitySpawnOverrides = new KList<>();

	@DontObfuscate
	@Desc("Add specific features in exact positions")
	@ArrayType(min = 1, type = IrisFeaturePositional.class)
	private KList<IrisFeaturePositional> specificFeatures = new KList<>();

	@DontObfuscate
	@Desc("Entity spawns during generation")
	@ArrayType(min = 1, type = IrisEntityInitialSpawn.class)
	private KList<IrisEntityInitialSpawn> entityInitialSpawns = new KList<>();

	@DontObfuscate
	@Desc("Add random chances for terrain features")
	@ArrayType(min = 1, type = IrisFeaturePotential.class)
	private KList<IrisFeaturePotential> features = new KList<>();

	@DontObfuscate
	@Desc("Reference loot tables in this area")
	private IrisLootReference loot = new IrisLootReference();

	@MinNumber(0)
	@DontObfuscate
	@Desc("The version of this dimension. Changing this will stop users from accidentally upgrading (and breaking their worlds).")
	private int version = 1;

	@ArrayType(min = 1, type = IrisBlockDrops.class)
	@DontObfuscate
	@Desc("Define custom block drops for this dimension")
	private KList<IrisBlockDrops> blockDrops = new KList<>();

	@DontObfuscate
	@Desc("Should bedrock be generated or not.")
	private boolean bedrock = true;

	@MinNumber(0)
	@MaxNumber(1)
	@DontObfuscate
	@Desc("The land chance. Up to 1.0 for total land or 0.0 for total sea")
	private double landChance = 0.625;

	@DontObfuscate
	@Desc("The placement style of regions")
	private IrisGeneratorStyle regionStyle = NoiseStyle.CELLULAR_IRIS_DOUBLE.style();

	@DontObfuscate
	@Desc("The placement style of land/sea")
	private IrisGeneratorStyle continentalStyle = NoiseStyle.CELLULAR_IRIS_DOUBLE.style();

	@DontObfuscate
	@Desc("The placement style of biomes")
	private IrisGeneratorStyle landBiomeStyle = NoiseStyle.CELLULAR_IRIS_DOUBLE.style();

	@DontObfuscate
	@Desc("The placement style of biomes")
	private IrisGeneratorStyle shoreBiomeStyle = NoiseStyle.CELLULAR_IRIS_DOUBLE.style();

	@DontObfuscate
	@Desc("The placement style of biomes")
	private IrisGeneratorStyle seaBiomeStyle = NoiseStyle.CELLULAR_IRIS_DOUBLE.style();

	@DontObfuscate
	@Desc("The placement style of biomes")
	private IrisGeneratorStyle caveBiomeStyle = NoiseStyle.CELLULAR_IRIS_DOUBLE.style();

	@DontObfuscate
	@Desc("The placement style of biomes")
	private IrisGeneratorStyle riverBiomeStyle = NoiseStyle.CELLULAR_IRIS_DOUBLE.style();

	@DontObfuscate
	@Desc("The placement style of biomes")
	private IrisGeneratorStyle lakeBiomeStyle = NoiseStyle.CELLULAR_IRIS_DOUBLE.style();

	@DontObfuscate
	@Desc("The placement style of biomes")
	private IrisGeneratorStyle islandBiomeStyle = NoiseStyle.CELLULAR_IRIS_DOUBLE.style();

	@DontObfuscate
	@Desc("The placement style of biomes")
	private IrisGeneratorStyle islandBiomeChanceStyle = NoiseStyle.CELLULAR_HEIGHT_IRIS_DOUBLE.style();

	@DontObfuscate
	@Desc("The placement style of biomes")
	private IrisGeneratorStyle skylandBiomeStyle = NoiseStyle.CELLULAR_IRIS_DOUBLE.style();

	@DontObfuscate
	@Desc("Generate caves or not.")
	private boolean caves = true;

	@DontObfuscate
	@Desc("Instead of filling objects with air, fills them with cobweb so you can see them")
	private boolean debugSmartBore = false;

	@DontObfuscate
	@Desc("Carve terrain or not")
	private boolean carving = true;

	@DontObfuscate
	@Desc("If defined, If air is defined below the area, this fluid will always place")
	private IrisCaveFluid forceFluid = new IrisCaveFluid();

	@DontObfuscate
	@Desc("Generate decorations or not")
	private boolean decorate = true;

	@DontObfuscate
	@Desc("Generate ravines or not")
	private boolean ravines = false;

	@MinNumber(1)
	@DontObfuscate
	@Desc("The rarity of a ravine layer having a lib (or rib) that sticks in or out by one block. Minecraft's default is 3.")
	private int ravineRibRarity = 2;

	@MinNumber(1)
	@DontObfuscate
	@Desc("The rarity of ravines. Each chunk has a 1 in X chance")
	private int ravineRarity = 50;

	@DontObfuscate
	@Desc("Use post processing or not")
	private boolean postProcessing = true;

	@DontObfuscate
	@Desc("Add slabs in post processing")
	private boolean postProcessingSlabs = true;

	@DontObfuscate
	@Desc("Add painted walls in post processing")
	private boolean postProcessingWalls = true;

	@DontObfuscate
	@Desc("Use post processing for caves or not")
	private boolean postProcessCaves = true;

	@DontObfuscate
	@Desc("The world environment")
	private Environment environment = Environment.NORMAL;

	@RegistryListRegion
	@Required
	@ArrayType(min = 1, type = String.class)
	@DontObfuscate
	@Desc("Define all of the regions to include in this dimension. Dimensions -> Regions -> Biomes -> Objects etc")
	private KList<String> regions = new KList<>();

	@ArrayType(min = 1, type = IrisJigsawStructurePlacement.class)
	@DontObfuscate
	@Desc("Jigsaw structures")
	private KList<IrisJigsawStructurePlacement> jigsawStructures = new KList<>();

	@Required
	@MinNumber(0)
	@MaxNumber(255)
	@DontObfuscate
	@Desc("The fluid height for this dimension")
	private int fluidHeight = 63;

	@RegistryListBiome
	@DontObfuscate
	@Desc("Keep this either undefined or empty. Setting any biome name into this will force iris to only generate the specified biome. Great for testing.")
	private String focus = "";

	@RegistryListBiome
	@DontObfuscate
	@Desc("Keep this either undefined or empty. Setting any region name into this will force iris to only generate the specified region. Great for testing.")
	private String focusRegion = "";

	@MinNumber(0.0001)
	@MaxNumber(512)
	@DontObfuscate
	@Desc("Zoom in or out the biome size. Higher = bigger biomes")
	private double biomeZoom = 5D;

	@MinNumber(0.0001)
	@MaxNumber(512)
	@DontObfuscate
	@Desc("Zoom in or out the terrain. This stretches the terrain. Due to performance improvements, Higher than 2.0 may cause weird rounding artifacts. Lower = more terrain changes per block. Its a true zoom-out.")
	private double terrainZoom = 1D;

	@MinNumber(0)
	@MaxNumber(360)
	@DontObfuscate
	@Desc("You can rotate the input coordinates by an angle. This can make terrain appear more natural (less sharp corners and lines). This literally rotates the entire dimension by an angle. Hint: Try 12 degrees or something not on a 90 or 45 degree angle.")
	private double dimensionAngleDeg = 0;

	@MinNumber(0)
	@MaxNumber(8192)
	@DontObfuscate
	@Desc("Coordinate fracturing applies noise to the input coordinates. This creates the 'iris swirls' and wavy features. The distance pushes these waves further into places they shouldnt be. This is a block value multiplier.")
	private double coordFractureDistance = 20;

	@MinNumber(0.0001)
	@MaxNumber(512)
	@DontObfuscate
	@Desc("Coordinate fracturing zoom. Higher = less frequent warping, Lower = more frequent and rapid warping / swirls.")
	private double coordFractureZoom = 8;

	@MinNumber(0.0001)
	@MaxNumber(512)
	@DontObfuscate
	@Desc("This zooms in the land space")
	private double landZoom = 1;

	@MinNumber(0.0001)
	@MaxNumber(512)
	@DontObfuscate
	@Desc("This zooms oceanic biomes")
	private double seaZoom = 1;

	@MinNumber(0.0001)
	@MaxNumber(512)
	@DontObfuscate
	@Desc("Zoom in continents")
	private double continentZoom = 1;

	@MinNumber(0.0001)
	@MaxNumber(512)
	@DontObfuscate
	@Desc("Change the size of regions")
	private double regionZoom = 1;

	@DontObfuscate
	@Desc("Disable this to stop placing schematics in biomes")
	private boolean placeObjects = true;

	@DontObfuscate
	@Desc("Prevent Leaf decay as if placed in creative mode")
	private boolean preventLeafDecay = false;

	@ArrayType(min = 1, type = IrisDepositGenerator.class)
	@DontObfuscate
	@Desc("Define global deposit generators")
	private KList<IrisDepositGenerator> deposits = new KList<>();

	@ArrayType(min = 1, type = IrisShapedGeneratorStyle.class)
	@DontObfuscate
	@Desc("Overlay additional noise on top of the interoplated terrain.")
	private KList<IrisShapedGeneratorStyle> overlayNoise = new KList<>();

	@ArrayType(min = 1, type = IrisCaveLayer.class)
	@DontObfuscate
	@Desc("Define cave layers")
	private KList<IrisCaveLayer> caveLayers = new KList<>();

	@ArrayType(min = 1, type = IrisCarveLayer.class)
	@DontObfuscate
	@Desc("Define carve layers")
	private KList<IrisCarveLayer> carveLayers = new KList<>();

	@MinNumber(0.0001)
	@MaxNumber(512)
	@DontObfuscate
	@Desc("The rock zoom mostly for zooming in on a wispy palette")
	private double rockZoom = 5;

	@DontObfuscate
	@Desc("The palette of blocks for 'stone'")
	private IrisMaterialPalette rockPalette = new IrisMaterialPalette().qclear().qadd("stone");

	@DontObfuscate
	@Desc("The palette of blocks for 'water'")
	private IrisMaterialPalette fluidPalette = new IrisMaterialPalette().qclear().qadd("water");

	@ArrayType(min = 1, type = IrisBiomeMutation.class)
	@DontObfuscate
	@Desc("Define biome mutations for this dimension")
	private KList<IrisBiomeMutation> mutations = new KList<>();

	private final transient AtomicCache<ChunkPosition> parallaxSize = new AtomicCache<>();
	private final transient AtomicCache<CNG> rockLayerGenerator = new AtomicCache<>();
	private final transient AtomicCache<CNG> fluidLayerGenerator = new AtomicCache<>();
	private final transient AtomicCache<CNG> coordFracture = new AtomicCache<>();
	private final transient AtomicCache<Double> sinr = new AtomicCache<>();
	private final transient AtomicCache<Double> cosr = new AtomicCache<>();
	private final transient AtomicCache<Double> rad = new AtomicCache<>();

	public boolean hasSky()
	{
		return getSky() != null;
	}

	public CNG getCoordFracture(RNG rng, int signature)
	{
		return coordFracture.aquire(() ->
		{
			CNG coordFracture = CNG.signature(rng.nextParallelRNG(signature));
			coordFracture.scale(0.012 / coordFractureZoom);
			return coordFracture;
		});
	}

	public double getDimensionAngle()
	{
		return rad.aquire(() -> Math.toRadians(dimensionAngleDeg));
	}

	public boolean isCarved(int x, int y, int z, RNG rng, int terrainHeight)
	{
		if(isCarving() && terrainHeight > getFluidHeight() || y < terrainHeight)
		{
			for(IrisCarveLayer j : getCarveLayers())
			{
				if(j.isCarved(rng, x, y, z))
				{
					return true;
				}
			}
		}

		return false;
	}

	public Environment getEnvironment(){
		return environment;
	}

	public boolean hasFocusRegion(){
		return !focusRegion.equals("");
	}

	public String getFocusRegion(){
		return focusRegion;
	}

	public double sinRotate()
	{
		return sinr.aquire(() -> Math.sin(getDimensionAngle()));
	}

	public double cosRotate()
	{
		return cosr.aquire(() -> Math.cos(getDimensionAngle()));
	}

	public KList<IrisRegion> getAllRegions(DataProvider g)
	{
		KList<IrisRegion> r = new KList<>();

		for(String i : getRegions())
		{
			r.add(g.getData().getRegionLoader().load(i));
		}

		return r;
	}

	public KList<IrisRegion> getAllAnyRegions()
	{
		KList<IrisRegion> r = new KList<>();

		for(String i : getRegions())
		{
			r.add(IrisDataManager.loadAnyRegion(i));
		}

		return r;
	}

	public KList<IrisBiome> getAllBiomes(DataProvider g)
	{
		KList<IrisBiome> r = new KList<>();

		for(IrisRegion i : getAllRegions(g))
		{
			if(i == null)
			{
				continue;
			}

			r.addAll(i.getAllBiomes(g));
		}

		return r;
	}

	public KList<IrisBiome> getAllAnyBiomes()
	{
		KList<IrisBiome> r = new KList<>();

		for(IrisRegion i : getAllAnyRegions())
		{
			if(i == null)
			{
				continue;
			}

			r.addAll(i.getAllAnyBiomes());
		}

		return r;
	}

	public IrisGeneratorStyle getBiomeStyle(InferredType type)
	{
		switch(type)
		{
			case CAVE:
				return caveBiomeStyle;
			case LAKE:
				return lakeBiomeStyle;
			case RIVER:
				return riverBiomeStyle;
			case LAND:
				return landBiomeStyle;
			case SEA:
				return seaBiomeStyle;
			case SHORE:
				return shoreBiomeStyle;
			default:
				break;
		}

		return landBiomeStyle;
	}
}
