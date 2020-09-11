package com.volmit.iris.object;

import com.volmit.iris.Iris;
import com.volmit.iris.gen.ContextualTerrainProvider;
import com.volmit.iris.gen.atomics.AtomicCache;
import com.volmit.iris.noise.CNG;
import com.volmit.iris.util.ArrayType;
import com.volmit.iris.util.Desc;
import com.volmit.iris.util.DontObfuscate;
import com.volmit.iris.util.KList;
import com.volmit.iris.util.MaxNumber;
import com.volmit.iris.util.MinNumber;
import com.volmit.iris.util.RNG;
import com.volmit.iris.util.RegistryListObject;
import com.volmit.iris.util.Required;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

@Accessors(chain = true)
@NoArgsConstructor
@AllArgsConstructor
@Desc("Represents an iris object placer. It places objects.")
@Data
public class IrisObjectPlacement
{

	@RegistryListObject
	@Required
	@ArrayType(min = 1, type = String.class)
	@DontObfuscate
	@Desc("List of objects to place")
	private KList<String> place = new KList<>();

	@DontObfuscate
	@Desc("If the place mode is set to CENTER_HEIGHT_RIGID and you have an X/Z translation, Turning on translate center will also translate the center height check.")
	private boolean translateCenter = false;

	@DontObfuscate
	@Desc("The placement mode")
	private ObjectPlaceMode mode = ObjectPlaceMode.CENTER_HEIGHT;

	@ArrayType(min = 1, type = IrisObjectReplace.class)
	@DontObfuscate
	@Desc("Find and replace blocks")
	private KList<IrisObjectReplace> edit = new KList<>();

	@DontObfuscate
	@Desc("Translate this object's placement")
	private IrisObjectTranslate translate = new IrisObjectTranslate();

	@DontObfuscate
	@Desc("Rotate this objects placement")
	private IrisObjectRotation rotation = new IrisObjectRotation();

	@DontObfuscate
	@Desc("Limit the max height or min height of placement.")
	private IrisObjectLimit clamp = new IrisObjectLimit();

	@MinNumber(0)
	@MaxNumber(1)
	@DontObfuscate
	@Desc("The maximum layer level of a snow filter overtop of this placement. Set to 0 to disable. Max of 1.")
	private double snow = 0;

	@Required
	@MinNumber(0)
	@MaxNumber(1)
	@DontObfuscate
	@Desc("The chance for this to place in a chunk. If you need multiple per chunk, set this to 1 and use density.")
	private double chance = 1;

	@MinNumber(1)
	@DontObfuscate
	@Desc("If the chance check passes, place this many in a single chunk")
	private int density = 1;

	@MaxNumber(64)
	@MinNumber(0)
	@DontObfuscate
	@Desc("If the place mode is set to stilt, you can over-stilt it even further into the ground. Especially useful when using fast stilt due to inaccuracies.")
	private int overStilt = 0;

	@MaxNumber(64)
	@MinNumber(0)
	@DontObfuscate
	@Desc("When boar is enabled, expand max-y of the cuboid it removes")
	private int boarExtendMaxY = 0;

	@MaxNumber(64)
	@MinNumber(0)
	@DontObfuscate
	@Desc("When boar is enabled, lower min-y of the cuboid it removes")
	private int boarExtendMinY = 0;

	@DontObfuscate
	@Desc("If set to true, objects will place on the terrain height, ignoring the water surface.")
	private boolean underwater = false;

	@DontObfuscate
	@Desc("If set to true, Iris will try to fill the insides of 'rooms' and 'pockets' where air should fit based off of raytrace checks. This prevents a village house placing in an area where a tree already exists, and instead replaces the parts of the tree where the interior of the structure is. \n\nThis operation does not affect warmed-up generation speed however it does slow down loading objects.")
	private boolean smartBore = false;

	@DontObfuscate
	@Desc("If set to true, Blocks placed underwater that could be waterlogged are waterlogged.")
	private boolean waterloggable = false;

	@DontObfuscate
	@Desc("If set to true, objects will place on the fluid height level Such as boats.")
	private boolean onwater = false;

	@DontObfuscate
	@Desc("If set to true, this object will only place parts of itself where blocks already exist. Warning: Melding is very performance intensive!")
	private boolean meld = false;

	@DontObfuscate
	@Desc("If set to true, this object will place from the ground up instead of height checks when not y locked to the surface. This is not compatable with X and Z axis rotations (it may look off)")
	private boolean bottom = false;

	@DontObfuscate
	@Desc("If set to true, air will be placed before the schematic places.")
	private boolean bore = false;

	@DontObfuscate
	@Desc("Use a generator to warp the field of coordinates. Using simplex for example would make a square placement warp like a flag")
	private IrisGeneratorStyle warp = new IrisGeneratorStyle(NoiseStyle.FLAT);

	private final transient AtomicCache<CNG> surfaceWarp = new AtomicCache<>();

	public CNG getSurfaceWarp(RNG rng)
	{
		return surfaceWarp.aquire(() ->
		{
			return getWarp().create(rng);
		});
	}

	public double warp(RNG rng, double x, double y, double z)
	{
		return getSurfaceWarp(rng).fitDouble(-(getWarp().getMultiplier() / 2D), (getWarp().getMultiplier() / 2D), x, y, z);
	}

	public IrisObject getSchematic(ContextualTerrainProvider g, RNG random)
	{
		if(place.isEmpty())
		{
			return null;
		}

		return (g == null ? Iris.globaldata : g.getData()).getObjectLoader().load(place.get(random.nextInt(place.size())));
	}

	public int getTriesForChunk(RNG random)
	{
		if(chance <= 0)
		{
			return 0;
		}

		if(chance >= 1 || random.nextDouble() < chance)
		{
			return density;
		}

		return 0;
	}
}
