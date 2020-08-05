package com.volmit.iris.object;

import com.volmit.iris.Iris;
import com.volmit.iris.util.Desc;
import com.volmit.iris.util.DontObfuscate;
import com.volmit.iris.util.KList;
import com.volmit.iris.util.RNG;

import lombok.Data;

@Data
public class IrisObjectPlacement
{
	@DontObfuscate
	@Desc("List of objects to place")
	private KList<String> place = new KList<>();

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
	@Desc("The maximum layer level of a snow filter overtop of this placement. Set to 0 to disable. Max of 1.")
	private double snow = 0;

	@DontObfuscate
	@Desc("The chance for this to place in a chunk. If you need multiple per chunk, set this to 1 and use density.")
	private double chance = 1;

	@DontObfuscate
	@Desc("If the chance check passes, place this many in a single chunk")
	private int density = 1;

	@DontObfuscate
	@Desc("If set to true, objects will place on the terrain height, ignoring the water surface.")
	private boolean underwater = false;

	@DontObfuscate
	@Desc("If set to true, objects will place on the fluid height level Such as boats.")
	private boolean onwater = false;

	@DontObfuscate
	@Desc("If set to true, this object will only place parts of itself where blocks already exist.")
	private boolean meld = false;

	@DontObfuscate
	@Desc("If set to true, this object will place from the ground up instead of height checks when not y locked to the surface.")
	private boolean bottom = false;

	@DontObfuscate
	@Desc("If set to true, air will be placed before the schematic places.")
	private boolean bore = false;

	public IrisObjectPlacement()
	{

	}

	public IrisObject getSchematic(RNG random)
	{
		if(place.isEmpty())
		{
			return null;
		}

		return Iris.data.getObjectLoader().load(place.get(random.nextInt(place.size())));
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
