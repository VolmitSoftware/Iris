package ninja.bytecode.iris.object;

import ninja.bytecode.iris.Iris;
import ninja.bytecode.iris.util.RNG;
import ninja.bytecode.shuriken.collections.KList;

public class IrisObjectPlacement
{
	private KList<String> place = new KList<>();
	private double chance = 1;
	private int density = 1;

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
