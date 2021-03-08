package com.volmit.iris.object;

import com.volmit.iris.generator.noise.CNG;
import com.volmit.iris.scaffold.cache.AtomicCache;
import com.volmit.iris.scaffold.data.DataProvider;
import com.volmit.iris.util.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

@EqualsAndHashCode()
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
	@Desc("When bore is enabled, expand max-y of the cuboid it removes")
	private int boreExtendMaxY = 0;

	@MaxNumber(64)
	@MinNumber(-1)
	@DontObfuscate
	@Desc("When bore is enabled, lower min-y of the cuboid it removes")
	private int boreExtendMinY = 0;

	@DontObfuscate
	@Desc("If set to true, objects will place on the terrain height, ignoring the water surface.")
	private boolean underwater = false;

	@DontObfuscate
	@Desc("If set to true, objects will place in carvings (such as underground) or under an overhang.")
	private CarvingMode carvingSupport = CarvingMode.SURFACE_ONLY;

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

	public IrisObjectPlacement toPlacement(String... place)
	{
		IrisObjectPlacement p = new IrisObjectPlacement();
		p.setPlace(new KList<>(place));
		p.setTranslateCenter(translateCenter);
		p.setMode(mode);
		p.setEdit(edit);
		p.setTranslate(translate);
		p.setWarp(warp);
		p.setBore(bore);
		p.setMeld(meld);
		p.setWaterloggable(waterloggable);
		p.setOnwater(onwater);
		p.setSmartBore(smartBore);
		p.setCarvingSupport(carvingSupport);
		p.setUnderwater(underwater);
		p.setBoreExtendMaxY(boreExtendMaxY);
		p.setBoreExtendMinY(boreExtendMinY);
		p.setOverStilt(overStilt);
		p.setDensity(density);
		p.setChance(chance);
		p.setSnow(snow);
		p.setClamp(clamp);
		p.setRotation(rotation);
		return p;
	}

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

	public IrisObject getObject(DataProvider g, RNG random)
	{
		if(place.isEmpty())
		{
			return null;
		}

		return g.getData().getObjectLoader().load(place.get(random.nextInt(place.size())));
	}

	public boolean isVacuum() {
		return getMode().equals(ObjectPlaceMode.VACUUM);
	}
}
