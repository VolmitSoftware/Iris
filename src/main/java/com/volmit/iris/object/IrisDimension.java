package com.volmit.iris.object;

import org.bukkit.Material;
import org.bukkit.World.Environment;
import org.bukkit.block.data.BlockData;
import org.bukkit.util.BlockVector;

import com.volmit.iris.Iris;
import com.volmit.iris.gen.ContextualChunkGenerator;
import com.volmit.iris.gen.PostBlockChunkGenerator;
import com.volmit.iris.gen.atomics.AtomicCache;
import com.volmit.iris.noise.CNG;
import com.volmit.iris.util.ArrayType;
import com.volmit.iris.util.B;
import com.volmit.iris.util.ChunkPosition;
import com.volmit.iris.util.Desc;
import com.volmit.iris.util.DontObfuscate;
import com.volmit.iris.util.IrisPostBlockFilter;
import com.volmit.iris.util.KList;
import com.volmit.iris.util.KSet;
import com.volmit.iris.util.MaxNumber;
import com.volmit.iris.util.MinNumber;
import com.volmit.iris.util.RNG;
import com.volmit.iris.util.Required;

import lombok.Data;
import lombok.EqualsAndHashCode;

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

	@MinNumber(0.0001)
	@MaxNumber(64)
	@DontObfuscate
	@Desc("The Thickness scale of cave veins")
	private double caveThickness = 1D;

	@Required
	@MinNumber(0)
	@DontObfuscate
	@Desc("The version of this dimension. Changing this will stop users from accidentally upgrading (and breaking their worlds).")
	private int version = 1;

	@MinNumber(0.0001)
	@MaxNumber(512)
	@DontObfuscate
	@Desc("The cave web scale. Smaller values means scaled up vein networks.")
	private double caveScale = 1D;

	@MinNumber(-256)
	@MaxNumber(256)
	@DontObfuscate
	@Desc("Shift the Y value of the cave networks up or down.")
	private double caveShift = 0D;

	@DontObfuscate
	@Desc("Generate caves or not.")
	private boolean caves = true;

	@DontObfuscate
	@Desc("Carve terrain or not")
	private double carvingZoom = 3.5;

	@MinNumber(-256)
	@MaxNumber(256)
	@DontObfuscate
	@Desc("Carving starts at this height")
	private int carvingMin = 115;

	@MinNumber(-256)
	@MaxNumber(256)
	@DontObfuscate
	@Desc("The maximum height carving happens at")
	private int carvingMax = 239;

	@MinNumber(0.0001)
	@MaxNumber(256)
	@DontObfuscate
	@Desc("The thickness of carvings (vertical)")
	private double carvingSliverThickness = 5.5D;

	@MinNumber(0.0001)
	@MaxNumber(512)
	@DontObfuscate
	@Desc("The thickness of ripples on carved walls")
	private double carvingRippleThickness = 3D;

	@MinNumber(0.0001)
	@MaxNumber(512)
	@DontObfuscate
	@Desc("How much of 3D space is carved out. Higher values make carvings cross into 3d space more often (bigger)")
	private double carvingEnvelope = 0.335D;

	@DontObfuscate
	@Desc("Carve terrain or not")
	private boolean carving = true;

	@DontObfuscate
	@Desc("Generate vanilla structures")
	private boolean vanillaStructures = false;

	@DontObfuscate
	@Desc("Generate decorations or not")
	private boolean decorate = true;

	@DontObfuscate
	@Desc("Use post processing or not")
	private boolean postProcessing = true;

	@ArrayType(min = 1, type = IrisPostProcessor.class)
	@DontObfuscate
	@Desc("Post Processors")
	private KList<IrisPostProcessor> postProcessors = getDefaultPostProcessors();

	@ArrayType(min = 1, type = IrisCompatabilityFilter.class)
	@DontObfuscate
	@Desc("Compatability filters")
	private KList<IrisCompatabilityFilter> compatability = getDefaultCompatability();

	@DontObfuscate
	@Desc("The ceiling dimension. Leave blank for normal sky.")
	private String ceiling = "";

	@DontObfuscate
	@Desc("Mirrors the generator floor into the ceiling. Think nether but worse...")
	private boolean mirrorCeiling = false;

	@Required
	@DontObfuscate
	@Desc("The world environment")
	private Environment environment = Environment.NORMAL;

	@Required
	@ArrayType(min = 1, type = String.class)
	@DontObfuscate
	@Desc("Define all of the regions to include in this dimension. Dimensions -> Regions -> Biomes -> Objects etc")
	private KList<String> regions = new KList<>();

	@Required
	@MinNumber(0)
	@MaxNumber(255)
	@DontObfuscate
	@Desc("The fluid height for this dimension")
	private int fluidHeight = 63;

	@DontObfuscate
	@Desc("Keep this either undefined or empty. Setting any biome name into this will force iris to only generate the specified biome. Great for testing.")
	private String focus = "";

	@MinNumber(0.0001)
	@MaxNumber(512)
	@DontObfuscate
	@Desc("Zoom in or out the biome size. Higher = bigger biomes")
	private double biomeZoom = 5D;

	@MinNumber(0.0001)
	@MaxNumber(512)
	@DontObfuscate
	@Desc("Zoom in or out the terrain. This stretches the terrain. Due to performance improvements, Higher than 2.0 may cause weird rounding artifacts. Lower = more terrain changes per block. Its a true zoom-out.")
	private double terrainZoom = 2D;

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

	@MinNumber(0)
	@MaxNumber(8192)
	@DontObfuscate
	@Desc("The shuffle of regions")
	private double regionShuffle = 11;

	@MinNumber(0)
	@MaxNumber(8192)
	@DontObfuscate
	@Desc("The shuffle of land vs sea")
	private double continentalShuffle = 99;

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

	@DontObfuscate
	@Desc("The dispersion of materials for the rock palette")
	private Dispersion rockDispersion = Dispersion.SCATTER;

	@MinNumber(0.0001)
	@MaxNumber(512)
	@DontObfuscate
	@Desc("The rock zoom mostly for zooming in on a wispy palette")
	private double rockZoom = 5;

	@ArrayType(min = 1, type = String.class)
	@DontObfuscate
	@Desc("The palette of blocks for 'stone'")
	private KList<String> rockPalette = new KList<String>().qadd("STONE");

	@ArrayType(min = 1, type = String.class)
	@DontObfuscate
	@Desc("The palette of blocks for 'water'")
	private KList<String> fluidPalette = new KList<String>().qadd("WATER");

	@ArrayType(min = 1, type = IrisBiomeMutation.class)
	@DontObfuscate
	@Desc("Define biome mutations for this dimension")
	private KList<IrisBiomeMutation> mutations = new KList<>();

	private transient AtomicCache<ChunkPosition> parallaxSize = new AtomicCache<>();
	private transient AtomicCache<KList<BlockData>> rockData = new AtomicCache<>();
	private transient AtomicCache<KList<BlockData>> fluidData = new AtomicCache<>();
	private transient AtomicCache<KList<IrisPostBlockFilter>> cacheFilters = new AtomicCache<>();
	private transient AtomicCache<CNG> rockLayerGenerator = new AtomicCache<>();
	private transient AtomicCache<CNG> fluidLayerGenerator = new AtomicCache<>();
	private transient AtomicCache<CNG> coordFracture = new AtomicCache<>();
	private transient AtomicCache<Double> sinr = new AtomicCache<>();
	private transient AtomicCache<Double> cosr = new AtomicCache<>();
	private transient AtomicCache<Double> rad = new AtomicCache<>();
	private transient boolean inverted = false;

	public KList<IrisPostBlockFilter> getPostBlockProcessors(PostBlockChunkGenerator g)
	{
		return cacheFilters.aquire(() ->
		{
			KList<IrisPostBlockFilter> cacheFilters = new KList<>();

			for(IrisPostProcessor i : getPostProcessors())
			{
				cacheFilters.add(g.createProcessor(i.getProcessor(), i.getPhase()));
			}

			g.setMinPhase(0);
			g.setMaxPhase(0);

			for(IrisPostBlockFilter i : cacheFilters)
			{
				g.setMinPhase(Math.min(g.getMinPhase(), i.getPhase()));
				g.setMaxPhase(Math.max(g.getMaxPhase(), i.getPhase()));
			}

			Iris.info("Post Processing: " + cacheFilters.size() + " filters. Phases: " + g.getMinPhase() + " - " + g.getMaxPhase());
			return cacheFilters;
		});
	}

	public static KList<IrisCompatabilityFilter> getDefaultCompatability()
	{
		KList<IrisCompatabilityFilter> filters = new KList<>();

		// Below 1.16
		filters.add(new IrisCompatabilityFilter("WEEPING_VINES", "NETHER_FENCE"));
		filters.add(new IrisCompatabilityFilter("WEEPING_VINES_PLANT", "NETHER_FENCE"));
		filters.add(new IrisCompatabilityFilter("WARPED_WART_BLOCK", "NETHER_WART_BLOCK"));
		filters.add(new IrisCompatabilityFilter("TWISTING_VINES", "BAMBOO"));
		filters.add(new IrisCompatabilityFilter("TWISTING_VINES_PLANT", "BAMBOO"));
		filters.add(new IrisCompatabilityFilter("TARGET", "COBBLESTONE"));
		filters.add(new IrisCompatabilityFilter("SOUL_SOIL", "SOULSAND"));
		filters.add(new IrisCompatabilityFilter("SOUL_TORCH", "TORCH"));
		filters.add(new IrisCompatabilityFilter("SOUL_LANTERN", "LANTERN"));
		filters.add(new IrisCompatabilityFilter("SOUL_FIRE", "FIRE"));
		filters.add(new IrisCompatabilityFilter("SOUL_CAMPFIRE", "CAMPFIRE"));
		filters.add(new IrisCompatabilityFilter("SHROOMLIGHT", "GLOWSTONE"));
		filters.add(new IrisCompatabilityFilter("RESPAWN_ANCHOR", "OBSIDIAN"));
		filters.add(new IrisCompatabilityFilter("NETHER_SPROUTS", "RED_MUSHROOM"));
		filters.add(new IrisCompatabilityFilter("NETHER_GOLD_ORE", "GOLD_ORE"));
		filters.add(new IrisCompatabilityFilter("LODESTONE", "STONE"));
		filters.add(new IrisCompatabilityFilter("STRIPPED_WARPED_HYPHAE", "BROWN_MUSHROOM_BLOCK"));
		filters.add(new IrisCompatabilityFilter("STRIPPED_CRIMSON_HYPHAE", "RED_MUSHROOM_BLOCK"));
		filters.add(new IrisCompatabilityFilter("WARPED_HYPHAE", "MUSHROOM_STEM"));
		filters.add(new IrisCompatabilityFilter("CRIMSON_HYPHAE", "RED_MUSHROOM_BLOCK"));
		filters.add(new IrisCompatabilityFilter("GILDED_BLACKSTONE", "COBBLESTONE"));
		filters.add(new IrisCompatabilityFilter("CRYING_OBSIDIAN", "OBSIDIAN"));
		filters.add(new IrisCompatabilityFilter("STRIPPED_WARPED_STEM", "MUSHROOM_STEM"));
		filters.add(new IrisCompatabilityFilter("STRIPPED_CRIMSON_STEM", "MUSHROOM_STEM"));
		filters.add(new IrisCompatabilityFilter("WARPED_STEM", "MUSHROOM_STEM"));
		filters.add(new IrisCompatabilityFilter("CRIMSON_STEM", "MUSHROOM_STEM"));
		filters.add(new IrisCompatabilityFilter("CRIMSON_ROOTS", "RED_MUSHROOM"));
		filters.add(new IrisCompatabilityFilter("WARPED_ROOTS", "BROWN_MUSHROOM"));
		filters.add(new IrisCompatabilityFilter("CRIMSON_PLANKS", "OAK_PLANKS"));
		filters.add(new IrisCompatabilityFilter("WARPED_PLANKS", "OAK_PLANKS"));
		filters.add(new IrisCompatabilityFilter("WARPED_NYLIUM", "MYCELIUM"));
		filters.add(new IrisCompatabilityFilter("CRIMSON_NYLIUM", "MYCELIUM"));
		filters.add(new IrisCompatabilityFilter("WARPED_FUNGUS", "BROWN_MUSHROOM"));
		filters.add(new IrisCompatabilityFilter("CRIMSON_FUNGUS", "RED_MUSHROOM"));
		filters.add(new IrisCompatabilityFilter("CRACKED_NETHER_BRICKS", "NETHER_BRICKS"));
		filters.add(new IrisCompatabilityFilter("CHISELED_NETHER_BRICKS", "NETHER_BRICKS"));
		filters.add(new IrisCompatabilityFilter("NETHER_FENCE", "LEGACY_NETHER_FENCE"));
		filters.add(new IrisCompatabilityFilter("CHAIN", "IRON_BARS"));
		filters.add(new IrisCompatabilityFilter("NETHERITE_BLOCK", "QUARTZ_BLOCK"));
		filters.add(new IrisCompatabilityFilter("BLACKSTONE", "COBBLESTONE"));
		filters.add(new IrisCompatabilityFilter("BASALT", "STONE"));
		filters.add(new IrisCompatabilityFilter("ANCIENT_DEBRIS", "NETHERRACK"));
		filters.add(new IrisCompatabilityFilter("NETHERRACK", "LEGACY_NETHERRACK"));

		// Below 1.15
		filters.add(new IrisCompatabilityFilter("HONEY_BLOCK", "OAK_LEAVES"));
		filters.add(new IrisCompatabilityFilter("BEEHIVE", "OAK_LEAVES"));
		filters.add(new IrisCompatabilityFilter("BEE_NEST", "OAK_LEAVES"));

		// Below 1.14
		filters.add(new IrisCompatabilityFilter("GRANITE_WALL", "COBBLESTONE_WALL"));
		filters.add(new IrisCompatabilityFilter("BLUE_ICE", "PACKED_ICE"));
		filters.add(new IrisCompatabilityFilter("DIORITE_WALL", "COBBLESTONE_WALL"));
		filters.add(new IrisCompatabilityFilter("ANDESITE_WALL", "COBBLESTONE_WALL"));
		filters.add(new IrisCompatabilityFilter("SWEET_BERRY_BUSH", "GRASS"));
		filters.add(new IrisCompatabilityFilter("STONECUTTER", "CRAFTING_TABLE"));
		filters.add(new IrisCompatabilityFilter("SANDSTONE_STAIRS", "LEGACY_SANDSTONE_STAIRS"));
		filters.add(new IrisCompatabilityFilter("SMOOTH_SANDSTONE_STAIRS", "LEGACY_SANDSTONE_STAIRS"));
		filters.add(new IrisCompatabilityFilter("MOSSY_COBBLESTONE_STAIRS", "COBBLESTONE_STAIRS"));
		filters.add(new IrisCompatabilityFilter("MOSSY_STONE_BRICK_STAIRS", "STONE_BRICK_STAIRS"));
		filters.add(new IrisCompatabilityFilter("POLISHED_GRANITE_STAIRS", "COBBLESTONE_STAIRS"));
		filters.add(new IrisCompatabilityFilter("GRANITE_STAIRS", "COBBLESTONE_STAIRS"));
		filters.add(new IrisCompatabilityFilter("POLISHED_DIORITE_STAIRS", "COBBLESTONE_STAIRS"));
		filters.add(new IrisCompatabilityFilter("DIORITE_STAIRS", "COBBLESTONE_STAIRS"));
		filters.add(new IrisCompatabilityFilter("POLISHED_ANDESITE_STAIRS", "COBBLESTONE_STAIRS"));
		filters.add(new IrisCompatabilityFilter("ANDESITE_STAIRS", "COBBLESTONE_STAIRS"));
		filters.add(new IrisCompatabilityFilter("STONE_STAIRS", "COBBLESTONE_STAIRS"));
		filters.add(new IrisCompatabilityFilter("END_STONE_BRICK_STAIRS", "LEGACY_SANDSTONE_STAIRS"));
		filters.add(new IrisCompatabilityFilter("NETHER_BRICK_STAIRS", "LEGACY_NETHER_BRICK_STAIRS"));
		filters.add(new IrisCompatabilityFilter("RED_NETHER_BRICK_STAIRS", "NETHER_BRICK_STAIRS"));
		filters.add(new IrisCompatabilityFilter("SMOOTH_QUARTZ_STAIRS", "LEGACY_QUARTZ_STAIRS"));
		filters.add(new IrisCompatabilityFilter("QUARTZ_STAIRS", "LEGACY_QUARTZ_STAIRS"));
		filters.add(new IrisCompatabilityFilter("RED_SANDSTONE_STAIRS", "LEGACY_RED_SANDSTONE_STAIRS"));
		filters.add(new IrisCompatabilityFilter("SMOOTH_RED_SANDSTONE_STAIRS", "LEGACY_RED_SANDSTONE_STAIRS"));
		filters.add(new IrisCompatabilityFilter("STONE_SLAB", "SMOOTH_STONE_SLAB"));
		filters.add(new IrisCompatabilityFilter("SMOKER", "FURNACE"));
		filters.add(new IrisCompatabilityFilter("SMITHING_TABLE", "CRAFTING_TABLE"));
		filters.add(new IrisCompatabilityFilter("END_STONE_BRICK_SLAB", "SANDSTONE_SLAB"));
		filters.add(new IrisCompatabilityFilter("RED_NETHER_BRICK_SLAB", "NETHER_BRICK_SLAB"));
		filters.add(new IrisCompatabilityFilter("SMOOTH_QUARTZ_SLAB", "QUARTZ_SLAB"));
		filters.add(new IrisCompatabilityFilter("CUT_SANDSTONE_SLAB", "SANDSTONE_SLAB"));
		filters.add(new IrisCompatabilityFilter("CUT_RED_SANDSTONE_SLAB", "RED_SANDSTONE_SLAB"));
		filters.add(new IrisCompatabilityFilter("SMOOTH_RED_SANDSTONE_SLAB", "RED_SANDSTONE_SLAB"));
		filters.add(new IrisCompatabilityFilter("SMOOTH_SANDSTONE_SLAB", "SANDSTONE_SLAB"));
		filters.add(new IrisCompatabilityFilter("MOSSY_COBBLESTONE_SLAB", "COBBLESTONE_SLAB"));
		filters.add(new IrisCompatabilityFilter("MOSSY_STONE_BRICK_SLAB", "STONE_BRICK_SLAB"));
		filters.add(new IrisCompatabilityFilter("STONE_SLAB", "SMOOTH_STONE_SLAB"));
		filters.add(new IrisCompatabilityFilter("ANDESITE_SLAB", "COBBLESTONE_SLAB"));
		filters.add(new IrisCompatabilityFilter("ANDESITE_SLAB", "COBBLESTONE_SLAB"));
		filters.add(new IrisCompatabilityFilter("DIORITE_SLAB", "COBBLESTONE_SLAB"));
		filters.add(new IrisCompatabilityFilter("GRANITE_SLAB", "COBBLESTONE_SLAB"));
		filters.add(new IrisCompatabilityFilter("POLISHED_ANDESITE_SLAB", "SMOOTH_STONE_SLAB"));
		filters.add(new IrisCompatabilityFilter("POLISHED_DIORITE_SLAB", "SMOOTH_STONE_SLAB"));
		filters.add(new IrisCompatabilityFilter("POLISHED_GRANITE_SLAB", "SMOOTH_STONE_SLAB"));
		filters.add(new IrisCompatabilityFilter("WARPED_WALL_SIGN", "LEGACY_WALL_SIGN"));
		filters.add(new IrisCompatabilityFilter("WARPED_SIGN", "LEGACY_SIGN_POST"));
		filters.add(new IrisCompatabilityFilter("SPRUCE_WALL_SIGN", "LEGACY_WALL_SIGN"));
		filters.add(new IrisCompatabilityFilter("SPRUCE_SIGN", "LEGACY_SIGN_POST"));
		filters.add(new IrisCompatabilityFilter("OAK_WALL_SIGN", "LEGACY_WALL_SIGN"));
		filters.add(new IrisCompatabilityFilter("OAK_SIGN", "LEGACY_SIGN_POST"));
		filters.add(new IrisCompatabilityFilter("JUNGLE_WALL_SIGN", "LEGACY_WALL_SIGN"));
		filters.add(new IrisCompatabilityFilter("JUNGLE_SIGN", "LEGACY_SIGN_POST"));
		filters.add(new IrisCompatabilityFilter("DARK_OAK_WALL_SIGN", "LEGACY_WALL_SIGN"));
		filters.add(new IrisCompatabilityFilter("DARK_OAK_SIGN", "LEGACY_SIGN_POST"));
		filters.add(new IrisCompatabilityFilter("CRIMSON_WALL_SIGN", "LEGACY_WALL_SIGN"));
		filters.add(new IrisCompatabilityFilter("CRIMSON_SIGN", "LEGACY_SIGN_POST"));
		filters.add(new IrisCompatabilityFilter("BIRCH_WALL_SIGN", "LEGACY_WALL_SIGN"));
		filters.add(new IrisCompatabilityFilter("BIRCH_SIGN", "LEGACY_SIGN_POST"));
		filters.add(new IrisCompatabilityFilter("ACACIA_WALL_SIGN", "LEGACY_WALL_SIGN"));
		filters.add(new IrisCompatabilityFilter("ACACIA_SIGN", "LEGACY_SIGN_POST"));
		filters.add(new IrisCompatabilityFilter("SCAFFOLDING", "BIRCH_FENCE"));
		filters.add(new IrisCompatabilityFilter("LOOM", "LOOM"));
		filters.add(new IrisCompatabilityFilter("LECTERN", "BOOKSHELF"));
		filters.add(new IrisCompatabilityFilter("LANTERN", "REDSTONE_LAMP"));
		filters.add(new IrisCompatabilityFilter("JIGSAW", "AIR"));
		filters.add(new IrisCompatabilityFilter("GRINDSTONE", "COBBLESTONE"));
		filters.add(new IrisCompatabilityFilter("FLETCHING_TABLE", "CRAFTING_TABLE"));
		filters.add(new IrisCompatabilityFilter("COMPOSTER", "CHEST"));
		filters.add(new IrisCompatabilityFilter("CARTOGRAPHY_TABLE", "CRAFTING_TABLE"));
		filters.add(new IrisCompatabilityFilter("CAMPFIRE", "DARK_OAK_SLAB"));
		filters.add(new IrisCompatabilityFilter("BLAST_FURNACE", "FURNACE"));
		filters.add(new IrisCompatabilityFilter("BELL", "REDSTONE_LAMP"));
		filters.add(new IrisCompatabilityFilter("minecraft:barrel[facing=south]", "minecraft:hay_bale[axis=z]", true));
		filters.add(new IrisCompatabilityFilter("minecraft:barrel[facing=north]", "minecraft:hay_bale[axis=z]", true));
		filters.add(new IrisCompatabilityFilter("minecraft:barrel[facing=east]", "minecraft:hay_bale[axis=x]", true));
		filters.add(new IrisCompatabilityFilter("minecraft:barrel[facing=west]", "minecraft:hay_bale[axis=x]", true));
		filters.add(new IrisCompatabilityFilter("minecraft:barrel[facing=up]", "minecraft:hay_bale[axis=y]", true));
		filters.add(new IrisCompatabilityFilter("minecraft:barrel[facing=down]", "minecraft:hay_bale[axis=y]", true));
		filters.add(new IrisCompatabilityFilter("BAMBOO", "BIRCH_FENCE"));
		filters.add(new IrisCompatabilityFilter("BAMBOO_SAPLING", "BIRCH_SAPLING"));
		filters.add(new IrisCompatabilityFilter("POTTED_BAMBOO", "POTTED_BIRCH_SAPLING"));

		return filters;
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

	private KList<IrisPostProcessor> getDefaultPostProcessors()
	{
		KList<IrisPostProcessor> p = new KList<IrisPostProcessor>();

		p.add(new IrisPostProcessor("wall-painter"));
		p.add(new IrisPostProcessor("slabber"));
		p.add(new IrisPostProcessor("waterlogger", 1));

		return p;
	}

	public BlockData getRock(RNG rng, double x, double y, double z)
	{
		if(getRockData().isEmpty())
		{
			return STONE;
		}

		if(getRockData().size() == 1)
		{
			return getRockData().get(0);
		}

		if(rockDispersion.equals(Dispersion.SCATTER))
		{
			return getRockData().get(getRockGenerator(rng).fit(0, 30000000, x, y, z) % getRockData().size());
		}

		else
		{
			return getRockData().get(getRockGenerator(rng).fit(0, getRockData().size() - 1, x, y, z));
		}
	}

	public CNG getRockGenerator(RNG rng)
	{
		return rockLayerGenerator.aquire(() ->
		{
			RNG rngx = rng.nextParallelRNG((int) (getRockData().size() * getRegions().size() * getCaveScale() * getLandZoom() * 10357));
			CNG rockLayerGenerator = new CNG(rng);
			switch(rockDispersion)
			{
				case SCATTER:
					rockLayerGenerator = CNG.signature(rngx).freq(1000000);
					break;
				case WISPY:
					rockLayerGenerator = CNG.signature(rngx);
					break;
			}

			return rockLayerGenerator;
		});
	}

	public KList<BlockData> getRockData()
	{
		return rockData.aquire(() ->
		{
			KList<BlockData> rockData = new KList<>();
			for(String ix : rockPalette)
			{
				BlockData bx = B.getBlockData(ix);
				if(bx != null)
				{
					rockData.add(bx);
				}
			}

			return rockData;
		});
	}

	public BlockData getFluid(RNG rng, double x, double y, double z)
	{
		if(getFluidData().isEmpty())
		{
			return WATER;
		}

		if(getFluidData().size() == 1)
		{
			return getFluidData().get(0);
		}

		if(rockDispersion.equals(Dispersion.SCATTER))
		{
			return getFluidData().get(getFluidGenerator(rng).fit(0, 30000000, x, y, z) % getFluidData().size());
		}

		else
		{
			return getFluidData().get(getFluidGenerator(rng).fit(0, getFluidData().size() - 1, x, y, z));
		}
	}

	public CNG getFluidGenerator(RNG rng)
	{
		return fluidLayerGenerator.aquire(() ->
		{
			RNG rngx = rng.nextParallelRNG(getFluidData().size() * (int) (getRockData().size() * getRegions().size() * getCaveScale() * getLandZoom() * 10357));
			CNG fluidLayerGenerator = new CNG(rng);
			switch(rockDispersion)
			{
				case SCATTER:
					fluidLayerGenerator = CNG.signature(rngx).freq(1000000);
					break;
				case WISPY:
					fluidLayerGenerator = CNG.signature(rngx);
					break;
			}

			return fluidLayerGenerator;
		});
	}

	public KList<BlockData> getFluidData()
	{
		return fluidData.aquire(() ->
		{
			KList<BlockData> fluidData = new KList<>();
			for(String ix : fluidPalette)
			{
				BlockData bx = B.getBlockData(ix);
				if(bx != null)
				{
					fluidData.add(bx);
				}
			}

			return fluidData;
		});
	}

	public double getDimensionAngle()
	{
		return rad.aquire(() -> Math.toRadians(dimensionAngleDeg));
	}

	public double sinRotate()
	{
		return sinr.aquire(() -> Math.sin(getDimensionAngle()));
	}

	public double cosRotate()
	{
		return cosr.aquire(() -> Math.cos(getDimensionAngle()));
	}

	public KList<IrisRegion> getAllRegions(ContextualChunkGenerator g)
	{
		KList<IrisRegion> r = new KList<>();

		for(String i : getRegions())
		{
			r.add(g != null ? g.loadRegion(i) : Iris.globaldata.getRegionLoader().load(i));
		}

		return r;
	}

	public KList<IrisBiome> getAllBiomes(ContextualChunkGenerator g)
	{
		KList<IrisBiome> r = new KList<>();

		for(IrisRegion i : getAllRegions(g))
		{
			r.addAll(i.getAllBiomes(g));
		}

		return r;
	}

	public ChunkPosition getParallaxSize(ContextualChunkGenerator g)
	{
		return parallaxSize.aquire(() ->
		{
			int x = 0;
			int z = 0;

			KSet<String> objects = new KSet<>();
			KList<IrisRegion> r = getAllRegions(g);
			KList<IrisBiome> b = getAllBiomes(g);

			for(IrisBiome i : b)
			{
				for(IrisObjectPlacement j : i.getObjects())
				{
					objects.addAll(j.getPlace());
				}
			}

			for(String i : objects)
			{
				try
				{
					BlockVector bv = IrisObject.sampleSize(g.getData().getObjectLoader().findFile(i));
					x = bv.getBlockX() > x ? bv.getBlockX() : x;
					z = bv.getBlockZ() > z ? bv.getBlockZ() : z;
				}

				catch(Throwable e)
				{

				}
			}

			for(IrisDepositGenerator i : getDeposits())
			{
				int max = i.getMaxDimension();
				x = max > x ? max : x;
				z = max > z ? max : z;
			}

			for(IrisRegion v : r)
			{
				for(IrisDepositGenerator i : v.getDeposits())
				{
					int max = i.getMaxDimension();
					x = max > x ? max : x;
					z = max > z ? max : z;
				}
			}

			for(IrisBiome v : b)
			{
				for(IrisDepositGenerator i : v.getDeposits())
				{
					int max = i.getMaxDimension();
					x = max > x ? max : x;
					z = max > z ? max : z;
				}
			}

			x = (Math.max(x, 16) + 16) >> 4;
			z = (Math.max(z, 16) + 16) >> 4;
			x = x % 2 == 0 ? x + 1 : x;
			z = z % 2 == 0 ? z + 1 : z;
			Iris.info("Parallax Size: " + x + ", " + z);
			return new ChunkPosition(x, z);
		});
	}

	public BlockData resolve(String bd)
	{
		for(IrisCompatabilityFilter i : getCompatability())
		{
			if(i.getWhen().equalsIgnoreCase(bd))
			{
				return i.getReplace();
			}
		}

		return null;
	}
}
