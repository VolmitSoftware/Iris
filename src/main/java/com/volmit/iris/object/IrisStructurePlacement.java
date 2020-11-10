package com.volmit.iris.object;

import com.volmit.iris.Iris;
import com.volmit.iris.generator.legacy.atomics.AtomicCache;
import com.volmit.iris.generator.noise.CellGenerator;
import com.volmit.iris.scaffold.data.DataProvider;
import com.volmit.iris.util.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

@Accessors(chain = true)
@NoArgsConstructor
@AllArgsConstructor
@Desc("Represents a structure placement")
@Data
public class IrisStructurePlacement
{

	@RegistryListStructure
	@Required
	@DontObfuscate
	@Desc("The structure tileset to use")
	private String tileset = "";

	@Required
	@MinNumber(0.0001)
	@DontObfuscate
	@Desc("The structure chance zoom. Higher = bigger cells, further away")
	private double zoom = 1D;

	@MinNumber(-1)
	@MaxNumber(1)
	@DontObfuscate
	@Desc("The ratio. Lower values means cells can get closer to other cells. Negative values means make veins of structures")
	private double ratio = 0.25D;

	@Required
	@MinNumber(1)
	@DontObfuscate
	@Desc("The rarity for this structure")
	private int rarity = 4;

	@MinNumber(-1)
	@MaxNumber(255)
	@DontObfuscate
	@Desc("The height or -1 for surface")
	private int height = -1;

	@MinNumber(0)
	@DontObfuscate
	@Desc("The chance cell shuffle (rougher edges)")
	private double shuffle = 22;

	private final transient AtomicCache<CellGenerator> chanceCell = new AtomicCache<>();
	private final transient AtomicCache<IrisStructure> structure = new AtomicCache<>();
	private final transient AtomicCache<IrisObjectPlacement> config = new AtomicCache<>();

	private IrisObjectPlacement getConfig()
	{
		return config.aquire(() ->
		{
			IrisObjectPlacement p = new IrisObjectPlacement();
			p.setWaterloggable(false);
			return p;
		});
	}

	public IrisObject load(DataProvider g, String s)
	{
		return g.getData().getObjectLoader().load(s);
	}

	public int gridSize(DataProvider g)
	{
		return getStructure(g).getGridSize();
	}

	public int gridHeight(DataProvider g)
	{
		return getStructure(g).getGridHeight();
	}

	public IrisStructure getStructure(DataProvider g)
	{
		return structure.aquire(() -> (g == null ? Iris.globaldata : g.getData()).getStructureLoader().load(getTileset()));
	}

	public CellGenerator getChanceGenerator(RNG g)
	{
		return chanceCell.aquire(() ->
		{
			CellGenerator chanceCell = new CellGenerator(g.nextParallelRNG(-72346).nextParallelRNG((height + 10000) * rarity));
			chanceCell.setCellScale(1D);
			chanceCell.setShuffle(getShuffle());
			return chanceCell;
		});
	}
}
