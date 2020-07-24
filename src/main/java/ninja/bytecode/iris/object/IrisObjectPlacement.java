package ninja.bytecode.iris.object;

import lombok.Data;
import ninja.bytecode.iris.Iris;
import ninja.bytecode.iris.util.Desc;
import ninja.bytecode.iris.util.RNG;
import ninja.bytecode.shuriken.collections.KList;

@Data
public class IrisObjectPlacement
{
	@Desc("List of objects to place")
	private KList<String> place = new KList<>();

	@Desc("Find and replace blocks")
	private KList<IrisObjectReplace> edit = new KList<>();

	@Desc("Translate this object's placement")
	private IrisObjectTranslate translate = new IrisObjectTranslate();

	@Desc("Rotate this objects placement")
	private IrisObjectRotation rotation = new IrisObjectRotation();

	@Desc("The maximum layer level of a snow filter overtop of this placement. Set to 0 to disable. Max of 1.")
	private double snow = 0;

	@Desc("The chance for this to place in a chunk. If you need multiple per chunk, set this to 1 and use density.")
	private double chance = 1;

	@Desc("If the chance check passes, place this many in a single chunk")
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
